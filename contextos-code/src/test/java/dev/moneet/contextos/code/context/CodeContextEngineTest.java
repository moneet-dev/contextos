package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.graph.CodeGraph;
import dev.moneet.contextos.code.graph.CodeGraphBuilder;
import dev.moneet.contextos.code.source.JavaRepositorySource;
import dev.moneet.contextos.core.graph.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CodeContextEngineTest {

    private static CodeRepository repository;
    private static CodeGraph graph;

    @BeforeAll
    static void load() {
        Path root = Path.of(System.getProperty("contextos.examples"), "payment-service");
        repository = new JavaRepositorySource(root).load();
        graph = new CodeGraphBuilder().build(repository);
    }

    @Test
    void shouldGenerateFocusedContextWithCallersAndCallees() {
        CodeContext context = generate(new FocusedCodeStrategy("PaymentService#charge", 1));

        Map<String, CodeContextItem> items = byDisplayName(context);

        assertEquals("target", items.get("PaymentService#charge(PaymentRequest)").reason());
        assertEquals(1.0, items.get("PaymentService#charge(PaymentRequest)").score());
        assertEquals("calls PaymentService#charge(PaymentRequest)",
                items.get("PaymentController#pay(PaymentRequest)").reason());
        assertEquals("called by PaymentService#charge(PaymentRequest)",
                items.get("FraudCheckClient#isAllowed(String,long)").reason());
        assertEquals("declares PaymentService#charge(PaymentRequest)",
                items.get("PaymentService").reason());

        assertEquals("PaymentService#charge(PaymentRequest)", context.items().get(0).symbol().displayName());
        assertTrue(context.rendered().contains("public PaymentReceipt charge(PaymentRequest request)"));
    }

    @Test
    void shouldRankByEdgeWeightsAlongPath() {
        List<CodeContextItem> items = generate(new FocusedCodeStrategy("PaymentService#charge", 2)).items();

        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).score() >= items.get(i).score(), "items are sorted by score");
        }

        CodeContextItem implementation = byDisplayName(items).get("HttpFraudCheckClient#isAllowed(String,long)");
        assertEquals(2, implementation.distance());
        assertEquals(0.9 * 0.85, implementation.score(), 1e-9);
    }

    @Test
    void shouldReachDataAccessThroughRepositories() {
        CodeContext context = generate(new FocusedCodeStrategy("PaymentService#refund", 1,
                Direction.OUTGOING, RenderMode.SIGNATURES, Integer.MAX_VALUE));

        assertTrue(context.rendered().contains("data access: payments [SQL_LITERAL]"));
        assertTrue(context.rendered().contains("data access: payment_transactions [NATIVE_QUERY]"));
    }

    @Test
    void shouldFindImplementationsThroughIncomingEdges() {
        CodeContext context = generate(new FocusedCodeStrategy("FraudCheckClient", 1,
                Direction.INCOMING, RenderMode.SIGNATURES, Integer.MAX_VALUE));

        assertEquals("implements FraudCheckClient",
                byDisplayName(context).get("HttpFraudCheckClient").reason());
        assertFalse(byDisplayName(context).containsKey("PaymentController"));
    }

    @Test
    void shouldRenderFullSourceForMembersAndHeadersForTypes() {
        String rendered = generate(new FocusedCodeStrategy("PaymentService#refund", 1,
                Direction.BOTH, RenderMode.FULL, Integer.MAX_VALUE)).rendered();

        assertTrue(rendered.contains("\"UPDATE payments SET status = 'REFUNDED' WHERE id = ?\""));
        assertTrue(rendered.contains("@Service\npublic class PaymentService\n"));
        assertFalse(rendered.contains("public class PaymentService {"), "type bodies are not rendered");
    }

    @Test
    void shouldShowPathForIndirectItems() {
        String rendered = generate(new FocusedCodeStrategy("PaymentService#charge", 2)).rendered();

        assertTrue(rendered.contains("path: PaymentService#charge(PaymentRequest) -CALLS-> "
                + "FraudCheckClient#isAllowed(String,long) <-OVERRIDES- HttpFraudCheckClient#isAllowed(String,long)"));
    }

    @Test
    void shouldCapItems() {
        CodeContext context = generate(new FocusedCodeStrategy("PaymentService#charge", 2,
                Direction.BOTH, RenderMode.SIGNATURES, 3));

        assertEquals(3, context.items().size());
    }

    @Test
    void shouldGenerateRepositoryOutline() {
        CodeContext context = generate(new FullCodeStrategy());

        assertEquals(repository.getSymbols().size(), context.items().size());
        assertTrue(context.rendered().contains("public interface FraudCheckClient"));
    }

    @Test
    void shouldLookUpSymbolsByIdNameMemberAndFile() {
        SymbolLookup lookup = new SymbolLookup();

        assertEquals(List.of("com.example.payments.service.PaymentService"),
                ids(lookup.find(repository, "PaymentService")));
        assertEquals(List.of("com.example.payments.service.PaymentService#charge(PaymentRequest)"),
                ids(lookup.find(repository, "PaymentService#charge")));
        assertEquals(2, lookup.find(repository, "isAllowed").size());
        assertEquals(List.of("com.example.payments.client.FraudCheckClient"),
                ids(lookup.find(repository, "client/FraudCheckClient.java")));
        assertEquals(List.of("com.example.payments.service.PaymentService"),
                ids(lookup.find(repository, "paymentservice")));
        assertTrue(lookup.find(repository, "NoSuchThing").isEmpty());
    }

    @Test
    void shouldRejectUnknownQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> generate(new FocusedCodeStrategy("NoSuchThing", 1)));
    }

    private static CodeContext generate(CodeContextStrategy strategy) {
        return new CodeContextEngine(strategy).generate(repository, graph);
    }

    private static Map<String, CodeContextItem> byDisplayName(CodeContext context) {
        return byDisplayName(context.items());
    }

    private static Map<String, CodeContextItem> byDisplayName(List<CodeContextItem> items) {
        return items.stream().collect(Collectors.toMap(i -> i.symbol().displayName(), i -> i));
    }

    private static List<String> ids(List<Symbol> symbols) {
        return symbols.stream().map(Symbol::id).toList();
    }
}
