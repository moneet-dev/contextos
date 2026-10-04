package dev.moneet.contextos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {

    private static final Path EXAMPLES = Path.of(System.getProperty("contextos.examples"));

    @Test
    void shouldLoadTheExampleConfig() {
        Workspace workspace = Workspace.load(EXAMPLES);

        assertTrue(workspace.hasRuntime());
        assertEquals(4, workspace.runtime().getIncidents().size());
        assertEquals(Set.of("payment-service"), workspace.repositories().keySet());
        assertEquals(Set.of("payments-db"), workspace.databases().keySet());
        assertEquals(EXAMPLES.resolve("runtime").toAbsolutePath().normalize(), workspace.runtimeDir());
        assertNotNull(workspace.contextOS());
    }

    @Test
    void shouldReadSchemasOverJdbcWithPasswordsFromTheEnvironment(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("orders.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE customers (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, customer_id INTEGER, "
                    + "FOREIGN KEY(customer_id) REFERENCES customers(id))");
        }
        write(dir, """
                {"databases": {"orders": {"jdbcUrl": "jdbc:sqlite:%s", "user": "reader",
                                          "passwordEnv": "ORDERS_DB_PASSWORD"}}}
                """.formatted(db.toString().replace('\\', '/')));

        Workspace workspace = Workspace.load(dir, Map.of("ORDERS_DB_PASSWORD", "secret")::get);

        assertEquals(2, workspace.databases().get("orders").getTables().size());
        assertTrue(workspace.databases().get("orders").containsTable("orders"));
        assertFalse(workspace.hasRuntime());
        assertThrows(IllegalStateException.class, workspace::contextOS);
    }

    @Test
    void shouldAskForAMissingPasswordVariable(@TempDir Path dir) throws Exception {
        write(dir, """
                {"databases": {"orders": {"jdbcUrl": "jdbc:sqlite::memory:", "passwordEnv": "ORDERS_DB_PASSWORD"}}}
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Workspace.load(dir, name -> null));

        assertEquals("Database 'orders': set the ORDERS_DB_PASSWORD environment variable to its password",
                e.getMessage());
    }

    @Test
    void shouldExplainAMissingDriverWithoutLeakingCredentials(@TempDir Path dir) throws Exception {
        write(dir, """
                {"databases": {"legacy": {"jdbcUrl": "jdbc:oracle:thin://scott:tiger@db.example.com:1521/orcl?ssl=true"}}}
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Workspace.load(dir));

        assertTrue(e.getMessage().startsWith("Database 'legacy' (jdbc:oracle:thin://db.example.com:1521/orcl): "
                + "no JDBC driver"), e.getMessage());
        assertFalse(e.getMessage().contains("tiger"));
    }

    @Test
    void shouldRejectMistakesInTheConfig(@TempDir Path dir) throws Exception {
        write(dir, "{\"repository\": {\"a\": \"a\"}}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Workspace.load(dir))
                .getMessage().contains("unknown key \"repository\""));

        write(dir, "{\"databases\": {\"db\": {\"jdbcUrl\": \"jdbc:sqlite::memory:\", \"ddl\": \"x.sql\"}}}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Workspace.load(dir))
                .getMessage().contains("exactly one of \"jdbcUrl\" or \"ddl\""));

        write(dir, "{\"repositories\": {\"app\": \"missing-dir\"}}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Workspace.load(dir))
                .getMessage().startsWith("Repository 'app' not found at"));

        Path empty = Files.createDirectories(dir.resolve("empty"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Workspace.load(empty))
                .getMessage().startsWith("No contextos.json at"));
    }

    @Test
    void shouldRedactCredentialsAndParameters() {
        assertEquals("jdbc:postgresql://db:5432/app", Workspace.redact("jdbc:postgresql://user:pw@db:5432/app?ssl=1"));
        assertEquals("jdbc:sqlserver://db:1433", Workspace.redact("jdbc:sqlserver://db:1433;password=pw"));
    }

    private static void write(Path dir, String json) throws Exception {
        Files.writeString(dir.resolve("contextos.json"), json);
    }
}
