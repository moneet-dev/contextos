package dev.moneet.contextos.code.domain;

import java.util.Objects;

/**
 * Position of a declaration in the repository. {@code file} is relative to the
 * repository root and always uses '/' as separator.
 */
public record SourceLocation(String file, int startLine, int endLine) {

    public SourceLocation {
        Objects.requireNonNull(file, "file must not be null");
    }

    @Override
    public String toString() {
        return file + ":" + startLine;
    }
}
