package dev.moneet.contextos.core.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fits ranked items into a budget. Items are considered from highest score to
 * lowest (ties keep their input order); each is included if it fits in the
 * remaining budget and omitted otherwise, so a smaller, lower-ranked item can
 * still use space a larger one could not. An item's cost includes the domain
 * header it introduces, so the rendered package never exceeds the budget.
 */
public final class ContextPackager {

    private final TokenEstimator estimator;
    private final ContextPackageRenderer renderer;

    public ContextPackager() {
        this(TokenEstimator.defaultEstimator(), new ContextPackageRenderer());
    }

    public ContextPackager(TokenEstimator estimator, ContextPackageRenderer renderer) {
        this.estimator = estimator;
        this.renderer = renderer;
    }

    public ContextPackage pack(ContextRequest request, List<ContextItem> items) {
        List<ContextItem> ranked = items.stream()
                .sorted(Comparator.comparingDouble(ContextItem::score).reversed())
                .toList();

        long remaining = request.budget().maxTokens();
        Set<String> domains = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<ContextPackage.Entry> included = new ArrayList<>();
        List<ContextPackage.Entry> omitted = new ArrayList<>();

        for (ContextItem item : ranked) {
            if (!ids.add(item.id())) {
                continue; // the same item from two providers is included once
            }

            int cost = estimator.estimate(renderer.item(item))
                    + (domains.contains(item.domain()) ? 0 : estimator.estimate(renderer.domainHeader(item.domain())));

            if (cost <= remaining) {
                included.add(new ContextPackage.Entry(item, cost));
                domains.add(item.domain());
                remaining -= cost;
            } else {
                omitted.add(new ContextPackage.Entry(item, cost));
            }
        }

        String rendered = renderer.render(included.stream().map(ContextPackage.Entry::item).toList());
        return new ContextPackage(request, included, omitted, estimator.estimate(rendered), rendered);
    }
}
