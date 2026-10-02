package dev.moneet.contextos.code.context;

import java.util.List;

/**
 * Result of a code context strategy: the ranked items (highest score first)
 * and their rendered, LLM-ready text.
 */
public record CodeContext(List<CodeContextItem> items, String rendered) {

    public CodeContext {
        items = List.copyOf(items);
    }

    @Override
    public String toString() {
        return rendered;
    }
}
