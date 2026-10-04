package dev.moneet.schema.context;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.graph.SchemaGraph;

public final class FullSchemaStrategy implements ContextStrategy {

    private final SchemaFormatter formatter = new SchemaFormatter();

    @Override
    public String generate(DatabaseSchema schema, SchemaGraph graph) {
        return formatter.format(schema.getTables());
    }
}