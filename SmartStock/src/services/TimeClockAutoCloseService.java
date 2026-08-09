package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TimeClockAutoCloseService {
    public static final UUID SETTINGS_ID = UUID.fromString("8e56e4a5-742e-4f69-b819-2e853b850001");
    public static final int DEFAULT_SCHEDULED_DELAY_HOURS = 4;
    public static final int DEFAULT_UNSCHEDULED_DETECTION_HOURS = 12;
    public static final int DEFAULT_MAX_WORK_HOURS = 8;
    public static final String REVIEW_PENDING = "PENDING";
    public static final String REVIEW_CONFIRMED = "CONFIRMED";
    public static final String REVIEW_CORRECTED = "CORRECTED";

    public record AutoCloseSettings(boolean enabled, int scheduledDelayHours,
                                    int unscheduledDetectionHours, int maxWorkHours,
                                    Integer updatedByUserId, String updatedByName) {
    }

    public record PolicySnapshot(boolean enabled, String rule, Instant detectionAt,
                                 int maxWorkHours, UUID shiftId, String shiftName,
                                 Instant scheduledShiftEndAt) {
    }

    public record PendingReview(long clockId, int userId, String employeeName,
                                String locationName, ZoneId locationZone,
                                LocalDate workDate, LocalDateTime clockIn,
                                LocalDateTime lunchStart, LocalDateTime lunchEnd,
                                LocalDateTime breakStart, LocalDateTime breakEnd,
                                LocalDateTime clockOut, BigDecimal workedHours,
                                String rule, LocalDateTime detectedAt,
                                String reviewStatus) {
    }

    public record Correction(LocalDateTime clockIn, LocalDateTime lunchStart,
                             LocalDateTime lunchEnd, LocalDateTime breakStart,
                             LocalDateTime breakEnd, LocalDateTime clockOut,
                             String reason) {
    }

    public record EmployeeAutoCloseNotice(long clockId, LocalDate workDate,
                                          LocalDateTime clockIn, LocalDateTime lunchStart,
                                          LocalDateTime lunchEnd, LocalDateTime clockOut,
                                          int maxWorkHours, String rule, String reviewStatus) {
    }

    private record OpenPunch(long clockId, int userId, String employeeName,
                             int locationId, String locationName, String timezone,
                             LocalDate workDate, Instant clockIn, Instant lunchStart,
                             Instant lunchEnd, Instant breakStart, Instant breakEnd,
                             String compensationType, BigDecimal rate,
                             String rule, Instant detectionAt, int maxWorkHours) {
    }

    private record PunchValues(Instant clockIn, Instant lunchStart, Instant lunchEnd,
                               Instant breakStart, Instant breakEnd, Instant clockOut,
                               BigDecimal hours, BigDecimal earned) {
    }

    private record LegacyOpenPunch(long clockId, int userId, Integer locationId,
                                   LocalDate workDate, Instant clockIn) {
    }

    private TimeClockAutoCloseService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS time_clock_auto_close_settings (
                        settings_id UUID PRIMARY KEY,
                        auto_close_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                        scheduled_detection_delay_hours INTEGER NOT NULL DEFAULT 4,
                        unscheduled_detection_hours INTEGER NOT NULL DEFAULT 12,
                        max_auto_work_hours INTEGER NOT NULL DEFAULT 8,
                        updated_by_user_id INTEGER REFERENCES users(user_id),
                        updated_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT time_clock_auto_close_scheduled_delay_chk
                            CHECK (scheduled_detection_delay_hours BETWEEN 0 AND 24),
                        CONSTRAINT time_clock_auto_close_unscheduled_chk
                            CHECK (unscheduled_detection_hours BETWEEN 1 AND 48),
                        CONSTRAINT time_clock_auto_close_max_work_chk
                            CHECK (max_auto_work_hours BETWEEN 1 AND 24),
                        CONSTRAINT time_clock_auto_close_threshold_order_chk
                            CHECK (unscheduled_detection_hours >= max_auto_work_hours)
                    )
                    """);
            stmt.executeUpdate("""
                    INSERT INTO time_clock_auto_close_settings (
                        settings_id, auto_close_enabled, scheduled_detection_delay_hours,
                        unscheduled_detection_hours, max_auto_work_hours, updated_by_name
                    ) VALUES (
                        '8e56e4a5-742e-4f69-b819-2e853b850001'::uuid,
                        TRUE, 4, 12, 8, 'System default'
                    ) ON CONFLICT (settings_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_time_clock_auto_close_settings_updated_at()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                            NEW.updated_at = CURRENT_TIMESTAMP;
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS time_clock_auto_close_settings_updated_at ON time_clock_auto_close_settings");
            stmt.executeUpdate("""
                    CREATE TRIGGER time_clock_auto_close_settings_updated_at
                    BEFORE UPDATE ON time_clock_auto_close_settings
                    FOR EACH ROW EXECUTE FUNCTION set_time_clock_auto_close_settings_updated_at()
                    """);

            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_enabled_snapshot BOOLEAN NOT NULL DEFAULT TRUE");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_rule_snapshot TEXT");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_detection_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_close_max_work_hours INTEGER NOT NULL DEFAULT 8");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_id_snapshot UUID");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_name_snapshot TEXT");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS scheduled_shift_end_at_snapshot TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_detected_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_review_status TEXT");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_by_user_id INTEGER REFERENCES users(user_id)");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_reviewed_by_name TEXT");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_clock_out_review_reason TEXT");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS break_start TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS break_end TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end_detected_at TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS auto_break_end_review_status TEXT");
            stmt.executeUpdate("""
                    DO $$ BEGIN
                        IF NOT EXISTS (SELECT 1 FROM pg_constraint
                            WHERE conname = 'employee_time_clock_break_order'
                              AND conrelid = 'employee_time_clock'::regclass) THEN
                            ALTER TABLE employee_time_clock ADD CONSTRAINT employee_time_clock_break_order
                                CHECK (break_start IS NULL OR break_end IS NULL OR break_end >= break_start);
                        END IF;
                    END $$
                    """);
            stmt.executeUpdate("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'employee_time_clock_auto_rule_chk'
                              AND conrelid = 'employee_time_clock'::regclass
                        ) THEN
                            ALTER TABLE employee_time_clock
                            ADD CONSTRAINT employee_time_clock_auto_rule_chk
                            CHECK (auto_close_rule_snapshot IS NULL OR auto_close_rule_snapshot IN ('SCHEDULED', 'UNSCHEDULED'));
                        END IF;
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'employee_time_clock_auto_review_chk'
                              AND conrelid = 'employee_time_clock'::regclass
                        ) THEN
                            ALTER TABLE employee_time_clock
                            ADD CONSTRAINT employee_time_clock_auto_review_chk
                            CHECK (auto_clock_out_review_status IS NULL OR auto_clock_out_review_status IN ('PENDING', 'CONFIRMED', 'CORRECTED'));
                        END IF;
                    END;
                    $$
                    """);
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS employee_time_clock_auto_due_idx
                    ON employee_time_clock(auto_close_detection_at)
                    WHERE clock_out IS NULL AND auto_close_enabled_snapshot
                    """);
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS employee_time_clock_auto_review_idx
                    ON employee_time_clock(auto_clock_out_review_status, auto_clock_out_detected_at DESC)
                    WHERE auto_clock_out
                    """);
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS employee_time_clock_open_break_due_idx
                    ON employee_time_clock(break_start)
                    WHERE clock_out IS NULL AND break_start IS NOT NULL AND break_end IS NULL
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employee_time_clock_adjustments (
                        adjustment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        clock_id BIGINT NOT NULL REFERENCES employee_time_clock(clock_id) ON DELETE CASCADE,
                        user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                        action_type TEXT NOT NULL,
                        before_clock_in TIMESTAMPTZ,
                        before_lunch_start TIMESTAMPTZ,
                        before_lunch_end TIMESTAMPTZ,
                        before_break_start TIMESTAMPTZ,
                        before_break_end TIMESTAMPTZ,
                        before_clock_out TIMESTAMPTZ,
                        before_hours NUMERIC(10,2),
                        after_clock_in TIMESTAMPTZ,
                        after_lunch_start TIMESTAMPTZ,
                        after_lunch_end TIMESTAMPTZ,
                        after_break_start TIMESTAMPTZ,
                        after_break_end TIMESTAMPTZ,
                        after_clock_out TIMESTAMPTZ,
                        after_hours NUMERIC(10,2),
                        reason TEXT NOT NULL,
                        actor_user_id INTEGER REFERENCES users(user_id),
                        actor_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT employee_time_clock_adjustments_action_chk
                            CHECK (action_type IN ('AUTO_CLOSE', 'BREAK_AUTO_END', 'CONFIRM', 'CORRECT'))
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS before_break_start TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS before_break_end TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS after_break_start TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock_adjustments ADD COLUMN IF NOT EXISTS after_break_end TIMESTAMPTZ");
            stmt.executeUpdate("ALTER TABLE employee_time_clock_adjustments DROP CONSTRAINT IF EXISTS employee_time_clock_adjustments_action_chk");
            stmt.executeUpdate("ALTER TABLE employee_time_clock_adjustments ADD CONSTRAINT employee_time_clock_adjustments_action_chk CHECK (action_type IN ('AUTO_CLOSE', 'BREAK_AUTO_END', 'CONFIRM', 'CORRECT'))");
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS employee_time_clock_adjustments_clock_idx
                    ON employee_time_clock_adjustments(clock_id, created_at DESC)
                    """);
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION prevent_employee_time_clock_adjustment_changes()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        RAISE EXCEPTION 'Time-clock adjustment history is append-only';
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS employee_time_clock_adjustments_append_only ON employee_time_clock_adjustments");
            stmt.executeUpdate("""
                    CREATE TRIGGER employee_time_clock_adjustments_append_only
                    BEFORE UPDATE OR DELETE ON employee_time_clock_adjustments
                    FOR EACH ROW EXECUTE FUNCTION prevent_employee_time_clock_adjustment_changes()
                    """);
        }
        SupabaseSecurityHardening.protectInternalTable(conn, "time_clock_auto_close_settings");
        SupabaseSecurityHardening.protectInternalTable(conn, "employee_time_clock_adjustments");
    }

    public static AutoCloseSettings loadSettings(Connection conn) throws SQLException {
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT auto_close_enabled, scheduled_detection_delay_hours,
                       unscheduled_detection_hours, max_auto_work_hours,
                       updated_by_user_id, updated_by_name
                FROM time_clock_auto_close_settings WHERE settings_id = ?
                """)) {
            ps.setObject(1, SETTINGS_ID);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AutoCloseSettings(rs.getBoolean(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                            rs.getObject(5, Integer.class), rs.getString(6));
                }
            }
        }
        return new AutoCloseSettings(true, DEFAULT_SCHEDULED_DELAY_HOURS,
                DEFAULT_UNSCHEDULED_DETECTION_HOURS, DEFAULT_MAX_WORK_HOURS, null, "System default");
    }

    public static AutoCloseSettings loadSettings() throws SQLException {
        try { return LanApiClient.loadTimeClockAutoCloseSettings(); }
        catch (Exception ex) { throw sql("Unable to load automatic clock-out settings from the SmartStock server.", ex); }
    }

    public static void saveSettings(AutoCloseSettings settings) throws SQLException {
        try { LanApiClient.saveTimeClockAutoCloseSettings(settings, UUID.randomUUID().toString()); }
        catch (Exception ex) { throw sql("Unable to save automatic clock-out settings through the SmartStock server.", ex); }
    }

    public static void saveSettings(Connection conn, AutoCloseSettings settings,
                                    Integer actorUserId, String actorName) throws SQLException {
        validateSettings(settings);
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                     UPDATE time_clock_auto_close_settings
                     SET auto_close_enabled = ?, scheduled_detection_delay_hours = ?,
                         unscheduled_detection_hours = ?, max_auto_work_hours = ?,
                         updated_by_user_id = ?, updated_by_name = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE settings_id = ?
                     """)) {
                ps.setBoolean(1, settings.enabled());
                ps.setInt(2, settings.scheduledDelayHours());
                ps.setInt(3, settings.unscheduledDetectionHours());
                ps.setInt(4, settings.maxWorkHours());
                setNullableInteger(ps, 5, actorUserId);
                ps.setString(6, actorName);
                ps.setObject(7, SETTINGS_ID);
                ps.executeUpdate();
        }
        SyncOutboxService.recordEvent(conn, "TIME_CLOCK_AUTO_CLOSE_SETTINGS_UPDATED", java.util.Map.of(
                "enabled", settings.enabled(),
                "scheduled_delay_hours", settings.scheduledDelayHours(),
                "unscheduled_detection_hours", settings.unscheduledDetectionHours(),
                "max_work_hours", settings.maxWorkHours()
        ));
    }

    public static PolicySnapshot snapshotForClockIn(Connection conn, int userId, Integer locationId,
                                                     LocalDate workDate, Instant clockIn) throws SQLException {
        AutoCloseSettings settings = loadSettings(conn);
        UUID shiftId = null;
        String shiftName = null;
        Instant scheduledEnd = null;
        String sql = """
                SELECT a.shift_id, a.shift_name_snapshot, a.shift_end_time,
                       COALESCE(NULLIF(l.timezone, ''), 'America/New_York') AS timezone
                FROM employee_schedule_assignments a
                JOIN locations l ON l.location_id = a.location_id
                WHERE a.user_id = ? AND a.work_date = ?
                  AND (? IS NULL OR a.location_id = ?)
                  AND a.shift_end_time IS NOT NULL
                ORDER BY CASE WHEN a.location_id = ? THEN 0 ELSE 1 END
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(workDate));
            setNullableInteger(ps, 3, locationId);
            setNullableInteger(ps, 4, locationId);
            setNullableInteger(ps, 5, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    LocalTime end = rs.getTime("shift_end_time").toLocalTime();
                    ZoneId zone = safeZone(rs.getString("timezone"));
                    Instant candidate = LocalDateTime.of(workDate, end).atZone(zone).toInstant();
                    if (candidate.isAfter(clockIn)) {
                        shiftId = rs.getObject("shift_id", UUID.class);
                        shiftName = rs.getString("shift_name_snapshot");
                        scheduledEnd = candidate;
                    }
                }
            }
        }
        String rule = scheduledEnd == null ? "UNSCHEDULED" : "SCHEDULED";
        Instant detection = scheduledEnd == null
                ? clockIn.plus(Duration.ofHours(settings.unscheduledDetectionHours()))
                : scheduledEnd.plus(Duration.ofHours(settings.scheduledDelayHours()));
        return new PolicySnapshot(settings.enabled(), rule, detection, settings.maxWorkHours(),
                shiftId, shiftName, scheduledEnd);
    }

    public static int processExpiredOpenPunches(Connection conn) throws SQLException {
        boolean ownsTransaction = conn.getAutoCommit();
        if (ownsTransaction) conn.setAutoCommit(false);
        try {
            ensureSchema(conn);
            snapshotLegacyOpenPunches(conn);
            processOverdueBreaks(conn);
            int closed = processDuePunches(conn, Instant.now());
            if (ownsTransaction) conn.commit();
            return closed;
        } catch (SQLException | RuntimeException ex) {
            if (ownsTransaction) conn.rollback();
            throw ex;
        } finally {
            if (ownsTransaction) conn.setAutoCommit(true);
        }
    }

    static int processOverdueBreaks(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                WITH closed_breaks AS (
                    UPDATE employee_time_clock
                    SET break_end = break_start + INTERVAL '15 minutes',
                        auto_break_end = TRUE,
                        auto_break_end_detected_at = CURRENT_TIMESTAMP,
                        auto_break_end_review_status = 'PENDING',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE clock_out IS NULL
                      AND break_start IS NOT NULL
                      AND break_end IS NULL
                      AND CURRENT_TIMESTAMP > break_start + INTERVAL '20 minutes'
                    RETURNING clock_id, user_id, clock_in, lunch_start, lunch_end,
                              break_start, break_end, clock_out, total_hours_worked
                )
                INSERT INTO employee_time_clock_adjustments (
                    adjustment_id, clock_id, user_id, action_type,
                    before_clock_in, before_lunch_start, before_lunch_end,
                    before_break_start, before_break_end, before_clock_out, before_hours,
                    after_clock_in, after_lunch_start, after_lunch_end,
                    after_break_start, after_break_end, after_clock_out, after_hours,
                    reason, actor_name
                )
                SELECT gen_random_uuid(), clock_id, user_id, 'BREAK_AUTO_END',
                       clock_in, lunch_start, lunch_end, break_start, NULL, clock_out, total_hours_worked,
                       clock_in, lunch_start, lunch_end, break_start, break_end, clock_out, total_hours_worked,
                       '10-minute break left open beyond 20 minutes; automatically ended at 15 minutes.',
                       'SmartStock automatic time clock'
                FROM closed_breaks
                """)) {
            return ps.executeUpdate();
        }
    }

    private static int processDuePunches(Connection conn, Instant detectedAt) throws SQLException {
        List<OpenPunch> due = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT tc.clock_id, tc.user_id,
                       COALESCE(tc.user_name, u.full_name, u.username, '') AS employee_name,
                       COALESCE(tc.location_id, 0) AS location_id,
                       COALESCE(tc.location_name, l.name, '') AS location_name,
                       COALESCE(NULLIF(l.timezone, ''), 'America/New_York') AS timezone,
                       tc.work_date, tc.clock_in, tc.lunch_start, tc.lunch_end,
                       tc.break_start, tc.break_end,
                       COALESCE(u.compensation_type::text, 'HOURLY') AS compensation_type,
                       COALESCE(u.salary, 0) AS rate,
                       COALESCE(tc.auto_close_rule_snapshot, 'UNSCHEDULED') AS auto_rule,
                       tc.auto_close_detection_at,
                       COALESCE(tc.auto_close_max_work_hours, 8) AS max_work_hours
                FROM employee_time_clock tc
                JOIN users u ON u.user_id = tc.user_id
                LEFT JOIN locations l ON l.location_id = tc.location_id
                WHERE tc.clock_out IS NULL
                  AND tc.auto_close_enabled_snapshot
                  AND tc.auto_close_detection_at IS NOT NULL
                  AND tc.auto_close_detection_at <= CURRENT_TIMESTAMP
                ORDER BY tc.auto_close_detection_at, tc.clock_id
                FOR UPDATE OF tc SKIP LOCKED
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(new OpenPunch(rs.getLong("clock_id"), rs.getInt("user_id"),
                            rs.getString("employee_name"), rs.getInt("location_id"),
                            rs.getString("location_name"), rs.getString("timezone"),
                            rs.getDate("work_date").toLocalDate(), instant(rs, "clock_in"),
                            instant(rs, "lunch_start"), instant(rs, "lunch_end"),
                            instant(rs, "break_start"), instant(rs, "break_end"),
                            rs.getString("compensation_type"), rs.getBigDecimal("rate"),
                            rs.getString("auto_rule"), instant(rs, "auto_close_detection_at"),
                            rs.getInt("max_work_hours")));
                }
            }
        }
        int closed = 0;
        for (OpenPunch punch : due) {
            PunchValues after = automaticValues(conn, punch, detectedAt);
            if (applyAutomaticClose(conn, punch, after, detectedAt)) closed++;
        }
        return closed;
    }

    private static void snapshotLegacyOpenPunches(Connection conn) throws SQLException {
        List<LegacyOpenPunch> openPunches = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT clock_id, user_id, location_id, work_date, clock_in
                FROM employee_time_clock
                WHERE clock_out IS NULL AND auto_close_detection_at IS NULL
                ORDER BY clock_id
                FOR UPDATE SKIP LOCKED
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    openPunches.add(new LegacyOpenPunch(rs.getLong("clock_id"), rs.getInt("user_id"),
                            rs.getObject("location_id", Integer.class), rs.getDate("work_date").toLocalDate(),
                            instant(rs, "clock_in")));
                }
            }
        }
        for (LegacyOpenPunch punch : openPunches) {
            PolicySnapshot snapshot = snapshotForClockIn(conn, punch.userId, punch.locationId,
                    punch.workDate, punch.clockIn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE employee_time_clock
                    SET auto_close_enabled_snapshot = ?, auto_close_rule_snapshot = ?,
                        auto_close_detection_at = ?, auto_close_max_work_hours = ?,
                        scheduled_shift_id_snapshot = ?, scheduled_shift_name_snapshot = ?,
                        scheduled_shift_end_at_snapshot = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE clock_id = ? AND clock_out IS NULL AND auto_close_detection_at IS NULL
                    """)) {
                ps.setBoolean(1, snapshot.enabled());
                ps.setString(2, snapshot.rule());
                setTimestamp(ps, 3, snapshot.detectionAt());
                ps.setInt(4, snapshot.maxWorkHours());
                if (snapshot.shiftId() == null) ps.setNull(5, Types.OTHER); else ps.setObject(5, snapshot.shiftId());
                ps.setString(6, snapshot.shiftName());
                setTimestamp(ps, 7, snapshot.scheduledShiftEndAt());
                ps.setLong(8, punch.clockId);
                ps.executeUpdate();
            }
        }
    }

    public static List<PendingReview> loadPendingReviews() throws SQLException {
        try { return LanApiClient.loadPendingTimeClockReviews(); }
        catch (Exception ex) { throw sql("Unable to load automatic time-clock reviews from the SmartStock server.", ex); }
    }

    public static List<PendingReview> loadPendingReviews(Connection conn) throws SQLException {
        ensureSchema(conn);
        List<PendingReview> reviews = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT tc.clock_id, tc.user_id,
                           COALESCE(tc.user_name, u.full_name, u.username, '') AS employee_name,
                           COALESCE(tc.location_name, l.name, '') AS location_name,
                           COALESCE(NULLIF(l.timezone, ''), 'America/New_York') AS timezone,
                           tc.work_date, tc.clock_in, tc.lunch_start, tc.lunch_end,
                           tc.break_start, tc.break_end, tc.clock_out,
                           COALESCE(tc.total_hours_worked, 0) AS worked_hours,
                           CASE WHEN tc.auto_clock_out_review_status = 'PENDING'
                                THEN tc.auto_close_rule_snapshot ELSE 'BREAK_AUTO_END' END AS review_rule,
                           CASE WHEN tc.auto_clock_out_review_status = 'PENDING'
                                THEN tc.auto_clock_out_detected_at ELSE tc.auto_break_end_detected_at END AS review_detected_at,
                           'PENDING' AS review_status
                    FROM employee_time_clock tc
                    JOIN users u ON u.user_id = tc.user_id
                    LEFT JOIN locations l ON l.location_id = tc.location_id
                    WHERE (tc.auto_clock_out AND tc.auto_clock_out_review_status = 'PENDING')
                       OR (tc.auto_break_end AND tc.auto_break_end_review_status = 'PENDING'
                           AND tc.clock_out IS NOT NULL)
                    ORDER BY COALESCE(tc.auto_break_end_detected_at, tc.auto_clock_out_detected_at), tc.clock_id
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ZoneId zone = safeZone(rs.getString("timezone"));
                        reviews.add(new PendingReview(rs.getLong("clock_id"), rs.getInt("user_id"),
                                rs.getString("employee_name"), rs.getString("location_name"), zone,
                                rs.getDate("work_date").toLocalDate(), local(rs, "clock_in", zone),
                                local(rs, "lunch_start", zone), local(rs, "lunch_end", zone),
                                local(rs, "break_start", zone), local(rs, "break_end", zone),
                                local(rs, "clock_out", zone), rs.getBigDecimal("worked_hours"),
                                rs.getString("review_rule"),
                                local(rs, "review_detected_at", zone),
                                rs.getString("review_status")));
                    }
                }
            }
        return reviews;
    }

    public static EmployeeAutoCloseNotice latestPendingNotice(int userId) throws SQLException {
        try { return LanApiClient.loadLatestTimeClockNotice(); }
        catch (Exception ex) { throw sql("Unable to load your automatic clock-out notice from the SmartStock server.", ex); }
    }

    public static EmployeeAutoCloseNotice latestPendingNotice(Connection conn, int userId) throws SQLException {
        ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT tc.clock_id, tc.work_date, tc.clock_in, tc.lunch_start,
                           tc.lunch_end, tc.clock_out, tc.auto_close_rule_snapshot,
                           tc.auto_close_max_work_hours, tc.auto_clock_out_review_status,
                           COALESCE(NULLIF(l.timezone, ''), 'America/New_York') AS timezone
                    FROM employee_time_clock tc
                    LEFT JOIN locations l ON l.location_id = tc.location_id
                    WHERE tc.user_id = ? AND tc.auto_clock_out
                      AND tc.auto_clock_out_review_status = 'PENDING'
                    ORDER BY tc.auto_clock_out_detected_at DESC, tc.clock_id DESC LIMIT 1
                    """)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        ZoneId zone = safeZone(rs.getString("timezone"));
                        return new EmployeeAutoCloseNotice(
                                rs.getLong("clock_id"),
                                rs.getDate("work_date").toLocalDate(),
                                local(rs, "clock_in", zone),
                                local(rs, "lunch_start", zone),
                                local(rs, "lunch_end", zone),
                                local(rs, "clock_out", zone),
                                rs.getInt("auto_close_max_work_hours"),
                                rs.getString("auto_close_rule_snapshot"),
                                rs.getString("auto_clock_out_review_status"));
                    }
                }
            }
        return null;
    }

    public static void confirm(long clockId, String reason) throws SQLException {
        try { LanApiClient.confirmTimeClockAutoClose(clockId, reason, UUID.randomUUID().toString()); }
        catch (Exception ex) { throw sql("Unable to confirm the automatic time-clock adjustment through the SmartStock server.", ex); }
    }

    public static void confirm(Connection conn, long clockId, String reason,
                               Integer actorUserId, String actorName) throws SQLException {
        ensureSchema(conn);
                PunchValues before = lockPunch(conn, clockId);
                String note = reason == null || reason.isBlank() ? "Automatic clock-out confirmed." : reason.trim();
                try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE employee_time_clock
                        SET auto_clock_out_review_status = CASE WHEN auto_clock_out_review_status = 'PENDING' THEN 'CONFIRMED' ELSE auto_clock_out_review_status END,
                            auto_break_end_review_status = CASE WHEN auto_break_end_review_status = 'PENDING' THEN 'CONFIRMED' ELSE auto_break_end_review_status END,
                            auto_clock_out_reviewed_at = CURRENT_TIMESTAMP,
                            auto_clock_out_reviewed_by_user_id = ?,
                            auto_clock_out_reviewed_by_name = ?,
                            auto_clock_out_review_reason = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE clock_id = ? AND (auto_clock_out_review_status = 'PENDING' OR auto_break_end_review_status = 'PENDING')
                        """)) {
                    setNullableInteger(ps, 1, actorUserId);
                    ps.setString(2, actorName);
                    ps.setString(3, note);
                    ps.setLong(4, clockId);
                    if (ps.executeUpdate() != 1) throw new SQLException("This automatic clock-out was already reviewed.");
                }
                insertAdjustment(conn, clockId, userIdForClock(conn, clockId), "CONFIRM", before, before,
                        note, actorUserId, actorName);
    }

    public static void correct(long clockId, ZoneId zone, Correction correction) throws SQLException {
        try { LanApiClient.correctTimeClockAutoClose(clockId, zone, correction, UUID.randomUUID().toString()); }
        catch (Exception ex) { throw sql("Unable to correct the automatic time-clock adjustment through the SmartStock server.", ex); }
    }

    public static void correct(Connection conn, long clockId, ZoneId zone, Correction correction,
                               Integer actorUserId, String actorName) throws SQLException {
        validateCorrection(correction);
        ensureSchema(conn);
                PunchValues before = lockPunch(conn, clockId);
                int userId = userIdForClock(conn, clockId);
                String compensationType;
                BigDecimal rate;
                LocalDate workDate;
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT COALESCE(u.compensation_type::text, 'HOURLY'), COALESCE(u.salary, 0), tc.work_date
                        FROM employee_time_clock tc JOIN users u ON u.user_id = tc.user_id
                        WHERE tc.clock_id = ?
                        """)) {
                    ps.setLong(1, clockId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Time-clock record was not found.");
                        compensationType = rs.getString(1);
                        rate = rs.getBigDecimal(2);
                        workDate = rs.getDate(3).toLocalDate();
                    }
                }
                if ("HOURLY".equalsIgnoreCase(compensationType)
                        && before.earned != null && before.hours != null
                        && before.hours.compareTo(BigDecimal.ZERO) > 0) {
                    rate = before.earned.divide(before.hours, 8, RoundingMode.HALF_UP);
                }
                Instant in = correction.clockIn().atZone(zone).toInstant();
                Instant lunchStart = toInstant(correction.lunchStart(), zone);
                Instant lunchEnd = toInstant(correction.lunchEnd(), zone);
                Instant breakStart = toInstant(correction.breakStart(), zone);
                Instant breakEnd = toInstant(correction.breakEnd(), zone);
                Instant out = correction.clockOut().atZone(zone).toInstant();
                BigDecimal hours = workedHours(in, lunchStart, lunchEnd, breakStart, breakEnd, out);
                BigDecimal earned = earned(conn, userId, workDate, compensationType, rate, hours, clockId);
                PunchValues after = new PunchValues(in, lunchStart, lunchEnd, breakStart, breakEnd, out, hours, earned);
                try (PreparedStatement ps = conn.prepareStatement("""
                        UPDATE employee_time_clock
                        SET clock_in = ?, lunch_start = ?, lunch_end = ?, break_start = ?, break_end = ?, clock_out = ?,
                            total_hours_worked = ?, total_earned = ?,
                            auto_clock_out_review_status = CASE WHEN auto_clock_out_review_status = 'PENDING' THEN 'CORRECTED' ELSE auto_clock_out_review_status END,
                            auto_break_end_review_status = CASE WHEN auto_break_end_review_status = 'PENDING' THEN 'CORRECTED' ELSE auto_break_end_review_status END,
                            auto_clock_out_reviewed_at = CURRENT_TIMESTAMP,
                            auto_clock_out_reviewed_by_user_id = ?,
                            auto_clock_out_reviewed_by_name = ?,
                            auto_clock_out_review_reason = ?, updated_at = CURRENT_TIMESTAMP
                        WHERE clock_id = ? AND (auto_clock_out_review_status = 'PENDING' OR auto_break_end_review_status = 'PENDING')
                        """)) {
                    setTimestamp(ps, 1, in); setTimestamp(ps, 2, lunchStart); setTimestamp(ps, 3, lunchEnd);
                    setTimestamp(ps, 4, breakStart); setTimestamp(ps, 5, breakEnd); setTimestamp(ps, 6, out);
                    ps.setBigDecimal(7, hours); setNullableDecimal(ps, 8, earned);
                    setNullableInteger(ps, 9, actorUserId);
                    ps.setString(10, actorName);
                    ps.setString(11, correction.reason().trim());
                    ps.setLong(12, clockId);
                    if (ps.executeUpdate() != 1) throw new SQLException("This automatic clock-out was already reviewed.");
                }
                insertAdjustment(conn, clockId, userId, "CORRECT", before, after,
                        correction.reason().trim(), actorUserId, actorName);
    }

    private static PunchValues automaticValues(Connection conn, OpenPunch punch, Instant detectedAt) throws SQLException {
        Instant maxTarget = punch.clockIn.plus(Duration.ofHours(punch.maxWorkHours));
        Instant lunchStart = punch.lunchStart;
        Instant lunchEnd = punch.lunchEnd;
        Instant breakStart = punch.breakStart;
        Instant breakEnd = punch.breakEnd;
        if (breakStart != null && breakEnd != null && !breakEnd.isBefore(breakStart)) {
            maxTarget = maxTarget.plus(Duration.between(breakStart, breakEnd));
        }
        Instant clockOut;
        if (lunchStart == null) {
            clockOut = earlier(maxTarget, detectedAt);
        } else if (lunchEnd != null && !lunchEnd.isBefore(lunchStart)) {
            Instant target = maxTarget.plus(Duration.between(lunchStart, lunchEnd));
            clockOut = earlier(target, detectedAt);
        } else {
            clockOut = earlier(lunchStart, maxTarget);
            lunchStart = clockOut;
            lunchEnd = clockOut;
        }
        if (clockOut.isBefore(punch.clockIn)) clockOut = punch.clockIn;
        if (breakStart != null && breakEnd == null) breakEnd = clockOut.isBefore(breakStart) ? breakStart : clockOut;
        BigDecimal hours = workedHours(punch.clockIn, lunchStart, lunchEnd,
                breakStart, breakEnd, clockOut)
                .min(BigDecimal.valueOf(punch.maxWorkHours)).max(BigDecimal.ZERO);
        BigDecimal earned = earned(conn, punch.userId, punch.workDate, punch.compensationType,
                punch.rate, hours, punch.clockId);
        return new PunchValues(punch.clockIn, lunchStart, lunchEnd, breakStart,
                breakEnd, clockOut, hours, earned);
    }

    private static boolean applyAutomaticClose(Connection conn, OpenPunch punch, PunchValues after,
                                               Instant detectedAt) throws SQLException {
        PunchValues before = new PunchValues(punch.clockIn, punch.lunchStart, punch.lunchEnd,
                punch.breakStart, punch.breakEnd, null, null, null);
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE employee_time_clock
                SET lunch_start = ?, lunch_end = ?, break_start = ?, break_end = ?, clock_out = ?,
                    total_hours_worked = ?, total_earned = ?,
                    auto_clock_out = TRUE,
                    auto_clock_out_detected_at = ?,
                    auto_clock_out_review_status = 'PENDING',
                    updated_at = CURRENT_TIMESTAMP
                WHERE clock_id = ? AND clock_out IS NULL
                """)) {
            setTimestamp(ps, 1, after.lunchStart); setTimestamp(ps, 2, after.lunchEnd);
            setTimestamp(ps, 3, after.breakStart); setTimestamp(ps, 4, after.breakEnd);
            setTimestamp(ps, 5, after.clockOut); ps.setBigDecimal(6, after.hours);
            setNullableDecimal(ps, 7, after.earned); setTimestamp(ps, 8, detectedAt);
            ps.setLong(9, punch.clockId);
            if (ps.executeUpdate() != 1) return false;
        }
        String reason = "Automatic clock-out using " + punch.rule.toLowerCase()
                + " safety rule; payable time capped at " + punch.maxWorkHours + " worked hours.";
        insertAdjustment(conn, punch.clockId, punch.userId, "AUTO_CLOSE", before, after, reason,
                null, "SmartStock automatic time clock");
        SyncOutboxService.recordEvent(conn, "TIME_CLOCK_AUTO_CLOSED", java.util.Map.of(
                "clock_id", punch.clockId,
                "user_id", punch.userId,
                "rule", punch.rule,
                "worked_hours", after.hours,
                "review_status", REVIEW_PENDING
        ));
        return true;
    }

    private static PunchValues lockPunch(Connection conn, long clockId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT clock_in, lunch_start, lunch_end, break_start, break_end, clock_out,
                       total_hours_worked, total_earned
                FROM employee_time_clock WHERE clock_id = ? FOR UPDATE
                """)) {
            ps.setLong(1, clockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Time-clock record was not found.");
                return new PunchValues(instant(rs, "clock_in"), instant(rs, "lunch_start"),
                        instant(rs, "lunch_end"), instant(rs, "break_start"), instant(rs, "break_end"),
                        instant(rs, "clock_out"),
                        rs.getBigDecimal("total_hours_worked"), rs.getBigDecimal("total_earned"));
            }
        }
    }

    private static int userIdForClock(Connection conn, long clockId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT user_id FROM employee_time_clock WHERE clock_id = ?")) {
            ps.setLong(1, clockId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Time-clock record was not found.");
                return rs.getInt(1);
            }
        }
    }

    private static void insertAdjustment(Connection conn, long clockId, int userId, String action,
                                         PunchValues before, PunchValues after, String reason,
                                         Integer actorUserId, String actorName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO employee_time_clock_adjustments (
                    adjustment_id, clock_id, user_id, action_type,
                    before_clock_in, before_lunch_start, before_lunch_end, before_break_start, before_break_end, before_clock_out, before_hours,
                    after_clock_in, after_lunch_start, after_lunch_end, after_break_start, after_break_end, after_clock_out, after_hours,
                    reason, actor_user_id, actor_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setObject(1, UUID.randomUUID()); ps.setLong(2, clockId); ps.setInt(3, userId); ps.setString(4, action);
            bindValues(ps, 5, before); bindValues(ps, 12, after);
            ps.setString(19, reason);
            setNullableInteger(ps, 20, actorUserId);
            ps.setString(21, actorName);
            ps.executeUpdate();
        }
    }

    private static void bindValues(PreparedStatement ps, int start, PunchValues values) throws SQLException {
        setTimestamp(ps, start, values.clockIn); setTimestamp(ps, start + 1, values.lunchStart);
        setTimestamp(ps, start + 2, values.lunchEnd); setTimestamp(ps, start + 3, values.breakStart);
        setTimestamp(ps, start + 4, values.breakEnd); setTimestamp(ps, start + 5, values.clockOut);
        setNullableDecimal(ps, start + 6, values.hours);
    }

    private static BigDecimal earned(Connection conn, int userId, LocalDate workDate,
                                     String compensationType, BigDecimal rate,
                                     BigDecimal hours, long excludedClockId) throws SQLException {
        if ("SALARY".equalsIgnoreCase(compensationType)) return null;
        if ("DAILY".equalsIgnoreCase(compensationType)) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT 1 FROM employee_time_clock
                    WHERE user_id = ? AND work_date = ? AND clock_id <> ?
                      AND clock_out IS NOT NULL AND total_earned IS NOT NULL
                    LIMIT 1
                    """)) {
                ps.setInt(1, userId); ps.setDate(2, Date.valueOf(workDate)); ps.setLong(3, excludedClockId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? null : normalizeMoney(rate);
                }
            }
        }
        return normalizeMoney((rate == null ? BigDecimal.ZERO : rate).multiply(hours));
    }

    static BigDecimal workedHours(Instant in, Instant lunchStart, Instant lunchEnd,
                                  Instant breakStart, Instant breakEnd, Instant out) {
        long minutes = Math.max(0, Duration.between(in, out).toMinutes());
        if (lunchStart != null && lunchEnd != null && !lunchEnd.isBefore(lunchStart)) {
            Instant clippedStart = lunchStart.isBefore(in) ? in : lunchStart;
            Instant clippedEnd = lunchEnd.isAfter(out) ? out : lunchEnd;
            if (clippedEnd.isAfter(clippedStart)) minutes -= Duration.between(clippedStart, clippedEnd).toMinutes();
        }
        if (breakStart != null && breakEnd != null && !breakEnd.isBefore(breakStart)) {
            Instant clippedStart = breakStart.isBefore(in) ? in : breakStart;
            Instant clippedEnd = breakEnd.isAfter(out) ? out : breakEnd;
            if (clippedEnd.isAfter(clippedStart)) minutes -= Duration.between(clippedStart, clippedEnd).toMinutes();
        }
        return BigDecimal.valueOf(Math.max(0, minutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static void validateSettings(AutoCloseSettings settings) throws SQLException {
        if (settings == null || settings.scheduledDelayHours() < 0 || settings.scheduledDelayHours() > 24
                || settings.unscheduledDetectionHours() < 1 || settings.unscheduledDetectionHours() > 48
                || settings.maxWorkHours() < 1 || settings.maxWorkHours() > 24
                || settings.unscheduledDetectionHours() < settings.maxWorkHours()) {
            throw new SQLException("Enter a scheduled delay from 0–24 hours, an unscheduled threshold from 1–48 hours, and a maximum from 1–24 hours. The unscheduled threshold cannot be below the maximum.");
        }
    }

    private static void validateCorrection(Correction correction) throws SQLException {
        if (correction == null || correction.clockIn() == null || correction.clockOut() == null
                || correction.reason() == null || correction.reason().isBlank()) {
            throw new SQLException("Clock-in, clock-out, and a correction reason are required.");
        }
        if (correction.clockOut().isBefore(correction.clockIn())) {
            throw new SQLException("Clock-out cannot be before clock-in.");
        }
        if ((correction.lunchStart() == null) != (correction.lunchEnd() == null)) {
            throw new SQLException("Enter both lunch start and lunch end, or leave both blank.");
        }
        if (correction.lunchStart() != null && (correction.lunchStart().isBefore(correction.clockIn())
                || correction.lunchEnd().isBefore(correction.lunchStart())
                || correction.lunchEnd().isAfter(correction.clockOut()))) {
            throw new SQLException("Lunch must fall between clock-in and clock-out.");
        }
        if ((correction.breakStart() == null) != (correction.breakEnd() == null)) {
            throw new SQLException("Enter both break start and break end, or leave both blank.");
        }
        if (correction.breakStart() != null && (correction.breakStart().isBefore(correction.clockIn())
                || correction.breakEnd().isBefore(correction.breakStart())
                || correction.breakEnd().isAfter(correction.clockOut()))) {
            throw new SQLException("Break must fall between clock-in and clock-out.");
        }
    }

    private static ZoneId safeZone(String value) {
        try { return ZoneId.of(value == null || value.isBlank() ? "America/New_York" : value); }
        catch (Exception ignored) { return ZoneId.of("America/New_York"); }
    }

    private static Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static LocalDateTime local(ResultSet rs, String column, ZoneId zone) throws SQLException {
        Instant value = instant(rs, column);
        return value == null ? null : LocalDateTime.ofInstant(value, zone);
    }

    private static Instant toInstant(LocalDateTime value, ZoneId zone) {
        return value == null ? null : value.atZone(zone).toInstant();
    }

    private static void setTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        else ps.setTimestamp(index, Timestamp.from(value));
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) ps.setNull(index, Types.INTEGER); else ps.setInt(index, value);
    }

    private static void setNullableDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) ps.setNull(index, Types.NUMERIC); else ps.setBigDecimal(index, value);
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static SQLException sql(String message, Exception cause) {
        return cause instanceof SQLException existing ? existing : new SQLException(message, cause);
    }
}
