package dev.moneet.contextos.sql;

import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.core.context.ContextRequest;
import dev.moneet.schema.context.SchemaFormatter;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.graph.TraversalDirection;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SqlContextProviderTest {

    private static DatabaseSchema schema;

    @BeforeAll
    static void load() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE companies (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY,
                    company_id INTEGER,
                    FOREIGN KEY(company_id) REFERENCES companies(id)
                )
            """);
            stmt.execute("CREATE INDEX idx_users_company ON users(company_id)");
            // orders reaches companies directly and through users
            stmt.execute("""
                CREATE TABLE orders (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER,
                    billing_company_id INTEGER,
                    FOREIGN KEY(user_id) REFERENCES users(id),
                    FOREIGN KEY(billing_company_id) REFERENCES companies(id)
                )
            """);
            stmt.execute("""
                CREATE TABLE invoices (
                    id INTEGER PRIMARY KEY,
                    order_id INTEGER,
                    FOREIGN KEY(order_id) REFERENCES orders(id)
                )
            """);
        }
        schema = new JdbcSchemaMetadataSource(conn, null).load();
        conn.close();
    }

    @Test
    void shouldFollowForeignKeysWithShortestDistances() {
        List<ContextItem> items = new SqlContextProvider(schema, TraversalDirection.DEPENDENCIES_ONLY)
                .collect(ContextRequest.of("orders", 2));

        assertEquals(Map.of("orders", "0", "users", "1", "companies", "1"), distances(items));
        assertEquals("sql:orders", items.get(0).id());
        assertEquals(1.0, items.get(0).score());
        assertEquals(0.8, byTable(items).get("users").score(), 1e-9);
    }

    @Test
    void shouldIncludeReferencingTablesBidirectionallyByDefault() {
        List<ContextItem> items = new SqlContextProvider(schema).collect(ContextRequest.of("users", 1));

        assertEquals(Map.of("users", "0", "companies", "1", "orders", "1"), distances(items));
        assertEquals("target table", byTable(items).get("users").reason());
        assertEquals("references users (orders.user_id → users.id)", byTable(items).get("orders").reason());
        assertEquals("referenced by users (users.company_id → companies.id)", byTable(items).get("companies").reason());
    }

    @Test
    void shouldDescribeJoinPathsAndQuality() {
        List<ContextItem> items = new SqlContextProvider(schema).collect(ContextRequest.of("invoices", 3));

        ContextItem users = byTable(items).get("users");
        assertEquals("invoices.order_id = orders.id AND orders.user_id = users.id", users.attributes().get("join"));
        // neither invoices.order_id nor orders.user_id is indexed
        assertEquals("WEAK", users.attributes().get("joinQuality"));
        assertEquals(List.of("table users", "foreign key invoices.order_id → orders.id (WEAK)",
                "foreign key orders.user_id → users.id (WEAK)"), users.provenance());

        ContextItem companies = new SqlContextProvider(schema).collect(ContextRequest.of("users", 1)).stream()
                .filter(i -> i.title().equals("companies")).findFirst().orElseThrow();
        assertEquals("EXCELLENT", companies.attributes().get("joinQuality"),
                "users.company_id is indexed and companies.id is the primary key");
    }

    @Test
    void shouldRenderTablesWithSchemaFormatter() {
        ContextItem users = new SqlContextProvider(schema).collect(ContextRequest.of("USERS", 0)).get(0);

        assertEquals("sql:users", users.id());
        assertEquals("target table", users.reason());
        assertEquals(new SchemaFormatter().format(List.of(schema.getTable("users"))), users.content());
        assertTrue(users.content().contains("idx_users_company"));
    }

    @Test
    void shouldReturnAllTablesForBlankTarget() {
        assertEquals(4, new SqlContextProvider(schema).collect(ContextRequest.of("", 1)).size());
    }

    @Test
    void shouldRejectUnknownTable() {
        assertThrows(IllegalArgumentException.class,
                () -> new SqlContextProvider(schema).collect(ContextRequest.of("nope", 1)));
    }

    @Test
    void shouldPackUnderBudget() {
        ContextPackage pkg = new SqlContextProvider(schema)
                .provide(new ContextRequest("orders", 1, ContextBudget.tokens(60)));

        assertTrue(pkg.usedTokens() <= 60);
        assertEquals("sql:orders", pkg.items().get(0).id());
        assertFalse(pkg.omitted().isEmpty());
        assertTrue(pkg.rendered().startsWith("=== SQL CONTEXT ===\n\n[TABLE] orders - target table (score 1.00)\n"));
    }

    private static Map<String, String> distances(List<ContextItem> items) {
        return items.stream().collect(Collectors.toMap(ContextItem::title, i -> i.attributes().get("distance")));
    }

    private static Map<String, ContextItem> byTable(List<ContextItem> items) {
        return items.stream().collect(Collectors.toMap(ContextItem::title, i -> i));
    }
}
