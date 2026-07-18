package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import managers.SupabaseSessionManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class CompanyBackupService {
    private static final String BACKUP_HEADER = "-- SmartStock company backup";
    private static final String DATA_SQL_ENTRY = "data.sql";
    private static final String ASSET_MANIFEST_ENTRY = "assets.tsv";
    private static final String ASSET_PREFIX = "assets/";
    private static final long MAX_PACKAGE_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_ZIP_ENTRIES = 10_000;
    private static final long MAX_SQL_BYTES = 250L * 1024L * 1024L;
    private static final long MAX_MANIFEST_BYTES = 10L * 1024L * 1024L;
    private static final long MAX_ASSET_BYTES = 100L * 1024L * 1024L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(15))
            .build();

    private CompanyBackupService() {
    }

    public static BackupSummary exportBackup(Path backupFile) throws SQLException, IOException {
        requirePhysicalServer();
        try (Connection conn = DB.getConnection()) {
            List<TableInfo> tables = loadBackupTables(conn);
            Map<String, StorageAsset> assets = new LinkedHashMap<>();
            StringBuilder sql = new StringBuilder();
            sql.append(BACKUP_HEADER).append('\n');
            sql.append("-- Created: ").append(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
            sql.append("-- Restore from Company Preferences > Restore Backup.\n\n");
            sql.append("BEGIN;\n");
            sql.append("SET LOCAL lock_timeout = '10s';\n");
            sql.append("SET LOCAL statement_timeout = '5min';\n\n");

            if (!tables.isEmpty()) {
                sql.append("TRUNCATE TABLE ");
                for (int i = 0; i < tables.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append(tables.get(i).qualifiedName());
                }
                sql.append(" RESTART IDENTITY CASCADE;\n\n");
            }

            int rowCount = 0;
            for (TableInfo table : tables) {
                int tableRows = appendTableData(conn, table, sql, assets);
                rowCount += tableRows;
                if (tableRows > 0) {
                    sql.append('\n');
                }
            }

            for (TableInfo table : tables) {
                appendSequenceResets(table, sql);
            }

            sql.append("\nCOMMIT;\n");
            Files.createDirectories(backupFile.toAbsolutePath().getParent());
            AssetWriteSummary assetSummary = writeBackupPackage(backupFile, sql.toString(), assets.values());
            return new BackupSummary(tables.size(), rowCount, assetSummary.saved(), assetSummary.skipped());
        }
    }

    public static BackupSummary restoreBackup(Path backupFile) throws SQLException, IOException {
        requirePhysicalServer();
        if (isPackageBackup(backupFile)) {
            return restorePackageBackup(backupFile);
        }
        String sql = Files.readString(backupFile, StandardCharsets.UTF_8);
        if (!sql.startsWith(BACKUP_HEADER)) {
            throw new IOException("This does not look like a SmartStock company backup file.");
        }
        try (Connection conn = DB.getConnection()) {
            int statements = SqlScriptRunner.runScript(conn, backupFile);
            return new BackupSummary(statements, 0, 0, 0);
        }
    }

    private static void requirePhysicalServer() {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            throw new SecurityException("Company backup and restore are available only on the physical SmartStock server.");
        }
    }

    private static List<TableInfo> loadBackupTables(Connection conn) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        Map<String, TableInfo> tables = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String name = rs.getString("TABLE_NAME");
                if (!isBackupTable(name)) {
                    continue;
                }
                TableInfo table = new TableInfo(schema, name);
                tables.put(table.key(), table);
            }
        }
        for (TableInfo table : tables.values()) {
            loadColumns(metaData, table);
            loadForeignKeys(metaData, table, tables);
        }
        return dependencyOrder(tables);
    }

    private static boolean isBackupTable(String tableName) {
        String lower = tableName == null ? "" : tableName.toLowerCase(Locale.ROOT);
        return !lower.startsWith("sync_")
                && !lower.equals("app_releases")
                && !lower.equals("spatial_ref_sys")
                && !lower.equals("schema_migrations");
    }

    private static void loadColumns(DatabaseMetaData metaData, TableInfo table) throws SQLException {
        Set<String> alwaysIdentityColumns = alwaysIdentityColumns(metaData.getConnection(), table);
        try (ResultSet rs = metaData.getColumns(null, table.schema(), table.name(), "%")) {
            while (rs.next()) {
                table.columns().add(new ColumnInfo(
                        rs.getString("COLUMN_NAME"),
                        rs.getInt("DATA_TYPE"),
                        rs.getString("TYPE_NAME"),
                        rs.getString("COLUMN_DEF"),
                        alwaysIdentityColumns.contains(rs.getString("COLUMN_NAME"))
                ));
            }
        }
    }

    private static Set<String> alwaysIdentityColumns(Connection conn, TableInfo table) throws SQLException {
        Set<String> columns = new HashSet<>();
        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND identity_generation = 'ALWAYS'
                """;
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, table.schema());
            ps.setString(2, table.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }

    private static void loadForeignKeys(DatabaseMetaData metaData, TableInfo table, Map<String, TableInfo> allTables) throws SQLException {
        try (ResultSet rs = metaData.getImportedKeys(null, table.schema(), table.name())) {
            while (rs.next()) {
                String parentSchema = rs.getString("PKTABLE_SCHEM");
                String parentName = rs.getString("PKTABLE_NAME");
                String key = TableInfo.key(parentSchema, parentName);
                if (allTables.containsKey(key) && !key.equals(table.key())) {
                    table.dependencies().add(key);
                }
            }
        }
    }

    private static List<TableInfo> dependencyOrder(Map<String, TableInfo> tables) {
        List<TableInfo> ordered = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (TableInfo table : tables.values()) {
            visit(table, tables, visited, visiting, ordered);
        }
        return ordered;
    }

    private static void visit(TableInfo table,
                              Map<String, TableInfo> tables,
                              Set<String> visited,
                              Set<String> visiting,
                              List<TableInfo> ordered) {
        if (visited.contains(table.key())) {
            return;
        }
        if (!visiting.add(table.key())) {
            return;
        }
        for (String dependencyKey : table.dependencies()) {
            TableInfo dependency = tables.get(dependencyKey);
            if (dependency != null) {
                visit(dependency, tables, visited, visiting, ordered);
            }
        }
        visiting.remove(table.key());
        visited.add(table.key());
        ordered.add(table);
    }

    private static int appendTableData(Connection conn,
                                       TableInfo table,
                                       StringBuilder sql,
                                       Map<String, StorageAsset> assets) throws SQLException {
        if (table.columns().isEmpty()) {
            return 0;
        }
        String query = "SELECT * FROM " + table.qualifiedName();
        int rows = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            ResultSetMetaData metaData = rs.getMetaData();
            while (rs.next()) {
                rows++;
                sql.append("INSERT INTO ").append(table.qualifiedName()).append(" (");
                for (int i = 0; i < table.columns().size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append(quoteIdentifier(table.columns().get(i).name()));
                }
                sql.append(")");
                if (table.hasAlwaysIdentityColumn()) {
                    sql.append(" OVERRIDING SYSTEM VALUE");
                }
                sql.append(" VALUES (");
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    if (i > 1) {
                        sql.append(", ");
                    }
                    ColumnInfo column = table.columns().get(i - 1);
                    Object value = rs.getObject(i);
                    collectAsset(value, table, column, assets);
                    sql.append(sqlLiteral(value, rs, i, column));
                }
                sql.append(");\n");
            }
        }
        return rows;
    }

    private static void collectAsset(Object value, TableInfo table, ColumnInfo column, Map<String, StorageAsset> assets) {
        if (!(value instanceof String stringValue)) {
            return;
        }
        if (!looksLikeAssetColumn(column.name())) {
            return;
        }
        StorageAsset asset = StorageAsset.fromUrl(stringValue.trim(), table.name(), column.name());
        if (asset != null) {
            assets.putIfAbsent(asset.url(), asset);
        }
    }

    private static boolean looksLikeAssetColumn(String columnName) {
        String lower = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT);
        return lower.contains("image")
                || lower.contains("logo")
                || lower.contains("photo")
                || lower.contains("document")
                || lower.contains("file")
                || lower.endsWith("_url");
    }

    private static String sqlLiteral(Object value, ResultSet rs, int columnIndex, ColumnInfo column) throws SQLException {
        if (value == null) {
            return "NULL";
        }
        if (column.dataType() == Types.BINARY || column.dataType() == Types.VARBINARY || column.dataType() == Types.LONGVARBINARY) {
            return "'\\\\x" + bytesToHex(rs.getBytes(columnIndex)) + "'";
        }
        if (value instanceof Number number && !(number instanceof BigDecimal)) {
            return String.valueOf(number);
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "TRUE" : "FALSE";
        }
        String typeName = column.typeName() == null ? "" : column.typeName().toLowerCase(Locale.ROOT);
        String literal = quoteSql(String.valueOf(value));
        if (typeName.equals("json") || typeName.equals("jsonb")) {
            return literal + "::" + typeName;
        }
        return literal;
    }

    private static void appendSequenceResets(TableInfo table, StringBuilder sql) {
        for (ColumnInfo column : table.columns()) {
            String defaultValue = column.defaultValue() == null ? "" : column.defaultValue().toLowerCase(Locale.ROOT);
            if (!defaultValue.contains("nextval(")) {
                continue;
            }
            sql.append("SELECT setval(pg_get_serial_sequence(")
                    .append(quoteSql(table.schema() + "." + table.name()))
                    .append(", ")
                    .append(quoteSql(column.name()))
                    .append("), COALESCE(MAX(")
                    .append(quoteIdentifier(column.name()))
                    .append("), 1), MAX(")
                    .append(quoteIdentifier(column.name()))
                    .append(") IS NOT NULL) FROM ")
                    .append(table.qualifiedName())
                    .append(";\n");
        }
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String quoteSql(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static AssetWriteSummary writeBackupPackage(Path backupFile, String sql, Iterable<StorageAsset> assets) throws IOException {
        int saved = 0;
        int skipped = 0;
        StringBuilder manifest = new StringBuilder();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(backupFile))) {
            zip.putNextEntry(new ZipEntry(DATA_SQL_ENTRY));
            zip.write(sql.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            int index = 1;
            for (StorageAsset asset : assets) {
                try {
                    DownloadedAsset downloaded = downloadAsset(asset);
                    String entryName = ASSET_PREFIX + String.format(Locale.ROOT, "%05d-", index) + sanitizeEntryName(asset.filename());
                    zip.putNextEntry(new ZipEntry(entryName));
                    zip.write(downloaded.bytes());
                    zip.closeEntry();
                    manifest.append(encodeField(entryName)).append('\t')
                            .append(encodeField(asset.url())).append('\t')
                            .append(encodeField(asset.bucket())).append('\t')
                            .append(encodeField(asset.objectPath())).append('\t')
                            .append(encodeField(downloaded.contentType())).append('\t')
                            .append(sha256(downloaded.bytes())).append('\n');
                    saved++;
                    index++;
                } catch (Exception ex) {
                    skipped++;
                }
            }

            zip.putNextEntry(new ZipEntry(ASSET_MANIFEST_ENTRY));
            zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new AssetWriteSummary(saved, skipped);
    }

    private static BackupSummary restorePackageBackup(Path backupFile) throws IOException, SQLException {
        Path tempDir = Files.createTempDirectory("smartstock-backup-restore-");
        Path sqlPath = tempDir.resolve(DATA_SQL_ENTRY);
        List<PackagedAsset> assets = new ArrayList<>();
        try {
            Map<String, byte[]> entryData = readBackupEntries(backupFile);
            byte[] sqlBytes = entryData.get(DATA_SQL_ENTRY);
            if (sqlBytes == null) {
                throw new IOException("Backup package is missing data.sql.");
            }
            if (sqlBytes.length > MAX_SQL_BYTES) {
                throw new IOException("Backup package data.sql is too large.");
            }
            String sql = new String(sqlBytes, StandardCharsets.UTF_8);
            if (!sql.startsWith(BACKUP_HEADER)) {
                throw new IOException("Backup package data.sql is not a SmartStock company backup.");
            }
            Files.write(sqlPath, sqlBytes);
            byte[] manifestBytes = entryData.get(ASSET_MANIFEST_ENTRY);
            if (manifestBytes != null) {
                assets.addAll(readAssetManifest(new String(manifestBytes, StandardCharsets.UTF_8), entryData));
            }
            int statements;
            try (Connection conn = DB.getConnection()) {
                statements = SqlScriptRunner.runScript(conn, sqlPath);
            }
            int restoredAssets = 0;
            int skippedAssets = 0;
            for (PackagedAsset asset : assets) {
                try {
                    uploadAsset(asset);
                    restoredAssets++;
                } catch (Exception ex) {
                    skippedAssets++;
                }
            }
            return new BackupSummary(statements, 0, restoredAssets, skippedAssets);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static List<PackagedAsset> readAssetManifest(String manifest, Map<String, byte[]> entryData) {
        List<PackagedAsset> assets = new ArrayList<>();
        for (String line : manifest.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            if (fields.length < 5) {
                continue;
            }
            String entryName = decodeField(fields[0]);
            if (!isSafeBackupEntryName(entryName) || !entryName.startsWith(ASSET_PREFIX)) {
                continue;
            }
            byte[] bytes = entryData.get(entryName);
            if (bytes == null) {
                continue;
            }
            if (fields.length >= 6 && !sha256(bytes).equalsIgnoreCase(fields[5])) {
                continue;
            }
            assets.add(new PackagedAsset(
                    decodeField(fields[2]),
                    decodeField(fields[3]),
                    decodeField(fields[4]),
                    bytes
            ));
        }
        return assets;
    }

    private static Map<String, byte[]> readBackupEntries(Path backupFile) throws IOException {
        if (Files.size(backupFile) > MAX_PACKAGE_BYTES) {
            throw new IOException("Backup package is too large.");
        }
        Map<String, byte[]> entryData = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(backupFile))) {
            ZipEntry entry;
            int entryCount = 0;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    throw new IOException("Backup package has too many files.");
                }
                String name = entry.getName();
                if (!isSafeBackupEntryName(name)) {
                    throw new IOException("Backup package contains an unsafe file path.");
                }
                if (entryData.containsKey(name)) {
                    throw new IOException("Backup package contains duplicate file entries.");
                }
                long maxBytes = maxBytesForEntry(name);
                entryData.put(name, readEntryBytes(zip, maxBytes));
            }
        }
        return entryData;
    }

    private static byte[] readEntryBytes(ZipInputStream zip, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("Backup package contains a file that is too large.");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static long maxBytesForEntry(String name) {
        if (DATA_SQL_ENTRY.equals(name)) {
            return MAX_SQL_BYTES;
        }
        if (ASSET_MANIFEST_ENTRY.equals(name)) {
            return MAX_MANIFEST_BYTES;
        }
        return MAX_ASSET_BYTES;
    }

    private static boolean isSafeBackupEntryName(String name) {
        return name != null
                && !name.isBlank()
                && !name.startsWith("/")
                && !name.startsWith("\\")
                && !name.contains("..")
                && !name.contains("\\")
                && (DATA_SQL_ENTRY.equals(name)
                || ASSET_MANIFEST_ENTRY.equals(name)
                || name.startsWith(ASSET_PREFIX));
    }

    private static DownloadedAsset downloadAsset(StorageAsset asset) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(asset.url()))
                .timeout(java.time.Duration.ofSeconds(60))
                .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                .GET();
        if (asset.authenticated()) {
            builder.header("Authorization", "Bearer " + SupabaseSessionManager.getValidAccessToken());
        }
        HttpResponse<byte[]> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new IOException("Storage download failed with HTTP " + response.statusCode());
        }
        String contentType = response.headers().firstValue("content-type").orElse("application/octet-stream");
        return new DownloadedAsset(response.body(), contentType);
    }

    private static void uploadAsset(PackagedAsset asset) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                        + "/storage/v1/object/"
                        + encodePathSegment(asset.bucket())
                        + "/"
                        + encodeObjectPath(asset.objectPath())))
                .timeout(java.time.Duration.ofSeconds(60))
                .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                .header("Authorization", "Bearer " + SupabaseSessionManager.getValidAccessToken())
                .header("Content-Type", asset.contentType().isBlank() ? "application/octet-stream" : asset.contentType())
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(asset.bytes()))
                .build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Storage upload failed with HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private static boolean isPackageBackup(Path backupFile) throws IOException {
        if (!Files.isRegularFile(backupFile)) {
            return false;
        }
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(backupFile))) {
            return zip.getNextEntry() != null;
        }
    }

    private static String encodeField(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static String encodeObjectPath(String objectPath) {
        String[] parts = objectPath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String part : parts) {
            if (!encoded.isEmpty()) {
                encoded.append("/");
            }
            encoded.append(encodePathSegment(part));
        }
        return encoded.toString();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decodePath(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String sanitizeEntryName(String filename) {
        String sanitized = filename == null ? "asset" : filename.trim();
        sanitized = sanitized.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        return sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized) ? "asset" : sanitized;
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var children = Files.list(path)) {
                    children.forEach(CompanyBackupService::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            // Temporary restore cleanup should not mask the restore result.
        }
    }

    public record BackupSummary(int tableCount, int rowCount, int assetCount, int skippedAssetCount) {
    }

    private record ColumnInfo(String name, int dataType, String typeName, String defaultValue, boolean alwaysIdentity) {
    }

    private record AssetWriteSummary(int saved, int skipped) {
    }

    private record DownloadedAsset(byte[] bytes, String contentType) {
    }

    private record PackagedAsset(String bucket, String objectPath, String contentType, byte[] bytes) {
    }

    private record StorageAsset(String url,
                                String bucket,
                                String objectPath,
                                boolean authenticated,
                                String tableName,
                                String columnName) {
        private static StorageAsset fromUrl(String url, String tableName, String columnName) {
            if (url == null || url.isBlank()) {
                return null;
            }
            String base = SupabaseSessionManager.getSupabaseUrl() + "/storage/v1/object/";
            if (!url.startsWith(base)) {
                return null;
            }
            String remainder = url.substring(base.length());
            boolean authenticated;
            if (remainder.startsWith("public/")) {
                authenticated = false;
                remainder = remainder.substring("public/".length());
            } else if (remainder.startsWith("authenticated/")) {
                authenticated = true;
                remainder = remainder.substring("authenticated/".length());
            } else {
                return null;
            }
            int slash = remainder.indexOf('/');
            if (slash <= 0 || slash >= remainder.length() - 1) {
                return null;
            }
            String bucket = decodePath(remainder.substring(0, slash));
            String objectPath = decodePath(remainder.substring(slash + 1));
            return new StorageAsset(url, bucket, objectPath, authenticated, tableName, columnName);
        }

        private String filename() {
            int slash = objectPath.lastIndexOf('/');
            return slash >= 0 ? objectPath.substring(slash + 1) : objectPath;
        }
    }

    private record TableInfo(String schema,
                             String name,
                             List<ColumnInfo> columns,
                             Set<String> dependencies) {
        private TableInfo(String schema, String name) {
            this(schema, name, new ArrayList<>(), new LinkedHashSet<>());
        }

        private String key() {
            return key(schema, name);
        }

        private String qualifiedName() {
            return quoteIdentifier(schema) + "." + quoteIdentifier(name);
        }

        private boolean hasAlwaysIdentityColumn() {
            for (ColumnInfo column : columns) {
                if (column.alwaysIdentity()) {
                    return true;
                }
            }
            return false;
        }

        private static String key(String schema, String name) {
            return (schema == null ? "" : schema) + "." + (name == null ? "" : name);
        }
    }
}
