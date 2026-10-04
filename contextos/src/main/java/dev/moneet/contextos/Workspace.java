package dev.moneet.contextos;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.source.JavaRepositorySource;
import dev.moneet.contextos.incident.domain.RuntimeSnapshot;
import dev.moneet.contextos.incident.source.FileRuntimeSource;
import dev.moneet.schema.domain.DatabaseSchema;
import dev.moneet.schema.jdbc.JdbcSchemaMetadataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Everything the tools read, loaded once at startup from a {@link ContextOsConfig}:
 * an optional runtime snapshot (topology, incidents, telemetry), code
 * repositories and database schemas, each by the name the topology uses.
 *
 * <p>Databases are read through SQL Schema Context, either over JDBC or from a
 * DDL script loaded into in-memory SQLite. JDBC connections are opened read-only
 * and only metadata is read. Used by the MCP server and the evaluation.
 */
public record Workspace(Path root,
                        Path runtimeDir,
                        RuntimeSnapshot runtime,
                        Map<String, CodeRepository> repositories,
                        Map<String, DatabaseSchema> databases) {

    public Workspace {
        repositories = Collections.unmodifiableMap(new LinkedHashMap<>(repositories));
        databases = Collections.unmodifiableMap(new LinkedHashMap<>(databases));
    }

    /**
     * Loads from a {@code contextos.json} file, or from a directory containing one,
     * reading passwords from the process environment.
     */
    public static Workspace load(Path path) {
        return load(path, System::getenv);
    }

    public static Workspace load(Path path, Function<String, String> environment) {
        Path file = Files.isDirectory(path) ? path.resolve(ContextOsConfig.FILE_NAME) : path;
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No " + ContextOsConfig.FILE_NAME + " at " + file.toAbsolutePath()
                    + "; see the contextos module README for the format");
        }
        ContextOsConfig config = ContextOsConfig.read(file);

        RuntimeSnapshot runtime = null;
        if (config.runtime() != null) {
            if (!Files.isRegularFile(config.runtime().resolve("services.json"))) {
                throw new IllegalArgumentException("No services.json in runtime directory " + config.runtime());
            }
            runtime = new FileRuntimeSource(config.runtime()).load();
        }

        Map<String, CodeRepository> repositories = new LinkedHashMap<>();
        config.repositories().forEach((name, dir) -> {
            if (!Files.isDirectory(dir)) {
                throw new IllegalArgumentException("Repository '" + name + "' not found at " + dir);
            }
            repositories.put(name, new JavaRepositorySource(dir).load());
        });

        Map<String, DatabaseSchema> databases = new LinkedHashMap<>();
        config.databases().forEach((name, db) -> databases.put(name, loadSchema(name, db, environment)));

        return new Workspace(file.toAbsolutePath().getParent(), config.runtime(), runtime, repositories, databases);
    }

    public boolean hasRuntime() {
        return runtime != null;
    }

    /** Cross-domain investigation over everything configured; needs a runtime. */
    public ContextOS contextOS() {
        if (runtime == null) {
            throw new IllegalStateException("No runtime is configured, so there are no incidents to investigate");
        }
        ContextOS.Builder builder = ContextOS.builder().runtime(runtime);
        repositories.forEach(builder::repository);
        databases.forEach(builder::database);
        return builder.build();
    }

    private static DatabaseSchema loadSchema(String name, ContextOsConfig.DatabaseConfig db,
                                             Function<String, String> environment) {
        if (!db.isJdbc()) {
            if (!Files.isRegularFile(db.ddl())) {
                throw new IllegalArgumentException("Database '" + name + "': DDL script not found at " + db.ddl());
            }
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                SqlScript.run(connection, db.ddl());
                return new JdbcSchemaMetadataSource(connection, null).load();
            } catch (SQLException e) {
                throw new IllegalStateException("Database '" + name + "': failed to load " + db.ddl(), e);
            }
        }

        String password = null;
        if (db.passwordEnv() != null) {
            password = environment.apply(db.passwordEnv());
            if (password == null) {
                throw new IllegalArgumentException("Database '" + name + "': set the " + db.passwordEnv()
                        + " environment variable to its password");
            }
        }
        try (Connection connection = DriverManager.getConnection(db.jdbcUrl(), db.user(), password)) {
            readOnly(connection);
            return new JdbcSchemaMetadataSource(connection, db.schema()).load();
        } catch (SQLException e) {
            String reason = e.getMessage() != null && e.getMessage().startsWith("No suitable driver")
                    ? "no JDBC driver for this URL on the classpath (PostgreSQL and SQLite are included; "
                    + "add other drivers' jars)"
                    : e.getMessage();
            throw new IllegalArgumentException("Database '" + name + "' (" + redact(db.jdbcUrl()) + "): " + reason, e);
        }
    }

    /**
     * A safety hint only: just metadata is read. Some drivers (e.g. SQLite) can't
     * switch an open connection to read-only; a read-only database user is the real
     * guarantee.
     */
    private static void readOnly(Connection connection) {
        try {
            connection.setReadOnly(true);
        } catch (SQLException e) {
            // not supported by this driver
        }
    }

    /** The URL without any credentials or parameters, which may carry secrets. */
    static String redact(String jdbcUrl) {
        String withoutParameters = jdbcUrl.replaceAll("[?;].*$", "");
        return withoutParameters.replaceAll("//[^/@]*@", "//");
    }
}
