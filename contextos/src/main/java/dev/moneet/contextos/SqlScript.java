package dev.moneet.contextos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/** Runs a DDL script: {@code --} line comments are removed, statements are split on {@code ;}. */
public final class SqlScript {

    private SqlScript() {
    }

    public static void run(Connection connection, Path script) {
        String sql;
        try {
            sql = Files.readAllLines(script, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read: " + script, e);
        }

        try (Statement statement = connection.createStatement()) {
            for (String part : sql.split(";")) {
                if (!part.isBlank()) {
                    statement.execute(part);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to run " + script, e);
        }
    }
}
