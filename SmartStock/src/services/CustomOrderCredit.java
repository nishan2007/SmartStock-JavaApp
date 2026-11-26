package services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

import utils.CurrencyFormatter;

final class CustomOrderCredit {
    static final String APPROVAL_ACTION = "Custom Order Credit Limit Override";

    @FunctionalInterface
    interface Approver<E extends Exception> {
        LanSalesService.Approval approve(String message) throws E;
    }

    record Charge(LanSalesService.Approval approval) {
        void audit(Connection c, long orderId, BigDecimal amount, int actor, String name,
                   String device, String deviceName) throws SQLException {
            if (approval != null) {
                CustomOrderPricingService.audit(c, orderId, "CREDIT_LIMIT_OVERRIDE", "balance_due",
                        null, amount, approval.reason() + " (approved by " + approval.approverName()
                                + ", user " + approval.approverUserId() + ")", actor, name, device, deviceName);
            }
        }
    }

    static <E extends Exception> Charge check(Connection c, Integer customerId, Integer locationId,
            BigDecimal amount, boolean explicit, Approver<E> approver) throws SQLException, E {
        if (customerId == null || amount.signum() <= 0) return null;
        BigDecimal limit;
        boolean business;
        try (var p = c.prepareStatement("""
                SELECT COALESCE(a.credit_limit,0), LOWER(BTRIM(t.name)) IN ('business','businesses')
                FROM customer_accounts a LEFT JOIN customer_types t ON t.customer_type_id=a.customer_type_id
                WHERE a.customer_id=? AND a.is_active=TRUE FOR UPDATE OF a
                """)) {
            p.setInt(1, customerId);
            try (var r = p.executeQuery()) {
                if (!r.next()) {
                    if (explicit) throw new SQLException("The customer account is inactive.");
                    return null;
                }
                limit = r.getBigDecimal(1);
                business = r.getBoolean(2);
            }
        }
        if (!explicit && (!business || limit.signum() <= 0)) return null;
        CustomerAccountLedgerService.requireCurrentMultiStoreBalance(c, locationId);
        BigDecimal balance = CustomerAccountLedgerService.repairCustomerBalance(c, customerId)
                .subtract(PhoneMinutesService.minutesDue(c, customerId));
        BigDecimal projected = balance.add(amount).max(BigDecimal.ZERO);
        int comparison = projected.compareTo(limit);
        if (comparison < 0 || (comparison == 0 && !business)) return new Charge(null);
        if (!business || limit.signum() <= 0) {
            throw new SQLException("Charge exceeds the customer's credit limit.");
        }
        String message = "This charge of " + CurrencyFormatter.format(amount)
                + (comparison == 0 ? " would reach" : " would exceed")
                + " the credit limit of " + CurrencyFormatter.format(limit)
                + ". Available credit is " + CurrencyFormatter.format(limit.subtract(balance.max(BigDecimal.ZERO)).max(BigDecimal.ZERO))
                + ". Manager approval is required.";
        return new Charge(approver.approve(message));
    }

    static LanSalesService.Approval reject(String message) throws SQLException {
        throw new SQLException(message);
    }

    private CustomOrderCredit() {}
}
