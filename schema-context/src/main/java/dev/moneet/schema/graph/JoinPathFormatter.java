package dev.moneet.schema.graph;

import dev.moneet.schema.domain.DatabaseSchema;

import java.util.List;

public final class JoinPathFormatter {

    public static String formatWithQuality(
            List<GraphEdge> path,
            DatabaseSchema schema) {

        StringBuilder sb = new StringBuilder();
        sb.append("Join Path:\n");

        List<JoinAnalysisResult> results =
                JoinPathAnalyzer.analyze(path, schema);

        for (JoinAnalysisResult result : results) {

            GraphEdge edge = result.getEdge();

            sb.append(" - ")
                    .append(edge.getFromTable())
                    .append(".")
                    .append(edge.getFromColumn())
                    .append(" = ")
                    .append(edge.getToTable())
                    .append(".")
                    .append(edge.getToColumn())
                    .append("  [")
                    .append(result.getQuality())
                    .append("]\n");
        }

        return sb.toString();
    }
}