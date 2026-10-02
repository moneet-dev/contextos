package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.core.graph.Reached;
import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.evidence.AnalysisWindow;
import dev.moneet.contextos.incident.evidence.ChangeEvidenceCollector;
import dev.moneet.contextos.incident.evidence.EvidenceCollector;
import dev.moneet.contextos.incident.evidence.EvidenceRanker;
import dev.moneet.contextos.incident.evidence.LogEvidenceCollector;
import dev.moneet.contextos.incident.evidence.MetricEvidenceCollector;
import dev.moneet.contextos.incident.evidence.RankedEvidence;
import dev.moneet.contextos.incident.evidence.TraceEvidenceCollector;
import dev.moneet.contextos.incident.graph.Direction;
import dev.moneet.contextos.incident.graph.ServiceGraph;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Context for one incident: the services within {@code depth} hops of the
 * affected services, and ranked evidence from their telemetry.
 */
public final class FocusedIncidentStrategy implements IncidentContextStrategy {

    private final String incidentId;
    private final int depth;
    private final Direction direction;
    private final int maxEvidence;

    private final List<EvidenceCollector> collectors = List.of(
            new ChangeEvidenceCollector(),
            new LogEvidenceCollector(),
            new MetricEvidenceCollector(),
            new TraceEvidenceCollector());
    private final EvidenceRanker ranker = new EvidenceRanker();
    private final IncidentRenderer renderer = new IncidentRenderer();

    public FocusedIncidentStrategy(String incidentId, int depth) {
        this(incidentId, depth, Direction.BOTH, Integer.MAX_VALUE);
    }

    public FocusedIncidentStrategy(String incidentId, int depth, Direction direction, int maxEvidence) {
        this.incidentId = incidentId;
        this.depth = depth;
        this.direction = direction;
        this.maxEvidence = maxEvidence;
    }

    @Override
    public IncidentContext generate(RuntimeSnapshot snapshot, ServiceGraph graph) {
        Incident incident = snapshot.getIncident(incidentId);

        List<String> affected = incident.affectedServices().stream()
                .filter(graph::containsService)
                .toList();
        if (affected.isEmpty()) {
            throw new IllegalArgumentException("No affected service of " + incident.id()
                    + " is in the topology: " + incident.affectedServices());
        }

        List<Reached<ServiceDependency>> scope = graph.traverse(affected, depth, direction);
        Set<String> services = scope.stream().map(Reached::id).collect(Collectors.toSet());
        AnalysisWindow window = AnalysisWindow.around(incident);

        List<Evidence> evidence = new ArrayList<>();
        for (EvidenceCollector collector : collectors) {
            evidence.addAll(collector.collect(snapshot.getTelemetry(), window, services));
        }

        List<RankedEvidence> ranked = ranker.rank(evidence, scope, incident).stream()
                .limit(maxEvidence)
                .toList();

        return new IncidentContext(incident, scope, ranked, renderer.render(incident, scope, ranked, graph));
    }
}
