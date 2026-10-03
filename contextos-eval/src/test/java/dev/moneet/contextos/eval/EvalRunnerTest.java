package dev.moneet.contextos.eval;

import dev.moneet.contextos.Workspace;
import dev.moneet.contextos.core.context.TokenEstimator;
import dev.moneet.contextos.eval.ChatModel.Message;
import dev.moneet.contextos.incident.domain.Incident;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** The harness end to end, with fake models in place of an API. */
class EvalRunnerTest {

    private static final Path EXAMPLES = Path.of(System.getProperty("contextos.examples"));

    private static ContextBuilder contexts;
    private static List<EvalCase> cases;

    @BeforeAll
    static void load() {
        contexts = new ContextBuilder(Workspace.load(EXAMPLES));
        cases = EvalCase.load(EXAMPLES.resolve("eval/cases.json"));
    }

    @Test
    void shouldLoadRubricsForEveryExampleIncident() {
        assertEquals(List.of("INC-143", "INC-144", "INC-145", "INC-146"),
                cases.stream().map(EvalCase::incident).toList());
        for (EvalCase evalCase : cases) {
            assertNotNull(contexts.incident(evalCase.incident()));
            assertEquals(2, evalCase.rootCause().size());
            assertFalse(evalCase.fix().isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> new EvalCase("X",
                List.of(new EvalCase.Criterion("a", "one")), List.of(new EvalCase.Criterion("a", "two"))));
    }

    @Test
    void shouldGiveEveryConditionTheSameIncidentAndBudget() {
        Incident incident = contexts.incident("INC-145");
        for (Condition condition : Condition.values()) {
            String context = contexts.build(condition, "INC-145", 3000);
            assertTrue(contexts.tokens(context) <= 3000, condition + " exceeds the budget");

            String question = Prompts.answer(incident, condition, context).get(1).content();
            assertTrue(question.startsWith("Incident INC-145 [SEV2]: payment-service latency and 5xx\n"));
            assertTrue(question.endsWith("What is the root cause, and what should we do to fix it?"));
        }
    }

    @Test
    void shouldScoreTrialsWithTheJudge() {
        // The subject answers well only when given cross-domain context
        ChatModel subject = fake(messages -> messages.get(1).content().contains("(cross-domain context)")
                ? "```json\n{\"root_cause\": \"good\", \"fix\": \"good\"}\n```"
                : "{\"root_cause\": \"no idea\", \"fix\": \"restart it\"}");
        // The judge passes every criterion for "good" answers
        ChatModel judge = fake(messages -> {
            String user = messages.get(1).content();
            boolean good = user.contains("Root cause: good");
            StringBuilder criteria = new StringBuilder();
            for (String line : user.split("\n")) {
                if (line.startsWith("- ")) {
                    String id = line.substring(2, line.indexOf(':'));
                    criteria.append(criteria.length() == 0 ? "" : ",")
                            .append("{\"id\": \"").append(id).append("\", \"met\": ").append(good)
                            .append(", \"reason\": \"r\"}");
                }
            }
            return "{\"criteria\": [" + criteria + "]}";
        });

        List<Trial> trials = runner(subject, judge).run(cases.subList(0, 2),
                new EvalRunner.Settings(2000, 2, List.of(Condition.values())));

        assertEquals(2 * 3 * 2, trials.size());
        assertTrue(trials.stream().filter(t -> t.condition() == Condition.CROSS_DOMAIN).allMatch(Trial::solved));
        assertTrue(trials.stream().filter(t -> t.condition() != Condition.CROSS_DOMAIN).noneMatch(Trial::solved));
        assertTrue(trials.stream().allMatch(t -> t.answer().parsed()));

        String report = Report.markdown(new Report.Setup("fake", "m", "j", 2000, 2, "now"), trials);
        assertTrue(report.contains("| cross-domain context | 4/4 (100%) | 4/4 (100%) | 4/4 (100%) |"), report);
        assertTrue(report.contains("| raw telemetry | 0/4 (0%) | 0/4 (0%) | 0/4 (0%) |"), report);
        assertTrue(report.contains("| INC-143 | slow-query | 0/2 | 0/2 | 2/2 |"), report);
    }

    @Test
    void shouldRecordFailedCallsAndContinue() {
        int[] calls = {0};
        ChatModel flaky = fake(messages -> {
            if (calls[0]++ == 0) {
                throw new IllegalStateException("network down");
            }
            return "{\"root_cause\": \"x\", \"fix\": \"y\"}";
        });

        List<Trial> trials = runner(flaky, fake(m -> "{\"criteria\": []}")).run(cases.subList(0, 1),
                new EvalRunner.Settings(2000, 2, List.of(Condition.INCIDENT_CONTEXT)));

        assertEquals("IllegalStateException: network down", trials.get(0).error());
        assertFalse(trials.get(0).solved());
        assertNull(trials.get(1).error());
        assertFalse(trials.get(1).solved(), "criteria the judge leaves out count as not met");
    }

    @Test
    void shouldStopOnAuthenticationErrors() {
        ChatModel unauthorized = fake(messages -> {
            throw new OpenAiCompatibleClient.ApiException(401, "invalid key");
        });

        assertThrows(OpenAiCompatibleClient.ApiException.class, () -> runner(unauthorized, unauthorized)
                .run(cases, new EvalRunner.Settings(2000, 1, List.of(Condition.RAW_TELEMETRY))));
    }

    @Test
    void shouldParseAnswersLeniently() {
        Answer fenced = Answer.parse("Here you go:\n```json\n{\"root_cause\": \"a\", \"fix\": \"b\", "
                + "\"evidence\": [\"e1\"]}\n```");
        assertTrue(fenced.parsed());
        assertEquals("a", fenced.rootCause());
        assertEquals(List.of("e1"), fenced.evidence());

        Answer prose = Answer.parse("The database is down.");
        assertFalse(prose.parsed());
        assertEquals("The database is down.", prose.rootCause());
    }

    @Test
    void shouldBuildRawBaselineFromTheIncidentWindowInTimeOrder() {
        String raw = new RawTelemetryContext(EXAMPLES.resolve("runtime"), TokenEstimator.defaultEstimator())
                .build(contexts.incident("INC-146"), 1500);

        assertTrue(TokenEstimator.defaultEstimator().estimate(raw) <= 1500, "within budget");
        assertTrue(raw.contains("2026-10-21T08:50:00Z"), "window starts 15 minutes before the incident");
        assertFalse(raw.contains("2026-09-30T"), "other incidents' records are excluded");
        assertTrue(raw.contains("[metrics]"), "metrics: " + raw);

        // Changes reach back 24 hours, so for INC-143 the previous day's deploy is the oldest record
        String inc143 = new RawTelemetryContext(EXAMPLES.resolve("runtime"), TokenEstimator.defaultEstimator())
                .build(contexts.incident("INC-143"), 1500);
        assertTrue(inc143.split("\n")[3].startsWith("[changes] {\"timestamp\": \"2026-09-29T16:20:00.000Z\""),
                inc143);
        assertTrue(raw.matches("(?s).*\\(\\d+ of \\d+ records shown; the rest did not fit the budget\\)\\n"),
                "count line");

        List<String> times = new ArrayList<>();
        for (String line : raw.split("\n")) {
            if (line.startsWith("[metrics] ")) {
                times.add(line.substring(10, line.indexOf(',')));
            }
        }
        assertEquals(times.stream().sorted().toList(), times, "records are in time order");
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
