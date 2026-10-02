package dev.moneet.contextos;

import dev.moneet.contextos.code.source.JavaRepositorySource;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.source.FileRuntimeSource;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ContextOSTest {

    private static final Path EXAMPLES = Path.of(System.getProperty("contextos.examples"));

    private static RuntimeSnapshot runtime;
    private static DatabaseSchema schema;
    private static ContextOS contextOS;

    @BeforeAll
    static void load() throws Exception {
        runtime = new FileRuntimeSource(EXAMPLES.resolve("runtime")).load();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SqlScript.run(connection, EXAMPLES.resolve("runtime/databases/payments-db.sql"));
            schema = new JdbcSchemaMetadataSource(connection, null).load();
        }
        contextOS = ContextOS.builder()
                .runtime(runtime)
                .repository("payment-service", new JavaRepositorySource(EXAMPLES.resolve("payment-service")).load())
                .database("payments-db", schema)
                .build();
    }

    @Test
    void shouldFollowLinksFromIncidentToCodeAndDatabase() {
        List<Link> links = contextOS.investigate("INC-143", ContextBudget.unlimited()).links();

        assertLink(links, "logger", "PaymentService");
        assertLink(links, "endpoint POST /payments", "PaymentController#pay(PaymentRequest)");
        assertLink(links, "endpoint POST /payments/{id}/refund", "PaymentController#refund(long)");
        assertLink(links, "mentions PaymentTransactionRepository.findByPaymentId",
                "PaymentTransactionRepository#findByPaymentId(long)");
        assertLink(links, "query on payments-db", "payment_transactions");
        assertLink(links, "data access", "payments");
        assertLink(links, "data access", "payment_transactions");
    }

    @Test
    void shouldIncludeEveryDomainWithLinkProvenance() {
        Map<String, ContextItem> items = byId(contextOS.investigate("INC-143", ContextBudget.unlimited()));

        assertEquals("CONTEXT_MAP", items.get("contextos:map:INC-143").kind());
        assertTrue(items.containsKey("incident:INC-143"));

        ContextItem findByPaymentId =
                items.get("code:com.example.payments.repository.PaymentTransactionRepository#findByPaymentId(long)");
        assertEquals("payment_transactions", findByPaymentId.attributes().get("tables"));
        assertTrue(findByPaymentId.attributes().get("linkedFrom").startsWith("incident:INC-143:"));

        ContextItem table = items.get("sql:payment_transactions");
        assertTrue(table.reason().contains("linked from payment-service -> payments-db (query on payments-db)"),
                table.reason());
        assertTrue(items.containsKey("sql:customers"), "tables around linked tables are included");
    }

    @Test
    void shouldScaleLinkedScoresByTheItemTheyCameFrom() {
        Map<String, ContextItem> items = byId(contextOS.investigate("INC-143", ContextBudget.unlimited()));

        ContextItem handler = items.get("code:com.example.payments.api.PaymentController#pay(PaymentRequest)");
        ContextItem failedSpans = items.get(handler.attributes().get("linkedFrom"));
        assertEquals(failedSpans.score() * 1.0 * ContextOS.LINK_DECAY, handler.score(), 1e-9);

        for (ContextItem item : items.values()) {
            if (item.attributes().containsKey("linkedFrom")) {
                assertTrue(item.score() < items.get(item.attributes().get("linkedFrom")).score(),
                        item.id() + " should score below the item it was linked from");
            }
        }
    }

    @Test
    void shouldPackAllDomainsUnderOneBudget() {
        CrossDomainContext context = contextOS.investigate("INC-143", ContextBudget.tokens(2500));
        ContextPackage pkg = context.contextPackage();

        assertTrue(pkg.usedTokens() <= 2500);
        assertFalse(pkg.omitted().isEmpty());
        assertEquals("contextos:map:INC-143", pkg.items().get(0).id());
        assertEquals("incident:INC-143", pkg.items().get(1).id());
        assertTrue(pkg.rendered().startsWith("=== CONTEXTOS CONTEXT ==="));
        assertTrue(pkg.rendered().indexOf("=== INCIDENT CONTEXT ===") < pkg.rendered().indexOf("=== CODE CONTEXT ==="));
    }

    @Test
    void shouldWorkWithoutCodeRepository() {
        ContextOS incidentAndDatabase = ContextOS.builder()
                .runtime(runtime)
                .database("payments-db", schema)
                .build();

        Map<String, ContextItem> items = byId(incidentAndDatabase.investigate("INC-143", ContextBudget.unlimited()));

        assertTrue(items.keySet().stream().noneMatch(id -> id.startsWith("code:")));
        assertTrue(items.containsKey("sql:payment_transactions"));
    }

    private static void assertLink(List<Link> links, String via, String targetTitle) {
        assertTrue(links.stream().anyMatch(l -> l.via().equals(via) && l.targetTitle().equals(targetTitle)),
                "missing link --" + via + "--> " + targetTitle);
    }

    private static Map<String, ContextItem> byId(CrossDomainContext context) {
        return context.contextPackage().items().stream()
                .collect(Collectors.toMap(ContextItem::id, Function.identity()));
    }
}
