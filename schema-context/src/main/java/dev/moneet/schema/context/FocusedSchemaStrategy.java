package dev.moneet.schema.context;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.domain.Table;
import dev.moneet.schema.graph.DfsTraversalStrategy;
import dev.moneet.schema.graph.SchemaGraph;
import dev.moneet.schema.graph.TraversalDirection;
import dev.moneet.schema.graph.TraversalStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class FocusedSchemaStrategy implements ContextStrategy {
    private final String tableName;
    private final int depth;
    private final TraversalStrategy traversalStrategy;
    private final TraversalDirection direction;

    private final SchemaFormatter formatter = new SchemaFormatter();

    public FocusedSchemaStrategy(String tableName, int depth) {
        this(tableName, depth, new DfsTraversalStrategy(), TraversalDirection.BIDIRECTIONAL);
    }

    public FocusedSchemaStrategy(String tableName,
                                 int depth,
                                 TraversalStrategy traversalStrategy) {

        this.tableName = tableName;
        this.depth = depth;
        this.traversalStrategy = traversalStrategy;
        this.direction = TraversalDirection.BIDIRECTIONAL;
    }

    public FocusedSchemaStrategy(String tableName,
                                 int depth,
                                 TraversalStrategy strategy,
                                 TraversalDirection direction) {

        this.tableName = tableName;
        this.depth = depth;
        this.traversalStrategy = strategy;
        this.direction = direction;
    }


    @Override
    public String generate(DatabaseSchema schema, SchemaGraph graph) {

        Set<String> relevantTables =
                graph.traverse(tableName,
                        depth,
                        traversalStrategy,
                        direction);

        List<Table> selected = new ArrayList<>();

        for (Table table : schema.getTables()) {
            if (relevantTables.contains(table.getName())) {
                selected.add(table);
            }
        }

        return new SchemaFormatter().format(selected);
    }
}