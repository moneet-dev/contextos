package dev.moneet.contextos.incident.graph;

import dev.moneet.contextos.core.graph.TypedGraph;
import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.domain.ServiceTopology;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServiceGraphBuilder {

    public ServiceGraph build(ServiceTopology topology) {

        Map<String, Service> services = new LinkedHashMap<>();
        for (Service service : topology.getServices()) {
            services.put(service.name(), service);
        }

        // Deterministic traversal order: by dependency kind, then by the service at the other end
        TypedGraph<ServiceDependency> graph = TypedGraph.of(services.keySet(), topology.getDependencies(),
                Comparator.comparing(ServiceDependency::kind));

        return new ServiceGraph(Collections.unmodifiableMap(services), graph);
    }
}
