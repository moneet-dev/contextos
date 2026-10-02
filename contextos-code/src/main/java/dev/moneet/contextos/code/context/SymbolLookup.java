package dev.moneet.contextos.code.context;

import dev.moneet.contextos.code.domain.CodeRepository;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;

import java.util.List;
import java.util.function.Predicate;

/**
 * Resolves a query to the symbols a context should start from. Tries, in order,
 * and returns the first non-empty match:
 * <ol>
 *   <li>symbol id: {@code com.acme.OrderService#place(Order)}</li>
 *   <li>display name: {@code OrderService#place(Order)}, {@code OrderService}</li>
 *   <li>member without parameters: {@code OrderService#place} (all overloads)</li>
 *   <li>simple name, excluding constructors: {@code place}</li>
 *   <li>file path suffix: {@code service/OrderService.java} (its top-level types)</li>
 *   <li>display name, then simple name, ignoring case</li>
 * </ol>
 */
public final class SymbolLookup {

    public List<Symbol> find(CodeRepository repository, String query) {
        List<Symbol> symbols = repository.getSymbols();
        String normalizedPath = query.replace('\\', '/');

        // Constructors share the type's simple name, so name-only tiers skip them
        Predicate<Symbol> notConstructor = s -> s.kind() != SymbolKind.CONSTRUCTOR;

        List<Predicate<Symbol>> tiers = List.of(
                s -> s.id().equals(query),
                s -> s.displayName().equals(query),
                s -> query.contains("#") && s.displayName().startsWith(query + "("),
                notConstructor.and(s -> s.name().equals(query)),
                s -> query.endsWith(".java") && s.parentId() == null
                        && (s.location().file().equals(normalizedPath)
                        || s.location().file().endsWith("/" + normalizedPath)),
                s -> s.displayName().equalsIgnoreCase(query),
                notConstructor.and(s -> s.name().equalsIgnoreCase(query))
        );

        for (Predicate<Symbol> tier : tiers) {
            List<Symbol> matches = symbols.stream().filter(tier).toList();
            if (!matches.isEmpty()) {
                return matches;
            }
        }
        return List.of();
    }
}
