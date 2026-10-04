# ContextOS

**Structured, provenance-aware context for AI agents, built from databases,
source code and production systems.**

An agent asked "why is payment-service returning 5xx?" needs more than log
lines. It needs:
- the evidence, ranked
- the code paths that produced it
- the tables those paths touch

All of it has to fit in a context window, and every piece has to say where it
came from. ContextOS builds that context in Java, with no LLM in the loop:
graphs, explicit ranking and a token budget.

It grew in four layers, each usable on its own:

| Layer | Module | What it understands |
|---|---|---|
| Data | `schema-context` (Schema Context Engine) + `contextos-sql` | Tables, keys, indexes, join paths and join quality |
| Software | `contextos-code` | Symbols, calls, inheritance, Spring endpoints, data access |
| Runtime | `contextos-incident` | Service topology, logs, metrics, traces, changes |
| All three | `contextos` | Links between them, under one budget |

> **Status: early preview.** It works end to end on the example workspace: four
> synthetic incidents, a Spring-style service and its database, available as a
> library or as an MCP server. It hasn't been proven on real systems yet, and
> that's the next step: see the [roadmap](ROADMAP.md). Contributors are
> welcome; start with [CONTRIBUTING](CONTRIBUTING.md) and the
> [good first issues](https://github.com/moneet-dev/contextos/labels/good%20first%20issue).

## Try it with Claude Code

Requires Java 17. Download the MCP server and the example workspace from the
[latest release](https://github.com/moneet-dev/contextos/releases/latest):

```bash
curl -LO https://github.com/moneet-dev/contextos/releases/latest/download/contextos-mcp-all.jar
curl -LO https://github.com/moneet-dev/contextos/releases/latest/download/contextos-examples.zip
unzip contextos-examples.zip
```

Register the server with absolute paths:

```bash
claude mcp add contextos -- java -jar /absolute/path/to/contextos-mcp-all.jar /absolute/path/to/examples
```

Then ask: *"What caused INC-143? Use the contextos tools."* The
[MCP server README](contextos-mcp/README.md) lists the tools, and any MCP
client can use them.

## Example: INC-143

`payment-service` 5xx rose 320%. Given only the incident id and a 4,000-token
budget, ContextOS follows links from the runtime evidence into the code and the
database, and records how it got there:

```
INC-143 payment-service 5xx elevated
incident -> code
  FAILED_SPANS payment-service --endpoint POST /payments--> PaymentController#pay(PaymentRequest)
  ERROR_LOGS payment-service --logger--> PaymentService
  SLOW_SPANS payment-service --endpoint POST /payments/{id}/refund--> PaymentController#refund(long)
  CHANGE payment-service --mentions PaymentTransactionRepository.findByPaymentId--> PaymentTransactionRepository#findByPaymentId(long)
incident -> sql
  SLOW_SPANS payment-service -> payments-db --query on payments-db--> payment_transactions (+1 more)
code -> sql
  PaymentService#refund(long) --data access--> payments (+2 more)
  PaymentTransactionRepository#findByPaymentId(long) --data access--> payment_transactions (+1 more)
```

The packed context then contains:
- the incident evidence: a refund batch at 13:58, slow
  `payment_transactions` queries, an exhausted connection pool, then 5xx
- the code path `PaymentController#refund → PaymentService#refund →
  PaymentTransactionRepository#findByPaymentId` with its native query
- the tables, rendered by the Schema Context Engine:

```
Table payment_transactions:
  id (PK)
  payment_id (FK -> payments.id)
  card_token
  created_at

Table payments:
  id (PK)
  customer_id (FK -> customers.id)
  amount_cents
  status (INDEXED)
  Indexes:
    - idx_payments_status [status]
```

Here `payment_id` carries no `(INDEXED)` marker and `payment_transactions` has
no indexes, so the agent can see why the refund lookup scans the table.

Every item carries its score, the reason it was selected and its source:
`file:line`, the telemetry records, or the foreign keys on its join path with
their join quality.

```bash
./gradlew :contextos:demo                      # INC-143, 4,000 tokens
./gradlew :contextos:demo --args="INC-143 2500"
```

Or give it to an agent: [`contextos-mcp`](contextos-mcp/README.md) serves the
same investigation as MCP tools, so Claude Code can call `investigate_incident`
itself and drill down with `code_context` and `schema_context`.

## Architecture

```
                         contextos            links domains, one budget
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
   contextos-sql       contextos-code      contextos-incident
   (adapter)           JavaParser          file-based telemetry
        │                    │                    │
   schema-context            │                    │
   (SQL engine)              │                    │
        └────────────────────┼────────────────────┘
                             ▼
                      contextos-core         TypedGraph + BFS, ContextItem,
                                             ContextPackage, budget packing
```

| Module | Purpose | Docs |
|---|---|---|
| `schema-context` | Schema Context Engine: JDBC extraction (multi-vendor), edge-aware schema graph, join analysis, context strategies | [README](schema-context/README.md), [architecture](schema-context/docs/architecture.md), [changelog](CHANGELOG.md) |
| `contextos-core` | Typed graph with BFS, the shared context model, budget packing | [README](contextos-core/README.md) |
| `contextos-sql` | ContextOS provider built on the Schema Context Engine's graph, join analysis and formatter | [README](contextos-sql/README.md) |
| `contextos-code` | Java repository parsing, symbol graph, ranked code context | [README](contextos-code/README.md) |
| `contextos-incident` | Topology and telemetry, evidence collection and ranking | [README](contextos-incident/README.md) |
| `contextos` | Cross-domain investigation | [README](contextos/README.md) |
| `contextos-mcp` | MCP server exposing the investigation and drill-down tools | [README](contextos-mcp/README.md) |
| `contextos-eval` | Evaluation: does the context help a model find the root cause? Any OpenAI-compatible API | [README](contextos-eval/README.md) |

### Design principles

- **Each domain stands alone.** The Schema Context Engine has no dependency on
  ContextOS. The code and incident modules each have their own demo and tests.
- **Reuse before rebuild.** The SQL adapter delegates traversal, shortest paths,
  join quality and rendering to the Schema Context Engine.
- **Share only what repeats.** Code and Incident Context were built separately
  first. `contextos-core` holds only what both ended up needing: the typed graph
  with breadth-first traversal, and the item/package/budget model.
- **Explainable ranking.** Scores are products of named factors, such as edge
  weights along a path or kind × proximity × timing × strength, and are shown
  next to each item.
- **Provenance everywhere.** No item enters a package without saying where it
  came from and why it was chosen.
- **A real budget.** Items are packed greedily by score. Omitted items are
  reported with their cost, and the rendered package never exceeds the budget.

### Limitations

- Java only for code context; Spring MVC annotations only for endpoint links.
- Telemetry comes from files. There are no Datadog or PagerDuty connectors yet.
- Ranking measures relevance, not root cause. The timeline and the context map
  carry the causal story.
- Token counts are estimated at about 4 characters per token, not counted by a tokenizer.

### Build and test

Requires Java 17.

```bash
./gradlew test                                 # all modules
./gradlew :contextos:demo                      # cross-domain investigation
./gradlew :contextos-code:demo                 # code context only
./gradlew :contextos-incident:demo             # incident context only
./gradlew run                                  # SQL Schema Context demo
```

To use it on your own code and database, describe them in a `contextos.json`
file: repositories, databases over JDBC, and optionally incidents and telemetry.
The format is described under [Configuration](contextos/README.md#configuration).
Code and schema context work without any telemetry.

Fixtures live in [`examples/`](examples), generated by `examples/runtime/generate_fixtures.py`:
- a Spring-style `payment-service` codebase
- the topology and telemetry for INC-143 to INC-146
- evaluation rubrics in `examples/eval/cases.json`
- the `payments-db` schema

## Roadmap and contributing

The [roadmap](ROADMAP.md) is ordered by what would show ContextOS holds up:
- an evaluation against a strong baseline
- real telemetry (OpenTelemetry first)
- more frameworks and languages

[CONTRIBUTING](CONTRIBUTING.md) covers setup and the few rules the design depends on.

## 📜 License

[MIT](LICENSE)

## 👤 Author

**Moneet Devadig**  
Backend Engineer | Context Engineering Enthusiast
