package dev.moneet.contextos.incident.domain;

import java.time.Instant;

public record MetricSample(Instant timestamp, String service, String metric, double value, SourceRef source) {
}
