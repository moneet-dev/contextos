package dev.moneet.contextos.incident.source;

import dev.moneet.contextos.incident.domain.RuntimeSnapshot;

public interface RuntimeSource {
    RuntimeSnapshot load();
}
