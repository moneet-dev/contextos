package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.ChangeEvent;
import dev.moneet.contextos.incident.domain.ChangeType;
import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.domain.LogEntry;
import dev.moneet.contextos.incident.domain.LogLevel;
import dev.moneet.contextos.incident.domain.MetricSample;
import dev.moneet.contextos.incident.domain.Severity;
import dev.moneet.contextos.incident.domain.SourceRef;
import dev.moneet.contextos.incident.domain.Span;
import dev.moneet.contextos.incident.domain.SpanStatus;
import dev.moneet.contextos.incident.domain.Telemetry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceCollectorsTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final AnalysisWindow WINDOW = AnalysisWindow.around(
            new Incident("INC-1", "t", Severity.SEV3, START, null, List.of("a"), List.of()));
    private static final Set<String> SCOPE = Set.of("a", "db");
    private static final SourceRef REF = new SourceRef("f", 1);

    // ---------------------------------------------------------------- logs

    @Test
    void shouldGroupLogsThatDifferOnlyInNumbersAndIds() {
        List<LogEntry> logs = List.of(
                log(-1, LogLevel.ERROR, "Charge failed for customer 4021"),
                log(2, LogLevel.ERROR, "Charge failed for customer 7713"),
                log(3, LogLevel.ERROR, "Charge failed for order 3f2a1c9e-1111-2222-3333-444455556666"),
                log(4, LogLevel.WARN, "Charge failed for customer 1"),
                log(5, LogLevel.INFO, "Charge failed for customer 2"));

        List<Evidence> evidence = new LogEvidenceCollector().collect(telemetry(logs), WINDOW, SCOPE);

        assertEquals(3, evidence.size(), "two ERROR templates and one WARN; INFO is ignored");
        Evidence customers = evidence.get(0);
        assertEquals(EvidenceKind.ERROR_LOGS, customers.kind());
        assertEquals(2, customers.occurrences());
        assertEquals(START.minusSeconds(60), customers.onset());
        assertEquals(START.plusSeconds(120), customers.lastSeen());
        assertTrue(customers.summary().startsWith("2x ERROR [svc.Logger] Charge failed for customer 4021"));
        assertEquals(EvidenceKind.WARNING_LOGS, evidence.get(2).kind());
    }

    @Test
    void shouldIgnoreLogsOutsideWindowOrScope() {
        List<LogEntry> logs = List.of(
                log(-20, LogLevel.ERROR, "too early"),
                log(31, LogLevel.ERROR, "too late"),
                new LogEntry(START, "elsewhere", LogLevel.ERROR, null, "other service", null, null, REF));

        assertTrue(new LogEvidenceCollector().collect(telemetry(logs), WINDOW, SCOPE).isEmpty());
    }

    @Test
    void shouldSummarizeRootCauseOfChainedExceptions() {
        assertEquals("SQLTransientConnectionException: pool exhausted",
                LogEvidenceCollector.rootCause("org.x.CannotCreateTransactionException: Could not open; "
                        + "nested exception is java.sql.SQLTransientConnectionException: pool exhausted"));
        assertEquals("IllegalStateException: bad",
                LogEvidenceCollector.rootCause("java.lang.IllegalStateException: bad"));
    }

    // ---------------------------------------------------------------- metrics

    @Test
    void shouldDetectMetricStepChange() {
        List<MetricSample> samples = new ArrayList<>();
        for (int minute = -60; minute <= 30; minute++) {
            double value = minute < -2 ? 25 : 105;
            samples.add(new MetricSample(START.plus(Duration.ofMinutes(minute)), "a", "http_5xx", value, REF));
        }

        List<Evidence> evidence = new MetricEvidenceCollector().collect(telemetry(samples), WINDOW, SCOPE);

        assertEquals(1, evidence.size());
        Evidence anomaly = evidence.get(0);
        assertEquals(START.minus(Duration.ofMinutes(2)), anomaly.onset());
        assertEquals("increase", anomaly.attributes().get("direction"));
        assertTrue(anomaly.summary().contains("(+320%)"), anomaly.summary());
        assertEquals(1.0, anomaly.strength());
    }

    @Test
    void shouldIgnoreNoiseAndSingleBlips() {
        List<MetricSample> samples = new ArrayList<>();
        for (int minute = -60; minute <= 30; minute++) {
            double value = minute == 5 ? 500 : 120 + (minute % 5);
            samples.add(new MetricSample(START.plus(Duration.ofMinutes(minute)), "a", "latency", value, REF));
        }

        assertTrue(new MetricEvidenceCollector().collect(telemetry(samples), WINDOW, SCOPE).isEmpty());
    }

    @Test
    void shouldRequireBaselineSamples() {
        List<MetricSample> samples = List.of(
                new MetricSample(START, "a", "new_metric", 10, REF),
                new MetricSample(START.plusSeconds(60), "a", "new_metric", 1000, REF));

        assertTrue(new MetricEvidenceCollector().collect(telemetry(samples), WINDOW, SCOPE).isEmpty());
    }

    // ---------------------------------------------------------------- traces

    @Test
    void shouldReportFailedSpansWithFailureRateAndCommonError() {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean failed = i < 8;
            spans.add(span(i, "POST /pay", null, 200, failed ? SpanStatus.ERROR : SpanStatus.OK,
                    failed ? (i == 0 ? "rare" : "timeout") : null));
        }

        List<Evidence> evidence = new TraceEvidenceCollector().collect(telemetry(spans), WINDOW, SCOPE);

        assertEquals(1, evidence.size());
        assertEquals(EvidenceKind.FAILED_SPANS, evidence.get(0).kind());
        assertEquals("a POST /pay: 8 of 10 spans failed (timeout)", evidence.get(0).summary());
        assertEquals(0.9, evidence.get(0).strength(), 1e-9);
    }

    @Test
    void shouldReportSlowSpansAgainstBaselineAndAttributePeer() {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            spans.add(span(-40 + i, "SELECT t", "db", 10, SpanStatus.OK, null));
            spans.add(span(i, "SELECT t", "db", 8000, SpanStatus.OK, null));
        }

        List<Evidence> evidence = new TraceEvidenceCollector().collect(telemetry(spans), WINDOW, Set.of("db"));

        assertEquals(1, evidence.size());
        Evidence slow = evidence.get(0);
        assertEquals(EvidenceKind.SLOW_SPANS, slow.kind());
        assertEquals("db", slow.peer());
        assertTrue(slow.summary().contains("median 8,000 ms vs 10.0 ms baseline (x800)"), slow.summary());
    }

    @Test
    void shouldNotReportTimeoutsAsSlowSpans() {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            spans.add(span(-40 + i, "POST /pay", null, 200, SpanStatus.OK, null));
            spans.add(span(i, "POST /pay", null, 30000, SpanStatus.ERROR, "timeout"));
            spans.add(span(i, "POST /pay", null, 210, SpanStatus.OK, null));
        }

        List<Evidence> evidence = new TraceEvidenceCollector().collect(telemetry(spans), WINDOW, SCOPE);

        assertEquals(List.of(EvidenceKind.FAILED_SPANS), evidence.stream().map(Evidence::kind).toList());
    }

    // ---------------------------------------------------------------- changes

    @Test
    void shouldReportChangesFromThePreviousDay() {
        List<ChangeEvent> changes = List.of(
                new ChangeEvent(START.minus(Duration.ofHours(20)), "a", ChangeType.DEPLOY, "v2", REF),
                new ChangeEvent(START.minus(Duration.ofHours(30)), "a", ChangeType.DEPLOY, "v1", REF),
                new ChangeEvent(START.minus(Duration.ofMinutes(5)), "other", ChangeType.JOB, "x", REF));

        List<Evidence> evidence = new ChangeEvidenceCollector().collect(
                new Telemetry(List.of(), List.of(), List.of(), changes), WINDOW, SCOPE);

        assertEquals(1, evidence.size());
        assertEquals("DEPLOY: v2", evidence.get(0).summary());
    }

    // ---------------------------------------------------------------- helpers

    private static LogEntry log(int minutes, LogLevel level, String message) {
        return new LogEntry(START.plus(Duration.ofMinutes(minutes)), "a", level, "svc.Logger", message, null, null, REF);
    }

    private static Span span(int minutes, String operation, String peer, long durationMs, SpanStatus status, String error) {
        return new Span("t" + minutes, "s" + minutes, null, "a", operation, peer,
                START.plus(Duration.ofMinutes(minutes)), durationMs, status, error, REF);
    }

    @SuppressWarnings("unchecked")
    private static Telemetry telemetry(List<?> records) {
        if (records.isEmpty() || records.get(0) instanceof LogEntry) {
            return new Telemetry((List<LogEntry>) records, List.of(), List.of(), List.of());
        }
        if (records.get(0) instanceof MetricSample) {
            return new Telemetry(List.of(), (List<MetricSample>) records, List.of(), List.of());
        }
        return new Telemetry(List.of(), List.of(), (List<Span>) records, List.of());
    }
}
