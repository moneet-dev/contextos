package dev.moneet.contextos.incident.domain;

import dev.moneet.contextos.core.graph.Edge;

import java.util.Objects;

/** {@code from} depends on {@code to}. */
public record ServiceDependency(String from, String to, DependencyKind kind) implements Edge {

    public ServiceDependency {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }

    @Override
    public String source() {
        return from;
    }

    @Override
    public String target() {
        return to;
    }
}
