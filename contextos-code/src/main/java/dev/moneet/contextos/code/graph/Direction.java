package dev.moneet.contextos.code.graph;

public enum Direction {
    /** Follow edges from source to target: what a symbol calls, references, extends. */
    OUTGOING,
    /** Follow edges from target to source: who calls, references, implements a symbol. */
    INCOMING,
    BOTH
}
