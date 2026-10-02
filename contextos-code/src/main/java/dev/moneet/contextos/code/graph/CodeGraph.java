package dev.moneet.contextos.code.graph;

import dev.moneet.contextos.code.domain.ReferenceKind;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;
import dev.moneet.contextos.core.graph.Direction;
import dev.moneet.contextos.core.graph.Reached;
import dev.moneet.contextos.core.graph.TypedGraph;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed, directed symbol graph with lookups in both directions.
 */
public final class CodeGraph {

    private final Map<String, Symbol> symbols;
    private final TypedGraph<SymbolReference> graph;

    public CodeGraph(Map<String, Symbol> symbols, TypedGraph<SymbolReference> graph) {
        this.symbols = symbols;
        this.graph = graph;
    }

    public Collection<Symbol> getSymbols() {
        return symbols.values();
    }

    public boolean containsSymbol(String id) {
        return symbols.containsKey(id);
    }

    public Symbol getSymbol(String id) {
        Symbol symbol = symbols.get(id);
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol not found: " + id);
        }
        return symbol;
    }

    /** Edges where {@code id} is the source. */
    public List<SymbolReference> getOutgoing(String id) {
        return graph.outgoing(id);
    }

    /** Edges where {@code id} is the target. */
    public List<SymbolReference> getIncoming(String id) {
        return graph.incoming(id);
    }

    public List<Reached<SymbolReference>> traverse(Collection<String> startIds, int maxDepth, Direction direction) {
        return traverse(startIds, maxDepth, direction, EnumSet.allOf(ReferenceKind.class));
    }

    /**
     * Breadth-first traversal from {@code startIds}, following only {@code kinds}
     * edges, up to {@code maxDepth} hops. Every reachable symbol is returned once,
     * with its shortest distance, in order of distance. Start symbols have distance 0.
     */
    public List<Reached<SymbolReference>> traverse(Collection<String> startIds,
                                                   int maxDepth,
                                                   Direction direction,
                                                   Set<ReferenceKind> kinds) {
        return graph.traverse(startIds, maxDepth, direction, edge -> kinds.contains(edge.kind()));
    }

    @Override
    public String toString() {
        return "CodeGraph{" +
                "symbols=" + symbols.size() +
                ", edges=" + graph.edgeCount() +
                '}';
    }
}
