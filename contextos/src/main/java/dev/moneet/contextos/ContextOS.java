package dev.moneet.contextos;

import dev.moneet.contextos.code.context.CodeContextProvider;
import dev.moneet.contextos.code.context.EndpointLookup;
import dev.moneet.contextos.code.context.RenderMode;
import dev.moneet.contextos.code.context.SymbolLookup;
import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.graph.CodeGraphBuilder;
import dev.moneet.contextos.code.source.SqlTables;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.core.context.ContextPackager;
import dev.moneet.contextos.core.context.ContextProvider;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.core.graph.Direction;
import dev.moneet.contextos.incident.context.IncidentContextProvider;
import dev.moneet.contextos.incident.domain.DependencyKind;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceTopology;
import dev.moneet.contextos.incident.graph.ServiceGraphBuilder;
import dev.moneet.contextos.sql.SqlContextProvider;
import dev.moneet.schema.domain.DatabaseSchema;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Assembles context across domains for an incident:
 * <pre>
 * incident evidence ──logger / endpoint / mention──▶ code ──data access──▶ database tables
 *                   └──────────────query on a database service───────────▶ database tables
 * </pre>
 * Links come from data the providers already capture: a service's
 * {@code repository}, evidence attributes ({@code logger}, {@code operation},
 * {@code peer}, log {@code message}), change descriptions that name
 * {@code Class.method}, and code items' {@code tables}.
 *
 * <p>A linked item scores {@code link weight x item score x 0.9}, where the link
 * weight is the score of the item it came from, so every domain shares the
 * incident's relevance scale. Items reached by several links keep their best
 * score. Everything is packed under one budget, preceded by a context map
 * showing which links were followed.
 */
public final class ContextOS {

    public static final double LINK_DECAY = 0.9;
    public static final String DOMAIN = "contextos";

    private static final Pattern MENTIONED_METHOD = Pattern.compile("\\b([A-Z][\\w$]*)\\.([a-z][\\w$]*)\\b");
    private static final Pattern OPERATION_TABLE =
            Pattern.compile("(?i)^\\s*(?:select|insert|update|delete)\\s+([A-Za-z_][\\w$.]*)\\s*$");

    private record CodeSource(CodeRepository repository, CodeContextProvider provider, EndpointLookup endpoints) {
    }

    private record DatabaseSource(DatabaseSchema schema, SqlContextProvider provider) {
    }

    private final ServiceTopology topology;
    private final IncidentContextProvider incidents;
    private final Map<String, CodeSource> repositories;
    private final Map<String, DatabaseSource> databases;
    private final int incidentDepth;
    private final int codeDepth;
    private final int sqlDepth;
    private final SymbolLookup lookup = new SymbolLookup();
    private final ContextPackager packager = new ContextPackager();

    private ContextOS(Builder builder) {
        this.topology = builder.snapshot.getTopology();
        this.incidents = new IncidentContextProvider(builder.snapshot,
                new ServiceGraphBuilder().build(builder.snapshot.getTopology()));
        this.repositories = Map.copyOf(builder.repositories);
        this.databases = Map.copyOf(builder.databases);
        this.incidentDepth = builder.incidentDepth;
        this.codeDepth = builder.codeDepth;
        this.sqlDepth = builder.sqlDepth;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CrossDomainContext investigate(String incidentId, ContextBudget budget) {
        Map<String, ContextItem> items = new LinkedHashMap<>();
        Map<String, String> codeScopes = new LinkedHashMap<>();
        List<Link> links = new ArrayList<>();

        // 1. Incident
        List<ContextItem> incidentItems = incidents.collect(new ContextRequest(incidentId, incidentDepth, budget));
        incidentItems.forEach(item -> keepBest(items, item));

        // 2. Incident -> code and incident -> database
        for (ContextItem evidence : incidentItems) {
            if (!evidence.kind().equals("INCIDENT")) {
                links.addAll(incidentLinks(evidence));
            }
        }

        // 3. Code, then code -> database
        for (Link link : strongest(links, CodeContextProvider.DOMAIN)) {
            CodeSource source = repositories.get(link.scope());
            for (ContextItem item : source.provider().collect(ContextRequest.of(link.target(), codeDepth))) {
                if (keepBest(items, linked(item, link))) {
                    codeScopes.put(item.id(), link.scope());
                }
            }
        }
        for (Map.Entry<String, String> code : codeScopes.entrySet()) {
            links.addAll(dataAccessLinks(items.get(code.getKey()), code.getValue()));
        }

        // 4. Database
        for (Link link : strongest(links, SqlContextProvider.DOMAIN)) {
            ContextProvider provider = databases.get(link.scope()).provider();
            for (ContextItem item : provider.collect(ContextRequest.of(link.target(), sqlDepth))) {
                keepBest(items, linked(item, link));
            }
        }

        List<ContextItem> all = new ArrayList<>();
        all.add(contextMap(incidentId, incidentItems.get(0), links));
        all.addAll(items.values());

        ContextPackage pkg = packager.pack(new ContextRequest(incidentId, incidentDepth, budget), all);
        return new CrossDomainContext(pkg, links);
    }

    // ---------------------------------------------------------------- links

    private List<Link> incidentLinks(ContextItem evidence) {
        List<Link> links = new ArrayList<>();
        Map<String, String> attributes = evidence.attributes();
        String service = attributes.get("service");
        String peer = attributes.get("peer");
        String repositoryName = repositoryOf(service);
        CodeSource code = repositoryName == null ? null : repositories.get(repositoryName);

        if (code != null) {
            String logger = attributes.get("logger");
            if (logger != null && code.repository().containsSymbol(logger)) {
                links.add(codeLink(evidence, repositoryName, code.repository().getSymbol(logger), "logger"));
            }

            String operation = attributes.get("operation");
            if (operation != null && peer == null) {
                code.endpoints().find(operation).ifPresent(handler ->
                        links.add(codeLink(evidence, repositoryName, handler, "endpoint " + operation)));
            }

            if (evidence.kind().equals("CHANGE")) {
                Matcher mention = MENTIONED_METHOD.matcher(evidence.content());
                while (mention.find()) {
                    String query = mention.group(1) + "#" + mention.group(2);
                    for (Symbol symbol : lookup.find(code.repository(), query)) {
                        links.add(codeLink(evidence, repositoryName, symbol,
                                "mentions " + mention.group(1) + "." + mention.group(2)));
                    }
                }
            }
        }

        String database = isDatabase(peer) ? peer : isDatabase(service) ? service : null;
        if (database != null) {
            Set<String> tables = new LinkedHashSet<>();
            tables.addAll(tablesIn(attributes.get("operation")));
            tables.addAll(tablesIn(attributes.get("message")));
            for (String table : tables) {
                if (databases.get(database).schema().containsTable(table)) {
                    links.add(new Link(evidence.id(), evidence.title(), SqlContextProvider.DOMAIN, database,
                            table, table, "query on " + database, evidence.score()));
                }
            }
        }
        return links;
    }

    /** Tables a code item touches, in the databases its repository's services query. */
    private List<Link> dataAccessLinks(ContextItem code, String repositoryName) {
        String tables = code.attributes().get("tables");
        if (tables == null) {
            return List.of();
        }

        List<Link> links = new ArrayList<>();
        for (String database : databasesOf(repositoryName)) {
            for (String table : tables.split(",")) {
                if (databases.get(database).schema().containsTable(table)) {
                    links.add(new Link(code.id(), code.title(), SqlContextProvider.DOMAIN, database,
                            table, table, "data access", code.score()));
                }
            }
        }
        return links;
    }

    private static Link codeLink(ContextItem from, String repositoryName, Symbol symbol, String via) {
        return new Link(from.id(), from.title(), CodeContextProvider.DOMAIN, repositoryName,
                symbol.id(), symbol.displayName(), via, from.score());
    }

    /** One link per target, the one with the highest weight. */
    private static List<Link> strongest(List<Link> links, String domain) {
        Map<String, Link> best = new LinkedHashMap<>();
        for (Link link : links) {
            if (link.toDomain().equals(domain)) {
                best.merge(link.targetKey(), link, (a, b) -> b.weight() > a.weight() ? b : a);
            }
        }
        return List.copyOf(best.values());
    }

    private boolean isDatabase(String service) {
        return service != null && databases.containsKey(service);
    }

    private String repositoryOf(String service) {
        if (service == null || !topology.containsService(service)) {
            return null;
        }
        return topology.getService(service).repository();
    }

    /** Registered databases queried by the services built from {@code repositoryName}. */
    private List<String> databasesOf(String repositoryName) {
        return topology.getServices().stream()
                .filter(s -> repositoryName.equals(s.repository()))
                .map(Service::name)
                .flatMap(service -> topology.getDependencies().stream()
                        .filter(d -> d.from().equals(service) && d.kind() == DependencyKind.QUERIES))
                .map(d -> d.to())
                .filter(databases::containsKey)
                .distinct()
                .toList();
    }

    private static Set<String> tablesIn(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> tables = new LinkedHashSet<>(SqlTables.tables(text));
        Matcher operation = OPERATION_TABLE.matcher(text);
        if (operation.matches()) {
            tables.add(operation.group(1));
        }
        return tables;
    }

    // ---------------------------------------------------------------- items

    private static ContextItem linked(ContextItem item, Link link) {
        Map<String, String> attributes = new LinkedHashMap<>(item.attributes());
        attributes.put("linkedFrom", link.fromId());

        return new ContextItem(item.id(), item.domain(), item.kind(), item.title(), item.content(),
                link.weight() * item.score() * LINK_DECAY,
                item.reason() + "; linked from " + link.fromTitle() + " (" + link.via() + ")",
                item.provenance(), attributes);
    }

    /** Keeps the higher-scoring version of an item; returns whether {@code item} was kept. */
    private static boolean keepBest(Map<String, ContextItem> items, ContextItem item) {
        ContextItem existing = items.get(item.id());
        if (existing == null || item.score() > existing.score()) {
            items.put(item.id(), item);
            return true;
        }
        return false;
    }

    /** Which links were followed, grouped by direction, strongest first. */
    private static ContextItem contextMap(String incidentId, ContextItem incident, List<Link> links) {
        StringBuilder sb = new StringBuilder(incident.title()).append("\n");

        Map<String, List<Link>> byDirection = links.stream()
                .sorted(Comparator.comparingDouble(Link::weight).reversed())
                .collect(Collectors.groupingBy(
                        l -> (l.fromId().startsWith("incident:") ? "incident" : "code") + " -> " + l.toDomain(),
                        LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Link>> direction : byDirection.entrySet()) {
            sb.append(direction.getKey()).append("\n");

            Map<String, List<Link>> byTarget = direction.getValue().stream()
                    .collect(Collectors.groupingBy(Link::targetKey, LinkedHashMap::new, Collectors.toList()));
            for (List<Link> sameTarget : byTarget.values()) {
                Link strongest = sameTarget.get(0);
                sb.append(String.format("  %s --%s--> %s", strongest.fromTitle(), strongest.via(),
                        strongest.targetTitle()));
                if (sameTarget.size() > 1) {
                    sb.append(" (+").append(sameTarget.size() - 1).append(" more)");
                }
                sb.append("\n");
            }
        }

        return new ContextItem(DOMAIN + ":map:" + incidentId, DOMAIN, "CONTEXT_MAP",
                "How this context was assembled", sb.toString(), 1.0,
                "links followed from the incident to code and database context",
                List.of(), Map.of("links", String.valueOf(links.size())));
    }

    // ---------------------------------------------------------------- builder

    public static final class Builder {

        private RuntimeSnapshot snapshot;
        private final Map<String, CodeSource> repositories = new LinkedHashMap<>();
        private final Map<String, DatabaseSource> databases = new LinkedHashMap<>();
        private int incidentDepth = 2;
        private int codeDepth = 2;
        private int sqlDepth = 1;

        public Builder runtime(RuntimeSnapshot snapshot) {
            this.snapshot = snapshot;
            return this;
        }

        /** A code repository, named as in the topology's {@code repository} fields. */
        public Builder repository(String name, CodeRepository repository) {
            repositories.put(name, new CodeSource(repository,
                    new CodeContextProvider(repository, new CodeGraphBuilder().build(repository),
                            Direction.BOTH, RenderMode.SIGNATURES),
                    new EndpointLookup(repository)));
            return this;
        }

        /** A database schema, named as its DATABASE service in the topology. */
        public Builder database(String service, DatabaseSchema schema) {
            databases.put(service, new DatabaseSource(schema, new SqlContextProvider(schema)));
            return this;
        }

        public Builder incidentDepth(int depth) {
            this.incidentDepth = depth;
            return this;
        }

        public Builder codeDepth(int depth) {
            this.codeDepth = depth;
            return this;
        }

        public Builder sqlDepth(int depth) {
            this.sqlDepth = depth;
            return this;
        }

        public ContextOS build() {
            Objects.requireNonNull(snapshot, "runtime snapshot is required");
            return new ContextOS(this);
        }
    }
}
