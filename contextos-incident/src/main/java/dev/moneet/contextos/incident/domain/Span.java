package dev.moneet.contextos.incident.domain;

import java.time.Instant;

/**
 * A trace span emitted by {@code service}. For client spans, {@code peer} is the
 * service being called (e.g. a database). {@code parentSpanId}, {@code peer} and
 * {@code error} may be null.
 */
public record Span(String traceId,
                   String spanId,
                   String parentSpanId,
                   String service,
                   String operation,
                   String peer,
                   Instant start,
                   long durationMs,
                   SpanStatus status,
                   String error,
                   SourceRef source) {
}
