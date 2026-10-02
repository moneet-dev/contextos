package dev.moneet.contextos.incident.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * A node in the runtime topology. {@code repository} optionally names the source
 * repository that implements the service, for linking to Code Context.
 */
public record Service(String name, ServiceKind kind, String description, String repository) {

    public Service {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        description = description == null ? "" : description;
    }

    public Optional<String> repositoryName() {
        return Optional.ofNullable(repository);
    }
}
