package dev.moneet.contextos.incident.domain;

import java.util.List;
import java.util.Objects;

/** Topology, telemetry and incidents for one environment, loaded once. */
public final class RuntimeSnapshot {

    private final ServiceTopology topology;
    private final Telemetry telemetry;
    private final List<Incident> incidents;

    public RuntimeSnapshot(ServiceTopology topology, Telemetry telemetry, List<Incident> incidents) {
        this.topology = Objects.requireNonNull(topology, "topology must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.incidents = List.copyOf(incidents);
    }

    public ServiceTopology getTopology() {
        return topology;
    }

    public Telemetry getTelemetry() {
        return telemetry;
    }

    public List<Incident> getIncidents() {
        return incidents;
    }

    public Incident getIncident(String id) {
        return incidents.stream()
                .filter(i -> i.id().equalsIgnoreCase(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
    }

    @Override
    public String toString() {
        return "RuntimeSnapshot{" +
                "topology=" + topology +
                ", telemetry=" + telemetry +
                ", incidents=" + incidents.size() +
                '}';
    }
}
