package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.Index;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndexExtractor {

    public List<Index> extract(DatabaseMetaData metaData,
                               String catalog,
                               String schema,
                               String tableName) {

        Map<String, List<String>> indexColumns = new HashMap<>();
        Map<String, Boolean> uniqueness = new HashMap<>();

        try (ResultSet rs = metaData.getIndexInfo(
                catalog,
                schema,
                tableName,
                false,
                false
        )) {

            while (rs.next()) {

                String indexName = rs.getString("INDEX_NAME");
                if (indexName == null) continue;

                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                String columnName = rs.getString("COLUMN_NAME");

                indexColumns
                        .computeIfAbsent(indexName, k -> new ArrayList<>())
                        .add(columnName);

                uniqueness.put(indexName, !nonUnique);
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Failed to extract indexes for table: " + tableName, e);
        }

        List<Index> indexes = new ArrayList<>();

        for (String name : indexColumns.keySet()) {
            indexes.add(new Index(
                    name,
                    indexColumns.get(name),
                    uniqueness.getOrDefault(name, false)
            ));
        }

        return indexes;
    }
}
