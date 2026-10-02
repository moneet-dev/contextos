package dev.moneet.contextos.code.source;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.moneet.contextos.code.domain.SourceLocation;
import dev.moneet.contextos.code.domain.Symbol;
import dev.moneet.contextos.code.domain.SymbolKind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Registers every type, method, constructor and field declared in a compilation unit.
 * Local and anonymous classes are not symbols; code inside them is attributed to
 * the enclosing member.
 */
final class SymbolExtractor {

    void extract(CompilationUnit cu, String file, List<String> lines, SymbolIndex index) {
        for (TypeDeclaration<?> type : cu.getTypes()) {
            extractType(type, null, null, file, lines, index);
        }
    }

    private void extractType(TypeDeclaration<?> type,
                             String parentId,
                             String parentDisplay,
                             String file,
                             List<String> lines,
                             SymbolIndex index) {

        String name = type.getNameAsString();
        String id = type.getFullyQualifiedName().orElse(name);
        String display = parentDisplay == null ? name : parentDisplay + "." + name;
        String header = typeHeader(type);

        index.add(new Symbol(id, kindOf(type), name, display, parentId,
                location(type, file), annotations(type.getAnnotations()), header, header), type, 0);

        for (BodyDeclaration<?> member : type.getMembers()) {

            if (member instanceof TypeDeclaration<?> nested) {
                extractType(nested, id, display, file, lines, index);

            } else if (member instanceof CallableDeclaration<?> callable) {
                String memberName = callable.getNameAsString();
                String parameters = callable.getParameters().stream()
                        .map(SymbolExtractor::parameterType)
                        .collect(Collectors.joining(","));
                String suffix = "#" + memberName + "(" + parameters + ")";
                SymbolKind kind = callable.isConstructorDeclaration()
                        ? SymbolKind.CONSTRUCTOR : SymbolKind.METHOD;
                // Modifiers as written: getDeclarationAsString(true, ...) adds an implicit
                // "abstract" to interface methods
                String signature = withAnnotations(callable.getAnnotations(),
                        modifiers(callable.getModifiers()) + callable.getDeclarationAsString(false, true, true));

                index.add(new Symbol(id + suffix, kind, memberName, display + suffix, id,
                        location(callable, file), annotations(callable.getAnnotations()),
                        signature, text(lines, callable)), callable, callable.getParameters().size());

            } else if (member instanceof FieldDeclaration field) {
                for (VariableDeclarator variable : field.getVariables()) {
                    String suffix = "#" + variable.getNameAsString();
                    String signature = withAnnotations(field.getAnnotations(),
                            modifiers(field.getModifiers()) + variable.getTypeAsString() + " " + variable);

                    index.add(new Symbol(id + suffix, SymbolKind.FIELD, variable.getNameAsString(),
                            display + suffix, id, location(field, file),
                            annotations(field.getAnnotations()), signature, text(lines, field)),
                            variable, 0);
                }
            }
        }
    }

    private static SymbolKind kindOf(TypeDeclaration<?> type) {
        if (type instanceof ClassOrInterfaceDeclaration c) {
            return c.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS;
        }
        if (type instanceof EnumDeclaration) {
            return SymbolKind.ENUM;
        }
        if (type instanceof RecordDeclaration) {
            return SymbolKind.RECORD;
        }
        if (type instanceof AnnotationDeclaration) {
            return SymbolKind.ANNOTATION;
        }
        return SymbolKind.CLASS;
    }

    /** Annotations, modifiers, name, type parameters, record components and supertypes. */
    private static String typeHeader(TypeDeclaration<?> type) {
        StringBuilder sb = new StringBuilder(modifiers(type.getModifiers()));
        List<ClassOrInterfaceType> extended = List.of();
        List<ClassOrInterfaceType> implemented = List.of();

        if (type instanceof ClassOrInterfaceDeclaration c) {
            sb.append(c.isInterface() ? "interface " : "class ").append(c.getNameAsString());
            if (c.getTypeParameters().isNonEmpty()) {
                sb.append(join("<", c.getTypeParameters(), ">"));
            }
            extended = c.getExtendedTypes();
            implemented = c.getImplementedTypes();
        } else if (type instanceof RecordDeclaration r) {
            sb.append("record ").append(r.getNameAsString());
            if (r.getTypeParameters().isNonEmpty()) {
                sb.append(join("<", r.getTypeParameters(), ">"));
            }
            sb.append(join("(", r.getParameters(), ")"));
            implemented = r.getImplementedTypes();
        } else if (type instanceof EnumDeclaration e) {
            sb.append("enum ").append(e.getNameAsString());
            implemented = e.getImplementedTypes();
        } else {
            sb.append("@interface ").append(type.getNameAsString());
        }

        if (!extended.isEmpty()) {
            sb.append(" extends ").append(join("", extended, ""));
        }
        if (!implemented.isEmpty()) {
            sb.append(" implements ").append(join("", implemented, ""));
        }
        return withAnnotations(type.getAnnotations(), sb.toString());
    }

    private static String parameterType(Parameter parameter) {
        return parameter.getType().asString() + (parameter.isVarArgs() ? "..." : "");
    }

    private static String modifiers(NodeList<Modifier> modifiers) {
        return modifiers.stream()
                .map(m -> m.getKeyword().asString() + " ")
                .collect(Collectors.joining());
    }

    private static String join(String prefix, List<? extends Node> nodes, String suffix) {
        return nodes.stream()
                .map(Node::toString)
                .collect(Collectors.joining(", ", prefix, suffix));
    }

    private static String withAnnotations(NodeList<AnnotationExpr> annotations, String declaration) {
        StringBuilder sb = new StringBuilder();
        for (AnnotationExpr annotation : annotations) {
            sb.append(annotation).append("\n");
        }
        return sb.append(declaration).toString();
    }

    private static List<String> annotations(NodeList<AnnotationExpr> annotations) {
        List<String> names = new ArrayList<>();
        for (AnnotationExpr annotation : annotations) {
            names.add(annotation.getNameAsString());
        }
        return names;
    }

    private static SourceLocation location(Node node, String file) {
        int begin = node.getBegin().map(p -> p.line).orElse(0);
        int end = node.getEnd().map(p -> p.line).orElse(begin);
        return new SourceLocation(file, begin, end);
    }

    /** Original source text of {@code node}, as written in the file. */
    private static String text(List<String> lines, Node node) {
        if (node.getBegin().isEmpty() || node.getEnd().isEmpty()) {
            return node.toString();
        }
        int begin = node.getBegin().get().line;
        int end = Math.min(node.getEnd().get().line, lines.size());
        return String.join("\n", lines.subList(begin - 1, end));
    }
}
