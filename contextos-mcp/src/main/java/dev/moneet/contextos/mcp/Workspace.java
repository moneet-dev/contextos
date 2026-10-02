package dev.moneet.contextos.mcp;

import dev.moneet.contextos.ContextOS;
import dev.moneet.contextos.SqlScript;
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
import java.util.Objects;

/**
 * Everything the tools read, loaded once at startup from a directory laid out
 * like {@code examples/}:
 * <pre>
 * runtime/                       topology, incidents, telemetry (FileRuntimeSource)
 * runtime/databases/&lt;name&gt;.sql   one DDL script per DATABASE service
 * &lt;repository&gt;/                  source code, named as in a service's "repository"
 * </pre>
 * Each DDL script is loaded into in-memory SQLite and read back through SQL
 * Schema Context.
 */
public record Workspace(RuntimeSnapshot runtime,
                        String repositoryName,
                        CodeRepository repository,
                        String databaseName,
                        DatabaseSchema schema) {

    public Workspace {
        Objects.requireNonNull(runtime, "runtime must not be null");
    }

    /** Loads the single repository and database the topology refers to. */
    public static Workspace load(Path root) {
        Path runtimeDir = root.resolve("runtime");
        if (!Files.isRegularFile(runtimeDir.resolve("services.json"))) {
            throw new IllegalArgumentException("No runtime/services.json under " + root.toAbsolutePath());
        }
        RuntimeSnapshot runtime = new FileRuntimeSource(runtimeDir).load();

        String repositoryName = runtime.getTopology().getServices().stream()
                .map(s -> s.repository())
                .filter(Objects::nonNull)
                .filter(name -> Files.isDirectory(root.resolve(name)))
                .findFirst()
                .orElse(null);
        CodeRepository repository = repositoryName == null
                ? null
                : new JavaRepositorySource(root.resolve(repositoryName)).load();

        String databaseName = runtime.getTopology().getServices().stream()
                .filter(s -> s.kind().name().equals("DATABASE"))
                .map(s -> s.name())
                .filter(name -> Files.isRegularFile(runtimeDir.resolve("databases/" + name + ".sql")))
                .findFirst()
                .orElse(null);
        DatabaseSchema schema = databaseName == null
                ? null
                : loadSchema(runtimeDir.resolve("databases/" + databaseName + ".sql"));

        return new Workspace(runtime, repositoryName, repository, databaseName, schema);
    }

    public ContextOS contextOS() {
        ContextOS.Builder builder = ContextOS.builder().runtime(runtime);
        if (repository != null) {
            builder.repository(repositoryName, repository);
        }
        if (schema != null) {
            builder.database(databaseName, schema);
        }
        return builder.build();
    }

    private static DatabaseSchema loadSchema(Path script) {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            SqlScript.run(connection, script);
            return new JdbcSchemaMetadataSource(connection, null).load();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load " + script, e);
        }
    }
}
