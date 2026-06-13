package services;

import data.DB;
import managers.PermissionManager;
import managers.SessionManager;
import models.AppNotification;
import models.CashDrawerContext;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class NotificationService {
    private static final int SYNC_PENDING_THRESHOLD = 25;
    private static final int CLEAR_SUPPRESSION_MINUTES = 30;

    private NotificationService() {
    }

    public static NotificationSummary loadSummary() {
        List<AppNotification> notifications = loadNotifications();
        int unread = 0;
        int urgent = 0;
        for (AppNotification notification : notifications) {
            if (notification.isUnreadVisible()) {
                unread++;
            }
            if (notification.isUrgentVisible()) {
                urgent++;
            }
        }
        return new NotificationSummary(unread, urgent);
    }

    public static List<AppNotification> loadNotifications() {
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            return List.of();
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            List<AppNotification> generated = new ArrayList<>();
            collectSafely(generated, AppNotification.Source.INVENTORY, () -> collectInventory(conn, generated));
            collectSafely(generated, AppNotification.Source.ORDERS, () -> collectOrders(conn, generated));
            collectSafely(generated, AppNotification.Source.EXCEPTIONS, () -> collectExceptions(conn, generated));
            collectSafely(generated, AppNotification.Source.SYNC, () -> collectSync(conn, generated));
            collectSafely(generated, AppNotification.Source.CASH_DRAWER, () -> collectCashDrawer(conn, generated));
            collectSafely(generated, AppNotification.Source.DEVICES, () -> collectDevices(conn, generated));
            collectSafely(generated, AppNotification.Source.MAINTENANCE, () -> collectMaintenance(conn, generated));
            applyState(conn, userId, generated);
            generated.sort(Comparator
                    .comparing((AppNotification n) -> severityRank(n.severity()))
                    .thenComparing(AppNotification::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AppNotification::title));
            return generated;
        } catch (Exception ex) {
            return List.of(new AppNotification(
                    "NOTIFICATION_LOAD_ERROR",
                    AppNotification.Severity.WARNING,
                    AppNotification.Source.SYNC,
                    "Notifications could not refresh",
                    ex.getMessage() == null ? "Check the database connection." : ex.getMessage(),
                    "SyncStatus",
                    new Timestamp(System.currentTimeMillis()),
                    null,
                    null,
                    null,
                    null
            ));
        }
    }

    private static void collectSafely(List<AppNotification> notifications, AppNotification.Source source,
                                      NotificationCollector collector) {
        try {
            collector.collect();
        } catch (Exception ex) {
            notifications.add(notification(
                    "NOTIFICATION_SOURCE_ERROR:" + source.name(),
                    AppNotification.Severity.WARNING,
                    source,
                    source.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " notifications could not refresh",
                    ex.getMessage() == null ? "Check this module's database schema." : ex.getMessage(),
                    "SyncStatus"
            ));
        }
    }

    public static void markRead(String notificationKey) throws SQLException {
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null || notificationKey == null || notificationKey.isBlank()) {
            return;
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO notification_user_state (
                        user_id, notification_key, read_at, last_seen_at, updated_at
                    )
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, notification_key)
                    DO UPDATE SET read_at = CURRENT_TIMESTAMP,
                                  snoozed_until = NULL,
                                  dismissed_at = NULL,
                                  dismissed_until = NULL,
                                  last_seen_at = CURRENT_TIMESTAMP,
                                  updated_at = CURRENT_TIMESTAMP
                    """)) {
                ps.setInt(1, userId);
                ps.setString(2, notificationKey);
                ps.executeUpdate();
            }
        }
    }

    public static void snooze(String notificationKey, int minutes) throws SQLException {
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null || notificationKey == null || notificationKey.isBlank()) {
            return;
        }
        int safeMinutes = Math.max(5, minutes);
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO notification_user_state (
                        user_id, notification_key, snoozed_until, last_seen_at, updated_at
                    )
                    VALUES (?, ?, CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, notification_key)
                    DO UPDATE SET snoozed_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'),
                                  dismissed_at = NULL,
                                  dismissed_until = NULL,
                                  last_seen_at = CURRENT_TIMESTAMP,
                                  updated_at = CURRENT_TIMESTAMP
                    """)) {
                ps.setInt(1, userId);
                ps.setString(2, notificationKey);
                ps.setInt(3, safeMinutes);
                ps.setInt(4, safeMinutes);
                ps.executeUpdate();
            }
        }
    }

    public static void clear(String notificationKey) throws SQLException {
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null || notificationKey == null || notificationKey.isBlank()) {
            return;
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO notification_user_state (
                        user_id, notification_key, dismissed_at, dismissed_until, last_seen_at, updated_at
                    )
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, notification_key)
                    DO UPDATE SET dismissed_at = CURRENT_TIMESTAMP,
                                  dismissed_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 minute'),
                                  last_seen_at = CURRENT_TIMESTAMP,
                                  updated_at = CURRENT_TIMESTAMP
                    """)) {
                ps.setInt(1, userId);
                ps.setString(2, notificationKey);
                ps.setInt(3, CLEAR_SUPPRESSION_MINUTES);
                ps.setInt(4, CLEAR_SUPPRESSION_MINUTES);
                ps.executeUpdate();
            }
        }
    }

    public static void markSeen(AppNotification notification) throws SQLException {
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null || notification == null) {
            return;
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO notification_user_state (
                        user_id, notification_key, last_seen_at, last_seen_severity, last_seen_source, updated_at
                    )
                    VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (user_id, notification_key)
                    DO UPDATE SET last_seen_at = CURRENT_TIMESTAMP,
                                  last_seen_severity = EXCLUDED.last_seen_severity,
                                  last_seen_source = EXCLUDED.last_seen_source,
                                  updated_at = CURRENT_TIMESTAMP
                    """)) {
                ps.setInt(1, userId);
                ps.setString(2, notification.notificationKey());
                ps.setString(3, notification.severity().name());
                ps.setString(4, notification.source().name());
                ps.executeUpdate();
            }
        }
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS notification_user_state (
                        user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                        notification_key TEXT NOT NULL,
                        read_at TIMESTAMPTZ,
                        snoozed_until TIMESTAMPTZ,
                        dismissed_at TIMESTAMPTZ,
                        dismissed_until TIMESTAMPTZ,
                        last_seen_at TIMESTAMPTZ,
                        last_seen_severity TEXT,
                        last_seen_source TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (user_id, notification_key)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS snoozed_until TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS dismissed_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS dismissed_until TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS last_seen_severity TEXT");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS last_seen_source TEXT");
            stmt.executeUpdate("ALTER TABLE notification_user_state ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS notification_user_state_snoozed_idx ON notification_user_state(user_id, snoozed_until)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS notification_user_state_dismissed_idx ON notification_user_state(user_id, dismissed_until)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS notification_user_state_updated_idx ON notification_user_state(updated_at DESC)");
        }
    }

    private static void collectInventory(Connection conn, List<AppNotification> notifications) throws SQLException {
        if (PermissionManager.hasPermission("INVENTORY_STOCK_NOTIFICATIONS") && hasTable(conn, "products") && hasTable(conn, "inventory")) {
            String sql = """
                    SELECT p.product_id, p.name, COALESCE(i.location_id, 0) AS location_id,
                           COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand,
                           COALESCE(i.reorder_level, 0) AS reorder_level
                    FROM products p
                    JOIN inventory i ON i.product_id = p.product_id
                    WHERE p.is_active = TRUE
                      AND COALESCE(p.product_type, 'INVENTORY') = 'INVENTORY'
                      AND COALESCE(i.reorder_level, 0) > 0
                      AND COALESCE(i.quantity_on_hand, 0) <= COALESCE(i.reorder_level, 0)
                      AND (? IS NULL OR i.location_id = ?)
                    ORDER BY i.quantity_on_hand, p.name
                    LIMIT 50
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bindLocation(ps, 1);
                bindLocation(ps, 2);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int qty = rs.getInt("quantity_on_hand");
                        int reorder = rs.getInt("reorder_level");
                        boolean out = qty <= 0;
                        notifications.add(notification(
                                (out ? "OUT_STOCK_PRODUCT:" : "LOW_STOCK_PRODUCT:")
                                        + rs.getInt("location_id") + ":" + rs.getInt("product_id"),
                                out ? AppNotification.Severity.URGENT : AppNotification.Severity.WARNING,
                                AppNotification.Source.INVENTORY,
                                out ? "Product out of stock" : "Product below reorder level",
                                rs.getString("name") + " has " + qty + " on hand. Reorder level: " + reorder + ".",
                                "ViewInventory"
                        ));
                    }
                }
            }
        }

        if (PermissionManager.hasPermission("INVENTORY_STOCK_NOTIFICATIONS") && hasTable(conn, "custom_order_items")) {
            String sql = """
                    SELECT custom_item_id::text AS id, item_name, '' AS variant_name,
                           COALESCE(quantity_on_hand, 0) AS quantity_on_hand,
                           COALESCE(reorder_level, 0) AS reorder_level
                    FROM custom_order_items
                    WHERE is_active = TRUE
                      AND COALESCE(has_variants, FALSE) = FALSE
                      AND COALESCE(reorder_level, 0) > 0
                      AND COALESCE(quantity_on_hand, 0) <= COALESCE(reorder_level, 0)
                    UNION ALL
                    SELECT v.custom_variant_id::text AS id, i.item_name, v.variant_name,
                           COALESCE(v.quantity_on_hand, 0) AS quantity_on_hand,
                           COALESCE(v.reorder_level, 0) AS reorder_level
                    FROM custom_order_item_variants v
                    JOIN custom_order_items i ON i.custom_item_id = v.custom_item_id
                    WHERE i.is_active = TRUE
                      AND v.is_active = TRUE
                      AND COALESCE(v.reorder_level, 0) > 0
                      AND COALESCE(v.quantity_on_hand, 0) <= COALESCE(v.reorder_level, 0)
                    ORDER BY item_name, variant_name
                    LIMIT 50
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal qty = zero(rs.getBigDecimal("quantity_on_hand"));
                    BigDecimal reorder = zero(rs.getBigDecimal("reorder_level"));
                    boolean out = qty.compareTo(BigDecimal.ZERO) <= 0;
                    String label = rs.getString("item_name")
                            + (blank(rs.getString("variant_name")) ? "" : " - " + rs.getString("variant_name"));
                    notifications.add(notification(
                            (out ? "OUT_STOCK_CUSTOM_ITEM:" : "LOW_STOCK_CUSTOM_ITEM:") + rs.getString("id"),
                            out ? AppNotification.Severity.URGENT : AppNotification.Severity.WARNING,
                            AppNotification.Source.INVENTORY,
                            out ? "Custom item out of stock" : "Custom item below reorder level",
                            label + " has " + qty.stripTrailingZeros().toPlainString()
                                    + " on hand. Reorder level: " + reorder.stripTrailingZeros().toPlainString() + ".",
                            "CustomOrderItems"
                    ));
                }
            }
        }
    }

    private static void collectOrders(Connection conn, List<AppNotification> notifications) throws SQLException {
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_WORK_NOTIFICATIONS") || !hasTable(conn, "custom_orders")) {
            return;
        }
        String sql = """
                SELECT custom_order_id, order_number, status, due_date, customer_name,
                       COALESCE(balance_due, 0) AS balance_due
                FROM custom_orders
                WHERE status NOT IN ('DELIVERED', 'CANCELLED')
                  AND (? IS NULL OR location_id = ?)
                  AND (
                      due_date <= CURRENT_DATE
                      OR status IN ('NEW', 'READY')
                      OR COALESCE(balance_due, 0) > 0
                      OR assigned_to_user_id IS NULL
                  )
                ORDER BY due_date NULLS LAST, created_at DESC
                LIMIT 75
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long orderId = rs.getLong("custom_order_id");
                    String order = safe(rs.getString("order_number"), "Order " + orderId);
                    String customer = safe(rs.getString("customer_name"), "Customer");
                    String status = safe(rs.getString("status"), "");
                    BigDecimal balance = zero(rs.getBigDecimal("balance_due"));
                    java.sql.Date dueDate = rs.getDate("due_date");
                    if (dueDate != null && dueDate.toLocalDate().isBefore(java.time.LocalDate.now())) {
                        notifications.add(notification("OVERDUE_ORDER:" + orderId, AppNotification.Severity.URGENT,
                                AppNotification.Source.ORDERS, "Custom order overdue",
                                order + " for " + customer + " was due " + dueDate + ".", "Orders"));
                    } else if (dueDate != null && dueDate.toLocalDate().isEqual(java.time.LocalDate.now())) {
                        notifications.add(notification("DUE_TODAY_ORDER:" + orderId, AppNotification.Severity.WARNING,
                                AppNotification.Source.ORDERS, "Custom order due today",
                                order + " for " + customer + " is due today.", "Orders"));
                    }
                    if ("READY".equalsIgnoreCase(status)) {
                        notifications.add(notification("READY_ORDER:" + orderId, AppNotification.Severity.WARNING,
                                AppNotification.Source.ORDERS, "Order ready for delivery",
                                order + " for " + customer + " is ready and not delivered.", "Orders"));
                    }
                    if ("NEW".equalsIgnoreCase(status)) {
                        notifications.add(notification("UNASSIGNED_ORDER:" + orderId, AppNotification.Severity.WARNING,
                                AppNotification.Source.ORDERS, "New order needs assignment",
                                order + " for " + customer + " is still new.", "OrdersManagerDashboard"));
                    }
                    if (balance.compareTo(BigDecimal.ZERO) > 0) {
                        notifications.add(notification("ORDER_BALANCE_DUE:" + orderId, AppNotification.Severity.WARNING,
                                AppNotification.Source.ORDERS, "Order has a balance due",
                                order + " still has a balance due of " + balance + ".", "Orders"));
                    }
                }
            }
        }
    }

    private static void collectExceptions(Connection conn, List<AppNotification> notifications) throws SQLException {
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS") || !hasTable(conn, "custom_order_payments")) {
            return;
        }
        String sql = """
                SELECT p.custom_order_payment_id, co.order_number, COALESCE(p.payment_amount, 0) AS amount
                FROM custom_order_payments p
                JOIN custom_orders co ON co.custom_order_id = p.custom_order_id
                WHERE p.payment_action = 'REFUND'
                  AND p.created_at >= CURRENT_TIMESTAMP - INTERVAL '1 day'
                  AND (? IS NULL OR co.location_id = ?)
                ORDER BY p.created_at DESC
                LIMIT 20
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLocation(ps, 1);
            bindLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notifications.add(notification("RECENT_REFUND:" + rs.getLong("custom_order_payment_id"),
                            AppNotification.Severity.WARNING, AppNotification.Source.EXCEPTIONS,
                            "Recent custom-order refund",
                            rs.getString("order_number") + " had a refund for " + zero(rs.getBigDecimal("amount")) + ".",
                            "OrdersManagerDashboard"));
                }
            }
        }
    }

    private static void collectSync(Connection conn, List<AppNotification> notifications) throws SQLException {
        if (!PermissionManager.hasPermission("SYNC_NOTIFICATIONS") || !hasTable(conn, "sync_outbox")) {
            return;
        }
        SyncWorker.SyncStatus status = SyncWorker.latestStatus();
        if (!status.cloudReachable()) {
            notifications.add(notification("SYNC_CLOUD_OFFLINE", AppNotification.Severity.URGENT,
                    AppNotification.Source.SYNC, "Cloud sync is offline",
                    safe(status.lastError(), "The last sync check could not reach the cloud database."), "SyncStatus"));
        }
        if (status.failedCount() > 0) {
            notifications.add(notification("SYNC_FAILED_EVENTS", AppNotification.Severity.URGENT,
                    AppNotification.Source.SYNC, "Sync has failed events",
                    status.failedCount() + " sync event(s) need attention.", "SyncStatus"));
        }
        if (status.conflictCount() > 0) {
            notifications.add(notification("SYNC_OPEN_CONFLICTS", AppNotification.Severity.URGENT,
                    AppNotification.Source.SYNC, "Sync conflicts need review",
                    status.conflictCount() + " open conflict(s) are waiting.", "SyncStatus"));
        }
        if (status.pendingCount() >= SYNC_PENDING_THRESHOLD) {
            notifications.add(notification("SYNC_PENDING_BACKLOG", AppNotification.Severity.WARNING,
                    AppNotification.Source.SYNC, "Sync backlog is building",
                    status.pendingCount() + " pending sync event(s) are queued.", "SyncStatus"));
        }
    }

    private static void collectCashDrawer(Connection conn, List<AppNotification> notifications) {
        if (!PermissionManager.hasPermission("BALANCE_DRAWER")) {
            return;
        }
        try {
            CashDrawerContext drawer = CashDrawerService.resolveCurrentDrawer(conn);
            if (drawer.isAssigned() && !drawer.hasActiveSession()) {
                notifications.add(notification("CASH_DRAWER_NOT_STARTED:" + drawer.cashDrawerId(),
                        AppNotification.Severity.URGENT, AppNotification.Source.CASH_DRAWER,
                        "Cash drawer is not started",
                        "Start " + drawer.drawerName() + " before taking cash.", "BalanceDraw"));
            }
        } catch (Exception ignored) {
        }
    }

    private static void collectDevices(Connection conn, List<AppNotification> notifications) throws SQLException {
        if (!hasTable(conn, "devices")) {
            return;
        }
        if (PermissionManager.hasPermission("DEVICE_MANAGEMENT")) {
            String sql = """
                    SELECT device_id::text AS device_id, COALESCE(device_name, hostname, installation_id, device_id::text) AS label
                    FROM devices
                    WHERE COALESCE(is_approved, FALSE) = FALSE
                      AND COALESCE(is_blocked, FALSE) = FALSE
                    ORDER BY last_seen DESC NULLS LAST
                    LIMIT 25
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notifications.add(notification("DEVICE_PENDING_APPROVAL:" + rs.getString("device_id"),
                            AppNotification.Severity.URGENT, AppNotification.Source.DEVICES,
                            "Device needs approval",
                            rs.getString("label") + " is waiting for device approval.", "DeviceManagement"));
                }
            }
        }
        Integer userId = SessionManager.getCurrentUserId();
        if (userId != null) {
            String sql = """
                    SELECT device_id::text AS device_id,
                           COALESCE(device_name, hostname, installation_id, device_id::text) AS label,
                           COALESCE(is_approved, FALSE) AS is_approved,
                           COALESCE(is_blocked, FALSE) AS is_blocked,
                           updated_at
                    FROM devices
                    WHERE last_login_user_id = ?
                      AND (COALESCE(is_approved, FALSE) = TRUE OR COALESCE(is_blocked, FALSE) = TRUE)
                      AND updated_at > first_seen + INTERVAL '1 second'
                    ORDER BY updated_at DESC
                    LIMIT 25
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp updatedAt = rs.getTimestamp("updated_at");
                        String version = updatedAt == null ? "unknown" : String.valueOf(updatedAt.getTime());
                        boolean blocked = rs.getBoolean("is_blocked");
                        notifications.add(notification("DEVICE_APPROVAL_UPDATE:" + rs.getString("device_id") + ":" + version,
                                blocked ? AppNotification.Severity.URGENT : AppNotification.Severity.WARNING,
                                AppNotification.Source.DEVICES,
                                blocked ? "Your device was blocked" : "Your device was approved",
                                rs.getString("label") + (blocked ? " was blocked." : " was approved for SmartStock."),
                                "DeviceManagement"));
                    }
                }
            }
        }
    }

    private static void collectMaintenance(Connection conn, List<AppNotification> notifications) throws SQLException {
        if (!PermissionManager.hasPermission("MAINTENANCE_MANAGEMENT")
                && !PermissionManager.hasPermission("MAINTENANCE_TECHNICIAN")
                && !PermissionManager.hasPermission("PARTS_MANAGEMENT")) {
            return;
        }
        if (PermissionManager.hasPermission("PARTS_MANAGEMENT") && hasTable(conn, "maintenance_parts")) {
            String sql = """
                    SELECT part_id, part_name, quantity_on_hand, reorder_point
                    FROM maintenance_parts
                    WHERE is_active = TRUE
                      AND COALESCE(reorder_point, 0) > 0
                      AND COALESCE(quantity_on_hand, 0) <= COALESCE(reorder_point, 0)
                    ORDER BY quantity_on_hand, part_name
                    LIMIT 25
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal qty = zero(rs.getBigDecimal("quantity_on_hand"));
                    notifications.add(notification("MAINTENANCE_PART_REORDER:" + rs.getLong("part_id"),
                            qty.compareTo(BigDecimal.ZERO) <= 0 ? AppNotification.Severity.URGENT : AppNotification.Severity.WARNING,
                            AppNotification.Source.MAINTENANCE, "Maintenance part needs reorder",
                            rs.getString("part_name") + " has " + qty + " on hand.", "MaintenanceManagement"));
                }
            }
        }
        if (hasTable(conn, "maintenance_tickets") && PermissionManager.hasPermission("MAINTENANCE_TECHNICIAN")) {
            String sql = """
                    SELECT ticket_id, priority, status, problem_summary
                    FROM maintenance_tickets
                    WHERE status IN ('OPEN', 'IN_PROGRESS', 'WAITING_PARTS')
                    ORDER BY opened_at DESC
                    LIMIT 25
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean urgent = "URGENT".equalsIgnoreCase(rs.getString("priority"));
                    notifications.add(notification("MAINTENANCE_TICKET:" + rs.getLong("ticket_id"),
                            urgent ? AppNotification.Severity.URGENT : AppNotification.Severity.WARNING,
                            AppNotification.Source.MAINTENANCE, "Open maintenance ticket",
                            rs.getString("priority") + " / " + rs.getString("status") + ": "
                                    + safe(rs.getString("problem_summary"), "A maintenance ticket is open."),
                            "MaintenanceManagement"));
                }
            }
        }
        Integer userId = SessionManager.getCurrentUserId();
        if (hasTable(conn, "maintenance_tickets") && userId != null) {
            String sql = """
                    SELECT ticket_id, priority, status, problem_summary, resolution_summary, updated_at
                    FROM maintenance_tickets
                    WHERE opened_by_user_id = ?
                      AND updated_at > opened_at + INTERVAL '1 second'
                    ORDER BY updated_at DESC
                    LIMIT 25
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp updatedAt = rs.getTimestamp("updated_at");
                        String version = updatedAt == null ? "unknown" : String.valueOf(updatedAt.getTime());
                        String status = safe(rs.getString("status"), "UPDATED");
                        boolean urgent = "RESOLVED".equalsIgnoreCase(status) || "URGENT".equalsIgnoreCase(rs.getString("priority"));
                        String detail = safe(rs.getString("resolution_summary"), rs.getString("problem_summary"));
                        notifications.add(notification("MAINTENANCE_TICKET_CREATOR_UPDATE:" + rs.getLong("ticket_id") + ":" + version,
                                urgent ? AppNotification.Severity.URGENT : AppNotification.Severity.WARNING,
                                AppNotification.Source.MAINTENANCE, "Your maintenance ticket was updated",
                                "Status: " + status + ". " + safe(detail, "Open the ticket for details."),
                                "MaintenanceManagement"));
                    }
                }
            }
        }
    }

    private static void applyState(Connection conn, int userId, List<AppNotification> notifications) throws SQLException {
        if (notifications.isEmpty()) {
            return;
        }
        Map<String, State> states = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT notification_key, read_at, snoozed_until, dismissed_at, dismissed_until
                FROM notification_user_state
                WHERE user_id = ?
                """)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    states.put(rs.getString("notification_key"),
                            new State(rs.getTimestamp("read_at"),
                                    rs.getTimestamp("snoozed_until"),
                                    rs.getTimestamp("dismissed_at"),
                                    rs.getTimestamp("dismissed_until")));
                }
            }
        }
        for (int i = 0; i < notifications.size(); i++) {
            AppNotification notification = notifications.get(i);
            State state = states.get(notification.notificationKey());
            if (state != null) {
                notifications.set(i, new AppNotification(
                        notification.notificationKey(),
                        notification.severity(),
                        notification.source(),
                        notification.title(),
                        notification.message(),
                        notification.actionTarget(),
                        notification.createdAt(),
                        state.readAt(),
                        state.snoozedUntil(),
                        state.dismissedAt(),
                        state.dismissedUntil()
                ));
            }
        }
    }

    private static AppNotification notification(String key, AppNotification.Severity severity,
                                                AppNotification.Source source, String title,
                                                String message, String actionTarget) {
        return new AppNotification(key, severity, source, title, message, actionTarget,
                new Timestamp(System.currentTimeMillis()), null, null, null, null);
    }

    private static boolean hasTable(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (ResultSet rs = metaData.getTables(null, "public", tableName, new String[]{"TABLE", "VIEW"})) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE", "VIEW"})) {
            return rs.next();
        }
    }

    private static void bindLocation(PreparedStatement ps, int index) throws SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, locationId);
        }
    }

    private static int severityRank(AppNotification.Severity severity) {
        return switch (severity) {
            case URGENT -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record NotificationSummary(int unreadCount, int urgentCount) {
        public String label() {
            if (urgentCount > 0) {
                return "Notifications (" + urgentCount + " urgent)";
            }
            if (unreadCount > 0) {
                return "Notifications (" + unreadCount + ")";
            }
            return "Notifications";
        }
    }

    private record State(Timestamp readAt, Timestamp snoozedUntil, Timestamp dismissedAt, Timestamp dismissedUntil) {
    }

    @FunctionalInterface
    private interface NotificationCollector {
        void collect() throws Exception;
    }
}
