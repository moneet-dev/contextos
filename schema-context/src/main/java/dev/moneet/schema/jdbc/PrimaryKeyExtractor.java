package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.PrimaryKey;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

public class PrimaryKeyExtractor {

    public Set<PrimaryKey> extract(DatabaseMetaData metaData,
                                   String catalog,
                                   String schema,
                                   String tableName) {

        Set<PrimaryKey> primaryKeys = new LinkedHashSet<>();

        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {

            while (rs.next()) {

                String columnName = rs.getString("COLUMN_NAME");

                primaryKeys.add(new PrimaryKey(columnName));
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to extract primary keys for table: " + tableName, e);
        }

        return primaryKeys;
    }
}
