package services;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import managers.ServerTimeClockManager;

/** Explicit date-level daily-pay choices. The caller owns the transaction and authentication. */
public final class DailyPayDayService {
    private DailyPayDayService() { }

    public static BigDecimal dailyPay(BigDecimal rate, boolean halfDay) {
        return utils.CurrencyFormatter.normalize(halfDay ? rate.multiply(new BigDecimal("0.5")) : rate);
    }

    public static boolean isHalfDay(Connection c, int userId, LocalDate date) throws SQLException {
        try (PreparedStatement p=c.prepareStatement("SELECT half_day FROM employee_daily_pay_days WHERE user_id=? AND work_date=?")) {
            p.setInt(1,userId);p.setDate(2,Date.valueOf(date));
            try(ResultSet r=p.executeQuery()){return r.next() && r.getBoolean(1);}
        }
    }

    public static Set<String> loadHalfDays(Connection c) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement p = c.prepareStatement("SELECT user_id,work_date FROM employee_daily_pay_days WHERE half_day");
             ResultSet r = p.executeQuery()) {
            while (r.next()) result.add(r.getInt(1) + "|" + r.getDate(2).toLocalDate());
        }
        return result;
    }

    public static void set(Connection c, int employeeId, LocalDate date, boolean halfDay,
                           int actorId, int locationId, boolean manager) throws SQLException {
        if (date == null) throw new SQLException("Select a work date.");
        ManualTimeClockService.lockEmployee(c, employeeId);
        if (!manager && (employeeId != actorId || !halfDay))
            throw new SQLException("Employees may only mark their own completed day as a half day.");
        if (!manager) {
            try (PreparedStatement p = c.prepareStatement("SELECT (CURRENT_TIMESTAMP AT TIME ZONE timezone)::date FROM locations WHERE location_id=?")) {
                p.setInt(1, locationId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next() || !date.equals(r.getDate(1).toLocalDate()))
                        throw new SQLException("You may only mark today's completed work as a half day.");
                }
            }
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM employee_time_clock WHERE user_id=? AND clock_out IS NULL")) {
                p.setInt(1, employeeId);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) throw new SQLException("Clock out before marking a half day.");
                }
            }
        }
        var dashboard = ServerTimeClockManager.loadDashboard(c, true);
        var rows = dashboard.rows().stream().filter(row -> row.userId() == employeeId
                && row.workDate().equals(date)).toList();
        if (rows.isEmpty() || rows.stream().anyMatch(row -> !"DAILY".equalsIgnoreCase(row.compensationType())
                || row.shiftClockOut() == null) || rows.stream().noneMatch(row -> row.dailyHours().signum() > 0))
            throw new SQLException("Only completed daily-rate work can be marked as a half day.");
        if (rows.stream().anyMatch(row -> !java.util.Objects.equals(row.locationId(), locationId)))
            throw new SQLException("Mark your half day at the store where you worked.");
        var period = EmployeePayrollSettingsService.periodFor(c, employeeId, date, "DAILY");
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM payroll_payments WHERE user_id=? AND pay_period_start=? AND pay_period_end=? LIMIT 1")) {
            p.setInt(1, employeeId); p.setDate(2, Date.valueOf(period.start())); p.setDate(3, Date.valueOf(period.end()));
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) throw new SQLException("This payroll period already has payments; its day classification cannot be changed.");
            }
        }
        try (PreparedStatement p = c.prepareStatement("""
            INSERT INTO employee_daily_pay_days(user_id,work_date,half_day,location_id,updated_by)
            VALUES(?,?,?,?,?) ON CONFLICT(user_id,work_date) DO UPDATE
            SET half_day=EXCLUDED.half_day,location_id=EXCLUDED.location_id,
                updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
            """)) {
            p.setInt(1, employeeId); p.setDate(2, Date.valueOf(date)); p.setBoolean(3, halfDay);
            p.setInt(4, locationId); p.setInt(5, actorId); p.executeUpdate();
        }
        // Refresh the stored earnings as well as the computed payroll dashboard.
        EmployeePayrollSettingsService.refreshCompletedEarningsForHalfDay(c, employeeId, date);
        SyncOutboxService.recordEvent(c, "PAYROLL_DAY_CLASSIFIED", Map.of("user_id", employeeId,
                "work_date", date.toString(), "half_day", halfDay, "updated_by", actorId));
    }
}
