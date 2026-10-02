package dev.moneet.contextos.incident.domain;

public enum EvidenceKind {
    /** A deploy, config change, flag flip or job run. */
    CHANGE,
    /** A group of similar ERROR log lines. */
    ERROR_LOGS,
    /** A group of similar WARN log lines. */
    WARNING_LOGS,
    /** A metric that left its baseline range. */
    METRIC_ANOMALY,
    /** Spans of one operation that ended in error. */
    FAILED_SPANS,
    /** Spans of one operation that became much slower than their baseline. */
    SLOW_SPANS
}
