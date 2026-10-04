package dev.moneet.schema.context;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.graph.SchemaGraph;

public final class SchemaContextEngine {

    private final ContextStrategy strategy;

    public SchemaContextEngine(ContextStrategy strategy) {
        this.strategy = strategy;
    }

    public String generate(DatabaseSchema schema, SchemaGraph graph) {
        return strategy.generate(schema, graph);
    }
}