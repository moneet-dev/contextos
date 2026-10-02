package dev.moneet.contextos.incident.evidence;

/** The factors multiplied into an evidence score, each in 0..1. */
public record ScoreBreakdown(double kind, double proximity, double timing, double strength) {

    public double score() {
        return kind * proximity * timing * strength;
    }

    @Override
    public String toString() {
        return String.format("kind %.2f x proximity %.2f x timing %.2f x strength %.2f",
                kind, proximity, timing, strength);
    }
}
