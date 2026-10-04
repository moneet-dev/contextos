# ContextOS — MCP server

Exposes ContextOS to MCP clients such as Claude Code, over stdio.

## Tools

| Tool | Arguments | Returns |
|---|---|---|
| `list_incidents` | none | Incidents with severity, start time and affected services; the configured repositories and databases |
| `investigate_incident` | `incident_id`, `budget`? | Cross-domain context: context map, ranked incident evidence, linked code and tables |
| `code_context` | `query`, `repository`?, `depth`? (2), `budget`? | Ranked code around a symbol (`Type`, `Type#method`, file path, ...) |
| `schema_context` | `table`, `database`?, `depth`? (1), `budget`? | A table with related tables, join conditions and join quality; empty `table` returns the whole schema |

Budgets default to 4,000 estimated tokens. Each result ends with the tokens
used and what was omitted for budget.

Problems with the request are returned as tool errors the model can read and
correct: an unknown incident, symbol or table, or out-of-range numbers.
Arguments are also validated against each tool's input schema.

## Run

The jar and the example workspace are attached to every
[release](https://github.com/moneet-dev/contextos/releases/latest). To build the jar instead:

```bash
./gradlew :contextos-mcp:serverJar
java -jar contextos-mcp/build/libs/contextos-mcp-all.jar examples
```

The argument is a `contextos.json` file, or a directory containing one. It
lists the code repositories, the databases (over JDBC, or from a DDL script) and,
optionally, the runtime folder with incidents and telemetry. See
[Configuration](../contextos/README.md#configuration). Without a runtime,
`code_context` and `schema_context` still work.

`repository` and `database` are needed only when more than one is configured.

### Register with Claude Code

```bash
claude mcp add contextos -- java -jar /absolute/path/to/contextos-mcp/build/libs/contextos-mcp-all.jar /absolute/path/to/contextos.json
```

Then ask, for example: *"What caused INC-143? Use the contextos tools."*

## Notes

- Stdout carries only protocol messages. Diagnostics go to stderr, including
  SLF4J's "no providers" notice, which is harmless.
- Responses escape non-ASCII characters (e.g. `→`), so they arrive intact
  whatever default charset the JVM uses for stdio. On Windows with Java 17 that
  charset is not UTF-8.
- The server exits when the client closes stdin.
