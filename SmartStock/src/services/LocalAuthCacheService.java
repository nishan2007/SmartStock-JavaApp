package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

public final class LocalAuthCacheService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private LocalAuthCacheService() {
    }

    public static boolean shouldUseLocalAuthCache() {
        DatabaseMode mode = DatabaseConfig.load().mode();
        return mode == DatabaseMode.SERVER || mode == DatabaseMode.CLIENT;
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS local_auth_cache (
                        user_id INTEGER PRIMARY KEY,
                        username TEXT NOT NULL,
                        full_name TEXT,
                        email TEXT,
                        badge_id TEXT,
                        role_name TEXT,
                        location_id INTEGER,
                        location_name TEXT,
                        location_timezone TEXT,
                        pin_salt TEXT,
                        pin_hash TEXT,
                        password_salt TEXT,
                        password_hash TEXT,
                        pin_cached_at TIMESTAMPTZ,
                        employee_pin_salt TEXT,
                        employee_pin_hash TEXT,
                        employee_pin_cached_at TIMESTAMPTZ,
                        password_cached_at TIMESTAMPTZ,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        cached_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE local_auth_cache ALTER COLUMN pin_salt DROP NOT NULL");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ALTER COLUMN pin_hash DROP NOT NULL");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS password_salt TEXT");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS password_hash TEXT");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS pin_cached_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS employee_pin_salt TEXT");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS employee_pin_hash TEXT");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS employee_pin_cached_at TIMESTAMPTZ");
            stmt.executeUpdate("""
                    UPDATE local_auth_cache
                    SET employee_pin_salt = COALESCE(employee_pin_salt, pin_salt),
                        employee_pin_hash = COALESCE(employee_pin_hash, pin_hash),
                        employee_pin_cached_at = COALESCE(employee_pin_cached_at, pin_cached_at, cached_at)
                    WHERE employee_pin_hash IS NULL
                      AND pin_hash IS NOT NULL
                    """);
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS password_cached_at TIMESTAMPTZ");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_username_idx ON local_auth_cache(LOWER(username))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_email_idx ON local_auth_cache(LOWER(email))");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS badge_id TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_badge_idx ON local_auth_cache(LOWER(badge_id))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_badge_normalized_idx ON local_auth_cache(UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')))");
        }
    }

    public static void savePasswordVerifier(Connection conn, CachedUser user, char[] password) throws SQLException {
        ensureSchema(conn);
        String salt = newSalt();
        String hash = hashSecret(password, salt);
        String sql = """
                INSERT INTO local_auth_cache (
                    user_id, username, full_name, email, badge_id, role_name,
                    location_id, location_name, location_timezone,
                    password_salt, password_hash, password_cached_at, is_active, cached_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET
                    username = EXCLUDED.username,
                    full_name = EXCLUDED.full_name,
                    email = EXCLUDED.email,
                    badge_id = EXCLUDED.badge_id,
                    role_name = EXCLUDED.role_name,
                    location_id = EXCLUDED.location_id,
                    location_name = EXCLUDED.location_name,
                    location_timezone = EXCLUDED.location_timezone,
                    password_salt = EXCLUDED.password_salt,
                    password_hash = EXCLUDED.password_hash,
                    password_cached_at = CURRENT_TIMESTAMP,
                    is_active = TRUE,
                    cached_at = CURRENT_TIMESTAMP
                """;
        saveVerifier(conn, user, salt, hash, sql);
    }

    public static void saveEmployeePin(Connection conn, CachedUser user, char[] pin) throws SQLException {
        ensureSchema(conn);
        String salt = newSalt();
        String hash = hashSecret(pin, salt);
        saveGlobalEmployeePin(conn, user.userId(), salt, hash);
        String sql = """
                INSERT INTO local_auth_cache (
                    user_id, username, full_name, email, badge_id, role_name,
                    location_id, location_name, location_timezone,
                    employee_pin_salt, employee_pin_hash, employee_pin_cached_at, is_active, cached_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, TRUE, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET
                    username = EXCLUDED.username,
                    full_name = EXCLUDED.full_name,
                    email = EXCLUDED.email,
                    badge_id = EXCLUDED.badge_id,
                    role_name = EXCLUDED.role_name,
                    location_id = EXCLUDED.location_id,
                    location_name = EXCLUDED.location_name,
                    location_timezone = EXCLUDED.location_timezone,
                    employee_pin_salt = EXCLUDED.employee_pin_salt,
                    employee_pin_hash = EXCLUDED.employee_pin_hash,
                    employee_pin_cached_at = CURRENT_TIMESTAMP,
                    is_active = TRUE,
                    cached_at = CURRENT_TIMESTAMP
                """;
        saveVerifier(conn, user, salt, hash, sql);
    }

    private static void saveGlobalEmployeePin(Connection conn, int userId, String salt, String hash) throws SQLException {
        if (DatabaseConfig.load().mode() == DatabaseMode.SERVER) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_pin_salt TEXT");
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_pin_hash TEXT");
                stmt.executeUpdate("ALTER TABLE users ADD COLUMN IF NOT EXISTS employee_pin_updated_at TIMESTAMPTZ");
            }
        }
        String sql = """
                UPDATE users
                SET employee_pin_salt = ?,
                    employee_pin_hash = ?,
                    employee_pin_updated_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, salt);
            ps.setString(2, hash);
            ps.setInt(3, userId);
            ps.executeUpdate();
        }
    }

    private static void saveVerifier(Connection conn, CachedUser user, String salt, String hash, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.userId());
            ps.setString(2, user.username());
            ps.setString(3, user.fullName());
            ps.setString(4, user.email());
            ps.setString(5, user.badgeId());
            ps.setString(6, user.roleName());
            ps.setInt(7, user.locationId());
            ps.setString(8, user.locationName());
            ps.setString(9, user.locationTimezone());
            ps.setString(10, salt);
            ps.setString(11, hash);
            ps.executeUpdate();
        }
    }

    public static CachedUser verify(Connection conn, String identifier, char[] secret, Integer preferredLocationId) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT lac.*,
                       u.password_cache_invalidated_at
                FROM local_auth_cache lac
                LEFT JOIN users u ON u.user_id = lac.user_id
                WHERE lac.is_active = TRUE
                  AND (
                      LOWER(lac.username) = LOWER(?)
                      OR LOWER(lac.email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(lac.badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return verifyGlobalEmployeePin(conn, identifier, secret, preferredLocationId);
                }
                boolean passwordMatches = isPasswordCacheFresh(rs)
                        && matchesStoredSecret(secret, rs.getString("password_salt"), rs.getString("password_hash"));
                boolean pinMatches = matchesStoredSecret(secret, rs.getString("employee_pin_salt"), rs.getString("employee_pin_hash"))
                        || matchesStoredSecret(secret, rs.getString("pin_salt"), rs.getString("pin_hash"));
                if (!passwordMatches && !pinMatches) {
                    return verifyGlobalEmployeePin(conn, identifier, secret, preferredLocationId);
                }
                return new CachedUser(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("badge_id"),
                        rs.getString("role_name"),
                        rs.getInt("location_id"),
                        rs.getString("location_name"),
                        rs.getString("location_timezone")
                );
            }
        }
    }

    private static CachedUser verifyGlobalEmployeePin(Connection conn, String identifier, char[] secret,
                                                      Integer preferredLocationId) throws SQLException {
        String sql = """
                SELECT u.user_id,
                       u.username,
                       u.full_name,
                       u.email,
                       u.badge_id,
                       u.employee_pin_salt,
                       u.employee_pin_hash,
                       COALESCE(r.role_name, 'USER') AS role_name,
                       l.location_id,
                       l.name AS location_name,
                       COALESCE(l.timezone, '') AS location_timezone
                FROM users u
                LEFT JOIN roles r ON u.role_id = r.role_id
                JOIN user_locations ul ON ul.user_id = u.user_id
                JOIN locations l ON l.location_id = ul.location_id
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                  AND u.employee_pin_salt IS NOT NULL
                  AND u.employee_pin_hash IS NOT NULL
                  AND (
                      LOWER(u.username) = LOWER(?)
                      OR LOWER(u.email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(u.badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                  AND (?::int IS NULL OR ul.location_id = ?)
                ORDER BY l.name
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            if (preferredLocationId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, preferredLocationId);
                ps.setInt(5, preferredLocationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                if (!matchesStoredSecret(secret, rs.getString("employee_pin_salt"), rs.getString("employee_pin_hash"))) {
                    return null;
                }
                return new CachedUser(
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("badge_id"),
                        rs.getString("role_name"),
                        rs.getInt("location_id"),
                        rs.getString("location_name"),
                        rs.getString("location_timezone")
                );
            }
        }
    }

    public static boolean hasPasswordVerifier(Connection conn, String identifier) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT 1
                FROM local_auth_cache
                WHERE is_active = TRUE
                  AND password_salt IS NOT NULL
                  AND password_hash IS NOT NULL
                  AND (
                      NOT EXISTS (
                          SELECT 1
                          FROM users u
                          WHERE u.user_id = local_auth_cache.user_id
                            AND u.password_cache_invalidated_at IS NOT NULL
                            AND (
                                local_auth_cache.password_cached_at IS NULL
                                OR local_auth_cache.password_cached_at < u.password_cache_invalidated_at
                            )
                      )
                  )
                  AND (
                      LOWER(username) = LOWER(?)
                      OR LOWER(email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean hasAnyPasswordVerifier(Connection conn, String identifier) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT 1
                FROM local_auth_cache
                WHERE is_active = TRUE
                  AND password_salt IS NOT NULL
                  AND password_hash IS NOT NULL
                  AND (
                      LOWER(username) = LOWER(?)
                      OR LOWER(email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean hasCachedUser(Connection conn, String identifier) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT 1
                FROM local_auth_cache
                WHERE is_active = TRUE
                  AND (
                      LOWER(username) = LOWER(?)
                      OR LOWER(email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static boolean hasGlobalEmployeePin(Connection conn, String identifier, Integer preferredLocationId) throws SQLException {
        String sql = """
                SELECT 1
                FROM users u
                JOIN user_locations ul ON ul.user_id = u.user_id
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                  AND u.employee_pin_salt IS NOT NULL
                  AND u.employee_pin_hash IS NOT NULL
                  AND (
                      LOWER(u.username) = LOWER(?)
                      OR LOWER(u.email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(u.badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                  AND (?::int IS NULL OR ul.location_id = ?)
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            if (preferredLocationId == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, preferredLocationId);
                ps.setInt(5, preferredLocationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static boolean matchesStoredSecret(char[] secret, String salt, String expectedHash) throws SQLException {
        if (salt == null || expectedHash == null) {
            return false;
        }
        return constantTimeEquals(expectedHash, hashSecret(secret, salt));
    }

    private static boolean isPasswordCacheFresh(ResultSet rs) throws SQLException {
        java.sql.Timestamp invalidatedAt = rs.getTimestamp("password_cache_invalidated_at");
        if (invalidatedAt == null) {
            return true;
        }
        java.sql.Timestamp cachedAt = rs.getTimestamp("password_cached_at");
        return cachedAt != null && !cachedAt.before(invalidatedAt);
    }

    private static String hashSecret(char[] secret, String salt) throws SQLException {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret, Base64.getDecoder().decode(salt), 120_000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new SQLException("Unable to hash local login secret.", ex);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        byte[] left = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int diff = left.length ^ right.length;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }

    public record CachedUser(
            int userId,
            String username,
            String fullName,
            String email,
            String badgeId,
            String roleName,
            int locationId,
            String locationName,
            String locationTimezone
    ) {
    }
}
