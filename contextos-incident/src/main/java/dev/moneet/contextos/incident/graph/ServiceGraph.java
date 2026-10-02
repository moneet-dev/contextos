package dev.moneet.contextos.incident.graph;

import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed, directed service graph. An edge {@code a -> b} means a depends on b.
 */
public final class ServiceGraph {

    private final Map<String, Service> services;
    private final Map<String, List<ServiceDependency>> dependencies;
    private final Map<String, List<ServiceDependency>> dependents;

    public ServiceGraph(Map<String, Service> services,
                        Map<String, List<ServiceDependency>> dependencies,
                        Map<String, List<ServiceDependency>> dependents) {
        this.services = services;
        this.dependencies = dependencies;
        this.dependents = dependents;
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
        return dependencies.getOrDefault(name, List.of());
    }

    /** Edges from the services that depend on {@code name}. */
    public List<ServiceDependency> getDependents(String name) {
        return dependents.getOrDefault(name, List.of());
    }

    /**
     * Breadth-first traversal from {@code startNames} up to {@code maxDepth} hops.
     * Every reachable service is returned once, with its shortest distance, in
     * order of distance. Start services have distance 0.
     */
    public List<ReachedService> traverse(Collection<String> startNames, int maxDepth, Direction direction) {

        Map<String, ReachedService> reached = new LinkedHashMap<>();
        Deque<ReachedService> queue = new ArrayDeque<>();

        for (String start : startNames) {
            if (containsService(start) && !reached.containsKey(start)) {
                ReachedService origin = new ReachedService(start, 0, List.of());
                reached.put(start, origin);
                queue.add(origin);
            }
        }

        while (!queue.isEmpty()) {
            ReachedService current = queue.poll();
            if (current.distance() >= maxDepth) {
                continue;
            }

            for (ServiceDependency edge : edges(current.name(), direction)) {
                String next = edge.other(current.name());
                if (reached.containsKey(next)) {
                    continue;
                }

                List<ServiceDependency> path = new ArrayList<>(current.path());
                path.add(edge);
                ReachedService step = new ReachedService(next, current.distance() + 1, path);
                reached.put(next, step);
                queue.add(step);
            }
        }

        return List.copyOf(reached.values());
    }

    private List<ServiceDependency> edges(String name, Direction direction) {
        return switch (direction) {
            case DEPENDENCIES -> getDependencies(name);
            case DEPENDENTS -> getDependents(name);
            case BOTH -> {
                List<ServiceDependency> both = new ArrayList<>(getDependencies(name));
                both.addAll(getDependents(name));
                yield both;
            }
        };
    }

    @Override
    public String toString() {
        return "ServiceGraph{" +
                "services=" + services.size() +
                ", dependencies=" + dependencies.values().stream().mapToInt(List::size).sum() +
                '}';
    }
}
