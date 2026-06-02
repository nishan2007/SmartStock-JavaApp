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
                        pin_salt TEXT NOT NULL,
                        pin_hash TEXT NOT NULL,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        cached_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_username_idx ON local_auth_cache(LOWER(username))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_email_idx ON local_auth_cache(LOWER(email))");
            stmt.executeUpdate("ALTER TABLE local_auth_cache ADD COLUMN IF NOT EXISTS badge_id TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_badge_idx ON local_auth_cache(LOWER(badge_id))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS local_auth_cache_badge_normalized_idx ON local_auth_cache(UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')))");
        }
    }

    public static void saveUser(Connection conn, CachedUser user, char[] pin) throws SQLException {
        ensureSchema(conn);
        String salt = newSalt();
        String hash = hashPin(pin, salt);
        String sql = """
                INSERT INTO local_auth_cache (
                    user_id, username, full_name, email, badge_id, role_name,
                    location_id, location_name, location_timezone,
                    pin_salt, pin_hash, is_active, cached_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id) DO UPDATE SET
                    username = EXCLUDED.username,
                    full_name = EXCLUDED.full_name,
                    email = EXCLUDED.email,
                    badge_id = EXCLUDED.badge_id,
                    role_name = EXCLUDED.role_name,
                    location_id = EXCLUDED.location_id,
                    location_name = EXCLUDED.location_name,
                    location_timezone = EXCLUDED.location_timezone,
                    pin_salt = EXCLUDED.pin_salt,
                    pin_hash = EXCLUDED.pin_hash,
                    is_active = TRUE,
                    cached_at = CURRENT_TIMESTAMP
                """;
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

    public static CachedUser verify(Connection conn, String identifier, char[] pin) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT *
                FROM local_auth_cache
                WHERE is_active = TRUE
                  AND (
                      LOWER(username) = LOWER(?)
                      OR LOWER(email) = LOWER(?)
                      OR UPPER(REGEXP_REPLACE(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?
                  )
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, identifier);
            ps.setString(2, identifier);
            ps.setString(3, BadgeCredentialService.normalizeBadge(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String expected = rs.getString("pin_hash");
                String actual = hashPin(pin, rs.getString("pin_salt"));
                if (!constantTimeEquals(expected, actual)) {
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

    private static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String hashPin(char[] pin, String salt) throws SQLException {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin, Base64.getDecoder().decode(salt), 120_000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new SQLException("Unable to hash local PIN.", ex);
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
