package dev.moneet.contextos.code.source;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.List;
import java.util.Optional;

/**
 * Maps type usages to repository type ids. Uses the symbol solver first and
 * falls back to imports, the current package, enclosing types and unique simple
 * names, so types still resolve when third-party dependencies are not available.
 * Types declared outside the repository resolve to empty.
 */
final class TypeResolver {

    private final SymbolIndex index;

    TypeResolver(SymbolIndex index) {
        this.index = index;
    }

    Optional<String> resolve(ClassOrInterfaceType type) {
        try {
            ResolvedType resolved = type.resolve();
            if (resolved.isReferenceType()) {
                return index.typeId(resolved.asReferenceType().getQualifiedName());
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            return resolveName(type.getNameWithScope(), type);
        }
    }

    Optional<String> resolve(ResolvedType resolved) {
        if (resolved.isReferenceType()) {
            return index.typeId(resolved.asReferenceType().getQualifiedName());
        }
        return Optional.empty();
    }

    /** Resolves a type name as written at {@code context}, e.g. {@code Payment} or {@code Outer.Inner}. */
    Optional<String> resolveName(String name, Node context) {
        Optional<String> qualified = index.typeId(name);
        if (qualified.isPresent()) {
            return qualified;
        }

        String first = name.contains(".") ? name.substring(0, name.indexOf('.')) : name;
        String rest = name.substring(first.length());
        Optional<CompilationUnit> cu = context.findCompilationUnit();

        if (cu.isPresent()) {
            for (ImportDeclaration imp : cu.get().getImports()) {
                if (imp.isStatic()) {
                    continue;
                }
                if (!imp.isAsterisk() && imp.getNameAsString().endsWith("." + first)) {
                    // An explicit import decides the type, even when it is external.
                    return index.typeId(imp.getNameAsString() + rest);
                }
            }

            String pkg = cu.get().getPackageDeclaration()
                    .map(p -> p.getNameAsString() + ".")
                    .orElse("");
            Optional<String> samePackage = index.typeId(pkg + name);
            if (samePackage.isPresent()) {
                return samePackage;
            }

            for (ImportDeclaration imp : cu.get().getImports()) {
                if (imp.isAsterisk() && !imp.isStatic()) {
                    Optional<String> wildcard = index.typeId(imp.getNameAsString() + "." + name);
                    if (wildcard.isPresent()) {
                        return wildcard;
                    }
                }
            }
        }

        Optional<String> enclosing = index.enclosingType(context);
        while (enclosing.isPresent()) {
            Optional<String> nested = index.typeId(enclosing.get() + "." + name);
            if (nested.isPresent()) {
                return nested;
            }
            enclosing = index.get(enclosing.get()).flatMap(s -> s.parent());
        }

        List<String> candidates = index.typesNamed(name);
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }
}
