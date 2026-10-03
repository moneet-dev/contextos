package dev.moneet.contextos.incident.domain;

import java.util.List;
import java.util.Objects;

/**
 * Something that stayed normal during the incident window: a metric within its
 * baseline range, or calls to a dependency at their usual latency without
 * errors. Healthy signals rule out causes, e.g. "database query latency stayed
 * normal" rules out a slow database.
 *
 * @param service  the service the signal belongs to
 * @param signal   the metric name, or the call ("operation -> peer")
 * @param summary  one line with the window values and the baseline
 */
public record HealthySignal(String service, String signal, String summary, List<SourceRef> sources) {

    public HealthySignal {
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        sources = List.copyOf(sources);
    }
}
