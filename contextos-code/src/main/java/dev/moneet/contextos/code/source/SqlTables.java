package dev.moneet.contextos.code.source;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight detection of SQL text and the tables it names. This is a hint
 * extractor, not a SQL parser: it reads the identifier after FROM, JOIN, INTO
 * and UPDATE.
 */
final class SqlTables {

    private static final Pattern SQL_START =
            Pattern.compile("(?is)^\\s*(select|insert|update|delete|merge|with)\\b.*");

    private static final Pattern TABLE_REFERENCE =
            Pattern.compile("(?i)\\b(?:from|join|into|update)\\s+([A-Za-z_][\\w$.]*)");

    private static final Set<String> KEYWORDS =
            Set.of("select", "set", "where", "values", "lateral", "only");

    private SqlTables() {
    }

    static boolean looksLikeSql(String text) {
        return SQL_START.matcher(text).matches() && !tables(text).isEmpty();
    }

    static Set<String> tables(String sql) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_REFERENCE.matcher(sql);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!KEYWORDS.contains(name.toLowerCase())) {
                tables.add(name);
            }
        }
        return tables;
    }
}
