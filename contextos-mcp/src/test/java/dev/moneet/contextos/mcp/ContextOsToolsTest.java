package dev.moneet.contextos.mcp;

import dev.moneet.contextos.Workspace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ContextOsToolsTest {

    private static ContextOsTools tools;

    @BeforeAll
    static void load() {
        tools = new ContextOsTools(Workspace.load(Path.of(System.getProperty("contextos.examples"))));
    }

    @Test
    void shouldLoadExamplesWorkspace() {
        Workspace workspace = Workspace.load(Path.of(System.getProperty("contextos.examples")));

        assertEquals(java.util.Set.of("payment-service"), workspace.repositories().keySet());
        assertEquals(java.util.Set.of("payments-db"), workspace.databases().keySet());
        assertEquals(5, workspace.databases().get("payments-db").getTables().size());
    }

    @Test
    void shouldListIncidents() {
        String list = tools.listIncidents();

        assertTrue(list.startsWith("INC-143 [SEV2] payment-service 5xx elevated\n"), list);
        assertTrue(list.contains("affected: payment-service"));
        assertTrue(list.contains("Code repositories: payment-service"));
        assertTrue(list.contains("Databases: payments-db"));
    }

    @Test
    void shouldInvestigateAcrossDomainsWithinBudget() {
        String context = tools.investigateIncident("INC-143", 3000);

        assertTrue(context.startsWith("=== CONTEXTOS CONTEXT ==="));
        assertTrue(context.contains("--logger--> PaymentService"));
        assertTrue(context.contains("=== CODE CONTEXT ==="));
        assertTrue(context.contains("=== SQL CONTEXT ==="));

        String summary = context.substring(context.lastIndexOf("---\n"));
        int used = Integer.parseInt(summary.substring(4, summary.indexOf(" of ")));
        assertTrue(used <= 3000, summary);
        assertTrue(summary.contains("omitted for budget:"), summary);
    }

    @Test
    void shouldDrillIntoCodeAndSchema() {
        String code = tools.codeContext("PaymentTransactionRepository#findByPaymentId", 1, null);
        assertTrue(code.contains("SELECT * FROM payment_transactions WHERE payment_id = ?1"), code);

        String schema = tools.schemaContext("payment_transactions", 1, null);
        assertTrue(schema.contains("[TABLE] payment_transactions - target table"), schema);
        assertTrue(schema.contains("payment_id (FK -> payments.id)"), schema);
        assertTrue(schema.contains("referenced by payment_transactions"), schema);

        assertTrue(tools.schemaContext("", 1, null).contains("[TABLE] ledger_entries"));
    }

    @Test
    void shouldRejectBadInputWithReadableMessages() {
        assertEquals("Incident not found: INC-999",
                assertThrows(IllegalArgumentException.class, () -> tools.investigateIncident("INC-999", null))
                        .getMessage());
        assertEquals("No symbol matches: NoSuchThing",
                assertThrows(IllegalArgumentException.class, () -> tools.codeContext("NoSuchThing", null, null))
                        .getMessage());
        assertEquals("Table not found: nope",
                assertThrows(IllegalArgumentException.class, () -> tools.schemaContext("nope", null, null))
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> tools.investigateIncident("INC-143", 10));
        assertThrows(IllegalArgumentException.class, () -> tools.codeContext("PaymentService", 9, null));
        assertThrows(IllegalArgumentException.class, () -> tools.codeContext(" ", null, null));
    }

    @Test
    void shouldServeCodeAndSchemaWithoutARuntimeAndAskWhichDatabase(@TempDir Path dir) throws Exception {
        Path examples = Path.of(System.getProperty("contextos.examples")).toAbsolutePath();
        String ddl = examples.resolve("runtime/databases/payments-db.sql").toString().replace('\\', '/');
        java.nio.file.Files.writeString(dir.resolve("contextos.json"), """
                {"repositories": {"payments": "%s"},
                 "databases": {"primary": {"ddl": "%s"}, "replica": {"ddl": "%s"}}}
                """.formatted(examples.resolve("payment-service").toString().replace('\\', '/'), ddl, ddl));
        ContextOsTools noRuntime = new ContextOsTools(Workspace.load(dir));

        assertTrue(noRuntime.listIncidents().startsWith("No runtime is configured"));
        assertThrows(IllegalArgumentException.class, () -> noRuntime.investigateIncident("INC-143", null));
        assertTrue(noRuntime.codeContext("PaymentService#refund", 1, null).contains("PaymentService#refund"));

        IllegalArgumentException which = assertThrows(IllegalArgumentException.class,
                () -> noRuntime.schemaContext("payments", 0, null));
        assertEquals("Several databases are configured; pass database as one of [primary, replica]",
                which.getMessage());
        assertTrue(noRuntime.schemaContext("replica", "payments", 0, null).contains("Table payments:"));
        assertThrows(IllegalArgumentException.class, () -> noRuntime.schemaContext("nope", "payments", 0, null));
    }

    @Test
    void shouldRejectDirectoryWithoutRuntime(@TempDir Path empty) {
        assertThrows(IllegalArgumentException.class, () -> Workspace.load(empty));
    }
}
