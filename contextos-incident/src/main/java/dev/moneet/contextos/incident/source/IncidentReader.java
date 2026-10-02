package dev.moneet.contextos.incident.source;

import com.fasterxml.jackson.databind.JsonNode;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.Severity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code incidents.json}:
 * <pre>
 * { "incidents": [ {"id", "title", "severity", "startedAt", "detectedAt"?,
 *                   "affectedServices": [...], "symptoms": [...]} ] }
 * </pre>
 */
final class IncidentReader {

    List<Incident> read(Path root, String file) {
        List<Incident> incidents = new ArrayList<>();
        if (!Files.exists(root.resolve(file))) {
            return incidents;
        }

        for (JsonNode node : Json.readDocument(root, file).path("incidents")) {
            String id = Json.text(node, "id", file);
            String where = file + " incident " + id;
            String detectedAt = Json.optionalText(node, "detectedAt");

            incidents.add(new Incident(
                    id,
                    Json.text(node, "title", where),
                    Json.enumValue(Severity.class, Json.text(node, "severity", where), where),
                    Json.instant(Json.text(node, "startedAt", where), where),
                    detectedAt == null ? null : Json.instant(detectedAt, where),
                    Json.textList(node, "affectedServices"),
                    Json.textList(node, "symptoms")));
        }
        return incidents;
    }
}
