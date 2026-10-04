package dev.moneet.schema.graph;

import java.util.LinkedHashSet;
import java.util.Set;

public final class DfsTraversalStrategy implements TraversalStrategy {

    @Override
    public Set<String> traverse(String start, int depth, SchemaGraph graph) {
        Set<String> visited = new LinkedHashSet<>();
        dfs(start, depth, graph, visited, TraversalDirection.BIDIRECTIONAL);
        return visited;
    }

    @Override
    public Set<String> traverse(String start, int depth, SchemaGraph graph, TraversalDirection direction) {
        Set<String> visited = new LinkedHashSet<>();
        dfs(start, depth, graph, visited, direction);
        return visited;
    }

    private void dfs(String current,
                     int depth,
                     SchemaGraph graph,
                     Set<String> visited,
                     TraversalDirection direction) {

        if (depth < 0 || visited.contains(current)) {
            return;
        }

        visited.add(current);

        if (direction == TraversalDirection.DEPENDENCIES_ONLY
                || direction == TraversalDirection.BIDIRECTIONAL) {

            for (String neighbor : graph.getDependencies(current)) {
                dfs(neighbor, depth - 1, graph, visited, direction);
            }
        }

        if (direction == TraversalDirection.DEPENDENTS_ONLY
                || direction == TraversalDirection.BIDIRECTIONAL) {

            for (String neighbor : graph.getDependents(current)) {
                dfs(neighbor, depth - 1, graph, visited, direction);
            }
        }
    }
}