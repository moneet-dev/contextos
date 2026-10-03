package dev.moneet.contextos.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rubric for one incident. A trial solves the case when every root-cause
 * criterion is met and at least one acceptable fix is proposed.
 */
public record EvalCase(String incident, List<Criterion> rootCause, List<Criterion> fix) {

    public record Criterion(String id, String description) {
    }

    public EvalCase {
        rootCause = List.copyOf(rootCause);
        fix = List.copyOf(fix);
        if (rootCause.isEmpty() || fix.isEmpty()) {
            throw new IllegalArgumentException(incident + ": needs at least one root-cause and one fix criterion");
        }
        // Fields are assigned after this compact constructor, so use the parameters
        Set<String> ids = new HashSet<>();
        List<Criterion> all = new ArrayList<>(rootCause);
        all.addAll(fix);
        for (Criterion criterion : all) {
            if (!ids.add(criterion.id())) {
                throw new IllegalArgumentException(incident + ": duplicate criterion id " + criterion.id());
            }
        }
    }

    public List<Criterion> criteria() {
        List<Criterion> all = new ArrayList<>(rootCause);
        all.addAll(fix);
        return all;
    }

    /** Reads {@code {"cases": [{"incident", "rootCause": [{id, description}], "fix": [...]}]}}. */
    public static List<EvalCase> load(Path file) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
        List<EvalCase> cases = new ArrayList<>();
        for (JsonNode node : root.path("cases")) {
            cases.add(new EvalCase(node.path("incident").asText(), criteria(node.path("rootCause")),
                    criteria(node.path("fix"))));
        }
        return cases;
    }

    private static List<Criterion> criteria(JsonNode array) {
        List<Criterion> criteria = new ArrayList<>();
        for (JsonNode node : array) {
            criteria.add(new Criterion(node.path("id").asText(), node.path("description").asText()));
        }
        return criteria;
    }
}
