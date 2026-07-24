package services;

import data.EnvironmentProfile;
import utils.SecureCredentialStore;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

/** Handles one-time device key pairs and revocable HTTPS API credentials. */
public final class DeviceCredentialService {
    private static final String PRIVATE_KEY_SECRET = EnvironmentProfile.active().secretKey("device-pairing-private-key");
    private static final String PUBLIC_KEY_SECRET = EnvironmentProfile.active().secretKey("device-pairing-public-key");
    public static final String LAN_API_TOKEN_SECRET = EnvironmentProfile.active().secretKey("lan-api-device-token");
    public static final String LAN_API_FINGERPRINT_SECRET = EnvironmentProfile.active().secretKey("lan-api-server-fingerprint");
    public static final String LAN_API_PAIRING_CHALLENGE_SECRET = EnvironmentProfile.active().secretKey("lan-api-pairing-challenge");
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceCredentialService() {
    }

    public static String pairingPublicKey() throws Exception {
        String publicKey = SecureCredentialStore.read(PUBLIC_KEY_SECRET);
        String privateKey = SecureCredentialStore.read(PRIVATE_KEY_SECRET);
        if (publicKey != null && privateKey != null) {
            return publicKey;
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, RANDOM);
        KeyPair pair = generator.generateKeyPair();
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        SecureCredentialStore.write(PUBLIC_KEY_SECRET, publicKey);
        SecureCredentialStore.write(PRIVATE_KEY_SECRET, privateKey);
        return publicKey;
    }

    public static void requestRotation(Connection conn, String deviceId, Integer actorUserId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE devices
                SET credential_status = 'ROTATION_PENDING',
                    credential_issued_at = NULL, credential_claimed_at = NULL
                WHERE device_id = ? AND is_approved = TRUE AND is_blocked = FALSE
                """)) {
            ps.setObject(1, UUID.fromString(deviceId));
            ps.executeUpdate();
        }
        audit(conn, "DEVICE_CREDENTIAL_ROTATION_REQUESTED", deviceId, actorUserId, "Administrator requested credential rotation");
    }

    public static void revokeCredential(Connection conn, String deviceId, Integer actorUserId) throws SQLException {
        if (hasColumn(conn, "devices", "credential_status")) {
            String sql = hasColumn(conn, "devices", "api_credential_hash") ? """
                    UPDATE devices SET credential_status = 'REVOKED',
                        api_credential_hash = NULL, api_previous_credential_hash = NULL,
                        api_credential_expires_at = NULL, api_previous_expires_at = NULL,
                        api_pairing_challenge_hash = NULL, api_pairing_challenge_expires_at = NULL
                    WHERE device_id = ?
                    """ : "UPDATE devices SET credential_status = 'REVOKED' WHERE device_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(deviceId));
                ps.executeUpdate();
            }
        }
        if (hasColumn(conn, "lan_api_sessions", "device_id")) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE lan_api_sessions SET revoked_at = CURRENT_TIMESTAMP
                    WHERE device_id = ? AND revoked_at IS NULL
                    """)) {
                ps.setObject(1, UUID.fromString(deviceId));
                ps.executeUpdate();
            }
        }
        audit(conn, "DEVICE_CREDENTIAL_REVOKED", deviceId, actorUserId, "Device credential revoked");
    }

    private static String decrypt(String envelope) throws Exception {
        String privateKeyText = SecureCredentialStore.read(PRIVATE_KEY_SECRET);
        if (privateKeyText == null) throw new IllegalStateException("This installation's pairing key is missing.");
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyText)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(envelope)), StandardCharsets.UTF_8);
    }

    /** Decrypts a server envelope using this installation's Keychain/DPAPI-backed pairing key. */
    public static String decryptLanEnvelope(String envelope) throws Exception {
        if (envelope == null || envelope.isBlank()) {
            throw new IllegalArgumentException("The LAN credential envelope is missing.");
        }
        return decrypt(envelope);
    }

    private static void audit(Connection conn, String eventType, String deviceId, Integer actorUserId, String details) {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO security_audit_events(event_type, device_id, actor_user_id, details)
                VALUES (?, ?::uuid, ?, ?)
                """)) {
            ps.setString(1, eventType);
            ps.setString(2, deviceId);
            if (actorUserId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, actorUserId);
            ps.setString(4, details);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("Could not record security audit event: " + ex.getMessage());
        }
    }


    private static boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }


}
