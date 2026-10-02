package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.ReferenceKind;
import dev.moneet.contextos.code.domain.SymbolReference;
import dev.moneet.contextos.code.graph.ReachedSymbol;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scores reached symbols by the product of edge weights along their path, so
 * relevance decays with every hop and weak relationships (imports, containment)
 * decay faster than calls. Start symbols score 1.0.
 */
public final class CodeContextRanker {

    private static final Map<ReferenceKind, Double> DEFAULT_WEIGHTS = new EnumMap<>(Map.of(
            ReferenceKind.CALLS, 0.9,
            ReferenceKind.OVERRIDES, 0.85,
            ReferenceKind.EXTENDS, 0.8,
            ReferenceKind.IMPLEMENTS, 0.8,
            ReferenceKind.REFERENCES, 0.7,
            ReferenceKind.CONTAINS, 0.6,
            ReferenceKind.IMPORTS, 0.3
    ));

    private final Map<ReferenceKind, Double> weights;

    public CodeContextRanker() {
        this(DEFAULT_WEIGHTS);
    }

    public CodeContextRanker(Map<ReferenceKind, Double> weights) {
        this.weights = new EnumMap<>(weights);
    }

    /** Items ordered by score (descending), then distance, then id. */
    public List<CodeContextItem> rank(List<ReachedSymbol> reached, CodeRepository repository) {
        List<CodeContextItem> items = new ArrayList<>();

        for (ReachedSymbol r : reached) {
            double score = 1.0;
            for (SymbolReference edge : r.path()) {
                score *= weights.getOrDefault(edge.kind(), 0.5);
            }

            items.add(new CodeContextItem(
                    repository.getSymbol(r.symbolId()),
                    score,
                    r.distance(),
                    reason(r, repository),
                    r.path(),
                    repository.getDataAccess(r.symbolId())));
        }

        items.sort(Comparator.comparingDouble(CodeContextItem::score).reversed()
                .thenComparingInt(CodeContextItem::distance)
                .thenComparing(item -> item.symbol().id()));
        return items;
    }

    /** E.g. "called by PaymentController#pay(PaymentRequest)". */
    private static String reason(ReachedSymbol reached, CodeRepository repository) {
        if (reached.lastEdge().isEmpty()) {
            return "target";
        }
        SymbolReference edge = reached.lastEdge().get();

        if (edge.targetId().equals(reached.symbolId())) {
            return edge.kind().targetPhrase() + " "
                    + repository.getSymbol(edge.sourceId()).displayName();
        }
        return edge.kind().sourcePhrase() + " "
                + repository.getSymbol(edge.targetId()).displayName();
    }
}
