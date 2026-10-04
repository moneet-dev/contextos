package dev.moneet.schema.graph;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.domain.Index;
import dev.moneet.schema.domain.PrimaryKey;
import dev.moneet.schema.domain.Table;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class JoinPathAnalyzer {

    public static List<JoinAnalysisResult> analyze(
            List<GraphEdge> path,
            DatabaseSchema schema) {

        Map<String, Table> tableMap = new HashMap<>();
        for (Table table : schema.getTables()) {
            tableMap.put(table.getName(), table);
        }

        List<JoinAnalysisResult> results = new ArrayList<>();

        for (GraphEdge edge : path) {

            Table fromTable = tableMap.get(edge.getFromTable());
            Table toTable = tableMap.get(edge.getToTable());

            boolean fromIndexed = isColumnIndexed(fromTable, edge.getFromColumn());
            boolean toIndexed = isColumnIndexed(toTable, edge.getToColumn());

            boolean fromPK = isPrimaryKey(fromTable, edge.getFromColumn());
            boolean toPK = isPrimaryKey(toTable, edge.getToColumn());

            JoinQuality quality = evaluate(fromIndexed, toIndexed, fromPK, toPK);

            results.add(new JoinAnalysisResult(edge, quality));
        }

        return results;
    }

    private static boolean isColumnIndexed(Table table, String column) {

        for (Index index : table.getIndexes()) {
            if (index.getColumns().contains(column)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPrimaryKey(Table table, String column) {

        for (PrimaryKey pk : table.getPrimaryKeys()) {
            if (pk.getColumnName().equals(column)) {
                return true;
            }
        }
        return false;
    }

    private static JoinQuality evaluate(boolean fromIndexed,
                                        boolean toIndexed,
                                        boolean fromPK,
                                        boolean toPK) {

        if ((fromIndexed || fromPK) && (toIndexed || toPK)) {
            return JoinQuality.EXCELLENT;
        }

        if (fromIndexed || toIndexed) {
            return JoinQuality.GOOD;
        }

        return JoinQuality.WEAK;
    }
}