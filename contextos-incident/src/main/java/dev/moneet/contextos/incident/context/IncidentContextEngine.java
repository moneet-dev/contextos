package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.graph.ServiceGraph;

public final class IncidentContextEngine {

    private final IncidentContextStrategy strategy;

    public IncidentContextEngine(IncidentContextStrategy strategy) {
        this.strategy = strategy;
    }

    public IncidentContext generate(RuntimeSnapshot snapshot, ServiceGraph graph) {
        return strategy.generate(snapshot, graph);
    }
}
