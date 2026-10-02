package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolReference;
import dev.moneet.contextos.code.graph.CodeGraph;
import dev.moneet.contextos.core.graph.Direction;
import dev.moneet.contextos.core.graph.Reached;

import java.util.List;

/**
 * Context around the symbols matching a query: the neighbourhood within
 * {@code depth} hops, ranked, optionally capped at {@code maxItems}.
 */
public final class FocusedCodeStrategy implements CodeContextStrategy {

    private final String query;
    private final int depth;
    private final Direction direction;
    private final RenderMode mode;
    private final int maxItems;

    private final SymbolLookup lookup = new SymbolLookup();
    private final CodeContextRanker ranker = new CodeContextRanker();
    private final CodeRenderer renderer = new CodeRenderer();

    public FocusedCodeStrategy(String query, int depth) {
        this(query, depth, Direction.BOTH, RenderMode.SIGNATURES, Integer.MAX_VALUE);
    }

    public FocusedCodeStrategy(String query, int depth, Direction direction, RenderMode mode, int maxItems) {
        this.query = query;
        this.depth = depth;
        this.direction = direction;
        this.mode = mode;
        this.maxItems = maxItems;
    }

    @Override
    public CodeContext generate(CodeRepository repository, CodeGraph graph) {

        List<String> targets = lookup.find(repository, query).stream()
                .map(Symbol::id)
                .toList();

        if (targets.isEmpty()) {
            throw new IllegalArgumentException("No symbol matches: " + query);
        }

        List<Reached<SymbolReference>> reached = graph.traverse(targets, depth, direction);

        List<CodeContextItem> items = ranker.rank(reached, repository).stream()
                .limit(maxItems)
                .toList();

        return new CodeContext(items, renderer.render(items, mode));
    }
}
