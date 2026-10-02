package dev.moneet.contextos.code.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable snapshot of a parsed source repository: files, declared symbols,
 * the references between them and data-access hints.
 */
public final class CodeRepository {

    private final String root;
    private final List<CodeFile> files;
    private final Map<String, Symbol> symbols;
    private final List<SymbolReference> references;
    private final Map<String, List<DataAccess>> dataAccess;
    private final List<String> skippedFiles;

    public CodeRepository(String root,
                          List<CodeFile> files,
                          List<Symbol> symbols,
                          List<SymbolReference> references,
                          List<DataAccess> dataAccess,
                          List<String> skippedFiles) {
        this.root = Objects.requireNonNull(root, "root must not be null");
        this.files = List.copyOf(files);
        this.references = List.copyOf(references);
        this.skippedFiles = List.copyOf(skippedFiles);

        Map<String, Symbol> byId = new LinkedHashMap<>();
        for (Symbol symbol : symbols) {
            byId.put(symbol.id(), symbol);
        }
        this.symbols = Collections.unmodifiableMap(byId);

        this.dataAccess = dataAccess.stream()
                .collect(Collectors.groupingBy(DataAccess::symbolId,
                        LinkedHashMap::new, Collectors.toUnmodifiableList()));
    }

    public String getRoot() {
        return root;
    }

    public List<CodeFile> getFiles() {
        return files;
    }

    public List<Symbol> getSymbols() {
        return List.copyOf(symbols.values());
    }

    public Symbol getSymbol(String id) {
        Symbol symbol = symbols.get(id);
        if (symbol == null) {
            throw new IllegalArgumentException("Symbol not found: " + id);
        }
        return symbol;
    }

    public boolean containsSymbol(String id) {
        return symbols.containsKey(id);
    }

    public List<SymbolReference> getReferences() {
        return references;
    }

    public List<DataAccess> getDataAccess(String symbolId) {
        return dataAccess.getOrDefault(symbolId, List.of());
    }

    public List<DataAccess> getDataAccess() {
        return dataAccess.values().stream()
                .flatMap(List::stream)
                .toList();
    }

    /** Files that failed to parse and were left out of the snapshot. */
    public List<String> getSkippedFiles() {
        return skippedFiles;
    }

    @Override
    public String toString() {
        return "CodeRepository{" +
                "root=" + root +
                ", files=" + files.size() +
                ", symbols=" + symbols.size() +
                ", references=" + references.size() +
                '}';
    }
}
