package dev.moneet.contextos.incident.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A finding derived from telemetry, attributed to a service (and, for calls to
 * another service, its {@code peer}).
 *
 * <p>{@code strength} (0..1) is how pronounced the signal is: volume, deviation
 * from baseline or failure rate. {@code attributes} keep structured details such
 * as the logger, exception, metric or operation, for linking to code later.
 * {@code sources} point at the first telemetry records behind the finding.
 */
public record Evidence(EvidenceKind kind,
                       String service,
                       String peer,
                       Instant onset,
                       Instant lastSeen,
                       int occurrences,
                       double strength,
                       String summary,
                       Map<String, String> attributes,
                       List<SourceRef> sources) {

    public Evidence {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(onset, "onset must not be null");
        attributes = Map.copyOf(attributes);
        sources = List.copyOf(sources);
    }

    public Optional<String> peerService() {
        return Optional.ofNullable(peer);
    }
}
