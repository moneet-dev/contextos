package dev.moneet.contextos.mcp;

import dev.moneet.contextos.ContextOS;
import dev.moneet.contextos.CrossDomainContext;
import dev.moneet.contextos.Workspace;
import dev.moneet.contextos.code.context.CodeContextProvider;
import dev.moneet.contextos.code.graph.CodeGraphBuilder;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.sql.SqlContextProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
    private final Map<String, CodeContextProvider> code = new LinkedHashMap<>();
    private final Map<String, SqlContextProvider> sql = new LinkedHashMap<>();

    public ContextOsTools(Workspace workspace) {
        this.workspace = workspace;
        this.contextOS = workspace.hasRuntime() ? workspace.contextOS() : null;
        workspace.repositories().forEach((name, repository) -> code.put(name,
                new CodeContextProvider(repository, new CodeGraphBuilder().build(repository))));
        workspace.databases().forEach((name, schema) -> sql.put(name, new SqlContextProvider(schema)));
    }

    public String listIncidents() {
        StringBuilder sb = new StringBuilder();
        if (!workspace.hasRuntime()) {
            sb.append("No runtime is configured, so there are no incidents; code_context and schema_context "
                    + "still work.\n");
        } else if (workspace.runtime().getIncidents().isEmpty()) {
            sb.append("No incidents.\n");
        } else {
            for (Incident incident : workspace.runtime().getIncidents()) {
                sb.append(incident.id()).append(" [").append(incident.severity()).append("] ")
                        .append(incident.title()).append("\n")
                        .append("  started ").append(incident.startedAt())
                        .append(", affected: ").append(String.join(", ", incident.affectedServices())).append("\n");
            }
        }
        sb.append("\nCode repositories: ").append(names(code.keySet()))
                .append("\nDatabases: ").append(names(sql.keySet())).append("\n");
        return sb.toString();
    }

    public String investigateIncident(String incidentId, Integer budget) {
        if (contextOS == null) {
            throw new IllegalArgumentException("No runtime is configured, so there are no incidents to investigate.");
        }
        CrossDomainContext context = contextOS.investigate(required(incidentId, "incident_id"), budget(budget));
        return withSummary(context.contextPackage());
    }

    public String codeContext(String query, Integer depth, Integer budget) {
        return codeContext(null, query, depth, budget);
    }

    /** {@code repository} may be null when exactly one repository is configured. */
    public String codeContext(String repository, String query, Integer depth, Integer budget) {
        CodeContextProvider provider = pick(code, repository, "repository", "code repository");
        return withSummary(provider.provide(new ContextRequest(required(query, "query"), depth(depth, 2),
                budget(budget))));
    }

    public String schemaContext(String table, Integer depth, Integer budget) {
        return schemaContext(null, table, depth, budget);
    }

    /** {@code database} may be null when exactly one database is configured. */
    public String schemaContext(String database, String table, Integer depth, Integer budget) {
        SqlContextProvider provider = pick(sql, database, "database", "database");
        String target = table == null ? "" : table;
        return withSummary(provider.provide(new ContextRequest(target, depth(depth, 1), budget(budget))));
    }

    private static <T> T pick(Map<String, T> providers, String name, String argument, String what) {
        if (providers.isEmpty()) {
            throw new IllegalArgumentException("No " + what + " is configured.");
        }
        if (name == null || name.isBlank()) {
            if (providers.size() > 1) {
                throw new IllegalArgumentException("Several " + what + "s are configured; pass " + argument
                        + " as one of " + providers.keySet());
            }
            return providers.values().iterator().next();
        }
        T provider = providers.get(name.trim());
        if (provider == null) {
            throw new IllegalArgumentException("Unknown " + argument + " '" + name + "'; configured: "
                    + providers.keySet());
        }
        return provider;
    }

    private static String names(Set<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
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

}
