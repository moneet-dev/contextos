package dev.moneet.contextos.code.source;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import dev.moneet.contextos.code.domain.DataAccess;
import dev.moneet.contextos.code.domain.DataAccessKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Collects hints that code touches database tables: JPA entity mappings,
 * {@code @Query} annotations and SQL string literals.
 */
final class DataAccessExtractor {

    private final SymbolIndex index;

    DataAccessExtractor(SymbolIndex index) {
        this.index = index;
    }

    List<DataAccess> extract(CompilationUnit cu) {
        List<DataAccess> result = new ArrayList<>();

        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            index.idOf(type).ifPresent(id -> entity(id, type, result));
        }

        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            index.idOf(method).ifPresent(id -> query(id, method, result));
        }

        sqlLiterals(cu, result);
        return result;
    }

    private void entity(String typeId, TypeDeclaration<?> type, List<DataAccess> result) {
        Optional<AnnotationExpr> table = annotation(type.getAnnotations(), "Table");
        Optional<AnnotationExpr> entity = annotation(type.getAnnotations(), "Entity");

        Optional<String> tableName = table.flatMap(a -> attribute(a, "name")).map(DataAccessExtractor::text);
        if (tableName.isPresent()) {
            result.add(new DataAccess(typeId, DataAccessKind.ENTITY_TABLE, tableName.get(), line(table.get())));
        } else if (entity.isPresent()) {
            String name = attribute(entity.get(), "name")
                    .map(DataAccessExtractor::text)
                    .orElse(type.getNameAsString());
            result.add(new DataAccess(typeId, DataAccessKind.ENTITY_NAME, name, line(entity.get())));
        }
    }

    private void query(String methodId, MethodDeclaration method, List<DataAccess> result) {
        Optional<AnnotationExpr> query = annotation(method.getAnnotations(), "Query");
        if (query.isEmpty()) {
            return;
        }

        Optional<String> sql = attribute(query.get(), "value").map(DataAccessExtractor::text);
        if (sql.isEmpty()) {
            return;
        }

        boolean nativeQuery = attribute(query.get(), "nativeQuery")
                .filter(BooleanLiteralExpr.class::isInstance)
                .map(e -> ((BooleanLiteralExpr) e).getValue())
                .orElse(false);
        DataAccessKind kind = nativeQuery ? DataAccessKind.NATIVE_QUERY : DataAccessKind.JPQL_QUERY;

        for (String table : SqlTables.tables(sql.get())) {
            result.add(new DataAccess(methodId, kind, table, line(query.get())));
        }
    }

    private void sqlLiterals(CompilationUnit cu, List<DataAccess> result) {
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        for (LiteralStringValueExpr literal : cu.findAll(LiteralStringValueExpr.class)) {
            if (!isString(literal) || literal.findAncestor(AnnotationExpr.class).isPresent()) {
                continue;
            }

            // SQL split across "..." + "..." is read as one string
            Expression whole = literal;
            while (whole.getParentNode().orElse(null) instanceof BinaryExpr parent
                    && parent.getOperator() == BinaryExpr.Operator.PLUS) {
                whole = parent;
            }
            if (!seen.add(whole)) {
                continue;
            }

            String sql = text(whole);
            if (!SqlTables.looksLikeSql(sql)) {
                continue;
            }

            Optional<String> owner = index.enclosingSymbol(whole);
            if (owner.isPresent()) {
                for (String table : SqlTables.tables(sql)) {
                    result.add(new DataAccess(owner.get(), DataAccessKind.SQL_LITERAL, table, line(whole)));
                }
            }
        }
    }

    private static Optional<AnnotationExpr> annotation(List<AnnotationExpr> annotations, String simpleName) {
        return annotations.stream()
                .filter(a -> a.getNameAsString().equals(simpleName)
                        || a.getNameAsString().endsWith("." + simpleName))
                .findFirst();
    }

    /** Value of an annotation attribute; "value" also matches the single-member form. */
    private static Optional<Expression> attribute(AnnotationExpr annotation, String name) {
        if (annotation instanceof SingleMemberAnnotationExpr single && name.equals("value")) {
            return Optional.of(single.getMemberValue());
        }
        if (annotation instanceof NormalAnnotationExpr normal) {
            return normal.getPairs().stream()
                    .filter(pair -> pair.getNameAsString().equals(name))
                    .map(MemberValuePair::getValue)
                    .findFirst();
        }
        return Optional.empty();
    }

    /** Concatenated value of all string literals in {@code expression}. */
    private static String text(Expression expression) {
        StringBuilder sb = new StringBuilder();
        for (LiteralStringValueExpr literal : expression.findAll(LiteralStringValueExpr.class)) {
            if (literal instanceof StringLiteralExpr string) {
                sb.append(string.asString());
            } else if (literal instanceof TextBlockLiteralExpr block) {
                sb.append(block.asString());
            }
        }
        return sb.toString();
    }

    private static boolean isString(LiteralStringValueExpr literal) {
        return literal instanceof StringLiteralExpr || literal instanceof TextBlockLiteralExpr;
    }

    private static int line(Node node) {
        return node.getBegin().map(p -> p.line).orElse(0);
    }
}
