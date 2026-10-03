package dev.moneet.contextos.eval;

import java.util.Locale;

/** What the model is given about an incident; every condition gets the same token budget. */
public enum Condition {
    /** Raw telemetry records from the incident window, in time order, cut off at the budget. */
    RAW_TELEMETRY("raw telemetry"),
    /** ContextOS incident context: ranked evidence with provenance. */
    INCIDENT_CONTEXT("incident context"),
    /** ContextOS cross-domain context: incident evidence plus linked code and database tables. */
    CROSS_DOMAIN("cross-domain context");

    private final String label;

    Condition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Condition parse(String name) {
        return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
