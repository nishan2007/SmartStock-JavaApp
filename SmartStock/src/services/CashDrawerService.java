package services;

import data.DatabaseConfig;
import data.DatabaseMode;
import managers.SessionManager;
import models.CashDrawer;
import models.CashDrawerAssignment;
import models.CashDrawerContext;
import models.CashDrawerHandover;
import models.CashDrawerSession;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CashDrawerService {
    public static final int[] FLOAT_DENOMINATIONS = {5000, 2000, 1000, 500, 100, 50, 20};
    public static final Map<Integer, Integer> DEFAULT_FLOAT_MIX = Map.of(
            1000, 8,
            500, 10,
            100, 50,
            20, 100
    );

    private static volatile boolean schemaEnsured;

    private CashDrawerService() {
    }

    public static CashDrawerContext resolveCurrentDrawer(Connection conn) throws SQLException {
        ensureSchema(conn);
        Integer locationId = SessionManager.getCurrentLocationId();
        String deviceId = DeviceContextService.currentDeviceId();
        if (locationId == null || deviceId == null || deviceId.isBlank()) {
            return new CashDrawerContext(null, null);
        }
        return resolveDrawerForDevice(conn, locationId, deviceId);
    }

    public static CashDrawerContext resolveDrawerForDevice(Connection conn, int locationId, String deviceId) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT cd.cash_drawer_id, cd.drawer_name, cds.cash_drawer_session_id
                FROM cash_drawer_device_assignments cdda
                JOIN cash_drawers cd ON cd.cash_drawer_id = cdda.cash_drawer_id
                LEFT JOIN cash_drawer_sessions cds ON cds.cash_drawer_id = cd.cash_drawer_id
                    AND cds.location_id = cdda.location_id
                    AND cds.device_id = cdda.device_id
                    AND cds.status = 'OPEN'
                WHERE cdda.location_id = ?
                  AND cdda.device_id = ?::uuid
                  AND cdda.is_active = TRUE
                  AND cd.is_active = TRUE
                ORDER BY cdda.assigned_at DESC, cds.opened_at DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Long sessionId = nullableLong(rs, "cash_drawer_session_id");
                    return new CashDrawerContext(rs.getLong("cash_drawer_id"), rs.getString("drawer_name"), sessionId);
                }
            }
        }
        return new CashDrawerContext(null, null);
    }

    public static CashDrawerContext requireActiveCashSession(Connection conn) throws SQLException {
        CashDrawerContext drawer = resolveCurrentDrawer(conn);
        if (!drawer.isAssigned()) {
            throw new SQLException("This device is not assigned to an active cash drawer for the selected store. Open Company Preferences > Cash Drawer Manager and assign this device before taking cash.");
        }
        if (!drawer.hasActiveSession()) {
            throw new SQLException("No active draw session is open for " + drawer.drawerName() + ". Open Operations > Balance Draw and start the draw before taking cash.");
        }
        return drawer;
    }

    public static CashDrawerContext ensureDefaultDrawerForCurrentDevice(Connection conn) throws SQLException {
        ensureSchema(conn);
        Integer locationId = SessionManager.getCurrentLocationId();
        String deviceId = DeviceContextService.currentDeviceId();
        if (locationId == null) {
            throw new SQLException("No store is selected for this session.");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new SQLException("This register does not have an approved device ID.");
        }

        CashDrawerContext existing = resolveDrawerForDevice(conn, locationId, deviceId);
        if (existing.isAssigned()) {
            return existing;
        }

        List<CashDrawer> activeDrawers = listDrawers(conn, locationId, false);
        long drawerId;
        if (activeDrawers.isEmpty()) {
            drawerId = saveDrawer(
                    conn,
                    null,
                    locationId,
                    "Main Drawer",
                    "Auto-created for this register.",
                    floatMixTotal(DEFAULT_FLOAT_MIX),
                    DEFAULT_FLOAT_MIX,
                    true,
                    SessionManager.getCurrentUserId()
            );
        } else if (activeDrawers.size() == 1) {
            drawerId = activeDrawers.get(0).getCashDrawerId();
        } else {
            throw new SQLException("This store has multiple active drawers. Open Company Preferences > Cash Drawer Manager and assign this device to one drawer.");
        }

        assignDevice(conn, drawerId, locationId, deviceId, SessionManager.getCurrentUserId(), "Auto-assigned by Balance Draw.");
        return resolveDrawerForDevice(conn, locationId, deviceId);
    }

    public static List<CashDrawer> listDrawers(Connection conn, Integer locationId, boolean includeInactive) throws SQLException {
        ensureSchema(conn);
        StringBuilder sql = new StringBuilder("""
                SELECT cd.cash_drawer_id,
                       cd.location_id,
                       COALESCE(l.name, '') AS location_name,
                       cd.drawer_name,
                       COALESCE(cd.description, '') AS description,
                       COALESCE(cd.starting_cash_amount, 20000.00) AS starting_cash_amount,
                       COALESCE(cd.float_mix, ?::jsonb)::text AS float_mix,
                       cd.is_active,
                       COUNT(cdda.assignment_id) FILTER (WHERE cdda.is_active = TRUE) AS active_device_count
                FROM cash_drawers cd
                LEFT JOIN locations l ON l.location_id = cd.location_id
                LEFT JOIN cash_drawer_device_assignments cdda ON cdda.cash_drawer_id = cd.cash_drawer_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (locationId != null) {
            sql.append(" AND cd.location_id = ? ");
            params.add(locationId);
        }
        if (!includeInactive) {
            sql.append(" AND cd.is_active = TRUE ");
        }
        sql.append("""
                GROUP BY cd.cash_drawer_id, cd.location_id, l.name, cd.drawer_name, cd.description, cd.starting_cash_amount, cd.float_mix, cd.is_active
                ORDER BY COALESCE(l.name, ''), cd.drawer_name
                """);

        List<CashDrawer> drawers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, floatMixToJson(DEFAULT_FLOAT_MIX));
            bindParams(ps, params, 2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    drawers.add(new CashDrawer(
                            rs.getLong("cash_drawer_id"),
                            rs.getInt("location_id"),
                            rs.getString("location_name"),
                            rs.getString("drawer_name"),
                            rs.getString("description"),
                            defaultZero(rs.getBigDecimal("starting_cash_amount")),
                            parseFloatMix(rs.getString("float_mix")),
                            rs.getBoolean("is_active"),
                            rs.getInt("active_device_count")
                    ));
                }
            }
        }
        return drawers;
    }

    public static Map<Integer, Integer> getDrawerFloatMix(Connection conn, long drawerId) throws SQLException {
        ensureSchema(conn);
        String sql = "SELECT COALESCE(float_mix, ?::jsonb)::text AS float_mix FROM cash_drawers WHERE cash_drawer_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, floatMixToJson(DEFAULT_FLOAT_MIX));
            ps.setLong(2, drawerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return parseFloatMix(rs.getString("float_mix"));
                }
            }
        }
        return DEFAULT_FLOAT_MIX;
    }

    public static List<CashDrawerAssignment> listAssignments(Connection conn, Integer locationId, Long drawerId) throws SQLException {
        ensureSchema(conn);
        StringBuilder sql = new StringBuilder("""
                SELECT cdda.assignment_id,
                       cdda.cash_drawer_id,
                       cd.drawer_name,
                       cdda.location_id,
                       COALESCE(l.name, '') AS location_name,
                       cdda.device_id::text AS device_id,
                       COALESCE(d.device_name, '') AS device_name,
                       COALESCE(d.hostname, '') AS hostname,
                       cdda.is_active,
                       cdda.assigned_at,
                       COALESCE(u.full_name, u.username, '') AS assigned_by_name,
                       COALESCE(cdda.notes, '') AS notes
                FROM cash_drawer_device_assignments cdda
                JOIN cash_drawers cd ON cd.cash_drawer_id = cdda.cash_drawer_id
                LEFT JOIN locations l ON l.location_id = cdda.location_id
                LEFT JOIN devices d ON d.device_id = cdda.device_id
                LEFT JOIN users u ON u.user_id = cdda.assigned_by_user_id
                WHERE cdda.is_active = TRUE
                """);
        List<Object> params = new ArrayList<>();
        if (locationId != null) {
            sql.append(" AND cdda.location_id = ? ");
            params.add(locationId);
        }
        if (drawerId != null) {
            sql.append(" AND cdda.cash_drawer_id = ? ");
            params.add(drawerId);
        }
        sql.append(" ORDER BY cd.drawer_name, COALESCE(d.device_name, d.hostname, cdda.device_id::text) ");

        List<CashDrawerAssignment> assignments = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assignments.add(new CashDrawerAssignment(
                            rs.getLong("assignment_id"),
                            rs.getLong("cash_drawer_id"),
                            rs.getString("drawer_name"),
                            rs.getInt("location_id"),
                            rs.getString("location_name"),
                            rs.getString("device_id"),
                            rs.getString("device_name"),
                            rs.getString("hostname"),
                            rs.getBoolean("is_active"),
                            rs.getTimestamp("assigned_at"),
                            rs.getString("assigned_by_name"),
                            rs.getString("notes")
                    ));
                }
            }
        }
        return assignments;
    }

    public static long saveDrawer(Connection conn, Long drawerId, int locationId, String drawerName,
                                  String description, BigDecimal startingCashAmount, Map<Integer, Integer> floatMix,
                                  boolean active, Integer userId) throws SQLException {
        ensureSchema(conn);
        if (drawerName == null || drawerName.trim().isEmpty()) {
            throw new SQLException("Drawer name is required.");
        }
        String cleanName = drawerName.trim();
        String cleanDescription = blankToNull(description);
        BigDecimal cleanStartingCash = defaultZero(startingCashAmount);
        Map<Integer, Integer> cleanFloatMix = cleanFloatMix(floatMix);
        validateFloatMixTotal(cleanStartingCash, cleanFloatMix);
        if (drawerId == null) {
            String sql = """
                    INSERT INTO cash_drawers (
                        location_id, drawer_name, description, starting_cash_amount, float_mix, is_active, created_by_user_id, updated_by_user_id
                    )
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    RETURNING cash_drawer_id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                ps.setString(2, cleanName);
                ps.setString(3, cleanDescription);
                ps.setBigDecimal(4, cleanStartingCash);
                ps.setString(5, floatMixToJson(cleanFloatMix));
                ps.setBoolean(6, active);
                setNullableInteger(ps, 7, userId);
                setNullableInteger(ps, 8, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("cash_drawer_id");
                    }
                }
            }
            throw new SQLException("Failed to create cash drawer.");
        }

        String sql = """
                UPDATE cash_drawers
                SET location_id = ?,
                    drawer_name = ?,
                    description = ?,
                    starting_cash_amount = ?,
                    float_mix = ?::jsonb,
                    is_active = ?,
                    updated_at = CURRENT_TIMESTAMP,
                    updated_by_user_id = ?
                WHERE cash_drawer_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, cleanName);
            ps.setString(3, cleanDescription);
            ps.setBigDecimal(4, cleanStartingCash);
            ps.setString(5, floatMixToJson(cleanFloatMix));
            ps.setBoolean(6, active);
            setNullableInteger(ps, 7, userId);
            ps.setLong(8, drawerId);
            ps.executeUpdate();
        }
        if (!active) {
            unassignDrawerDevices(conn, drawerId, userId);
        }
        return drawerId;
    }

    public static CashDrawerSession getActiveSessionForCurrentDevice(Connection conn) throws SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        String deviceId = DeviceContextService.currentDeviceId();
        if (locationId == null || deviceId == null || deviceId.isBlank()) {
            return null;
        }
        return getActiveSessionForDevice(conn, locationId, deviceId);
    }

    public static CashDrawerSession getActiveSessionForDevice(Connection conn, int locationId, String deviceId) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT cds.*
                FROM cash_drawer_sessions cds
                WHERE cds.location_id = ?
                  AND cds.device_id = ?::uuid
                  AND cds.status = 'OPEN'
                ORDER BY cds.opened_at DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSession(rs);
                }
            }
        }
        return null;
    }

    public static CashDrawerSession openSessionForCurrentDevice(Connection conn, String notes) throws SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        String deviceId = DeviceContextService.currentDeviceId();
        if (locationId == null) {
            throw new SQLException("No store is selected for this session.");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new SQLException("This register does not have a device ID.");
        }
        return openSessionForDevice(conn,locationId,deviceId,DeviceContextService.currentDeviceName(),
                SessionManager.getCurrentUserId(),SessionManager.getCurrentUserDisplayName(),notes);
    }

    public static CashDrawerSession openSessionForDevice(Connection conn,int locationId,String deviceId,String deviceName,
                                                         Integer userId,String userName,String notes)throws SQLException{
        ensureSchema(conn);
        CashDrawerContext drawer = resolveDrawerForDevice(conn, locationId, deviceId);
        if (!drawer.isAssigned()) {
            throw new SQLException("This device is not assigned to an active cash drawer.");
        }
        if (drawer.hasActiveSession()) {
            CashDrawerSession existing = getActiveSessionForDevice(conn,locationId,deviceId);
            if (existing != null) {
                return existing;
            }
        }

        String sql = """
                INSERT INTO cash_drawer_sessions (
                    cash_drawer_id, location_id, device_id, drawer_name, device_name,
                    opening_cash, status, opened_by_user_id, opened_by_name,
                    main_cashier_user_id, main_cashier_name,
                    current_cashier_user_id, current_cashier_name,
                    opening_notes
                )
                SELECT cd.cash_drawer_id, cd.location_id, ?::uuid, cd.drawer_name, ?,
                       COALESCE(cd.starting_cash_amount, 20000.00), 'OPEN', ?, ?, ?, ?, ?, ?, ?
                FROM cash_drawers cd
                WHERE cd.cash_drawer_id = ?
                RETURNING *
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ps.setString(2, blankToNull(deviceName));
            setNullableInteger(ps, 3, userId);
            ps.setString(4, blankToNull(userName));
            setNullableInteger(ps, 5, userId);
            ps.setString(6, blankToNull(userName));
            setNullableInteger(ps, 7, userId);
            ps.setString(8, blankToNull(userName));
            ps.setString(9, blankToNull(notes));
            ps.setLong(10, drawer.cashDrawerId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CashDrawerSession opened=mapSession(rs);
                    SyncOutboxService.recordEvent(conn,"CASH_DRAWER_SESSION_OPENED",Map.of(
                            "cash_drawer_session_id",opened.sessionId(),"cash_drawer_id",opened.cashDrawerId(),"location_id",opened.locationId(),
                            "device_id",String.valueOf(opened.deviceId()),"opened_by_user_id",userId==null?"":userId,"opening_cash",opened.openingCash()));
                    return opened;
                }
            }
        }
        throw new SQLException("Failed to start draw session.");
    }

    public static CashDrawerHandover recordHandover(Connection conn, long sessionId, BigDecimal countedCash, String notes) throws SQLException {
        return recordHandover(conn,sessionId,countedCash,notes,SessionManager.getCurrentUserId(),SessionManager.getCurrentUserDisplayName());
    }

    public static CashDrawerHandover recordHandover(Connection conn,long sessionId,BigDecimal countedCash,String notes,Integer userId,String userName)throws SQLException{
        ensureSchema(conn);
        CashDrawerSession session = getSessionForUpdate(conn, sessionId);
        if (session == null) {
            throw new SQLException("Draw session was not found.");
        }
        if (!session.isOpen()) {
            throw new SQLException("This draw session is already closed.");
        }

        BigDecimal expectedCash = calculateExpectedCash(conn, sessionId);
        BigDecimal cleanCountedCash = defaultZero(countedCash);
        BigDecimal variance = cleanCountedCash.subtract(expectedCash);
        String sql = """
                INSERT INTO cash_drawer_handovers (
                    cash_drawer_session_id, cash_drawer_id, location_id, device_id,
                    from_user_id, from_user_name, to_user_id, to_user_name,
                    expected_cash, counted_cash, variance, notes
                )
                VALUES (?, ?, ?, ?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING *
                """;
        CashDrawerHandover handover;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, session.sessionId());
            ps.setLong(2, session.cashDrawerId());
            ps.setInt(3, session.locationId());
            ps.setString(4, session.deviceId());
            setNullableInteger(ps, 5, session.currentCashierUserId());
            ps.setString(6, blankToNull(session.currentCashierName()));
            setNullableInteger(ps, 7, userId);
            ps.setString(8, blankToNull(userName));
            ps.setBigDecimal(9, expectedCash);
            ps.setBigDecimal(10, cleanCountedCash);
            ps.setBigDecimal(11, variance);
            ps.setString(12, blankToNull(notes));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to record draw handover.");
                }
                handover = mapHandover(rs);
            }
        }

        String updateSql = """
                UPDATE cash_drawer_sessions
                SET current_cashier_user_id = ?,
                    current_cashier_name = ?
                WHERE cash_drawer_session_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            setNullableInteger(ps, 1, userId);
            ps.setString(2, blankToNull(userName));
            ps.setLong(3, sessionId);
            ps.executeUpdate();
        }
        SyncOutboxService.recordEvent(conn, "CASH_DRAWER_HANDOVER_CREATED", Map.of(
                "cash_drawer_handover_id", handover.handoverId(),
                "cash_drawer_session_id", handover.sessionId(),
                "cash_drawer_id", session.cashDrawerId(),
                "location_id", session.locationId(),
                "from_user_name", String.valueOf(handover.fromUserName()),
                "to_user_name", String.valueOf(handover.toUserName()),
                "expected_cash", handover.expectedCash(),
                "counted_cash", handover.countedCash(),
                "variance", handover.variance()
        ));
        return handover;
    }

    public static CashDrawerSession closeSession(Connection conn, long sessionId, BigDecimal countedCash, String notes) throws SQLException {
        return closeSession(conn,sessionId,countedCash,notes,SessionManager.getCurrentUserId(),SessionManager.getCurrentUserDisplayName());
    }

    public static CashDrawerSession closeSession(Connection conn,long sessionId,BigDecimal countedCash,String notes,Integer userId,String userName)throws SQLException{
        ensureSchema(conn);
        CashDrawerSession session = getSessionForUpdate(conn, sessionId);
        if (session == null) {
            throw new SQLException("Draw session was not found.");
        }
        if (!session.isOpen()) {
            throw new SQLException("This draw session is already closed.");
        }

        BigDecimal expectedCash = calculateExpectedCash(conn, sessionId);
        BigDecimal cleanCountedCash = defaultZero(countedCash);
        BigDecimal openingCash = defaultZero(session.openingCash());
        BigDecimal cashToRemove = cleanCountedCash.subtract(openingCash);
        BigDecimal variance = cleanCountedCash.subtract(expectedCash);
        String closingReport = buildClosingReport(conn, session, expectedCash, cleanCountedCash, cashToRemove, variance);

        String sql = """
                UPDATE cash_drawer_sessions
                SET expected_cash = ?,
                    counted_cash = ?,
                    cash_to_remove = ?,
                    variance = ?,
                    status = 'CLOSED',
                    closed_at = CURRENT_TIMESTAMP,
                    closed_by_user_id = ?,
                    closed_by_name = ?,
                    balanced_by_user_id = ?,
                    balanced_by_name = ?,
                    current_cashier_user_id = ?,
                    current_cashier_name = ?,
                    closing_report = ?,
                    closing_notes = ?
                WHERE cash_drawer_session_id = ?
                RETURNING *
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, expectedCash);
            ps.setBigDecimal(2, cleanCountedCash);
            ps.setBigDecimal(3, cashToRemove);
            ps.setBigDecimal(4, variance);
            setNullableInteger(ps, 5, userId);
            ps.setString(6, blankToNull(userName));
            setNullableInteger(ps, 7, userId);
            ps.setString(8, blankToNull(userName));
            setNullableInteger(ps, 9, userId);
            ps.setString(10, blankToNull(userName));
            ps.setString(11, closingReport);
            ps.setString(12, blankToNull(notes));
            ps.setLong(13, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CashDrawerSession closed = mapSession(rs);
                    SyncOutboxService.recordEvent(conn, "CASH_DRAWER_SESSION_CLOSED", Map.of(
                            "cash_drawer_session_id", closed.sessionId(),
                            "cash_drawer_id", closed.cashDrawerId(),
                            "location_id", closed.locationId(),
                            "device_id", String.valueOf(closed.deviceId()),
                            "expected_cash", expectedCash,
                            "counted_cash", cleanCountedCash,
                            "cash_to_remove", cashToRemove,
                            "variance", variance,
                            "closed_by_user_id", userId == null ? "" : userId
                    ));
                    return closed;
                }
            }
        }
        throw new SQLException("Failed to close draw session.");
    }

    public static CashDrawerSession reviseClosedSessionCount(Connection conn, long sessionId, BigDecimal countedCash, String notes) throws SQLException {
        return reviseClosedSessionCount(conn,sessionId,countedCash,notes,SessionManager.getCurrentUserId(),SessionManager.getCurrentUserDisplayName());
    }

    public static CashDrawerSession reviseClosedSessionCount(Connection conn,long sessionId,BigDecimal countedCash,String notes,Integer userId,String userName)throws SQLException{
        ensureSchema(conn);
        CashDrawerSession session = getSessionForUpdate(conn, sessionId);
        if (session == null) {
            throw new SQLException("Draw session was not found.");
        }
        if (session.isOpen()) {
            throw new SQLException("Open draw sessions must be closed before they can be corrected.");
        }
        if (isCoveredBySubmittedBalanceSheet(conn, session)) {
            throw new SQLException("This draw is already included in a submitted balance sheet and cannot be edited.");
        }

        BigDecimal expectedCash = calculateExpectedCash(conn, sessionId);
        BigDecimal cleanCountedCash = defaultZero(countedCash);
        BigDecimal openingCash = defaultZero(session.openingCash());
        if (cleanCountedCash.compareTo(openingCash) < 0) {
            throw new SQLException("Corrected counted cash is below the required float of " + openingCash + ".");
        }
        BigDecimal cashToRemove = cleanCountedCash.subtract(openingCash);
        BigDecimal variance = cleanCountedCash.subtract(expectedCash);
        String closingReport = buildClosingReport(conn, session, expectedCash, cleanCountedCash, cashToRemove, variance);
        String correctionNotes = mergeCorrectionNotes(session.closingNotes(), notes);

        String sql = """
                UPDATE cash_drawer_sessions
                SET expected_cash = ?,
                    counted_cash = ?,
                    cash_to_remove = ?,
                    variance = ?,
                    balanced_by_user_id = ?,
                    balanced_by_name = ?,
                    closing_report = ?,
                    closing_notes = ?
                WHERE cash_drawer_session_id = ?
                RETURNING *
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, expectedCash);
            ps.setBigDecimal(2, cleanCountedCash);
            ps.setBigDecimal(3, cashToRemove);
            ps.setBigDecimal(4, variance);
            setNullableInteger(ps, 5, userId);
            ps.setString(6, blankToNull(userName));
            ps.setString(7, closingReport);
            ps.setString(8, blankToNull(correctionNotes));
            ps.setLong(9, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    CashDrawerSession revised = mapSession(rs);
                    SyncOutboxService.recordEvent(conn, "CASH_DRAWER_SESSION_CORRECTED", Map.of(
                            "cash_drawer_session_id", revised.sessionId(),
                            "cash_drawer_id", revised.cashDrawerId(),
                            "location_id", revised.locationId(),
                            "device_id", String.valueOf(revised.deviceId()),
                            "expected_cash", expectedCash,
                            "counted_cash", cleanCountedCash,
                            "cash_to_remove", cashToRemove,
                            "variance", variance,
                            "balanced_by_user_id", userId == null ? "" : userId
                    ));
                            return revised;
                        }
                    }
            }
        throw new SQLException("Failed to correct draw session.");
    }

    public static long recordChangeBasketUpdate(Connection conn,
                                                int locationId,
                                                String storeName,
                                                BigDecimal targetAmount,
                                                Map<Integer, Integer> denominationCounts,
                                                String notes,
                                                int userId,
                                                String userName,
                                                java.util.UUID deviceId,
                                                String deviceName) throws SQLException {
        ensureSchema(conn);
        Map<Integer, Integer> cleanCounts = cleanDenominationCounts(denominationCounts);
        BigDecimal cleanTarget = defaultZero(targetAmount);
        BigDecimal countedAmount = floatMixTotal(cleanCounts);
        BigDecimal variance = countedAmount.subtract(cleanTarget);
        String sql = """
                INSERT INTO change_basket_updates (
                    location_id,
                    store_name,
                    target_amount,
                    counted_amount,
                    variance,
                    denomination_counts,
                    updated_by_user_id,
                    updated_by_name,
                    device_id,
                    device_name,
                    notes
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::uuid, ?, ?)
                RETURNING change_basket_update_id
                """;
        long updateId;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setString(2, blankToNull(storeName));
            ps.setBigDecimal(3, cleanTarget);
            ps.setBigDecimal(4, countedAmount);
            ps.setBigDecimal(5, variance);
            ps.setString(6, denominationCountsToJson(cleanCounts));
            ps.setInt(7, userId);
            ps.setString(8, blankToNull(userName));
            ps.setObject(9, deviceId);
            ps.setString(10, blankToNull(deviceName));
            ps.setString(11, blankToNull(notes));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Failed to save change basket update.");
                }
                updateId = rs.getLong("change_basket_update_id");
            }
        }
        SyncOutboxService.recordEvent(conn, "CHANGE_BASKET_UPDATED", Map.of(
                "change_basket_update_id", updateId,
                "location_id", locationId,
                "target_amount", cleanTarget,
                "counted_amount", countedAmount,
                "variance", variance,
                "denomination_counts", denominationCountsToJson(cleanCounts)
        ));
        return updateId;
    }

    public static BigDecimal calculateExpectedCash(Connection conn, long sessionId) throws SQLException {
        ensureSchema(conn);
        CashDrawerSession session = getSession(conn, sessionId);
        if (session == null) {
            throw new SQLException("Draw session was not found.");
        }
        return defaultZero(session.openingCash()).add(calculateNetCashActivity(conn, sessionId));
    }

    public static BigDecimal calculateNetCashActivity(Connection conn, long sessionId) throws SQLException {
        ensureSchema(conn);
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(sum(conn, """
                SELECT COALESCE(SUM(amount_paid), 0)
                FROM sales
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                """, sessionId));
        total = total.subtract(sum(conn, """
                SELECT COALESCE(SUM(refund_amount), 0)
                FROM sale_returns
                WHERE cash_drawer_session_id = ?
                  AND refund_method = 'CASH'
                """, sessionId));
        total = total.subtract(sum(conn, """
                SELECT COALESCE(SUM(refund_amount),0) FROM cross_store_refund_requests
                WHERE cash_drawer_session_id=? AND refund_method='CASH'
                  AND status NOT IN ('REJECTED','CANCELLED')
                """,sessionId));
        total = total.add(sum(conn, """
                SELECT COALESCE(SUM(CASE
                    WHEN COALESCE(payment_action, 'PAYMENT') IN ('REFUND', 'REVERSAL') THEN -payment_amount
                    ELSE payment_amount
                END), 0)
                FROM custom_order_payments
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                  AND COALESCE(payment_reference, '') NOT LIKE 'Account payment transaction #%'
                """, sessionId));
        total = total.add(sum(conn, """
                SELECT COALESCE(SUM(CASE
                    WHEN COALESCE(payment_action, 'PAYMENT') IN ('REFUND', 'REVERSAL') THEN -payment_amount
                    ELSE payment_amount
                END), 0)
                FROM invoice_payments
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                  AND COALESCE(payment_reference, '') NOT LIKE 'Account payment transaction #%'
                """, sessionId));
        total = total.add(sum(conn, """
                SELECT COALESCE(SUM(ABS(amount)), 0)
                FROM customer_account_transactions
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                  AND transaction_type = 'PAYMENT'
                  AND custom_order_id IS NULL
                  AND invoice_id IS NULL
                """, sessionId));
        return total;
    }

    public static List<CashDrawerSession> listRecentSessions(Connection conn, Integer locationId, Long drawerId, boolean openOnly) throws SQLException {
        ensureSchema(conn);
        StringBuilder sql = new StringBuilder("SELECT * FROM cash_drawer_sessions WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (locationId != null) {
            sql.append(" AND location_id = ? ");
            params.add(locationId);
        }
        if (drawerId != null) {
            sql.append(" AND cash_drawer_id = ? ");
            params.add(drawerId);
        }
        if (openOnly) {
            sql.append(" AND status = 'OPEN' ");
        }
        sql.append(" ORDER BY opened_at DESC LIMIT 100");

        List<CashDrawerSession> sessions = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapSession(rs));
                }
            }
        }
        return sessions;
    }

    public static List<CashDrawerHandover> listHandovers(Connection conn, long sessionId) throws SQLException {
        ensureSchema(conn);
        String sql = """
                SELECT *
                FROM cash_drawer_handovers
                WHERE cash_drawer_session_id = ?
                ORDER BY handed_over_at ASC, cash_drawer_handover_id ASC
                """;
        List<CashDrawerHandover> handovers = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    handovers.add(mapHandover(rs));
                }
            }
        }
        return handovers;
    }

    public static List<String> listCashHandlers(Connection conn, long sessionId) throws SQLException {
        ensureSchema(conn);
        Set<String> names = new LinkedHashSet<>();
        CashDrawerSession session = getSession(conn, sessionId);
        if (session != null) {
            addName(names, session.mainCashierName());
            addName(names, session.currentCashierName());
            addName(names, session.balancedByName());
        }
        for (CashDrawerHandover handover : listHandovers(conn, sessionId)) {
            addName(names, handover.fromUserName());
            addName(names, handover.toUserName());
        }
        addNamesFromQuery(conn, names, """
                SELECT user_name AS name
                FROM sales
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                """, sessionId);
        addNamesFromQuery(conn, names, """
                SELECT user_name AS name
                FROM sale_returns
                WHERE cash_drawer_session_id = ?
                  AND refund_method = 'CASH'
                """, sessionId);
        addNamesFromQuery(conn, names, """
                SELECT taken_by_name AS name
                FROM custom_order_payments
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                  AND COALESCE(payment_reference, '') NOT LIKE 'Account payment transaction #%'
                """, sessionId);
        addNamesFromQuery(conn, names, """
                SELECT taken_by_name AS name
                FROM invoice_payments
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                  AND COALESCE(payment_reference, '') NOT LIKE 'Account payment transaction #%'
                """, sessionId);
        addNamesFromQuery(conn, names, """
                SELECT user_name AS name
                FROM customer_account_transactions
                WHERE cash_drawer_session_id = ?
                  AND payment_method = 'CASH'
                """, sessionId);
        return new ArrayList<>(names);
    }

    public static void assignDevice(Connection conn, long drawerId, int locationId, String deviceId,
                                    Integer userId, String notes) throws SQLException {
        if (deviceId == null || deviceId.isBlank()) {
            throw new SQLException("Select a device to assign.");
        }

        String drawerSql = "SELECT is_active FROM cash_drawers WHERE cash_drawer_id = ? AND location_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(drawerSql)) {
            ps.setLong(1, drawerId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Cash drawer does not belong to the selected store.");
                }
                if (!rs.getBoolean("is_active")) {
                    throw new SQLException("Cannot assign devices to an inactive cash drawer.");
                }
            }
        }

        UUID uuid = UUID.fromString(deviceId);
        unassignDevice(conn, locationId, deviceId, userId);

        String sql = """
                INSERT INTO cash_drawer_device_assignments (
                    cash_drawer_id, location_id, device_id, assigned_by_user_id, notes
                )
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, drawerId);
            ps.setInt(2, locationId);
            ps.setObject(3, uuid);
            setNullableInteger(ps, 4, userId);
            ps.setString(5, blankToNull(notes));
            ps.executeUpdate();
        }
    }

    public static void unassignAssignment(Connection conn, long assignmentId, Integer userId) throws SQLException {
        String sql = """
                UPDATE cash_drawer_device_assignments
                SET is_active = FALSE,
                    unassigned_at = CURRENT_TIMESTAMP,
                    unassigned_by_user_id = ?
                WHERE assignment_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, userId);
            ps.setLong(2, assignmentId);
            ps.executeUpdate();
        }
    }

    private static void unassignDrawerDevices(Connection conn, long drawerId, Integer userId) throws SQLException {
        String sql = """
                UPDATE cash_drawer_device_assignments
                SET is_active = FALSE,
                    unassigned_at = CURRENT_TIMESTAMP,
                    unassigned_by_user_id = ?
                WHERE cash_drawer_id = ?
                  AND is_active = TRUE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, userId);
            ps.setLong(2, drawerId);
            ps.executeUpdate();
        }
    }

    private static void unassignDevice(Connection conn, int locationId, String deviceId, Integer userId) throws SQLException {
        String sql = """
                UPDATE cash_drawer_device_assignments
                SET is_active = FALSE,
                    unassigned_at = CURRENT_TIMESTAMP,
                    unassigned_by_user_id = ?
                WHERE location_id = ?
                  AND device_id = ?::uuid
                  AND is_active = TRUE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, userId);
            ps.setInt(2, locationId);
            ps.setString(3, deviceId);
            ps.executeUpdate();
        }
    }

    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        bindParams(ps, params, 1);
    }

    private static void bindParams(PreparedStatement ps, List<Object> params, int startIndex) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(startIndex + i, params.get(i));
        }
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
    }

    private static CashDrawerSession getSession(Connection conn, long sessionId) throws SQLException {
        String sql = "SELECT * FROM cash_drawer_sessions WHERE cash_drawer_session_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSession(rs);
                }
            }
        }
        return null;
    }

    private static CashDrawerSession getSessionForUpdate(Connection conn, long sessionId) throws SQLException {
        String sql = "SELECT * FROM cash_drawer_sessions WHERE cash_drawer_session_id = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapSession(rs);
                }
            }
        }
        return null;
    }

    private static BigDecimal sum(Connection conn, String sql, long sessionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return defaultZero(rs.getBigDecimal(1));
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static void addNamesFromQuery(Connection conn, Set<String> names, String sql, long sessionId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    addName(names, rs.getString("name"));
                }
            }
        }
    }

    private static void addName(Set<String> names, String name) {
        if (name != null && !name.isBlank()) {
            names.add(name.trim());
        }
    }

    private static String mergeCorrectionNotes(String existingNotes, String correctionNotes) {
        String userName = defaultText(SessionManager.getCurrentUserDisplayName());
        String entry = "Correction by " + userName;
        if (correctionNotes != null && !correctionNotes.isBlank()) {
            entry += ": " + correctionNotes.trim();
        }
        if (existingNotes == null || existingNotes.isBlank()) {
            return entry;
        }
        return existingNotes.trim() + "\n" + entry;
    }

    private static boolean isCoveredBySubmittedBalanceSheet(Connection conn, CashDrawerSession session) throws SQLException {
        if (!tableExists(conn, "balance_sheet_submissions")) {
            return false;
        }
        String sql = """
                SELECT 1
                FROM balance_sheet_submissions
                WHERE (? IS NULL OR location_id = ?)
                  AND (COALESCE(?, ?) AT TIME ZONE COALESCE(NULLIF(store_timezone, ''), 'UTC'))::date
                      BETWEEN period_start AND period_end
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, session.locationId());
            ps.setInt(2, session.locationId());
            ps.setTimestamp(3, session.closedAt());
            ps.setTimestamp(4, session.openedAt());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String buildClosingReport(Connection conn,
                                             CashDrawerSession session,
                                             BigDecimal expectedCash,
                                             BigDecimal countedCash,
                                             BigDecimal cashToRemove,
                                             BigDecimal variance) throws SQLException {
        List<String> handlers = listCashHandlers(conn, session.sessionId());
        StringBuilder report = new StringBuilder();
        report.append("Cash Drawer Closeout\n");
        report.append("Drawer: ").append(session.drawerName()).append('\n');
        report.append("Main Cashier: ").append(defaultText(session.mainCashierName())).append('\n');
        report.append("Balanced By: ").append(defaultText(SessionManager.getCurrentUserDisplayName())).append('\n');
        report.append("Cash Handlers: ").append(handlers.isEmpty() ? "None" : String.join(", ", handlers)).append('\n');
        report.append("Opening Float: ").append(defaultZero(session.openingCash())).append('\n');
        report.append("Expected Cash: ").append(expectedCash).append('\n');
        report.append("Counted Cash: ").append(countedCash).append('\n');
        report.append("Cash To Remove: ").append(cashToRemove).append('\n');
        report.append("Variance: ").append(variance).append('\n');
        List<CashDrawerHandover> handovers = listHandovers(conn, session.sessionId());
        if (!handovers.isEmpty()) {
            report.append("Handovers:\n");
            for (CashDrawerHandover handover : handovers) {
                report.append("- ")
                        .append(defaultText(handover.fromUserName()))
                        .append(" to ")
                        .append(defaultText(handover.toUserName()))
                        .append(", expected=")
                        .append(handover.expectedCash())
                        .append(", counted=")
                        .append(handover.countedCash())
                        .append(", variance=")
                        .append(handover.variance())
                        .append('\n');
            }
        }
        return report.toString();
    }

    private static CashDrawerSession mapSession(ResultSet rs) throws SQLException {
        return new CashDrawerSession(
                rs.getLong("cash_drawer_session_id"),
                rs.getLong("cash_drawer_id"),
                rs.getInt("location_id"),
                rs.getString("device_id"),
                rs.getString("drawer_name"),
                rs.getString("device_name"),
                defaultZero(rs.getBigDecimal("opening_cash")),
                defaultZero(rs.getBigDecimal("expected_cash")),
                defaultZero(rs.getBigDecimal("counted_cash")),
                defaultZero(rs.getBigDecimal("cash_to_remove")),
                defaultZero(rs.getBigDecimal("variance")),
                rs.getString("status"),
                rs.getTimestamp("opened_at"),
                rs.getString("opened_by_name"),
                nullableInteger(rs, "main_cashier_user_id"),
                rs.getString("main_cashier_name"),
                nullableInteger(rs, "current_cashier_user_id"),
                rs.getString("current_cashier_name"),
                rs.getTimestamp("closed_at"),
                rs.getString("closed_by_name"),
                nullableInteger(rs, "balanced_by_user_id"),
                rs.getString("balanced_by_name"),
                rs.getString("opening_notes"),
                rs.getString("closing_notes"),
                rs.getString("closing_report")
        );
    }

    private static CashDrawerHandover mapHandover(ResultSet rs) throws SQLException {
        return new CashDrawerHandover(
                rs.getLong("cash_drawer_handover_id"),
                rs.getLong("cash_drawer_session_id"),
                rs.getString("from_user_name"),
                rs.getString("to_user_name"),
                defaultZero(rs.getBigDecimal("expected_cash")),
                defaultZero(rs.getBigDecimal("counted_cash")),
                defaultZero(rs.getBigDecimal("variance")),
                rs.getTimestamp("handed_over_at"),
                rs.getString("notes")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static BigDecimal floatMixTotal(Map<Integer, Integer> floatMix) {
        BigDecimal total = BigDecimal.ZERO;
        if (floatMix == null) {
            return total;
        }
        for (Map.Entry<Integer, Integer> entry : floatMix.entrySet()) {
            int denomination = entry.getKey() == null ? 0 : entry.getKey();
            int quantity = entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0);
            total = total.add(BigDecimal.valueOf((long) denomination * quantity));
        }
        return total;
    }

    private static void validateFloatMixTotal(BigDecimal startingCashAmount, Map<Integer, Integer> floatMix) throws SQLException {
        BigDecimal mixTotal = floatMixTotal(floatMix);
        if (mixTotal.compareTo(defaultZero(startingCashAmount)) != 0) {
            throw new SQLException("Float mix total (" + mixTotal + ") must equal starting cash amount (" + defaultZero(startingCashAmount) + ").");
        }
    }

    private static Map<Integer, Integer> cleanFloatMix(Map<Integer, Integer> floatMix) {
        Map<Integer, Integer> clean = new HashMap<>();
        Map<Integer, Integer> source = floatMix == null || floatMix.isEmpty() ? DEFAULT_FLOAT_MIX : floatMix;
        for (int denomination : FLOAT_DENOMINATIONS) {
            int quantity = Math.max(source.getOrDefault(denomination, 0), 0);
            if (quantity > 0) {
                clean.put(denomination, quantity);
            }
        }
        return clean;
    }

    private static Map<Integer, Integer> cleanDenominationCounts(Map<Integer, Integer> counts) {
        Map<Integer, Integer> clean = new HashMap<>();
        Map<Integer, Integer> source = counts == null ? Map.of() : counts;
        for (int denomination : FLOAT_DENOMINATIONS) {
            clean.put(denomination, Math.max(source.getOrDefault(denomination, 0), 0));
        }
        return clean;
    }

    private static String floatMixToJson(Map<Integer, Integer> floatMix) {
        Map<Integer, Integer> clean = cleanFloatMix(floatMix);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (int denomination : FLOAT_DENOMINATIONS) {
            int quantity = clean.getOrDefault(denomination, 0);
            if (quantity <= 0) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            json.append('"').append(denomination).append('"').append(':').append(quantity);
            first = false;
        }
        json.append('}');
        return json.toString();
    }

    private static String denominationCountsToJson(Map<Integer, Integer> counts) {
        Map<Integer, Integer> clean = cleanDenominationCounts(counts);
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (int denomination : FLOAT_DENOMINATIONS) {
            if (!first) {
                json.append(',');
            }
            json.append('"').append(denomination).append('"').append(':').append(clean.getOrDefault(denomination, 0));
            first = false;
        }
        json.append('}');
        return json.toString();
    }

    private static Map<Integer, Integer> parseFloatMix(String json) {
        if (json == null || json.isBlank()) {
            return DEFAULT_FLOAT_MIX;
        }
        Map<Integer, Integer> mix = new HashMap<>();
        String clean = json.trim();
        if (clean.startsWith("{") && clean.endsWith("}")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        if (!clean.isBlank()) {
            for (String pair : clean.split(",")) {
                String[] parts = pair.split(":", 2);
                if (parts.length != 2) {
                    continue;
                }
                try {
                    int denomination = Integer.parseInt(parts[0].replace("\"", "").trim());
                    int quantity = Integer.parseInt(parts[1].replace("\"", "").trim());
                    if (isSupportedFloatDenomination(denomination) && quantity > 0) {
                        mix.put(denomination, quantity);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore malformed stored values and fall back below if no usable counts remain.
                }
            }
        }
        return mix.isEmpty() ? DEFAULT_FLOAT_MIX : Collections.unmodifiableMap(mix);
    }

    private static boolean isSupportedFloatDenomination(int denomination) {
        for (int supported : FLOAT_DENOMINATIONS) {
            if (supported == denomination) {
                return true;
            }
        }
        return false;
    }

    private static String defaultText(String value) {
        return value == null || value.isBlank() ? "Unknown" : value.trim();
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, table, new String[]{"TABLE"})) {
            return rs.next();
        }
    }

    public record StoreOption(Integer id, String name) {
        @Override
        public String toString() {
            return name == null || name.isBlank() ? "Store " + id : name;
        }
    }

    public record DeviceOption(String id, String name) {
        @Override
        public String toString() {
            return name == null || name.isBlank() ? id : name;
        }
    }

    public static List<StoreOption> listStores(Connection conn) throws SQLException {
        ensureSchema(conn);
        List<StoreOption> stores = new ArrayList<>();
        String sql = "SELECT location_id, name FROM locations ORDER BY name";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                stores.add(new StoreOption(rs.getInt("location_id"), rs.getString("name")));
            }
        }
        return stores;
    }

    public static List<DeviceOption> listApprovedDevices(Connection conn) throws SQLException {
        ensureSchema(conn);
        List<DeviceOption> devices = new ArrayList<>();
        String sql = """
                SELECT device_id::text AS device_id,
                       COALESCE(NULLIF(TRIM(device_name), ''), NULLIF(TRIM(hostname), ''), installation_id) AS display_name
                FROM devices
                WHERE is_approved = TRUE
                  AND is_blocked = FALSE
                ORDER BY display_name
                """;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                devices.add(new DeviceOption(rs.getString("device_id"), rs.getString("display_name")));
            }
        }
        return devices;
    }
}
