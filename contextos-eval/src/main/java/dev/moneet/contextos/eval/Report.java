package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Writes {@code results.json} (every trial) and {@code report.md} (summary tables). */
public final class Report {

    /** How the run was configured, recorded with the results. */
    public record Setup(String provider, String model, String judgeModel, int budgetTokens, int runs,
                        String inputs,
                        String startedAt) {
    }

    /** The contents of a {@code results.json}. */
    public record Saved(Setup setup, List<Trial> trials) {
    }

    private Report() {
    }

    /** Reads {@code results.json} from {@code dir}, as written by {@link #write}. */
    public static Saved read(Path dir) {
        try {
            return new ObjectMapper().readValue(dir.resolve("results.json").toFile(), Saved.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + dir.resolve("results.json"), e);
        }
    }

    public static void write(Path dir, Setup setup, List<Trial> trials) {
        try {
            Files.createDirectories(dir);
            Map<String, Object> results = new LinkedHashMap<>();
            results.put("setup", setup);
            results.put("trials", trials);
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValue(dir.resolve("results.json").toFile(), results);
            Files.writeString(dir.resolve("report.md"), markdown(setup, trials), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report to " + dir, e);
        }
    }

    static String markdown(Setup setup, List<Trial> trials) {
        Set<Condition> conditions = new LinkedHashSet<>();
        Set<String> incidents = new LinkedHashSet<>();
        trials.forEach(t -> {
            conditions.add(t.condition());
            incidents.add(t.incident());
        });

        StringBuilder sb = new StringBuilder("# ContextOS evaluation\n\n");
        sb.append("- Model: `").append(setup.model()).append("` via ").append(setup.provider()).append("\n");
        sb.append("- Judge: `").append(setup.judgeModel()).append("`\n");
        sb.append("- Budget: ").append(setup.budgetTokens()).append(" estimated tokens of context in every condition\n");
        sb.append("- Runs: ").append(setup.runs()).append(" per incident and condition\n");
        sb.append("- Inputs: `").append(setup.inputs()).append("` (fingerprint of the rubric and fixtures)\n");
        sb.append("- Started: ").append(setup.startedAt()).append("\n\n");

        sb.append("## Results\n\n");
        sb.append("| Condition | Solved | Root cause | Fix | Context tokens (avg) | Errors |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (Condition condition : conditions) {
            List<Trial> rows = trials.stream().filter(t -> t.condition() == condition).toList();
            sb.append("| ").append(condition.label())
                    .append(" | ").append(rate(rows, Trial::solved))
                    .append(" | ").append(rate(rows, Trial::rootCauseCorrect))
                    .append(" | ").append(rate(rows, Trial::fixCorrect))
                    .append(" | ").append(Math.round(rows.stream().mapToInt(Trial::contextTokens).average().orElse(0)))
                    .append(" | ").append(rows.stream().filter(t -> t.error() != null).count())
                    .append(" |\n");
        }

        sb.append("\n## Solved, by incident\n\n| Incident |");
        conditions.forEach(c -> sb.append(" ").append(c.label()).append(" |"));
        sb.append("\n|---|");
        conditions.forEach(c -> sb.append("---|"));
        sb.append("\n");
        for (String incident : incidents) {
            sb.append("| ").append(incident).append(" |");
            for (Condition condition : conditions) {
                List<Trial> rows = trials.stream()
                        .filter(t -> t.incident().equals(incident) && t.condition() == condition).toList();
                sb.append(" ").append(count(rows, Trial::solved)).append(" |");
            }
            sb.append("\n");
        }

        sb.append("\n## Criteria met, by incident\n\n| Incident | Criterion |");
        conditions.forEach(c -> sb.append(" ").append(c.label()).append(" |"));
        sb.append("\n|---|---|");
        conditions.forEach(c -> sb.append("---|"));
        sb.append("\n");
        for (String incident : incidents) {
            Set<String> criteria = new LinkedHashSet<>();
            trials.stream().filter(t -> t.incident().equals(incident))
                    .forEach(t -> t.grades().forEach(g -> criteria.add((g.rootCause() ? "" : "fix: ") + g.criterion())));
            for (String criterion : criteria) {
                sb.append("| ").append(incident).append(" | ").append(criterion).append(" |");
                for (Condition condition : conditions) {
                    List<Trial> rows = trials.stream()
                            .filter(t -> t.incident().equals(incident) && t.condition() == condition).toList();
                    long met = rows.stream().filter(t -> t.grades().stream()
                            .anyMatch(g -> ((g.rootCause() ? "" : "fix: ") + g.criterion()).equals(criterion)
                                    && g.met())).count();
                    sb.append(" ").append(met).append("/").append(rows.size()).append(" |");
                }
                sb.append("\n");
            }
        }

        List<Trial> errors = trials.stream().filter(t -> t.error() != null).toList();
        if (!errors.isEmpty()) {
            sb.append("\n## Errors\n\n");
            errors.forEach(t -> sb.append("- ").append(t.incident()).append(" ").append(t.condition().label())
                    .append(" run ").append(t.run()).append(": ").append(t.error()).append("\n"));
        }

        sb.append("\n## Method\n\n");
        sb.append("Each incident is described the same way in every condition; only the context differs. ");
        sb.append("Raw telemetry is the incident window's records in time order, cut off at the budget. ");
        sb.append("A trial is solved when the judge finds every root-cause criterion and at least one fix ");
        sb.append("from the rubric in `examples/eval/cases.json`. Full answers and the judge's reasons are in ");
        sb.append("`results.json`.\n");
        return sb.toString();
    }

    private static String rate(List<Trial> trials, Predicate<Trial> test) {
        if (trials.isEmpty()) {
            return "-";
        }
        long n = trials.stream().filter(test).count();
        return n + "/" + trials.size() + " (" + Math.round(100.0 * n / trials.size()) + "%)";
    }

    private static String count(List<Trial> trials, Predicate<Trial> test) {
        return trials.stream().filter(test).count() + "/" + trials.size();
    }
}
