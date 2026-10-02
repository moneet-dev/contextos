package dev.moneet.contextos.incident.domain;

/**
 * Relationship between two services, always read as {@code from -> to}:
 * {@code from} depends on {@code to}. The phrases describe a service reached by
 * traversing the edge forwards (it is {@code to}) or backwards (it is {@code from}).
 */
public enum DependencyKind {
    CALLS("called by", "calls"),
    QUERIES("queried by", "queries"),
    PUBLISHES("published to by", "publishes to"),
    CONSUMES("consumed by", "consumes from");

    private final String toPhrase;
    private final String fromPhrase;

    DependencyKind(String toPhrase, String fromPhrase) {
        this.toPhrase = toPhrase;
        this.fromPhrase = fromPhrase;
    }

    /** How {@code to} relates to {@code from}, e.g. "called by". */
    public String toPhrase() {
        return toPhrase;
    }

    /** How {@code from} relates to {@code to}, e.g. "calls". */
    public String fromPhrase() {
        return fromPhrase;
    }
}
