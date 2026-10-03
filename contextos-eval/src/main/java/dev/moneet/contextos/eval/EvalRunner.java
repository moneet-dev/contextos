package dev.moneet.contextos.eval;

import dev.moneet.contextos.incident.domain.Incident;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Asks the model about every case under every condition, {@code runs} times,
 * and grades each answer. Calls are sequential, which keeps free-tier rate
 * limits manageable. A failed call is recorded on its trial; configuration
 * errors (bad key, unknown model) stop the run.
 */
public final class EvalRunner {

    public record Settings(int budgetTokens, int runs, List<Condition> conditions) {

        public Settings {
            conditions = List.copyOf(conditions);
            if (budgetTokens < 100 || runs < 1 || conditions.isEmpty()) {
                throw new IllegalArgumentException("budget >= 100, runs >= 1 and at least one condition required");
            }
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
        List<Trial> trials = new ArrayList<>();
        int total = cases.size() * settings.conditions().size() * settings.runs();

        for (EvalCase evalCase : cases) {
            Incident incident = contexts.incident(evalCase.incident());
            for (Condition condition : settings.conditions()) {
                String context = contexts.build(condition, incident.id(), settings.budgetTokens());
                int contextTokens = contexts.tokens(context);

                for (int run = 1; run <= settings.runs(); run++) {
                    Trial trial = trial(evalCase, incident, condition, run, context, contextTokens);
                    trials.add(trial);
                    progress.printf("[%d/%d] %s %-20s run %d: %s (%.1fs)%n", trials.size(), total, incident.id(),
                            condition.label(), run, outcome(trial), trial.elapsedMs() / 1000.0);
                }
            }
        }
        return trials;
    }

    private Trial trial(EvalCase evalCase, Incident incident, Condition condition, int run,
                        String context, int contextTokens) {
        long started = System.nanoTime();
        try {
            Answer answer = Answer.parse(subject.complete(Prompts.answer(incident, condition, context)));
            List<Judge.Grade> grades = judge.grade(evalCase, answer);
            return new Trial(incident.id(), condition, run, contextTokens, answer, grades, null, elapsed(started));
        } catch (OpenAiCompatibleClient.ApiException e) {
            if (e.status() == 401 || e.status() == 403 || e.status() == 404) {
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
