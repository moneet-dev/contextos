# ContextOS — Core

The small set of pieces that SQL, Code and Incident Context all need. Each was
extracted only after it existed in at least two domains. The module has no
dependencies.

## Graph (`core.graph`)

| Type | Purpose |
|---|---|
| `Edge` | `source()` / `target()`; implemented by `SymbolReference` and `ServiceDependency` |
| `TypedGraph<E>` | Immutable graph indexed in both directions, with deterministic edge order |
| `TypedGraph.traverse` | Breadth-first search up to a depth, in `OUTGOING` / `INCOMING` / `BOTH` directions, with an optional edge filter |
| `Reached<E>` | A node with its shortest distance and the edge path that reached it |

Ranking stays in each domain; the formulas differ too much to share.

## Context (`core.context`)

| Type | Purpose |
|---|---|
| `ContextRequest` | target (table, symbol query or incident id), depth, budget |
| `ContextItem` | id, domain, kind, title, content, score (0..1), reason, provenance, attributes |
| `ContextProvider` | `collect(request)` returns ranked items for one domain; `provide(request)` packs them |
| `ContextBudget` / `TokenEstimator` | token limit; characters-per-token estimate (4 by default) |
| `ContextPackager` | Greedy packing by score: skips items that don't fit and keeps trying smaller ones |
| `ContextPackage` | included and omitted items with their costs, tokens used, rendered text |
| `ContextPackageRenderer` | Groups items by domain, each with a reason, score and source header |

An item's cost includes the domain header it introduces, so the rendered
package stays within the budget.

```java
ContextProvider provider = new CodeContextProvider(repository, graph);
ContextPackage pkg = provider.provide(
        new ContextRequest("PaymentService#charge", 2, ContextBudget.tokens(2000)));

pkg.rendered();   // === CODE CONTEXT === ...
pkg.omitted();    // what didn't fit, and how much it would have cost
```

## Providers

| Domain | Provider | Module | Target |
|---|---|---|---|
| `sql` | `SqlContextProvider` | `contextos-sql` | table name |
| `code` | `CodeContextProvider` | `contextos-code` | symbol query |
| `incident` | `IncidentContextProvider` | `contextos-incident` | incident id |
