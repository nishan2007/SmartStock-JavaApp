package ui.helpers;

import managers.SessionManager;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class StoreTimeZoneHelper {
    private StoreTimeZoneHelper() {
    }

    public static ZoneId getStoreZone() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (Exception ignored) {
                // Fall back to the device zone if the stored value is invalid.
            }
        }
        return ZoneId.systemDefault();
    }

    public static String getStoreZoneId() {
        return getStoreZone().getId();
    }

    public static LocalDate today() {
        return LocalDate.now(getStoreZone());
    }

    public static String formatLocalTimestamp(Timestamp timestamp, DateTimeFormatter formatter) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(formatter);
    }

}
