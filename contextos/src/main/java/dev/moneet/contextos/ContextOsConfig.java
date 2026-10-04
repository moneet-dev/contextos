package dev.moneet.contextos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What a ContextOS workspace contains, read from {@code contextos.json}:
 * <pre>
 * {
 *   "runtime": "runtime",                               (optional)
 *   "repositories": { "payment-service": "../payment-service" },
 *   "databases": {
 *     "payments-db": { "jdbcUrl": "jdbc:postgresql://localhost:5432/payments",
 *                      "user": "contextos_ro", "passwordEnv": "PAYMENTS_DB_PASSWORD",
 *                      "schema": "public" },
 *     "local-db":    { "ddl": "databases/local.sql" }
 *   }
 * }
 * </pre>
 * Paths are relative to the config file. Names match the topology: a service's
 * {@code repository} field, and the name of a {@code DATABASE} service. A
 * password is never written in the file, only the environment variable that
 * holds it. Unknown keys are rejected, so a typo doesn't silently drop a setting.
 */
public record ContextOsConfig(Path runtime, Map<String, Path> repositories, Map<String, DatabaseConfig> databases) {

    /**
     * Either a live connection ({@code jdbcUrl}, optionally {@code user},
     * {@code passwordEnv} and {@code schema}) or a DDL script ({@code ddl}) that is
     * loaded into in-memory SQLite.
     */
    public record DatabaseConfig(String jdbcUrl, String user, String passwordEnv, String schema, Path ddl) {

        public boolean isJdbc() {
            return jdbcUrl != null;
        }
    }

    public static final String FILE_NAME = "contextos.json";

    private static final Set<String> TOP_LEVEL = Set.of("runtime", "repositories", "databases");
    private static final Set<String> DATABASE_KEYS = Set.of("jdbcUrl", "user", "passwordEnv", "schema", "ddl");

    public ContextOsConfig {
        // In the order written in the file
        repositories = Collections.unmodifiableMap(new LinkedHashMap<>(repositories));
        databases = Collections.unmodifiableMap(new LinkedHashMap<>(databases));
    }

    public static ContextOsConfig read(Path file) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(file + " must contain a JSON object");
        }
        checkKeys(root, TOP_LEVEL, file.toString());
        Path base = file.toAbsolutePath().getParent();

        Path runtime = root.hasNonNull("runtime") ? base.resolve(root.get("runtime").asText()).normalize() : null;

        Map<String, Path> repositories = new LinkedHashMap<>();
        root.path("repositories").fields().forEachRemaining(entry ->
                repositories.put(entry.getKey(), base.resolve(entry.getValue().asText()).normalize()));

        Map<String, DatabaseConfig> databases = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> entries = root.path("databases").fields();
        while (entries.hasNext()) {
            Map.Entry<String, JsonNode> entry = entries.next();
            String where = file + " database '" + entry.getKey() + "'";
            JsonNode node = entry.getValue();
            checkKeys(node, DATABASE_KEYS, where);

            boolean jdbc = node.hasNonNull("jdbcUrl");
            boolean ddl = node.hasNonNull("ddl");
            if (jdbc == ddl) {
                throw new IllegalArgumentException(where + ": give exactly one of \"jdbcUrl\" or \"ddl\"");
            }
            databases.put(entry.getKey(), new DatabaseConfig(
                    text(node, "jdbcUrl"), text(node, "user"), text(node, "passwordEnv"), text(node, "schema"),
                    ddl ? base.resolve(node.get("ddl").asText()).normalize() : null));
        }

        return new ContextOsConfig(runtime, repositories, databases);
    }

    private static void checkKeys(JsonNode node, Set<String> allowed, String where) {
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException(where + ": unknown key \"" + name + "\" (expected one of "
                        + allowed + ")");
            }
        });
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
