package dev.moneet.contextos.code.graph;

import dev.moneet.contextos.code.domain.SymbolReference;

import java.util.List;
import java.util.Optional;

/**
 * A symbol reached by traversal. {@code path} holds the edges from a start
 * symbol to this one in traversal order; {@code distance} is its length,
 * which BFS guarantees is the shortest.
 */
public record ReachedSymbol(String symbolId, int distance, List<SymbolReference> path) {

    public ReachedSymbol {
        path = List.copyOf(path);
    }

    public Optional<SymbolReference> lastEdge() {
        return path.isEmpty() ? Optional.empty() : Optional.of(path.get(path.size() - 1));
    }

    /** Symbol ids along the path, from the start symbol to this one. */
    public List<String> nodes() {
        String[] nodes = new String[path.size() + 1];
        String current = symbolId;
        nodes[path.size()] = current;
        for (int i = path.size() - 1; i >= 0; i--) {
            current = path.get(i).other(current);
            nodes[i] = current;
        }
        return List.of(nodes);
    }
}
