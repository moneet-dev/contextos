package dev.moneet.contextos.incident.graph;

public enum Direction {
    /** What a service depends on (calls, queries, publishes to): candidate causes. */
    DEPENDENCIES,
    /** What depends on a service (callers, consumers): blast radius. */
    DEPENDENTS,
    BOTH
}
