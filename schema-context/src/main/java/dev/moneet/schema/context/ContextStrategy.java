package dev.moneet.schema.context;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.graph.SchemaGraph;

public interface ContextStrategy {
    String generate(DatabaseSchema schema, SchemaGraph graph);
}
