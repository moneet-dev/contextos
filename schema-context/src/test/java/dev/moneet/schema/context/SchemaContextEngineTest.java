package dev.moneet.schema.context;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.graph.SchemaGraph;
import dev.moneet.schema.graph.SchemaGraphBuilder;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaContextEngineTest {

    @Test
    void shouldGenerateFocusedContext() throws Exception {

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

        DatabaseSchema schema =
                new JdbcSchemaMetadataSource(conn, null).load();

        SchemaGraph graph =
                new SchemaGraphBuilder().build(schema);

        ContextStrategy strategy =
                new FocusedSchemaStrategy("orders", 1);

        SchemaContextEngine engine =
                new SchemaContextEngine(strategy);

        String output = engine.generate(schema, graph);

        assertTrue(output.contains("Table orders"));
        assertTrue(output.contains("Table users"));
    }
}
