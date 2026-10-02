package dev.moneet.contextos.code.graph;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;
import dev.moneet.contextos.core.graph.TypedGraph;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CodeGraphBuilder {

    public CodeGraph build(CodeRepository repository) {

        Map<String, Symbol> symbols = new LinkedHashMap<>();
        for (Symbol symbol : repository.getSymbols()) {
            symbols.put(symbol.id(), symbol);
        }

        // Deterministic traversal order: by edge kind, then by the symbol at the other end
        TypedGraph<SymbolReference> graph = TypedGraph.of(symbols.keySet(), repository.getReferences(),
                Comparator.comparing(SymbolReference::kind));

        return new CodeGraph(Collections.unmodifiableMap(symbols), graph);
    }
}
