package services;

import managers.SessionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

public final class EmployeePayrollSettingsService {
    public static final BigDecimal DEFAULT_SEMI_MONTHLY_LIMIT = new BigDecimal("80.00");
    public static final BigDecimal DEFAULT_FOUR_BLOCK_LIMIT = new BigDecimal("40.00");
    private static final LocalDate DEFAULT_EFFECTIVE_FROM = LocalDate.of(1900, 1, 1);

    public enum PeriodType {
        SEMI_MONTHLY("Semi-monthly (1st–15th / 16th–month end)"),
        WEEKLY("Weekly (Monday–Sunday)"),
        FOUR_MONTH_BLOCKS("Four month blocks (1–7 / 8–15 / 16–23 / 24–end)");

        private final String label;

        PeriodType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public BigDecimal defaultLimit() {
            return this == SEMI_MONTHLY ? DEFAULT_SEMI_MONTHLY_LIMIT : DEFAULT_FOUR_BLOCK_LIMIT;
        }

        public static PeriodType fromDatabase(String value) {
            if (value == null || value.isBlank()) return SEMI_MONTHLY;
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return SEMI_MONTHLY;
            }
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record PayrollSetting(UUID settingId, int userId, PeriodType periodType,
                                 BigDecimal workHourLimit, LocalDate effectiveFrom,
                                 Integer createdByUserId, String createdByName) {
    }

    public record SettingView(PayrollSetting current, PayrollSetting pending) {
    }

    public record PayPeriod(LocalDate start, LocalDate end, LocalDate payDate,
                            PeriodType periodType, BigDecimal workHourLimit) {
    }

    private EmployeePayrollSettingsService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employee_payroll_settings (
                        setting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                        period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY',
                        work_hour_limit NUMERIC(8,2) NOT NULL DEFAULT 80.00,
                        effective_from DATE NOT NULL,
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT employee_payroll_settings_period_type_chk
                            CHECK (period_type IN ('SEMI_MONTHLY', 'WEEKLY', 'FOUR_MONTH_BLOCKS')),
                        CONSTRAINT employee_payroll_settings_hour_limit_chk CHECK (work_hour_limit > 0),
                        CONSTRAINT employee_payroll_settings_user_effective_key UNIQUE (user_id, effective_from)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE employee_payroll_settings DROP CONSTRAINT IF EXISTS employee_payroll_settings_period_type_chk");
            stmt.executeUpdate("""
                    ALTER TABLE employee_payroll_settings
                    ADD CONSTRAINT employee_payroll_settings_period_type_chk
                    CHECK (period_type IN ('SEMI_MONTHLY', 'WEEKLY', 'FOUR_MONTH_BLOCKS'))
                    """);
            stmt.executeUpdate("""
                    CREATE INDEX IF NOT EXISTS employee_payroll_settings_user_effective_idx
                    ON employee_payroll_settings(user_id, effective_from DESC)
                    """);
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_employee_payroll_settings_updated_at()
                    RETURNS TRIGGER AS $$
                    BEGIN
                        IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                            NEW.updated_at = CURRENT_TIMESTAMP;
                        END IF;
                        RETURN NEW;
                    END;
                    $$ LANGUAGE plpgsql
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS employee_payroll_settings_set_updated_at ON employee_payroll_settings");
            stmt.executeUpdate("""
                    CREATE TRIGGER employee_payroll_settings_set_updated_at
                    BEFORE UPDATE ON employee_payroll_settings
                    FOR EACH ROW EXECUTE FUNCTION set_employee_payroll_settings_updated_at()
                    """);
            stmt.executeUpdate("""
                    INSERT INTO employee_payroll_settings (
                        setting_id, user_id, period_type, work_hour_limit, effective_from,
                        created_by_name
                    )
                    SELECT (
                        SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 1, 8) || '-' ||
                        SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 9, 4) || '-' ||
                        SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 13, 4) || '-' ||
                        SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 17, 4) || '-' ||
                        SUBSTR(md5('smartstock-employee-payroll-default:' || u.user_id), 21, 12)
                    )::uuid,
                    u.user_id, 'SEMI_MONTHLY', 80.00, DATE '1900-01-01', 'System default'
                    FROM users u
                    WHERE NOT EXISTS (
                        SELECT 1 FROM employee_payroll_settings existing
                        WHERE existing.user_id = u.user_id
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE IF EXISTS payroll_payments ADD COLUMN IF NOT EXISTS pay_period_type TEXT NOT NULL DEFAULT 'SEMI_MONTHLY'");
            stmt.executeUpdate("ALTER TABLE IF EXISTS payroll_payments ADD COLUMN IF NOT EXISTS work_hour_limit NUMERIC(8,2) NOT NULL DEFAULT 80.00");
            stmt.executeUpdate("ALTER TABLE IF EXISTS payroll_payments ADD COLUMN IF NOT EXISTS regular_hours NUMERIC(10,2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE IF EXISTS payroll_payments ADD COLUMN IF NOT EXISTS overtime_hours NUMERIC(10,2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE IF EXISTS payroll_payments ADD COLUMN IF NOT EXISTS regular_pay NUMERIC(12,2) NOT NULL DEFAULT 0");
            stmt.executeUpdate("ALTER TABLE IF EXISTS payroll_payments ADD COLUMN IF NOT EXISTS overtime_pay NUMERIC(12,2) NOT NULL DEFAULT 0");
        }
        SupabaseSecurityHardening.protectInternalTable(conn, "employee_payroll_settings");
    }

    public static PayrollSetting settingFor(Connection conn, int userId, LocalDate date,
                                             String compensationType) throws SQLException {
        if (!usesSelectablePeriod(compensationType)) return defaultSetting(userId);
        String sql = """
                SELECT setting_id, user_id, period_type, work_hour_limit, effective_from,
                       created_by_user_id, created_by_name
                FROM employee_payroll_settings
                WHERE user_id = ? AND effective_from <= ?
                ORDER BY effective_from DESC, updated_at DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readSetting(rs) : defaultSetting(userId);
            }
        }
    }

    public static SettingView loadCurrentAndPending(Connection conn, int userId, LocalDate today,
                                                    String compensationType) throws SQLException {
        ensureSchema(conn);
        PayrollSetting current = settingFor(conn, userId, today, compensationType);
        PayrollSetting pending = null;
        if (usesSelectablePeriod(compensationType)) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT setting_id, user_id, period_type, work_hour_limit, effective_from,
                           created_by_user_id, created_by_name
                    FROM employee_payroll_settings
                    WHERE user_id = ? AND effective_from > ?
                    ORDER BY effective_from
                    LIMIT 1
                    """)) {
                ps.setInt(1, userId);
                ps.setDate(2, Date.valueOf(today));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) pending = readSetting(rs);
                }
            }
        }
        return new SettingView(current, pending);
    }

    public static PayPeriod periodFor(Connection conn, int userId, LocalDate date,
                                      String compensationType) throws SQLException {
        PayrollSetting setting = settingFor(conn, userId, date, compensationType);
        PeriodType type = usesSelectablePeriod(compensationType) ? setting.periodType() : PeriodType.SEMI_MONTHLY;
        BigDecimal limit = usesSelectablePeriod(compensationType) ? setting.workHourLimit() : DEFAULT_SEMI_MONTHLY_LIMIT;
        return periodFor(type, limit, date);
    }

    public static PayPeriod periodFor(PeriodType type, BigDecimal limit, LocalDate date) {
        LocalDate start;
        LocalDate end;
        int day = date.getDayOfMonth();
        if (type == PeriodType.WEEKLY) {
            start = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            end = start.plusDays(6);
        } else if (type == PeriodType.FOUR_MONTH_BLOCKS) {
            if (day <= 7) {
                start = date.withDayOfMonth(1);
                end = date.withDayOfMonth(7);
            } else if (day <= 15) {
                start = date.withDayOfMonth(8);
                end = date.withDayOfMonth(15);
            } else if (day <= 23) {
                start = date.withDayOfMonth(16);
                end = date.withDayOfMonth(23);
            } else {
                start = date.withDayOfMonth(24);
                end = date.withDayOfMonth(date.lengthOfMonth());
            }
        } else if (day <= 15) {
            start = date.withDayOfMonth(1);
            end = date.withDayOfMonth(15);
        } else {
            start = date.withDayOfMonth(16);
            end = date.withDayOfMonth(date.lengthOfMonth());
        }
        LocalDate payDate = end.plusDays(1);
        if (payDate.getDayOfWeek() == DayOfWeek.SUNDAY) payDate = payDate.plusDays(1);
        return new PayPeriod(start, end, payDate, type, normalizeLimit(limit, type));
    }

    public static PayrollSetting saveNextSetting(Connection conn, int userId, PeriodType type,
                                                 BigDecimal limit, LocalDate today) throws SQLException {
        ensureSchema(conn);
        BigDecimal normalized = normalizeLimit(limit, type);
        PayrollSetting current = settingFor(conn, userId, today, "HOURLY");
        LocalDate candidate = periodFor(current.periodType(), current.workHourLimit(), today).end().plusDays(1);
        while (!periodFor(type, normalized, candidate).start().equals(candidate)) {
            candidate = candidate.plusDays(1);
        }
        UUID settingId = null;
        try (PreparedStatement find = conn.prepareStatement("""
                SELECT setting_id FROM employee_payroll_settings
                WHERE user_id = ? AND effective_from > ? ORDER BY effective_from LIMIT 1
                """)) {
            find.setInt(1, userId);
            find.setDate(2, Date.valueOf(today));
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) settingId = rs.getObject(1, UUID.class);
            }
        }
        if (settingId == null) settingId = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO employee_payroll_settings (
                    setting_id, user_id, period_type, work_hour_limit, effective_from,
                    created_by_user_id, created_by_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (setting_id) DO UPDATE SET
                    period_type = EXCLUDED.period_type,
                    work_hour_limit = EXCLUDED.work_hour_limit,
                    effective_from = EXCLUDED.effective_from,
                    created_by_user_id = EXCLUDED.created_by_user_id,
                    created_by_name = EXCLUDED.created_by_name,
                    updated_at = CURRENT_TIMESTAMP
                """)) {
            ps.setObject(1, settingId);
            ps.setInt(2, userId);
            ps.setString(3, type.name());
            ps.setBigDecimal(4, normalized);
            ps.setDate(5, Date.valueOf(candidate));
            if (SessionManager.getCurrentUserId() == null) ps.setNull(6, Types.INTEGER);
            else ps.setInt(6, SessionManager.getCurrentUserId());
            ps.setString(7, SessionManager.getCurrentUserDisplayName());
            ps.executeUpdate();
        }
        return new PayrollSetting(settingId, userId, type, normalized, candidate,
                SessionManager.getCurrentUserId(), SessionManager.getCurrentUserDisplayName());
    }

    public static void ensureDefaultForEmployee(Connection conn, int userId) throws SQLException {
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO employee_payroll_settings (
                    setting_id, user_id, period_type, work_hour_limit, effective_from, created_by_name
                ) VALUES (?, ?, 'SEMI_MONTHLY', 80.00, ?, 'System default')
                ON CONFLICT (user_id, effective_from) DO NOTHING
                """)) {
            ps.setObject(1, UUID.randomUUID());
            ps.setInt(2, userId);
            ps.setDate(3, Date.valueOf(DEFAULT_EFFECTIVE_FROM));
            ps.executeUpdate();
        }
    }

    public static void setInitialSetting(Connection conn, int userId, PeriodType type,
                                         BigDecimal limit) throws SQLException {
        ensureDefaultForEmployee(conn, userId);
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE employee_payroll_settings
                SET period_type = ?, work_hour_limit = ?, updated_at = CURRENT_TIMESTAMP,
                    created_by_user_id = ?, created_by_name = ?
                WHERE user_id = ? AND effective_from = ?
                """)) {
            ps.setString(1, type.name());
            ps.setBigDecimal(2, normalizeLimit(limit, type));
            if (SessionManager.getCurrentUserId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, SessionManager.getCurrentUserId());
            ps.setString(4, SessionManager.getCurrentUserDisplayName());
            ps.setInt(5, userId);
            ps.setDate(6, Date.valueOf(DEFAULT_EFFECTIVE_FROM));
            ps.executeUpdate();
        }
    }

    public static boolean isHourly(String compensationType) {
        return compensationType == null || compensationType.isBlank()
                || "HOURLY".equalsIgnoreCase(compensationType.trim());
    }

    public static boolean usesSelectablePeriod(String compensationType) {
        return isHourly(compensationType) || "SALARY".equalsIgnoreCase(compensationType == null ? "" : compensationType.trim());
    }

    private static BigDecimal normalizeLimit(BigDecimal limit, PeriodType type) {
        BigDecimal value = limit == null ? type.defaultLimit() : limit;
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Work hour limit must be greater than zero.");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static PayrollSetting readSetting(ResultSet rs) throws SQLException {
        return new PayrollSetting(rs.getObject("setting_id", UUID.class), rs.getInt("user_id"),
                PeriodType.fromDatabase(rs.getString("period_type")), rs.getBigDecimal("work_hour_limit"),
                rs.getDate("effective_from").toLocalDate(),
                (Integer) rs.getObject("created_by_user_id"), rs.getString("created_by_name"));
    }

    private static PayrollSetting defaultSetting(int userId) {
        return new PayrollSetting(null, userId, PeriodType.SEMI_MONTHLY,
                DEFAULT_SEMI_MONTHLY_LIMIT, DEFAULT_EFFECTIVE_FROM, null, "System default");
    }
}
