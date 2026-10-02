package dev.moneet.contextos.code;

import dev.moneet.contextos.code.context.CodeContext;
import dev.moneet.contextos.code.context.CodeContextEngine;
import dev.moneet.contextos.code.context.CodeContextItem;
import dev.moneet.contextos.code.context.FocusedCodeStrategy;
import dev.moneet.contextos.code.context.RenderMode;
import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.DataAccess;
import dev.moneet.contextos.code.graph.CodeGraph;
import dev.moneet.contextos.code.graph.CodeGraphBuilder;
import dev.moneet.contextos.code.graph.Direction;
import dev.moneet.contextos.code.source.JavaRepositorySource;

import java.nio.file.Path;

/**
 * Usage: {@code ./gradlew :contextos-code:demo [--args="<source-root> <query> <depth>"]}
 */
public class CodeContextDemo {

    public static void main(String[] args) {
        Path root = Path.of(args.length > 0 ? args[0] : "examples/payment-service");

        System.out.println("========================================");
        System.out.println("   Code Context Demo");
        System.out.println("========================================\n");

        System.out.println("1. LOADING REPOSITORY: " + root + "\n");
        CodeRepository repository = new JavaRepositorySource(root).load();
        System.out.println(repository);
        if (!repository.getSkippedFiles().isEmpty()) {
            System.out.println("Skipped (parse errors): " + repository.getSkippedFiles());
        }

        System.out.println("\nDATA ACCESS HINTS:");
        System.out.println("-".repeat(80));
        for (DataAccess access : repository.getDataAccess()) {
            System.out.println("  " + repository.getSymbol(access.symbolId()).displayName() + " -> " + access);
        }

        System.out.println("\n2. BUILDING CODE GRAPH...\n");
        CodeGraph graph = new CodeGraphBuilder().build(repository);
        System.out.println(graph);

        System.out.println("\n3. GENERATING CODE CONTEXTS...\n");

        if (args.length > 1) {
            int depth = args.length > 2 ? Integer.parseInt(args[2]) : 2;
            print("FOCUSED CONTEXT: '" + args[1] + "' (depth " + depth + ")",
                    new FocusedCodeStrategy(args[1], depth), repository, graph);
            return;
        }

        print("A. FOCUSED CONTEXT: 'PaymentService#charge' (depth 2, both directions)",
                new FocusedCodeStrategy("PaymentService#charge", 2, Direction.BOTH, RenderMode.SIGNATURES, 15),
                repository, graph);

        print("B. WHO DEPENDS ON 'FraudCheckClient'? (depth 2, incoming)",
                new FocusedCodeStrategy("FraudCheckClient", 2, Direction.INCOMING, RenderMode.SIGNATURES, 10),
                repository, graph);

        print("C. FULL SOURCE: 'PaymentService#refund' (depth 1, outgoing)",
                new FocusedCodeStrategy("PaymentService#refund", 1, Direction.OUTGOING, RenderMode.FULL, 10),
                repository, graph);

        System.out.println("========================================");
        System.out.println("   Demo Complete");
        System.out.println("========================================");
    }

    private static void print(String title,
                              FocusedCodeStrategy strategy,
                              CodeRepository repository,
                              CodeGraph graph) {

        System.out.println(title);
        System.out.println("-".repeat(80));

        CodeContext context = new CodeContextEngine(strategy).generate(repository, graph);

        System.out.println("Ranking:");
        for (CodeContextItem item : context.items()) {
            System.out.printf("  %.2f  d=%d  %-55s %s%n",
                    item.score(), item.distance(), item.symbol().displayName(), item.reason());
        }
        System.out.println();
        System.out.println(context.rendered());
    }
}
