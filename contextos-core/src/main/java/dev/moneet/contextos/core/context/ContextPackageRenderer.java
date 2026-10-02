package dev.moneet.contextos.core.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders items grouped by domain (domains in order of their best item), each
 * item with a provenance header:
 * <pre>
 * === CODE CONTEXT ===
 *
 * [METHOD] PaymentService#charge(PaymentRequest) - target (score 1.00)
 * source: src/main/java/.../PaymentService.java:34
 * public PaymentReceipt charge(PaymentRequest request)
 * </pre>
 * The output is the concatenation of {@link #domainHeader} and {@link #item}
 * blocks, so the packager can cost each block separately.
 */
public final class ContextPackageRenderer {

    public String domainHeader(String domain) {
        return "=== " + domain.toUpperCase() + " CONTEXT ===\n\n";
    }

    public String item(ContextItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(item.kind()).append("] ").append(item.title());
        if (item.reason() != null && !item.reason().isEmpty()) {
            sb.append(" - ").append(item.reason());
        }
        sb.append(String.format(" (score %.2f)\n", item.score()));
        if (!item.provenance().isEmpty()) {
            sb.append("source: ").append(String.join(", ", item.provenance())).append("\n");
        }
        sb.append(item.content().strip()).append("\n\n");
        return sb.toString();
    }

    /** Items in the given order, grouped by domain. */
    public String render(List<ContextItem> items) {
        Map<String, List<ContextItem>> byDomain = new LinkedHashMap<>();
        for (ContextItem item : items) {
            byDomain.computeIfAbsent(item.domain(), k -> new ArrayList<>()).add(item);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<ContextItem>> domain : byDomain.entrySet()) {
            sb.append(domainHeader(domain.getKey()));
            domain.getValue().forEach(item -> sb.append(item(item)));
        }
        return sb.toString();
    }
}
