package dev.moneet.contextos.code.domain;

import java.util.Objects;

/**
 * A typed edge between two symbols. {@code line} is the first line in the
 * source symbol's file where the relationship occurs.
 */
public record SymbolReference(String sourceId, String targetId, ReferenceKind kind, int line) {

    public SymbolReference {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }

    /** The endpoint opposite {@code symbolId}. */
    public String other(String symbolId) {
        return sourceId.equals(symbolId) ? targetId : sourceId;
    }
}
