package dev.moneet.contextos.core.graph;

/**
 * A directed edge between two node ids. Domain edge types (symbol references,
 * service dependencies, foreign keys) implement this to be traversed by {@link TypedGraph}.
 */
public interface Edge {

    String source();

    String target();

    /** The endpoint opposite {@code id}. */
    default String other(String id) {
        return source().equals(id) ? target() : source();
    }
}
