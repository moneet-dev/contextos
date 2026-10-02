package dev.moneet.contextos.code.domain;

public enum SymbolKind {
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    ANNOTATION,
    CONSTRUCTOR,
    METHOD,
    FIELD;

    public boolean isType() {
        return this == CLASS || this == INTERFACE || this == ENUM
                || this == RECORD || this == ANNOTATION;
    }
}
