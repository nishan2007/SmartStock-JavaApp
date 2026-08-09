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
        SchemaContractService.requireLocalReady(conn);
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
