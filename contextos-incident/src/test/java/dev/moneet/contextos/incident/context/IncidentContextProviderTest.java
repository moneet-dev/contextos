package dev.moneet.contextos.incident.context;

import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.graph.ServiceGraphBuilder;
import dev.moneet.contextos.incident.source.FileRuntimeSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class IncidentContextProviderTest {

    private static IncidentContextProvider provider;

    @BeforeAll
    static void load() {
        RuntimeSnapshot snapshot =
                new FileRuntimeSource(Path.of(System.getProperty("contextos.examples"), "runtime")).load();
        provider = new IncidentContextProvider(snapshot, new ServiceGraphBuilder().build(snapshot.getTopology()));
    }

    @Test
    void shouldStartWithIncidentOverview() {
        ContextItem overview = provider.collect(ContextRequest.of("INC-143", 2)).get(0);

        assertEquals("incident:INC-143", overview.id());
        assertEquals("INCIDENT", overview.kind());
        assertEquals(1.0, overview.score());
        assertTrue(overview.content().startsWith("INCIDENT INC-143 [SEV2] payment-service 5xx elevated\n"));
        assertTrue(overview.content().contains("SERVICES IN SCOPE\n  payment-service"));
        assertEquals("payment-service", overview.attributes().get("affectedServices"));
    }

    @Test
    void shouldMapEvidenceWithProvenanceAndLinkableAttributes() {
        List<ContextItem> items = provider.collect(ContextRequest.of("INC-143", 2));

        ContextItem errors = items.stream()
                .filter(i -> i.kind().equals("ERROR_LOGS") && "payment-service".equals(i.attributes().get("service")))
                .findFirst().orElseThrow();
        assertEquals("com.example.payments.service.PaymentService", errors.attributes().get("logger"));
        assertTrue(errors.content().startsWith("onset 2026-09-30T14:02:08Z, last seen "));
        assertTrue(errors.provenance().get(0).startsWith("telemetry/logs.jsonl:"));
        assertTrue(errors.attributes().get("score").startsWith("kind 1.00"));

        ContextItem slowQuery = items.stream()
                .filter(i -> "SELECT payment_transactions".equals(i.attributes().get("operation"))
                        && i.kind().equals("SLOW_SPANS"))
                .findFirst().orElseThrow();
        assertEquals("payments-db", slowQuery.attributes().get("peer"));

        Set<String> ids = new HashSet<>();
        items.forEach(i -> assertTrue(ids.add(i.id()), "duplicate id " + i.id()));
    }

    @Test
    void shouldPackOverviewFirstUnderBudget() {
        ContextPackage pkg = provider.provide(new ContextRequest("INC-143", 2, ContextBudget.tokens(500)));

        assertTrue(pkg.usedTokens() <= 500);
        assertEquals("incident:INC-143", pkg.items().get(0).id());
        assertFalse(pkg.omitted().isEmpty());
    }
}
