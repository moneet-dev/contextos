package dev.moneet.schema.context;

import dev.moneet.schema.domain.Column;
import dev.moneet.schema.domain.Index;
import dev.moneet.schema.domain.PrimaryKey;
import dev.moneet.schema.domain.Table;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SchemaFormatter {

    public String format(List<Table> tables) {

        StringBuilder sb = new StringBuilder();

        for (Table table : tables) {
            appendTable(sb, table);
        }

        return sb.toString();
    }

    // =====================================================
    // Table Formatting
    // =====================================================

    private void appendTable(StringBuilder sb, Table table) {

        sb.append("Table ").append(table.getName()).append(":\n");

        Set<String> indexedColumns = collectIndexedColumns(table);
        Set<String> primaryKeyColumns = collectPrimaryKeys(table);

        for (Column column : table.getColumns()) {

            sb.append("  ").append(column.getName());

            if (primaryKeyColumns.contains(column.getName())) {
                sb.append(" (PK)");
            }

            if (indexedColumns.contains(column.getName())) {
                sb.append(" (INDEXED)");
            }

            table.getForeignKeys().stream()
                    .filter(fk -> fk.getColumn().equals(column.getName()))
                    .findFirst()
                    .ifPresent(fk ->
                            sb.append(" (FK -> ")
                                    .append(fk.getReferencedTable())
                                    .append(".")
                                    .append(fk.getReferencedColumn())
                                    .append(")")
                    );

            sb.append("\n");
        }

        appendIndexes(sb, table);

        sb.append("\n");
    }

    // =====================================================
    // Index Rendering
    // =====================================================

    private void appendIndexes(StringBuilder sb, Table table) {

        if (table.getIndexes().isEmpty()) {
            return;
        }

        sb.append("  Indexes:\n");

        for (Index index : table.getIndexes()) {

            sb.append("    - ")
                    .append(index.getName())
                    .append(" ")
                    .append(index.getColumns());

            if (index.isUnique()) {
                sb.append(" [UNIQUE]");
            }

            sb.append("\n");
        }
    }

    // =====================================================
    // Helpers
    // =====================================================

    private Set<String> collectIndexedColumns(Table table) {

        Set<String> indexed = new HashSet<>();

        for (Index index : table.getIndexes()) {
            indexed.addAll(index.getColumns());
        }

        return indexed;
    }

    private Set<String> collectPrimaryKeys(Table table) {

        Set<String> pks = new HashSet<>();

        for (PrimaryKey pk : table.getPrimaryKeys()) {
            pks.add(pk.getColumnName());
        }

        return pks;
    }
}