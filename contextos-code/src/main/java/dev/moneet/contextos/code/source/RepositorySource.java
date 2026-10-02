package dev.moneet.contextos.code.source;

import dev.moneet.contextos.code.domain.CodeRepository;

public interface RepositorySource {
    CodeRepository load();
}
