package dev.moneet.contextos.core.context;

/** Maximum size of a context package, in estimated tokens. */
public record ContextBudget(int maxTokens) {

    public ContextBudget {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive: " + maxTokens);
        }
    }

    public static ContextBudget tokens(int maxTokens) {
        return new ContextBudget(maxTokens);
    }

    public static ContextBudget unlimited() {
        return new ContextBudget(Integer.MAX_VALUE);
    }

    public boolean isUnlimited() {
        return maxTokens == Integer.MAX_VALUE;
    }
}
