package dev.moneet.contextos.incident.domain;

import java.util.List;

public final class Telemetry {

    private final List<LogEntry> logs;
    private final List<MetricSample> metrics;
    private final List<Span> spans;
    private final List<ChangeEvent> changes;

    public Telemetry(List<LogEntry> logs, List<MetricSample> metrics, List<Span> spans, List<ChangeEvent> changes) {
        this.logs = List.copyOf(logs);
        this.metrics = List.copyOf(metrics);
        this.spans = List.copyOf(spans);
        this.changes = List.copyOf(changes);
    }

    public List<LogEntry> getLogs() {
        return logs;
    }

    public List<MetricSample> getMetrics() {
        return metrics;
    }

    public List<Span> getSpans() {
        return spans;
    }

    public List<ChangeEvent> getChanges() {
        return changes;
    }

    @Override
    public String toString() {
        return "Telemetry{" +
                "logs=" + logs.size() +
                ", metrics=" + metrics.size() +
                ", spans=" + spans.size() +
                ", changes=" + changes.size() +
                '}';
    }
}
