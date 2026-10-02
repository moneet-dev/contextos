# ContextOS — cross-domain context

Connects the three domains so an agent investigating a production incident
gets the runtime evidence, the code involved and the database tables it
touches, packed under one token budget.

```
                  ┌─ logger ─────────────▶ class that logged the error
incident evidence ├─ endpoint ───────────▶ handler method (Spring mappings)
                  ├─ mentions ───────────▶ Class.method named in a deploy
                  └─ query on database ──▶ table ◀── data access ── code
```

## Usage

```java
ContextOS contextOS = ContextOS.builder()
        .runtime(new FileRuntimeSource(Path.of("examples/runtime")).load())
        .repository("payment-service", new JavaRepositorySource(Path.of("examples/payment-service")).load())
        .database("payments-db", new JdbcSchemaMetadataSource(connection, null).load())
        .build();

CrossDomainContext context = contextOS.investigate("INC-143", ContextBudget.tokens(4000));
context.rendered();   // context map, then incident, code and SQL context
context.links();      // every link followed, with its source item and weight
```

Repositories and databases are registered under the names the topology uses:
a service's `repository` field, and a `DATABASE` service's name.

## How links are found

Every link comes from data the providers already capture:

| From | To | Using |
|---|---|---|
| Evidence on a service with a registered repository | Class | the evidence's `logger` attribute, when the repository declares that class |
| Request span (no peer) | Handler method | the span's `operation` (e.g. `POST /payments/{id}/refund`) matched to `@*Mapping` routes |
| Change event | Method | `Class.method` mentioned in the description |
| Span or log on / to a registered database | Table | the table in the span operation (`SELECT payment_transactions`) or the SQL in the log message |
| Code item | Table | the code item's `tables` attribute (JPQL entities resolved via `@Table`), in databases its service queries |

Each target is collected once, through the strongest link to it:
- code at depth 2 in both directions
- tables at depth 1 in both directions

## Scoring and packing

A linked item scores **weight of the item it came from × its own score × 0.9**.
Code and SQL items are therefore never more relevant than the incident evidence
that led to them, and all domains share one scale. An item reached by several
links keeps its best score and records `linkedFrom`.

The package starts with a **context map** listing the links followed. After it
come the incident overview, then everything else by score. Items that don't fit
the budget are listed in `omitted`.

## Run

```bash
./gradlew :contextos:demo
./gradlew :contextos:demo --args="INC-143 2500"
./gradlew :contextos:test
```

The demo loads `examples/runtime`, `examples/payment-service` and
`examples/runtime/databases/payments-db.sql`. The SQL script runs in in-memory
SQLite, and the schema is read back through SQL Schema Context.
