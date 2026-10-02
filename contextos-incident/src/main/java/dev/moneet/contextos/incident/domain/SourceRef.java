package dev.moneet.contextos.incident.domain;

/** Where a telemetry record came from: a file relative to the snapshot root, and a 1-based line. */
public record SourceRef(String file, int line) {

    @Override
    public String toString() {
        return file + ":" + line;
    }
}
