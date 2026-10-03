package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A model's analysis. Models don't always return clean JSON, so parsing is
 * lenient: code fences and surrounding prose are ignored, and an answer that
 * isn't JSON at all is kept whole as the root cause (and marked unparsed).
 */
public record Answer(String rootCause, String fix, List<String> evidence, boolean parsed, String raw) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public Answer {
        evidence = List.copyOf(evidence);
    }

    public static Answer parse(String raw) {
        Optional<JsonNode> json = jsonObject(raw);
        if (json.isEmpty() || !json.get().has("root_cause")) {
            return new Answer(raw.strip(), "", List.of(), false, raw);
        }
        JsonNode node = json.get();
        List<String> evidence = new ArrayList<>();
        node.path("evidence").forEach(e -> evidence.add(e.asText()));
        return new Answer(node.path("root_cause").asText(), node.path("fix").asText(), evidence, true, raw);
    }

    /** The outermost {...} in the text, if it parses as a JSON object. */
    static Optional<JsonNode> jsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return Optional.empty();
        }
        try {
            JsonNode node = JSON.readTree(text.substring(start, end + 1));
            return node.isObject() ? Optional.of(node) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
