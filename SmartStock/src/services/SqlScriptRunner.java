package services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class SqlScriptRunner {
    private SqlScriptRunner() {
    }

    public static int runScripts(Connection conn, List<String> relativePaths) throws IOException, SQLException {
        int executed = 0;
        Path root = findProjectRoot();
        for (String relativePath : relativePaths) {
            Path script = root.resolve(relativePath).normalize();
            try {
                if (Files.isRegularFile(script)) {
                    executed += runScript(conn, script);
                } else {
                    // Native installers do not run from a source checkout. Maven packages
                    // the database directory into the application JAR, so use that copy
                    // instead of silently omitting required schema scripts.
                    executed += runResource(conn, relativePath);
                }
            } catch (IOException | SQLException ex) {
                throw new SQLException("Schema setup failed in " + relativePath + ": "
                        + ex.getMessage(), ex);
            }
        }
        return executed;
    }

    public static int runScript(Connection conn, Path script) throws IOException, SQLException {
        String sql = Files.readString(script, StandardCharsets.UTF_8);
        return runSql(conn, sql);
    }

    public static int runResource(Connection conn, String resourcePath) throws IOException, SQLException {
        String cleanPath = resourcePath == null ? "" : resourcePath.replaceFirst("^/+", "");
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = SqlScriptRunner.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(cleanPath)) {
            if (input == null) {
                throw new IOException("Packaged SQL resource was not found: " + cleanPath);
            }
            return runSql(conn, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static String readResource(String resourcePath) throws IOException {
        String cleanPath = resourcePath == null ? "" : resourcePath.replaceFirst("^/+", "");
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) loader = SqlScriptRunner.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(cleanPath)) {
            if (input == null) {
                throw new IOException("Packaged SQL resource was not found: " + cleanPath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static int runSql(Connection conn, String sql) throws SQLException {
        int executed = 0;
        try (Statement stmt = conn.createStatement()) {
            for (String statement : splitStatements(sql)) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    stmt.execute(trimmed);
                } catch (SQLException ex) {
                    throw new SQLException("statement " + (executed + 1) + " failed: "
                            + ex.getMessage(), ex.getSQLState(), ex.getErrorCode(), ex);
                }
                executed++;
            }
        }
        return executed;
    }

    private static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path path = current; path != null; path = path.getParent()) {
            if (Files.isDirectory(path.resolve("database"))
                    && Files.isDirectory(path.resolve("src"))) {
                return path;
            }
            Path module = path.resolve("SmartStock");
            if (Files.isDirectory(module.resolve("database"))
                    && Files.isDirectory(module.resolve("src"))) {
                return module;
            }
        }
        return current;
    }

    static List<String> splitStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean lineComment = false;
        boolean blockComment = false;
        String dollarTag = null;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';

            if (lineComment) {
                current.append(c);
                if (c == '\n') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                current.append(c);
                if (c == '*' && next == '/') {
                    current.append(next);
                    i++;
                    blockComment = false;
                }
                continue;
            }
            if (!singleQuote && !doubleQuote && dollarTag == null && c == '-' && next == '-') {
                current.append(c).append(next);
                i++;
                lineComment = true;
                continue;
            }
            if (!singleQuote && !doubleQuote && dollarTag == null && c == '/' && next == '*') {
                current.append(c).append(next);
                i++;
                blockComment = true;
                continue;
            }
            if (!doubleQuote && dollarTag == null && c == '\'') {
                current.append(c);
                if (singleQuote && next == '\'') {
                    current.append(next);
                    i++;
                } else {
                    singleQuote = !singleQuote;
                }
                continue;
            }
            if (!singleQuote && dollarTag == null && c == '"') {
                current.append(c);
                doubleQuote = !doubleQuote;
                continue;
            }
            if (!singleQuote && !doubleQuote && c == '$') {
                String tag = readDollarTag(sql, i);
                if (tag != null) {
                    current.append(tag);
                    i += tag.length() - 1;
                    if (dollarTag == null) {
                        dollarTag = tag;
                    } else if (dollarTag.equals(tag)) {
                        dollarTag = null;
                    }
                    continue;
                }
            }
            if (!singleQuote && !doubleQuote && dollarTag == null && c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().trim().isEmpty()) {
            statements.add(current.toString());
        }
        return statements;
    }

    private static String readDollarTag(String sql, int start) {
        int end = sql.indexOf('$', start + 1);
        if (end < 0) {
            return null;
        }
        String tag = sql.substring(start, end + 1);
        String body = tag.substring(1, tag.length() - 1);
        if (!body.matches("[A-Za-z_][A-Za-z0-9_]*|")) {
            return null;
        }
        return tag;
    }
}
