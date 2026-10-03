package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.moneet.contextos.core.context.TokenEstimator;
import dev.moneet.contextos.incident.domain.Incident;
import dev.moneet.contextos.incident.evidence.AnalysisWindow;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * The baseline condition: the telemetry an engineer would grep, with no ranking,
 * grouping or linking. Records from the same window ContextOS analyses (changes
 * from the preceding 24 hours) are listed in time order, as written in the files,
 * until the budget is used up.
 */
public final class RawTelemetryContext {

    private record Record(Instant time, String text) {
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path runtimeDir;
    private final TokenEstimator estimator;

    public RawTelemetryContext(Path runtimeDir, TokenEstimator estimator) {
        this.runtimeDir = runtimeDir;
        this.estimator = estimator;
    }

    public String build(Incident incident, int budgetTokens) {
        AnalysisWindow window = AnalysisWindow.around(incident);
        List<Record> records = new ArrayList<>();
        records.addAll(jsonLines("changes.jsonl", "timestamp", window::inChangeRange));
        records.addAll(jsonLines("logs.jsonl", "timestamp", window::contains));
        records.addAll(jsonLines("traces.jsonl", "start", window::contains));
        records.addAll(metrics(window::contains));
        records.sort(Comparator.comparing(Record::time));

        String header = "Raw telemetry records between " + window.start() + " and " + window.end()
                + " (changes from " + window.changesFrom() + "), oldest first.\n"
                + "Prefix [file] names the source: changes, logs, traces or metrics "
                + "(metrics.csv columns: timestamp,service,metric,value).\n\n";

        // Room for the closing count line, sized for the largest count it can show
        int reserved = estimator.estimate(trailer(records.size(), records.size()));

        StringBuilder sb = new StringBuilder(header);
        int shown = 0;
        for (Record record : records) {
            String line = record.text() + "\n";
            if (estimator.estimate(sb + line) + reserved > budgetTokens) {
                break;
            }
            sb.append(line);
            shown++;
        }
        return sb.append(trailer(shown, records.size())).toString();
    }

    private static String trailer(int shown, int total) {
        return "\n(" + shown + " of " + total + " records shown; the rest did not fit the budget)\n";
    }

    private List<Record> jsonLines(String file, String timeField, Predicate<Instant> include) {
        List<Record> records = new ArrayList<>();
        String tag = "[" + file.substring(0, file.indexOf('.')) + "] ";
        for (String line : lines(file)) {
            try {
                Instant time = Instant.parse(JSON.readTree(line).path(timeField).asText());
                if (include.test(time)) {
                    records.add(new Record(time, tag + line));
                }
            } catch (IOException | DateTimeParseException e) {
                // not a record; skip
            }
        }
        return records;
    }

    private List<Record> metrics(Predicate<Instant> include) {
        List<Record> records = new ArrayList<>();
        for (String line : lines("metrics.csv")) {
            try {
                Instant time = Instant.parse(line.substring(0, line.indexOf(',')));
                if (include.test(time)) {
                    records.add(new Record(time, "[metrics] " + line));
                }
            } catch (IndexOutOfBoundsException | DateTimeParseException e) {
                // header or malformed line
            }
        }
        return records;
    }

    private List<String> lines(String file) {
        Path path = runtimeDir.resolve("telemetry").resolve(file);
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }
}
