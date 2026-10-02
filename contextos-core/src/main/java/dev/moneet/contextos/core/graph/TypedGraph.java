package dev.moneet.contextos.core.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable directed graph over string node ids with typed edges, indexed in
 * both directions.
 */
public final class TypedGraph<E extends Edge> {

    private final Set<String> nodes;
    private final Map<String, List<E>> outgoing;
    private final Map<String, List<E>> incoming;

    private TypedGraph(Set<String> nodes, Map<String, List<E>> outgoing, Map<String, List<E>> incoming) {
        this.nodes = nodes;
        this.outgoing = outgoing;
        this.incoming = incoming;
    }

    /**
     * Builds a graph. Edges whose endpoints are not both in {@code nodes} are dropped.
     * Edge lists are sorted by {@code order}, then by the node at the other end, so
     * traversal is deterministic.
     */
    public static <E extends Edge> TypedGraph<E> of(Collection<String> nodes,
                                                    Collection<E> edges,
                                                    Comparator<? super E> order) {
        Set<String> nodeSet = new LinkedHashSet<>(nodes);
        Map<String, List<E>> outgoing = new HashMap<>();
        Map<String, List<E>> incoming = new HashMap<>();

        for (E edge : edges) {
            if (nodeSet.contains(edge.source()) && nodeSet.contains(edge.target())) {
                outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
                incoming.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
            }
        }

        Comparator<E> base = order::compare;
        Comparator<E> outgoingOrder = base.thenComparing(Edge::target);
        Comparator<E> incomingOrder = base.thenComparing(Edge::source);

        return new TypedGraph<>(
                Collections.unmodifiableSet(nodeSet),
                sorted(outgoing, outgoingOrder),
                sorted(incoming, incomingOrder));
    }

    public Set<String> nodes() {
        return nodes;
    }

    public boolean contains(String id) {
        return nodes.contains(id);
    }

    /** Edges where {@code id} is the source. */
    public List<E> outgoing(String id) {
        return outgoing.getOrDefault(id, List.of());
    }

    /** Edges where {@code id} is the target. */
    public List<E> incoming(String id) {
        return incoming.getOrDefault(id, List.of());
    }

    public int edgeCount() {
        return outgoing.values().stream().mapToInt(List::size).sum();
    }

    public List<Reached<E>> traverse(Collection<String> startIds, int maxDepth, Direction direction) {
        return traverse(startIds, maxDepth, direction, edge -> true);
    }

    /**
     * Breadth-first traversal from {@code startIds}, following only edges accepted
     * by {@code follow}, up to {@code maxDepth} hops. Every reachable node is
     * returned once, with its shortest distance, in order of distance. Start
     * nodes have distance 0; unknown start ids are ignored.
     */
    public List<Reached<E>> traverse(Collection<String> startIds,
                                     int maxDepth,
                                     Direction direction,
                                     Predicate<? super E> follow) {

        Map<String, Reached<E>> reached = new LinkedHashMap<>();
        Deque<Reached<E>> queue = new ArrayDeque<>();

        for (String start : startIds) {
            if (contains(start) && !reached.containsKey(start)) {
                Reached<E> origin = new Reached<>(start, 0, List.of());
                reached.put(start, origin);
                queue.add(origin);
            }
        }

        while (!queue.isEmpty()) {
            Reached<E> current = queue.poll();
            if (current.distance() >= maxDepth) {
                continue;
            }

            for (E edge : edges(current.id(), direction)) {
                String next = edge.other(current.id());
                if (!follow.test(edge) || reached.containsKey(next)) {
                    continue;
                }

                List<E> path = new ArrayList<>(current.path());
                path.add(edge);
                Reached<E> step = new Reached<>(next, current.distance() + 1, path);
                reached.put(next, step);
                queue.add(step);
            }
        }

        return List.copyOf(reached.values());
    }

    private List<E> edges(String id, Direction direction) {
        return switch (direction) {
            case OUTGOING -> outgoing(id);
            case INCOMING -> incoming(id);
            case BOTH -> {
                List<E> both = new ArrayList<>(outgoing(id));
                both.addAll(incoming(id));
                yield both;
            }
        };
    }

    private static <E> Map<String, List<E>> sorted(Map<String, List<E>> map, Comparator<E> order) {
        Map<String, List<E>> result = new HashMap<>();
        for (Map.Entry<String, List<E>> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream().sorted(order).toList());
        }
        return Map.copyOf(result);
    }

    @Override
    public String toString() {
        return "TypedGraph{" +
                "nodes=" + nodes.size() +
                ", edges=" + edgeCount() +
                '}';
    }
}
