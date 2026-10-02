package dev.moneet.contextos.code.domain;

import java.util.List;
import java.util.Objects;

public record CodeFile(String path, String packageName, List<String> imports) {

    public CodeFile {
        Objects.requireNonNull(path, "path must not be null");
        imports = List.copyOf(imports);
    }
}
