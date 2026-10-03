package dev.moneet.contextos.eval;

import java.util.List;

/**
 * One question asked and graded. {@code error} is set when the model call
 * failed; such a trial counts as unsolved.
 */
public record Trial(String incident,
                    Condition condition,
                    int run,
                    int contextTokens,
                    Answer answer,
                    List<Judge.Grade> grades,
                    String error,
                    long elapsedMs) {

    public Trial {
        grades = List.copyOf(grades);
    }

    public boolean rootCauseCorrect() {
        return error == null && grades.stream().filter(Judge.Grade::rootCause).allMatch(Judge.Grade::met)
                && grades.stream().anyMatch(Judge.Grade::rootCause);
    }

    public boolean fixCorrect() {
        return error == null && grades.stream().filter(g -> !g.rootCause()).anyMatch(Judge.Grade::met);
    }

    public boolean solved() {
        return rootCauseCorrect() && fixCorrect();
    }
}
