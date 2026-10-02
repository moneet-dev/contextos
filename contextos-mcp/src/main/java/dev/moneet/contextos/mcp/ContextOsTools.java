package dev.moneet.contextos.mcp;

import dev.moneet.contextos.ContextOS;
import dev.moneet.contextos.CrossDomainContext;
import dev.moneet.contextos.code.context.CodeContextProvider;
import dev.moneet.contextos.code.graph.CodeGraphBuilder;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.sql.SqlContextProvider;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The tool implementations, independent of MCP. Each returns the text given
 * to the model and throws {@link IllegalArgumentException} for bad input
 * (unknown incident, symbol or table; out-of-range numbers).
 */
public final class ContextOsTools {

    public static final int DEFAULT_BUDGET = 4000;
    public static final int MAX_BUDGET = 50_000;
    public static final int MAX_DEPTH = 5;

    private final Workspace workspace;
    private final ContextOS contextOS;
    private final CodeContextProvider code;
    private final SqlContextProvider sql;

    public ContextOsTools(Workspace workspace) {
        this.workspace = workspace;
        this.contextOS = workspace.contextOS();
        this.code = workspace.repository() == null ? null
                : new CodeContextProvider(workspace.repository(), new CodeGraphBuilder().build(workspace.repository()));
        this.sql = workspace.schema() == null ? null : new SqlContextProvider(workspace.schema());
    }

    public String listIncidents() {
        if (workspace.runtime().getIncidents().isEmpty()) {
            return "No incidents.";
        }
        StringBuilder sb = new StringBuilder();
        for (Incident incident : workspace.runtime().getIncidents()) {
            sb.append(incident.id()).append(" [").append(incident.severity()).append("] ")
                    .append(incident.title()).append("\n")
                    .append("  started ").append(incident.startedAt())
                    .append(", affected: ").append(String.join(", ", incident.affectedServices())).append("\n");
        }
        sb.append("\nCode repository: ").append(orNone(workspace.repositoryName()))
                .append("\nDatabase: ").append(orNone(workspace.databaseName())).append("\n");
        return sb.toString();
    }

    public String investigateIncident(String incidentId, Integer budget) {
        CrossDomainContext context = contextOS.investigate(required(incidentId, "incident_id"), budget(budget));
        return withSummary(context.contextPackage());
    }

    public String codeContext(String query, Integer depth, Integer budget) {
        if (code == null) {
            throw new IllegalArgumentException("No code repository is loaded.");
        }
        return withSummary(code.provide(new ContextRequest(required(query, "query"), depth(depth, 2), budget(budget))));
    }

    public String schemaContext(String table, Integer depth, Integer budget) {
        if (sql == null) {
            throw new IllegalArgumentException("No database schema is loaded.");
        }
        String target = table == null ? "" : table;
        return withSummary(sql.provide(new ContextRequest(target, depth(depth, 1), budget(budget))));
    }

    /** Rendered package followed by what was used and what was left out. */
    private static String withSummary(ContextPackage pkg) {
        Map<String, Long> omitted = pkg.omitted().stream()
                .collect(Collectors.groupingBy(e -> e.item().domain(), TreeMap::new, Collectors.counting()));

        StringBuilder sb = new StringBuilder(pkg.rendered());
        sb.append("---\n");
        sb.append(pkg.usedTokens()).append(" of ")
                .append(pkg.request().budget().isUnlimited() ? "unlimited" : pkg.request().budget().maxTokens())
                .append(" estimated tokens; ").append(pkg.included().size()).append(" items included");
        if (!omitted.isEmpty()) {
            sb.append(", omitted for budget: ").append(omitted.entrySet().stream()
                    .map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining(", ")));
        }
        return sb.append("\n").toString();
    }

    private static ContextBudget budget(Integer budget) {
        int tokens = budget == null ? DEFAULT_BUDGET : budget;
        if (tokens < 100 || tokens > MAX_BUDGET) {
            throw new IllegalArgumentException("budget must be between 100 and " + MAX_BUDGET + " tokens");
        }
        return ContextBudget.tokens(tokens);
    }

    private static int depth(Integer depth, int defaultDepth) {
        int value = depth == null ? defaultDepth : depth;
        if (value < 0 || value > MAX_DEPTH) {
            throw new IllegalArgumentException("depth must be between 0 and " + MAX_DEPTH);
        }
        return value;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String orNone(String value) {
        return value == null ? "none" : value;
    }
}
