package dev.moneet.contextos.code.domain;

/**
 * Relationship between two symbols, always read as {@code source -> target}.
 * The phrases describe a symbol reached by traversing the edge forwards
 * (it is the target) or backwards (it is the source).
 */
public enum ReferenceKind {
    CONTAINS("member of", "declares"),
    CALLS("called by", "calls"),
    REFERENCES("referenced by", "references"),
    EXTENDS("extended by", "extends"),
    IMPLEMENTS("implemented by", "implements"),
    OVERRIDES("overridden by", "overrides"),
    IMPORTS("imported by", "imports");

    private final String targetPhrase;
    private final String sourcePhrase;

    ReferenceKind(String targetPhrase, String sourcePhrase) {
        this.targetPhrase = targetPhrase;
        this.sourcePhrase = sourcePhrase;
    }

    /** How the target relates to the source, e.g. "called by". */
    public String targetPhrase() {
        return targetPhrase;
    }

    /** How the source relates to the target, e.g. "calls". */
    public String sourcePhrase() {
        return sourcePhrase;
    }
}
