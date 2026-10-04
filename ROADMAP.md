# Roadmap

ContextOS works end to end on the example workspace: it investigates an
incident, follows links into the code and the database, and packs the result
under a token budget, as a library or as an MCP server. What it hasn't yet
shown is that it holds up on real systems and beats a strong baseline. The
roadmap is ordered by that.

Items with an issue link are open for contributors.

## 1. Evaluation that can be trusted

The early evaluation (10 trials, 4 synthetic incidents, one model) found the
same accuracy as raw telemetry with about 40% of the tokens. That is a
signal, not a result.

- [ ] A stronger raw baseline: WARN/ERROR lines and changes, newest first
      around the incident start, under the same budget ([#15](https://github.com/moneet-dev/contextos/issues/15))
- [ ] A budget sweep (1,000 / 2,000 / 4,000 tokens) to show where ranking matters ([#16](https://github.com/moneet-dev/contextos/issues/16))
- [ ] More and harder incidents: red herrings that cross domains, causes
      outside the affected service, incidents with no code or schema cause ([#17](https://github.com/moneet-dev/contextos/issues/17))
- [ ] Results across several models and providers, published with their inputs fingerprint
- [ ] Token counts from a real tokenizer instead of the 4-characters estimate ([#18](https://github.com/moneet-dev/contextos/issues/18))

## 2. Real telemetry

Telemetry is read from files in ContextOS's own format today.

- [ ] Import OpenTelemetry (OTLP JSON) traces and logs ([#19](https://github.com/moneet-dev/contextos/issues/19))
- [ ] Prometheus metrics: query a range around the incident, compute baselines
- [ ] Connectors for hosted platforms (Datadog, Grafana, PagerDuty incidents)
- [ ] Changes from git history and deploy logs, linked to commits

## 3. More code

- [ ] Endpoint links beyond Spring MVC: JAX-RS, Micronaut, Kafka and message listeners ([#20](https://github.com/moneet-dev/contextos/issues/20))
- [ ] A second language, likely Python or TypeScript, behind the same code graph
- [ ] Incremental indexing for large repositories

## 4. Easier to adopt

- [x] Self-contained MCP server jar on GitHub releases
- [ ] MCP over HTTP for shared, remote use
- [ ] Libraries published to Maven Central
- [ ] A container image with the example workspace
