package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.Span;
import dev.moneet.contextos.incident.domain.SpanStatus;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Groups spans by (service, operation, peer) and reports:
 * <ul>
 *   <li>FAILED_SPANS: at least 3 failed spans in the window. Strength = 0.5 + 0.5 x failure rate.</li>
 *   <li>SLOW_SPANS: successful spans whose window median is at least 3x the baseline
 *       median and at least 100 ms slower. Strength grows with the slowdown: 100x or more = 1.0.</li>
 * </ul>
 * Failed spans are left out of the latency comparison so timeouts are not reported twice.
 */
public final class TraceEvidenceCollector implements EvidenceCollector {

    private static final int MIN_FAILURES = 3;
    private static final int MIN_SAMPLES = 3;
    private static final double MIN_SLOWDOWN = 3.0;
    private static final long MIN_SLOWDOWN_MS = 100;

    @Override
    public List<Evidence> collect(Telemetry telemetry, AnalysisWindow window, Set<String> services) {
        Map<String, List<Span>> groups = new LinkedHashMap<>();
        for (Span span : telemetry.getSpans()) {
            if (services.contains(span.service()) || (span.peer() != null && services.contains(span.peer()))) {
                String key = span.service() + "|" + span.operation() + "|" + span.peer();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(span);
            }
        }

        List<Evidence> evidence = new ArrayList<>();
        for (List<Span> group : groups.values()) {
            List<Span> inWindow = group.stream().filter(s -> window.contains(s.start()))
                    .sorted(Comparator.comparing(Span::start)).toList();
            List<Span> baseline = group.stream().filter(s -> window.inBaseline(s.start())).toList();

            failures(inWindow).ifPresent(evidence::add);
            slowdown(inWindow, baseline).ifPresent(evidence::add);
        }
        return evidence;
    }

    private static Optional<Evidence> failures(List<Span> inWindow) {
        List<Span> failed = inWindow.stream().filter(s -> s.status() == SpanStatus.ERROR).toList();
        if (failed.size() < MIN_FAILURES) {
            return Optional.empty();
        }

        Span first = failed.get(0);
        String error = mostCommon(failed, s -> s.error() == null ? "unknown error" : s.error());
        double rate = (double) failed.size() / inWindow.size();

        String summary = describe(first) + ": " + failed.size() + " of " + inWindow.size()
                + " spans failed (" + error + ")";

        Map<String, String> attributes = attributes(first);
        attributes.put("error", error);
        attributes.put("failureRate", String.format("%.2f", rate));

        return Optional.of(new Evidence(EvidenceKind.FAILED_SPANS, first.service(), first.peer(),
                first.start(), failed.get(failed.size() - 1).start(), failed.size(), 0.5 + 0.5 * rate,
                summary, attributes, failed.stream().limit(3).map(Span::source).toList()));
    }

    private static Optional<Evidence> slowdown(List<Span> inWindow, List<Span> baseline) {
        List<Span> ok = inWindow.stream().filter(s -> s.status() == SpanStatus.OK).toList();
        List<Long> baselineDurations = baseline.stream()
                .filter(s -> s.status() == SpanStatus.OK)
                .map(Span::durationMs)
                .toList();
        if (ok.size() < MIN_SAMPLES || baselineDurations.size() < MIN_SAMPLES) {
            return Optional.empty();
        }

        double baselineMedian = Numbers.median(baselineDurations);
        double windowMedian = Numbers.median(ok.stream().map(Span::durationMs).toList());
        double factor = windowMedian / Math.max(1, baselineMedian);
        if (factor < MIN_SLOWDOWN || windowMedian - baselineMedian < MIN_SLOWDOWN_MS) {
            return Optional.empty();
        }

        List<Span> slow = ok.stream().filter(s -> s.durationMs() > MIN_SLOWDOWN * baselineMedian).toList();
        Span first = slow.get(0);
        Instant last = slow.get(slow.size() - 1).start();

        String summary = describe(first) + ": median " + Numbers.format(windowMedian) + " ms vs "
                + Numbers.format(baselineMedian) + " ms baseline (x" + Numbers.format(factor) + "), "
                + ok.size() + " spans";

        Map<String, String> attributes = attributes(first);
        attributes.put("medianMs", Numbers.format(windowMedian));
        attributes.put("baselineMedianMs", Numbers.format(baselineMedian));

        double strength = 0.5 + 0.5 * Numbers.clamp01(Math.log10(factor) / 2);

        return Optional.of(new Evidence(EvidenceKind.SLOW_SPANS, first.service(), first.peer(),
                first.start(), last, slow.size(), strength, summary, attributes,
                slow.stream().limit(3).map(Span::source).toList()));
    }

    private static String describe(Span span) {
        return span.service() + " " + span.operation() + (span.peer() == null ? "" : " -> " + span.peer());
    }

    private static Map<String, String> attributes(Span span) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("operation", span.operation());
        if (span.peer() != null) {
            attributes.put("peer", span.peer());
        }
        return attributes;
    }

    private static String mostCommon(List<Span> spans, Function<Span, String> key) {
        return spans.stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown error");
    }
}
