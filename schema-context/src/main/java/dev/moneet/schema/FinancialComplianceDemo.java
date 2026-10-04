package dev.moneet.schema;

import dev.moneet.schema.context.ContextStrategy;
import dev.moneet.schema.context.FocusedSchemaStrategy;
import dev.moneet.schema.context.FullSchemaStrategy;
import dev.moneet.schema.context.SchemaContextEngine;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.graph.*;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;


public class FinancialComplianceDemo {

    public static void main(String[] args) throws Exception {

        banner();

        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        createProductionDatabase(conn);

        System.out.println("\n1. LOADING SCHEMA...");
        DatabaseSchema schema =
                new JdbcSchemaMetadataSource(conn, null).load();

        System.out.println("\n2. BUILDING GRAPH...");
        SchemaGraph graph =
                new SchemaGraphBuilder().build(schema);

        displaySchema(schema);
        displayGraphInsights(graph);
        displayShortestPathExamples(schema, graph);

        System.out.println("\n3. CONTEXT GENERATION...");
        demonstrateContextStrategies(schema, graph);

        conn.close();
        System.out.println("\nDemo complete.");
    }

    // =====================================================
    // Banner
    // =====================================================

    private static void banner() {
        System.out.println("========================================");
        System.out.println("   Schema Context Engine (Enterprise FinOps Demo)");
        System.out.println("========================================");
    }

    private static void displaySchema(DatabaseSchema schema) {

        System.out.println("\nSCHEMA TABLES:");
        System.out.println("----------------------------------------");

        schema.getTables().forEach(table ->
                System.out.println(" - " + table.getName())
        );
    }
    // =====================================================
    // Schema Creation
    // =====================================================

    private static void createProductionDatabase(Connection conn) throws Exception {

        try (Statement stmt = conn.createStatement()) {

            stmt.execute("""
                        CREATE TABLE regulators (
                            regulator_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            agency_name TEXT NOT NULL UNIQUE,
                            jurisdiction_country TEXT NOT NULL,
                            reporting_threshold_usd DECIMAL(15, 2) NOT NULL
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE transaction_codes (
                            code_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            iso_code TEXT NOT NULL UNIQUE,
                            description TEXT NOT NULL,
                            high_risk_flag BOOLEAN NOT NULL DEFAULT 0
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE sanctions_lists (
                            list_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            list_name TEXT NOT NULL UNIQUE,
                            issuing_body TEXT NOT NULL
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE legal_entities (
                            entity_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            lei_code TEXT UNIQUE,
                            primary_name TEXT NOT NULL,
                            domicile_country TEXT NOT NULL,
                            incorporation_date DATE
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE kyc_profiles (
                            profile_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            entity_id INTEGER NOT NULL,
                            risk_rating TEXT NOT NULL CHECK(risk_rating IN ('LOW', 'MEDIUM', 'HIGH', 'UNACCEPTABLE')),
                            last_review_date DATE NOT NULL,
                            next_review_date DATE NOT NULL,
                            FOREIGN KEY(entity_id) REFERENCES legal_entities(entity_id) ON DELETE CASCADE
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE entity_sanctions_hits (
                            entity_id INTEGER NOT NULL,
                            list_id INTEGER NOT NULL,
                            match_score DECIMAL(5, 2) NOT NULL,
                            review_status TEXT NOT NULL CHECK(review_status IN ('PENDING', 'FALSE_POSITIVE', 'CONFIRMED')),
                            PRIMARY KEY (entity_id, list_id),
                            FOREIGN KEY(entity_id) REFERENCES legal_entities(entity_id),
                            FOREIGN KEY(list_id) REFERENCES sanctions_lists(list_id)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE accounts (
                            account_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            entity_id INTEGER NOT NULL,
                            account_number TEXT NOT NULL UNIQUE,
                            base_currency TEXT NOT NULL CHECK(length(base_currency) = 3),
                            status TEXT NOT NULL CHECK(status IN ('OPEN', 'BLOCKED', 'CLOSED')),
                            FOREIGN KEY(entity_id) REFERENCES legal_entities(entity_id)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE transactions (
                            transaction_id TEXT PRIMARY KEY,
                            source_account_id INTEGER,
                            dest_account_id INTEGER,
                            code_id INTEGER NOT NULL,
                            amount DECIMAL(18, 4) NOT NULL,
                            settlement_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            FOREIGN KEY(source_account_id) REFERENCES accounts(account_id),
                            FOREIGN KEY(dest_account_id) REFERENCES accounts(account_id),
                            FOREIGN KEY(code_id) REFERENCES transaction_codes(code_id)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE aml_alerts (
                            alert_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            transaction_id TEXT NOT NULL,
                            rule_triggered TEXT NOT NULL CHECK(rule_triggered IN ('VELOCITY', 'STRUCTURING', 'SANCTION_HIT', 'THRESHOLD_EXCEEDED')),
                            alert_timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            investigator_notes TEXT,
                            FOREIGN KEY(transaction_id) REFERENCES transactions(transaction_id)
                        )
                    """);

            stmt.execute("""
                        CREATE TABLE sar_filings (
                            alert_id INTEGER NOT NULL,
                            regulator_id INTEGER NOT NULL,
                            filing_date DATE NOT NULL,
                            acknowledgement_id TEXT,
                            status TEXT NOT NULL CHECK(status IN ('DRAFT', 'SUBMITTED', 'ACCEPTED', 'REJECTED')),
                            PRIMARY KEY (alert_id, regulator_id),
                            FOREIGN KEY(alert_id) REFERENCES aml_alerts(alert_id),
                            FOREIGN KEY(regulator_id) REFERENCES regulators(regulator_id)
                        )
                    """);

            // Enterprise Indexes
            stmt.execute("CREATE INDEX idx_tx_source ON transactions(source_account_id)");
            stmt.execute("CREATE INDEX idx_tx_dest ON transactions(dest_account_id)");
            stmt.execute("CREATE INDEX idx_sanctions_status ON entity_sanctions_hits(review_status)");
            stmt.execute("CREATE INDEX idx_aml_alerts_time_rule ON aml_alerts(alert_timestamp, rule_triggered)");
            stmt.execute("CREATE INDEX idx_kyc_next_review ON kyc_profiles(next_review_date)");
        }

        System.out.println("✓ In-memory enterprise compliance database created.");
    }

    // =====================================================
    // Graph Insights
    // =====================================================

    private static void displayGraphInsights(SchemaGraph graph) {

        System.out.println("\nDEPENDENCY TREE:");
        System.out.println("----------------------------------------");
        System.out.println(graph.toTree());
    }

    // =====================================================
    // Shortest Path + Join Quality
    // =====================================================

    private static void displayShortestPathExamples(DatabaseSchema schema,
                                                    SchemaGraph graph) {

        System.out.println("\nSHORTEST PATH (transactions → legal_entities, DEPENDENCIES_ONLY)");
        System.out.println("----------------------------------------");

        List<GraphEdge> path =
                graph.getShortestPathEdges(
                        "transactions",
                        "legal_entities",
                        TraversalDirection.DEPENDENCIES_ONLY
                );

        if (path.isEmpty()) {
            System.out.println("No path found.");
        } else {
            System.out.println(
                    JoinPathFormatter.formatWithQuality(path, schema)
            );
        }

        System.out.println("\nSHORTEST PATH (legal_entities → aml_alerts, DEPENDENTS_ONLY)");
        System.out.println("----------------------------------------");

        List<GraphEdge> downstreamPath =
                graph.getShortestPathEdges(
                        "legal_entities",
                        "aml_alerts",
                        TraversalDirection.DEPENDENTS_ONLY
                );

        if (downstreamPath.isEmpty()) {
            System.out.println("No path found.");
        } else {
            System.out.println(
                    JoinPathFormatter.formatWithQuality(downstreamPath, schema)
            );
        }
    }

    // =====================================================
    // Context Demonstration
    // =====================================================

    private static void demonstrateContextStrategies(DatabaseSchema schema,
                                                     SchemaGraph graph) {

        printContext("FULL SCHEMA",
                new FullSchemaStrategy(),
                schema, graph);

        printContext("FOCUSED (transactions, depth 1, DFS)",
                new FocusedSchemaStrategy(
                        "transactions",
                        1,
                        new DfsTraversalStrategy(),
                        TraversalDirection.BIDIRECTIONAL
                ),
                schema, graph);

        printContext("FOCUSED (transactions, depth 2, BFS, DEPENDENCIES_ONLY)",
                new FocusedSchemaStrategy(
                        "transactions",
                        2,
                        new BfsTraversalStrategy(),
                        TraversalDirection.DEPENDENCIES_ONLY
                ),
                schema, graph);
    }

    private static void printContext(String title,
                                     ContextStrategy strategy,
                                     DatabaseSchema schema,
                                     SchemaGraph graph) {

        System.out.println("\n" + title);
        System.out.println("----------------------------------------");

        SchemaContextEngine engine =
                new SchemaContextEngine(strategy);

        System.out.println(engine.generate(schema, graph));
    }
}