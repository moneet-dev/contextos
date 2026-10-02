package dev.moneet.contextos;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.source.JavaRepositorySource;
import dev.moneet.contextos.core.context.ContextBudget;
import dev.moneet.contextos.core.context.ContextItem;
import dev.moneet.contextos.core.context.ContextPackage;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.source.FileRuntimeSource;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Usage: {@code ./gradlew :contextos:demo [--args="<incident-id> <token-budget>"]}
 */
public class ContextOsDemo {

    public static void main(String[] args) throws Exception {
        String incidentId = args.length > 0 ? args[0] : "INC-143";
        int budget = args.length > 1 ? Integer.parseInt(args[1]) : 4000;
        Path examples = Path.of("examples");

        System.out.println("========================================");
        System.out.println("   ContextOS: " + incidentId);
        System.out.println("========================================\n");

        System.out.println("1. LOADING SOURCES\n");
        RuntimeSnapshot runtime = new FileRuntimeSource(examples.resolve("runtime")).load();
        System.out.println("  runtime     " + runtime);

        CodeRepository code = new JavaRepositorySource(examples.resolve("payment-service")).load();
        System.out.println("  code        " + code);

        DatabaseSchema schema;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SqlScript.run(connection, examples.resolve("runtime/databases/payments-db.sql"));
            schema = new JdbcSchemaMetadataSource(connection, null).load();
        }
        System.out.println("  database    payments-db, " + schema.getTables().size() + " tables");

        ContextOS contextOS = ContextOS.builder()
                .runtime(runtime)
                .repository("payment-service", code)
                .database("payments-db", schema)
                .build();

        System.out.println("\n2. INVESTIGATING " + incidentId + " (budget " + budget + " tokens)\n");
        CrossDomainContext context = contextOS.investigate(incidentId, ContextBudget.tokens(budget));
        ContextPackage pkg = context.contextPackage();

        System.out.println("-".repeat(80));
        System.out.println(pkg.rendered());
        System.out.println("-".repeat(80));

        System.out.println("\n3. SUMMARY\n");
        System.out.println("  links followed   " + context.links().size());
        System.out.println("  tokens used      " + pkg.usedTokens() + " of " + budget);
        System.out.println("  included         " + countByDomain(pkg, true));
        System.out.println("  omitted          " + countByDomain(pkg, false));

        System.out.println("\n========================================");
        System.out.println("   Demo Complete");
        System.out.println("========================================");
    }

    private static Map<String, Long> countByDomain(ContextPackage pkg, boolean included) {
        return (included ? pkg.included() : pkg.omitted()).stream()
                .map(ContextPackage.Entry::item)
                .collect(Collectors.groupingBy(ContextItem::domain, TreeMap::new, Collectors.counting()));
    }
}
