package dev.moneet.contextos.incident.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.moneet.contextos.incident.domain.SourceRef;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** JSON / JSON Lines helpers for the fixture readers. Errors name the file and line. */
final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    static JsonNode readDocument(Path root, String file) {
        try {
            return MAPPER.readTree(Files.readString(root.resolve(file), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read: " + root.resolve(file), e);
        }
    }

    /** One record per non-blank line; a missing file yields no records. */
    static <T> List<T> readLines(Path root, String file, BiFunction<JsonNode, SourceRef, T> mapper) {
        List<T> records = new ArrayList<>();
        forEachLine(root, file, (line, source) -> {
            try {
                records.add(mapper.apply(MAPPER.readTree(line), source));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid JSON at " + source, e);
            }
        });
        return records;
    }

    interface LineHandler {
        void handle(String line, SourceRef source);
    }

    /** Calls {@code handler} for every non-blank line; a missing file is treated as empty. */
    static void forEachLine(Path root, String file, LineHandler handler) {
        Path path = root.resolve(file);
        if (!Files.exists(path)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read: " + path, e);
        }
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                handler.handle(lines.get(i), new SourceRef(file, i + 1));
            }
        }
    }

    static String text(JsonNode node, String field, Object where) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Missing '" + field + "' at " + where);
        }
        return value.asText();
    }

    static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    static Instant instant(String text, Object where) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid timestamp '" + text + "' at " + where, e);
        }
    }

    static <E extends Enum<E>> E enumValue(Class<E> type, String text, Object where) {
        try {
            return Enum.valueOf(type, text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + type.getSimpleName() + " '" + text + "' at " + where, e);
        }
    }

    static List<String> textList(JsonNode node, String field) {
        List<String> values = new ArrayList<>();
        JsonNode array = node.get(field);
        if (array != null) {
            array.forEach(v -> values.add(v.asText()));
        }
        return values;
    }
}
