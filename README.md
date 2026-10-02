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
| Data | Schema Context Engine (root project) + `contextos-sql` | Tables, keys, indexes, join paths and join quality |
| Software | `contextos-code` | Symbols, calls, inheritance, Spring endpoints, data access |
| Runtime | `contextos-incident` | Service topology, logs, metrics, traces, changes |
| All three | `contextos` | Links between them, under one budget |

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

## Architecture

```
                         contextos            links domains, one budget
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
   contextos-sql       contextos-code      contextos-incident
   (adapter)           JavaParser          file-based telemetry
        │                    │                    │
   Schema Context            │                    │
   Engine (root)             │                    │
        └────────────────────┼────────────────────┘
                             ▼
                      contextos-core         TypedGraph + BFS, ContextItem,
                                             ContextPackage, budget packing
```

| Module | Purpose | Docs |
|---|---|---|
| *(root)* | Schema Context Engine: JDBC extraction (multi-vendor), edge-aware schema graph, join analysis, context strategies | [below](#schema-context-engine), [architecture](docs/architecture.md), [changelog](CHANGELOG.md) |
| `contextos-core` | Typed graph with BFS, the shared context model, budget packing | [README](contextos-core/README.md) |
| `contextos-sql` | ContextOS provider built on the Schema Context Engine's graph, join analysis and formatter | [README](contextos-sql/README.md) |
| `contextos-code` | Java repository parsing, symbol graph, ranked code context | [README](contextos-code/README.md) |
| `contextos-incident` | Topology and telemetry, evidence collection and ranking | [README](contextos-incident/README.md) |
| `contextos` | Cross-domain investigation | [README](contextos/README.md) |

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
```

Fixtures live in [`examples/`](examples):
- a Spring-style `payment-service` codebase
- the INC-143 topology and telemetry
- the `payments-db` schema

---

## Schema Context Engine

A lightweight Java library for extracting database schema metadata, building structural dependency graphs, and generating LLM-ready schema context.

This project is designed as a cleanly layered context engineering engine, not just a metadata dumper.

### 🚀 Features

- **JDBC-based schema extraction** — portable across database vendors
- **Immutable domain model** — thread-safe and predictable
- **Dependency graph construction** — FK-based relationship mapping
- **Tree-like relationship visualization** — clear hierarchical view
- **Depth-limited subgraph extraction** — focused context on demand
- **Full-schema and focused context generation** — flexible output strategies
- **Integration-tested** — using SQLite in-memory database

### 🏗 Architecture

The project follows a strict layered design:

```
JDBC Layer      → Extract metadata
Domain Layer    → Structural representation
Graph Layer     → Relationship reasoning
Context Layer   → LLM-ready context generation
```

#### Package Structure

```
dev.moneet.schema
├── jdbc       → Metadata extraction (JDBC → domain objects)
├── domain     → Immutable schema model (Table, Column, FK, Index, etc.)
├── graph      → Dependency graph logic (relationship reasoning)
└── context    → Context generation strategies (full/focused)
```

### 📦 Installation

Clone the repository:

```bash
git clone <your-repo-url>
cd schema-context
```

Build:

```bash
./gradlew build
```

Run:

```bash
./gradlew run
```
Run tests:

```bash
./gradlew test
```

### 🔍 Example Usage

#### 1️⃣ Extract Schema

```java
Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");

SchemaMetadataSource source = 
    new JdbcSchemaMetadataSource(conn, null);

DatabaseSchema schema = source.load();
```

#### 2️⃣ Build Dependency Graph

```java
SchemaGraph graph = 
    new SchemaGraphBuilder().build(schema);

System.out.println(graph.toTree());
```

Example output:

```
companies
  users
    orders
      invoices
```

#### 3️⃣ Generate Context (Full Schema)

```java
ContextStrategy strategy = new FullSchemaStrategy();
SchemaContextEngine engine = new SchemaContextEngine(strategy);

String context = engine.generate(schema, graph);
System.out.println(context);
```

#### 4️⃣ Generate Focused Context

```java
ContextStrategy strategy = 
    new FocusedSchemaStrategy("orders", 1);

SchemaContextEngine engine = 
    new SchemaContextEngine(strategy);

String context = engine.generate(schema, graph);
```

Output includes:

- Target table schema
- Related tables within specified depth
- All foreign key relationships

### 🧠 Design Philosophy

Built with:

- **Strict separation of concerns** — each layer has a single responsibility
- **Immutable domain modeling** — no surprise mutations
- **No JDBC leakage** — metadata extracted once, domain objects used thereafter
- **Graph-based reasoning** — relationship logic before formatting
- **Strategy-based context generation** — flexible output without tight coupling

**Ideal for:**

- DB-aware coding assistants
- Schema-aware AI agents
- Context compression engines
- Query reasoning systems

### 🧪 Testing

Uses SQLite in-memory database for:

- Integration-tested metadata extraction
- Graph behavior verification
- Context output validation

Run:

```bash
./gradlew clean test
```

### 🛣 Future Enhancements

- BFS-based distance prioritization
- Token-budget aware context trimming
- Natural language → schema mapping
- Multi-schema support
- Vendor-specific optimization plugins

### Version Roadmap

#### v0.1
Core schema extraction + DFS traversal.

#### v0.2 (Planned)
Distance-aware graph traversal with BFS and level grouping.

#### v0.3
Token-aware context prioritization.

## 📜 License

MIT

## 👤 Author

**Moneet Devadig**  
Backend Engineer | Context Engineering Enthusiast
