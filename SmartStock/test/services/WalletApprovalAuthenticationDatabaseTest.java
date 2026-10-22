package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Uses only an explicitly selected disposable loopback database and temporary tables. */
class WalletApprovalAuthenticationDatabaseTest {
    @Test void walletApproverAuthenticationEnforcesPinRevocationActivityAndStore() throws Exception {
        String url = System.getProperty("smartstock.wallet.test.jdbc", "");
        assumeTrue(!url.isBlank());
        if (!url.equals("jdbc:postgresql://127.0.0.1:55449/postgres")) {
            throw new IllegalArgumentException("Use the isolated Wallet approval test cluster.");
        }
        String credential = "SSW1" + "A".repeat(32);
        byte[] salt = new byte[16];
        String pinHash = Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec("1234".toCharArray(), salt, 120_000, 256)).getEncoded());
        try (Connection c = DriverManager.getConnection(url, "wallet_test", "")) {
            c.setAutoCommit(false);
            try (var s = c.createStatement()) {
                s.execute("CREATE TEMP TABLE users(user_id int,username text,full_name text,is_active boolean,employee_pin_salt text,employee_pin_hash text)");
                s.execute("CREATE TEMP TABLE user_locations(user_id int,location_id int)");
                s.execute("CREATE TEMP TABLE company_customization(location_id int,require_badge_pin_login boolean)");
                s.execute("CREATE TEMP TABLE employee_wallet_credentials(user_id int,credential_hash text,status text,last_used_at timestamptz,updated_at timestamptz)");
                s.execute("INSERT INTO user_locations VALUES(1,1)");
                s.execute("INSERT INTO company_customization VALUES(1,true)");
            }
            try (var p = c.prepareStatement("INSERT INTO users VALUES(1,'manager','Manager',true,?,?)")) {
                p.setString(1, Base64.getEncoder().encodeToString(salt)); p.setString(2, pinHash); p.executeUpdate();
            }
            try (var p = c.prepareStatement("INSERT INTO employee_wallet_credentials VALUES(1,?,'ACTIVE',null,null)")) {
                p.setString(1, LanSecurity.sha256(credential)); p.executeUpdate();
            }
            assertNull(AppleWalletBadgeService.authenticate(c,credential,1,new char[0]));
            assertNull(AppleWalletBadgeService.authenticate(c,credential,1,"9999".toCharArray()));
            assertNotNull(AppleWalletBadgeService.authenticate(c,"]Q3"+credential,1,"1234".toCharArray()));
            assertNull(AppleWalletBadgeService.authenticate(c,credential,2,"1234".toCharArray()));
            try (var s = c.createStatement()) {
                s.execute("UPDATE company_customization SET require_badge_pin_login=false");
            }
            assertFalse(AppleWalletBadgeService.authenticate(c,credential,1,new char[0]).pinRequired());
            try (var s = c.createStatement()) { s.execute("UPDATE users SET is_active=false"); }
            assertNull(AppleWalletBadgeService.authenticate(c,credential,1,new char[0]));
            try (var s = c.createStatement()) {
                s.execute("UPDATE users SET is_active=true");
                s.execute("UPDATE employee_wallet_credentials SET status='REVOKED'");
            }
            assertNull(AppleWalletBadgeService.authenticate(c,credential,1,new char[0]));
            c.rollback();
        }
    }
}
