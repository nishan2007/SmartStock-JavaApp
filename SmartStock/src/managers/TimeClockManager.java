package managers;

import services.LanApiClient;
import services.ManagerApprovalService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Register-safe facade. All time-clock and payroll persistence is owned by the LAN service. */
public final class TimeClockManager {
    public static final String MULTIPLE_SESSION_OVERRIDE_PERMISSION = "TIME_CLOCK_OVERRIDE";

    private TimeClockManager() { }

    public static boolean canViewAllRecords() {
        return PermissionManager.hasPermission("TIME_CLOCK_MANAGEMENT")
                || PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT")
                || PermissionManager.hasPermission("ROLE_MANAGEMENT");
    }

    public static TimeClockDashboard loadDashboard(boolean ignoredClientScope) throws SQLException {
        try { return LanApiClient.loadTimeClockDashboard(); }
        catch (Exception ex) { throw sql("Unable to load the time clock from the SmartStock server.", ex); }
    }

    public static boolean requiresMultipleSessionOverride() throws SQLException {
        try { return LanApiClient.loadTimeClockPunchState().requiresOverride(); }
        catch (Exception ex) { throw sql("Unable to check time-clock status with the SmartStock server.", ex); }
    }

    public static boolean currentUserCanApproveMultipleSessionOverride() throws SQLException {
        try { return LanApiClient.loadTimeClockPunchState().requesterCanOverride(); }
        catch (Exception ex) { throw sql("Unable to check time-clock permissions with the SmartStock server.", ex); }
    }

    public static void clockIn() throws SQLException, TimeClockException { clockIn(null); }

    public static void clockIn(ManagerApprovalService.ApprovalResult approval) throws SQLException, TimeClockException {
        String token = approval == null ? null : approval.lanApprovalToken();
        String reason = approval == null ? null : approval.reason();
        punch("CLOCK_IN", token, reason);
    }

    public static void lunchStart() throws SQLException, TimeClockException { punch("LUNCH_START", null, null); }
    public static void lunchEnd() throws SQLException, TimeClockException { punch("LUNCH_END", null, null); }
    public static void breakStart() throws SQLException, TimeClockException { punch("BREAK_START", null, null); }
    public static void breakEnd() throws SQLException, TimeClockException { punch("BREAK_END", null, null); }
    public static void clockOut() throws SQLException, TimeClockException { punch("CLOCK_OUT", null, null); }

    private static void punch(String action, String approvalToken, String approvalReason)
            throws SQLException, TimeClockException {
        try { LanApiClient.timeClockPunch(action, approvalToken, approvalReason, UUID.randomUUID().toString()); }
        catch (LanApiClient.LanApiException ex) {
            if (ex.code().startsWith("TIME_CLOCK_") || "APPROVAL_REQUIRED".equals(ex.code())
                    || "APPROVAL_INVALID".equals(ex.code())) throw new TimeClockException(ex.getMessage());
            throw sql("The SmartStock server could not record this punch.", ex);
        } catch (Exception ex) { throw sql("The SmartStock server could not record this punch.", ex); }
    }

    public static PayrollDashboard loadPayrollDashboard() throws SQLException {
        try { return LanApiClient.loadPayrollDashboard(); }
        catch (Exception ex) { throw sql("Unable to load payroll from the SmartStock server.", ex); }
    }

    public static void markPayrollPaid(PayrollSummary summary) throws SQLException {
        markPayrollPaid(summary, "CASH", null);
    }

    public static void markPayrollPaid(PayrollSummary summary, String paymentMethod,
                                       String paymentReference) throws SQLException {
        try { LanApiClient.markPayrollPaid(summary, paymentMethod, paymentReference, UUID.randomUUID().toString()); }
        catch (Exception ex) { throw sql("Unable to record payroll payment through the SmartStock server.", ex); }
    }

    public static void addPayrollBonus(PayrollSummary summary, BigDecimal amount, String reason) throws SQLException {
        addPayrollBonuses(List.of(summary), amount, reason);
    }

    public static void addPayrollBonuses(List<PayrollSummary> summaries, BigDecimal amount,
                                         String reason) throws SQLException {
        try { LanApiClient.addPayrollBonuses(summaries, amount, reason, UUID.randomUUID().toString()); }
        catch (Exception ex) { throw sql("Unable to record the payroll bonus through the SmartStock server.", ex); }
    }

    private static SQLException sql(String message, Exception cause) {
        return cause instanceof SQLException existing ? existing : new SQLException(message, cause);
    }

    public enum ClockState { NOT_CLOCKED_IN, CLOCKED_IN, ON_LUNCH, ON_BREAK, CLOCKED_OUT }
    public record TimeClockDashboard(List<TimeClockRow> rows, ClockStatus status) { }
    public record PayrollDashboard(List<TimeClockRow> timeRows, List<PayrollSummary> summaries) { }
    public record PayrollSummary(int userId, String employeeName, String employeeRole,
                                 LocalDate payPeriodStart, LocalDate payPeriodEnd, LocalDate payDate,
                                 int daysWorked, BigDecimal totalHours, BigDecimal regularHours,
                                 BigDecimal overtimeHours, BigDecimal regularPay, BigDecimal overtimePay,
                                 BigDecimal bonusAmount, BigDecimal totalPay, int recordCount,
                                 String compensationType, String payPeriodType, BigDecimal workHourLimit,
                                 Integer locationId, String locationName, boolean paid, LocalDateTime paidAt,
                                 String paidByName, BigDecimal paidAmount, BigDecimal amountDue) {
        public PayrollSummary {
            if (paidByName == null) paidByName = "";
            paidAmount = utils.CurrencyFormatter.normalize(paidAmount);
            regularPay = utils.CurrencyFormatter.normalize(regularPay);
            overtimePay = utils.CurrencyFormatter.normalize(overtimePay);
            bonusAmount = utils.CurrencyFormatter.normalize(bonusAmount);
            amountDue = utils.CurrencyFormatter.normalize(amountDue);
        }
    }
    public record ClockStatus(ClockState state, boolean canClockIn, boolean canLunchStart,
                              boolean canLunchEnd, boolean canBreakStart, boolean canBreakEnd,
                              boolean canClockOut) { }
    public record TimeClockRow(int clockId, int userId, String employeeName, String employeeRole,
                               LocalDate workDate, LocalDateTime clockIn, LocalDateTime lunchStart,
                               LocalDateTime lunchEnd, LocalDateTime breakStart, LocalDateTime breakEnd,
                               LocalDateTime clockOut, BigDecimal dailyHours,
                               LocalDate payPeriodStart, LocalDate payPeriodEnd, LocalDate payDate,
                               BigDecimal totalHours, String compensationType, BigDecimal salary,
                               BigDecimal regularHours, BigDecimal overtimeHours, BigDecimal regularPay,
                               BigDecimal overtimePay, BigDecimal totalPay, String payPeriodType,
                               BigDecimal workHourLimit, Integer locationId, String locationName,
                               LocalDateTime shiftClockIn, LocalDateTime shiftLunchStart,
                               LocalDateTime shiftLunchEnd, LocalDateTime shiftBreakStart,
                               LocalDateTime shiftBreakEnd, LocalDateTime shiftClockOut,
                               boolean autoClockOut, String autoClockOutReviewStatus,
                               boolean autoBreakEnd, String autoBreakEndReviewStatus) { }

    public static class TimeClockException extends Exception {
        public TimeClockException(String message) { super(message); }
    }
}
