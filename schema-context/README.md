# Schema Context Engine

> Part of [ContextOS](../README.md). This module is the database schema engine that
> `contextos-sql` builds on. It has no dependency on ContextOS and can be used on its own.

A lightweight Java library for extracting database schema metadata, building structural dependency graphs, and generating LLM-ready schema context.

This project is designed as a cleanly layered context engineering engine, not just a metadata dumper.

## 🚀 Features

- **JDBC-based schema extraction** — portable across database vendors
- **Immutable domain model** — thread-safe and predictable
- **Dependency graph construction** — FK-based relationship mapping
- **Tree-like relationship visualization** — clear hierarchical view
- **Depth-limited subgraph extraction** — focused context on demand
- **Full-schema and focused context generation** — flexible output strategies
- **Integration-tested** — using SQLite in-memory database

## 🏗 Architecture

The project follows a strict layered design:

```
JDBC Layer      → Extract metadata
Domain Layer    → Structural representation
Graph Layer     → Relationship reasoning
Context Layer   → LLM-ready context generation
```

### Package Structure

```
dev.moneet.schema
├── jdbc       → Metadata extraction (JDBC → domain objects)
├── domain     → Immutable schema model (Table, Column, FK, Index, etc.)
├── graph      → Dependency graph logic (relationship reasoning)
└── context    → Context generation strategies (full/focused)
```

## 📦 Installation

Clone the repository:

```bash
git clone <your-repo-url>
cd contextos
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

## 🔍 Example Usage

### 1️⃣ Extract Schema

```java
Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");

SchemaMetadataSource source = 
    new JdbcSchemaMetadataSource(conn, null);

DatabaseSchema schema = source.load();
```

### 2️⃣ Build Dependency Graph

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

### 3️⃣ Generate Context (Full Schema)

```java
ContextStrategy strategy = new FullSchemaStrategy();
SchemaContextEngine engine = new SchemaContextEngine(strategy);

String context = engine.generate(schema, graph);
System.out.println(context);
```

### 4️⃣ Generate Focused Context

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

## 🧠 Design Philosophy

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

## 🧪 Testing

Uses SQLite in-memory database for:

- Integration-tested metadata extraction
- Graph behavior verification
- Context output validation

Run:

```bash
./gradlew clean test
```

## 🛣 Future Enhancements

- BFS-based distance prioritization
- Token-budget aware context trimming
- Natural language → schema mapping
- Multi-schema support
- Vendor-specific optimization plugins

## Version Roadmap

### v0.1
Core schema extraction + DFS traversal.

### v0.2 (Planned)
Distance-aware graph traversal with BFS and level grouping.

### v0.3
Token-aware context prioritization.

## License

MIT, as for the rest of the repository.
