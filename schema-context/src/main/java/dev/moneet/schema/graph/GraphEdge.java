package dev.moneet.schema.graph;

import java.util.Objects;

public final class GraphEdge {

    private final String fromTable;
    private final String toTable;
    private final String fromColumn;
    private final String toColumn;

    public GraphEdge(String fromTable,
                     String toTable,
                     String fromColumn,
                     String toColumn) {

        this.fromTable = fromTable;
        this.toTable = toTable;
        this.fromColumn = fromColumn;
        this.toColumn = toColumn;
    }

    public String getFromTable() {
        return fromTable;
    }

    public String getToTable() {
        return toTable;
    }

    public String getFromColumn() {
        return fromColumn;
    }

    public String getToColumn() {
        return toColumn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GraphEdge)) return false;
        GraphEdge edge = (GraphEdge) o;
        return Objects.equals(fromTable, edge.fromTable) &&
                Objects.equals(toTable, edge.toTable) &&
                Objects.equals(fromColumn, edge.fromColumn) &&
                Objects.equals(toColumn, edge.toColumn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromTable, toTable, fromColumn, toColumn);
    }

    @Override
    public String toString() {
        return fromTable + "." + fromColumn +
                " → " +
                toTable + "." + toColumn;
    }
}