package dev.moneet.contextos.incident.evidence;

import java.util.List;

final class Numbers {

    private Numbers() {
    }

    static double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    static double standardDeviation(List<Double> values) {
        double mean = mean(values);
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
        return Math.sqrt(variance);
    }

    static double median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    /** 1 decimal below 100, thousands-grouped integer above. */
    static String format(double value) {
        return Math.abs(value) >= 100 ? String.format("%,.0f", value) : String.format("%.1f", value);
    }

    static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
