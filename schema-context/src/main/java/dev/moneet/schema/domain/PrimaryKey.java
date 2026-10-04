package dev.moneet.schema.domain;

import java.util.Objects;

public final class PrimaryKey {

    private final String columnName;

    public PrimaryKey(String columnName) {
        this.columnName = Objects.requireNonNull(columnName,
                "columnName must not be null");
    }

    public String getColumnName() {
        return columnName;
    }

    @Override
    public String toString() {
        return columnName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrimaryKey)) return false;
        PrimaryKey that = (PrimaryKey) o;
        return columnName.equalsIgnoreCase(that.columnName);
    }

    @Override
    public int hashCode() {
        return columnName.toLowerCase().hashCode();
    }
}
