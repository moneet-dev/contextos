package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.HealthySignal;
import dev.moneet.contextos.incident.domain.MetricSample;
import dev.moneet.contextos.incident.domain.Span;
import dev.moneet.contextos.incident.domain.SpanStatus;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds signals that stayed normal during the window, for the given services:
 * <ul>
 *   <li>metrics with baseline data that the metric collector did not flag</li>
 *   <li>calls (service, operation, peer) with baseline data, no failed spans in
 *       the window, and no slowdown flagged by the trace collector</li>
 * </ul>
 * Signals are listed in the order of {@code services}, metrics before calls,
 * and capped at {@code limit}.
 */
public final class HealthySignalCollector {

    private static final int MIN_SAMPLES = 3;

    private final int limit;

    public HealthySignalCollector(int limit) {
        this.limit = limit;
    }

    public List<HealthySignal> collect(Telemetry telemetry,
                                       AnalysisWindow window,
                                       Collection<String> services,
                                       List<Evidence> anomalies) {

        Set<String> anomalous = new HashSet<>();
        for (Evidence evidence : anomalies) {
            if (evidence.kind() == EvidenceKind.METRIC_ANOMALY) {
                anomalous.add(metricKey(evidence.service(), evidence.attributes().get("metric")));
            } else if (evidence.kind() == EvidenceKind.FAILED_SPANS || evidence.kind() == EvidenceKind.SLOW_SPANS) {
                anomalous.add(callKey(evidence.service(), evidence.attributes().get("operation"), evidence.peer()));
            }
        }

        List<HealthySignal> signals = new ArrayList<>();
        for (String service : services) {
            signals.addAll(metrics(telemetry, window, service, anomalous));
            signals.addAll(calls(telemetry, window, service, services, anomalous));
        }
        return signals.stream().limit(limit).toList();
    }

    private static List<HealthySignal> metrics(Telemetry telemetry, AnalysisWindow window, String service,
                                               Set<String> anomalous) {
        Map<String, List<MetricSample>> series = new LinkedHashMap<>();
        for (MetricSample sample : telemetry.getMetrics()) {
            if (sample.service().equals(service)) {
                series.computeIfAbsent(sample.metric(), k -> new ArrayList<>()).add(sample);
            }
        }

        List<HealthySignal> signals = new ArrayList<>();
        for (Map.Entry<String, List<MetricSample>> entry : series.entrySet()) {
            if (anomalous.contains(metricKey(service, entry.getKey()))) {
                continue;
            }
            List<Double> baseline = entry.getValue().stream()
                    .filter(s -> window.inBaseline(s.timestamp())).map(MetricSample::value).toList();
            List<MetricSample> inWindow = entry.getValue().stream()
                    .filter(s -> window.contains(s.timestamp()))
                    .sorted(Comparator.comparing(MetricSample::timestamp)).toList();
            if (baseline.size() < MIN_SAMPLES || inWindow.isEmpty()) {
                continue;
            }

            double min = inWindow.stream().mapToDouble(MetricSample::value).min().orElseThrow();
            double max = inWindow.stream().mapToDouble(MetricSample::value).max().orElseThrow();
            signals.add(new HealthySignal(service, entry.getKey(),
                    entry.getKey() + " " + Numbers.format(min) + " to " + Numbers.format(max)
                            + " during the window (baseline " + Numbers.format(Numbers.mean(baseline)) + ")",
                    List.of(inWindow.get(0).source())));
        }
        return signals;
    }

    /** Calls this service makes to a dependency in scope; the affected service's own endpoints are left out. */
    private static List<HealthySignal> calls(Telemetry telemetry, AnalysisWindow window, String service,
                                             Collection<String> services, Set<String> anomalous) {
        Map<String, List<Span>> groups = new LinkedHashMap<>();
        for (Span span : telemetry.getSpans()) {
            if (span.service().equals(service) && span.peer() != null && services.contains(span.peer())) {
                groups.computeIfAbsent(callKey(service, span.operation(), span.peer()), k -> new ArrayList<>())
                        .add(span);
            }
        }

        List<HealthySignal> signals = new ArrayList<>();
        for (Map.Entry<String, List<Span>> group : groups.entrySet()) {
            if (anomalous.contains(group.getKey())) {
                continue;
            }
            List<Span> inWindow = group.getValue().stream()
                    .filter(s -> window.contains(s.start()))
                    .sorted(Comparator.comparing(Span::start)).toList();
            List<Long> baseline = group.getValue().stream()
                    .filter(s -> window.inBaseline(s.start()) && s.status() == SpanStatus.OK)
                    .map(Span::durationMs).toList();
            boolean failures = inWindow.stream().anyMatch(s -> s.status() == SpanStatus.ERROR);
            if (inWindow.size() < MIN_SAMPLES || baseline.size() < MIN_SAMPLES || failures) {
                continue;
            }

            Span first = inWindow.get(0);
            String call = first.operation() + " -> " + first.peer();
            signals.add(new HealthySignal(service, call,
                    call + ": median " + Numbers.format(Numbers.median(inWindow.stream().map(Span::durationMs).toList()))
                            + " ms (baseline " + Numbers.format(Numbers.median(baseline)) + " ms), "
                            + inWindow.size() + " calls, no errors",
                    List.of(first.source())));
        }
        return signals;
    }

    private static String metricKey(String service, String metric) {
        return service + "|" + metric;
    }

    private static String callKey(String service, String operation, String peer) {
        return service + "|" + operation + "|" + peer;
    }
}
