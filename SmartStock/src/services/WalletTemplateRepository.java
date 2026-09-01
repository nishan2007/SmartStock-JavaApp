package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Uses the caller's LAN authorization, selected store and transaction. */
public final class WalletTemplateRepository {
    private WalletTemplateRepository() { }
    public static WalletBadgeTemplate load(Connection c, int location) throws SQLException {
        try (var ps = c.prepareStatement("SELECT wallet_template_json FROM company_customization WHERE location_id=?")) {
            ps.setInt(1, location);
            try (var rs = ps.executeQuery()) { return rs.next() ? WalletBadgeTemplate.parse(rs.getString(1)) : WalletBadgeTemplate.defaults(); }
        }
    }
    public static void save(Connection c, int location, String json) throws SQLException {
        String normalized = WalletBadgeTemplate.parse(json).json();
        try (var ps = c.prepareStatement("INSERT INTO company_customization(location_id,wallet_template_json,updated_at) VALUES(?,?,CURRENT_TIMESTAMP) ON CONFLICT(location_id) DO UPDATE SET wallet_template_json=EXCLUDED.wallet_template_json,updated_at=CURRENT_TIMESTAMP")) {
            ps.setInt(1, location); ps.setString(2, normalized); ps.executeUpdate();
        }
    }
}
