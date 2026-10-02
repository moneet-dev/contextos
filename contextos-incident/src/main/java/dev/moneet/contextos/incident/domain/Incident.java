package dev.moneet.contextos.incident.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Incident(String id,
                       String title,
                       Severity severity,
                       Instant startedAt,
                       Instant detectedAt,
                       List<String> affectedServices,
                       List<String> symptoms) {

    public Incident {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        affectedServices = List.copyOf(affectedServices);
        symptoms = List.copyOf(symptoms);
    }
}
