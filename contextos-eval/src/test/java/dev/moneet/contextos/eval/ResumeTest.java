package dev.moneet.contextos.eval;

import dev.moneet.contextos.Workspace;
import dev.moneet.contextos.eval.ChatModel.Message;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Saving after every trial, stopping on exhausted quotas, and continuing with --resume. */
class ResumeTest {

    private static final Path EXAMPLES = Path.of(System.getProperty("contextos.examples"));
    private static final EvalRunner.Settings SETTINGS =
            new EvalRunner.Settings(2000, 2, List.of(Condition.INCIDENT_CONTEXT, Condition.CROSS_DOMAIN));

    private static ContextBuilder contexts;
    private static List<EvalCase> cases;

    @BeforeAll
    static void load() {
        contexts = new ContextBuilder(Workspace.load(EXAMPLES));
        cases = EvalCase.load(EXAMPLES.resolve("eval/cases.json")).subList(0, 2);
    }

    @Test
    void shouldCheckpointAfterEveryTrial() {
        List<Integer> sizes = new ArrayList<>();

        List<Trial> trials = runner(answering(), judging()).run(cases, SETTINGS, List.of(),
                checkpoint -> sizes.add(checkpoint.size()));

        assertEquals(8, trials.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), sizes);
    }

    @Test
    void shouldKeepCompletedTrialsWhenAQuotaRunsOut() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel quotaAfterThree = fake(messages -> {
            if (calls.incrementAndGet() > 3) {
                throw new OpenAiCompatibleClient.ApiException(429, "daily quota", true);
            }
            return "{\"root_cause\": \"good\", \"fix\": \"good\"}";
        });
        List<List<Trial>> checkpoints = new ArrayList<>();

        assertThrows(OpenAiCompatibleClient.ApiException.class, () -> runner(quotaAfterThree, judging())
                .run(cases, SETTINGS, List.of(), checkpoints::add));

        List<Trial> saved = checkpoints.get(checkpoints.size() - 1);
        assertEquals(3, saved.size(), "the three answered trials are saved before stopping");
        assertTrue(saved.stream().allMatch(t -> t.error() == null));
    }

    @Test
    void shouldResumeOnlyMissingOrFailedTrials() {
        List<Trial> first = runner(answering(), judging()).run(cases, SETTINGS);
        // Pretend two trials failed last time and one was never run
        List<Trial> previous = new ArrayList<>(first.subList(0, 7));
        previous.set(1, failed(previous.get(1)));
        previous.set(4, failed(previous.get(4)));

        AtomicInteger calls = new AtomicInteger();
        ChatModel counting = fake(messages -> {
            calls.incrementAndGet();
            return "{\"root_cause\": \"good\", \"fix\": \"good\"}";
        });
        List<Trial> resumed = runner(counting, judging()).run(cases, SETTINGS, previous, trials -> { });

        assertEquals(3, calls.get(), "two failed trials and one missing trial are run");
        assertEquals(8, resumed.size());
        assertTrue(resumed.stream().allMatch(t -> t.error() == null));
        assertEquals(first.get(0), resumed.get(0), "completed trials are kept as they were");
        assertEquals(List.of(1, 2, 1, 2), resumed.subList(0, 4).stream().map(Trial::run).toList(),
                "trials stay in case, condition, run order");
    }

    @Test
    void shouldCoverEveryCaseOnceBeforeRepeatingAndStopAtTheLimit() {
        EvalRunner.Settings capped = new EvalRunner.Settings(2000, 2,
                List.of(Condition.INCIDENT_CONTEXT, Condition.CROSS_DOMAIN), 5);
        List<String> saved = new ArrayList<>();

        List<Trial> first = runner(answering(), judging()).run(cases, capped, List.of(),
                trials -> saved.add(trials.size() + ""));

        assertEquals(5, first.size(), "stops at the limit");
        assertEquals(4, first.stream().filter(t -> t.run() == 1).count(), "every case and condition ran once");
        assertEquals(List.of("1", "2", "3", "4", "5"), saved, "saved after every trial");

        AtomicInteger calls = new AtomicInteger();
        ChatModel counting = fake(messages -> {
            calls.incrementAndGet();
            return "{\"root_cause\": \"good\", \"fix\": \"good\"}";
        });
        List<Trial> rest = runner(counting, judging()).run(cases, SETTINGS, first, trials -> { });

        assertEquals(3, calls.get(), "a later run picks up the remaining trials");
        assertEquals(8, rest.size());
    }

    @Test
    void shouldReadBackSavedResults(@TempDir Path dir) {
        List<Trial> trials = runner(answering(), judging()).run(cases, SETTINGS);
        Report.Setup setup = new Report.Setup("gemini", "m", "j", 2000, 2, "f", "20261003-120000");

        Report.write(dir, setup, trials);
        Report.Saved saved = Report.read(dir);

        assertEquals(setup, saved.setup());
        assertEquals(trials, saved.trials());
    }

    @Test
    void shouldRefuseToOverwriteOrMixRuns(@TempDir Path dir) {
        assertNull(EvalMain.previous(dir, false, "gemini", "m", "j", 2000, "f"), "a new run in an empty directory");
        assertThrows(IllegalArgumentException.class, () -> EvalMain.previous(dir, true, "gemini", "m", "j", 2000, "f"),
                "nothing to resume");

        Report.write(dir, new Report.Setup("gemini", "m", "j", 2000, 2, "f", "t"), List.of());

        IllegalArgumentException overwrite = assertThrows(IllegalArgumentException.class,
                () -> EvalMain.previous(dir, false, "gemini", "m", "j", 2000, "f"));
        assertTrue(overwrite.getMessage().contains("pass --resume"));

        IllegalArgumentException mixed = assertThrows(IllegalArgumentException.class,
                () -> EvalMain.previous(dir, true, "gemini", "other-model", "j", 2000, "f"));
        assertTrue(mixed.getMessage().contains("--model m"), mixed.getMessage());

        assertNotNull(EvalMain.previous(dir, true, "gemini", "m", "j", 2000, "f"));

        IllegalArgumentException changed = assertThrows(IllegalArgumentException.class,
                () -> EvalMain.previous(dir, true, "gemini", "m", "j", 2000, "other-fixtures"));
        assertTrue(changed.getMessage().contains("rubric or fixtures changed"), changed.getMessage());
    }

    private static Trial failed(Trial trial) {
        return new Trial(trial.incident(), trial.condition(), trial.run(), trial.contextTokens(), null, List.of(),
                "ApiException: HTTP 429", 0);
    }

    private static ChatModel answering() {
        return fake(messages -> "{\"root_cause\": \"good\", \"fix\": \"good\"}");
    }

    /** Marks every rubric criterion met. */
    private static ChatModel judging() {
        return fake(messages -> {
            StringBuilder criteria = new StringBuilder();
            for (String line : messages.get(1).content().split("\n")) {
                if (line.startsWith("- ")) {
                    criteria.append(criteria.length() == 0 ? "" : ",").append("{\"id\": \"")
                            .append(line, 2, line.indexOf(':')).append("\", \"met\": true, \"reason\": \"r\"}");
                }
            }
            return "{\"criteria\": [" + criteria + "]}";
        });
    }

    private static EvalRunner runner(ChatModel subject, ChatModel judge) {
        return new EvalRunner(contexts, subject, new Judge(judge),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
    }

    private static ChatModel fake(Function<List<Message>, String> reply) {
        return new ChatModel() {
            @Override
            public String complete(List<Message> messages) {
                return reply.apply(messages);
            }

            @Override
            public String name() {
                return "fake";
            }
        };
    }
}
