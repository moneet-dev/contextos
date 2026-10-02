package dev.moneet.contextos.code.source;

import com.github.javaparser.ast.Node;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup tables shared by the extractors while a repository is being loaded.
 *
 * <p>The symbol solver may return declarations from its own parse of a file, so
 * method declarations are also indexed by file and position, which is identical
 * across parses.
 */
final class SymbolIndex {

    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Map<Node, String> nodeIds = new IdentityHashMap<>();
    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<String, String> locationIds = new HashMap<>();
    private final Map<String, Integer> parameterCounts = new HashMap<>();
    private final Map<String, List<String>> members = new HashMap<>();
    private final Map<String, List<String>> typesBySimpleName = new HashMap<>();

    void add(Symbol symbol, Node declaration, int parameterCount) {
        symbols.put(symbol.id(), symbol);
        nodeIds.put(declaration, symbol.id());
        nodes.put(symbol.id(), declaration);
        locationKey(declaration).ifPresent(key -> locationIds.put(key, symbol.id()));
        parameterCounts.put(symbol.id(), parameterCount);

        symbol.parent().ifPresent(parent ->
                members.computeIfAbsent(parent, k -> new ArrayList<>()).add(symbol.id()));

        if (symbol.kind().isType()) {
            typesBySimpleName.computeIfAbsent(symbol.name(), k -> new ArrayList<>()).add(symbol.id());
        }
    }

    List<Symbol> symbols() {
        return List.copyOf(symbols.values());
    }

    Optional<Symbol> get(String id) {
        return Optional.ofNullable(symbols.get(id));
    }

    Optional<Node> nodeOf(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    Optional<String> idOf(Node declaration) {
        return Optional.ofNullable(nodeIds.get(declaration));
    }

    /** Id of a declaration node that may come from a different parse of the same file. */
    Optional<String> idAt(Node declaration) {
        String id = nodeIds.get(declaration);
        if (id != null) {
            return Optional.of(id);
        }
        return locationKey(declaration).map(locationIds::get);
    }

    /** Innermost declared symbol containing {@code node}. */
    Optional<String> enclosingSymbol(Node node) {
        Node current = node;
        while (current != null) {
            String id = nodeIds.get(current);
            if (id != null) {
                return Optional.of(id);
            }
            current = current.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    /** Innermost declared type containing {@code node}. */
    Optional<String> enclosingType(Node node) {
        Optional<String> id = enclosingSymbol(node);
        while (id.isPresent()) {
            Symbol symbol = symbols.get(id.get());
            if (symbol.kind().isType()) {
                return id;
            }
            id = symbol.parent();
        }
        return Optional.empty();
    }

    Optional<String> typeId(String qualifiedName) {
        Symbol symbol = symbols.get(qualifiedName);
        return symbol != null && symbol.kind().isType() ? Optional.of(qualifiedName) : Optional.empty();
    }

    List<String> typesNamed(String simpleName) {
        return typesBySimpleName.getOrDefault(simpleName, List.of());
    }

    List<String> members(String typeId) {
        return members.getOrDefault(typeId, List.of());
    }

    /** Methods of {@code typeId} with the given name and parameter count. */
    List<String> methods(String typeId, String name, int parameterCount) {
        return members(typeId).stream()
                .map(symbols::get)
                .filter(s -> s.kind() == SymbolKind.METHOD || s.kind() == SymbolKind.CONSTRUCTOR)
                .filter(s -> s.name().equals(name))
                .filter(s -> parameterCounts.get(s.id()) == parameterCount)
                .map(Symbol::id)
                .toList();
    }

    int parameterCount(String id) {
        return parameterCounts.getOrDefault(id, 0);
    }

    private static Optional<String> locationKey(Node node) {
        Optional<Path> path = node.findCompilationUnit()
                .flatMap(cu -> cu.getStorage())
                .map(storage -> storage.getPath().toAbsolutePath().normalize());

        if (path.isEmpty() || node.getBegin().isEmpty()) {
            return Optional.empty();
        }
        var begin = node.getBegin().get();
        return Optional.of(path.get() + ":" + begin.line + ":" + begin.column);
    }
}
