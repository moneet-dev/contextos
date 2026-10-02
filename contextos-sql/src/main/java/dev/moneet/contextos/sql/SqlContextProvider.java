package dev.moneet.contextos.sql;

import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextProvider;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.schema.context.SchemaFormatter;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.domain.Table;
import dev.moneet.schema.graph.BfsTraversalStrategy;
import dev.moneet.schema.graph.GraphEdge;
import dev.moneet.schema.graph.JoinAnalysisResult;
import dev.moneet.schema.graph.JoinPathAnalyzer;
import dev.moneet.schema.graph.JoinQuality;
import dev.moneet.schema.graph.SchemaGraph;
import dev.moneet.schema.graph.SchemaGraphBuilder;
import dev.moneet.schema.graph.TraversalDirection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ContextOS provider for a database schema loaded by SQL Schema Context. It is
 * built entirely on that library:
 * <ul>
 *   <li>tables within {@code depth} come from {@link SchemaGraph#traverse} with
 *       {@link BfsTraversalStrategy}</li>
 *   <li>each table's distance and join path come from
 *       {@link SchemaGraph#getShortestPathEdges}</li>
 *   <li>join quality comes from {@link JoinPathAnalyzer}</li>
 *   <li>tables are rendered by {@link SchemaFormatter}, including indexes</li>
 * </ul>
 *
 * <p>The request target is a table name (case-insensitive); a blank target
 * returns every table. Score is {@code 0.8^distance}. The default direction is
 * {@link TraversalDirection#BIDIRECTIONAL}, as in {@code FocusedSchemaStrategy}.
 *
 * <p>Item attributes: {@code table}, {@code distance}, and for related tables
 * {@code join} (join conditions from the target) and {@code joinQuality} (the
 * weakest join on the path).
 */
public final class SqlContextProvider implements ContextProvider {

    public static final String DOMAIN = "sql";

    private static final double DECAY = 0.8;

    private final DatabaseSchema schema;
    private final TraversalDirection direction;
    private final SchemaGraph graph;
    private final SchemaFormatter formatter = new SchemaFormatter();

    public SqlContextProvider(DatabaseSchema schema) {
        this(schema, TraversalDirection.BIDIRECTIONAL);
    }

    public SqlContextProvider(DatabaseSchema schema, TraversalDirection direction) {
        this.schema = schema;
        this.direction = direction;
        this.graph = new SchemaGraphBuilder().build(schema);
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public List<ContextItem> collect(ContextRequest request) {
        if (request.target().isBlank()) {
            return schema.getTables().stream()
                    .map(table -> toItem(table, List.of(), "full schema"))
                    .toList();
        }

        String target = schema.getTable(request.target()).getName();

        List<ContextItem> items = new ArrayList<>();
        for (String name : graph.traverse(target, request.depth(), new BfsTraversalStrategy(), direction)) {
            List<GraphEdge> path = name.equals(target)
                    ? List.of()
                    : graph.getShortestPathEdges(target, name, direction);
            items.add(toItem(schema.getTable(name), path, reason(target, name, path)));
        }
        items.sort(Comparator.comparingDouble(ContextItem::score).reversed());
        return items;
    }

    private ContextItem toItem(Table table, List<GraphEdge> path, String reason) {
        List<String> provenance = new ArrayList<>();
        provenance.add("table " + table.getName());

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("table", table.getName());
        attributes.put("distance", String.valueOf(path.size()));

        if (!path.isEmpty()) {
            List<JoinAnalysisResult> joins = JoinPathAnalyzer.analyze(path, schema);
            joins.forEach(join -> provenance.add("foreign key " + join.getEdge() + " (" + join.getQuality() + ")"));

            attributes.put("join", path.stream()
                    .map(edge -> edge.getFromTable() + "." + edge.getFromColumn()
                            + " = " + edge.getToTable() + "." + edge.getToColumn())
                    .collect(Collectors.joining(" AND ")));
            attributes.put("joinQuality", joins.stream()
                    .map(JoinAnalysisResult::getQuality)
                    .max(Comparator.naturalOrder())   // EXCELLENT < GOOD < WEAK
                    .map(JoinQuality::name)
                    .orElseThrow());
        }

        return new ContextItem(DOMAIN + ":" + table.getName(), DOMAIN, "TABLE", table.getName(),
                formatter.format(List.of(table)), Math.pow(DECAY, path.size()), reason,
                provenance, attributes);
    }

    /** E.g. "referenced by orders (orders.user_id → users.id)". */
    private static String reason(String target, String table, List<GraphEdge> path) {
        if (table.equals(target)) {
            return "target table";
        }
        GraphEdge edge = path.get(path.size() - 1);
        String relation = edge.getToTable().equals(table)
                ? "referenced by " + edge.getFromTable()
                : "references " + edge.getToTable();
        return relation + " (" + edge + ")";
    }
}
