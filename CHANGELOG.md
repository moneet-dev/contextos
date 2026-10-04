# Changelog

All notable changes to this project are documented here.

---

## ContextOS – Repository layout and configuration

### Changed

- The repository is now ContextOS: the Gradle root project is `contextos`, and the SQL
  Schema Context engine moved from the root into the `schema-context` module (packages
  and APIs unchanged; its docs are in `schema-context/README.md`)

### Added

- `contextos.json` workspace configuration: an optional runtime, any number of code
  repositories, and databases over JDBC (PostgreSQL and SQLite drivers included;
  passwords only from environment variables) or from DDL scripts
- `--max-trials` for the evaluation, which now runs one round of every incident and
  condition at a time
- Fixture background noise: five unrelated services with access logs, harmless
  warnings, low-rate errors, metrics and traces around every incident
- Evaluation runs record a fingerprint of the rubric and fixtures; `--resume` refuses
  to mix results from different inputs

### Fixed

- INC-146 rubric: the separate "database is healthy" criterion was folded into the
  root-cause criterion; it rewarded mentioning database health rather than avoiding
  the INC-143 misdiagnosis

---

## ContextOS – Code, Incident and Cross-Domain Context

ContextOS modules added around the Schema Context Engine, which is unchanged
and has no dependency on them.

### Added

- `contextos-code`: Java repository parsing (JavaParser), typed symbol graph,
  ranked code context with provenance, data-access hints, Spring endpoint lookup
- `contextos-incident`: service topology and file-based telemetry (logs,
  metrics, traces, changes), evidence collection and explainable ranking
- `contextos-core`: typed graph with BFS, `ContextItem` / `ContextPackage`,
  token budget and greedy packing
- `contextos-sql`: provider built on `SchemaGraph`, `BfsTraversalStrategy`,
  `getShortestPathEdges`, `JoinPathAnalyzer` and `SchemaFormatter`
- `contextos`: cross-domain investigation from an incident to code and tables
  under one budget
- `contextos-mcp`: stdio MCP server with `list_incidents`, `investigate_incident`,
  `code_context` and `schema_context` tools
- `contextos-eval`: evaluation harness comparing raw telemetry, incident context and
  cross-domain context, for any OpenAI-compatible model API
- Incident context lists healthy signals (metrics within baseline, normal dependency
  calls) for the affected services and their dependencies, to rule causes out
- `examples/`: payment-service codebase, runtime snapshot with incidents INC-143 to INC-146
  (`generate_fixtures.py`), payments-db schema, evaluation rubrics

### Architectural Decisions

- Each domain is usable on its own; shared pieces were extracted only after
  Code and Incident Context both needed them
- The SQL adapter reuses the engine's traversal, path and join analysis rather
  than reimplementing them
- No LLM in the loop: graphs, named ranking factors and an explicit budget

---

## v0.4 – Minimal Multi-Vendor Metadata Support

### Added

- `DatabaseVendor` enum for vendor detection
- Vendor resolution via `DatabaseMetaData#getDatabaseProductName()`
- Schema resolution logic:
  - PostgreSQL → public
  - MySQL → uses catalog
  - SQL Server → dbo
  - Oracle → current user
- Catalog + schema passed to all extractors
- Identifier normalization using JDBC metadata flags
- Multi-vendor compatibility for:
  - Table extraction
  - Column extraction
  - Primary key extraction
  - Foreign key extraction
  - Index extraction

### Improved

- Graph consistency across different JDBC drivers
- Reduced risk of identifier mismatch due to case handling
- Cleaner extraction flow in `JdbcSchemaMetadataSource`

### Architectural Decisions

- No dialect abstraction layer introduced
- No plugin mechanism added
- Vendor handling remains minimal and localized
- Design remains metadata-only

### Notes

Multi-vendor support validated against:

- SQLite
- PostgreSQL
- MySQL

Docker/Testcontainers not required for local development.

---

## v0.3 – Graph Enhancements

- Edge-based `SchemaGraph`
- Directional traversal support
- BFS + DFS strategies
- Shortest path detection
- Join path formatting
- Index-aware join quality hints
- Level grouping API
- Distance map API

---

## v0.2 – Context Engine

- `FullSchemaStrategy`
- `FocusedSchemaStrategy`
- Depth-controlled traversal
- Context generation engine

---

## v0.1 – Core Extraction + DFS

- JDBC metadata extraction
- Domain modeling
- Basic graph building
- DFS traversal