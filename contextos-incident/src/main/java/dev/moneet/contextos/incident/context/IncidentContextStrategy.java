package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.graph.ServiceGraph;

public interface IncidentContextStrategy {

    IncidentContext generate(RuntimeSnapshot snapshot, ServiceGraph graph);
}
