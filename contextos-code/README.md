# ContextOS — Code Context

Builds ranked, provenance-aware source-code context for LLMs from a Java repository.
It follows the same layering as SQL Schema Context and does not depend on it.

```
source   → Parse a repository (JavaParser + symbol solver)   ≈ jdbc
domain   → Immutable code model (Symbol, SymbolReference)     ≈ domain
graph    → Typed symbol graph, BFS with distances and paths   ≈ graph
context  → Lookup, ranking, rendering strategies              ≈ context
```

## Usage

```java
CodeRepository repository = new JavaRepositorySource(Path.of("path/to/repo")).load();
CodeGraph graph = new CodeGraphBuilder().build(repository);

CodeContextStrategy strategy = new FocusedCodeStrategy("PaymentService#charge", 2);
CodeContext context = new CodeContextEngine(strategy).generate(repository, graph);

context.items();     // ranked: symbol, score, distance, reason, path, data access
context.rendered();  // LLM-ready text, grouped by file
```

`FocusedCodeStrategy(query, depth, direction, mode, maxItems)` also takes:

- `Direction.OUTGOING` (what it uses), `INCOMING` (who uses it) or `BOTH`
- `RenderMode.SIGNATURES` or `FULL` (member source as written)
- a cap on the number of items

`FullCodeStrategy` renders an outline of every symbol.

Queries can be a symbol id, `Type`, `Type#method`, `Type#method(Param)`, a simple
member name, or a file path such as `service/PaymentService.java`.

## What is extracted

| Edge | Meaning |
|---|---|
| `CONTAINS` | type → member, outer → nested type |
| `CALLS` | method → method/constructor |
| `REFERENCES` | uses a type (fields, parameters, return types, type arguments, `new`) or a field |
| `EXTENDS` / `IMPLEMENTS` | type → supertype |
| `OVERRIDES` | method → same-name, same-arity method of a direct supertype |
| `IMPORTS` | top-level type → imported repository type |

Only the repository sources and the JDK are used for resolution. When a call can't
be resolved (e.g. `JpaRepository.save` with Spring absent), it is matched by
receiver type, name and argument count, or recorded as a reference to the receiver type.

**Data-access hints** link code to database tables for later cross-domain context:
`@Table`/`@Entity`, `@Query` (JPQL vs native) and SQL string literals.

## Ranking

Score = product of edge weights along the shortest path from the target
(`CALLS` 0.9, `OVERRIDES` 0.85, `EXTENDS`/`IMPLEMENTS` 0.8, `REFERENCES` 0.7,
`CONTAINS` 0.6, `IMPORTS` 0.3). Every item carries a reason such as
`called by PaymentController#pay(PaymentRequest)`; items more than one hop away also show the path.

## Run

```bash
./gradlew :contextos-code:demo
./gradlew :contextos-code:demo --args="examples/payment-service PaymentService#refund 1"
./gradlew :contextos-code:test
```

The demo uses `examples/payment-service`, a small Spring-style service that the
Incident and cross-domain phases will reuse.
