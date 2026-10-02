package dev.moneet.contextos.code.source;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import dev.moneet.contextos.code.domain.ReferenceKind;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;
import dev.moneet.contextos.code.domain.SymbolReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts typed references between repository symbols.
 *
 * <p>Calls are resolved with the symbol solver. When that fails (typically because
 * a supertype lives in a library that is not on the classpath) the call is matched
 * by receiver type, method name and argument count; if no method matches, a
 * REFERENCES edge to the receiver type is recorded instead.
 */
final class ReferenceExtractor {

    private final SymbolIndex index;
    private final TypeResolver types;

    ReferenceExtractor(SymbolIndex index) {
        this.index = index;
        this.types = new TypeResolver(index);
    }

    List<SymbolReference> extract(CompilationUnit cu) {
        List<SymbolReference> references = new ArrayList<>();

        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            index.idOf(type).ifPresent(typeId -> {
                containment(typeId, references);
                supertypes(typeId, type, references);
            });
        }

        for (TypeDeclaration<?> type : cu.getTypes()) {
            index.idOf(type).ifPresent(typeId -> imports(typeId, cu, references));
        }

        for (VariableDeclarator variable : cu.findAll(VariableDeclarator.class)) {
            index.idOf(variable).ifPresent(fieldId ->
                    typeUsages(fieldId, variable.getType(), references));
        }

        for (CallableDeclaration<?> callable : cu.findAll(CallableDeclaration.class)) {
            index.idOf(callable).ifPresent(id -> {
                if (callable instanceof MethodDeclaration method) {
                    typeUsages(id, method.getType(), references);
                }
                callable.getParameters().forEach(p -> typeUsages(id, p.getType(), references));
            });
        }

        cu.findAll(MethodCallExpr.class).forEach(call -> call(call, references));
        cu.findAll(ObjectCreationExpr.class).forEach(creation -> creation(creation, references));
        cu.findAll(NameExpr.class).forEach(name -> fieldAccess(name, references));
        cu.findAll(FieldAccessExpr.class).forEach(access -> fieldAccess(access, references));

        return references;
    }

    /** OVERRIDES edges from methods to same-name, same-arity methods of direct repository supertypes. */
    List<SymbolReference> overrides(List<SymbolReference> references) {
        List<SymbolReference> overrides = new ArrayList<>();

        for (SymbolReference reference : references) {
            if (reference.kind() != ReferenceKind.EXTENDS && reference.kind() != ReferenceKind.IMPLEMENTS) {
                continue;
            }
            for (String memberId : index.members(reference.sourceId())) {
                Symbol member = index.get(memberId).orElseThrow();
                if (member.kind() != SymbolKind.METHOD) {
                    continue;
                }
                for (String overridden : index.methods(reference.targetId(), member.name(),
                        index.parameterCount(memberId))) {
                    overrides.add(new SymbolReference(memberId, overridden,
                            ReferenceKind.OVERRIDES, member.location().startLine()));
                }
            }
        }
        return overrides;
    }

    private void containment(String typeId, List<SymbolReference> references) {
        for (String memberId : index.members(typeId)) {
            int line = index.get(memberId).orElseThrow().location().startLine();
            references.add(new SymbolReference(typeId, memberId, ReferenceKind.CONTAINS, line));
        }
    }

    private void supertypes(String typeId, TypeDeclaration<?> type, List<SymbolReference> references) {
        List<ClassOrInterfaceType> extended = List.of();
        List<ClassOrInterfaceType> implemented = List.of();

        if (type instanceof ClassOrInterfaceDeclaration c) {
            extended = c.getExtendedTypes();
            implemented = c.getImplementedTypes();
        } else if (type instanceof EnumDeclaration e) {
            implemented = e.getImplementedTypes();
        } else if (type instanceof RecordDeclaration r) {
            implemented = r.getImplementedTypes();
        }

        for (ClassOrInterfaceType supertype : extended) {
            supertype(typeId, supertype, ReferenceKind.EXTENDS, references);
        }
        for (ClassOrInterfaceType supertype : implemented) {
            supertype(typeId, supertype, ReferenceKind.IMPLEMENTS, references);
        }
    }

    private void supertype(String typeId,
                           ClassOrInterfaceType supertype,
                           ReferenceKind kind,
                           List<SymbolReference> references) {

        types.resolve(supertype).ifPresent(target ->
                references.add(new SymbolReference(typeId, target, kind, line(supertype))));

        // Type arguments, e.g. Payment in JpaRepository<Payment, Long>
        supertype.getTypeArguments().ifPresent(arguments ->
                arguments.forEach(argument -> typeUsages(typeId, argument, references)));
    }

    private void imports(String typeId, CompilationUnit cu, List<SymbolReference> references) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (!imp.isStatic() && !imp.isAsterisk()) {
                index.typeId(imp.getNameAsString()).ifPresent(target ->
                        references.add(new SymbolReference(typeId, target, ReferenceKind.IMPORTS, line(imp))));
            }
        }
    }

    /** REFERENCES edges for every repository type mentioned in {@code type}, including type arguments. */
    private void typeUsages(String sourceId, Type type, List<SymbolReference> references) {
        for (ClassOrInterfaceType usage : type.findAll(ClassOrInterfaceType.class)) {
            types.resolve(usage).ifPresent(target ->
                    references.add(new SymbolReference(sourceId, target, ReferenceKind.REFERENCES, line(usage))));
        }
    }

    private void call(MethodCallExpr call, List<SymbolReference> references) {
        Optional<String> owner = index.enclosingSymbol(call);
        if (owner.isEmpty()) {
            return;
        }

        try {
            ResolvedMethodLikeDeclaration method = call.resolve();
            resolvedMethod(method).ifPresent(target ->
                    references.add(new SymbolReference(owner.get(), target, ReferenceKind.CALLS, line(call))));
            return;
        } catch (RuntimeException e) {
            // fall through to name-based matching
        }

        Optional<String> receiverType = call.getScope().isPresent()
                ? scopeType(call.getScope().get())
                : index.enclosingType(call);
        if (receiverType.isEmpty()) {
            return;
        }

        List<String> candidates = index.methods(receiverType.get(),
                call.getNameAsString(), call.getArguments().size());
        if (!candidates.isEmpty()) {
            references.add(new SymbolReference(owner.get(), candidates.get(0), ReferenceKind.CALLS, line(call)));
        } else if (call.getScope().isPresent()) {
            references.add(new SymbolReference(owner.get(), receiverType.get(), ReferenceKind.REFERENCES, line(call)));
        }
    }

    private void creation(ObjectCreationExpr creation, List<SymbolReference> references) {
        Optional<String> owner = index.enclosingSymbol(creation);
        if (owner.isEmpty()) {
            return;
        }

        try {
            Optional<String> constructor = resolvedMethod(creation.resolve());
            if (constructor.isPresent()) {
                references.add(new SymbolReference(owner.get(), constructor.get(), ReferenceKind.CALLS, line(creation)));
                return;
            }
        } catch (RuntimeException e) {
            // fall through to the type reference
        }

        types.resolve(creation.getType()).ifPresent(type ->
                references.add(new SymbolReference(owner.get(), type, ReferenceKind.REFERENCES, line(creation))));
    }

    private void fieldAccess(Expression expression, List<SymbolReference> references) {
        Optional<String> owner = index.enclosingSymbol(expression);
        if (owner.isEmpty()) {
            return;
        }

        try {
            ResolvedValueDeclaration value = expression instanceof NameExpr name
                    ? name.resolve()
                    : ((FieldAccessExpr) expression).resolve();
            if (value.isField()) {
                String id = value.asField().declaringType().getQualifiedName() + "#" + value.getName();
                if (index.get(id).isPresent()) {
                    references.add(new SymbolReference(owner.get(), id, ReferenceKind.REFERENCES, line(expression)));
                }
            } else if (value.isEnumConstant()) {
                types.resolve(value.getType()).ifPresent(type ->
                        references.add(new SymbolReference(owner.get(), type, ReferenceKind.REFERENCES, line(expression))));
            }
        } catch (RuntimeException e) {
            // locals, parameters, type names and unresolvable expressions are not field references
        }
    }

    /** Repository id of a resolved method or constructor; empty for library methods. */
    private Optional<String> resolvedMethod(ResolvedMethodLikeDeclaration method) {
        Optional<? extends Node> ast = method.toAst();
        if (ast.isPresent()) {
            Optional<String> id = index.idAt(ast.get());
            if (id.isPresent()) {
                return id;
            }
        }
        return index.typeId(method.declaringType().getQualifiedName())
                .flatMap(type -> index.methods(type, method.getName(), method.getNumberOfParams())
                        .stream().findFirst());
    }

    private Optional<String> scopeType(Expression scope) {
        try {
            return types.resolve(scope.calculateResolvedType());
        } catch (RuntimeException e) {
            // fall through
        }

        if (scope instanceof NameExpr name) {
            // A field of the enclosing type, by declared type
            Optional<String> enclosing = index.enclosingType(scope);
            if (enclosing.isPresent()) {
                Optional<Node> field = index.nodeOf(enclosing.get() + "#" + name.getNameAsString());
                if (field.isPresent() && field.get() instanceof VariableDeclarator variable
                        && variable.getType() instanceof ClassOrInterfaceType fieldType) {
                    return types.resolve(fieldType);
                }
            }
            // A static call on a type, e.g. Payment.pending(...)
            return types.resolveName(name.getNameAsString(), scope);
        }
        return Optional.empty();
    }

    private static int line(Node node) {
        return node.getBegin().map(p -> p.line).orElse(0);
    }
}
