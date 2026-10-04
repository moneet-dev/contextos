package dev.moneet.schema.graph;

import java.util.Set;

public interface TraversalStrategy {
    Set<String> traverse(String start, int depth, SchemaGraph graph);

    /**
     * Traverses the graph starting from a table.
     * <p>
     * Traversal is bidirectional (dependencies + dependents).
     *
     * @param start starting table
     * @param depth maximum traversal depth
     * @param graph graph reference
     * @return set of reachable tables
     */
    Set<String> traverse(
            String start,
            int depth,
            SchemaGraph graph,
            TraversalDirection direction
    );
}