package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.evidence.RankedEvidence;
import dev.moneet.contextos.incident.graph.ReachedService;

import java.util.List;

/**
 * Result of an incident context strategy: the incident, the services in scope
 * (with their distance and path from the affected services), the ranked
 * evidence and the rendered, LLM-ready text.
 */
public record IncidentContext(Incident incident,
                              List<ReachedService> scope,
                              List<RankedEvidence> evidence,
                              String rendered) {

    public IncidentContext {
        scope = List.copyOf(scope);
        evidence = List.copyOf(evidence);
    }

    @Override
    public String toString() {
        return rendered;
    }
}
