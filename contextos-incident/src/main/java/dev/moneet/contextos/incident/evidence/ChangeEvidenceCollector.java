package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.ChangeEvent;
import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reports deploys, config changes, flag flips and job runs on in-scope services
 * from the change range (24 hours before the incident by default). A change has
 * no measurable magnitude, so its strength is a constant 0.8; recency is
 * accounted for by the ranker's timing factor.
 */
public final class ChangeEvidenceCollector implements EvidenceCollector {

    private static final double STRENGTH = 0.8;

    @Override
    public List<Evidence> collect(Telemetry telemetry, AnalysisWindow window, Set<String> services) {
        List<Evidence> evidence = new ArrayList<>();

        for (ChangeEvent change : telemetry.getChanges()) {
            if (services.contains(change.service()) && window.inChangeRange(change.timestamp())) {
                evidence.add(new Evidence(EvidenceKind.CHANGE, change.service(), null,
                        change.timestamp(), change.timestamp(), 1, STRENGTH,
                        change.type() + ": " + change.description(),
                        Map.of("type", change.type().name()),
                        List.of(change.source())));
            }
        }
        return evidence;
    }
}
