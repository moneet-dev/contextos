package dev.moneet.contextos.incident.domain;

import java.util.Objects;

/** {@code from} depends on {@code to}. */
public record ServiceDependency(String from, String to, DependencyKind kind) {

    public ServiceDependency {
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }

    /** The endpoint opposite {@code service}. */
    public String other(String service) {
        return from.equals(service) ? to : from;
    }
}
