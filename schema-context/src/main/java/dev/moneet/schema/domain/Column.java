package dev.moneet.schema.domain;

public final class Column {
    String name;
    String type;
    boolean nullable;

    public Column(String name, String type, boolean nullable) {
        this.name = name;
        this.type = type;
        this.nullable = nullable;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isNullable() {
        return nullable;
    }
}
