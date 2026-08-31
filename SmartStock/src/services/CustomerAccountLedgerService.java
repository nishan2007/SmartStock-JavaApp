package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class CustomerAccountLedgerService {
    private static final String BALANCE_DELTA_CASE = """
            CASE
                WHEN COALESCE(transaction_type, '') IN ('SALE_CREDIT', 'CUSTOM_ORDER_CREDIT', 'INVOICE_CREDIT', 'MANUAL_CHARGE')
                    THEN ABS(COALESCE(amount, 0))
                WHEN COALESCE(transaction_type, '') = 'PAYMENT'
                    THEN -ABS(COALESCE(credit_applied_amount, amount, 0))
                WHEN COALESCE(transaction_type, '') IN ('RETURN', 'CUSTOM_ORDER_REFUND')
                    THEN -ABS(COALESCE(amount, 0))
                WHEN COALESCE(transaction_type, '') IN ('SALE_PAID', 'CUSTOM_ORDER_PAID', 'CUSTOM_ORDER_BALANCE', 'CUSTOM_ORDER_PAYMENT')
                    THEN 0
                WHEN COALESCE(amount, 0) < 0
                    THEN COALESCE(amount, 0)
                ELSE COALESCE(amount, 0)
            END
            """;

    private CustomerAccountLedgerService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        SchemaContractService.requireLocalReady(conn);
    }

    public static void repairAllBalances(Connection conn) throws SQLException {
        ensureSchema(conn);
        if (!isServerMode()) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    WITH ledger_events AS (
                        SELECT customer_id,%s AS balance_delta FROM customer_account_transactions
                        UNION ALL
                        SELECT customer_id,%s AS balance_delta FROM sync_cross_store_customer_history_cache
                        WHERE event_key LIKE 'LEDGER:%%'
                    ), ledger AS (
                        SELECT customer_id,COALESCE(SUM(balance_delta),0) AS balance FROM ledger_events GROUP BY customer_id
                    )
                    UPDATE customer_accounts ca
                    SET current_balance = GREATEST(COALESCE(ledger.balance, 0), 0),
                        updated_at = CURRENT_TIMESTAMP
                    FROM ledger
                    WHERE ca.customer_id = ledger.customer_id
                      AND ca.current_balance IS DISTINCT FROM GREATEST(COALESCE(ledger.balance, 0), 0)
                    """.formatted(BALANCE_DELTA_CASE,remoteBalanceDeltaSql()));
            stmt.executeUpdate("""
                    UPDATE customer_accounts ca
                    SET current_balance = 0,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM customer_account_transactions t WHERE t.customer_id = ca.customer_id
                    ) AND NOT EXISTS (
                        SELECT 1 FROM sync_cross_store_customer_history_cache r
                        WHERE r.customer_id=ca.customer_id AND r.event_key LIKE 'LEDGER:%%'
                    )
                      AND ca.current_balance IS DISTINCT FROM 0
                    """);
        }
    }

    public static BigDecimal repairCustomerBalance(Connection conn, int customerId) throws SQLException {
        ensureSchema(conn);
        BigDecimal balance = calculateCustomerBalance(conn, customerId);
        if (!isServerMode()) {
            return balance;
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE customer_accounts
                SET current_balance = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE customer_id = ?
                  AND current_balance IS DISTINCT FROM ?
                """)) {
            ps.setBigDecimal(1, balance);
            ps.setInt(2, customerId);
            ps.setBigDecimal(3, balance);
            ps.executeUpdate();
        }
        return balance;
    }

    private static boolean isServerMode() {
        return DatabaseConfig.load().mode() == DatabaseMode.SERVER;
    }

    public static String balanceDeltaSql(String tableAlias) {
        if (tableAlias == null || tableAlias.isBlank()) {
            return BALANCE_DELTA_CASE;
        }
        return BALANCE_DELTA_CASE
                .replace("transaction_type", tableAlias + ".transaction_type")
                .replace("credit_applied_amount", "__CREDIT_APPLIED__")
                .replace("amount", tableAlias + ".amount")
                .replace("__CREDIT_APPLIED__", tableAlias + ".credit_applied_amount");
    }

    public static void requireCurrentMultiStoreBalance(Connection conn,int currentLocationId)throws SQLException{
        ensureSchema(conn);
        if(!CrossStoreCustomerHistoryService.allStoresCurrent(conn,currentLocationId))
            throw new SQLException("Customer credit is temporarily unavailable because one or more store balances have not completed a current synchronization.","55000");
    }

    private static BigDecimal calculateCustomerBalance(Connection conn, int customerId) throws SQLException {
        String remoteDelta = columnExists(conn, "sync_cross_store_customer_history_cache", "credit_applied_amount")
                ? remoteBalanceDeltaSql()
                : legacyRemoteBalanceDeltaSql();
        String sql = """
                SELECT GREATEST(COALESCE(SUM(balance_delta),0),0) AS balance FROM (
                  SELECT %s AS balance_delta FROM customer_account_transactions WHERE customer_id=?
                  UNION ALL
                  SELECT %s AS balance_delta FROM sync_cross_store_customer_history_cache
                  WHERE customer_id=? AND event_key LIKE 'LEDGER:%%'
                ) all_store_ledger
                """.formatted(BALANCE_DELTA_CASE, remoteDelta);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal balance = rs.getBigDecimal("balance");
                    return balance == null ? BigDecimal.ZERO : balance;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    static String remoteBalanceDeltaSql(){return """
            CASE
              WHEN COALESCE(event_type,'') IN ('SALE_CREDIT','CUSTOM_ORDER_CREDIT','INVOICE_CREDIT','MANUAL_CHARGE') THEN ABS(COALESCE(amount,0))
              WHEN COALESCE(event_type,'')='PAYMENT' THEN -ABS(COALESCE(credit_applied_amount,amount,0))
              WHEN COALESCE(event_type,'') IN ('RETURN','CUSTOM_ORDER_REFUND') THEN -ABS(COALESCE(amount,0))
              WHEN COALESCE(event_type,'') IN ('SALE_PAID','CUSTOM_ORDER_PAID','CUSTOM_ORDER_BALANCE','CUSTOM_ORDER_PAYMENT') THEN 0
              WHEN COALESCE(amount,0)<0 THEN COALESCE(amount,0)
              ELSE COALESCE(amount,0)
            END
            """;}

    static String legacyRemoteBalanceDeltaSql(){return """
            CASE
              WHEN COALESCE(event_type,'') IN ('SALE_CREDIT','CUSTOM_ORDER_CREDIT','INVOICE_CREDIT','MANUAL_CHARGE') THEN ABS(COALESCE(amount,0))
              WHEN COALESCE(event_type,'')='PAYMENT' THEN -ABS(COALESCE(amount,0))
              WHEN COALESCE(event_type,'') IN ('RETURN','CUSTOM_ORDER_REFUND') THEN -ABS(COALESCE(amount,0))
              WHEN COALESCE(event_type,'') IN ('SALE_PAID','CUSTOM_ORDER_PAID','CUSTOM_ORDER_BALANCE','CUSTOM_ORDER_PAYMENT') THEN 0
              WHEN COALESCE(amount,0)<0 THEN COALESCE(amount,0)
              ELSE COALESCE(amount,0)
            END
            """;}

    private static void backfillTransactionLocations(Statement stmt) throws SQLException {
        Connection conn = stmt.getConnection();
        if (tableExists(conn, "sales") && columnExists(conn, "sales", "location_id")) {
            stmt.executeUpdate("""
                    UPDATE customer_account_transactions t
                    SET location_id = s.location_id
                    FROM sales s
                    WHERE t.sale_id = s.sale_id
                      AND t.location_id IS NULL
                      AND s.location_id IS NOT NULL
                    """);
        }
        if (tableExists(conn, "custom_orders") && columnExists(conn, "custom_orders", "location_id")) {
            stmt.executeUpdate("""
                    UPDATE customer_account_transactions t
                    SET location_id = co.location_id
                    FROM custom_orders co
                    WHERE t.custom_order_id = co.custom_order_id
                      AND t.location_id IS NULL
                      AND co.location_id IS NOT NULL
                    """);
        }
        if (tableExists(conn, "invoices") && columnExists(conn, "invoices", "location_id")) {
            stmt.executeUpdate("""
                    UPDATE customer_account_transactions t
                    SET location_id = so.location_id
                    FROM invoices so
                    WHERE t.invoice_id = so.invoice_id
                      AND t.location_id IS NULL
                      AND so.location_id IS NOT NULL
                    """);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND table_type = 'BASE TABLE'
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(10);
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(10);
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
