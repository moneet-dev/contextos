package dev.moneet.contextos.incident.source;

import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.nio.file.Path;

/**
 * Loads a runtime snapshot from a directory of fixture files:
 * <pre>
 * services.json              topology (required)
 * incidents.json             incidents
 * telemetry/logs.jsonl       structured logs
 * telemetry/metrics.csv      metric samples
 * telemetry/traces.jsonl     spans
 * telemetry/changes.jsonl    deploys, config changes, job runs
 * </pre>
 * Every record keeps its file and line as provenance.
 */
public class FileRuntimeSource implements RuntimeSource {

    private final Path root;
    private final TopologyReader topologyReader = new TopologyReader();
    private final IncidentReader incidentReader = new IncidentReader();
    private final TelemetryReader telemetryReader = new TelemetryReader();

    public FileRuntimeSource(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public RuntimeSnapshot load() {
        Telemetry telemetry = new Telemetry(
                telemetryReader.readLogs(root, "telemetry/logs.jsonl"),
                telemetryReader.readMetrics(root, "telemetry/metrics.csv"),
                telemetryReader.readSpans(root, "telemetry/traces.jsonl"),
                telemetryReader.readChanges(root, "telemetry/changes.jsonl"));

        return new RuntimeSnapshot(
                topologyReader.read(root, "services.json"),
                telemetry,
                incidentReader.read(root, "incidents.json"));
    }
}
