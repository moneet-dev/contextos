package dev.moneet.contextos.eval;

import dev.moneet.contextos.eval.ChatModel.Message;
import dev.moneet.contextos.incident.domain.Incident;

import java.util.List;

/**
 * The question asked about each incident, and the rubric-grading prompt.
 * The incident summary is the same in every condition; only the context differs.
 */
final class Prompts {

    static final String ANSWER_SYSTEM = """
            You are the on-call engineer investigating a production incident.
            Use only the context provided. Identify the root cause (what actually broke and why) \
            and the most appropriate fix.
            Respond with a single JSON object and nothing else:
            {"root_cause": "...", "fix": "...", "evidence": ["the facts from the context you relied on"]}""";

    static final String JUDGE_SYSTEM = """
            You grade incident analyses against a rubric.
            For each criterion, decide whether the analysis clearly states it. Equivalent wording counts. \
            Vague, hedged or merely possible mentions do not count, and an analysis that contradicts \
            a criterion fails it.
            Respond with a single JSON object and nothing else:
            {"criteria": [{"id": "<criterion id>", "met": true or false, "reason": "<one sentence>"}]}""";

    private Prompts() {
    }

    static List<Message> answer(Incident incident, Condition condition, String context) {
        String user = "Incident " + incident.id() + " [" + incident.severity() + "]: " + incident.title() + "\n"
                + "Started: " + incident.startedAt() + "\n"
                + "Affected: " + String.join(", ", incident.affectedServices()) + "\n"
                + "Symptoms: " + String.join("; ", incident.symptoms()) + "\n\n"
                + "Context (" + condition.label() + "):\n"
                + "<<<\n" + context.strip() + "\n>>>\n\n"
                + "What is the root cause, and what should we do to fix it?";
        return List.of(Message.system(ANSWER_SYSTEM), Message.user(user));
    }

    static List<Message> judge(EvalCase evalCase, Answer answer) {
        StringBuilder rubric = new StringBuilder();
        for (EvalCase.Criterion criterion : evalCase.criteria()) {
            rubric.append("- ").append(criterion.id()).append(": ").append(criterion.description()).append("\n");
        }
        String user = "Rubric for " + evalCase.incident() + ":\n" + rubric + "\n"
                + "Analysis to grade:\n"
                + "Root cause: " + answer.rootCause() + "\n"
                + "Fix: " + answer.fix() + "\n";
        return List.of(Message.system(JUDGE_SYSTEM), Message.user(user));
    }
}
