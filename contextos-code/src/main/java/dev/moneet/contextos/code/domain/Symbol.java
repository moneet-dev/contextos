package dev.moneet.contextos.code.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A declared type, method, constructor or field.
 *
 * <p>Ids are stable and derived from the declaration:
 * {@code com.acme.OrderService} for types,
 * {@code com.acme.OrderService#place(Order)} for methods and constructors,
 * {@code com.acme.OrderService#repository} for fields.
 * {@code displayName} is the same without the package.
 *
 * <p>{@code signature} is the declaration without a body (annotations included);
 * {@code source} is the original source text. For types both are the type header,
 * so members are never rendered twice.
 */
public record Symbol(String id,
                     SymbolKind kind,
                     String name,
                     String displayName,
                     String parentId,
                     SourceLocation location,
                     List<String> annotations,
                     String signature,
                     String source) {

    public Symbol {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(location, "location must not be null");
        annotations = List.copyOf(annotations);
    }

    public Optional<String> parent() {
        return Optional.ofNullable(parentId);
    }
}
