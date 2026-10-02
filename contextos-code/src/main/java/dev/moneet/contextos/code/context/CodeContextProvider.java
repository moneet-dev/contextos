package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.DataAccess;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.graph.CodeGraph;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextProvider;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.core.graph.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ContextOS provider for source code. The request target is a symbol query
 * (see {@link SymbolLookup}); items are the ranked neighbourhood produced by
 * {@link FocusedCodeStrategy}.
 *
 * <p>Item attributes: {@code symbol}, {@code file}, {@code line}, and, when the
 * symbol touches the database, {@code tables} (comma-separated names as written
 * in code; JPQL queries name entities, not tables).
 */
public final class CodeContextProvider implements ContextProvider {

    public static final String DOMAIN = "code";

    private final CodeRepository repository;
    private final CodeGraph graph;
    private final Direction direction;
    private final RenderMode mode;
    private final CodeRenderer renderer = new CodeRenderer();

    public CodeContextProvider(CodeRepository repository, CodeGraph graph) {
        this(repository, graph, Direction.BOTH, RenderMode.SIGNATURES);
    }

    public CodeContextProvider(CodeRepository repository, CodeGraph graph, Direction direction, RenderMode mode) {
        this.repository = repository;
        this.graph = graph;
        this.direction = direction;
        this.mode = mode;
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public List<ContextItem> collect(ContextRequest request) {
        CodeContext context = new FocusedCodeStrategy(request.target(), request.depth(), direction, mode,
                Integer.MAX_VALUE).generate(repository, graph);

        return context.items().stream().map(this::toItem).toList();
    }

    private ContextItem toItem(CodeContextItem item) {
        Symbol symbol = item.symbol();

        List<String> provenance = new ArrayList<>();
        provenance.add(symbol.location().toString());
        if (item.distance() > 1) {
            provenance.add("via " + CodeRenderer.path(item, this::displayName));
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("symbol", symbol.id());
        attributes.put("file", symbol.location().file());
        attributes.put("line", String.valueOf(symbol.location().startLine()));

        String reason = item.reason();
        if (!item.dataAccess().isEmpty()) {
            attributes.put("tables", item.dataAccess().stream()
                    .map(DataAccess::name).distinct().collect(Collectors.joining(",")));
            reason += "; data access: " + item.dataAccess().stream()
                    .map(Object::toString).distinct().collect(Collectors.joining(", "));
        }

        return new ContextItem(DOMAIN + ":" + symbol.id(), DOMAIN, symbol.kind().name(), symbol.displayName(),
                renderer.text(item, mode), item.score(), reason, provenance, attributes);
    }

    private String displayName(String id) {
        return repository.containsSymbol(id) ? repository.getSymbol(id).displayName() : id;
    }
}
