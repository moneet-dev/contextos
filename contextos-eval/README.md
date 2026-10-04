# ContextOS — evaluation

Measures whether ContextOS context helps a model find the root cause of an
incident. The model is asked the same question about each incident with three
different contexts, all under the same token budget:

| Condition | Context given to the model |
|---|---|
| `raw_telemetry` | The incident window's raw log, metric, trace and change records, oldest first, cut off at the budget: what you'd see scrolling the files |
| `incident_context` | ContextOS incident context: ranked evidence with reasons and provenance |
| `cross_domain` | ContextOS cross-domain context: the evidence plus the code and database tables it links to |

A judge model grades each answer against the rubric in
[`examples/eval/cases.json`](../examples/eval/cases.json). An answer **solves**
an incident when every root-cause criterion is met and an acceptable fix is
proposed.

## Incidents

| Incident | Root cause | What makes it hard |
|---|---|---|
| INC-143 | Refund batch + slow `payment_transactions` lookup (no index on `payment_id`) exhausts the connection pool | The cause is in the database schema, three steps from the symptom |
| INC-144 | Deploy v2.16.0 introduced a `NullPointerException` in `PaymentService.charge` | An unrelated config change and fraud-api warnings are also in the window |
| INC-145 | Third-party fraud-api slows to ~9 s; `HttpFraudCheckClient` calls time out | An unrelated checkout-service deploy is also in the window |
| INC-146 | Config change cut the connection pool from 50 to 5 | Same pool-exhaustion errors as INC-143, but the database is healthy (shown by ContextOS's healthy signals) |

The fixtures are generated deterministically by
[`examples/runtime/generate_fixtures.py`](../examples/runtime/generate_fixtures.py).

## Run

Works with any OpenAI-compatible chat completions API. Set the provider's key
in your environment, then run with a model name as that provider spells it:

```bash
export GROQ_API_KEY=...        # never passed on the command line
./gradlew :contextos-eval:eval --args="--provider groq --model <model>"
```

| `--provider` | Endpoint | Key variable |
|---|---|---|
| `groq` | `https://api.groq.com/openai/v1` | `GROQ_API_KEY` |
| `openrouter` | `https://openrouter.ai/api/v1` | `OPENROUTER_API_KEY` |
| `gemini` | `https://generativelanguage.googleapis.com/v1beta/openai` | `GEMINI_API_KEY` |
| `mistral` | `https://api.mistral.ai/v1` | `MISTRAL_API_KEY` |
| `github` | `https://models.github.ai/inference` | `GITHUB_TOKEN` |
| `cerebras` | `https://api.cerebras.ai/v1` | `CEREBRAS_API_KEY` |
| `ollama` | `http://localhost:11434/v1` | none |
| `custom` | `--base-url` | `--api-key-env` |

Options:

| Option | Default | Meaning |
|---|---|---|
| `--judge-model` | same as `--model` | Model that grades answers |
| `--runs` | 3 | Repetitions per incident and condition |
| `--budget` | 4000 | Estimated context tokens per condition |
| `--incidents` | all | e.g. `INC-145,INC-146`, run in that order |
| `--max-trials` | no limit | Stop after this many trials, e.g. to fit a daily quota; continue with `--resume` |
| `--conditions` | all | e.g. `raw_telemetry,cross_domain` |
| `--temperature` | 0.2 | For answers; the judge always uses 0 |
| `--max-tokens` | 4096 | Output limit per call. Reasoning models spend part of it thinking; a cut-off answer is recorded as an error, not graded |
| `--out` | `build/eval/<timestamp>` | Where `report.md` and `results.json` go |
| `--dry-run` | | Write every prompt to `--out` without calling any API |
| `--resume` | | Continue the run saved in `--out`: completed trials are kept, failed ones retried |

**Try `--dry-run` first.** It costs nothing and shows exactly what each
condition sends.

A full run makes 4 incidents × 3 conditions × `--runs` answer calls, plus one
judge call each. With the defaults that's 36 + 36 calls of about 4–5k tokens.
Rate limits (HTTP 429) are retried, waiting as long as the provider asks. A bad
key, an unknown model or a quota of zero stops the run immediately.

### Free tiers and daily quotas

Free tiers often cap requests per model per day. Gemini's free tier, for
example, allowed 20 a day per model, while a full run needs 36 answer calls and
36 judge calls. So the harness:
- saves `results.json` and `report.md` after every trial, so the report can be read
  while the run is still going
- runs one round at a time (run 1 of every incident and condition, then run 2,
  ...), so a stopped or capped run has covered every incident before repeating any
- stops the run when a rate limit outlasts every retry (likely a daily quota),
  instead of failing trial after trial
- stops at once on a model whose quota is 0
- continues where it left off once the quota resets:

```bash
./gradlew :contextos-eval:eval --args="--provider gemini --model <m> --judge-model <j> --resume --out build/eval/<run>"
```

A resumed run must use the same provider, models and budget, so every trial in
the report is comparable. A new run refuses to overwrite an existing
`results.json`.

Reasoning models also spend output tokens thinking. An answer cut off at
`--max-tokens` is recorded as an error rather than graded.

## Reading the results

`report.md` has:
- solve rate, root-cause rate and fix rate per condition
- solves by incident
- how often each rubric criterion was met

`results.json` has every answer and the judge's reason for each criterion.

Keep in mind:
- **Judge bias.** By default the model grades its own answers. A stronger
  `--judge-model`, ideally from a different model family, gives more reliable
  grades.
- **Small sample.** Four incidents and three runs show a direction, not a
  precise rate.
- **Equal budgets, unequal sizes.** The incident-only context is often much
  smaller than the budget (1–2k tokens), because that is all the ranked evidence
  there is. The other two fill the budget. The report shows average context
  tokens per condition.
- **Where the raw baseline ends.** It is cut off by time, so with a 4,000-token
  budget it covers roughly the 15 minutes before the incident. That includes
  most causes but few symptoms.
