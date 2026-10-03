package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Grades an answer against a case's rubric with a model. A criterion the judge
 * leaves out, or a response that isn't JSON, counts as not met; the judge is
 * asked once more before giving up.
 */
public final class Judge {

    public record Grade(String criterion, boolean rootCause, boolean met, String reason) {
    }

    private final ChatModel model;

    public Judge(ChatModel model) {
        this.model = model;
    }

    public String modelName() {
        return model.name();
    }

    public List<Grade> grade(EvalCase evalCase, Answer answer) {
        Map<String, JsonNode> verdicts = Map.of();
        for (int attempt = 0; attempt < 2 && verdicts.isEmpty(); attempt++) {
            verdicts = verdicts(model.complete(Prompts.judge(evalCase, answer)));
        }

        List<Grade> grades = new ArrayList<>();
        for (EvalCase.Criterion criterion : evalCase.criteria()) {
            boolean rootCause = evalCase.rootCause().contains(criterion);
            JsonNode verdict = verdicts.get(criterion.id());
            if (verdict == null) {
                grades.add(new Grade(criterion.id(), rootCause, false, "not graded by the judge"));
            } else {
                grades.add(new Grade(criterion.id(), rootCause, verdict.path("met").asBoolean(false),
                        verdict.path("reason").asText("")));
            }
        }
        return grades;
    }

    private static Map<String, JsonNode> verdicts(String response) {
        Optional<JsonNode> json = Answer.jsonObject(response);
        Map<String, JsonNode> verdicts = new HashMap<>();
        json.ifPresent(node -> node.path("criteria").forEach(c -> verdicts.put(c.path("id").asText(), c)));
        return verdicts;
    }
}
