package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.util.List;
import java.util.Set;

public interface EvidenceCollector {

    /** Evidence from {@code telemetry} for {@code services}, within {@code window}. */
    List<Evidence> collect(Telemetry telemetry, AnalysisWindow window, Set<String> services);
}
