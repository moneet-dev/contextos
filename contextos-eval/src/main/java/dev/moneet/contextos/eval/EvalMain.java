package dev.moneet.contextos.eval;

import dev.moneet.contextos.Workspace;
import dev.moneet.contextos.incident.domain.Incident;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line for the evaluation.
 *
 * <pre>
 * --provider     groq | openrouter | gemini | mistral | github | cerebras | ollama | custom
 * --model        model name as the provider spells it (required unless --dry-run)
 * --judge-model  model that grades answers (default: --model)
 * --base-url     override the provider's endpoint (required for custom)
 * --api-key-env  environment variable holding the key (default: the provider's)
 * --runs         repetitions per incident and condition (default 3)
 * --budget       context tokens per condition (default 4000)
 * --incidents    comma-separated ids (default: all cases)
 * --conditions   comma-separated: raw_telemetry, incident_context, cross_domain (default: all)
 * --temperature  sampling temperature for answers (default 0.2; the judge uses 0)
 * --max-tokens   output token limit per call, including any reasoning (default 4096)
 * --examples     workspace directory (default examples)
 * --cases        rubric file (default examples/eval/cases.json)
 * --out          output directory (default build/eval/&lt;timestamp&gt;)
 * --dry-run      write every prompt to --out without calling any API
 * </pre>
 * The API key is read from the environment and never printed or written.
 */
public final class EvalMain {

    private EvalMain() {
    }

    public static void main(String[] args) {
        try {
            run(options(args));
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        } catch (OpenAiCompatibleClient.ApiException e) {
            System.err.println("Stopped: " + e.getMessage());
            System.err.println("Check the API key, the model name and the provider's base URL.");
            System.exit(1);
        }
    }

    static void run(Map<String, String> options) {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        Path examples = Path.of(options.getOrDefault("examples", "examples"));
        Path out = Path.of(options.getOrDefault("out", "build/eval/" + stamp));
        int budget = integer(options, "budget", 4000);
        int runs = integer(options, "runs", 3);
        boolean dryRun = options.containsKey("dry-run");

        List<EvalCase> cases = EvalCase.load(Path.of(options.getOrDefault("cases", "examples/eval/cases.json")));
        if (options.containsKey("incidents")) {
            List<String> wanted = Arrays.stream(options.get("incidents").split(",")).map(String::trim).toList();
            cases = cases.stream().filter(c -> wanted.contains(c.incident())).toList();
            if (cases.isEmpty()) {
                throw new IllegalArgumentException("No cases for incidents " + wanted);
            }
        }
        List<Condition> conditions = options.containsKey("conditions")
                ? Arrays.stream(options.get("conditions").split(",")).map(Condition::parse).toList()
                : List.of(Condition.values());

        ContextBuilder contexts = new ContextBuilder(Workspace.load(examples));

        if (dryRun) {
            dryRun(contexts, cases, conditions, budget, out);
            return;
        }

        Provider provider = Provider.parse(required(options, "provider"));
        String model = required(options, "model");
        String judgeModel = options.getOrDefault("judge-model", model);
        String baseUrl = options.getOrDefault("base-url", provider.baseUrl());
        if (baseUrl == null) {
            throw new IllegalArgumentException("--base-url is required for provider " + provider.name().toLowerCase());
        }
        String keyEnv = options.getOrDefault("api-key-env", provider.apiKeyEnv());
        String apiKey = keyEnv == null ? null : System.getenv(keyEnv);
        if (keyEnv != null && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException("Set the " + keyEnv + " environment variable to your API key "
                    + "(or pass --api-key-env with the variable you use).");
        }
        double temperature = Double.parseDouble(options.getOrDefault("temperature", "0.2"));
        int maxTokens = integer(options, "max-tokens", 4096);

        ChatModel subject = new OpenAiCompatibleClient(baseUrl, model, apiKey, temperature, maxTokens);
        Judge judge = new Judge(new OpenAiCompatibleClient(baseUrl, judgeModel, apiKey, 0.0, maxTokens));

        System.out.printf("Evaluating %s via %s: %d incident(s) x %d condition(s) x %d run(s), budget %d tokens%n",
                model, provider.name().toLowerCase(), cases.size(), conditions.size(), runs, budget);
        List<Trial> trials = new EvalRunner(contexts, subject, judge, System.out)
                .run(cases, new EvalRunner.Settings(budget, runs, conditions));

        Report.write(out, new Report.Setup(provider.name().toLowerCase(), model, judgeModel, budget, runs, stamp),
                trials);
        System.out.println("\nReport: " + out.resolve("report.md").toAbsolutePath());
        System.out.println("Trials: " + out.resolve("results.json").toAbsolutePath());
    }

    /** Writes the exact messages each condition would send, for inspection before spending credits. */
    private static void dryRun(ContextBuilder contexts, List<EvalCase> cases, List<Condition> conditions,
                               int budget, Path out) {
        for (EvalCase evalCase : cases) {
            Incident incident = contexts.incident(evalCase.incident());
            for (Condition condition : conditions) {
                String context = contexts.build(condition, incident.id(), budget);
                StringBuilder sb = new StringBuilder();
                for (ChatModel.Message message : Prompts.answer(incident, condition, context)) {
                    sb.append("## ").append(message.role()).append("\n\n").append(message.content()).append("\n\n");
                }
                Path file = out.resolve(incident.id()).resolve(condition.name().toLowerCase() + ".md");
                write(file, sb.toString());
                System.out.printf("%s %-20s %5d context tokens -> %s%n", incident.id(), condition.label(),
                        contexts.tokens(context), file);
            }
        }
    }

    static Map<String, String> options(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
            String name = args[i].substring(2);
            if (name.equals("dry-run")) {
                options.put(name, "true");
            } else if (i + 1 < args.length) {
                options.put(name, args[++i]);
            } else {
                throw new IllegalArgumentException("Missing value for --" + name);
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " is required (providers: " + Provider.names() + ")");
        }
        return value;
    }

    private static int integer(Map<String, String> options, String name, int defaultValue) {
        try {
            return options.containsKey(name) ? Integer.parseInt(options.get(name)) : defaultValue;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + name + " must be an integer");
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + file, e);
        }
    }
}
