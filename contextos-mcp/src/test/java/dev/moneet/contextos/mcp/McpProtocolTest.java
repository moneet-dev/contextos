package dev.moneet.contextos.mcp;

import dev.moneet.contextos.Workspace;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Speaks newline-delimited JSON-RPC to the server over in-memory streams, the
 * same way an MCP client does over stdio.
 */
class McpProtocolTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static PipedOutputStream toServer;
    private static final BlockingQueue<String> fromServer = new LinkedBlockingQueue<>();
    private static McpSyncServer server;
    private static int nextId = 1;

    @BeforeAll
    static void start() throws Exception {
        PipedInputStream serverIn = new PipedInputStream(1 << 20);
        toServer = new PipedOutputStream(serverIn);

        ContextOsTools tools = new ContextOsTools(Workspace.load(Path.of(System.getProperty("contextos.examples"))));
        server = ContextOsMcpServer.start(tools, serverIn, new LineQueueOutputStream(fromServer));

        JsonNode init = request("initialize", """
                {"protocolVersion": "2025-06-18", "capabilities": {},
                 "clientInfo": {"name": "test-client", "version": "1.0"}}""");
        assertEquals("contextos", init.path("result").path("serverInfo").path("name").asString());
        notify("notifications/initialized");
    }

    @AfterAll
    static void stop() throws IOException {
        server.closeGracefully();
        toServer.close();
    }

    @Test
    void shouldAdvertiseInstructionsAndFourTools() throws Exception {
        JsonNode tools = request("tools/list", "{}").path("result").path("tools");

        List<String> names = new ArrayList<>();
        tools.forEach(tool -> names.add(tool.path("name").asString()));
        assertEquals(List.of("list_incidents", "investigate_incident", "code_context", "schema_context"), names);

        JsonNode investigate = tools.get(1);
        assertEquals("incident_id", investigate.path("inputSchema").path("required").get(0).asString());
        assertEquals("integer", investigate.path("inputSchema").path("properties").path("budget").path("type").asString());
    }

    @Test
    void shouldInvestigateIncidentOverProtocol() throws Exception {
        JsonNode result = request("tools/call", """
                {"name": "investigate_incident", "arguments": {"incident_id": "INC-143", "budget": 2500}}""")
                .path("result");

        assertFalse(result.path("isError").asBoolean());
        String text = result.path("content").get(0).path("text").asString();
        assertTrue(text.startsWith("=== CONTEXTOS CONTEXT ==="), text);
        assertTrue(text.contains("--endpoint POST /payments/{id}/refund--> PaymentController#refund(long)"));
    }

    @Test
    void shouldReturnToolErrorsForBadInput() throws Exception {
        JsonNode result = request("tools/call", """
                {"name": "investigate_incident", "arguments": {"incident_id": "INC-999"}}""").path("result");

        assertTrue(result.path("isError").asBoolean());
        assertEquals("Incident not found: INC-999", result.path("content").get(0).path("text").asString());

        // Arguments are validated against the input schema before the handler runs
        JsonNode badNumber = request("tools/call", """
                {"name": "code_context", "arguments": {"query": "PaymentService", "depth": "deep"}}""").path("result");
        assertTrue(badNumber.path("isError").asBoolean());
        assertTrue(badNumber.path("content").get(0).path("text").asString().contains("/depth"));
    }

    @Test
    void shouldWriteNonAsciiTextAsAsciiEscapes() throws Exception {
        // Unicode escapes rather than literal characters, so this file's compile encoding doesn't matter
        String json = ContextOsMcpServer.jsonMapper().writeValueAsString(Map.of("text", "a \u2192 b \u00e9"));

        assertTrue(json.chars().allMatch(c -> c < 128), json);
        assertTrue(json.contains("a \\u2192 b \\u00E9") || json.contains("a \\u2192 b \\u00e9"), json);
        assertEquals("a \u2192 b \u00e9", JSON.readTree(json).path("text").asString());
    }

    private static synchronized JsonNode request(String method, String params) throws Exception {
        int id = nextId++;
        send("{\"jsonrpc\": \"2.0\", \"id\": " + id + ", \"method\": \"" + method + "\", \"params\": " + params + "}");

        // Skip anything that isn't the response to this request (e.g. server notifications)
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String line = fromServer.poll(1, TimeUnit.SECONDS);
            if (line == null) {
                continue;
            }
            JsonNode message = JSON.readTree(line);
            if (message.path("id").asInt(-1) == id) {
                assertTrue(message.path("error").isMissingNode(), "error response: " + line);
                return message;
            }
        }
        throw new AssertionError("No response to " + method);
    }

    private static void notify(String method) throws IOException {
        send("{\"jsonrpc\": \"2.0\", \"method\": \"" + method + "\"}");
    }

    private static void send(String json) throws IOException {
        toServer.write((json.replace('\n', ' ') + "\n").getBytes(StandardCharsets.UTF_8));
        toServer.flush();
    }

    /**
     * Collects server output line by line. Used instead of a pipe because the
     * server writes from pooled threads, which a PipedInputStream rejects once
     * the writing thread has ended.
     */
    private static final class LineQueueOutputStream extends OutputStream {

        private final BlockingQueue<String> lines;
        private final ByteArrayOutputStream current = new ByteArrayOutputStream();

        LineQueueOutputStream(BlockingQueue<String> lines) {
            this.lines = lines;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                lines.add(current.toString(StandardCharsets.UTF_8));
                current.reset();
            } else {
                current.write(b);
            }
        }
    }
}
