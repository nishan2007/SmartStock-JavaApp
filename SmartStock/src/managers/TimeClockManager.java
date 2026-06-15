package managers;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.BalanceSheetService;
import services.ManagerApprovalService;
import services.SyncOutboxService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    public static final String MULTIPLE_SESSION_OVERRIDE_PERMISSION = "TIME_CLOCK_OVERRIDE";

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

    public static boolean requiresMultipleSessionOverride() throws SQLException {
        int userId = requireCurrentUserId();
        LocalDate workDate = LocalDate.now(ZoneId.of(currentStoreZoneId()));
        try (Connection conn = DB.getConnection()) {
            ensureTimeClockOverrideSchema(conn);
            return hasClosedSessionForDate(conn, userId, workDate);
        }
    }

    public static boolean currentUserCanApproveMultipleSessionOverride() throws SQLException {
        int userId = requireCurrentUserId();
        try (Connection conn = DB.getConnection()) {
            ensureTimeClockOverrideSchema(conn);
            return userHasPermission(conn, userId, MULTIPLE_SESSION_OVERRIDE_PERMISSION);
        }
    }

    public static void clockIn() throws SQLException, TimeClockException {
        clockIn(null);
    }

    public static void clockIn(ManagerApprovalService.ApprovalResult approval) throws SQLException, TimeClockException {
        int userId = requireCurrentUserId();
        LocalDate workDate = LocalDate.now(ZoneId.of(currentStoreZoneId()));

        String sql = """
                INSERT INTO employee_time_clock (
                    user_id,
                    user_name,
                    location_id,
                    location_name,
                    work_date,
                    clock_in,
                    multiple_session_override_required,
                    multiple_session_override_reason,
                    multiple_session_override_by_user_id,
                    multiple_session_override_by_name
                )
                SELECT u.user_id,
                       COALESCE(u.full_name, u.username),
                       ?,
                       ?,
                       ?,
                       CURRENT_TIMESTAMP,
                       ?,
                       ?,
                       ?,
                       ?
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
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ensureTimeClockOverrideSchema(conn);
            boolean requiresOverride = hasClosedSessionForDate(conn, userId, workDate);
            boolean currentUserCanOverride = userHasPermission(conn, userId, MULTIPLE_SESSION_OVERRIDE_PERMISSION);
            if (requiresOverride && !currentUserCanOverride && approval == null) {
                throw new TimeClockException("Manager approval is required after a completed time clock session today.");
            }

            Integer overrideByUserId = null;
            String overrideByName = null;
            String overrideReason = null;
            if (requiresOverride) {
                if (approval != null) {
                    overrideByUserId = approval.approvedByUserId();
                    overrideByName = approval.approvedByName();
                    overrideReason = approval.reason();
                } else {
                    overrideByUserId = userId;
                    overrideByName = SessionManager.getCurrentUserDisplayName();
                    overrideReason = "Current user has " + MULTIPLE_SESSION_OVERRIDE_PERMISSION + " permission.";
                }
            }

            setNullableInteger(ps, 1, SessionManager.getCurrentLocationId());
            ps.setString(2, SessionManager.getCurrentLocationName());
            ps.setDate(3, java.sql.Date.valueOf(workDate));
            ps.setBoolean(4, requiresOverride);
            ps.setString(5, overrideReason);
            setNullableInteger(ps, 6, overrideByUserId);
            ps.setString(7, overrideByName);
            ps.setInt(8, userId);

            int inserted = ps.executeUpdate();
            if (inserted == 0) {
                throw new TimeClockException("You are already clocked in.");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    SyncOutboxService.recordEvent(conn, "TIME_CLOCK_PUNCH_CREATED", Map.of(
                            "clock_id", keys.getInt(1),
                            "action", "CLOCK_IN",
                            "multiple_session_override_required", requiresOverride,
                            "override_by_user_id", overrideByUserId == null ? "" : overrideByUserId,
                            "override_by_name", overrideByName == null ? "" : overrideByName,
                            "override_reason", overrideReason == null ? "" : overrideReason,
                            "user_id", userId,
                            "location_id", SessionManager.getCurrentLocationId() == null ? "" : SessionManager.getCurrentLocationId()
                    ));
                }
            }
        }
    }

    private static boolean userHasPermission(Connection conn, int userId, String permissionKey) throws SQLException {
        String sql = """
                SELECT 1
                FROM users u
                JOIN roles r ON r.role_id = u.role_id
                JOIN role_permissions rp ON rp.role_id = r.role_id
                JOIN permissions p ON p.permission_id = rp.permission_id
                WHERE u.user_id = ?
                  AND UPPER(p.permission_key) = UPPER(?)
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, permissionKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasClosedSessionForDate(Connection conn, int userId, LocalDate workDate) throws SQLException {
        String sql = """
                SELECT 1
                FROM employee_time_clock
                WHERE user_id = ?
                  AND work_date = ?
                  AND clock_out IS NOT NULL
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setDate(2, java.sql.Date.valueOf(workDate));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
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
                       COALESCE(u.compensation_type::TEXT, 'HOURLY') AS compensation_type,
                       COALESCE(u.salary, 0) AS salary,
                       tc.total_hours_worked,
                       tc.total_earned,
                       tc.location_id,
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
                            rs.getBigDecimal("salary"),
                            rs.getBigDecimal("total_hours_worked"),
                            rs.getBigDecimal("total_earned"),
                            (Integer) rs.getObject("location_id"),
                            rs.getString("location_name")
                    ));
                }
            }
        }

        return records;
    }

    private static List<TimeClockRow> buildRows(List<TimeRecord> records) {
        List<TimeSegment> segments = splitRecordsByWorkDate(records);
        Map<String, BigDecimal> payPeriodHours = new HashMap<>();
        Map<String, Integer> dailyPaidClockIds = new HashMap<>();

        for (TimeSegment segment : segments) {
            PayPeriod payPeriod = payPeriodFor(segment.workDate);
            String key = segment.record.userId + "|" + payPeriod.start();
            payPeriodHours.merge(key, segment.hours, BigDecimal::add);

            if (segment.record.isDaily() && segment.record.clockOut != null && segment.hours.compareTo(BigDecimal.ZERO) > 0) {
                String dailyKey = dailyPayKey(segment.record.userId, segment.workDate);
                dailyPaidClockIds.merge(dailyKey, segment.record.clockId, Math::min);
            }
        }

        List<TimeClockRow> rows = new ArrayList<>();
        for (TimeSegment segment : segments) {
            TimeRecord record = segment.record;
            PayPeriod payPeriod = payPeriodFor(segment.workDate);
            String key = record.userId + "|" + payPeriod.start();
            BigDecimal totalHours = payPeriodHours.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal segmentPay = segmentPay(segment, dailyPaidClockIds);

            rows.add(new TimeClockRow(
                    record.clockId,
                    record.userId,
                    record.employeeName,
                    record.employeeRole,
                    segment.workDate,
                    segment.clockIn,
                    segment.lunchStart,
                    segment.lunchEnd,
                    segment.clockOut,
                    segment.hours,
                    payPeriod.start(),
                    payPeriod.end(),
                    payPeriod.payDate(),
                    totalHours,
                    record.compensationType,
                    record.salary,
                    segmentPay,
                    record.locationId,
                    record.locationName,
                    record.clockIn,
                    record.lunchStart,
                    record.lunchEnd,
                    record.clockOut
            ));
        }
        rows.sort((a, b) -> {
            int dateCompare = b.workDate().compareTo(a.workDate());
            if (dateCompare != 0) {
                return dateCompare;
            }
            int clockCompare = nullSafeDateTime(b.clockIn()).compareTo(nullSafeDateTime(a.clockIn()));
            if (clockCompare != 0) {
                return clockCompare;
            }
            return Integer.compare(b.clockId(), a.clockId());
        });
        return rows;
    }

    public static PayrollDashboard loadPayrollDashboard() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            List<TimeClockRow> rows = buildRows(loadRecords(conn, true));
            ensurePayrollPaymentsSchema(conn);
            Map<String, PayrollPaymentStatus> paidStatuses = loadPayrollPaymentStatuses(conn);
            Map<String, PayrollSummary> summariesByKey = new HashMap<>();
            Map<String, Set<LocalDate>> workedDatesByKey = new HashMap<>();

            for (TimeClockRow row : rows) {
                String key = payrollKey(row.userId(), row.payPeriodStart(), row.payPeriodEnd());
                Set<LocalDate> workedDates = workedDatesByKey.computeIfAbsent(key, ignored -> new HashSet<>());
                workedDates.add(row.workDate());
                PayrollSummary existing = summariesByKey.get(key);
                BigDecimal payrollPay = payrollPay(row, existing == null);
                if (existing == null) {
                    PayrollPaymentStatus paidStatus = paidStatuses.get(key);
                    summariesByKey.put(key, new PayrollSummary(
                            row.userId(),
                            row.employeeName(),
                            row.employeeRole(),
                            row.payPeriodStart(),
                            row.payPeriodEnd(),
                            row.payDate(),
                            workedDates.size(),
                            row.dailyHours(),
                            payrollPay,
                            1,
                            row.compensationType(),
                            row.locationId(),
                            row.locationName(),
                            isFullyPaid(payrollPay, paidStatus),
                            paidStatus == null ? null : paidStatus.paidAt(),
                            paidStatus == null ? "" : paidStatus.paidByName(),
                            paidStatus == null ? BigDecimal.ZERO : paidStatus.paidAmount(),
                            amountDue(payrollPay, paidStatus)
                    ));
                } else {
                    BigDecimal totalPay = existing.totalPay().add(payrollPay);
                    PayrollPaymentStatus paidStatus = paidStatuses.get(key);
                    summariesByKey.put(key, new PayrollSummary(
                            existing.userId(),
                            existing.employeeName(),
                            existing.employeeRole(),
                            existing.payPeriodStart(),
                            existing.payPeriodEnd(),
                            existing.payDate(),
                            workedDates.size(),
                            existing.totalHours().add(row.dailyHours()),
                            totalPay,
                            existing.recordCount() + 1,
                            existing.compensationType(),
                            mergeLocationId(existing.locationId(), row.locationId()),
                            mergeLocations(existing.locationName(), row.locationName()),
                            isFullyPaid(totalPay, paidStatus),
                            paidStatus == null ? null : paidStatus.paidAt(),
                            paidStatus == null ? "" : paidStatus.paidByName(),
                            paidStatus == null ? BigDecimal.ZERO : paidStatus.paidAmount(),
                            amountDue(totalPay, paidStatus)
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
        BigDecimal paymentAmount = defaultZero(summary.amountDue()).setScale(2, RoundingMode.HALF_UP);
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("This payroll period is already fully paid.");
        }
        String sql = """
                INSERT INTO payroll_payments (
                    user_id,
                    location_id,
                    employee_name,
                    employee_role,
                    pay_period_start,
                    pay_period_end,
                    payment_number,
                    pay_date,
                    days_worked,
                    total_hours,
                    total_pay,
                    record_count,
                    compensation_type,
                    location_name,
                    paid_at,
                    paid_by_user_id,
                    paid_by_name
                )
                SELECT ?, ?, ?, ?, ?, ?,
                       COALESCE(MAX(payment_number), 0) + 1,
                       ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?
                FROM payroll_payments
                WHERE user_id = ?
                  AND pay_period_start = ?
                  AND pay_period_end = ?
                RETURNING payroll_payment_id
                """;

        try (Connection conn = DB.getConnection()) {
            ensurePayrollPaymentsSchema(conn);
            Integer payrollLocationId = summary.locationId() == null
                    ? SessionManager.getCurrentLocationId()
                    : summary.locationId();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, summary.userId());
                setNullableInteger(ps, 2, payrollLocationId);
                ps.setString(3, summary.employeeName());
                ps.setString(4, summary.employeeRole());
                ps.setDate(5, java.sql.Date.valueOf(summary.payPeriodStart()));
                ps.setDate(6, java.sql.Date.valueOf(summary.payPeriodEnd()));
                ps.setDate(7, java.sql.Date.valueOf(summary.payDate()));
                ps.setInt(8, summary.daysWorked());
                ps.setBigDecimal(9, summary.totalHours());
                ps.setBigDecimal(10, paymentAmount);
                ps.setInt(11, summary.recordCount());
                ps.setString(12, summary.compensationType());
                ps.setString(13, summary.locationName());
                setNullableInteger(ps, 14, SessionManager.getCurrentUserId());
                ps.setString(15, SessionManager.getCurrentUserDisplayName());
                ps.setInt(16, summary.userId());
                ps.setDate(17, java.sql.Date.valueOf(summary.payPeriodStart()));
                ps.setDate(18, java.sql.Date.valueOf(summary.payPeriodEnd()));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        LocalDate paidDate = LocalDate.now(ZoneId.of(currentStoreZoneId()));
                        BalanceSheetService.recordPayrollExpense(
                                rs.getLong("payroll_payment_id"),
                                paymentAmount,
                                summary.employeeName(),
                                paidDate,
                                "Pay period " + summary.payPeriodStart() + " - " + summary.payPeriodEnd()
                                        + "; scheduled pay date " + summary.payDate(),
                                payrollLocationId
                        );
                    }
                }
            }
        }
    }

    private static Map<String, PayrollPaymentStatus> loadPayrollPaymentStatuses(Connection conn) throws SQLException {
        Map<String, PayrollPaymentStatus> statuses = new HashMap<>();
        String sql = """
                WITH ranked AS (
                    SELECT user_id,
                           pay_period_start,
                           pay_period_end,
                           COALESCE(total_pay, 0) AS payment_amount,
                           (paid_at AT TIME ZONE ?) AS local_paid_at,
                           paid_by_name,
                           ROW_NUMBER() OVER (
                               PARTITION BY user_id, pay_period_start, pay_period_end
                               ORDER BY paid_at DESC, payment_number DESC, payroll_payment_id DESC
                           ) AS latest_rank
                    FROM payroll_payments
                )
                SELECT user_id,
                       pay_period_start,
                       pay_period_end,
                       SUM(payment_amount) AS paid_amount,
                       MAX(local_paid_at) AS local_paid_at,
                       MAX(paid_by_name) FILTER (WHERE latest_rank = 1) AS paid_by_name
                FROM ranked
                GROUP BY user_id, pay_period_start, pay_period_end
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
                                    rs.getString("paid_by_name"),
                                    defaultZero(rs.getBigDecimal("paid_amount"))
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
        String clockOutSql = """
                WITH calculated AS (
                    SELECT tc.clock_id,
                           ROUND(
                               GREATEST(EXTRACT(EPOCH FROM (
                                   CURRENT_TIMESTAMP - tc.clock_in
                                   - CASE
                                       WHEN tc.lunch_start IS NOT NULL AND tc.lunch_end IS NOT NULL
                                           THEN tc.lunch_end - tc.lunch_start
                                       ELSE INTERVAL '0 seconds'
                                     END
                               )) / 3600, 0)::NUMERIC,
                               2
                           ) AS rounded_hours,
                           COALESCE(u.compensation_type::TEXT, 'HOURLY') AS compensation_type,
                           COALESCE(u.salary, 0) AS salary,
                           NOT EXISTS (
                               SELECT 1
                               FROM employee_time_clock paid_daily
                               WHERE paid_daily.user_id = tc.user_id
                                 AND paid_daily.work_date = tc.work_date
                                 AND paid_daily.clock_out IS NOT NULL
                                 AND paid_daily.total_earned IS NOT NULL
                           ) AS should_pay_daily
                    FROM employee_time_clock tc
                    JOIN users u ON u.user_id = tc.user_id
                    WHERE tc.clock_id = ?
                )
                UPDATE employee_time_clock tc
                SET clock_out = CURRENT_TIMESTAMP,
                    total_hours_worked = calculated.rounded_hours,
                    total_earned = ROUND(
                        CASE calculated.compensation_type
                            WHEN 'DAILY' THEN CASE WHEN calculated.should_pay_daily THEN calculated.salary ELSE NULL END
                            WHEN 'SALARY' THEN NULL
                            ELSE calculated.salary * calculated.rounded_hours
                        END,
                        2
                    ),
                    updated_at = CURRENT_TIMESTAMP
                FROM calculated
                WHERE tc.clock_id = calculated.clock_id
                """;

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

            try (PreparedStatement ps = conn.prepareStatement("clock_out".equals(columnName) ? clockOutSql : sql)) {
                ps.setInt(1, current.clockId);
                ps.executeUpdate();
            }
            SyncOutboxService.recordEvent(conn, "TIME_CLOCK_PUNCH_CREATED", Map.of(
                    "clock_id", current.clockId,
                    "action", columnName.toUpperCase(),
                    "user_id", SessionManager.getCurrentUserId() == null ? "" : SessionManager.getCurrentUserId(),
                    "location_id", SessionManager.getCurrentLocationId() == null ? "" : SessionManager.getCurrentLocationId()
            ));
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

    private static BigDecimal sessionHours(TimeRecord record) {
        if (record.clockOut != null && record.totalHoursWorked != null) {
            return record.totalHoursWorked.setScale(2, RoundingMode.HALF_UP);
        }
        return calculateHours(record);
    }

    private static List<TimeSegment> splitRecordsByWorkDate(List<TimeRecord> records) {
        List<TimeSegment> segments = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.of(currentStoreZoneId()));
        for (TimeRecord record : records) {
            segments.addAll(splitRecordByWorkDate(record, now));
        }
        return segments;
    }

    private static List<TimeSegment> splitRecordByWorkDate(TimeRecord record, LocalDateTime now) {
        List<TimeSegment> segments = new ArrayList<>();
        if (record.clockIn == null) {
            return segments;
        }

        LocalDateTime shiftEnd = record.clockOut == null ? now : record.clockOut;
        if (!shiftEnd.isAfter(record.clockIn)) {
            segments.add(new TimeSegment(
                    record,
                    record.workDate,
                    record.clockIn,
                    null,
                    null,
                    record.clockOut,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            ));
            return segments;
        }

        LocalDate segmentDate = record.clockIn.toLocalDate();
        while (segmentDate.atStartOfDay().isBefore(shiftEnd)) {
            LocalDateTime dayStart = segmentDate.atStartOfDay();
            LocalDateTime dayEnd = segmentDate.plusDays(1).atStartOfDay();
            LocalDateTime segmentStart = maxDateTime(record.clockIn, dayStart);
            LocalDateTime segmentEnd = minDateTime(shiftEnd, dayEnd);

            if (segmentEnd.isAfter(segmentStart)) {
                long totalMinutes = minutesBetween(segmentStart, segmentEnd);
                long lunchMinutes = lunchOverlapMinutes(record, segmentStart, segmentEnd, shiftEnd);
                long workedMinutes = Math.max(0, totalMinutes - lunchMinutes);
                BigDecimal hours = BigDecimal.valueOf(workedMinutes)
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

                LocalDateTime segmentLunchStart = null;
                LocalDateTime segmentLunchEnd = null;
                if (lunchMinutes > 0 && record.lunchStart != null) {
                    segmentLunchStart = maxDateTime(record.lunchStart, segmentStart);
                    if (record.lunchEnd != null) {
                        segmentLunchEnd = minDateTime(record.lunchEnd, segmentEnd);
                    }
                }

                LocalDateTime segmentClockOut = record.clockOut == null && segmentEnd.equals(shiftEnd)
                        ? null
                        : segmentEnd;

                segments.add(new TimeSegment(
                        record,
                        segmentDate,
                        segmentStart,
                        segmentLunchStart,
                        segmentLunchEnd,
                        segmentClockOut,
                        hours
                ));
            }

            segmentDate = segmentDate.plusDays(1);
        }

        return segments;
    }

    private static long lunchOverlapMinutes(TimeRecord record, LocalDateTime segmentStart, LocalDateTime segmentEnd, LocalDateTime shiftEnd) {
        if (record.lunchStart == null) {
            return 0;
        }
        LocalDateTime lunchEnd = record.lunchEnd == null ? shiftEnd : record.lunchEnd;
        LocalDateTime overlapStart = maxDateTime(record.lunchStart, segmentStart);
        LocalDateTime overlapEnd = minDateTime(lunchEnd, segmentEnd);
        return minutesBetween(overlapStart, overlapEnd);
    }

    private static BigDecimal segmentPay(TimeSegment segment, Map<String, Integer> dailyPaidClockIds) {
        TimeRecord record = segment.record;
        if (record.isDaily()) {
            Integer paidClockId = dailyPaidClockIds.get(dailyPayKey(record.userId, segment.workDate));
            return paidClockId != null && paidClockId == record.clockId
                    ? record.salary.setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (record.isSalary()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (record.clockOut != null && record.totalEarned != null && record.totalHoursWorked != null) {
            BigDecimal totalHours = record.totalHoursWorked.setScale(2, RoundingMode.HALF_UP);
            if (totalHours.compareTo(BigDecimal.ZERO) > 0) {
                return record.totalEarned
                        .multiply(segment.hours)
                        .divide(totalHours, 2, RoundingMode.HALF_UP);
            }
        }
        return record.salary.multiply(segment.hours).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal payrollPay(TimeClockRow row, boolean firstRowForPeriod) {
        if ("SALARY".equalsIgnoreCase(row.compensationType())) {
            return firstRowForPeriod ? row.salary().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        }
        return row.totalPay();
    }

    private static String dailyPayKey(int userId, LocalDate workDate) {
        return userId + "|" + workDate;
    }

    private static long minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return Duration.between(start, end).toMinutes();
    }

    private static LocalDateTime maxDateTime(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime minDateTime(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime nullSafeDateTime(LocalDateTime value) {
        return value == null ? LocalDateTime.MIN : value;
    }

    private static PayPeriod payPeriodFor(LocalDate date) {
        if (date.getDayOfMonth() <= 15) {
            LocalDate start = date.withDayOfMonth(1);
            LocalDate end = date.withDayOfMonth(15);
            return new PayPeriod(start, end, adjustPayDate(end.plusDays(1)));
        }

        LocalDate start = date.withDayOfMonth(16);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        return new PayPeriod(start, end, adjustPayDate(end.plusDays(1)));
    }

    private static LocalDate adjustPayDate(LocalDate payDate) {
        if (payDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return payDate.plusDays(1);
        }
        return payDate;
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

    private static void ensurePayrollPaymentsSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id)");
            stmt.executeUpdate("ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS payment_number INTEGER NOT NULL DEFAULT 1");
            stmt.executeUpdate("DROP INDEX IF EXISTS payroll_payments_employee_period_idx");
            stmt.executeUpdate("""
                    CREATE UNIQUE INDEX IF NOT EXISTS payroll_payments_employee_period_payment_idx
                    ON payroll_payments(user_id, pay_period_start, pay_period_end, payment_number)
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS payroll_payments_location_paid_idx ON payroll_payments(location_id, paid_at DESC)");
        }
    }

    private static void ensureTimeClockOverrideSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_required BOOLEAN NOT NULL DEFAULT FALSE");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_reason TEXT");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_by_user_id INTEGER REFERENCES users(user_id)");
            stmt.executeUpdate("ALTER TABLE employee_time_clock ADD COLUMN IF NOT EXISTS multiple_session_override_by_name TEXT");
            stmt.executeUpdate("INSERT INTO permissions (permission_key, permission_name) SELECT 'TIME_CLOCK_OVERRIDE', 'Time Clock Override' WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'TIME_CLOCK_OVERRIDE')");
            stmt.executeUpdate("""
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT r.role_id, p.permission_id
                    FROM roles r
                    JOIN permissions p ON UPPER(p.permission_key) = 'TIME_CLOCK_OVERRIDE'
                    WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
                      AND NOT EXISTS (
                          SELECT 1
                          FROM role_permissions rp
                          WHERE rp.role_id = r.role_id
                            AND rp.permission_id = p.permission_id
                      )
                    """);
        }
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal paidAmount(PayrollPaymentStatus status) {
        return status == null ? BigDecimal.ZERO : defaultZero(status.paidAmount());
    }

    private static BigDecimal amountDue(BigDecimal totalPay, PayrollPaymentStatus status) {
        BigDecimal due = defaultZero(totalPay).subtract(paidAmount(status));
        return due.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO : due.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isFullyPaid(BigDecimal totalPay, PayrollPaymentStatus status) {
        return status != null
                && paidAmount(status).compareTo(BigDecimal.ZERO) > 0
                && paidAmount(status).compareTo(defaultZero(totalPay)) >= 0;
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
            int daysWorked,
            BigDecimal totalHours,
            BigDecimal totalPay,
            int recordCount,
            String compensationType,
            Integer locationId,
            String locationName,
            boolean paid,
            LocalDateTime paidAt,
            String paidByName,
            BigDecimal paidAmount,
            BigDecimal amountDue
    ) {
        public PayrollSummary {
            if (paidByName == null) {
                paidByName = "";
            }
            paidAmount = defaultZero(paidAmount).setScale(2, RoundingMode.HALF_UP);
            amountDue = defaultZero(amountDue).setScale(2, RoundingMode.HALF_UP);
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
            BigDecimal salary,
            BigDecimal totalPay,
            Integer locationId,
            String locationName,
            LocalDateTime shiftClockIn,
            LocalDateTime shiftLunchStart,
            LocalDateTime shiftLunchEnd,
            LocalDateTime shiftClockOut
    ) {
    }

    public static class TimeClockException extends Exception {
        public TimeClockException(String message) {
            super(message);
        }
    }

    private record PayPeriod(LocalDate start, LocalDate end, LocalDate payDate) {
    }

    private record PayrollPaymentStatus(LocalDateTime paidAt, String paidByName, BigDecimal paidAmount) {
    }

    private record TimeSegment(
            TimeRecord record,
            LocalDate workDate,
            LocalDateTime clockIn,
            LocalDateTime lunchStart,
            LocalDateTime lunchEnd,
            LocalDateTime clockOut,
            BigDecimal hours
    ) {
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
            BigDecimal salary,
            BigDecimal totalHoursWorked,
            BigDecimal totalEarned,
            Integer locationId,
            String locationName
    ) {
        private TimeRecord {
            if (compensationType == null || compensationType.isBlank()) {
                compensationType = "HOURLY";
            } else {
                compensationType = compensationType.trim().toUpperCase();
            }
            if (salary == null) {
                salary = BigDecimal.ZERO;
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

    }

    private static Integer mergeLocationId(Integer current, Integer next) {
        if (current == null) {
            return next;
        }
        if (next == null || current.equals(next)) {
            return current;
        }
        return SessionManager.getCurrentLocationId();
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
