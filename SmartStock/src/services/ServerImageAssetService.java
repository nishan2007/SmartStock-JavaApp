package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import data.DB;
import data.EnvironmentProfile;
import managers.SupabaseSessionManager;
import utils.SecureFilePermissions;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned image manifest and local mirror. Database rows contain metadata
 * only; image bytes live in the server mirror and Supabase Storage.
 */
public final class ServerImageAssetService {
    private static final Path LEGACY_IMAGE_ROOT =
            Path.of(System.getProperty("user.home"), ".smartstock", "image-store");
    private static final String INVENTORY_SCAN_STATE = "storage-inventory-last-completed-at";
    private static final long DEFAULT_INVENTORY_SCAN_HOURS = 24L;
    private static final Object IMAGE_ROOT_LOCK = new Object();
    private static volatile Path preparedImageRoot;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final List<ReferenceSource> SOURCES = List.of(
            new ReferenceSource("products", "product_id", "image_url", "PRODUCT"),
            new ReferenceSource("custom_order_items", "custom_item_id", "image_url", "CUSTOM_ITEM"),
            new ReferenceSource("custom_order_item_variants", "custom_variant_id", "image_url", "CUSTOM_VARIANT"),
            new ReferenceSource("users", "user_id", "employee_photo_url", "EMPLOYEE_PHOTO"),
            new ReferenceSource("company_info", "company_info_id", "company_logo_url", "COMPANY_LOGO"),
            new ReferenceSource("company_customization", "location_id", "badge_template_logo_url", "BADGE_LOGO")
    );

    private ServerImageAssetService() { }

    public static void ensureSchema(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
    }

    public static String storeUpload(Connection conn, String category, String bucket, String objectPath,
                                     String contentType, String originalFilename, String accessLevel,
                                     byte[] bytes) throws Exception {
        ensureSchema(conn);
        validateLocation(bucket, objectPath);
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("The image is empty.");
        Path target = mirrorPath(bucket, objectPath);
        writeAtomically(target, bytes);
        String hash = sha256(bytes);
        UUID id;
        String sql = """
                INSERT INTO image_assets(category,bucket_name,object_path,access_level,original_filename,
                    content_type,byte_size,sha256,local_status,cloud_status,last_verified_at,last_error)
                VALUES(?,?,?,?,?,?,?,?, 'PRESENT','PENDING',CURRENT_TIMESTAMP,NULL)
                ON CONFLICT(bucket_name,object_path) DO UPDATE SET
                    category=EXCLUDED.category,access_level=EXCLUDED.access_level,
                    original_filename=EXCLUDED.original_filename,content_type=EXCLUDED.content_type,
                    byte_size=EXCLUDED.byte_size,sha256=EXCLUDED.sha256,local_status='PRESENT',
                    cloud_status='PENDING',lifecycle_status='ACTIVE',deleted_at=NULL,
                    last_verified_at=CURRENT_TIMESTAMP,last_error=NULL
                RETURNING asset_id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, clean(category, 80, "IMAGE"));
            ps.setString(2, bucket);
            ps.setString(3, objectPath);
            ps.setString(4, "AUTHENTICATED".equalsIgnoreCase(accessLevel) ? "AUTHENTICATED" : "PUBLIC");
            ps.setString(5, clean(originalFilename, 500, filename(objectPath)));
            ps.setString(6, clean(contentType, 200, "application/octet-stream"));
            ps.setLong(7, bytes.length);
            ps.setString(8, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Image manifest row was not created.");
                id = rs.getObject(1, UUID.class);
            }
        }
        return ImageAssetReference.format(id);
    }

    public static AssetBytes load(String reference) throws Exception {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            AssetRow row = find(conn, ImageAssetReference.assetId(reference));
            if (row == null || "DELETED".equals(row.lifecycleStatus())) {
                throw new IOException("The requested image is unavailable.");
            }
            Path path = mirrorPath(row.bucket(), row.objectPath());
            if (Files.isRegularFile(path)) {
                byte[] bytes = Files.readAllBytes(path);
                if (row.sha256().isBlank() || row.sha256().equals(sha256(bytes))) {
                    recordLocalBytes(conn, row.id(), bytes);
                    return new AssetBytes(bytes, row.contentType(), sha256(bytes));
                }
                touchLocal(conn, row.id(), "CORRUPT", "Local image checksum did not match the manifest.");
            }
            byte[] downloaded = restoreFromLegacyCache(row);
            if (downloaded == null) downloaded = download(row);
            if (downloaded == null || downloaded.length == 0) throw new IOException("The image is missing locally and in cloud storage.");
            if (!row.sha256().isBlank() && !row.sha256().equals(sha256(downloaded))) {
                touchLocal(conn, row.id(), "CORRUPT", "Cloud image checksum did not match the manifest.");
                throw new IOException("The cloud image checksum did not match the manifest.");
            }
            writeAtomically(path, downloaded);
            recordLocalBytes(conn, row.id(), downloaded);
            return new AssetBytes(downloaded, row.contentType(), sha256(downloaded));
        }
    }

    public static boolean isEmployeePhoto(Connection conn, String reference) throws SQLException {
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category='EMPLOYEE_PHOTO' FROM image_assets WHERE asset_id=?")) {
            ps.setObject(1, ImageAssetReference.assetId(reference));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    public static SyncResult synchronize(Connection local) throws SQLException {
        return synchronize(local, false);
    }

    public static SyncResult synchronize(Connection local, boolean forceInventoryScan) throws SQLException {
        ensureSchema(local);
        try {
            imageRoot();
        } catch (IOException ex) {
            throw new SQLException("The local image mirror could not be prepared.", ex);
        }
        int discovered = reconcileReferences(local);
        if (ServerSupabaseCredentials.isConfigured()
                && (forceInventoryScan || shouldDiscoverCloudObjects(local))) {
            if (discoverCloudObjects(local)) recordInventoryScan(local);
        }
        int uploaded = 0;
        int repaired = 0;
        try (PreparedStatement ps = local.prepareStatement("""
                SELECT asset_id FROM image_assets
                WHERE lifecycle_status <> 'DELETED'
                  AND (cloud_status IN ('PENDING','FAILED','MISSING') OR local_status <> 'PRESENT'
                       OR sha256='' OR byte_size=0)
                ORDER BY created_at
                """);
             ResultSet rs = ps.executeQuery()) {
            List<UUID> ids = new ArrayList<>();
            while (rs.next()) ids.add(rs.getObject(1, UUID.class));
            for (UUID id : ids) {
                AssetRow row = find(local, id);
                if (row == null) continue;
                try {
                    Path path = mirrorPath(row.bucket(), row.objectPath());
                    if (!Files.isRegularFile(path)) {
                        byte[] bytes = restoreFromLegacyCache(row);
                        if (bytes == null) bytes = download(row);
                        if (bytes != null && bytes.length > 0
                                && (row.sha256().isBlank() || row.sha256().equals(sha256(bytes)))) {
                            writeAtomically(path, bytes);
                            recordLocalBytes(local, id, bytes);
                            repaired++;
                        }
                    }
                    if (Files.isRegularFile(path)) {
                        recordLocalBytes(local, id, Files.readAllBytes(path));
                    }
                    if (Files.isRegularFile(path) && !"PRESENT".equals(row.cloudStatus())) {
                        upload(row, Files.readAllBytes(path));
                        try (PreparedStatement update = local.prepareStatement("""
                                UPDATE image_assets SET cloud_status='PRESENT',last_verified_at=CURRENT_TIMESTAMP,
                                    last_error=NULL WHERE asset_id=?
                                """)) {
                            update.setObject(1, id);
                            update.executeUpdate();
                        }
                        uploaded++;
                    }
                } catch (Exception ex) {
                    try (PreparedStatement update = local.prepareStatement("""
                            UPDATE image_assets SET cloud_status=CASE WHEN cloud_status='PRESENT' THEN cloud_status ELSE 'FAILED' END,
                                last_error=? WHERE asset_id=?
                            """)) {
                        update.setString(1, clean(ex.getMessage(), 2000, "Image synchronization failed."));
                        update.setObject(2, id);
                        update.executeUpdate();
                    }
                }
            }
        }
        return new SyncResult(discovered, uploaded, repaired);
    }

    private static boolean discoverCloudObjects(Connection conn) {
        for (CloudPrefix source : List.of(
                new CloudPrefix("Product Images", "products/", "PRODUCT", "PUBLIC"),
                new CloudPrefix("Product Images", "company/", "COMPANY_LOGO", "PUBLIC"),
                new CloudPrefix("employee files", "employee photos/", "EMPLOYEE_PHOTO", "AUTHENTICATED"))) {
            int offset = 0;
            while (true) {
                try {
                    String body = "{\"prefix\":\"" + source.prefix().replace("\\", "\\\\").replace("\"", "\\\"")
                            + "\",\"limit\":1000,\"offset\":" + offset + ",\"sortBy\":{\"column\":\"name\",\"order\":\"asc\"}}";
                    HttpRequest.Builder request = HttpRequest.newBuilder()
                                    .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                                            + "/storage/v1/object/list/" + encode(source.bucket())))
                                    .timeout(Duration.ofSeconds(60))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                    HttpResponse<String> response = HTTP.send(
                            ServerSupabaseCredentials.applyTo(request).build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    CloudTransferMetrics.record(conn, "storage_inventory_list", body, response.body());
                    requireSuccess(response.statusCode(), response.body(), "list");
                    JsonArray rows = JsonParser.parseString(response.body()).getAsJsonArray();
                    for (var element : rows) {
                        JsonObject object = element.getAsJsonObject();
                        if (!object.has("name") || object.get("name").isJsonNull()) continue;
                        String name = object.get("name").getAsString();
                        if (name.isBlank() || object.has("id") && object.get("id").isJsonNull()) continue;
                        registerCloudObject(conn, source, source.prefix() + name, object);
                    }
                    if (rows.size() < 1000) break;
                    offset += rows.size();
                } catch (Exception ex) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean shouldDiscoverCloudObjects(Connection conn) throws SQLException {
        try (PreparedStatement count = conn.prepareStatement("SELECT COUNT(*) FROM image_assets");
             ResultSet rows = count.executeQuery()) {
            if (rows.next() && rows.getLong(1) == 0) return true;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT state_value FROM image_sync_state WHERE state_key=?")) {
            ps.setString(1, INVENTORY_SCAN_STATE);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return true;
                try {
                    long hours = Long.getLong(
                            "smartstock.image.inventory.hours", DEFAULT_INVENTORY_SCAN_HOURS);
                    return Instant.parse(rs.getString(1))
                            .isBefore(Instant.now().minus(Duration.ofHours(Math.max(1L, hours))));
                } catch (Exception ex) {
                    return true;
                }
            }
        }
    }

    private static void recordInventoryScan(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO image_sync_state(state_key,state_value,updated_at)
                VALUES(?,?,CURRENT_TIMESTAMP)
                ON CONFLICT(state_key) DO UPDATE SET
                    state_value=EXCLUDED.state_value,updated_at=CURRENT_TIMESTAMP
                """)) {
            ps.setString(1, INVENTORY_SCAN_STATE);
            ps.setString(2, Instant.now().toString());
            ps.executeUpdate();
        }
    }

    private static void registerCloudObject(Connection conn, CloudPrefix source, String objectPath,
                                            JsonObject object) throws SQLException {
        long size = 0;
        String contentType = "application/octet-stream";
        if (object.has("metadata") && object.get("metadata").isJsonObject()) {
            JsonObject metadata = object.getAsJsonObject("metadata");
            if (metadata.has("size") && !metadata.get("size").isJsonNull()) size = metadata.get("size").getAsLong();
            if (metadata.has("mimetype") && !metadata.get("mimetype").isJsonNull()) {
                contentType = metadata.get("mimetype").getAsString();
            }
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO image_assets(category,bucket_name,object_path,access_level,original_filename,
                    content_type,byte_size,local_status,cloud_status,lifecycle_status,unused_since)
                VALUES(?,?,?,?,?,?,?,'MISSING','PRESENT','UNUSED',CURRENT_TIMESTAMP)
                ON CONFLICT(bucket_name,object_path) DO UPDATE SET cloud_status='PRESENT',
                    content_type=CASE WHEN image_assets.content_type='application/octet-stream'
                        THEN EXCLUDED.content_type ELSE image_assets.content_type END,
                    byte_size=GREATEST(image_assets.byte_size,EXCLUDED.byte_size)
                WHERE image_assets.cloud_status IS DISTINCT FROM 'PRESENT'
                   OR (image_assets.content_type='application/octet-stream'
                       AND image_assets.content_type IS DISTINCT FROM EXCLUDED.content_type)
                   OR image_assets.byte_size < EXCLUDED.byte_size
                """)) {
            ps.setString(1, source.category());
            ps.setString(2, source.bucket());
            ps.setString(3, objectPath);
            ps.setString(4, source.accessLevel());
            ps.setString(5, filename(objectPath));
            ps.setString(6, contentType);
            ps.setLong(7, size);
            ps.executeUpdate();
        }
    }

    public static int reconcileReferences(Connection conn) throws SQLException {
        ensureSchema(conn);
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TEMP TABLE IF NOT EXISTS smartstock_image_refs_seen (
                        source_table TEXT NOT NULL,
                        source_key TEXT NOT NULL,
                        source_column TEXT NOT NULL,
                        PRIMARY KEY(source_table,source_key,source_column)
                    )
                    """);
            stmt.executeUpdate("TRUNCATE smartstock_image_refs_seen");
        }
        int active = 0;
        for (ReferenceSource source : SOURCES) {
            if (!hasColumns(conn, source.table(), source.key(), source.column())) continue;
            String sql = "SELECT " + quote(source.key()) + "::text, " + quote(source.column())
                    + " FROM " + quote(source.table()) + " WHERE NULLIF(TRIM(" + quote(source.column()) + "),'') IS NOT NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String value = rs.getString(2);
                    UUID id = ImageAssetReference.isAssetReference(value)
                            ? ImageAssetReference.assetId(value) : registerLegacy(conn, source.category(), value);
                    if (id == null) continue;
                    upsertReference(conn, id, source.table(), rs.getString(1), source.column());
                    markReferenceSeen(conn, source.table(), rs.getString(1), source.column());
                    active++;
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    UPDATE image_asset_references r SET active=FALSE
                    WHERE r.active=TRUE AND NOT EXISTS (
                        SELECT 1 FROM smartstock_image_refs_seen s
                        WHERE s.source_table=r.source_table
                          AND s.source_key=r.source_key
                          AND s.source_column=r.source_column
                    )
                    """);
            stmt.executeUpdate("""
                    UPDATE image_assets a SET lifecycle_status='UNUSED',
                        unused_since=COALESCE(a.unused_since,CURRENT_TIMESTAMP)
                    WHERE a.lifecycle_status NOT IN ('DELETED','DELETE_PENDING') AND NOT a.retained
                      AND (a.lifecycle_status <> 'UNUSED' OR a.unused_since IS NULL)
                      AND NOT EXISTS (SELECT 1 FROM image_asset_references r WHERE r.asset_id=a.asset_id AND r.active)
                    """);
            stmt.executeUpdate("""
                    UPDATE image_assets a SET lifecycle_status='ACTIVE',unused_since=NULL
                    WHERE a.lifecycle_status='UNUSED'
                      AND EXISTS (SELECT 1 FROM image_asset_references r WHERE r.asset_id=a.asset_id AND r.active)
                    """);
        }
        return active;
    }

    private static UUID registerLegacy(Connection conn, String category, String value) throws SQLException {
        LegacyLocation location = parseLegacyUrl(value);
        if (location == null) return null;
        String sql = """
                INSERT INTO image_assets(category,bucket_name,object_path,access_level,original_filename,
                    local_status,cloud_status,last_error)
                VALUES(?,?,?,?,?,'MISSING','PRESENT',NULL)
                ON CONFLICT(bucket_name,object_path) DO NOTHING
                RETURNING asset_id
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            ps.setString(2, location.bucket());
            ps.setString(3, location.path());
            ps.setString(4, location.authenticated() ? "AUTHENTICATED" : "PUBLIC");
            ps.setString(5, filename(location.path()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getObject(1, UUID.class);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT asset_id FROM image_assets WHERE bucket_name=? AND object_path=?")) {
            ps.setString(1, location.bucket());
            ps.setString(2, location.path());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, UUID.class) : null;
            }
        }
    }

    private static void upsertReference(Connection conn, UUID id, String table, String key, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO image_asset_references(asset_id,source_table,source_key,source_column,active)
                VALUES(?,?,?,?,TRUE)
                ON CONFLICT(source_table,source_key,source_column) DO UPDATE SET
                    asset_id=EXCLUDED.asset_id,active=TRUE
                WHERE image_asset_references.asset_id IS DISTINCT FROM EXCLUDED.asset_id
                   OR image_asset_references.active IS DISTINCT FROM TRUE
                """)) {
            ps.setObject(1, id);
            ps.setString(2, table);
            ps.setString(3, key);
            ps.setString(4, column);
            ps.executeUpdate();
        }
    }

    private static void markReferenceSeen(Connection conn, String table, String key, String column)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO smartstock_image_refs_seen(source_table,source_key,source_column)
                VALUES(?,?,?) ON CONFLICT DO NOTHING
                """)) {
            ps.setString(1, table);
            ps.setString(2, key);
            ps.setString(3, column);
            ps.executeUpdate();
        }
    }

    public static List<AssetView> list(Connection conn) throws SQLException {
        ensureSchema(conn);
        List<AssetView> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT a.asset_id,a.category,a.original_filename,a.byte_size,a.lifecycle_status,
                       a.local_status,a.cloud_status,a.created_at,a.updated_at,a.unused_since,
                       a.last_error,COUNT(r.reference_id) FILTER (WHERE r.active) AS reference_count
                FROM image_assets a LEFT JOIN image_asset_references r ON r.asset_id=a.asset_id
                GROUP BY a.asset_id ORDER BY (a.lifecycle_status='UNUSED') DESC,a.updated_at DESC
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new AssetView(
                    rs.getObject(1, UUID.class).toString(), rs.getString(2), rs.getString(3), rs.getLong(4),
                    rs.getString(5), rs.getString(6), rs.getString(7),
                    epoch(rs.getTimestamp(8)), epoch(rs.getTimestamp(9)), epoch(rs.getTimestamp(10)),
                    rs.getString(11), rs.getInt(12)));
        }
        return rows;
    }

    public static void retain(Connection conn, UUID id) throws SQLException {
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE image_assets SET retained=TRUE,lifecycle_status='ACTIVE',unused_since=NULL,last_error=NULL
                WHERE asset_id=? AND lifecycle_status <> 'DELETED'
                """)) {
            ps.setObject(1, id);
            if (ps.executeUpdate() != 1) throw new SQLException("Image was not found.");
        }
    }

    public static void purge(Connection conn, UUID id, int actorId, String actorName) throws Exception {
        ensureSchema(conn);
        reconcileReferences(conn);
        AssetRow row = find(conn, id);
        if (row == null) throw new SQLException("Image was not found.");
        if (!"UNUSED".equals(row.lifecycleStatus()) && !"DELETE_PENDING".equals(row.lifecycleStatus())) {
            throw new SQLException("Only unused images can be permanently deleted.");
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM image_asset_references WHERE asset_id=? AND active LIMIT 1")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) throw new SQLException("The image is referenced and cannot be deleted.");
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE image_assets SET lifecycle_status='DELETE_PENDING',last_error=NULL WHERE asset_id=?")) {
            ps.setObject(1, id);
            ps.executeUpdate();
        }
        try {
            deleteCloud(row);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE image_assets SET cloud_status='DELETED',last_error=NULL WHERE asset_id=?")) {
                ps.setObject(1, id);
                ps.executeUpdate();
            }
        } catch (Exception ex) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE image_assets SET cloud_status='FAILED',last_error=? WHERE asset_id=?")) {
                ps.setString(1, clean(ex.getMessage(), 2000, "Cloud deletion failed."));
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            throw ex;
        }
        try {
            Files.deleteIfExists(mirrorPath(row.bucket(), row.objectPath()));
        } catch (Exception ex) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE image_assets SET lifecycle_status='DELETE_PENDING',last_error=? WHERE asset_id=?")) {
                ps.setString(1, clean(ex.getMessage(), 2000, "Local image deletion failed."));
                ps.setObject(2, id);
                ps.executeUpdate();
            }
            throw ex;
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE image_assets SET lifecycle_status='DELETED',local_status='MISSING',cloud_status='DELETED',
                    deleted_at=CURRENT_TIMESTAMP,deleted_by_user_id=?,deleted_by_name=?,last_error=NULL WHERE asset_id=?
                """)) {
            ps.setInt(1, actorId);
            ps.setString(2, actorName);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    public static Counts counts(Connection conn) throws SQLException {
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT COUNT(*) FILTER (
                           WHERE local_status='PRESENT'
                             AND cloud_status IN ('PENDING','FAILED','MISSING')
                             AND lifecycle_status NOT IN ('DELETE_PENDING','DELETED')
                       ),
                       COUNT(*) FILTER (WHERE local_status<>'PRESENT' AND lifecycle_status<>'DELETED'),
                       COUNT(*) FILTER (WHERE cloud_status IN ('MISSING','FAILED')),
                       COUNT(*) FILTER (WHERE lifecycle_status='UNUSED'),
                       COUNT(*) FILTER (WHERE lifecycle_status='DELETE_PENDING' OR (lifecycle_status='UNUSED' AND cloud_status='FAILED'))
                FROM image_assets
                """);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new Counts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                    ServerSupabaseCredentials.isConfigured());
        }
    }

    private static AssetRow find(Connection conn, UUID id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT asset_id,bucket_name,object_path,access_level,content_type,sha256,
                       lifecycle_status,local_status,cloud_status FROM image_assets WHERE asset_id=?
                """)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new AssetRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getString(9)) : null;
            }
        }
    }

    private static void touchLocal(Connection conn, UUID id, String status, String error) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE image_assets SET local_status=?,last_verified_at=CURRENT_TIMESTAMP,last_error=? WHERE asset_id=?
                """)) {
            ps.setString(1, status);
            ps.setString(2, error);
            ps.setObject(3, id);
            ps.executeUpdate();
        }
    }

    private static void recordLocalBytes(Connection conn, UUID id, byte[] bytes) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE image_assets SET local_status='PRESENT',byte_size=?,sha256=?,
                    last_verified_at=CURRENT_TIMESTAMP,last_error=NULL
                WHERE asset_id=? AND (
                    local_status IS DISTINCT FROM 'PRESENT'
                    OR byte_size IS DISTINCT FROM ?
                    OR sha256 IS DISTINCT FROM ?
                    OR last_error IS NOT NULL
                    OR last_verified_at IS NULL
                    OR last_verified_at < CURRENT_TIMESTAMP - INTERVAL '24 hours'
                )
                """)) {
            ps.setLong(1, bytes.length);
            String hash = sha256(bytes);
            ps.setString(2, hash);
            ps.setObject(3, id);
            ps.setLong(4, bytes.length);
            ps.setString(5, hash);
            ps.executeUpdate();
        }
    }

    private static byte[] restoreFromLegacyCache(AssetRow row) {
        try {
            String route = "PUBLIC".equals(row.accessLevel())
                    ? "/storage/v1/object/public/" : "/storage/v1/object/authenticated/";
            String url = storageUri(route, row.bucket(), row.objectPath()).toString();
            String extension = extension(row.objectPath());
            String cachedName = sha256(url.getBytes(StandardCharsets.UTF_8)) + "." + extension;
            Path profileCache = EnvironmentProfile.active().directory()
                    .resolve("image-cache").resolve(cachedName);
            if (Files.isRegularFile(profileCache)) return Files.readAllBytes(profileCache);
            Path legacyCache = Path.of(System.getProperty("user.home"), ".smartstock",
                    "image-cache", cachedName);
            return Files.isRegularFile(legacyCache) ? Files.readAllBytes(legacyCache) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void upload(AssetRow row, byte[] bytes) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                        .uri(storageUri("/storage/v1/object/", row.bucket(), row.objectPath()))
                        .timeout(Duration.ofSeconds(60))
                        .header("Content-Type", row.contentType()).header("x-upsert", "true")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes));
        HttpResponse<String> response = HTTP.send(
                ServerSupabaseCredentials.applyTo(request).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        requireSuccess(response.statusCode(), response.body(), "upload");
    }

    private static byte[] download(AssetRow row) throws Exception {
        boolean publicAsset = "PUBLIC".equals(row.accessLevel());
        String credential = publicAsset
                ? SupabaseSessionManager.getSupabasePublishableKey()
                : ServerSupabaseCredentials.require();
        String route = publicAsset ? "/storage/v1/object/public/" : "/storage/v1/object/authenticated/";
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(storageUri(route, row.bucket(), row.objectPath()))
                .timeout(Duration.ofSeconds(60));
        if (publicAsset) request.header("apikey", credential);
        else ServerSupabaseCredentials.applyTo(request);
        HttpResponse<byte[]> response = HTTP.send(request.GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 404) return null;
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Cloud image download failed with HTTP " + response.statusCode() + ".");
        }
        return response.body();
    }

    private static void deleteCloud(AssetRow row) throws Exception {
        if ("DELETED".equals(row.cloudStatus()) || "MISSING".equals(row.cloudStatus())) return;
        HttpRequest.Builder request = HttpRequest.newBuilder()
                        .uri(storageUri("/storage/v1/object/", row.bucket(), row.objectPath()))
                        .timeout(Duration.ofSeconds(60)).DELETE();
        HttpResponse<String> response = HTTP.send(
                ServerSupabaseCredentials.applyTo(request).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 404) requireSuccess(response.statusCode(), response.body(), "delete");
    }

    private static URI storageUri(String route, String bucket, String path) {
        String encodedPath = java.util.Arrays.stream(path.split("/"))
                .map(ServerImageAssetService::encode).reduce((a, b) -> a + "/" + b).orElse("");
        return URI.create(SupabaseSessionManager.getSupabaseUrl() + route + encode(bucket) + "/" + encodedPath);
    }

    private static void requireSuccess(int status, String body, String operation) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException("Cloud image " + operation + " failed with HTTP " + status + ": " + clean(body, 500, ""));
        }
    }

    private static Path mirrorPath(String bucket, String objectPath) throws IOException {
        validateLocation(bucket, objectPath);
        Path root = imageRoot();
        Path path = root.resolve(bucket).resolve(objectPath).normalize();
        if (!path.startsWith(root)) throw new IOException("Image path escaped the configured image store.");
        return path;
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Path root = imageRoot();
        Files.createDirectories(root);
        SecureFilePermissions.restrictDirectoryToOwner(root);
        Files.createDirectories(target.getParent());
        Path current = root;
        Path relativeParent = root.relativize(target.getParent());
        for (Path segment : relativeParent) {
            current = current.resolve(segment);
            SecureFilePermissions.restrictDirectoryToOwner(current);
        }
        Path temp = Files.createTempFile(target.getParent(), ".image-", ".tmp");
        try {
            Files.write(temp, bytes);
            SecureFilePermissions.restrictFileToOwner(temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            SecureFilePermissions.restrictFileToOwner(target);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static Path imageRoot() throws IOException {
        String override = System.getProperty("smartstock.image.store");
        Path desired = override == null || override.isBlank()
                ? EnvironmentProfile.active().directory().resolve("image-store")
                : Path.of(override.trim());
        desired = desired.toAbsolutePath().normalize();
        Path ready = preparedImageRoot;
        if (desired.equals(ready)) return desired;
        synchronized (IMAGE_ROOT_LOCK) {
            if (desired.equals(preparedImageRoot)) return desired;
            if ((override == null || override.isBlank())
                    && EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT
                    && Files.isDirectory(LEGACY_IMAGE_ROOT)
                    && !Files.exists(desired)) {
                Files.createDirectories(desired.getParent());
                try {
                    Files.move(LEGACY_IMAGE_ROOT, desired, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(LEGACY_IMAGE_ROOT, desired);
                }
            }
            Files.createDirectories(desired);
            SecureFilePermissions.restrictDirectoryToOwner(desired);
            preparedImageRoot = desired;
            return desired;
        }
    }

    private static void validateLocation(String bucket, String path) throws IOException {
        if (bucket == null || bucket.isBlank() || bucket.contains("/") || bucket.contains("\\")
                || ".".equals(bucket) || "..".equals(bucket)) throw new IOException("Invalid image bucket.");
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
                || java.util.Arrays.stream(path.split("/")).anyMatch(p -> p.isBlank() || ".".equals(p) || "..".equals(p))) {
            throw new IOException("Invalid image object path.");
        }
    }

    private static LegacyLocation parseLegacyUrl(String value) {
        try {
            URI uri = URI.create(value);
            String marker = "/storage/v1/object/";
            int start = uri.getPath().indexOf(marker);
            if (start < 0) return null;
            String rest = uri.getPath().substring(start + marker.length());
            boolean authenticated = rest.startsWith("authenticated/");
            if (authenticated) rest = rest.substring("authenticated/".length());
            else if (rest.startsWith("public/")) rest = rest.substring("public/".length());
            else return null;
            int slash = rest.indexOf('/');
            if (slash <= 0 || slash == rest.length() - 1) return null;
            return new LegacyLocation(decode(rest.substring(0, slash)), decodePath(rest.substring(slash + 1)), authenticated);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean hasColumns(Connection conn, String table, String... names) throws SQLException {
        for (String name : names) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema=current_schema() AND table_name=? AND column_name=?
                    """)) {
                ps.setString(1, table);
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                }
            }
        }
        return true;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String decodePath(String value) {
        return java.util.Arrays.stream(value.split("/")).map(ServerImageAssetService::decode)
                .reduce((a, b) -> a + "/" + b).orElse("");
    }

    private static String filename(String path) {
        int slash = path == null ? -1 : path.lastIndexOf('/');
        return slash < 0 ? clean(path, 500, "image") : clean(path.substring(slash + 1), 500, "image");
    }

    private static String extension(String path) {
        String name = filename(path).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "img";
        String extension = name.substring(dot + 1).replaceAll("[^a-z0-9]", "");
        if ("jpeg".equals(extension)) return "jpg";
        return extension.isBlank() || extension.length() > 5 ? "img" : extension;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String clean(String value, int max, String fallback) {
        String result = value == null ? "" : value.trim();
        if (result.isBlank()) result = fallback;
        return result.length() <= max ? result : result.substring(0, max);
    }

    private static long epoch(java.sql.Timestamp value) {
        return value == null ? 0L : value.toInstant().toEpochMilli();
    }

    private record ReferenceSource(String table, String key, String column, String category) { }
    private record CloudPrefix(String bucket, String prefix, String category, String accessLevel) { }
    private record LegacyLocation(String bucket, String path, boolean authenticated) { }
    private record AssetRow(UUID id, String bucket, String objectPath, String accessLevel, String contentType,
                            String sha256, String lifecycleStatus, String localStatus, String cloudStatus) { }
    public record AssetBytes(byte[] bytes, String contentType, String sha256) { }
    public record AssetView(String assetId, String category, String filename, long byteSize, String lifecycleStatus,
                            String localStatus, String cloudStatus, long createdAtEpochMillis, long updatedAtEpochMillis,
                            long unusedSinceEpochMillis, String lastError, int referenceCount) { }
    public record SyncResult(int references, int uploaded, int repaired) { }
    public record Counts(int pendingUploads, int missingLocal, int missingCloud, int unused, int failedPurges,
                         boolean cloudCredentialConfigured) { }
}
