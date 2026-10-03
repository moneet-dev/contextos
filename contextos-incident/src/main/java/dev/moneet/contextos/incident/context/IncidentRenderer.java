package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.core.graph.Reached;
import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.HealthySignal;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.evidence.RankedEvidence;
import dev.moneet.contextos.incident.graph.ServiceGraph;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Renders an incident context as sections: the incident, the services in scope,
 * a timeline of evidence by onset, the evidence ranked with reasons and
 * telemetry sources, and the signals that stayed healthy. Times are UTC; dates are shown only when they differ
 * from the incident start date.
 */
public final class IncidentRenderer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public String render(Incident incident,
                         List<Reached<ServiceDependency>> scope,
                         List<RankedEvidence> evidence,
                         List<HealthySignal> healthy,
                         ServiceGraph graph) {

        StringBuilder sb = new StringBuilder();
        String day = DATE.format(incident.startedAt());

        sb.append(header(incident));
        sb.append("\n").append(scope(scope, graph));

        sb.append("\nTIMELINE (UTC)\n");
        evidence.stream()
                .sorted(Comparator.comparing((RankedEvidence r) -> r.evidence().onset()))
                .forEach(r -> sb.append(String.format("  %-16s %-20s %-15s %s\n",
                        time(r.evidence().onset(), day), r.evidence().service(),
                        r.evidence().kind(), r.evidence().summary())));

        sb.append("\nEVIDENCE (ranked)\n");
        for (int i = 0; i < evidence.size(); i++) {
            RankedEvidence r = evidence.get(i);
            Evidence e = r.evidence();
            sb.append(String.format("%2d. [%.2f] %s %s: %s\n", i + 1, r.score(), e.kind(), e.service(), e.summary()));
            sb.append("    why: ").append(r.reason()).append("\n");
            sb.append("    score: ").append(r.breakdown()).append("\n");
            sb.append("    source: ").append(sources(e)).append("\n");
        }

        if (!healthy.isEmpty()) {
            sb.append("\n").append(healthy(healthy));
        }
        return sb.toString();
    }

    /** Signals that stayed normal on the affected services and their dependencies. */
    public String healthy(List<HealthySignal> healthy) {
        StringBuilder sb = new StringBuilder("HEALTHY SIGNALS (no anomaly during the window)\n");
        for (HealthySignal signal : healthy) {
            sb.append(String.format("  %-20s %s\n", signal.service(), signal.summary()));
        }
        return sb.toString();
    }

    /** Id, severity, title, start and detection times, affected services and symptoms. */
    public String header(Incident incident) {
        StringBuilder sb = new StringBuilder();
        String day = DATE.format(incident.startedAt());

        sb.append("INCIDENT ").append(incident.id())
                .append(" [").append(incident.severity()).append("] ").append(incident.title()).append("\n");
        sb.append("Started ").append(day).append(" ").append(TIME.format(incident.startedAt())).append(" UTC");
        if (incident.detectedAt() != null) {
            sb.append(", detected ").append(time(incident.detectedAt(), day));
        }
        sb.append("\n");
        sb.append("Affected: ").append(String.join(", ", incident.affectedServices())).append("\n");
        if (!incident.symptoms().isEmpty()) {
            sb.append("Symptoms:\n");
            incident.symptoms().forEach(s -> sb.append("  - ").append(s).append("\n"));
        }
        return sb.toString();
    }

    /** One line per service in scope: name, kind and how it relates to the affected services. */
    public String scope(List<Reached<ServiceDependency>> scope, ServiceGraph graph) {
        StringBuilder sb = new StringBuilder("SERVICES IN SCOPE\n");
        for (Reached<ServiceDependency> reached : scope) {
            Service service = graph.getService(reached.id());
            sb.append(String.format("  %-22s %-9s %s\n", service.name(), service.kind(), relation(reached)));
        }
        return sb.toString();
    }

    private static String relation(Reached<ServiceDependency> reached) {
        if (reached.lastEdge().isEmpty()) {
            return "affected";
        }
        ServiceDependency edge = reached.lastEdge().get();
        String phrase = edge.to().equals(reached.id())
                ? edge.kind().toPhrase() + " " + edge.from()
                : edge.kind().fromPhrase() + " " + edge.to();
        return phrase + (reached.distance() > 1 ? "  (path: " + String.join(" > ", reached.nodes()) + ")" : "");
    }

    static String sources(Evidence evidence) {
        String shown = String.join(", ", evidence.sources().stream().map(Object::toString).toList());
        int more = evidence.occurrences() - evidence.sources().size();
        return more > 0 ? shown + " (+" + more + " more)" : shown;
    }

    private static String time(Instant instant, String incidentDay) {
        return DATE.format(instant).equals(incidentDay) ? TIME.format(instant) : DATE_TIME.format(instant);
    }
}
