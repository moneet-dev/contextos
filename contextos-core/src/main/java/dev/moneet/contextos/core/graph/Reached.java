package dev.moneet.contextos.core.graph;

import java.util.List;
import java.util.Optional;

/**
 * A node reached by traversal. {@code path} holds the edges from a start node
 * to this one in traversal order; {@code distance} is its length, which
 * breadth-first traversal guarantees is the shortest.
 */
public record Reached<E extends Edge>(String id, int distance, List<E> path) {

    public Reached {
        path = List.copyOf(path);
    }

    public Optional<E> lastEdge() {
        return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1));
    }

    /** Node ids along the path, from the start node to this one. */
    public List<String> nodes() {
        String[] nodes = new String[path.size() + 1];
        String current = id;
        nodes[path.size()] = current;
        for (int i = path.size() - 1; i >= 0; i--) {
            current = path.get(i).other(current);
            nodes[i] = current;
        }
        return List.of(nodes);
    }
}
