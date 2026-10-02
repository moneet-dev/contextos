package dev.moneet.contextos.incident;

import dev.moneet.contextos.incident.context.FocusedIncidentStrategy;
import dev.moneet.contextos.incident.context.IncidentContext;
import dev.moneet.contextos.incident.context.IncidentContextEngine;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.graph.ServiceGraph;
import dev.moneet.contextos.incident.graph.ServiceGraphBuilder;
import dev.moneet.contextos.incident.source.FileRuntimeSource;

import java.nio.file.Path;

/**
 * Usage: {@code ./gradlew :contextos-incident:demo [--args="<snapshot-dir> <incident-id> <depth>"]}
 */
public class IncidentContextDemo {

    public static void main(String[] args) {
        Path root = Path.of(args.length > 0 ? args[0] : "examples/runtime");
        String incidentId = args.length > 1 ? args[1] : "INC-143";
        int depth = args.length > 2 ? Integer.parseInt(args[2]) : 2;

        System.out.println("========================================");
        System.out.println("   Incident Context Demo");
        System.out.println("========================================\n");

        System.out.println("1. LOADING RUNTIME SNAPSHOT: " + root + "\n");
        RuntimeSnapshot snapshot = new FileRuntimeSource(root).load();
        System.out.println(snapshot);

        System.out.println("\n2. BUILDING SERVICE GRAPH...\n");
        ServiceGraph graph = new ServiceGraphBuilder().build(snapshot.getTopology());
        System.out.println(graph);

        System.out.println("\n3. GENERATING INCIDENT CONTEXT: " + incidentId + " (depth " + depth + ")\n");
        System.out.println("-".repeat(80));
        IncidentContext context = new IncidentContextEngine(new FocusedIncidentStrategy(incidentId, depth))
                .generate(snapshot, graph);
        System.out.println(context.rendered());

        System.out.println("========================================");
        System.out.println("   Demo Complete");
        System.out.println("========================================");
    }
}
