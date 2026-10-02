package dev.moneet.contextos.incident.graph;

import dev.moneet.contextos.core.graph.Reached;
import dev.moneet.contextos.incident.domain.DependencyKind;
import dev.moneet.contextos.incident.domain.Service;
import dev.moneet.contextos.incident.domain.ServiceDependency;
import dev.moneet.contextos.incident.domain.ServiceKind;
import dev.moneet.contextos.incident.domain.ServiceTopology;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ServiceGraphTest {

    /*
     *   gateway -CALLS-> checkout -CALLS-> payments -QUERIES-> db
     *   batch -CALLS-> payments
     *   checkout -QUERIES-> db       (shortcut)
     *   notifier -CONSUMES-> events <-PUBLISHES- payments
     */
    private final ServiceGraph graph = new ServiceGraphBuilder().build(new ServiceTopology(
            List.of(service("gateway"), service("checkout"), service("payments"), service("db"),
                    service("batch"), service("events"), service("notifier")),
            List.of(
                    edge("gateway", "checkout", DependencyKind.CALLS),
                    edge("checkout", "payments", DependencyKind.CALLS),
                    edge("payments", "db", DependencyKind.QUERIES),
                    edge("batch", "payments", DependencyKind.CALLS),
                    edge("checkout", "db", DependencyKind.QUERIES),
                    edge("notifier", "events", DependencyKind.CONSUMES),
                    edge("payments", "events", DependencyKind.PUBLISHES))));

    @Test
    void shouldIndexDependenciesAndDependents() {
        assertEquals(List.of("db", "events"),
                graph.getDependencies("payments").stream().map(ServiceDependency::to).toList());
        assertEquals(List.of("batch", "checkout"),
                graph.getDependents("payments").stream().map(ServiceDependency::from).toList());
    }

    @Test
    void shouldTraverseDependenciesOnly() {
        assertEquals(Map.of("checkout", 0, "payments", 1, "db", 1, "events", 2),
                distances(graph.traverse(List.of("checkout"), 3, Direction.DEPENDENCIES)));
    }

    @Test
    void shouldTraverseDependentsOnly() {
        assertEquals(Map.of("db", 0, "payments", 1, "checkout", 1, "batch", 2, "gateway", 2),
                distances(graph.traverse(List.of("db"), 2, Direction.DEPENDENTS)));
    }

    @Test
    void shouldTraverseBothDirectionsWithShortestPaths() {
        List<Reached<ServiceDependency>> reached = graph.traverse(List.of("payments"), 2, Direction.BOTH);

        assertEquals(Map.of("payments", 0, "db", 1, "events", 1, "checkout", 1, "batch", 1,
                "notifier", 2, "gateway", 2), distances(reached));

        Reached<ServiceDependency> notifier = reached.stream().filter(r -> r.id().equals("notifier")).findFirst().orElseThrow();
        assertEquals(List.of("payments", "events", "notifier"), notifier.nodes());
        assertEquals(DependencyKind.CONSUMES, notifier.lastEdge().orElseThrow().kind());
    }

    @Test
    void shouldIgnoreUnknownStartServices() {
        assertTrue(graph.traverse(List.of("ghost"), 2, Direction.BOTH).isEmpty());
    }

    private static Map<String, Integer> distances(List<Reached<ServiceDependency>> reached) {
        return reached.stream().collect(Collectors.toMap(Reached::id, Reached::distance));
    }

    private static Service service(String name) {
        return new Service(name, ServiceKind.SERVICE, null, null);
    }

    private static ServiceDependency edge(String from, String to, DependencyKind kind) {
        return new ServiceDependency(from, to, kind);
    }
}
