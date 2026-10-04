package dev.moneet.contextos.incident.source;

import dev.moneet.contextos.incident.domain.ChangeType;
import dev.moneet.contextos.incident.domain.DependencyKind;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.LogEntry;
import dev.moneet.contextos.incident.domain.LogLevel;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.domain.ServiceKind;
import dev.moneet.contextos.incident.domain.Severity;
import dev.moneet.contextos.incident.domain.Span;
import dev.moneet.contextos.incident.domain.SpanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FileRuntimeSourceTest {

    private static final String SERVICES = """
            {"services": [{"name": "a", "kind": "SERVICE"}, {"name": "b", "kind": "DATABASE"}],
             "dependencies": [{"from": "a", "to": "b", "kind": "QUERIES"}]}
            """;

    @Test
    void shouldLoadExampleSnapshot() {
        RuntimeSnapshot snapshot =
                new FileRuntimeSource(Path.of(System.getProperty("contextos.examples"), "runtime")).load();

        assertEquals(13, snapshot.getTopology().getServices().size());
        assertEquals("payment-service", snapshot.getTopology().getService("payment-service").repository());
        assertEquals(ServiceKind.DATABASE, snapshot.getTopology().getService("payments-db").kind());

        Incident incident = snapshot.getIncident("inc-143");
        assertEquals(Severity.SEV2, incident.severity());
        assertEquals(Instant.parse("2026-09-30T14:02:00Z"), incident.startedAt());
        assertEquals(java.util.List.of("payment-service"), incident.affectedServices());

        assertEquals(4937, snapshot.getTelemetry().getLogs().size());
        assertEquals(8736, snapshot.getTelemetry().getMetrics().size());
        assertEquals(3919, snapshot.getTelemetry().getSpans().size());
        assertEquals(7, snapshot.getTelemetry().getChanges().size());
        assertEquals(4, snapshot.getIncidents().size());
    }

    @Test
    void shouldKeepProvenanceAndOptionalFields(@TempDir Path root) throws Exception {
        write(root, "services.json", SERVICES);
        write(root, "telemetry/logs.jsonl", """
                {"timestamp": "2026-01-01T10:00:00Z", "service": "a", "level": "info", "message": "up"}

                {"timestamp": "2026-01-01T10:01:00Z", "service": "a", "level": "ERROR", "logger": "x.Y",
                 "message": "boom", "exception": "java.lang.IllegalStateException: bad", "traceId": "t1"}
                """.replace("\n \"message\"", " \"message\""));
        write(root, "telemetry/metrics.csv", "timestamp,service,metric,value\n2026-01-01T10:00:00Z,a,rps,12.5\n");
        write(root, "telemetry/traces.jsonl", """
                {"traceId": "t1", "spanId": "s1", "service": "a", "operation": "q", "peer": "b",
                 "start": "2026-01-01T10:01:00Z", "durationMs": 40, "status": "ERROR", "error": "timeout"}
                """.replace("\n \"start\"", " \"start\""));
        write(root, "telemetry/changes.jsonl",
                "{\"timestamp\": \"2026-01-01T09:00:00Z\", \"service\": \"a\", \"type\": \"DEPLOY\", \"description\": \"v2\"}\n");

        RuntimeSnapshot snapshot = new FileRuntimeSource(root).load();

        assertEquals(DependencyKind.QUERIES, snapshot.getTopology().getDependencies().get(0).kind());
        assertTrue(snapshot.getIncidents().isEmpty(), "incidents.json is optional");

        LogEntry error = snapshot.getTelemetry().getLogs().get(1);
        assertEquals(LogLevel.INFO, snapshot.getTelemetry().getLogs().get(0).level());
        assertEquals(LogLevel.ERROR, error.level());
        assertEquals("x.Y", error.logger());
        assertEquals("t1", error.traceId());
        assertEquals("telemetry/logs.jsonl:3", error.source().toString());

        assertEquals(12.5, snapshot.getTelemetry().getMetrics().get(0).value());
        assertEquals("telemetry/metrics.csv:2", snapshot.getTelemetry().getMetrics().get(0).source().toString());

        Span span = snapshot.getTelemetry().getSpans().get(0);
        assertEquals("b", span.peer());
        assertNull(span.parentSpanId());
        assertEquals(SpanStatus.ERROR, span.status());
        assertEquals(40, span.durationMs());

        assertEquals(ChangeType.DEPLOY, snapshot.getTelemetry().getChanges().get(0).type());
    }

    @Test
    void shouldTreatMissingTelemetryAsEmpty(@TempDir Path root) throws Exception {
        write(root, "services.json", SERVICES);

        RuntimeSnapshot snapshot = new FileRuntimeSource(root).load();

        assertTrue(snapshot.getTelemetry().getLogs().isEmpty());
        assertTrue(snapshot.getTelemetry().getMetrics().isEmpty());
    }

    @Test
    void shouldRejectDependencyOnUnknownService(@TempDir Path root) throws Exception {
        write(root, "services.json", """
                {"services": [{"name": "a", "kind": "SERVICE"}],
                 "dependencies": [{"from": "a", "to": "ghost", "kind": "CALLS"}]}
                """);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FileRuntimeSource(root).load());
        assertTrue(e.getMessage().contains("ghost"));
    }

    @Test
    void shouldReportFileAndLineOfInvalidRecords(@TempDir Path root) throws Exception {
        write(root, "services.json", SERVICES);
        write(root, "telemetry/metrics.csv", "timestamp,service,metric,value\n2026-01-01T10:00:00Z,a,rps,lots\n");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FileRuntimeSource(root).load());
        assertTrue(e.getMessage().contains("telemetry/metrics.csv:2"), e.getMessage());
    }

    private static void write(Path root, String file, String content) throws Exception {
        Path path = root.resolve(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
