package dev.moneet.schema.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Table {

    private final String name;
    private final List<Column> columns;
    private final Set<PrimaryKey> primaryKeys;
    private final List<ForeignKey> foreignKeys;
    private final List<Index> indexes;

    public Table(String name,
                 List<Column> columns,
                 Set<PrimaryKey> primaryKeys,
                 List<ForeignKey> foreignKeys,
                 List<Index> indexes) {

        this.name = name;
        this.columns = List.copyOf(columns);
        this.primaryKeys = Set.copyOf(primaryKeys);
        this.foreignKeys = List.copyOf(foreignKeys);
        this.indexes = List.copyOf(indexes);
    }

    public String getName() {
        return name;
    }

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public Set<PrimaryKey> getPrimaryKeys() {
        return Collections.unmodifiableSet(primaryKeys);
    }

    public List<ForeignKey> getForeignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    public List<Index> getIndexes() {
        return Collections.unmodifiableList(indexes);
    }

    // Convenience helper (optional, but useful)
    public Set<String> getPrimaryKeyColumnNames() {
        Set<String> result = new LinkedHashSet<>();
        for (PrimaryKey pk : primaryKeys) {
            result.add(pk.getColumnName());
        }
        return result;
    }
}