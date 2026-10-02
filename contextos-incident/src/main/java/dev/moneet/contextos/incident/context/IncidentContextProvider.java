package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextProvider;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.evidence.RankedEvidence;
import dev.moneet.contextos.incident.graph.Direction;
import dev.moneet.contextos.incident.graph.ServiceGraph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ContextOS provider for production incidents. The request target is an
 * incident id. Items are an overview (incident and services in scope, score 1.0)
 * followed by the ranked evidence from {@link FocusedIncidentStrategy}.
 *
 * <p>Evidence attributes carry the collector's details (logger, exception,
 * metric, operation, peer) plus {@code service}, so other domains can link to them.
 */
public final class IncidentContextProvider implements ContextProvider {

    public static final String DOMAIN = "incident";

    private final RuntimeSnapshot snapshot;
    private final ServiceGraph graph;
    private final Direction direction;
    private final IncidentRenderer renderer = new IncidentRenderer();

    public IncidentContextProvider(RuntimeSnapshot snapshot, ServiceGraph graph) {
        this(snapshot, graph, Direction.BOTH);
    }

    public IncidentContextProvider(RuntimeSnapshot snapshot, ServiceGraph graph, Direction direction) {
        this.snapshot = snapshot;
        this.graph = graph;
        this.direction = direction;
    }

    @Override
    public String domain() {
        return DOMAIN;
    }

    @Override
    public List<ContextItem> collect(ContextRequest request) {
        IncidentContext context = new FocusedIncidentStrategy(request.target(), request.depth(), direction,
                Integer.MAX_VALUE).generate(snapshot, graph);
        Incident incident = context.incident();

        List<ContextItem> items = new ArrayList<>();
        items.add(new ContextItem(
                DOMAIN + ":" + incident.id(),
                DOMAIN,
                "INCIDENT",
                incident.id() + " " + incident.title(),
                renderer.header(incident) + "\n" + renderer.scope(context.scope(), graph),
                1.0,
                "incident under investigation",
                List.of("incidents.json"),
                Map.of("incident", incident.id(),
                        "affectedServices", String.join(",", incident.affectedServices()))));

        for (RankedEvidence ranked : context.evidence()) {
            items.add(toItem(incident, ranked));
        }
        return items;
    }

    private static ContextItem toItem(Incident incident, RankedEvidence ranked) {
        Evidence evidence = ranked.evidence();

        String content = "onset " + evidence.onset()
                + (evidence.lastSeen().equals(evidence.onset()) ? "" : ", last seen " + evidence.lastSeen())
                + "\n" + evidence.summary();

        Map<String, String> attributes = new LinkedHashMap<>(evidence.attributes());
        attributes.put("service", evidence.service());
        if (evidence.peer() != null) {
            attributes.put("peer", evidence.peer());
        }
        attributes.put("score", ranked.breakdown().toString());

        // The first telemetry record identifies the evidence within an incident
        String key = evidence.sources().isEmpty()
                ? evidence.kind() + ":" + evidence.service() + ":" + evidence.onset()
                : evidence.sources().get(0).toString();

        return new ContextItem(
                DOMAIN + ":" + incident.id() + ":" + key,
                DOMAIN,
                evidence.kind().name(),
                evidence.service() + (evidence.peer() == null ? "" : " -> " + evidence.peer()),
                content,
                ranked.score(),
                ranked.reason(),
                List.of(IncidentRenderer.sources(evidence)),
                attributes);
    }
}
