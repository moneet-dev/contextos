package dev.moneet.contextos.incident.domain;

import java.time.Instant;

/** Something that changed in production: a deploy, config change, flag flip or job run. */
public record ChangeEvent(Instant timestamp, String service, ChangeType type, String description, SourceRef source) {
}
