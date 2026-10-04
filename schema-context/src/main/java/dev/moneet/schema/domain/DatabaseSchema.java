package dev.moneet.schema.domain;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DatabaseSchema {

    private final List<Table> tables;

    public DatabaseSchema(List<Table> tables) {
        Objects.requireNonNull(tables, "tables must not be null");
        this.tables = List.copyOf(tables); // defensive copy (immutable)
    }

    public List<Table> getTables() {
        return Collections.unmodifiableList(tables);
    }

    public Table getTable(String name) {
        return tables.stream()
                .filter(t -> t.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Table not found: " + name));
    }

    public boolean containsTable(String name) {
        return tables.stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(name));
    }

    @Override
    public String toString() {
        return "DatabaseSchema{" +
                "tables=" + tables +
                '}';
    }
}
