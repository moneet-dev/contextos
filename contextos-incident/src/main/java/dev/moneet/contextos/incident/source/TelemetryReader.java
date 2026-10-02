package dev.moneet.contextos.incident.source;

import dev.moneet.contextos.incident.domain.ChangeEvent;
import dev.moneet.contextos.incident.domain.ChangeType;
import dev.moneet.contextos.incident.domain.LogEntry;
import dev.moneet.contextos.incident.domain.LogLevel;
import dev.moneet.contextos.incident.domain.MetricSample;
import dev.moneet.contextos.incident.domain.Span;
import dev.moneet.contextos.incident.domain.SpanStatus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads telemetry files. Each is optional; a missing file yields no records.
 * <ul>
 *   <li>logs (JSON Lines): timestamp, service, level, logger, message, exception?, traceId?</li>
 *   <li>metrics (CSV with header): timestamp,service,metric,value</li>
 *   <li>traces (JSON Lines): traceId, spanId, parentSpanId?, service, operation, peer?,
 *       start, durationMs, status, error?</li>
 *   <li>changes (JSON Lines): timestamp, service, type, description</li>
 * </ul>
 */
final class TelemetryReader {

    List<LogEntry> readLogs(Path root, String file) {
        return Json.readLines(root, file, (node, source) -> new LogEntry(
                Json.instant(Json.text(node, "timestamp", source), source),
                Json.text(node, "service", source),
                Json.enumValue(LogLevel.class, Json.text(node, "level", source), source),
                Json.optionalText(node, "logger"),
                Json.text(node, "message", source),
                Json.optionalText(node, "exception"),
                Json.optionalText(node, "traceId"),
                source));
    }

    List<MetricSample> readMetrics(Path root, String file) {
        List<MetricSample> samples = new ArrayList<>();

        Json.forEachLine(root, file, (line, source) -> {
            if (source.line() == 1) {
                return; // header
            }
            String[] fields = line.split(",", -1);
            if (fields.length != 4) {
                throw new IllegalArgumentException("Expected timestamp,service,metric,value at " + source);
            }
            try {
                samples.add(new MetricSample(
                        Json.instant(fields[0].trim(), source),
                        fields[1].trim(),
                        fields[2].trim(),
                        Double.parseDouble(fields[3].trim()),
                        source));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid metric value at " + source, e);
            }
        });
        return samples;
    }

    List<Span> readSpans(Path root, String file) {
        return Json.readLines(root, file, (node, source) -> new Span(
                Json.text(node, "traceId", source),
                Json.text(node, "spanId", source),
                Json.optionalText(node, "parentSpanId"),
                Json.text(node, "service", source),
                Json.text(node, "operation", source),
                Json.optionalText(node, "peer"),
                Json.instant(Json.text(node, "start", source), source),
                node.path("durationMs").asLong(),
                Json.enumValue(SpanStatus.class, Json.text(node, "status", source), source),
                Json.optionalText(node, "error"),
                source));
    }

    List<ChangeEvent> readChanges(Path root, String file) {
        return Json.readLines(root, file, (node, source) -> new ChangeEvent(
                Json.instant(Json.text(node, "timestamp", source), source),
                Json.text(node, "service", source),
                Json.enumValue(ChangeType.class, Json.text(node, "type", source), source),
                Json.text(node, "description", source),
                source));
    }
}
