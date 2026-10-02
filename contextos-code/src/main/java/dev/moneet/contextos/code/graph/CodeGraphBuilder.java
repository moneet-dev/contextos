package dev.moneet.contextos.code.graph;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class CodeGraphBuilder {

    public CodeGraph build(CodeRepository repository) {

        Map<String, Symbol> symbols = new LinkedHashMap<>();
        Map<String, List<SymbolReference>> outgoing = new HashMap<>();
        Map<String, List<SymbolReference>> incoming = new HashMap<>();

        for (Symbol symbol : repository.getSymbols()) {
            symbols.put(symbol.id(), symbol);
            outgoing.put(symbol.id(), new ArrayList<>());
            incoming.put(symbol.id(), new ArrayList<>());
        }

        for (SymbolReference reference : repository.getReferences()) {
            if (symbols.containsKey(reference.sourceId()) && symbols.containsKey(reference.targetId())) {
                outgoing.get(reference.sourceId()).add(reference);
                incoming.get(reference.targetId()).add(reference);
            }
        }

        // Deterministic traversal order: by edge kind, then by the symbol at the other end
        return new CodeGraph(
                Collections.unmodifiableMap(symbols),
                makeImmutable(outgoing, SymbolReference::targetId),
                makeImmutable(incoming, SymbolReference::sourceId));
    }

    private Map<String, List<SymbolReference>> makeImmutable(Map<String, List<SymbolReference>> map,
                                                             Function<SymbolReference, String> otherEnd) {
        Comparator<SymbolReference> order = Comparator
                .comparing(SymbolReference::kind)
                .thenComparing(otherEnd);

        Map<String, List<SymbolReference>> result = new HashMap<>();
        for (Map.Entry<String, List<SymbolReference>> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue().stream().sorted(order).toList());
        }
        return Map.copyOf(result);
    }
}
