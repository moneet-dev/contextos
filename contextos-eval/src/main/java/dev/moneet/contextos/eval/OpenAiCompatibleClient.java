package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Client for OpenAI-compatible {@code POST /chat/completions} endpoints (Groq,
 * OpenRouter, Gemini, Mistral, GitHub Models, Cerebras, Ollama, ...).
 *
 * <p>Rate limits (429) and transient server errors (5xx) are retried with
 * backoff, honouring {@code Retry-After}; free tiers hit rate limits routinely.
 * Authentication and other client errors fail immediately.
 */
public final class OpenAiCompatibleClient implements ChatModel {

    /** Thrown for responses that retrying won't fix, e.g. a bad key or unknown model. */
    public static final class ApiException extends RuntimeException {

        private final int status;

        ApiException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 6;
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final double temperature;
    private final int maxTokens;
    private final HttpClient http;
    private final Sleeper sleeper;

    public OpenAiCompatibleClient(String baseUrl, String model, String apiKey, double temperature, int maxTokens) {
        this(baseUrl, model, apiKey, temperature, maxTokens,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(),
                duration -> Thread.sleep(duration.toMillis()));
    }

    OpenAiCompatibleClient(String baseUrl, String model, String apiKey, double temperature, int maxTokens,
                           HttpClient http, Sleeper sleeper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.http = http;
        this.sleeper = sleeper;
    }

    @Override
    public String name() {
        return model;
    }

    @Override
    public String complete(List<Message> messages) {
        HttpRequest request = request(body(messages));

        for (int attempt = 1; ; attempt++) {
            HttpResponse<String> response = send(request);
            int status = response.statusCode();

            if (status == 200) {
                return content(response.body());
            }
            boolean retryable = status == 429 || status >= 500;
            if (!retryable || attempt == MAX_ATTEMPTS) {
                throw new ApiException(status, "HTTP " + status + " from " + baseUrl + ": " + excerpt(response.body()));
            }
            pause(backoff(response, attempt));
        }
    }

    private String body(List<Message> messages) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        ArrayNode array = body.putArray("messages");
        for (Message message : messages) {
            array.addObject().put("role", message.role()).put("content", message.content());
        }
        return body.toString();
    }

    private HttpRequest request(String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }
        return builder.build();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("Request to " + baseUrl + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static String content(String responseBody) {
        try {
            JsonNode message = JSON.readTree(responseBody).path("choices").path(0).path("message");
            JsonNode content = message.path("content");
            if (content.isMissingNode() || content.isNull()) {
                throw new ApiException(200, "Response has no message content: " + excerpt(responseBody));
            }
            return content.asText();
        } catch (IOException e) {
            throw new ApiException(200, "Response is not JSON: " + excerpt(responseBody));
        }
    }

    /** Retry-After (seconds) when given, otherwise 2, 4, 8, ... seconds, capped at a minute. */
    static Duration backoff(HttpResponse<String> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            try {
                Duration requested = Duration.ofMillis((long) (Double.parseDouble(retryAfter.get().trim()) * 1000));
                return requested.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : requested;
            } catch (NumberFormatException e) {
                // an HTTP date; fall back to exponential backoff
            }
        }
        Duration exponential = Duration.ofSeconds(1L << Math.min(attempt, 6));
        return exponential.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : exponential;
    }

    private void pause(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private static String excerpt(String body) {
        String flat = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        return flat.length() > 300 ? flat.substring(0, 300) + "..." : flat;
    }
}
