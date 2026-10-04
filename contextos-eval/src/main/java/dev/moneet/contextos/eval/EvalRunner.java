package dev.moneet.contextos.eval;

import dev.moneet.contextos.incident.domain.Incident;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Asks the model about every case under every condition, {@code runs} times,
 * and grades each answer. Calls are sequential, which keeps free-tier rate
 * limits manageable. A failed call is recorded on its trial; errors no later
 * call can recover from (bad key, unknown model, zero quota) stop the run.
 */
public final class EvalRunner {

    /**
     * @param maxTrials  most trials to run in this call (saved trials don't count);
     *                   the rest can be run later with the same settings and the saved results
     */
    public record Settings(int budgetTokens, int runs, List<Condition> conditions, int maxTrials) {

        public Settings {
            conditions = List.copyOf(conditions);
            if (budgetTokens < 100 || runs < 1 || conditions.isEmpty() || maxTrials < 1) {
                throw new IllegalArgumentException(
                        "budget >= 100, runs >= 1, max trials >= 1 and at least one condition required");
            }
        }

        public Settings(int budgetTokens, int runs, List<Condition> conditions) {
            this(budgetTokens, runs, conditions, Integer.MAX_VALUE);
        }
    }

    private final ContextBuilder contexts;
    private final ChatModel subject;
    private final Judge judge;
    private final PrintStream progress;

    public EvalRunner(ContextBuilder contexts, ChatModel subject, Judge judge, PrintStream progress) {
        this.contexts = contexts;
        this.subject = subject;
        this.judge = judge;
        this.progress = progress;
    }

    public List<Trial> run(List<EvalCase> cases, Settings settings) {
        return run(cases, settings, List.of(), trials -> { });
    }

    /**
     * Runs every trial not already completed in {@code previous}; previous trials that
     * failed are run again. Trials run one round at a time (run 1 of every case and
     * condition, then run 2, ...), so a capped or interrupted run covers every case
     * before repeating any. {@code checkpoint} receives all trials so far, previous ones
     * included, after each trial, so nothing is lost by stopping early. A fatal error
     * stops the run after a final checkpoint.
     */
    public List<Trial> run(List<EvalCase> cases, Settings settings, List<Trial> previous,
                           Consumer<List<Trial>> checkpoint) {
        Map<String, Trial> results = new LinkedHashMap<>();
        previous.forEach(t -> results.put(key(t.incident(), t.condition(), t.run()), t));
        Comparator<Trial> order = order(cases);
        Map<String, String> builtContexts = new HashMap<>();

        int total = cases.size() * settings.conditions().size() * settings.runs();
        int position = 0;
        int ran = 0;

        for (int run = 1; run <= settings.runs(); run++) {
            for (EvalCase evalCase : cases) {
                Incident incident = contexts.incident(evalCase.incident());
                for (Condition condition : settings.conditions()) {
                    position++;
                    String key = key(incident.id(), condition, run);
                    Trial done = results.get(key);
                    if (done != null && done.error() == null) {
                        progress.printf("[%d/%d] %s %-20s run %d: %s (saved)%n", position, total, incident.id(),
                                condition.label(), run, outcome(done));
                        continue;
                    }
                    if (ran == settings.maxTrials()) {
                        progress.printf("Reached the limit of %d trial(s); the rest can be run later.%n", ran);
                        return sorted(results, order);
                    }

                    String context = builtContexts.computeIfAbsent(incident.id() + "|" + condition,
                            k -> contexts.build(condition, incident.id(), settings.budgetTokens()));
                    Trial trial;
                    try {
                        trial = trial(evalCase, incident, condition, run, context, contexts.tokens(context));
                    } catch (OpenAiCompatibleClient.ApiException fatal) {
                        checkpoint.accept(sorted(results, order));
                        throw fatal;
                    }
                    ran++;
                    results.put(key, trial);
                    checkpoint.accept(sorted(results, order));
                    progress.printf("[%d/%d] %s %-20s run %d: %s (%.1fs)%n", position, total, incident.id(),
                            condition.label(), run, outcome(trial), trial.elapsedMs() / 1000.0);
                }
            }
        }
        return sorted(results, order);
    }

    private static String key(String incident, Condition condition, int run) {
        return incident + "|" + condition + "|" + run;
    }

    /** Case order, then condition, then run; trials for cases not in this run go last. */
    private static Comparator<Trial> order(List<EvalCase> cases) {
        List<String> incidents = cases.stream().map(EvalCase::incident).toList();
        return Comparator.<Trial>comparingInt(t -> incidents.contains(t.incident())
                        ? incidents.indexOf(t.incident()) : Integer.MAX_VALUE)
                .thenComparing(Trial::incident)
                .thenComparing(Trial::condition)
                .thenComparingInt(Trial::run);
    }

    private static List<Trial> sorted(Map<String, Trial> results, Comparator<Trial> order) {
        return results.values().stream().sorted(order).toList();
    }

    private Trial trial(EvalCase evalCase, Incident incident, Condition condition, int run,
                        String context, int contextTokens) {
        long started = System.nanoTime();
        try {
            Answer answer = Answer.parse(subject.complete(Prompts.answer(incident, condition, context)));
            List<Judge.Grade> grades = judge.grade(evalCase, answer);
            return new Trial(incident.id(), condition, run, contextTokens, answer, grades, null, elapsed(started));
        } catch (OpenAiCompatibleClient.ApiException e) {
            if (e.fatal()) {
                throw e;
            }
            return failed(incident, condition, run, contextTokens, e, started);
        } catch (RuntimeException e) {
            return failed(incident, condition, run, contextTokens, e, started);
        }
    }

    private static Trial failed(Incident incident, Condition condition, int run, int contextTokens,
                                RuntimeException e, long started) {
        return new Trial(incident.id(), condition, run, contextTokens, null, List.of(),
                e.getClass().getSimpleName() + ": " + e.getMessage(), elapsed(started));
    }

    private static String outcome(Trial trial) {
        if (trial.error() != null) {
            return "ERROR " + trial.error();
        }
        long rootMet = trial.grades().stream().filter(g -> g.rootCause() && g.met()).count();
        long root = trial.grades().stream().filter(Judge.Grade::rootCause).count();
        return (trial.solved() ? "solved" : "not solved")
                + " (root cause " + rootMet + "/" + root + ", fix " + (trial.fixCorrect() ? "yes" : "no") + ")";
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
