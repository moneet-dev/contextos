package dev.moneet.contextos.code.source;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.DataAccess;
import dev.moneet.contextos.code.domain.DataAccessKind;
import dev.moneet.contextos.code.domain.ReferenceKind;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaRepositorySourceTest {

    private static final String PKG = "com.example.payments.";

    private static CodeRepository repository;

    @BeforeAll
    static void load() {
        Path root = Path.of(System.getProperty("contextos.examples"), "payment-service");
        repository = new JavaRepositorySource(root).load();
    }

    @Test
    void shouldExtractSymbolsWithIdsLocationsAndSignatures() {
        Symbol charge = repository.getSymbol(PKG + "service.PaymentService#charge(PaymentRequest)");

        assertEquals(SymbolKind.METHOD, charge.kind());
        assertEquals("PaymentService#charge(PaymentRequest)", charge.displayName());
        assertEquals(PKG + "service.PaymentService", charge.parentId());
        assertEquals("src/main/java/com/example/payments/service/PaymentService.java", charge.location().file());
        assertEquals("@Transactional\npublic PaymentReceipt charge(PaymentRequest request)", charge.signature());
        assertTrue(charge.source().contains("paymentRepository.save("));

        assertEquals(SymbolKind.INTERFACE, repository.getSymbol(PKG + "client.FraudCheckClient").kind());
        assertEquals(SymbolKind.RECORD, repository.getSymbol(PKG + "model.PaymentRequest").kind());
        assertEquals(SymbolKind.ENUM, repository.getSymbol(PKG + "domain.PaymentStatus").kind());
        assertEquals(SymbolKind.FIELD, repository.getSymbol(PKG + "service.PaymentService#jdbcTemplate").kind());
        assertTrue(repository.getSkippedFiles().isEmpty());
    }

    @Test
    void shouldRenderInterfaceMethodsWithoutImplicitModifiers() {
        Symbol isAllowed = repository.getSymbol(PKG + "client.FraudCheckClient#isAllowed(String,long)");
        assertEquals("boolean isAllowed(String customerId, long amountCents)", isAllowed.signature());
    }

    @Test
    void shouldResolveCallsAcrossFiles() {
        assertReference(PKG + "api.PaymentController#pay(PaymentRequest)",
                PKG + "service.PaymentService#charge(PaymentRequest)", ReferenceKind.CALLS);

        // Static factory call
        assertReference(PKG + "service.PaymentService#charge(PaymentRequest)",
                PKG + "domain.Payment#pending(String,long)", ReferenceKind.CALLS);

        // Call through an interface
        assertReference(PKG + "service.PaymentService#charge(PaymentRequest)",
                PKG + "client.FraudCheckClient#isAllowed(String,long)", ReferenceKind.CALLS);

        // Declared repository method
        assertReference(PKG + "service.PaymentService#refund(long)",
                PKG + "repository.PaymentTransactionRepository#findByPaymentId(long)", ReferenceKind.CALLS);
    }

    @Test
    void shouldFallBackToReceiverTypeWhenMethodIsInheritedFromLibrary() {
        // paymentRepository.save(...) is declared by Spring's JpaRepository, which is not on the classpath
        assertReference(PKG + "service.PaymentService#charge(PaymentRequest)",
                PKG + "repository.PaymentRepository", ReferenceKind.REFERENCES);
    }

    @Test
    void shouldExtractTypeHierarchyAndOverrides() {
        assertReference(PKG + "client.HttpFraudCheckClient", PKG + "client.FraudCheckClient",
                ReferenceKind.IMPLEMENTS);
        assertReference(PKG + "client.HttpFraudCheckClient#isAllowed(String,long)",
                PKG + "client.FraudCheckClient#isAllowed(String,long)", ReferenceKind.OVERRIDES);
    }

    @Test
    void shouldExtractContainmentFieldsTypeArgumentsAndImports() {
        assertReference(PKG + "service.PaymentService", PKG + "service.PaymentService#charge(PaymentRequest)",
                ReferenceKind.CONTAINS);
        assertReference(PKG + "service.PaymentService#refund(long)", PKG + "service.PaymentService#jdbcTemplate",
                ReferenceKind.REFERENCES);
        assertReference(PKG + "repository.PaymentRepository", PKG + "domain.Payment", ReferenceKind.REFERENCES);
        assertReference(PKG + "domain.Payment#pending(String,long)", PKG + "domain.PaymentStatus",
                ReferenceKind.REFERENCES);
        assertReference(PKG + "service.PaymentService", PKG + "client.FraudCheckClient", ReferenceKind.IMPORTS);
    }

    @Test
    void shouldExtractDataAccessHints() {
        assertDataAccess(PKG + "domain.Payment", DataAccessKind.ENTITY_TABLE, "payments");
        assertDataAccess(PKG + "domain.PaymentTransaction", DataAccessKind.ENTITY_TABLE, "payment_transactions");
        assertDataAccess(PKG + "repository.PaymentRepository#findByStatus(PaymentStatus)",
                DataAccessKind.JPQL_QUERY, "Payment");
        assertDataAccess(PKG + "repository.PaymentTransactionRepository#findByPaymentId(long)",
                DataAccessKind.NATIVE_QUERY, "payment_transactions");
        assertDataAccess(PKG + "service.PaymentService#refund(long)", DataAccessKind.SQL_LITERAL, "payments");
    }

    @Test
    void shouldIgnoreNonSqlStringsAndReadConcatenatedSql(@TempDir Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("src/main/java/acme"));
        Files.writeString(dir.resolve("Dao.java"), """
                package acme;

                @Entity
                class Order {
                }

                class Dao {
                    private static final String STATUS = "Updating from cache";
                    private static final String FIND = "SELECT o.id FROM orders o "
                            + "JOIN customers c ON c.id = o.customer_id";

                    void archive() {
                        run(\"""
                            INSERT INTO order_archive
                            SELECT * FROM orders
                            \""");
                    }

                    void run(String sql) {
                    }
                }
                """);

        CodeRepository repo = new JavaRepositorySource(root).load();

        assertEquals("Order", repo.getDataAccess("acme.Order").get(0).name());
        assertEquals(DataAccessKind.ENTITY_NAME, repo.getDataAccess("acme.Order").get(0).kind());
        assertTrue(repo.getDataAccess("acme.Dao#STATUS").isEmpty());
        assertEquals("[orders, customers]", names(repo, "acme.Dao#FIND"));
        assertEquals("[order_archive, orders]", names(repo, "acme.Dao#archive()"));
        assertTrue(repo.getDataAccess("acme.Dao#run(String)").isEmpty());
    }

    @Test
    void shouldSkipFilesThatDoNotParse(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("Broken.java"), "class Broken { void m( }");
        Files.writeString(root.resolve("Fine.java"), "class Fine { }");

        CodeRepository repo = new JavaRepositorySource(root).load();

        assertEquals(1, repo.getFiles().size());
        assertTrue(repo.containsSymbol("Fine"));
        assertEquals(java.util.List.of("Broken.java"), repo.getSkippedFiles());
    }

    private static String names(CodeRepository repo, String symbolId) {
        return repo.getDataAccess(symbolId).stream().map(DataAccess::name).toList().toString();
    }

    private static void assertReference(String source, String target, ReferenceKind kind) {
        assertTrue(repository.getReferences().stream()
                        .anyMatch(r -> r.sourceId().equals(source) && r.targetId().equals(target) && r.kind() == kind),
                source + " -" + kind + "-> " + target);
    }

    private static void assertDataAccess(String symbolId, DataAccessKind kind, String name) {
        assertTrue(repository.getDataAccess(symbolId).stream()
                        .anyMatch(d -> d.kind() == kind && d.name().equals(name)),
                symbolId + " should access " + name + " [" + kind + "] but has "
                        + repository.getDataAccess(symbolId));
    }
}
