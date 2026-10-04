package dev.moneet.schema.jdbc;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TableExtractor {

    public List<String> extractTables(DatabaseMetaData metaData,
                                      String catalog,
                                      String schema) {

        List<String> tables = new ArrayList<>();

        try (ResultSet rs = metaData.getTables(
                catalog,
                schema,
                "%",
                new String[]{"TABLE"}
        )) {

            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to extract tables for catalog: "
                            + catalog + ", schema: " + schema, e);
        }

        return tables;
    }
}