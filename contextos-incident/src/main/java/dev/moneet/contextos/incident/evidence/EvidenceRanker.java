package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.graph.ReachedService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores evidence as {@code kind x proximity x timing x strength}:
 * <ul>
 *   <li>kind: errors and failed spans 1.0, anomalies, slow spans and changes 0.9, warnings 0.7</li>
 *   <li>proximity: 1.0 for an affected service, minus 0.15 per hop (minimum 0.4)</li>
 *   <li>timing: 1.0 from 15 minutes before to 2 minutes after the incident start.
 *       Later signals are more likely effects than causes and decay to 0.3 at +30
 *       minutes. Earlier signals (changes) decay on a log scale to 0.7 at 24 hours before,
 *       and 0.5 at most beyond that</li>
 *   <li>strength: from the collector (volume, deviation, failure rate)</li>
 * </ul>
 */
public final class EvidenceRanker {

    private static final Map<EvidenceKind, Double> KIND_WEIGHTS = new EnumMap<>(Map.of(
            EvidenceKind.ERROR_LOGS, 1.0,
            EvidenceKind.FAILED_SPANS, 1.0,
            EvidenceKind.METRIC_ANOMALY, 0.9,
            EvidenceKind.SLOW_SPANS, 0.9,
            EvidenceKind.CHANGE, 0.9,
            EvidenceKind.WARNING_LOGS, 0.7
    ));

    private static final Duration LEAD = Duration.ofMinutes(15);
    private static final Duration GRACE = Duration.ofMinutes(2);
    private static final Duration LAG_LIMIT = Duration.ofMinutes(30);
    private static final Duration CHANGE_LIMIT = Duration.ofHours(24);

    /** Ranked by score (descending), then onset (earliest first). */
    public List<RankedEvidence> rank(List<Evidence> evidence, List<ReachedService> scope, Incident incident) {
        Map<String, ReachedService> reached = new HashMap<>();
        scope.forEach(r -> reached.put(r.name(), r));

        List<RankedEvidence> ranked = new ArrayList<>();
        for (Evidence e : evidence) {
            ReachedService service = closest(e, reached);
            if (service == null) {
                continue;
            }

            Duration offset = Duration.between(incident.startedAt(), e.onset());
            ScoreBreakdown breakdown = new ScoreBreakdown(
                    KIND_WEIGHTS.getOrDefault(e.kind(), 0.5),
                    Math.max(0.4, 1 - 0.15 * service.distance()),
                    timing(offset),
                    e.strength());

            String reason = relation(service) + "; " + describeOffset(offset);
            ranked.add(new RankedEvidence(e, breakdown.score(), service.distance(), reason, breakdown));
        }

        ranked.sort(Comparator.comparingDouble(RankedEvidence::score).reversed()
                .thenComparing(r -> r.evidence().onset()));
        return ranked;
    }

    private static ReachedService closest(Evidence evidence, Map<String, ReachedService> reached) {
        ReachedService service = reached.get(evidence.service());
        ReachedService peer = evidence.peer() == null ? null : reached.get(evidence.peer());
        if (service == null) {
            return peer;
        }
        if (peer == null) {
            return service;
        }
        return peer.distance() < service.distance() ? peer : service;
    }

    static double timing(Duration offset) {
        if (offset.compareTo(GRACE) > 0) {
            double lag = (double) offset.minus(GRACE).toSeconds() / LAG_LIMIT.minus(GRACE).toSeconds();
            return Math.max(0.3, 1 - 0.7 * lag);
        }
        Duration lead = offset.negated();
        if (lead.compareTo(LEAD) <= 0) {
            return 1.0;
        }
        // Log scale: a change from yesterday is still suspicious, one from an hour ago more so
        double hours = lead.minus(LEAD).toSeconds() / 3600.0;
        double limit = CHANGE_LIMIT.minus(LEAD).toSeconds() / 3600.0;
        return Math.max(0.5, 1 - 0.3 * Math.log1p(hours) / Math.log1p(limit));
    }

    /** E.g. "payments-db, queried by payment-service (1 hop)". */
    private static String relation(ReachedService service) {
        if (service.lastEdge().isEmpty()) {
            return service.name() + ", affected service";
        }
        ServiceDependency edge = service.lastEdge().get();
        String phrase = edge.to().equals(service.name())
                ? edge.kind().toPhrase() + " " + edge.from()
                : edge.kind().fromPhrase() + " " + edge.to();
        return service.name() + ", " + phrase + " (" + service.distance()
                + (service.distance() == 1 ? " hop)" : " hops)");
    }

    static String describeOffset(Duration offset) {
        if (offset.abs().compareTo(Duration.ofSeconds(30)) < 0) {
            return "started at incident start";
        }
        Duration abs = offset.abs();
        String amount = abs.toHours() > 0
                ? abs.toHours() + "h " + abs.toMinutesPart() + "m"
                : abs.toMinutes() + "m " + abs.toSecondsPart() + "s";
        return "started " + amount + (offset.isNegative() ? " before" : " after") + " incident start";
    }
}
