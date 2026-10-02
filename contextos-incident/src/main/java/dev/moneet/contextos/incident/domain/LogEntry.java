package dev.moneet.contextos.incident.domain;

import java.time.Instant;

/** {@code exception} and {@code traceId} may be null. */
public record LogEntry(Instant timestamp,
                       String service,
                       LogLevel level,
                       String logger,
                       String message,
                       String exception,
                       String traceId,
                       SourceRef source) {
}
