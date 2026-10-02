package dev.moneet.contextos.incident.graph;

import dev.moneet.contextos.incident.domain.ServiceDependency;

import java.util.List;
import java.util.Optional;

/**
 * A service reached by traversal. {@code path} holds the edges from a start
 * service to this one; {@code distance} is its length, the shortest available.
 */
public record ReachedService(String name, int distance, List<ServiceDependency> path) {

    public ReachedService {
        path = List.copyOf(path);
    }

    public Optional<ServiceDependency> lastEdge() {
        return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1));
    }

    /** Service names along the path, from the start service to this one. */
    public List<String> nodes() {
        String[] nodes = new String[path.size() + 1];
        String current = name;
        nodes[path.size()] = current;
        for (int i = path.size() - 1; i >= 0; i--) {
            current = path.get(i).other(current);
            nodes[i] = current;
        }
        return List.of(nodes);
    }
}
