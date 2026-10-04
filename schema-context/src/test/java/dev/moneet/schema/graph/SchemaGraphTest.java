package dev.moneet.schema.graph;

import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class SchemaGraphTest {

    @Test
    void shouldBuildDependencyGraphAndPrintTree() throws Exception {

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

        System.out.println("\n====== DEPENDENCY TREE ======");
        System.out.println(graph.toTree());

        assertTrue(graph.getDependencies("orders").contains("users"));
        assertTrue(graph.getDependents("users").contains("orders"));
    }
}
