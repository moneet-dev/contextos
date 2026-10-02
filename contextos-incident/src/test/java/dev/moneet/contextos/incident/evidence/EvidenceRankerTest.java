package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.DependencyKind;
import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.domain.Severity;
import dev.moneet.contextos.core.graph.Reached;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceRankerTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final Incident INCIDENT =
            new Incident("INC-1", "t", Severity.SEV2, START, null, List.of("api"), List.of());

    private static final List<Reached<ServiceDependency>> SCOPE = List.of(
            new Reached<>("api", 0, List.<ServiceDependency>of()),
            new Reached<>("db", 1, List.of(new ServiceDependency("api", "db", DependencyKind.QUERIES))),
            new Reached<>("job", 1, List.of(new ServiceDependency("job", "api", DependencyKind.CALLS))));

    @Test
    void shouldScoreAsProductOfFactors() {
        RankedEvidence ranked = rankOne(evidence(EvidenceKind.SLOW_SPANS, "db", null, 0, 0.8));

        assertEquals(0.9 * 0.85 * 1.0 * 0.8, ranked.score(), 1e-9);
        assertEquals(1, ranked.distance());
        assertEquals("db, queried by api (1 hop); started at incident start", ranked.reason());
    }

    @Test
    void shouldUseCloserOfServiceAndPeer() {
        RankedEvidence ranked = rankOne(evidence(EvidenceKind.FAILED_SPANS, "db", "api", 0, 1.0));

        assertEquals(0, ranked.distance());
        assertTrue(ranked.reason().startsWith("api, affected service"));
    }

    @Test
    void shouldDescribeDependentsAndOffsets() {
        RankedEvidence ranked = rankOne(evidence(EvidenceKind.CHANGE, "job", null, -4, 0.8));

        assertEquals("job, calls api (1 hop); started 4m 0s before incident start", ranked.reason());
    }

    @Test
    void shouldDropEvidenceOutsideScope() {
        assertTrue(new EvidenceRanker()
                .rank(List.of(evidence(EvidenceKind.ERROR_LOGS, "elsewhere", null, 0, 1)), SCOPE, INCIDENT)
                .isEmpty());
    }

    @Test
    void shouldFavourSignalsAroundIncidentStart() {
        assertEquals(1.0, EvidenceRanker.timing(Duration.ofMinutes(-15)));
        assertEquals(1.0, EvidenceRanker.timing(Duration.ofMinutes(2)));
        assertEquals(0.3, EvidenceRanker.timing(Duration.ofMinutes(30)), 1e-9);
        assertTrue(EvidenceRanker.timing(Duration.ofMinutes(10)) < 1.0);

        double hourBefore = EvidenceRanker.timing(Duration.ofHours(-1));
        double dayBefore = EvidenceRanker.timing(Duration.ofHours(-24));
        assertTrue(hourBefore > dayBefore);
        assertEquals(0.7, dayBefore, 1e-9);
        assertEquals(0.5, EvidenceRanker.timing(Duration.ofDays(-30)));
    }

    @Test
    void shouldOrderByScoreThenOnset() {
        List<RankedEvidence> ranked = new EvidenceRanker().rank(List.of(
                evidence(EvidenceKind.WARNING_LOGS, "api", null, 0, 1.0),
                evidence(EvidenceKind.ERROR_LOGS, "api", null, 1, 1.0),
                evidence(EvidenceKind.ERROR_LOGS, "api", null, -1, 1.0)), SCOPE, INCIDENT);

        assertEquals(List.of(-60L, 60L, 0L), ranked.stream()
                .map(r -> Duration.between(START, r.evidence().onset()).toSeconds())
                .toList());
    }

    private static RankedEvidence rankOne(Evidence evidence) {
        List<RankedEvidence> ranked = new EvidenceRanker().rank(List.of(evidence), SCOPE, INCIDENT);
        assertEquals(1, ranked.size());
        return ranked.get(0);
    }

    private static Evidence evidence(EvidenceKind kind, String service, String peer, int minutes, double strength) {
        Instant onset = START.plus(Duration.ofMinutes(minutes));
        return new Evidence(kind, service, peer, onset, onset, 1, strength, kind.name(), Map.of(), List.of());
    }
}
