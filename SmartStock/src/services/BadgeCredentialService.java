package services;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Base64;

public final class BadgeCredentialService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BADGE_PREFIX = "SSB1";
    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int RANDOM_BADGE_CHARS = 16;
    private static final int HASH_ITERATIONS = 120_000;
    private static final int HASH_BITS = 256;

    private BadgeCredentialService() {
    }

    public static String generateBadgeId() {
        StringBuilder badge = new StringBuilder(BADGE_PREFIX.length() + RANDOM_BADGE_CHARS);
        badge.append(BADGE_PREFIX);
        for (int i = 0; i < RANDOM_BADGE_CHARS; i++) {
            badge.append(CROCKFORD_BASE32.charAt(RANDOM.nextInt(CROCKFORD_BASE32.length())));
        }
        return badge.toString();
    }

    public static String normalizeBadge(String badgeId) {
        if (badgeId == null) {
            return "";
        }
        return badgeId.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "")
                .replace('I', '1')
                .replace('L', '1')
                .replace('O', '0');
    }

    public static boolean looksLikeGeneratedBadge(String value) {
        return normalizeBadge(value).startsWith(BADGE_PREFIX);
    }

    public static BadgeHash createHash(String badgeId, LocalDate dateOfBirth) throws SQLException {
        String salt = newSalt();
        return new BadgeHash(salt, hashBadge(badgeId, dateOfBirth, salt));
    }

    public static boolean verify(String enteredBadge, ResultSet userRow) throws SQLException {
        String savedBadge = userRow.getString("badge_id");
        String normalizedEntered = normalizeBadge(enteredBadge);
        if (normalizedEntered.isEmpty() || !normalizedEntered.equals(normalizeBadge(savedBadge))) {
            return false;
        }

        String salt = getNullableColumn(userRow, "badge_secret_salt");
        String expectedHash = getNullableColumn(userRow, "badge_secret_hash");
        if (salt == null || salt.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return true;
        }

        LocalDate dateOfBirth = null;
        Date sqlDate = userRow.getDate("date_of_birth");
        if (sqlDate != null) {
            dateOfBirth = sqlDate.toLocalDate();
        }
        String actualHash = hashBadge(savedBadge, dateOfBirth, salt);
        return constantTimeEquals(expectedHash, actualHash);
    }

    private static String newSalt() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String hashBadge(String badgeId, LocalDate dateOfBirth, String salt) throws SQLException {
        String normalizedBadge = normalizeBadge(badgeId);
        String dobPart = dateOfBirth == null ? "" : dateOfBirth.toString();
        char[] secret = (normalizedBadge + "|" + dobPart).toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(secret, Base64.getDecoder().decode(salt), HASH_ITERATIONS, HASH_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception ex) {
            throw new SQLException("Unable to hash badge credential.", ex);
        }
    }

    private static String getNullableColumn(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            return null;
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

    public record BadgeHash(String salt, String hash) {
    }
}
