package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.moneet.contextos.eval.ChatModel.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiCompatibleClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private record Reply(int status, String body, String retryAfter) {
    }

    private HttpServer server;
    private final ConcurrentLinkedQueue<Reply> replies = new ConcurrentLinkedQueue<>();
    private final List<JsonNode> requests = new ArrayList<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final List<Duration> sleeps = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            synchronized (requests) {
                requests.add(JSON.readTree(exchange.getRequestBody()));
            }
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            Reply reply = replies.poll();
            if (reply.retryAfter() != null) {
                exchange.getResponseHeaders().add("Retry-After", reply.retryAfter());
            }
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void shouldSendOpenAiChatCompletionRequest() {
        replies.add(new Reply(200, completion("the answer"), null));

        String text = client("secret-key").complete(List.of(Message.system("sys"), Message.user("question")));

        assertEquals("the answer", text);
        JsonNode request = requests.get(0);
        assertEquals("test-model", request.path("model").asText());
        assertEquals(0.2, request.path("temperature").asDouble());
        assertEquals(512, request.path("max_tokens").asInt());
        assertEquals("system", request.path("messages").get(0).path("role").asText());
        assertEquals("question", request.path("messages").get(1).path("content").asText());
        assertEquals("Bearer secret-key", authorization.get());
    }

    @Test
    void shouldOmitAuthorizationWithoutKey() {
        replies.add(new Reply(200, completion("ok"), null));

        client(null).complete(List.of(Message.user("q")));

        assertNull(authorization.get(), "e.g. a local Ollama server needs no key");
    }

    @Test
    void shouldRetryRateLimitsHonouringRetryAfter() {
        replies.add(new Reply(429, "{\"error\": \"rate limited\"}", "7"));
        replies.add(new Reply(503, "unavailable", null));
        replies.add(new Reply(200, completion("finally"), null));

        assertEquals("finally", client("k").complete(List.of(Message.user("q"))));
        assertEquals(List.of(Duration.ofSeconds(7), Duration.ofSeconds(4)), sleeps);
        assertEquals(3, requests.size());
    }

    @Test
    void shouldFailFastOnAuthenticationErrors() {
        replies.add(new Reply(401, "{\"error\": \"invalid api key\"}", null));

        OpenAiCompatibleClient.ApiException e = assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("bad").complete(List.of(Message.user("q"))));

        assertEquals(401, e.status());
        assertTrue(e.getMessage().contains("invalid api key"));
        assertEquals(1, requests.size());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void shouldTreatARateLimitThatOutlastsEveryRetryAsAnExhaustedQuota() {
        String body = "{\"error\": {\"message\": \"You exceeded your current quota.\\n* Quota exceeded for metric: "
                + "generate_content_free_tier_requests, limit: 20, model: m\\nPlease check your plan.\"}}";
        for (int i = 0; i < 6; i++) {
            replies.add(new Reply(429, body, "1"));
        }

        OpenAiCompatibleClient.ApiException e = assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("k").complete(List.of(Message.user("q"))));

        assertEquals(429, e.status());
        assertTrue(e.fatal(), "every later call would hit the same quota");
        assertTrue(e.getMessage().startsWith("Still rate-limited after 6 attempts; likely a daily quota for test-model"),
                e.getMessage());
        assertTrue(e.getMessage().endsWith("[quota: generate_content_free_tier_requests, limit: 20, model: m]"),
                e.getMessage());
        assertEquals(6, requests.size());
        assertEquals(5, sleeps.size());
    }

    @Test
    void shouldGiveUpOnPersistentServerErrorsWithoutStoppingTheRun() {
        for (int i = 0; i < 6; i++) {
            replies.add(new Reply(503, "overloaded", null));
        }

        OpenAiCompatibleClient.ApiException e = assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("k").complete(List.of(Message.user("q"))));

        assertEquals(503, e.status());
        assertFalse(e.fatal(), "an overloaded model may recover by the next trial");
        assertEquals(6, requests.size());
    }

    @Test
    void shouldStopAtOnceWhenTheQuotaIsZero() {
        replies.add(new Reply(429, "{\"error\": {\"code\": 429, \"message\": \"You exceeded your current quota. "
                + "Quota exceeded for metric: generate_content_free_tier_requests, limit: 0, model: pro\"}}", null));

        OpenAiCompatibleClient.ApiException e = assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("k").complete(List.of(Message.user("q"))));

        assertTrue(e.fatal());
        assertTrue(e.getMessage().startsWith("The quota for model test-model is 0"), e.getMessage());
        assertEquals(1, requests.size(), "a zero quota is not retried");
    }

    @Test
    void shouldWaitForTheDelayGivenInTheBody() {
        replies.add(new Reply(429, "{\"error\": {\"message\": \"Quota exceeded for metric: requests, limit: 10. "
                + "Please retry in 12.4s.\", \"details\": [{\"retryDelay\": \"12s\"}]}}", null));
        replies.add(new Reply(200, completion("ok"), null));

        assertEquals("ok", client("k").complete(List.of(Message.user("q"))));
        assertEquals(List.of(Duration.ofMillis(12_900)), sleeps, "first delay given (12.4 s) plus a 0.5 s margin");
    }

    @Test
    void shouldRejectAnswersCutOffByTheTokenLimit() {
        replies.add(new Reply(200, JSON.createObjectNode().set("choices", JSON.createArrayNode().add(
                JSON.createObjectNode().put("finish_reason", "length").set("message",
                        JSON.createObjectNode().put("content", "{\"root_cause\": \"the da")))).toString(), null));

        OpenAiCompatibleClient.ApiException e = assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("k").complete(List.of(Message.user("q"))));

        assertEquals("Response truncated at max_tokens=512; raise --max-tokens", e.getMessage());
        assertFalse(e.fatal());
    }

    @Test
    void shouldRejectResponsesWithoutContent() {
        replies.add(new Reply(200, "{\"choices\": []}", null));

        assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("k").complete(List.of(Message.user("q"))));
    }

    private OpenAiCompatibleClient client(String key) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/";
        return new OpenAiCompatibleClient(baseUrl, "test-model", key, 0.2, 512,
                HttpClient.newHttpClient(), sleeps::add);
    }

    private static String completion(String content) {
        return JSON.createObjectNode().set("choices", JSON.createArrayNode().add(
                JSON.createObjectNode().set("message",
                        JSON.createObjectNode().put("role", "assistant").put("content", content)))).toString();
    }
}
