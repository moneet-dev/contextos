package dev.moneet.contextos.code.domain;

import dev.moneet.contextos.core.graph.Edge;

import java.util.Objects;

/**
 * A typed edge between two symbols. {@code line} is the first line in the
 * source symbol's file where the relationship occurs.
 */
public record SymbolReference(String sourceId, String targetId, ReferenceKind kind, int line) implements Edge {

    public SymbolReference {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }

    @Override
    public String source() {
        return sourceId;
    }

    @Override
    public String target() {
        return targetId;
    }
}
