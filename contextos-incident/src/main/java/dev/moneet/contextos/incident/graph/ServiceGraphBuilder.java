package dev.moneet.contextos.incident.graph;

import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.domain.ServiceTopology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ServiceGraphBuilder {

    public ServiceGraph build(ServiceTopology topology) {

        Map<String, Service> services = new LinkedHashMap<>();
        Map<String, List<ServiceDependency>> dependencies = new HashMap<>();
        Map<String, List<ServiceDependency>> dependents = new HashMap<>();

        for (Service service : topology.getServices()) {
            services.put(service.name(), service);
            dependencies.put(service.name(), new ArrayList<>());
            dependents.put(service.name(), new ArrayList<>());
        }

        for (ServiceDependency dependency : topology.getDependencies()) {
            dependencies.get(dependency.from()).add(dependency);
            dependents.get(dependency.to()).add(dependency);
        }

        // Deterministic traversal order: by dependency kind, then by the service at the other end
        return new ServiceGraph(
                Collections.unmodifiableMap(services),
                makeImmutable(dependencies, ServiceDependency::to),
                makeImmutable(dependents, ServiceDependency::from));
    }

    private Map<String, List<ServiceDependency>> makeImmutable(Map<String, List<ServiceDependency>> map,
                                                              Function<ServiceDependency, String> otherEnd) {
        Comparator<ServiceDependency> order = Comparator
                .comparing(ServiceDependency::kind)
                .thenComparing(otherEnd);

        Map<String, List<ServiceDependency>> result = new HashMap<>();
        for (Map.Entry<String, List<ServiceDependency>> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream().sorted(order).toList());
        }
        return Map.copyOf(result);
    }
}
