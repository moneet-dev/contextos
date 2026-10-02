package dev.moneet.contextos.core.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One unit of context from any domain.
 *
 * @param id          stable id, unique across domains, e.g. {@code code:com.acme.Foo#bar()}
 * @param domain      producing domain, e.g. {@code sql}, {@code code}, {@code incident}
 * @param kind        domain-specific kind, e.g. {@code TABLE}, {@code METHOD}, {@code METRIC_ANOMALY}
 * @param title       short human-readable name
 * @param content     the text given to the model
 * @param score       relevance in 0..1, as ranked by the producing domain
 * @param reason      why the item was selected
 * @param provenance  where the content came from: files and lines, tables, telemetry records
 * @param attributes  structured details for linking between domains (tables, loggers, services)
 */
public record ContextItem(String id,
                          String domain,
                          String kind,
                          String title,
                          String content,
                          double score,
                          String reason,
                          List<String> provenance,
                          Map<String, String> attributes) {

    public ContextItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(domain, "domain must not be null");
        Objects.requireNonNull(content, "content must not be null");
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be within 0..1: " + score);
        }
        provenance = List.copyOf(provenance);
        attributes = Map.copyOf(attributes);
    }
}
