package services;

import models.AppNotification;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import ui.helpers.SessionDataCache;

/** Register-safe notification facade backed by the authenticated LAN service. */
public final class NotificationService {
    private static final String SUMMARY_CACHE_KEY = "notifications:summary";
    private NotificationService() { }

    public static NotificationSummary loadSummary() {
        List<AppNotification> notifications = loadNotifications();
        int unread = 0, urgent = 0;
        for (AppNotification notification : notifications) {
            if (notification.isUnreadVisible()) unread++;
            if (notification.isUrgentVisible()) urgent++;
        }
        NotificationSummary summary = new NotificationSummary(unread, urgent);
        SessionDataCache.put(SUMMARY_CACHE_KEY, summary);
        return summary;
    }

    /** Returns immediately and never performs network access. */
    public static NotificationSummary cachedSummary() {
        return SessionDataCache.get(SUMMARY_CACHE_KEY, NotificationSummary.class,
                        SessionDataCache.NOTIFICATION_TTL)
                .map(SessionDataCache.CachedValue::value)
                .orElse(new NotificationSummary(0, 0));
    }

    public static List<AppNotification> loadNotifications() {
        try {
            List<AppNotification> notifications = LanApiClient.loadNotifications();
            int unread = 0, urgent = 0;
            for (AppNotification notification : notifications) {
                if (notification.isUnreadVisible()) unread++;
                if (notification.isUrgentVisible()) urgent++;
            }
            SessionDataCache.put(SUMMARY_CACHE_KEY, new NotificationSummary(unread, urgent));
            return notifications;
        }
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
                UUID.randomUUID().toString()); SessionDataCache.invalidate(SUMMARY_CACHE_KEY); }
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
