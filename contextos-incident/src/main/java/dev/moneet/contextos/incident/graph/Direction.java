package dev.moneet.contextos.incident.graph;

public enum Direction {
    /** What a service depends on (calls, queries, publishes to): candidate causes. */
    DEPENDENCIES,
    /** What depends on a service (callers, consumers): blast radius. */
    DEPENDENTS,
    BOTH;

    /** Dependency edges point from dependent to dependency, so DEPENDENCIES follows them forwards. */
    public dev.moneet.contextos.core.graph.Direction toCore() {
        return switch (this) {
            case DEPENDENCIES -> dev.moneet.contextos.core.graph.Direction.OUTGOING;
            case DEPENDENTS -> dev.moneet.contextos.core.graph.Direction.INCOMING;
            case BOTH -> dev.moneet.contextos.core.graph.Direction.BOTH;
        };
    }
}
