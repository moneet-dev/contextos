package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.Column;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ColumnExtractor {

    public List<Column> extract(DatabaseMetaData metaData,
                                String catalog,
                                String schema,
                                String tableName) {

        List<Column> columns = new ArrayList<>();

        try (ResultSet rs = metaData.getColumns(
                catalog,
                schema,
                tableName,
                "%"
        )) {

            while (rs.next()) {

                String name = rs.getString("COLUMN_NAME");
                String type = rs.getString("TYPE_NAME");

                boolean nullable =
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;

                columns.add(new Column(name, type, nullable));
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to extract columns for table: " + tableName, e);
        }

        return columns;
    }
}
