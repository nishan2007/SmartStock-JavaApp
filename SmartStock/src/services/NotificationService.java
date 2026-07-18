package services;

import models.AppNotification;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Register-safe notification facade backed by the authenticated LAN service. */
public final class NotificationService {
    private NotificationService() { }

    public static NotificationSummary loadSummary() {
        List<AppNotification> notifications = loadNotifications();
        int unread = 0, urgent = 0;
        for (AppNotification notification : notifications) {
            if (notification.isUnreadVisible()) unread++;
            if (notification.isUrgentVisible()) urgent++;
        }
        return new NotificationSummary(unread, urgent);
    }

    public static List<AppNotification> loadNotifications() {
        try { return LanApiClient.loadNotifications(); }
        catch (Exception ex) { return List.of(); }
    }

    public static void markRead(String notificationKey) throws SQLException {
        mutate("READ", notificationKey, 0, null);
    }

    public static void snooze(String notificationKey, int minutes) throws SQLException {
        mutate("SNOOZE", notificationKey, minutes, null);
    }

    public static void clear(String notificationKey) throws SQLException {
        mutate("CLEAR", notificationKey, 0, null);
    }

    public static void markSeen(AppNotification notification) throws SQLException {
        if (notification != null) mutate("SEEN", notification.notificationKey(), 0, notification);
    }

    private static void mutate(String action, String key, int minutes,
                               AppNotification notification) throws SQLException {
        if (key == null || key.isBlank()) return;
        try { LanApiClient.updateNotification(action, key, minutes, notification,
                UUID.randomUUID().toString()); }
        catch (Exception ex) { throw new SQLException("The notification could not be updated through the SmartStock server.", ex); }
    }

    public record NotificationSummary(int unreadCount, int urgentCount) {
        public String label() {
            if (urgentCount > 0) return "Notifications (" + urgentCount + " urgent)";
            if (unreadCount > 0) return "Notifications (" + unreadCount + ")";
            return "Notifications";
        }
    }
}
