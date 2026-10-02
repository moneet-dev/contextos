package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.MetricSample;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Flags a metric whose value leaves its baseline range during the window.
 *
 * <p>A sample is anomalous when it deviates from the baseline mean by more than
 * {@code max(3 standard deviations, 50% of the mean)}. The onset is the first of
 * two consecutive anomalous samples, so single blips are ignored. Strength grows
 * with the relative change: +200% or more = 1.0.
 */
public final class MetricEvidenceCollector implements EvidenceCollector {

    private static final int MIN_BASELINE_SAMPLES = 3;

    @Override
    public List<Evidence> collect(Telemetry telemetry, AnalysisWindow window, Set<String> services) {
        Map<String, List<MetricSample>> series = new LinkedHashMap<>();
        for (MetricSample sample : telemetry.getMetrics()) {
            if (services.contains(sample.service())) {
                series.computeIfAbsent(sample.service() + "|" + sample.metric(), k -> new ArrayList<>()).add(sample);
            }
        }

        List<Evidence> evidence = new ArrayList<>();
        for (List<MetricSample> samples : series.values()) {
            anomaly(samples, window).ifPresent(evidence::add);
        }
        return evidence;
    }

    private static Optional<Evidence> anomaly(List<MetricSample> samples, AnalysisWindow window) {
        List<MetricSample> ordered = samples.stream().sorted(Comparator.comparing(MetricSample::timestamp)).toList();

        List<Double> baseline = ordered.stream()
                .filter(s -> window.inBaseline(s.timestamp()))
                .map(MetricSample::value)
                .toList();
        List<MetricSample> inWindow = ordered.stream()
                .filter(s -> window.contains(s.timestamp()))
                .toList();

        if (baseline.size() < MIN_BASELINE_SAMPLES || inWindow.isEmpty()) {
            return Optional.empty();
        }

        double mean = Numbers.mean(baseline);
        double threshold = Math.max(3 * Numbers.standardDeviation(baseline), 0.5 * Math.abs(mean));

        int onset = -1;
        for (int i = 0; i < inWindow.size(); i++) {
            boolean anomalous = isAnomalous(inWindow.get(i), mean, threshold);
            boolean confirmed = i + 1 == inWindow.size() || isAnomalous(inWindow.get(i + 1), mean, threshold);
            if (anomalous && confirmed) {
                onset = i;
                break;
            }
        }
        if (onset < 0) {
            return Optional.empty();
        }

        List<MetricSample> anomalous = inWindow.subList(onset, inWindow.size()).stream()
                .filter(s -> isAnomalous(s, mean, threshold))
                .toList();
        MetricSample peak = anomalous.stream()
                .max(Comparator.comparingDouble(s -> Math.abs(s.value() - mean)))
                .orElseThrow();
        MetricSample first = anomalous.get(0);

        String change;
        double strength;
        if (mean == 0) {
            change = "from 0";
            strength = 1.0;
        } else {
            double relative = (peak.value() - mean) / Math.abs(mean);
            change = String.format("%+,.0f%%", relative * 100);
            strength = 0.5 + 0.5 * Numbers.clamp01(Math.abs(relative) / 2);
        }

        String summary = first.metric() + " baseline " + Numbers.format(mean)
                + ", peak " + Numbers.format(peak.value()) + " (" + change + ")";

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("metric", first.metric());
        attributes.put("baseline", Numbers.format(mean));
        attributes.put("peak", Numbers.format(peak.value()));
        attributes.put("direction", peak.value() > mean ? "increase" : "decrease");

        return Optional.of(new Evidence(EvidenceKind.METRIC_ANOMALY, first.service(), null,
                first.timestamp(), anomalous.get(anomalous.size() - 1).timestamp(), anomalous.size(),
                strength, summary, attributes, List.of(first.source(), peak.source()).stream().distinct().toList()));
    }

    private static boolean isAnomalous(MetricSample sample, double mean, double threshold) {
        return Math.abs(sample.value() - mean) > threshold;
    }
}
