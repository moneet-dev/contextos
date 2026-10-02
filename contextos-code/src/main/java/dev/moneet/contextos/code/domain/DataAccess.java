package dev.moneet.contextos.code.domain;

import java.util.Objects;

/**
 * Evidence that a symbol touches a database table or entity. Kept as a hint
 * (not resolved against a schema) so it can later be linked to SQL Schema Context.
 */
public record DataAccess(String symbolId, DataAccessKind kind, String name, int line) {

    public DataAccess {
        Objects.requireNonNull(symbolId, "symbolId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String toString() {
        return name + " [" + kind + "]";
    }
}
