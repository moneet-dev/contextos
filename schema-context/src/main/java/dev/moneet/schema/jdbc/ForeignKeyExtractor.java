package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.ForeignKey;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ForeignKeyExtractor {

    public List<ForeignKey> extract(DatabaseMetaData metaData,
                                    String catalog,
                                    String schema,
                                    String tableName) {

        List<ForeignKey> foreignKeys = new ArrayList<>();

        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, tableName)) {

            while (rs.next()) {

                String column = rs.getString("FKCOLUMN_NAME");
                String referencedTable = rs.getString("PKTABLE_NAME");
                String referencedColumn = rs.getString("PKCOLUMN_NAME");

                foreignKeys.add(
                        new ForeignKey(column, referencedTable, referencedColumn)
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to extract foreign keys for table: " + tableName, e);
        }

        return foreignKeys;
    }
}
