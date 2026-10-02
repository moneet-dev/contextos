package dev.moneet.contextos.code.graph;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.ReferenceKind;
import dev.moneet.contextos.code.domain.SourceLocation;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;
import dev.moneet.contextos.code.domain.SymbolReference;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CodeGraphTest {

    /*
     *   a -CALLS-> b -CALLS-> c -CALLS-> d
     *   a -CALLS-> c                          (shortcut: c is 1 hop from a, d is 2)
     *   e -IMPLEMENTS-> a
     *   f -IMPORTS-> d
     */
    private final CodeGraph graph = new CodeGraphBuilder().build(new CodeRepository("/",
            List.of(),
            List.of(symbol("a"), symbol("b"), symbol("c"), symbol("d"), symbol("e"), symbol("f")),
            List.of(
                    edge("a", "b", ReferenceKind.CALLS),
                    edge("b", "c", ReferenceKind.CALLS),
                    edge("c", "d", ReferenceKind.CALLS),
                    edge("a", "c", ReferenceKind.CALLS),
                    edge("e", "a", ReferenceKind.IMPLEMENTS),
                    edge("f", "d", ReferenceKind.IMPORTS)),
            List.of(),
            List.of()));

    @Test
    void shouldIndexEdgesInBothDirections() {
        assertEquals(2, graph.getOutgoing("a").size());
        assertEquals(List.of("a", "b"),
                graph.getIncoming("c").stream().map(SymbolReference::sourceId).toList());
    }

    @Test
    void shouldReturnShortestDistances() {
        Map<String, Integer> distances = distances(graph.traverse(List.of("a"), 3, Direction.OUTGOING));

        assertEquals(Map.of("a", 0, "b", 1, "c", 1, "d", 2), distances);
    }

    @Test
    void shouldRespectMaxDepth() {
        Map<String, Integer> distances = distances(graph.traverse(List.of("a"), 1, Direction.OUTGOING));

        assertEquals(Set.of("a", "b", "c"), distances.keySet());
    }

    @Test
    void shouldTraverseIncomingAndBothDirections() {
        assertEquals(Set.of("d", "c", "f", "a", "b"),
                distances(graph.traverse(List.of("d"), 2, Direction.INCOMING)).keySet());

        Map<String, Integer> both = distances(graph.traverse(List.of("a"), 1, Direction.BOTH));
        assertEquals(Map.of("a", 0, "b", 1, "c", 1, "e", 1), both);
    }

    @Test
    void shouldFilterByEdgeKind() {
        Map<String, Integer> distances = distances(graph.traverse(List.of("d"), 5, Direction.INCOMING,
                EnumSet.of(ReferenceKind.CALLS)));

        assertFalse(distances.containsKey("f"));
        assertFalse(distances.containsKey("e"));
    }

    @Test
    void shouldRecordPathFromStart() {
        ReachedSymbol e = graph.traverse(List.of("d"), 3, Direction.INCOMING).stream()
                .filter(r -> r.symbolId().equals("e"))
                .findFirst()
                .orElseThrow();

        assertEquals(3, e.distance());
        assertEquals(List.of("d", "c", "a", "e"), e.nodes());
        assertEquals(ReferenceKind.IMPLEMENTS, e.lastEdge().orElseThrow().kind());
    }

    @Test
    void shouldIgnoreUnknownStartSymbols() {
        assertTrue(graph.traverse(List.of("missing"), 2, Direction.BOTH).isEmpty());
    }

    private static Map<String, Integer> distances(List<ReachedSymbol> reached) {
        return reached.stream().collect(Collectors.toMap(ReachedSymbol::symbolId, ReachedSymbol::distance));
    }

    private static Symbol symbol(String id) {
        return new Symbol(id, SymbolKind.METHOD, id, id, null,
                new SourceLocation("X.java", 1, 1), List.of(), id + "()", id + "() {}");
    }

    private static SymbolReference edge(String source, String target, ReferenceKind kind) {
        return new SymbolReference(source, target, kind, 1);
    }
}
