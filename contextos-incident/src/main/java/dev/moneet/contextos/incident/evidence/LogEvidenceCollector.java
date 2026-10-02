package dev.moneet.contextos.incident.evidence;

import dev.moneet.contextos.incident.domain.Evidence;
import dev.moneet.contextos.incident.domain.EvidenceKind;
import dev.moneet.contextos.incident.domain.LogEntry;
import dev.moneet.contextos.incident.domain.LogLevel;
import dev.moneet.contextos.incident.domain.SourceRef;
import dev.moneet.contextos.incident.domain.Telemetry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups WARN and ERROR log lines that differ only in numbers or ids, e.g.
 * "Charge failed for customer 4021" and "... customer 7713", into one finding.
 * Strength grows with volume: 1 line = 0.5, 10 = 0.75, 100+ = 1.0.
 */
public final class LogEvidenceCollector implements EvidenceCollector {

    private static final Pattern UUID =
            Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern HEX = Pattern.compile("\\b0x[0-9a-fA-F]+\\b");
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern EXCEPTION_CLASS = Pattern.compile("^([\\w$]+\\.)+([\\w$]+)(.*)$", Pattern.DOTALL);
    private static final String NESTED = "nested exception is ";

    @Override
    public List<Evidence> collect(Telemetry telemetry, AnalysisWindow window, Set<String> services) {
        Map<String, List<LogEntry>> groups = new LinkedHashMap<>();

        for (LogEntry log : telemetry.getLogs()) {
            if (log.level().compareTo(LogLevel.WARN) < 0
                    || !services.contains(log.service())
                    || !window.contains(log.timestamp())) {
                continue;
            }
            String key = String.join("|", log.service(), log.level().name(), String.valueOf(log.logger()),
                    template(log.message()), template(log.exception()));
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(log);
        }

        List<Evidence> evidence = new ArrayList<>();
        for (List<LogEntry> group : groups.values()) {
            evidence.add(toEvidence(group));
        }
        return evidence;
    }

    private static Evidence toEvidence(List<LogEntry> group) {
        List<LogEntry> ordered = group.stream().sorted(Comparator.comparing(LogEntry::timestamp)).toList();
        LogEntry first = ordered.get(0);
        Instant last = ordered.get(ordered.size() - 1).timestamp();
        int count = ordered.size();

        StringBuilder summary = new StringBuilder()
                .append(count).append("x ").append(first.level())
                .append(first.logger() == null ? "" : " [" + first.logger() + "]")
                .append(" ").append(first.message());
        if (first.exception() != null) {
            summary.append(" -- ").append(rootCause(first.exception()));
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("level", first.level().name());
        attributes.put("message", template(first.message()));
        if (first.logger() != null) {
            attributes.put("logger", first.logger());
        }
        if (first.exception() != null) {
            attributes.put("exception", first.exception());
        }

        double strength = 0.5 + 0.5 * Numbers.clamp01(Math.log10(count) / 2);

        return new Evidence(
                first.level() == LogLevel.ERROR ? EvidenceKind.ERROR_LOGS : EvidenceKind.WARNING_LOGS,
                first.service(), null, first.timestamp(), last, count, strength, summary.toString(),
                attributes, sources(ordered));
    }

    /** Text with ids and numbers replaced, so similar lines group together. */
    static String template(String text) {
        if (text == null) {
            return "";
        }
        String result = UUID.matcher(text).replaceAll("<id>");
        result = HEX.matcher(result).replaceAll("<hex>");
        return NUMBER.matcher(result).replaceAll("<n>");
    }

    /** Innermost cause of a chained exception message, without the package. */
    static String rootCause(String exception) {
        int nested = exception.lastIndexOf(NESTED);
        String cause = nested >= 0 ? exception.substring(nested + NESTED.length()) : exception;
        Matcher matcher = EXCEPTION_CLASS.matcher(cause);
        return matcher.matches() ? matcher.group(2) + matcher.group(3) : cause;
    }

    private static List<SourceRef> sources(List<LogEntry> entries) {
        return entries.stream().limit(3).map(LogEntry::source).toList();
    }
}
