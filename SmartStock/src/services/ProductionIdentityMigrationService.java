package services;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Copies a controlled batch of up to three login identities from a
 * non-production SmartStock database into a fresh production store database.
 * Production readiness still enforces the accepted total identity count.
 * Passwords and local auth caches are intentionally outside this boundary.
 */
public final class ProductionIdentityMigrationService {
    public static final int REQUIRED_USER_COUNT = 3;

    private ProductionIdentityMigrationService() {
    }

    public static MigrationPreview preview(Connection source, Connection target,
                                           MigrationManifest manifest) throws SQLException {
        validateManifest(manifest);
        int targetLocationId = requireTargetLocation(target, manifest.productionLocation());
        List<PreparedIdentity> identities = new ArrayList<>();
        Set<String> badges = new HashSet<>();

        for (UserMapping mapping : manifest.users()) {
            SourceIdentity identity = loadSourceIdentity(source, mapping.sourceUsername());
            UUID productionAuthId = parseAuthId(mapping.productionAuthUserId());
            if (productionAuthId.toString().equalsIgnoreCase(identity.sourceAuthUserId())) {
                throw new SQLException("Production Auth UUID must not reuse the test Auth UUID for "
                        + identity.username() + ".");
            }
            int targetRoleId = requireTargetRole(target, identity.roleName());
            requireTargetIdentityAvailable(target, identity);
            String normalizedBadge = BadgeCredentialService.normalizeBadge(identity.badgeId());
            if (!badges.add(normalizedBadge)) {
                throw new SQLException("The migration manifest contains duplicate normalized badge IDs.");
            }
            identities.add(new PreparedIdentity(identity, productionAuthId, targetRoleId, targetLocationId));
        }
        return new MigrationPreview(List.copyOf(identities));
    }

    public static MigrationResult migrate(Connection source, Connection target,
                                          MigrationManifest manifest) throws SQLException {
        MigrationPreview preview = preview(source, target, manifest);
        boolean originalAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        List<MigratedIdentity> migrated = new ArrayList<>();
        try {
            for (PreparedIdentity prepared : preview.identities()) {
                int targetUserId = insertIdentity(target, prepared);
                assignLocation(target, targetUserId, prepared.targetLocationId());
                migrated.add(new MigratedIdentity(
                        prepared.source().sourceUserId(),
                        targetUserId,
                        prepared.source().username(),
                        prepared.productionAuthUserId(),
                        prepared.source().badgeId()
                ));
            }
            target.commit();
            return new MigrationResult(List.copyOf(migrated));
        } catch (Exception ex) {
            target.rollback();
            if (ex instanceof SQLException sqlException) throw sqlException;
            throw new SQLException("Identity migration failed and was rolled back.", ex);
        } finally {
            target.setAutoCommit(originalAutoCommit);
        }
    }

    public static void validateManifest(MigrationManifest manifest) {
        if (manifest == null || manifest.users() == null
                || manifest.users().isEmpty()
                || manifest.users().size() > REQUIRED_USER_COUNT) {
            throw new IllegalArgumentException(
                    "The production identity manifest must contain between one and three users.");
        }
        if (blank(manifest.productionLocation())) {
            throw new IllegalArgumentException("productionLocation is required.");
        }
        Set<String> usernames = new HashSet<>();
        Set<String> authIds = new HashSet<>();
        for (UserMapping mapping : manifest.users()) {
            if (mapping == null || blank(mapping.sourceUsername())
                    || blank(mapping.productionAuthUserId())) {
                throw new IllegalArgumentException(
                        "Every user requires sourceUsername and productionAuthUserId.");
            }
            if (!usernames.add(mapping.sourceUsername().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Source usernames must be unique.");
            }
            UUID authId = parseAuthId(mapping.productionAuthUserId());
            if (!authIds.add(authId.toString())) {
                throw new IllegalArgumentException("Production Auth UUIDs must be unique.");
            }
        }
    }

    public static List<SourceIdentity> listActiveAdministrators(Connection source)
            throws SQLException {
        List<SourceIdentity> identities = new ArrayList<>();
        try (PreparedStatement ps = source.prepareStatement("""
                SELECT u.username
                FROM users u
                JOIN roles r ON r.role_id=u.role_id
                WHERE COALESCE(u.is_active, TRUE)=TRUE
                  AND UPPER(r.role_name)='ADMIN'
                ORDER BY LOWER(u.username)
                """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) identities.add(loadSourceIdentity(source, rs.getString(1)));
        }
        return List.copyOf(identities);
    }

    public static SourceIdentity loadActiveAdministrator(Connection source, String username)
            throws SQLException {
        SourceIdentity identity = loadSourceIdentity(source, username);
        if (!identity.active() || !"ADMIN".equalsIgnoreCase(identity.roleName())) {
            throw new SQLException("The selected source user must be an active administrator.");
        }
        return identity;
    }

    static SourceIdentity loadSourceIdentity(Connection source, String username)
            throws SQLException {
        String sql = """
                SELECT u.user_id,u.username,u.email,u.full_name,u.nickname,u.date_of_birth,
                       u.badge_id,u.badge_secret_salt,u.badge_secret_hash,u.badge_generated_at,
                       u.auth_user_id::text AS source_auth_user_id,u.is_active,r.role_name
                FROM users u
                JOIN roles r ON r.role_id=u.role_id
                WHERE LOWER(u.username)=LOWER(?)
                """;
        try (PreparedStatement ps = source.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Source user was not found: " + username);
                SourceIdentity result = new SourceIdentity(
                        rs.getInt("user_id"),
                        required(rs.getString("username"), "Source username is missing."),
                        required(rs.getString("email"), "Source email is missing for " + username + "."),
                        required(rs.getString("full_name"), "Source display name is missing for " + username + "."),
                        rs.getString("nickname"),
                        rs.getDate("date_of_birth"),
                        required(rs.getString("badge_id"), "Source badge ID is missing for " + username + "."),
                        rs.getString("badge_secret_salt"),
                        rs.getString("badge_secret_hash"),
                        rs.getTimestamp("badge_generated_at"),
                        rs.getString("source_auth_user_id"),
                        rs.getBoolean("is_active"),
                        required(rs.getString("role_name"), "Source role is missing for " + username + ".")
                );
                if (rs.next()) throw new SQLException("Source username is not unique: " + username);
                if ((blank(result.badgeSecretSalt()) || blank(result.badgeSecretHash()))
                        && !(blank(result.badgeSecretSalt()) && blank(result.badgeSecretHash()))) {
                    throw new SQLException("Source badge verifier is incomplete for " + username + ".");
                }
                if (!blank(result.badgeSecretHash()) && result.dateOfBirth() == null) {
                    throw new SQLException("Date of birth is required to preserve the badge verifier for "
                            + username + ".");
                }
                return result;
            }
        }
    }

    private static void requireTargetIdentityAvailable(Connection target, SourceIdentity identity)
            throws SQLException {
        String sql = """
                SELECT CASE
                         WHEN LOWER(username)=LOWER(?) THEN 'username'
                         WHEN LOWER(COALESCE(email,''))=LOWER(?) THEN 'email'
                         ELSE 'badge'
                       END AS conflict
                FROM users
                WHERE LOWER(username)=LOWER(?)
                   OR LOWER(COALESCE(email,''))=LOWER(?)
                   OR UPPER(REGEXP_REPLACE(COALESCE(badge_id,''),'[^a-zA-Z0-9]','','g'))=?
                LIMIT 1
                """;
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            ps.setString(1, identity.username());
            ps.setString(2, identity.email());
            ps.setString(3, identity.username());
            ps.setString(4, identity.email());
            ps.setString(5, BadgeCredentialService.normalizeBadge(identity.badgeId()));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException("Production already contains this user's "
                            + rs.getString("conflict") + ": " + identity.username());
                }
            }
        }
    }

    private static int requireTargetRole(Connection target, String roleName) throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "SELECT role_id FROM roles WHERE UPPER(role_name)=UPPER(?)")) {
            ps.setString(1, roleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Production role does not exist: " + roleName);
                }
                int id = rs.getInt(1);
                if (rs.next()) throw new SQLException("Production role is not unique: " + roleName);
                return id;
            }
        }
    }

    private static int requireTargetLocation(Connection target, String locationName)
            throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "SELECT location_id FROM locations WHERE UPPER(name)=UPPER(?)")) {
            ps.setString(1, locationName.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Production location does not exist: " + locationName);
                }
                int id = rs.getInt(1);
                if (rs.next()) {
                    throw new SQLException("Production location name is not unique: " + locationName);
                }
                return id;
            }
        }
    }

    private static int insertIdentity(Connection target, PreparedIdentity prepared)
            throws SQLException {
        SourceIdentity source = prepared.source();
        String sql = """
                INSERT INTO users (
                    username,password_hash,full_name,nickname,email,date_of_birth,hire_date,
                    badge_id,badge_secret_salt,badge_secret_hash,badge_generated_at,badge_print_count,
                    compensation_type,salary,role_id,auth_user_id,is_active,
                    password_cache_invalidated_at,employee_pin_salt,employee_pin_hash,employee_pin_updated_at
                )
                VALUES (?,NULL,?,?,?,?,CURRENT_DATE,?,?,?,?,0,'HOURLY',0,?,?::uuid,?,
                        CURRENT_TIMESTAMP,NULL,NULL,NULL)
                RETURNING user_id
                """;
        try (PreparedStatement ps = target.prepareStatement(sql)) {
            ps.setString(1, source.username());
            ps.setString(2, source.fullName());
            setNullable(ps, 3, source.nickname(), Types.VARCHAR);
            ps.setString(4, source.email());
            setNullable(ps, 5, source.dateOfBirth(), Types.DATE);
            ps.setString(6, source.badgeId());
            setNullable(ps, 7, source.badgeSecretSalt(), Types.VARCHAR);
            setNullable(ps, 8, source.badgeSecretHash(), Types.VARCHAR);
            setNullable(ps, 9, source.badgeGeneratedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            ps.setInt(10, prepared.targetRoleId());
            ps.setObject(11, prepared.productionAuthUserId());
            ps.setBoolean(12, source.active());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Production user insert returned no ID.");
                return rs.getInt(1);
            }
        }
    }

    private static void assignLocation(Connection target, int userId, int locationId)
            throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "INSERT INTO user_locations(user_id,location_id) VALUES (?,?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, locationId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Production store assignment was not created.");
            }
        }
    }

    private static void setNullable(PreparedStatement ps, int index, Object value, int sqlType)
            throws SQLException {
        if (value == null) ps.setNull(index, sqlType);
        else ps.setObject(index, value);
    }

    private static UUID parseAuthId(String value) {
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("productionAuthUserId must be a UUID.", ex);
        }
    }

    private static String required(String value, String message) throws SQLException {
        if (blank(value)) throw new SQLException(message);
        return value.trim();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record MigrationManifest(String productionLocation, List<UserMapping> users) {
    }

    public record UserMapping(String sourceUsername, String productionAuthUserId) {
    }

    public record MigrationPreview(List<PreparedIdentity> identities) {
    }

    public record MigrationResult(List<MigratedIdentity> identities) {
    }

    public record MigratedIdentity(int sourceUserId, int productionUserId, String username,
                                   UUID productionAuthUserId, String badgeId) {
    }

    public record PreparedIdentity(SourceIdentity source, UUID productionAuthUserId,
                                   int targetRoleId, int targetLocationId) {
    }

    public record SourceIdentity(int sourceUserId, String username, String email,
                                 String fullName, String nickname, Date dateOfBirth,
                                 String badgeId, String badgeSecretSalt, String badgeSecretHash,
                                 Timestamp badgeGeneratedAt, String sourceAuthUserId,
                                 boolean active, String roleName) {
    }
}
