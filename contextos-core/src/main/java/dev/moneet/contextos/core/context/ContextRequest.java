package dev.moneet.contextos.core.context;

import java.util.Objects;

/**
 * What to build context for. {@code target} is interpreted by each provider:
 * a table name, a symbol query, an incident id. {@code depth} is the number of
 * relationship hops to explore around it.
 */
public record ContextRequest(String target, int depth, ContextBudget budget) {

    public ContextRequest {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(budget, "budget must not be null");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative: " + depth);
        }
    }

    public static ContextRequest of(String target, int depth) {
        return new ContextRequest(target, depth, ContextBudget.unlimited());
    }

    public ContextRequest withBudget(ContextBudget budget) {
        return new ContextRequest(target, depth, budget);
    }
}
