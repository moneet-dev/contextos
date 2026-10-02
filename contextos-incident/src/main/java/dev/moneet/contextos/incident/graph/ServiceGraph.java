package dev.moneet.contextos.incident.graph;

import dev.moneet.contextos.core.graph.Reached;
import dev.moneet.contextos.core.graph.TypedGraph;
import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Typed, directed service graph. An edge {@code a -> b} means a depends on b.
 */
public final class ServiceGraph {

    private final Map<String, Service> services;
    private final TypedGraph<ServiceDependency> graph;

    public ServiceGraph(Map<String, Service> services, TypedGraph<ServiceDependency> graph) {
        this.services = services;
        this.graph = graph;
    }

    public Collection<Service> getServices() {
        return services.values();
    }

    public boolean containsService(String name) {
        return services.containsKey(name);
    }

    public Service getService(String name) {
        Service service = services.get(name);
        if (service == null) {
            throw new IllegalArgumentException("Service not found: " + name);
        }
        return service;
    }

    /** Edges from {@code name} to the services it depends on. */
    public List<ServiceDependency> getDependencies(String name) {
        return graph.outgoing(name);
    }

    /** Edges from the services that depend on {@code name}. */
    public List<ServiceDependency> getDependents(String name) {
        return graph.incoming(name);
    }

    /**
     * Breadth-first traversal from {@code startNames} up to {@code maxDepth} hops.
     * Every reachable service is returned once, with its shortest distance, in
     * order of distance. Start services have distance 0.
     */
    public List<Reached<ServiceDependency>> traverse(Collection<String> startNames, int maxDepth, Direction direction) {
        return graph.traverse(startNames, maxDepth, direction.toCore());
    }

    @Override
    public String toString() {
        return "ServiceGraph{" +
                "services=" + services.size() +
                ", dependencies=" + graph.edgeCount() +
                '}';
    }
}
