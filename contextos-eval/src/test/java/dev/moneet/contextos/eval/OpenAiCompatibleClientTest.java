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
    void shouldGiveUpAfterRepeatedRateLimits() {
        for (int i = 0; i < 6; i++) {
            replies.add(new Reply(429, "slow down", "1"));
        }

        OpenAiCompatibleClient.ApiException e = assertThrows(OpenAiCompatibleClient.ApiException.class,
                () -> client("k").complete(List.of(Message.user("q"))));

        assertEquals(429, e.status());
        assertEquals(6, requests.size());
        assertEquals(5, sleeps.size());
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
