package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.evidence.RankedEvidence;
import dev.moneet.contextos.incident.graph.Direction;
import dev.moneet.contextos.incident.graph.ReachedService;
import dev.moneet.contextos.incident.graph.ServiceGraph;
import dev.moneet.contextos.incident.graph.ServiceGraphBuilder;
import dev.moneet.contextos.incident.source.FileRuntimeSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IncidentContextEngineTest {

    private static RuntimeSnapshot snapshot;
    private static ServiceGraph graph;

    @BeforeAll
    static void load() {
        snapshot = new FileRuntimeSource(Path.of(System.getProperty("contextos.examples"), "runtime")).load();
        graph = new ServiceGraphBuilder().build(snapshot.getTopology());
    }

    @Test
    void shouldScopeServicesAroundAffectedService() {
        IncidentContext context = generate(new FocusedIncidentStrategy("INC-143", 1));

        assertEquals(List.of("payment-service", "fraud-api", "payments-db", "payment-events",
                        "checkout-service", "refund-batch"),
                context.scope().stream().map(ReachedService::name).toList());
    }

    @Test
    void shouldRankAffectedServiceSymptomsFirst() {
        List<RankedEvidence> evidence = generate(new FocusedIncidentStrategy("INC-143", 2)).evidence();

        RankedEvidence top = evidence.get(0);
        assertEquals("payment-service", top.evidence().service());
        assertEquals(0, top.distance());
        for (int i = 1; i < evidence.size(); i++) {
            assertTrue(evidence.get(i - 1).score() >= evidence.get(i).score());
        }
    }

    @Test
    void shouldSurfaceTheCausalChain() {
        IncidentContext context = generate(new FocusedIncidentStrategy("INC-143", 2));

        Evidence job = find(context, EvidenceKind.CHANGE, "refund-batch").orElseThrow();
        assertTrue(job.summary().contains("Refund batch"));

        Evidence slowQuery = find(context, EvidenceKind.SLOW_SPANS, "payment-service", "SELECT payment_transactions")
                .orElseThrow();
        assertEquals("payments-db", slowQuery.peer());
        assertTrue(slowQuery.onset().isBefore(job.onset().plusSeconds(60)));

        Evidence pool = find(context, EvidenceKind.ERROR_LOGS, "payment-service").orElseThrow();
        assertEquals("com.example.payments.service.PaymentService", pool.attributes().get("logger"));
        assertTrue(pool.summary().contains("SQLTransientConnectionException"));

        assertTrue(find(context, EvidenceKind.METRIC_ANOMALY, "payment-service", "http_5xx_per_min").isPresent());
    }

    @Test
    void shouldRankRecentDeployOfAffectedServiceAboveUnrelatedConfigChange() {
        IncidentContext context = generate(new FocusedIncidentStrategy("INC-143", 2));

        assertTrue(score(context, "DEPLOY") > score(context, "CONFIG"));
    }

    @Test
    void shouldExcludeTelemetryBeforeWindow() {
        IncidentContext context = generate(new FocusedIncidentStrategy("INC-143", 2));

        assertFalse(context.rendered().contains("Rate limit"), "fraud-api warning at 13:30 is before the window");
    }

    @Test
    void shouldExcludeServicesOutsideScope() {
        IncidentContext context = generate(new FocusedIncidentStrategy("INC-143", 1, Direction.DEPENDENCIES, 50));

        assertTrue(context.evidence().stream()
                .noneMatch(r -> r.evidence().service().equals("checkout-service")
                        || r.evidence().service().equals("notification-service")));
    }

    @Test
    void shouldRenderSectionsWithProvenance() {
        String rendered = generate(new FocusedIncidentStrategy("INC-143", 2, Direction.BOTH, 5)).rendered();

        assertTrue(rendered.startsWith("INCIDENT INC-143 [SEV2] payment-service 5xx elevated\n"));
        assertTrue(rendered.contains("SERVICES IN SCOPE"));
        assertTrue(rendered.contains("notification-service   SERVICE   consumes from payment-events  "
                + "(path: payment-service > payment-events > notification-service)"));
        assertTrue(rendered.contains("TIMELINE (UTC)"));
        assertTrue(rendered.contains(" 5. ["));
        assertFalse(rendered.contains(" 6. ["));
        assertTrue(rendered.contains("source: telemetry/"));
        assertTrue(rendered.contains("score: kind "));
    }

    @Test
    void shouldRejectUnknownIncident() {
        assertThrows(IllegalArgumentException.class,
                () -> generate(new FocusedIncidentStrategy("INC-999", 2)));
    }

    private static IncidentContext generate(IncidentContextStrategy strategy) {
        return new IncidentContextEngine(strategy).generate(snapshot, graph);
    }

    private static Optional<Evidence> find(IncidentContext context, EvidenceKind kind, String service, String... text) {
        return context.evidence().stream()
                .map(RankedEvidence::evidence)
                .filter(e -> e.kind() == kind && e.service().equals(service))
                .filter(e -> List.of(text).stream().allMatch(e.summary()::contains))
                .findFirst();
    }

    private static double score(IncidentContext context, String summaryPrefix) {
        return context.evidence().stream()
                .filter(r -> r.evidence().summary().startsWith(summaryPrefix))
                .findFirst()
                .orElseThrow()
                .score();
    }
}
