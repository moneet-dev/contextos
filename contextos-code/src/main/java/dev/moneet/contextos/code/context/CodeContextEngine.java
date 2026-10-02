package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.graph.CodeGraph;

public final class CodeContextEngine {

    private final CodeContextStrategy strategy;

    public CodeContextEngine(CodeContextStrategy strategy) {
        this.strategy = strategy;
    }

    public CodeContext generate(CodeRepository repository, CodeGraph graph) {
        return strategy.generate(repository, graph);
    }
}
