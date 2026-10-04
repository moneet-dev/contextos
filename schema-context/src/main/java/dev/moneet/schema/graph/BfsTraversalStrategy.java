package dev.moneet.schema.graph;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import static dev.moneet.schema.graph.TraversalDirection.*;

public final class BfsTraversalStrategy implements TraversalStrategy {

    @Override
    public Set<String> traverse(String start, int depth, SchemaGraph graph) {

        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int currentDepth = 0;

        while (!queue.isEmpty() && currentDepth < depth) {

            int levelSize = queue.size();

            for (int i = 0; i < levelSize; i++) {

                String current = queue.poll();

                for (String neighbor : graph.getDependencies(current)) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }

                for (String neighbor : graph.getDependents(current)) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            currentDepth++;
        }

        return visited;
    }

    @Override
    public Set<String> traverse(String start, int depth, SchemaGraph graph, TraversalDirection direction) {

        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int currentDepth = 0;

        while (!queue.isEmpty() && currentDepth < depth) {

            int levelSize = queue.size();

            for (int i = 0; i < levelSize; i++) {

                String current = queue.poll();

                if (direction == DEPENDENCIES_ONLY || direction == BIDIRECTIONAL) {
                    for (String neighbor : graph.getDependencies(current)) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }

                if (direction == DEPENDENTS_ONLY || direction == BIDIRECTIONAL) {
                    for (String neighbor : graph.getDependents(current)) {
                        if (visited.add(neighbor)) {
                            queue.add(neighbor);
                        }
                    }
                }
            }

            currentDepth++;
        }

        return visited;
    }
}