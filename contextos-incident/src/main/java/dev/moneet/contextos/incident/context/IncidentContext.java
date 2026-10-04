package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.core.graph.Reached;
import dev.moneet.contextos.incident.domain.HealthySignal;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.evidence.RankedEvidence;

import java.util.List;

/**
 * Result of an incident context strategy: the incident, the services in scope
 * (with their distance and path from the affected services), the ranked
 * evidence, the signals that stayed healthy, and the rendered, LLM-ready text.
 */
public record IncidentContext(Incident incident,
                              List<Reached<ServiceDependency>> scope,
                              List<RankedEvidence> evidence,
                              List<HealthySignal> healthy,
                              String rendered) {

    public IncidentContext {
        scope = List.copyOf(scope);
        evidence = List.copyOf(evidence);
        healthy = List.copyOf(healthy);
    }

    @Override
    public String toString() {
        return rendered;
    }
}
