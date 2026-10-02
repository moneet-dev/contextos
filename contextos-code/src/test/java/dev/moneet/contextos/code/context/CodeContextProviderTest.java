package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.graph.CodeGraph;
import dev.moneet.contextos.code.graph.CodeGraphBuilder;
import dev.moneet.contextos.code.source.JavaRepositorySource;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.contextos.core.graph.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CodeContextProviderTest {

    private static CodeRepository repository;
    private static CodeGraph graph;

    @BeforeAll
    static void load() {
        repository = new JavaRepositorySource(
                Path.of(System.getProperty("contextos.examples"), "payment-service")).load();
        graph = new CodeGraphBuilder().build(repository);
    }

    @Test
    void shouldMapRankedSymbolsToContextItems() {
        List<ContextItem> items = new CodeContextProvider(repository, graph)
                .collect(ContextRequest.of("PaymentService#charge", 2));

        ContextItem target = items.get(0);
        assertEquals("code:com.example.payments.service.PaymentService#charge(PaymentRequest)", target.id());
        assertEquals("code", target.domain());
        assertEquals("METHOD", target.kind());
        assertEquals("PaymentService#charge(PaymentRequest)", target.title());
        assertEquals(1.0, target.score());
        assertEquals("@Transactional\npublic PaymentReceipt charge(PaymentRequest request)", target.content());
        assertEquals(List.of("src/main/java/com/example/payments/service/PaymentService.java:34"), target.provenance());
        assertEquals("34", target.attributes().get("line"));

        ContextItem impl = byTitle(items).get("HttpFraudCheckClient#isAllowed(String,long)");
        assertEquals("via PaymentService#charge(PaymentRequest) -CALLS-> FraudCheckClient#isAllowed(String,long) "
                + "<-OVERRIDES- HttpFraudCheckClient#isAllowed(String,long)", impl.provenance().get(1));
    }

    @Test
    void shouldExposeDataAccessForLinking() {
        List<ContextItem> items = new CodeContextProvider(repository, graph, Direction.OUTGOING, RenderMode.FULL)
                .collect(ContextRequest.of("PaymentService#refund", 1));

        ContextItem refund = byTitle(items).get("PaymentService#refund(long)");
        assertEquals("payments", refund.attributes().get("tables"));
        assertTrue(refund.reason().endsWith("data access: payments [SQL_LITERAL]"));
        assertTrue(refund.content().contains("jdbcTemplate.update("));
        assertEquals("payment_transactions", byTitle(items)
                .get("PaymentTransactionRepository#findByPaymentId(long)").attributes().get("tables"));
    }

    @Test
    void shouldPackUnderBudget() {
        ContextPackage pkg = new CodeContextProvider(repository, graph)
                .provide(new ContextRequest("PaymentService#charge", 2, ContextBudget.tokens(300)));

        assertTrue(pkg.usedTokens() <= 300);
        assertFalse(pkg.omitted().isEmpty());
        assertTrue(pkg.rendered().startsWith("=== CODE CONTEXT ===\n\n[METHOD] PaymentService#charge(PaymentRequest)"));
    }

    private static Map<String, ContextItem> byTitle(List<ContextItem> items) {
        return items.stream().collect(Collectors.toMap(ContextItem::title, i -> i));
    }
}
