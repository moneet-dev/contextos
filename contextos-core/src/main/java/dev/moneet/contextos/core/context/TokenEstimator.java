package dev.moneet.contextos.core.context;

public interface TokenEstimator {

    int estimate(String text);

    /**
     * Characters-per-token heuristic. About 4 characters per token is typical for
     * English and code with common LLM tokenizers; it is an estimate, not a count.
     */
    static TokenEstimator characters(double charactersPerToken) {
        if (charactersPerToken <= 0) {
            throw new IllegalArgumentException("charactersPerToken must be positive");
        }
        return text -> (int) Math.ceil(text.length() / charactersPerToken);
    }

    static TokenEstimator defaultEstimator() {
        return characters(4.0);
    }
}
