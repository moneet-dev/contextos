# ContextOS — SQL adapter

Exposes SQL Schema Context (the [`schema-context`](../schema-context/README.md) module) as a ContextOS provider. The SQL
library is used as it is and does not depend on ContextOS.

```java
DatabaseSchema schema = new JdbcSchemaMetadataSource(connection, null).load();

SqlContextProvider provider = new SqlContextProvider(schema);   // BIDIRECTIONAL
ContextPackage pkg = provider.provide(new ContextRequest("orders", 2, ContextBudget.tokens(1500)));
```

All graph logic comes from SQL Schema Context:

| Concern | SQL Schema Context API |
|---|---|
| Tables within depth | `SchemaGraph.traverse(target, depth, new BfsTraversalStrategy(), direction)` |
| Distance and join path per table | `SchemaGraph.getShortestPathEdges(target, table, direction)` |
| Join quality | `JoinPathAnalyzer.analyze(path, schema)` (EXCELLENT / GOOD / WEAK) |
| Rendering | `SchemaFormatter` (columns, PK/FK, indexes) |

- **Target** is a table name (case-insensitive). A blank target returns every table.
- **Score** is `0.8^distance`.
- **Direction** is a `TraversalDirection`. It defaults to `BIDIRECTIONAL`, as
  in `FocusedSchemaStrategy`.
- **Attributes** on related tables:
  - `join`: the join conditions from the target, e.g.
    `invoices.order_id = orders.id AND orders.user_id = users.id`
  - `joinQuality`: the weakest join on that path
- **Provenance** lists each foreign key on the path with its quality.
