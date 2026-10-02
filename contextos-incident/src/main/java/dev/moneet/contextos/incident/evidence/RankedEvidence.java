package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;

/**
 * Evidence with its score and explanation. {@code distance} is the graph distance
 * from the affected services to the closer of the evidence's service and peer;
 * {@code reason} says how that service relates to the incident and when the
 * signal started relative to it.
 */
public record RankedEvidence(Evidence evidence, double score, int distance, String reason, ScoreBreakdown breakdown) {
}
