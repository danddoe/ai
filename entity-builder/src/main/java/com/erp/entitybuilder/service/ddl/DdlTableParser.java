package com.erp.entitybuilder.service.ddl;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.ColumnDefinition;
import net.sf.jsqlparser.statement.create.table.CreateTable;
import net.sf.jsqlparser.statement.create.table.ForeignKeyIndex;
import net.sf.jsqlparser.statement.create.table.Index;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code CREATE TABLE} DDL via JSQLParser.
 * <p>
 * SQL Server-style input is normalized first (bracket identifiers, table-level {@code CHECK} constraints)
 * because JSQLParser otherwise yields {@code UnsupportedStatement}.
 */
public final class DdlTableParser {

    private static final Pattern TABLE_LEVEL_CHECK = Pattern.compile(
            "(?is)CONSTRAINT\\s+\\S+\\s+CHECK\\s*\\(");

    /** Table-level {@code CHECK} without a preceding {@code CONSTRAINT} name (T-SQL allows this). */
    private static final Pattern ANONYMOUS_TABLE_CHECK = Pattern.compile("(?is),\\s*CHECK\\s*\\(");

    private static final Pattern SQL_SERVER_GO_LINE = Pattern.compile("(?im)^\\s*GO\\s*;?\\s*$");

    private static final Pattern LEADING_CREATE_TABLE = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\b");

    private static final Pattern CREATE_TABLE_PREFIX = Pattern.compile("(?is)^\\s*CREATE\\s+TABLE\\s+");

    private DdlTableParser() {}

    public static List<ParsedDdlTable> parseCreateTables(String ddl) throws JSQLParserException {
        if (ddl == null || ddl.isBlank()) {
            throw new JSQLParserException("DDL is blank");
        }
        ddl = normalizeDdlForParser(ddl);
        List<String> createOnly = splitIntoIsolatedCreateTableStatements(ddl);
        if (createOnly.isEmpty()) {
            throw new JSQLParserException("No CREATE TABLE statements found (only non-CREATE DDL such as ALTER/GO is ignored)");
        }
        List<ParsedDdlTable> out = new ArrayList<>();
        for (String single : createOnly) {
            Statement st = CCJSqlParserUtil.parse(single);
            if (!(st instanceof CreateTable ct)) {
                throw new JSQLParserException("Only CREATE TABLE statements are supported; got: " + st.getClass().getSimpleName());
            }
            if (ct.getSelect() != null) {
                throw new JSQLParserException("CREATE TABLE AS SELECT is not supported");
            }
            if (ct.getLikeTable() != null) {
                throw new JSQLParserException("CREATE TABLE LIKE is not supported");
            }
            out.add(parseCreateTable(ct));
        }
        if (out.isEmpty()) {
            throw new JSQLParserException("No CREATE TABLE statements found");
        }
        return out;
    }

    /**
     * Strips T-SQL {@code [bracket]} identifiers, drops table-level {@code CONSTRAINT … CHECK (…)} (unsupported
     * by JSQLParser), and fixes a trailing comma before {@code );} left after removals.
     */
    static String normalizeDdlForParser(String ddl) {
        String s = normalizeConfusableSqlServerDelimiters(ddl);
        s = stripSqlServerBrackets(s);
        s = removeSqlServerGoBatchSeparators(s);
        s = removeTableLevelCheckConstraints(s);
        s = removeAnonymousTableCheckConstraints(s);
        s = s.replaceAll(",\\s*\\)\\s*;", ");");
        return s;
    }

    /**
     * T-SQL batch separator; not valid SQL for JSQLParser.
     */
    private static String removeSqlServerGoBatchSeparators(String sql) {
        return SQL_SERVER_GO_LINE.matcher(sql).replaceAll("");
    }

    /**
     * JSQLParser does not support T-SQL {@code ALTER TABLE ... ADD DEFAULT ... FOR col}. Scripts often mix
     * those with {@code CREATE TABLE}; keep only {@code CREATE TABLE} chunks for parsing.
     * <p>
     * Semicolon-splitting alone is not enough: if {@code CREATE TABLE ... )} is not followed by {@code ;}
     * before {@code ALTER TABLE}, the first "statement" segment still contains {@code ALTER} and the parser
     * fails (often on {@code [} if bracket stripping is bypassed elsewhere, or on {@code FOR}). Each segment
     * is therefore cut down to a single {@code CREATE TABLE (...)} using parenthesis matching on the column list.
     */
    static String filterToCreateTableStatementsOnly(String normalizedDdl) {
        List<String> isolated = splitIntoIsolatedCreateTableStatements(normalizedDdl);
        return String.join("\n", isolated);
    }

    /**
     * Fullwidth {@code ［ ］} (common when pasting from rich text) are normalized so bracket stripping works.
     */
    private static String normalizeConfusableSqlServerDelimiters(String ddl) {
        return ddl.replace('\uFF3B', '[').replace('\uFF3D', ']');
    }

    private static List<String> splitIntoIsolatedCreateTableStatements(String normalizedDdl) {
        List<String> parts = splitSqlStatementsSemicolonAware(normalizedDdl);
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String rest = p.trim();
            while (LEADING_CREATE_TABLE.matcher(rest).lookingAt()) {
                IsolatedCreate extracted = isolateLeadingCreateTable(rest);
                if (extracted == null) {
                    // e.g. CREATE TABLE t AS SELECT … has no "(columns)" — let JSQLParser classify it
                    out.add(ensureTrailingSemicolon(rest));
                    break;
                }
                out.add(extracted.statementText());
                rest = extracted.remainder().trim();
            }
        }
        return out;
    }

    private record IsolatedCreate(String statementText, String remainder) {}

    private static String ensureTrailingSemicolon(String sql) {
        String t = sql.trim();
        return t.endsWith(";") ? t : t + ";";
    }

    /**
     * Returns the first {@code CREATE TABLE name (...)} only (trailing semicolon added) and the rest of {@code chunk}.
     */
    private static IsolatedCreate isolateLeadingCreateTable(String chunk) {
        Matcher lead = CREATE_TABLE_PREFIX.matcher(chunk);
        if (!lead.lookingAt()) {
            return null;
        }
        int openParen = findColumnListOpeningParen(chunk, lead.end());
        if (openParen < 0) {
            return null;
        }
        int closeParen = indexOfMatchingCloseParen(chunk, openParen);
        if (closeParen < 0) {
            return null;
        }
        String body = chunk.substring(0, closeParen + 1).trim();
        String stmt = body.endsWith(";") ? body : body + ";";
        int pos = closeParen + 1;
        while (pos < chunk.length() && Character.isWhitespace(chunk.charAt(pos))) {
            pos++;
        }
        if (pos < chunk.length() && chunk.charAt(pos) == ';') {
            pos++;
        }
        return new IsolatedCreate(stmt, chunk.substring(pos));
    }

    /**
     * {@code startAfterCreateTableKeyword} is the index right after {@code CREATE TABLE} (whitespace allowed);
     * finds the {@code (} that opens the column / constraint list.
     */
    private static int findColumnListOpeningParen(String s, int startAfterCreateTableKeyword) {
        int i = startAfterCreateTableKeyword;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        while (i < s.length()) {
            char c = s.charAt(i);
            if (isSqlUnquotedIdentChar(c) || c == '.') {
                i++;
                continue;
            }
            break;
        }
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        if (i >= s.length() || s.charAt(i) != '(') {
            return -1;
        }
        return i;
    }

    private static boolean isSqlUnquotedIdentChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_'
                || c == '$';
    }

    /**
     * Splits on {@code ;} outside single-quoted string literals ({@code ''} escape).
     */
    private static List<String> splitSqlStatementsSemicolonAware(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inSingle = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inSingle) {
                cur.append(c);
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        cur.append(sql.charAt(i + 1));
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                cur.append(c);
                inSingle = true;
                continue;
            }
            if (c == ';') {
                String t = cur.toString().trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        String tail = cur.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    /**
     * Converts T-SQL {@code [id]} delimited identifiers to plain identifiers. Skips brackets inside
     * comments and string literals (so literals are not corrupted). Supports {@code ]]}
     * as an escaped {@code ]} inside the bracket name.
     */
    private static String stripSqlServerBrackets(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        while (i < input.length()) {
            char c = input.charAt(i);
            char n = i + 1 < input.length() ? input.charAt(i + 1) : '\0';

            if (inLineComment) {
                sb.append(c);
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                }
                i++;
                continue;
            }
            if (inBlockComment) {
                sb.append(c);
                if (c == '*' && n == '/') {
                    sb.append(n);
                    i += 2;
                    inBlockComment = false;
                    continue;
                }
                i++;
                continue;
            }
            if (inSingle) {
                sb.append(c);
                if (c == '\'') {
                    if (n == '\'') {
                        sb.append(n);
                        i += 2;
                        continue;
                    }
                    inSingle = false;
                }
                i++;
                continue;
            }
            if (inDouble) {
                sb.append(c);
                if (c == '"') {
                    if (n == '"') {
                        sb.append(n);
                        i += 2;
                        continue;
                    }
                    inDouble = false;
                }
                i++;
                continue;
            }

            if (c == '-' && n == '-') {
                sb.append(c).append(n);
                i += 2;
                inLineComment = true;
                continue;
            }
            if (c == '/' && n == '*') {
                sb.append(c).append(n);
                i += 2;
                inBlockComment = true;
                continue;
            }
            if (c == '\'') {
                sb.append(c);
                inSingle = true;
                i++;
                continue;
            }
            if (c == '"') {
                sb.append(c);
                inDouble = true;
                i++;
                continue;
            }

            if (c == '[') {
                StringBuilder id = new StringBuilder();
                int j = i + 1;
                boolean closed = false;
                while (j < input.length()) {
                    char ch = input.charAt(j);
                    if (ch == ']') {
                        if (j + 1 < input.length() && input.charAt(j + 1) == ']') {
                            id.append(']');
                            j += 2;
                            continue;
                        }
                        sb.append(id);
                        i = j + 1;
                        closed = true;
                        break;
                    }
                    id.append(ch);
                    j++;
                }
                if (closed) {
                    continue;
                }
                sb.append('[').append(id);
                i = j;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static String removeAnonymousTableCheckConstraints(String sql) {
        Matcher m = ANONYMOUS_TABLE_CHECK.matcher(sql);
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            int openParen = m.end() - 1;
            if (openParen < 0 || sql.charAt(openParen) != '(') {
                continue;
            }
            int close = indexOfMatchingCloseParen(sql, openParen);
            if (close < 0) {
                continue;
            }
            spans.add(new int[]{m.start(), close + 1});
        }
        if (spans.isEmpty()) {
            return sql;
        }
        StringBuilder sb = new StringBuilder(sql);
        for (int idx = spans.size() - 1; idx >= 0; idx--) {
            int[] sp = spans.get(idx);
            sb.delete(sp[0], sp[1]);
        }
        return sb.toString();
    }

    private static String removeTableLevelCheckConstraints(String sql) {
        Matcher m = TABLE_LEVEL_CHECK.matcher(sql);
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            int openParen = m.end() - 1;
            if (openParen < 0 || sql.charAt(openParen) != '(') {
                continue;
            }
            int close = indexOfMatchingCloseParen(sql, openParen);
            if (close < 0) {
                continue;
            }
            int start = m.start();
            int removeStart = start;
            int k = start - 1;
            while (k >= 0 && Character.isWhitespace(sql.charAt(k))) {
                k--;
            }
            if (k >= 0 && sql.charAt(k) == ',') {
                removeStart = k;
            }
            spans.add(new int[]{removeStart, close + 1});
        }
        if (spans.isEmpty()) {
            return sql;
        }
        StringBuilder sb = new StringBuilder(sql);
        for (int idx = spans.size() - 1; idx >= 0; idx--) {
            int[] sp = spans.get(idx);
            sb.delete(sp[0], sp[1]);
        }
        return sb.toString();
    }

    /**
     * {@code openIdx} points at {@code '('}; returns index of matching {@code ')'} with single-quoted
     * string awareness ({@code ''} escape).
     */
    private static int indexOfMatchingCloseParen(String s, int openIdx) {
        int depth = 0;
        boolean inSingle = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inSingle) {
                if (c == '\'') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '\'') {
                        i++;
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (c == '\'') {
                inSingle = true;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static ParsedDdlTable parseCreateTable(CreateTable ct) throws JSQLParserException {
        Table table = ct.getTable();
        if (table == null) {
            throw new JSQLParserException("CREATE TABLE missing table name");
        }
        String rawTable = tableFqn(table);
        String entitySlug = DdlSlugUtil.toSlug(rawTable, 100);

        Set<String> pkColumns = new HashSet<>();
        collectPrimaryKeyColumns(ct, pkColumns);

        Map<String, ParsedDdlForeignKey> fkByColumnRaw = new LinkedHashMap<>();
        collectTableForeignKeys(ct, fkByColumnRaw);

        List<ColumnDefinition> colDefs = ct.getColumnDefinitions();
        if (colDefs == null || colDefs.isEmpty()) {
            throw new JSQLParserException("CREATE TABLE has no columns: " + rawTable);
        }

        List<ParsedDdlColumn> columns = new ArrayList<>();
        for (Object o : colDefs) {
            if (!(o instanceof ColumnDefinition cd)) {
                continue;
            }
            String rawCol = DdlSlugUtil.stripQuotes(cd.getColumnName());
            String colKey = normalizeIdentKey(rawCol);
            boolean notNull = specsContainNotNull(cd.getColumnSpecs());
            if (!notNull && cd.getColDataType() != null) {
                String dt = cd.getColDataType().toString().toUpperCase(Locale.ROOT);
                if (dt.contains("NOT NULL")) {
                    notNull = true;
                }
            }
            if (!notNull) {
                String all = cd.toString().toUpperCase(Locale.ROOT);
                if (all.contains("NOT NULL")) {
                    notNull = true;
                }
            }
            boolean hasDef = hasDefaultClause(cd.getColumnSpecs())
                    || cd.toString().toUpperCase(Locale.ROOT).contains("DEFAULT");
            boolean colPk = specsContainPrimaryKey(cd.getColumnSpecs()) || pkColumns.contains(colKey);
            ParsedDdlForeignKey inlineFk = extractInlineForeignKey(cd);
            ParsedDdlForeignKey tableFk = fkByColumnRaw.remove(colKey);
            ParsedDdlForeignKey fk = inlineFk != null ? inlineFk : tableFk;

            String sqlType = "";
            if (cd.getColDataType() != null && cd.getColDataType().getDataType() != null) {
                sqlType = cd.getColDataType().getDataType();
            }

            String colSlug = DdlSlugUtil.toSlug(rawCol, 100);
            columns.add(new ParsedDdlColumn(rawCol, colSlug, sqlType, notNull, hasDef, colPk, fk));
        }

        if (!fkByColumnRaw.isEmpty()) {
            // Composite or unmatched table-level FK columns — skip silently for field typing
        }

        Set<String> seenSlugs = new HashSet<>();
        for (ParsedDdlColumn c : columns) {
            if (!seenSlugs.add(c.columnSlug())) {
                throw new JSQLParserException("Duplicate column slug after normalization: " + c.columnSlug());
            }
        }

        return new ParsedDdlTable(rawTable, entitySlug, List.copyOf(columns));
    }

    private static String tableFqn(Table table) {
        String schema = table.getSchemaName();
        String name = DdlSlugUtil.stripQuotes(table.getName());
        if (schema != null && !schema.isBlank()) {
            return DdlSlugUtil.stripQuotes(schema) + "_" + name;
        }
        return name;
    }

    private static String normalizeIdentKey(String raw) {
        return DdlSlugUtil.stripQuotes(raw).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static void collectPrimaryKeyColumns(CreateTable ct, Set<String> pkColumns) {
        List<?> indexes = ct.getIndexes();
        if (indexes == null) {
            return;
        }
        for (Object o : indexes) {
            if (!(o instanceof Index idx)) {
                continue;
            }
            String type = idx.getType();
            boolean pk = type != null && type.toUpperCase(Locale.ROOT).contains("PRIMARY");
            if (!pk && idx.toString().toUpperCase(Locale.ROOT).contains("PRIMARY KEY")) {
                pk = true;
            }
            if (!pk) {
                continue;
            }
            for (String c : idx.getColumnsNames()) {
                pkColumns.add(normalizeIdentKey(c));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectTableForeignKeys(CreateTable ct, Map<String, ParsedDdlForeignKey> fkByColumnRaw) throws JSQLParserException {
        List<?> indexes = ct.getIndexes();
        if (indexes == null) {
            return;
        }
        for (Object o : indexes) {
            if (!(o instanceof ForeignKeyIndex fk)) {
                continue;
            }
            List<String> localCols = fk.getColumnsNames();
            if (localCols == null || localCols.isEmpty()) {
                continue;
            }
            if (localCols.size() != 1) {
                continue;
            }
            Table ref = fk.getTable();
            if (ref == null) {
                continue;
            }
            String refTable = tableFqn(ref);
            String refCol = "id";
            List<?> refCols = fk.getReferencedColumnNames();
            if (refCols != null && !refCols.isEmpty()) {
                refCol = DdlSlugUtil.stripQuotes(String.valueOf(refCols.get(0)));
            }
            String localRaw = DdlSlugUtil.stripQuotes(localCols.get(0));
            fkByColumnRaw.put(normalizeIdentKey(localRaw), new ParsedDdlForeignKey(localRaw, refTable, refCol));
        }
    }

    @SuppressWarnings("unchecked")
    private static ParsedDdlForeignKey extractInlineForeignKey(ColumnDefinition cd) {
        List<?> specs = cd.getColumnSpecs();
        if (specs == null) {
            return null;
        }
        for (Object o : specs) {
            if (o instanceof ForeignKeyIndex fk) {
                Table ref = fk.getTable();
                if (ref == null) {
                    return null;
                }
                String refTable = tableFqn(ref);
                String refCol = "id";
                List<?> refCols = fk.getReferencedColumnNames();
                if (refCols != null && !refCols.isEmpty()) {
                    refCol = DdlSlugUtil.stripQuotes(String.valueOf(refCols.get(0)));
                }
                String localRaw = DdlSlugUtil.stripQuotes(cd.getColumnName());
                return new ParsedDdlForeignKey(localRaw, refTable, refCol);
            }
        }
        return null;
    }

    private static boolean specsContainNotNull(List<?> specs) {
        if (specs == null) {
            return false;
        }
        for (Object o : specs) {
            String s = o == null ? "" : o.toString();
            String u = s.toUpperCase(Locale.ROOT);
            if ("NOT NULL".equals(s.trim()) || u.contains("NOT NULL")) {
                return true;
            }
        }
        return false;
    }

    /** Inline {@code PRIMARY KEY} on a column is not always a single token in the parser output. */
    private static boolean specsContainPrimaryKey(List<?> specs) {
        if (specs == null) {
            return false;
        }
        for (Object o : specs) {
            if (o instanceof String s) {
                String u = s.trim().toUpperCase(Locale.ROOT);
                if ("PRIMARY KEY".equals(u) || u.contains("PRIMARY KEY")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasDefaultClause(List<?> specs) {
        if (specs == null) {
            return false;
        }
        for (Object o : specs) {
            if (o instanceof String s) {
                String t = s.trim();
                if (t.length() >= 7 && t.substring(0, 7).equalsIgnoreCase("DEFAULT")) {
                    return true;
                }
            }
        }
        return false;
    }
}
