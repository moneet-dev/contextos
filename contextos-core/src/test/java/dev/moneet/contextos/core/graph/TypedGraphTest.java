package dev.moneet.contextos.core.graph;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TypedGraphTest {

    record E(String source, String target, String kind) implements Edge {
    }

    /*
     *   a -x-> b -x-> c -x-> d
     *   a -y-> c                (shortcut)
     *   e -y-> a
     *   a -x-> ghost            (dropped: unknown node)
     */
    private final TypedGraph<E> graph = TypedGraph.of(
            List.of("a", "b", "c", "d", "e"),
            List.of(new E("a", "b", "x"), new E("b", "c", "x"), new E("c", "d", "x"),
                    new E("a", "c", "y"), new E("e", "a", "y"), new E("a", "ghost", "x")),
            Comparator.comparing(E::kind));

    @Test
    void shouldDropEdgesToUnknownNodesAndSortEdges() {
        assertEquals(5, graph.edgeCount());
        assertEquals(List.of("b", "c"), graph.outgoing("a").stream().map(E::target).toList());
        assertEquals(List.of(), graph.outgoing("d"));
    }

    @Test
    void shouldFindShortestDistancesWhereDepthFirstSearchWouldNot() {
        // DFS via b would mark c at depth 2 first; BFS reaches c at 1 and d at 2
        assertEquals(Map.of("a", 0, "b", 1, "c", 1, "d", 2),
                distances(graph.traverse(List.of("a"), 2, Direction.OUTGOING)));
    }

    @Test
    void shouldTraverseIncomingAndBoth() {
        assertEquals(Map.of("d", 0, "c", 1, "a", 2, "b", 2),
                distances(graph.traverse(List.of("d"), 2, Direction.INCOMING)));
        assertEquals(Map.of("a", 0, "b", 1, "c", 1, "e", 1),
                distances(graph.traverse(List.of("a"), 1, Direction.BOTH)));
    }

    @Test
    void shouldFilterEdges() {
        assertEquals(Map.of("a", 0, "b", 1, "c", 2),
                distances(graph.traverse(List.of("a"), 2, Direction.OUTGOING, e -> e.kind().equals("x"))));
    }

    @Test
    void shouldRecordPathsAndNodes() {
        Reached<E> e = graph.traverse(List.of("d"), 3, Direction.INCOMING).stream()
                .filter(r -> r.id().equals("e")).findFirst().orElseThrow();

        assertEquals(3, e.distance());
        assertEquals(List.of("d", "c", "a", "e"), e.nodes());
        assertEquals("y", e.lastEdge().orElseThrow().kind());
    }

    @Test
    void shouldHandleMultipleAndUnknownStarts() {
        assertEquals(Map.of("b", 0, "e", 0, "c", 1, "a", 1),
                distances(graph.traverse(List.of("b", "e", "missing"), 1, Direction.OUTGOING)));
        assertEquals(Map.of("a", 0), distances(graph.traverse(List.of("a"), 0, Direction.BOTH)));
    }

    private static Map<String, Integer> distances(List<Reached<E>> reached) {
        return reached.stream().collect(Collectors.toMap(Reached::id, Reached::distance));
    }
}
