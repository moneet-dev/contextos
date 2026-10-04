package dev.moneet.schema.graph;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.domain.ForeignKey;
import dev.moneet.schema.domain.Table;

import java.util.*;

public final class SchemaGraphBuilder {


    public SchemaGraph build(DatabaseSchema schema) {

        Map<String, Set<GraphEdge>> outgoing = new LinkedHashMap<>();
        Map<String, Set<GraphEdge>> incoming = new LinkedHashMap<>();

        for (Table table : schema.getTables()) {
            outgoing.put(table.getName(), new LinkedHashSet<>());
            incoming.put(table.getName(), new LinkedHashSet<>());
        }

        for (Table table : schema.getTables()) {

            for (ForeignKey fk : table.getForeignKeys()) {

                GraphEdge edge = new GraphEdge(
                        table.getName(),
                        fk.getReferencedTable(),
                        fk.getColumn(),
                        fk.getReferencedColumn()
                );

                outgoing.get(table.getName()).add(edge);
                incoming.get(fk.getReferencedTable()).add(edge);
            }
        }

        return new SchemaGraph(outgoing, incoming);
    }

    private Map<String, Set<String>> makeImmutable(Map<String, Set<String>> map) {
        Map<String, Set<String>> result = new HashMap<>();

        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            result.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }

        return Map.copyOf(result);
    }
}
