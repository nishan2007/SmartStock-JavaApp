package Receipt;

import services.LanApiClient;
import services.CustomerAccountLedgerService;
import ui.helpers.StoreTimeZoneHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class ServerAccountPaymentReceiptBuilder {
    private ServerAccountPaymentReceiptBuilder() {
    }

    public static AccountPaymentReceiptData loadPaymentReceipt(Connection conn,int customerId,long transactionId)throws SQLException{
            CustomerAccountLedgerService.ensureSchema(conn);
            AccountPaymentReceiptData header = loadPaymentHeader(conn, customerId, transactionId);
            List<AccountPaymentReceiptData.AllocationLine> allocations = loadAllocations(conn, transactionId);
            return new AccountPaymentReceiptData(
                    header.getTransactionId(),
                    header.getLocationId(),
                    header.getPaymentId(),
                    header.getPaymentTime(),
                    header.getStoreName(),
                    header.getUserName(),
                    header.getCustomerName(),
                    header.getAccountNumber(),
                    header.getCustomerEmail(),
                    header.getPaymentMethod(),
                    header.getPaymentReference(),
                    header.getDeviceName(),
                    header.getCashDrawerName(),
                    header.getPaymentAmount(),
                    header.getAccountBalanceAfter(),
                    allocations
            );
    }

    private static AccountPaymentReceiptData loadPaymentHeader(Connection conn, int customerId, long transactionId) throws SQLException {
        String sql = """
                SELECT t.transaction_id,
                       COALESCE(t.payment_id, 'PAY-' || LPAD(t.transaction_id::text, 6, '0')) AS payment_id,
                       t.location_id,
                       (t.created_at AT TIME ZONE ?) AS payment_time,
                       COALESCE(l.name, 'Unknown Store') AS store_name,
                       COALESCE(t.user_name, '') AS user_name,
                       COALESCE(ca.name, '') AS customer_name,
                       COALESCE(ca.account_number, '') AS account_number,
                       COALESCE(ca.email, '') AS customer_email,
                       COALESCE(t.payment_method, '') AS payment_method,
                       COALESCE(t.payment_reference, '') AS payment_reference,
                       COALESCE(t.device_name, t.device_id, '') AS device_name,
                       COALESCE(t.cash_drawer_name, '') AS cash_drawer_name,
                       ABS(COALESCE(t.amount, 0)) AS payment_amount,
                       COALESCE((
                           SELECT SUM(COALESCE(t2.amount, 0))
                           FROM customer_account_transactions t2
                           WHERE t2.customer_id = t.customer_id
                             AND (
                                 t2.created_at < t.created_at
                                 OR (t2.created_at = t.created_at AND t2.transaction_id <= t.transaction_id)
                             )
                       ), 0) AS account_balance_after
                FROM customer_account_transactions t
                LEFT JOIN customer_accounts ca ON ca.customer_id = t.customer_id
                LEFT JOIN locations l ON l.location_id = t.location_id
                WHERE t.customer_id = ?
                  AND t.transaction_id = ?
                  AND t.transaction_type = 'PAYMENT'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, customerId);
            ps.setLong(3, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Customer account payment was not found.");
                }
                return new AccountPaymentReceiptData(
                        rs.getLong("transaction_id"),
                        nullableInt(rs, "location_id"),
                        rs.getString("payment_id"),
                        rs.getTimestamp("payment_time"),
                        rs.getString("store_name"),
                        rs.getString("user_name"),
                        rs.getString("customer_name"),
                        rs.getString("account_number"),
                        rs.getString("customer_email"),
                        rs.getString("payment_method"),
                        rs.getString("payment_reference"),
                        rs.getString("device_name"),
                        rs.getString("cash_drawer_name"),
                        rs.getBigDecimal("payment_amount"),
                        rs.getBigDecimal("account_balance_after"),
                        List.of()
                );
            }
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static List<AccountPaymentReceiptData.AllocationLine> loadAllocations(Connection conn, long transactionId) throws SQLException {
        String sql = """
                SELECT CASE
                           WHEN a.sale_id IS NOT NULL THEN 'Sale ' || COALESCE(NULLIF(s.receipt_number, ''), a.sale_id::text)
                           WHEN a.custom_order_id IS NOT NULL THEN 'Custom Order ' || COALESCE(NULLIF(co.order_number, ''), a.custom_order_id::text)
                           WHEN a.invoice_id IS NOT NULL THEN 'Invoice ' || COALESCE(NULLIF(i.invoice_number, ''), a.invoice_id::text)
                           ELSE 'Account Balance'
                       END AS target_label,
                       COALESCE(a.amount, 0) AS applied_amount,
                       COALESCE(s.total_amount, co.total_amount, i.total_amount, 0) AS charge_total,
                       COALESCE(s.amount_paid, co.amount_paid, i.amount_paid, 0) AS charge_paid,
                       COALESCE(s.payment_status, co.payment_status, i.payment_status, '') AS payment_status,
                       (COALESCE(s.created_at, co.created_at, i.created_at) AT TIME ZONE ?) AS charge_date
                FROM customer_account_payment_allocations a
                LEFT JOIN sales s ON s.sale_id = a.sale_id
                LEFT JOIN custom_orders co ON co.custom_order_id = a.custom_order_id
                LEFT JOIN invoices i ON i.invoice_id = a.invoice_id
                WHERE a.payment_transaction_id = ?
                ORDER BY a.allocation_id ASC
                """;
        List<AccountPaymentReceiptData.AllocationLine> allocations = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setLong(2, transactionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    allocations.add(new AccountPaymentReceiptData.AllocationLine(
                            rs.getString("target_label"),
                            rs.getBigDecimal("applied_amount"),
                            rs.getBigDecimal("charge_total"),
                            rs.getBigDecimal("charge_paid"),
                            rs.getString("payment_status"),
                            rs.getTimestamp("charge_date")
                    ));
                }
            }
        }
        return allocations;
    }
}
