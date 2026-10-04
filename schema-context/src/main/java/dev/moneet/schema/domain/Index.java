package dev.moneet.schema.domain;

import java.util.List;

public final class Index {
    String name;
    List<String> columns;
    boolean unique;

    public Index(String name, List<String> columns, boolean unique) {
        this.name = name;
        this.columns = columns;
        this.unique = unique;
    }

    public String getName() {
        return name;
    }

    public List<String> getColumns() {
        return columns;
    }

    public boolean isUnique() {
        return unique;
    }

    @Override
    public String toString() {
        return name + " " + columns +
                (unique ? " [UNIQUE]" : "");
    }
}
