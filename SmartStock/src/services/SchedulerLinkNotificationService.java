package services;

import data.DB;
import data.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Atomically queues one notification for each scheduler URL and recipient pair. */
final class SchedulerLinkNotificationService {
    private SchedulerLinkNotificationService() { }

    static boolean notifyIfChanged(String publicOrigin) throws Exception {
        if (publicOrigin == null || publicOrigin.isBlank()) return false;
        String schedulerUrl = publicOrigin.replaceAll("/+$", "") + "/scheduler/";
        long outboxId = 0;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int locationId;
                boolean enabled;
                String recipient;
                Integer configuredLocationId = DatabaseConfig.load().locationId();
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT l.location_id,
                               COALESCE(cc.scheduler_link_email_enabled,FALSE),
                               COALESCE(cc.scheduler_link_notification_email,'')
                        FROM locations l
                        LEFT JOIN company_customization cc ON cc.location_id=l.location_id
                        WHERE (? IS NULL OR l.location_id=?)
                        ORDER BY l.location_id LIMIT 1
                        """)) {
                    if (configuredLocationId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, configuredLocationId);
                    if (configuredLocationId == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, configuredLocationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        locationId = rs.getInt(1);
                        enabled = rs.getBoolean(2);
                        recipient = rs.getString(3).trim();
                    }
                }
                if (!enabled || recipient.isBlank()) { conn.rollback(); return false; }
                String lastOrigin;
                String lastRecipient;
                try (PreparedStatement lock = conn.prepareStatement("SELECT COALESCE(last_notified_origin,''), COALESCE(last_notified_recipient,'') FROM scheduler_web_runtime WHERE runtime_id=1 FOR UPDATE");
                     ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return false; }
                    lastOrigin = rs.getString(1);
                    lastRecipient = rs.getString(2);
                }
                if (schedulerUrl.equals(lastOrigin) && recipient.equalsIgnoreCase(lastRecipient)) {
                    conn.rollback();
                    return false;
                }
                outboxId = ServerEmailOutboxService.queueSchedulerLinkChanged(conn, locationId, recipient, schedulerUrl);
                try (PreparedStatement update = conn.prepareStatement("UPDATE scheduler_web_runtime SET last_notified_origin=?, last_notified_recipient=? WHERE runtime_id=1")) {
                    update.setString(1, schedulerUrl);
                    update.setString(2, recipient);
                    update.executeUpdate();
                }
                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        }
        ServerEmailOutboxService.processOneAsync(outboxId, null);
        return true;
    }
}
