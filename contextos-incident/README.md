# ContextOS — Incident Context

Builds ranked, explainable incident context for LLMs. It works from a service
topology and production telemetry: logs, metrics, traces and change events.
It follows the same layering as SQL Schema Context and Code Context and depends
on neither.

```
source    → Load topology, incidents and telemetry from files   ≈ jdbc
domain    → Immutable runtime model (Service, Span, Evidence…)  ≈ domain
graph     → Typed service graph, BFS with distances and paths   ≈ graph
evidence  → Collect findings from telemetry, rank them          (new)
context   → Strategies and rendering                            ≈ context
```

## Usage

```java
RuntimeSnapshot snapshot = new FileRuntimeSource(Path.of("examples/runtime")).load();
ServiceGraph graph = new ServiceGraphBuilder().build(snapshot.getTopology());

IncidentContextStrategy strategy = new FocusedIncidentStrategy("INC-143", 2);
IncidentContext context = new IncidentContextEngine(strategy).generate(snapshot, graph);

context.scope();     // services within 2 hops of the affected services, with paths
context.evidence();  // ranked: evidence, score, distance, reason, score breakdown
context.rendered();  // incident, services in scope, timeline, ranked evidence
```

`FocusedIncidentStrategy(incidentId, depth, direction, maxEvidence)` takes
`Direction.DEPENDENCIES` (candidate causes), `DEPENDENTS` (blast radius) or `BOTH`.

## Evidence

All evidence comes from the services in scope and from a window around the
incident start: 15 minutes before to 30 minutes after. Metrics and spans are
compared against the 45 minutes before that window. Changes are taken from the
previous 24 hours.

| Kind | Collected when |
|---|---|
| `CHANGE` | A deploy, config change, flag flip or job run |
| `ERROR_LOGS` / `WARNING_LOGS` | Log lines grouped by service, logger and message template (numbers and ids masked) |
| `METRIC_ANOMALY` | Two consecutive samples deviate from the baseline mean by more than max(3σ, 50%) |
| `FAILED_SPANS` | 3 or more failed spans of one (service, operation, peer) |
| `SLOW_SPANS` | Successful spans with a median at least 3x and 100 ms above the baseline |

Score = **kind × proximity × timing × strength**, and every factor is shown in the output:

- **kind**: errors and failed spans 1.0; anomalies, slow spans and changes 0.9; warnings 0.7.
- **proximity**: 1.0 for an affected service, −0.15 per hop.
- **timing**: 1.0 from 15 minutes before to 2 minutes after the incident start. Later signals decay to 0.3 at +30 minutes. Earlier changes decay on a log scale to 0.7 at 24 hours before.
- **strength**: volume, deviation or failure rate.

Each piece of evidence keeps the file and line of its first telemetry records,
plus structured attributes (logger, exception, metric, operation, peer) for
linking to code and database context.

## Fixture format

```
services.json            {"services": [{name, kind, description?, repository?}],
                          "dependencies": [{from, to, kind}]}      kind: CALLS | QUERIES | PUBLISHES | CONSUMES
incidents.json           {"incidents": [{id, title, severity, startedAt, detectedAt?,
                                         affectedServices, symptoms}]}
telemetry/logs.jsonl     {timestamp, service, level, logger?, message, exception?, traceId?}
telemetry/metrics.csv    timestamp,service,metric,value
telemetry/traces.jsonl   {traceId, spanId, parentSpanId?, service, operation, peer?,
                          start, durationMs, status, error?}
telemetry/changes.jsonl  {timestamp, service, type, description}  type: DEPLOY | CONFIG | FEATURE_FLAG | JOB
```

`examples/runtime` holds INC-143. A refund batch drives a slow
`payment_transactions` query on the payment database. That query exhausts
payment-service's connection pool, and charges return 5xx. The fixture also
contains unrelated noise (a fraud-api warning and later SMTP failures).

## Run

```bash
./gradlew :contextos-incident:demo
./gradlew :contextos-incident:demo --args="examples/runtime INC-143 1"
./gradlew :contextos-incident:test
```
