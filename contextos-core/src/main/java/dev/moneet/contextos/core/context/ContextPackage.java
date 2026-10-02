package dev.moneet.contextos.core.context;

import java.util.List;

/**
 * Context assembled for one request under a budget.
 *
 * @param included    items that fit, highest score first, with their estimated cost
 * @param omitted     items left out because they did not fit, with their estimated cost
 * @param usedTokens  estimated size of {@code rendered}; within the budget for additive
 *                    estimators such as the characters-per-token default
 */
public record ContextPackage(ContextRequest request,
                             List<Entry> included,
                             List<Entry> omitted,
                             int usedTokens,
                             String rendered) {

    /** An item and its estimated cost in tokens, including any domain header it introduces. */
    public record Entry(ContextItem item, int tokens) {
    }

    public ContextPackage {
        included = List.copyOf(included);
        omitted = List.copyOf(omitted);
    }

    public List<ContextItem> items() {
        return included.stream().map(Entry::item).toList();
    }

    @Override
    public String toString() {
        return rendered;
    }
}
