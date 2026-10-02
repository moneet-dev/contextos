package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.graph.CodeGraph;

public interface CodeContextStrategy {

    CodeContext generate(CodeRepository repository, CodeGraph graph);
}
