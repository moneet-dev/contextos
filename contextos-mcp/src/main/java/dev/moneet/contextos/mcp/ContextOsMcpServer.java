package dev.moneet.contextos.mcp;

import dev.moneet.contextos.Workspace;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * ContextOS as an MCP server over stdio.
 *
 * <p>Usage: {@code java -jar contextos-mcp-all.jar [contextos.json or a directory containing it]}
 * (default {@code examples}). See the contextos module README for the config format.
 * Stdout carries only protocol messages; diagnostics go to stderr.
 */
public final class ContextOsMcpServer {

    static final String INSTRUCTIONS = """
            ContextOS builds ranked, provenance-aware context for investigating production incidents.
            Start with list_incidents, then investigate_incident: it follows links from the incident's \
            evidence to the code (logger, endpoint, mentioned methods) and database tables involved, \
            and packs everything under a token budget. Use code_context and schema_context to drill \
            into a symbol or table the investigation points to. Every item says why it was selected \
            and where it came from.""";

    private ContextOsMcpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        Path root = Path.of(args.length > 0 ? args[0] : "examples");
        Workspace workspace = Workspace.load(root);
        System.err.println("ContextOS MCP: loaded " + workspace.runtime() + " from " + root.toAbsolutePath());

        // The client ends the session by closing stdin
        CountDownLatch endOfInput = new CountDownLatch(1);
        McpSyncServer server = start(new ContextOsTools(workspace), new EndOfInputSignal(System.in, endOfInput),
                System.out);
        endOfInput.await();
        server.closeGracefully();
        System.exit(0);
    }

    /** Starts a server on the given streams; the transport reads requests on its own threads. */
    static McpSyncServer start(ContextOsTools tools, InputStream in, OutputStream out) {
        return McpServer.sync(new StdioServerTransportProvider(jsonMapper(), in, out))
                .serverInfo("contextos", "0.5.0")
                .instructions(INSTRUCTIONS)
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(specifications(tools))
                .build();
    }

    /**
     * Escapes all non-ASCII characters, so the wire format is pure ASCII and text such as
     * code read from source files survives whatever default charset the JVM applies to the
     * stdio streams (not UTF-8 on Windows with Java 17).
     */
    static McpJsonMapper jsonMapper() {
        return new JacksonMcpJsonMapper(JsonMapper.builder()
                .enable(JsonWriteFeature.ESCAPE_NON_ASCII)
                .build());
    }

    static List<SyncToolSpecification> specifications(ContextOsTools tools) {
        return List.of(
                tool("list_incidents",
                        "List the incidents available to investigate, with severity, start time and affected services.",
                        schema(Map.of(), List.of()),
                        args -> tools.listIncidents()),

                tool("investigate_incident",
                        "Build cross-domain context for an incident: ranked evidence (logs, metrics, traces, "
                                + "changes), the code it links to, and the database tables that code touches, "
                                + "packed under a token budget. Starts with a map of the links followed.",
                        schema(Map.of(
                                "incident_id", property("string", "Incident id, e.g. INC-143"),
                                "budget", property("integer", "Token budget (default 4000)")),
                                List.of("incident_id")),
                        args -> tools.investigateIncident(string(args, "incident_id"), integer(args, "budget"))),

                tool("code_context",
                        "Ranked code context around a symbol: its callers, callees, types and data access. "
                                + "Query by Type, Type#method, Type#method(Param), method name or file path.",
                        schema(Map.of(
                                "query", property("string", "Symbol query, e.g. PaymentService#refund"),
                                "repository", property("string",
                                        "Repository name; needed only when several are configured"),
                                "depth", property("integer", "Relationship hops to follow (default 2)"),
                                "budget", property("integer", "Token budget (default 4000)")),
                                List.of("query")),
                        args -> tools.codeContext(string(args, "repository"), string(args, "query"),
                                integer(args, "depth"), integer(args, "budget"))),

                tool("schema_context",
                        "Database schema context around a table: columns, keys, indexes, and related tables "
                                + "with join conditions and join quality. An empty table returns the whole schema.",
                        schema(Map.of(
                                "table", property("string", "Table name, e.g. payment_transactions"),
                                "database", property("string",
                                        "Database name; needed only when several are configured"),
                                "depth", property("integer", "Foreign-key hops to follow (default 1)"),
                                "budget", property("integer", "Token budget (default 4000)")),
                                List.of("table")),
                        args -> tools.schemaContext(string(args, "database"), string(args, "table"),
                                integer(args, "depth"), integer(args, "budget"))));
    }

    private static SyncToolSpecification tool(String name,
                                              String description,
                                              Map<String, Object> inputSchema,
                                              Function<Map<String, Object>, String> handler) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(name, inputSchema).description(description).build())
                .callHandler((exchange, request) -> call(handler, request))
                .build();
    }

    /** Bad input becomes a tool error the model can read and correct; anything else is reported too. */
    private static CallToolResult call(Function<Map<String, Object>, String> handler, CallToolRequest request) {
        Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
        try {
            return CallToolResult.builder().addTextContent(handler.apply(args)).isError(false).build();
        } catch (IllegalArgumentException e) {
            return CallToolResult.builder().addTextContent(e.getMessage()).isError(true).build();
        } catch (RuntimeException e) {
            e.printStackTrace(System.err);
            return CallToolResult.builder()
                    .addTextContent("Internal error: " + e.getClass().getSimpleName() + ": " + e.getMessage())
                    .isError(true).build();
        }
    }

    /** Counts down {@code latch} once the wrapped stream reports end of input. */
    private static final class EndOfInputSignal extends FilterInputStream {

        private final CountDownLatch latch;

        EndOfInputSignal(InputStream in, CountDownLatch latch) {
            super(in);
            this.latch = latch;
        }

        @Override
        public int read() throws IOException {
            return signal(super.read());
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return signal(super.read(buffer, offset, length));
        }

        private int signal(int result) {
            if (result < 0) {
                latch.countDown();
            }
            return result;
        }
    }

    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> property(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private static String string(Map<String, Object> args, String name) {
        Object value = args.get(name);
        return value == null ? null : value.toString();
    }

    private static Integer integer(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
    }
}
