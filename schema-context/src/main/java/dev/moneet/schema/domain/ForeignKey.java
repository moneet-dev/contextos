package dev.moneet.schema.domain;

public final class ForeignKey {
    String column;
    String referencedTable;
    String referencedColumn;

    public ForeignKey(String column, String referencedTable, String referencedColumn) {
        this.column = column;
        this.referencedTable = referencedTable;
        this.referencedColumn = referencedColumn;
    }

    public String getReferencedTable() {
        return referencedTable;
    }

    public String getColumn() {
        return column;
    }

    public String getReferencedColumn() {
        return referencedColumn;
    }

}
