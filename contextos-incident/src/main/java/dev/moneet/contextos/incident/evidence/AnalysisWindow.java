package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Incident;

import java.time.Duration;
import java.time.Instant;

/**
 * Time ranges used to collect evidence for an incident.
 *
 * @param start          beginning of the analysis window
 * @param end            end of the analysis window
 * @param baselineStart  beginning of the "normal" period that metrics and spans are compared against
 * @param baselineEnd    end of the baseline period (exclusive)
 * @param changesFrom    how far back deploys, config changes and jobs are considered
 */
public record AnalysisWindow(Instant start, Instant end, Instant baselineStart, Instant baselineEnd, Instant changesFrom) {

    /**
     * Window from 15 minutes before to 30 minutes after the incident start;
     * baseline the 45 minutes before that; changes from the preceding 24 hours.
     */
    public static AnalysisWindow around(Incident incident) {
        Instant started = incident.startedAt();
        return new AnalysisWindow(
                started.minus(Duration.ofMinutes(15)),
                started.plus(Duration.ofMinutes(30)),
                started.minus(Duration.ofMinutes(60)),
                started.minus(Duration.ofMinutes(15)),
                started.minus(Duration.ofHours(24)));
    }

    public boolean contains(Instant time) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    public boolean inBaseline(Instant time) {
        return !time.isBefore(baselineStart) && time.isBefore(baselineEnd);
    }

    public boolean inChangeRange(Instant time) {
        return !time.isBefore(changesFrom) && !time.isAfter(end);
    }
}
