package models;

import java.sql.Timestamp;

public record AppNotification(
        String notificationKey,
        Severity severity,
        Source source,
        String title,
        String message,
        String actionTarget,
        Timestamp createdAt,
        Timestamp readAt,
        Timestamp snoozedUntil,
        Timestamp dismissedAt,
        Timestamp dismissedUntil
) {
    public enum Severity {
        INFO,
        WARNING,
        URGENT
    }

    public enum Source {
        INVENTORY,
        ORDERS,
        EXCEPTIONS,
        SYNC,
        CASH_DRAWER,
        DEVICES,
        MAINTENANCE
    }

    public boolean isRead() {
        return readAt != null;
    }

    public boolean isSnoozed() {
        return snoozedUntil != null && snoozedUntil.after(new Timestamp(System.currentTimeMillis()));
    }

    public boolean isDismissed() {
        return dismissedUntil != null && dismissedUntil.after(new Timestamp(System.currentTimeMillis()));
    }

    public boolean isUnreadVisible() {
        return !isRead() && !isSnoozed() && !isDismissed();
    }

    public boolean isUrgentVisible() {
        return severity == Severity.URGENT && isUnreadVisible();
    }
}
