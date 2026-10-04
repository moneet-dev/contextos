package dev.moneet.schema.jdbc;

import dev.moneet.schema.domain.DatabaseSchema;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class JdbcSchemaIntegrationTest {

    @Test
    void shouldExtractSchemaFromSQLite() throws Exception {

        Connection conn =
                DriverManager.getConnection("jdbc:sqlite::memory:");

        try (Statement stmt = conn.createStatement()) {

            stmt.execute("""
                CREATE TABLE users (
                    id INTEGER PRIMARY KEY,
                    email TEXT
                )
            """);

            stmt.execute("""
                CREATE TABLE orders (
                    id INTEGER PRIMARY KEY,
                    user_id INTEGER,
                    FOREIGN KEY(user_id) REFERENCES users(id)
                )
            """);
        }

        JdbcSchemaMetadataSource source =
                new JdbcSchemaMetadataSource(conn, null);

        DatabaseSchema schema = source.load();

        assertEquals(2, schema.getTables().size());
        assertTrue(schema.containsTable("users"));
        assertTrue(schema.containsTable("orders"));
    }
}
