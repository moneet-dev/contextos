package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.graph.CodeGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Outline of the whole repository: every symbol's signature, unranked.
 */
public final class FullCodeStrategy implements CodeContextStrategy {

    private final CodeRenderer renderer = new CodeRenderer();

    @Override
    public CodeContext generate(CodeRepository repository, CodeGraph graph) {
        List<CodeContextItem> items = new ArrayList<>();

        for (Symbol symbol : repository.getSymbols()) {
            items.add(new CodeContextItem(symbol, 1.0, 0, "repository outline",
                    List.of(), repository.getDataAccess(symbol.id())));
        }

        return new CodeContext(items, renderer.render(items, RenderMode.SIGNATURES));
    }
}
