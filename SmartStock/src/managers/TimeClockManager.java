package managers;

import data.DB;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TimeClockManager {

    private TimeClockManager() {
    }

    public static boolean canViewAllRecords() {
        return PermissionManager.hasPermission("TIME_CLOCK_MANAGEMENT")
                || PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT")
                || PermissionManager.hasPermission("ROLE_MANAGEMENT");
    }

    public static TimeClockDashboard loadDashboard(boolean canViewAllRecords) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            List<TimeRecord> records = loadRecords(conn, canViewAllRecords);
            return new TimeClockDashboard(buildRows(records), getCurrentStatus(conn));
        }
    }

    public static void clockIn() throws SQLException, TimeClockException {
        int userId = requireCurrentUserId();

        String sql = """
                INSERT INTO employee_time_clock (
                    user_id,
                    user_name,
                    location_id,
                    location_name,
                    work_date,
                    clock_in,
                    compensation_type,
                    pay_period_type,
                    hourly_wage,
                    salary_amount,
                    daily_salary
                )
                SELECT u.user_id,
                       COALESCE(u.full_name, u.username),
                       ?,
                       ?,
                       ?,
                       CURRENT_TIMESTAMP,
                       COALESCE(u.compensation_type, 'HOURLY'),
                       COALESCE(u.pay_period_type, 'SEMI_MONTHLY'),
                       COALESCE(u.hourly_wage, 0),
                       COALESCE(u.salary_amount, 0),
                       COALESCE(u.daily_salary, 0)
                FROM users u
                WHERE u.user_id = ?
                  AND NOT EXISTS (
                      SELECT 1
                      FROM employee_time_clock open_clock
                      WHERE open_clock.user_id = u.user_id
                        AND open_clock.clock_out IS NULL
                  )
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, SessionManager.getCurrentLocationId());
            ps.setString(2, SessionManager.getCurrentLocationName());
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now(ZoneId.of(currentStoreZoneId()))));
            ps.setInt(4, userId);

            int inserted = ps.executeUpdate();
            if (inserted == 0) {
                throw new TimeClockException("You are already clocked in.");
            }
        }
    }

    public static void lunchStart() throws SQLException, TimeClockException {
        updateCurrentClock("lunch_start");
    }

    public static void lunchEnd() throws SQLException, TimeClockException {
        updateCurrentClock("lunch_end");
    }

    public static void clockOut() throws SQLException, TimeClockException {
        updateCurrentClock("clock_out");
    }

    private static List<TimeRecord> loadRecords(Connection conn, boolean canViewAllRecords) throws SQLException {
        List<TimeRecord> records = new ArrayList<>();

        StringBuilder sql = new StringBuilder("""
                SELECT tc.clock_id,
                       tc.user_id,
                       COALESCE(tc.user_name, u.full_name, u.username, '') AS employee_name,
                       COALESCE(r.role_name, '') AS employee_role,
                       tc.work_date,
                       (tc.clock_in AT TIME ZONE COALESCE(NULLIF(l.timezone, ''), ?)) AS local_clock_in,
                       (tc.lunch_start AT TIME ZONE COALESCE(NULLIF(l.timezone, ''), ?)) AS local_lunch_start,
                       (tc.lunch_end AT TIME ZONE COALESCE(NULLIF(l.timezone, ''), ?)) AS local_lunch_end,
                       (tc.clock_out AT TIME ZONE COALESCE(NULLIF(l.timezone, ''), ?)) AS local_clock_out,
                       COALESCE(tc.compensation_type, u.compensation_type, 'HOURLY') AS compensation_type,
                       COALESCE(tc.pay_period_type, u.pay_period_type, 'SEMI_MONTHLY') AS pay_period_type,
                       COALESCE(tc.hourly_wage, u.hourly_wage, 0) AS hourly_wage,
                       COALESCE(tc.salary_amount, u.salary_amount, 0) AS salary_amount,
                       COALESCE(tc.daily_salary, u.daily_salary, 0) AS daily_salary,
                       COALESCE(tc.location_name, l.name, '') AS location_name
                FROM employee_time_clock tc
                LEFT JOIN users u ON u.user_id = tc.user_id
                LEFT JOIN roles r ON r.role_id = u.role_id
                LEFT JOIN locations l ON l.location_id = tc.location_id
                """);

        if (!canViewAllRecords) {
            sql.append(" WHERE tc.user_id = ? ");
        }

        sql.append(" ORDER BY tc.work_date DESC, tc.clock_in DESC, tc.clock_id DESC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setString(1, currentStoreZoneId());
            ps.setString(2, currentStoreZoneId());
            ps.setString(3, currentStoreZoneId());
            ps.setString(4, currentStoreZoneId());
            if (!canViewAllRecords) {
                ps.setInt(5, requireCurrentUserId());
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    records.add(new TimeRecord(
                            rs.getInt("clock_id"),
                            rs.getInt("user_id"),
                            rs.getString("employee_name"),
                            rs.getString("employee_role"),
                            rs.getDate("work_date").toLocalDate(),
                            toLocalDateTime(rs.getTimestamp("local_clock_in")),
                            toLocalDateTime(rs.getTimestamp("local_lunch_start")),
                            toLocalDateTime(rs.getTimestamp("local_lunch_end")),
                            toLocalDateTime(rs.getTimestamp("local_clock_out")),
                            rs.getString("compensation_type"),
                            rs.getString("pay_period_type"),
                            rs.getBigDecimal("hourly_wage"),
                            rs.getBigDecimal("salary_amount"),
                            rs.getBigDecimal("daily_salary"),
                            rs.getString("location_name")
                    ));
                }
            }
        }

        return records;
    }

    private static List<TimeClockRow> buildRows(List<TimeRecord> records) {
        Map<String, BigDecimal> payPeriodHours = new HashMap<>();
        Map<String, BigDecimal> hourlyPayPeriodPay = new HashMap<>();
        Map<String, BigDecimal> dailyPayPeriodPay = new HashMap<>();
        Set<String> paidDailyWorkDates = new HashSet<>();

        for (TimeRecord record : records) {
            PayPeriod payPeriod = payPeriodFor(record.workDate, record.payPeriodType);
            String key = record.userId + "|" + payPeriod.start();
            BigDecimal dailyHours = calculateHours(record);
            payPeriodHours.merge(key, dailyHours, BigDecimal::add);
            if (record.isHourly()) {
                hourlyPayPeriodPay.merge(key, dailyHours.multiply(record.hourlyWage), BigDecimal::add);
            } else if (record.isDaily()) {
                String dailyKey = key + "|" + record.workDate;
                if (paidDailyWorkDates.add(dailyKey)) {
                    dailyPayPeriodPay.merge(key, record.dailySalary, BigDecimal::add);
                }
            }
        }

        List<TimeClockRow> rows = new ArrayList<>();
        for (TimeRecord record : records) {
            PayPeriod payPeriod = payPeriodFor(record.workDate, record.payPeriodType);
            String key = record.userId + "|" + payPeriod.start();
            BigDecimal dailyHours = calculateHours(record);
            BigDecimal totalHours = payPeriodHours.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal totalPay;
            if (record.isSalary()) {
                totalPay = record.salaryAmount;
            } else if (record.isDaily()) {
                totalPay = dailyPayPeriodPay.getOrDefault(key, BigDecimal.ZERO);
            } else {
                totalPay = hourlyPayPeriodPay.getOrDefault(key, BigDecimal.ZERO);
            }

            rows.add(new TimeClockRow(
                    record.clockId,
                    record.userId,
                    record.employeeName,
                    record.employeeRole,
                    record.workDate,
                    record.clockIn,
                    record.lunchStart,
                    record.lunchEnd,
                    record.clockOut,
                    dailyHours,
                    payPeriod.start(),
                    payPeriod.end(),
                    payPeriod.payDate(),
                    totalHours,
                    record.compensationType,
                    record.payPeriodType,
                    record.hourlyWage,
                    record.salaryAmount,
                    record.dailySalary,
                    totalPay,
                    record.locationName
            ));
        }
        return rows;
    }

    public static PayrollDashboard loadPayrollDashboard() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            List<TimeClockRow> rows = buildRows(loadRecords(conn, true));
            Map<String, PayrollPaymentStatus> paidStatuses = loadPayrollPaymentStatuses(conn);
            Map<String, PayrollSummary> summariesByKey = new HashMap<>();

            for (TimeClockRow row : rows) {
                String key = payrollKey(row.userId(), row.payPeriodStart(), row.payPeriodEnd());
                PayrollSummary existing = summariesByKey.get(key);
                if (existing == null) {
                    PayrollPaymentStatus paidStatus = paidStatuses.get(key);
                    summariesByKey.put(key, new PayrollSummary(
                            row.userId(),
                            row.employeeName(),
                            row.employeeRole(),
                            row.payPeriodStart(),
                            row.payPeriodEnd(),
                            row.payDate(),
                            row.totalHours(),
                            row.totalPay(),
                            1,
                            row.compensationType(),
                            row.locationName(),
                            paidStatus != null,
                            paidStatus == null ? null : paidStatus.paidAt(),
                            paidStatus == null ? "" : paidStatus.paidByName()
                    ));
                } else {
                    summariesByKey.put(key, new PayrollSummary(
                            existing.userId(),
                            existing.employeeName(),
                            existing.employeeRole(),
                            existing.payPeriodStart(),
                            existing.payPeriodEnd(),
                            existing.payDate(),
                            row.totalHours(),
                            row.totalPay(),
                            existing.recordCount() + 1,
                            existing.compensationType(),
                            mergeLocations(existing.locationName(), row.locationName()),
                            existing.paid(),
                            existing.paidAt(),
                            existing.paidByName()
                    ));
                }
            }

            List<PayrollSummary> summaries = new ArrayList<>(summariesByKey.values());
            summaries.sort((a, b) -> {
                int dateCompare = b.payPeriodStart().compareTo(a.payPeriodStart());
                if (dateCompare != 0) {
                    return dateCompare;
                }
                return a.employeeName().compareToIgnoreCase(b.employeeName());
            });

            return new PayrollDashboard(rows, summaries);
        }
    }

    public static void markPayrollPaid(PayrollSummary summary) throws SQLException {
        String sql = """
                INSERT INTO payroll_payments (
                    user_id,
                    employee_name,
                    employee_role,
                    pay_period_start,
                    pay_period_end,
                    pay_date,
                    total_hours,
                    total_pay,
                    record_count,
                    compensation_type,
                    location_name,
                    paid_at,
                    paid_by_user_id,
                    paid_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)
                ON CONFLICT (user_id, pay_period_start, pay_period_end)
                DO UPDATE SET
                    employee_name = EXCLUDED.employee_name,
                    employee_role = EXCLUDED.employee_role,
                    pay_date = EXCLUDED.pay_date,
                    total_hours = EXCLUDED.total_hours,
                    total_pay = EXCLUDED.total_pay,
                    record_count = EXCLUDED.record_count,
                    compensation_type = EXCLUDED.compensation_type,
                    location_name = EXCLUDED.location_name,
                    paid_at = CURRENT_TIMESTAMP,
                    paid_by_user_id = EXCLUDED.paid_by_user_id,
                    paid_by_name = EXCLUDED.paid_by_name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, summary.userId());
            ps.setString(2, summary.employeeName());
            ps.setString(3, summary.employeeRole());
            ps.setDate(4, java.sql.Date.valueOf(summary.payPeriodStart()));
            ps.setDate(5, java.sql.Date.valueOf(summary.payPeriodEnd()));
            ps.setDate(6, java.sql.Date.valueOf(summary.payDate()));
            ps.setBigDecimal(7, summary.totalHours());
            ps.setBigDecimal(8, summary.totalPay());
            ps.setInt(9, summary.recordCount());
            ps.setString(10, summary.compensationType());
            ps.setString(11, summary.locationName());
            setNullableInteger(ps, 12, SessionManager.getCurrentUserId());
            ps.setString(13, SessionManager.getCurrentUserDisplayName());
            ps.executeUpdate();
        }
    }

    private static Map<String, PayrollPaymentStatus> loadPayrollPaymentStatuses(Connection conn) throws SQLException {
        Map<String, PayrollPaymentStatus> statuses = new HashMap<>();
        String sql = """
                SELECT user_id,
                       pay_period_start,
                       pay_period_end,
                       (paid_at AT TIME ZONE ?) AS local_paid_at,
                       paid_by_name
                FROM payroll_payments
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, currentStoreZoneId());
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                statuses.put(
                        payrollKey(
                                rs.getInt("user_id"),
                                rs.getDate("pay_period_start").toLocalDate(),
                                rs.getDate("pay_period_end").toLocalDate()
                        ),
                        new PayrollPaymentStatus(
                                toLocalDateTime(rs.getTimestamp("local_paid_at")),
                                rs.getString("paid_by_name")
                        )
                );
            }
            }
        }

        return statuses;
    }

    private static ClockStatus getCurrentStatus(Connection conn) throws SQLException {
        CurrentClock current = getCurrentClock(conn);
        if (current == null) {
            return new ClockStatus(ClockState.NOT_CLOCKED_IN, true, false, false, false);
        }

        if (current.clockOut != null) {
            return new ClockStatus(ClockState.CLOCKED_OUT, false, false, false, false);
        }
        if (current.lunchStart != null && current.lunchEnd == null) {
            return new ClockStatus(ClockState.ON_LUNCH, false, false, true, false);
        }
        if (current.clockIn != null) {
            return new ClockStatus(ClockState.CLOCKED_IN, false, true, false, true);
        }

        return new ClockStatus(ClockState.NOT_CLOCKED_IN, true, false, false, false);
    }

    private static void updateCurrentClock(String columnName) throws SQLException, TimeClockException {
        if (!List.of("lunch_start", "lunch_end", "clock_out").contains(columnName)) {
            return;
        }

        String sql = "UPDATE employee_time_clock SET " + columnName + " = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE clock_id = ?";

        try (Connection conn = DB.getConnection()) {
            CurrentClock current = getCurrentClock(conn);
            if (current == null || current.clockOut != null) {
                throw new TimeClockException("Clock in before using this punch.");
            }
            if ("lunch_start".equals(columnName) && current.lunchStart != null) {
                throw new TimeClockException("Lunch start has already been recorded.");
            }
            if ("lunch_end".equals(columnName) && (current.lunchStart == null || current.lunchEnd != null)) {
                throw new TimeClockException("Lunch start must be recorded before lunch end.");
            }
            if ("clock_out".equals(columnName) && current.lunchStart != null && current.lunchEnd == null) {
                throw new TimeClockException("Punch lunch end before clocking out.");
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, current.clockId);
                ps.executeUpdate();
            }
        }
    }

    private static CurrentClock getCurrentClock(Connection conn) throws SQLException {
        String sql = """
                SELECT clock_id, clock_in, lunch_start, lunch_end, clock_out
                FROM employee_time_clock
                WHERE user_id = ?
                  AND clock_out IS NULL
                ORDER BY clock_id DESC
                LIMIT 1
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requireCurrentUserId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CurrentClock(
                            rs.getInt("clock_id"),
                            toLocalDateTime(rs.getTimestamp("clock_in")),
                            toLocalDateTime(rs.getTimestamp("lunch_start")),
                            toLocalDateTime(rs.getTimestamp("lunch_end")),
                            toLocalDateTime(rs.getTimestamp("clock_out"))
                    );
                }
            }
        }

        return null;
    }

    private static int requireCurrentUserId() {
        Integer userId = SessionManager.getCurrentUserId();
        if (userId == null) {
            throw new IllegalStateException("No employee is logged in.");
        }
        return userId;
    }

    private static BigDecimal calculateHours(TimeRecord record) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(currentStoreZoneId()));
        LocalDateTime shiftEnd = record.clockOut == null ? now : record.clockOut;
        BigDecimal totalMinutes = BigDecimal.valueOf(minutesBetween(record.clockIn, shiftEnd));

        if (record.lunchStart != null) {
            LocalDateTime lunchEnd = record.lunchEnd == null ? now : record.lunchEnd;
            totalMinutes = totalMinutes.subtract(BigDecimal.valueOf(minutesBetween(record.lunchStart, lunchEnd)));
        }

        if (totalMinutes.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }

        return totalMinutes.divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return Duration.between(start, end).toMinutes();
    }

    private static PayPeriod payPeriodFor(LocalDate date, String payPeriodType) {
        if ("WEEKLY".equalsIgnoreCase(payPeriodType)) {
            LocalDate start = date.with(DayOfWeek.MONDAY);
            LocalDate end = start.plusDays(6);
            return new PayPeriod(start, end, end.plusDays(1));
        }

        if (date.getDayOfMonth() <= 15) {
            LocalDate start = date.withDayOfMonth(1);
            LocalDate end = date.withDayOfMonth(15);
            return new PayPeriod(start, end, end.plusDays(1));
        }

        LocalDate start = date.withDayOfMonth(16);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        return new PayPeriod(start, end, end.plusDays(1));
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String currentStoreZoneId() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone.trim()).getId();
            } catch (Exception ignored) {
                // Fall back to the device zone if the stored value is invalid.
            }
        }
        return ZoneId.systemDefault().getId();
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String payrollKey(int userId, LocalDate payPeriodStart, LocalDate payPeriodEnd) {
        return userId + "|" + payPeriodStart + "|" + payPeriodEnd;
    }

    public enum ClockState {
        NOT_CLOCKED_IN,
        CLOCKED_IN,
        ON_LUNCH,
        CLOCKED_OUT
    }

    public record TimeClockDashboard(List<TimeClockRow> rows, ClockStatus status) {
    }

    public record PayrollDashboard(List<TimeClockRow> timeRows, List<PayrollSummary> summaries) {
    }

    public record PayrollSummary(
            int userId,
            String employeeName,
            String employeeRole,
            LocalDate payPeriodStart,
            LocalDate payPeriodEnd,
            LocalDate payDate,
            BigDecimal totalHours,
            BigDecimal totalPay,
            int recordCount,
            String compensationType,
            String locationName,
            boolean paid,
            LocalDateTime paidAt,
            String paidByName
    ) {
        public PayrollSummary {
            if (paidByName == null) {
                paidByName = "";
            }
        }
    }

    public record ClockStatus(
            ClockState state,
            boolean canClockIn,
            boolean canLunchStart,
            boolean canLunchEnd,
            boolean canClockOut
    ) {
    }

    public record TimeClockRow(
            int clockId,
            int userId,
            String employeeName,
            String employeeRole,
            LocalDate workDate,
            LocalDateTime clockIn,
            LocalDateTime lunchStart,
            LocalDateTime lunchEnd,
            LocalDateTime clockOut,
            BigDecimal dailyHours,
            LocalDate payPeriodStart,
            LocalDate payPeriodEnd,
            LocalDate payDate,
            BigDecimal totalHours,
            String compensationType,
            String payPeriodType,
            BigDecimal hourlyWage,
            BigDecimal salaryAmount,
            BigDecimal dailySalary,
            BigDecimal totalPay,
            String locationName
    ) {
    }

    public static class TimeClockException extends Exception {
        public TimeClockException(String message) {
            super(message);
        }
    }

    private record PayPeriod(LocalDate start, LocalDate end, LocalDate payDate) {
    }

    private record PayrollPaymentStatus(LocalDateTime paidAt, String paidByName) {
    }

    private static String mergeLocations(String current, String next) {
        if (next == null || next.isBlank()) {
            return current == null ? "" : current;
        }
        if (current == null || current.isBlank()) {
            return next;
        }
        if (current.contains(next)) {
            return current;
        }
        return current + ", " + next;
    }

    private record TimeRecord(
            int clockId,
            int userId,
            String employeeName,
            String employeeRole,
            LocalDate workDate,
            LocalDateTime clockIn,
            LocalDateTime lunchStart,
            LocalDateTime lunchEnd,
            LocalDateTime clockOut,
            String compensationType,
            String payPeriodType,
            BigDecimal hourlyWage,
            BigDecimal salaryAmount,
            BigDecimal dailySalary,
            String locationName
    ) {
        private TimeRecord {
            if (compensationType == null || compensationType.isBlank()) {
                compensationType = "HOURLY";
            } else {
                compensationType = compensationType.trim().toUpperCase();
            }
            if (payPeriodType == null || payPeriodType.isBlank()) {
                payPeriodType = "SEMI_MONTHLY";
            } else {
                payPeriodType = payPeriodType.trim().toUpperCase();
            }
            if (hourlyWage == null) {
                hourlyWage = BigDecimal.ZERO;
            }
            if (salaryAmount == null) {
                salaryAmount = BigDecimal.ZERO;
            }
            if (dailySalary == null) {
                dailySalary = BigDecimal.ZERO;
            }
            if (employeeName == null) {
                employeeName = "";
            }
            if (employeeRole == null) {
                employeeRole = "";
            }
            if (locationName == null) {
                locationName = "";
            }
        }

        private boolean isSalary() {
            return "SALARY".equalsIgnoreCase(compensationType);
        }

        private boolean isDaily() {
            return "DAILY".equalsIgnoreCase(compensationType);
        }

        private boolean isHourly() {
            return !isSalary() && !isDaily();
        }
    }

    private record CurrentClock(
            int clockId,
            LocalDateTime clockIn,
            LocalDateTime lunchStart,
            LocalDateTime lunchEnd,
            LocalDateTime clockOut
    ) {
    }
}
