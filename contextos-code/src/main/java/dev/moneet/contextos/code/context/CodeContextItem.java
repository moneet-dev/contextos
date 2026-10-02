package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.DataAccess;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;

import java.util.List;

/**
 * One selected symbol and why it was selected: its score, graph distance from
 * the target, a human-readable reason and the edge path that reached it.
 */
public record CodeContextItem(Symbol symbol,
                              double score,
                              int distance,
                              String reason,
                              List<SymbolReference> path,
                              List<DataAccess> dataAccess) {

    public CodeContextItem {
        path = List.copyOf(path);
        dataAccess = List.copyOf(dataAccess);
    }
}
