package dev.moneet.schema.graph;

import java.util.*;

public final class SchemaGraph {

    private final Map<String, Set<GraphEdge>> outgoing;
    private final Map<String, Set<GraphEdge>> incoming;

    public SchemaGraph(Map<String, Set<GraphEdge>> outgoing,
                       Map<String, Set<GraphEdge>> incoming) {

        this.outgoing = new LinkedHashMap<>(outgoing);
        this.incoming = new LinkedHashMap<>(incoming);
    }

    // =====================================================
    // Basic Accessors
    // =====================================================

    public Set<String> getTables() {
        return Collections.unmodifiableSet(outgoing.keySet());
    }

    public Set<GraphEdge> getOutgoingEdges(String table) {
        return outgoing.getOrDefault(table, Set.of());
    }

    public Set<GraphEdge> getIncomingEdges(String table) {
        return incoming.getOrDefault(table, Set.of());
    }

    // Backward-compatible helpers

    public Set<String> getDependencies(String table) {
        Set<String> deps = new LinkedHashSet<>();
        for (GraphEdge edge : getOutgoingEdges(table)) {
            deps.add(edge.getToTable());
        }
        return deps;
    }

    public Set<String> getDependents(String table) {
        Set<String> deps = new LinkedHashSet<>();
        for (GraphEdge edge : getIncomingEdges(table)) {
            deps.add(edge.getFromTable());
        }
        return deps;
    }

    // =====================================================
    // Root & Leaf
    // =====================================================

    public Set<String> getRootTables() {
        Set<String> roots = new LinkedHashSet<>();
        for (String table : getTables()) {
            if (getOutgoingEdges(table).isEmpty()) {
                roots.add(table);
            }
        }
        return roots;
    }

    public Set<String> getLeafTables() {
        Set<String> leaves = new LinkedHashSet<>();
        for (String table : getTables()) {
            if (getIncomingEdges(table).isEmpty()) {
                leaves.add(table);
            }
        }
        return leaves;
    }

    // =====================================================
    // Traversal
    // =====================================================

    public Set<String> getSubgraph(String start, int depth) {
        return traverse(start,
                depth,
                new DfsTraversalStrategy(),
                TraversalDirection.BIDIRECTIONAL);
    }

    public Set<String> traverse(String start,
                                int depth,
                                TraversalStrategy strategy) {
        return strategy.traverse(start,
                depth,
                this,
                TraversalDirection.BIDIRECTIONAL);
    }

    public Set<String> traverse(String start,
                                int depth,
                                TraversalStrategy strategy,
                                TraversalDirection direction) {
        return strategy.traverse(start, depth, this, direction);
    }

    // =====================================================
    // BFS Level Grouping (Bidirectional)
    // =====================================================

    public Map<Integer, Set<String>> getLevels(String start) {

        Map<Integer, Set<String>> levels = new LinkedHashMap<>();
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(start);
        visited.add(start);

        int depth = 0;

        while (!queue.isEmpty()) {

            int size = queue.size();
            Set<String> currentLevel = new LinkedHashSet<>();

            for (int i = 0; i < size; i++) {

                String current = queue.poll();
                currentLevel.add(current);

                for (String neighbor : getNeighbors(current,
                        TraversalDirection.BIDIRECTIONAL)) {

                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            levels.put(depth++, currentLevel);
        }

        return levels;
    }

    // =====================================================
    // Distance Map (Bidirectional)
    // =====================================================

    public Map<String, Integer> getDistances(String start) {

        Map<String, Integer> distances = new LinkedHashMap<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(start);
        distances.put(start, 0);

        while (!queue.isEmpty()) {

            String current = queue.poll();
            int currentDistance = distances.get(current);

            for (String neighbor : getNeighbors(current,
                    TraversalDirection.BIDIRECTIONAL)) {

                if (!distances.containsKey(neighbor)) {
                    distances.put(neighbor, currentDistance + 1);
                    queue.add(neighbor);
                }
            }
        }

        return distances;
    }

    // =====================================================
    // Shortest Path (Edge Reconstruction)
    // =====================================================

    public List<GraphEdge> getShortestPathEdges(String from,
                                                String to,
                                                TraversalDirection direction) {

        Map<String, GraphEdge> parentEdge = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {

            String current = queue.poll();

            if (current.equals(to)) break;

            for (GraphEdge edge : getNeighborEdges(current, direction)) {

                String neighbor =
                        edge.getFromTable().equals(current)
                                ? edge.getToTable()
                                : edge.getFromTable();

                if (visited.add(neighbor)) {
                    parentEdge.put(neighbor, edge);
                    queue.add(neighbor);
                }
            }
        }

        if (!parentEdge.containsKey(to)) {
            return List.of();
        }

        List<GraphEdge> path = new ArrayList<>();
        String current = to;

        while (!current.equals(from)) {
            GraphEdge edge = parentEdge.get(current);
            path.add(edge);

            current =
                    edge.getFromTable().equals(current)
                            ? edge.getToTable()
                            : edge.getFromTable();
        }

        Collections.reverse(path);
        return path;
    }

    // =====================================================
    // Tree Rendering
    // =====================================================

    public String toTree() {

        StringBuilder sb = new StringBuilder();

        for (String root : getRootTables()) {
            buildTree(root, sb, "", new LinkedHashSet<>());
        }

        return sb.toString();
    }

    private void buildTree(String current,
                           StringBuilder sb,
                           String indent,
                           Set<String> path) {

        sb.append(indent).append(current).append("\n");

        if (path.contains(current)) {
            sb.append(indent).append("  (cycle detected)\n");
            return;
        }

        Set<String> newPath = new LinkedHashSet<>(path);
        newPath.add(current);

        for (String dependent : getDependents(current)) {
            buildTree(dependent, sb, indent + "  ", newPath);
        }
    }

    // =====================================================
    // Internal Helpers
    // =====================================================

    private Set<String> getNeighbors(String table,
                                     TraversalDirection direction) {

        Set<String> neighbors = new LinkedHashSet<>();

        if (direction == TraversalDirection.DEPENDENCIES_ONLY
                || direction == TraversalDirection.BIDIRECTIONAL) {
            for (GraphEdge edge : getOutgoingEdges(table)) {
                neighbors.add(edge.getToTable());
            }
        }

        if (direction == TraversalDirection.DEPENDENTS_ONLY
                || direction == TraversalDirection.BIDIRECTIONAL) {
            for (GraphEdge edge : getIncomingEdges(table)) {
                neighbors.add(edge.getFromTable());
            }
        }

        return neighbors;
    }

    private Set<GraphEdge> getNeighborEdges(String table,
                                            TraversalDirection direction) {

        Set<GraphEdge> edges = new LinkedHashSet<>();

        if (direction == TraversalDirection.DEPENDENCIES_ONLY
                || direction == TraversalDirection.BIDIRECTIONAL) {
            edges.addAll(getOutgoingEdges(table));
        }

        if (direction == TraversalDirection.DEPENDENTS_ONLY
                || direction == TraversalDirection.BIDIRECTIONAL) {
            edges.addAll(getIncomingEdges(table));
        }

        return edges;
    }

    @Override
    public String toString() {
        return "SchemaGraph{" +
                "tables=" + outgoing.keySet() +
                '}';
    }
}