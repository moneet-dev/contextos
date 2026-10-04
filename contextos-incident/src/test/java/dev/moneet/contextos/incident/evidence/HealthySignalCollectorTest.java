package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.HealthySignal;
import dev.moneet.contextos.incident.domain.Incident;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthySignalCollectorTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final AnalysisWindow WINDOW = AnalysisWindow.around(
            new Incident("INC-1", "t", Severity.SEV3, START, null, List.of("api"), List.of()));
    private static final SourceRef REF = new SourceRef("f", 1);

    @Test
    void shouldReportMetricsThatStayedWithinBaseline() {
        List<HealthySignal> signals = collect(new Telemetry(List.of(), metrics("db", "query_ms", 9, 9),
                List.of(), List.of()), List.of("db"), List.of());

        assertEquals(1, signals.size());
        assertEquals("db", signals.get(0).service());
        assertEquals("query_ms 9.0 to 9.0 during the window (baseline 9.0)", signals.get(0).summary());
    }

    @Test
    void shouldLeaveOutAnomaliesAndServicesNotAsked() {
        Telemetry telemetry = new Telemetry(List.of(), concat(
                metrics("db", "query_ms", 9, 9000),
                metrics("other", "rps", 5, 5)), List.of(), List.of());
        Evidence anomaly = new Evidence(EvidenceKind.METRIC_ANOMALY, "db", null, START, START, 1, 1.0, "slow",
                Map.of("metric", "query_ms"), List.of());

        assertTrue(collect(telemetry, List.of("db"), List.of(anomaly)).isEmpty());
    }

    @Test
    void shouldReportDependencyCallsWithNormalLatencyAndNoErrors() {
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            spans.add(span(-40 + i, "SELECT t", "db", 10, SpanStatus.OK));
            spans.add(span(i, "SELECT t", "db", 12, SpanStatus.OK));
            spans.add(span(i, "POST /check", "fraud", 100, i == 0 ? SpanStatus.ERROR : SpanStatus.OK));
            spans.add(span(-40 + i, "POST /check", "fraud", 100, SpanStatus.OK));
            spans.add(span(i, "GET /new", "db", 5, SpanStatus.OK));
        }

        List<HealthySignal> signals = collect(new Telemetry(List.of(), List.of(), spans, List.of()),
                List.of("api", "db", "fraud"), List.of());

        assertEquals(1, signals.size(), "a failed call and calls without a baseline are not healthy");
        assertEquals("SELECT t -> db: median 12.0 ms (baseline 10.0 ms), 4 calls, no errors",
                signals.get(0).summary());
    }

    @Test
    void shouldCapTheNumberOfSignals() {
        Telemetry telemetry = new Telemetry(List.of(), concat(
                metrics("db", "a", 1, 1), metrics("db", "b", 1, 1), metrics("db", "c", 1, 1)),
                List.of(), List.of());

        assertEquals(2, new HealthySignalCollector(2).collect(telemetry, WINDOW, List.of("db"), List.of()).size());
    }

    private static List<HealthySignal> collect(Telemetry telemetry, List<String> services, List<Evidence> anomalies) {
        return new HealthySignalCollector(12).collect(telemetry, WINDOW, services, anomalies);
    }

    /** One sample a minute from an hour before to 30 minutes after the start. */
    private static List<MetricSample> metrics(String service, String metric, double baseline, double during) {
        List<MetricSample> samples = new ArrayList<>();
        for (int minute = -60; minute <= 30; minute++) {
            samples.add(new MetricSample(START.plus(Duration.ofMinutes(minute)), service, metric,
                    minute < -15 ? baseline : during, REF));
        }
        return samples;
    }

    private static Span span(int minutes, String operation, String peer, long durationMs, SpanStatus status) {
        return new Span("t", "s" + minutes, null, "api", operation, peer, START.plus(Duration.ofMinutes(minutes)),
                durationMs, status, status == SpanStatus.ERROR ? "boom" : null, REF);
    }

    @SafeVarargs
    private static <T> List<T> concat(List<T>... lists) {
        List<T> all = new ArrayList<>();
        for (List<T> list : lists) {
            all.addAll(list);
        }
        return all;
    }
}
