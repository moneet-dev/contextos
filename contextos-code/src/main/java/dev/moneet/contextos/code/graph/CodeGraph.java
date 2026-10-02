package dev.moneet.contextos.code.graph;

import dev.moneet.contextos.code.domain.ReferenceKind;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed, directed symbol graph with lookups in both directions.
 */
public final class CodeGraph {

    private final Map<String, Symbol> symbols;
    private final Map<String, List<SymbolReference>> outgoing;
    private final Map<String, List<SymbolReference>> incoming;

    public CodeGraph(Map<String, Symbol> symbols,
                     Map<String, List<SymbolReference>> outgoing,
                     Map<String, List<SymbolReference>> incoming) {
        this.symbols = symbols;
        this.outgoing = outgoing;
        this.incoming = incoming;
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
        return outgoing.getOrDefault(id, List.of());
    }

    /** Edges where {@code id} is the target. */
    public List<SymbolReference> getIncoming(String id) {
        return incoming.getOrDefault(id, List.of());
    }

    public List<ReachedSymbol> traverse(Collection<String> startIds, int maxDepth, Direction direction) {
        return traverse(startIds, maxDepth, direction, EnumSet.allOf(ReferenceKind.class));
    }

    /**
     * Breadth-first traversal from {@code startIds}, following only {@code kinds}
     * edges, up to {@code maxDepth} hops. Every reachable symbol is returned once,
     * with its shortest distance, in order of distance. Start symbols have distance 0.
     */
    public List<ReachedSymbol> traverse(Collection<String> startIds,
                                        int maxDepth,
                                        Direction direction,
                                        Set<ReferenceKind> kinds) {

        Map<String, ReachedSymbol> reached = new LinkedHashMap<>();
        Deque<ReachedSymbol> queue = new ArrayDeque<>();

        for (String start : startIds) {
            if (containsSymbol(start) && !reached.containsKey(start)) {
                ReachedSymbol origin = new ReachedSymbol(start, 0, List.of());
                reached.put(start, origin);
                queue.add(origin);
            }
        }

        while (!queue.isEmpty()) {
            ReachedSymbol current = queue.poll();
            if (current.distance() >= maxDepth) {
                continue;
            }

            for (SymbolReference edge : edges(current.symbolId(), direction)) {
                String next = edge.other(current.symbolId());
                if (!kinds.contains(edge.kind()) || reached.containsKey(next)) {
                    continue;
                }

                List<SymbolReference> path = new ArrayList<>(current.path());
                path.add(edge);
                ReachedSymbol step = new ReachedSymbol(next, current.distance() + 1, path);
                reached.put(next, step);
                queue.add(step);
            }
        }

        return List.copyOf(reached.values());
    }

    private List<SymbolReference> edges(String id, Direction direction) {
        return switch (direction) {
            case OUTGOING -> getOutgoing(id);
            case INCOMING -> getIncoming(id);
            case BOTH -> {
                List<SymbolReference> both = new ArrayList<>(getOutgoing(id));
                both.addAll(getIncoming(id));
                yield both;
            }
        };
    }

    @Override
    public String toString() {
        return "CodeGraph{" +
                "symbols=" + symbols.size() +
                ", edges=" + outgoing.values().stream().mapToInt(List::size).sum() +
                '}';
    }
}
