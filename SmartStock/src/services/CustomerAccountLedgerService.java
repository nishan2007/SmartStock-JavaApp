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
                WHEN COALESCE(transaction_type, '') IN ('PAYMENT', 'RETURN', 'CUSTOM_ORDER_REFUND')
                    THEN -ABS(COALESCE(amount, 0))
                WHEN COALESCE(transaction_type, '') IN ('SALE_PAID', 'CUSTOM_ORDER_PAID')
                    THEN 0
                WHEN COALESCE(amount, 0) < 0
                    THEN COALESCE(amount, 0)
                ELSE COALESCE(amount, 0)
            END
            """;

    private CustomerAccountLedgerService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (!isServerMode()) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            if (tableExists(conn, "customer_account_transactions")) {
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS payment_id TEXT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS location_id INTEGER");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS custom_order_id BIGINT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS invoice_id BIGINT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS sales_order_id BIGINT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS payment_method TEXT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS payment_reference TEXT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS cash_drawer_id BIGINT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS cash_drawer_name TEXT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS cash_drawer_session_id BIGINT");
                ensureUpdatedAtTableSchema(stmt, "customer_account_transactions");
                stmt.executeUpdate("""
                        UPDATE customer_account_transactions
                        SET payment_id = 'PAY-' || LPAD(transaction_id::text, 6, '0')
                        WHERE COALESCE(transaction_type, '') = 'PAYMENT'
                          AND COALESCE(payment_id, '') = ''
                        """);
                stmt.executeUpdate("""
                        UPDATE customer_account_transactions
                        SET payment_id = NULL
                        WHERE COALESCE(transaction_type, '') <> 'PAYMENT'
                          AND TRIM(COALESCE(payment_id, '')) = ''
                        """);
                stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS idx_customer_account_transactions_payment_id ON customer_account_transactions(payment_id) WHERE payment_id IS NOT NULL");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_transactions_customer_created_idx ON customer_account_transactions(customer_id, created_at DESC)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_transactions_location_created_idx ON customer_account_transactions(location_id, created_at DESC)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_transactions_sales_order_idx ON customer_account_transactions(sales_order_id)");
                stmt.executeUpdate("DROP INDEX IF EXISTS customer_account_transactions_payment_id_idx");
                stmt.executeUpdate("DROP INDEX IF EXISTS idx_customer_account_transactions_location_created");
                backfillTransactionLocations(stmt);
            }
            if (tableExists(conn, "customer_account_payment_allocations")) {
                stmt.executeUpdate("ALTER TABLE customer_account_payment_allocations ADD COLUMN IF NOT EXISTS custom_order_id BIGINT");
                stmt.executeUpdate("ALTER TABLE customer_account_payment_allocations ADD COLUMN IF NOT EXISTS invoice_id BIGINT");
                stmt.executeUpdate("ALTER TABLE customer_account_payment_allocations ADD COLUMN IF NOT EXISTS sales_order_id BIGINT");
                ensureUpdatedAtTableSchema(stmt, "customer_account_payment_allocations");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_payment_idx ON customer_account_payment_allocations(payment_transaction_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_sale_idx ON customer_account_payment_allocations(sale_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_custom_order_idx ON customer_account_payment_allocations(custom_order_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_invoice_idx ON customer_account_payment_allocations(invoice_id)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS customer_account_payment_allocations_sales_order_idx ON customer_account_payment_allocations(sales_order_id)");
                stmt.executeUpdate("DROP INDEX IF EXISTS idx_customer_payment_allocations_payment");
                stmt.executeUpdate("DROP INDEX IF EXISTS idx_customer_payment_allocations_sale");
            }
        }
    }

    public static void repairAllBalances(Connection conn) throws SQLException {
        ensureSchema(conn);
        if (!isServerMode()) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    WITH ledger AS (
                        SELECT customer_id, COALESCE(SUM(%s), 0) AS balance
                        FROM customer_account_transactions
                        GROUP BY customer_id
                    )
                    UPDATE customer_accounts ca
                    SET current_balance = GREATEST(COALESCE(ledger.balance, 0), 0),
                        updated_at = CURRENT_TIMESTAMP
                    FROM ledger
                    WHERE ca.customer_id = ledger.customer_id
                      AND ca.current_balance IS DISTINCT FROM GREATEST(COALESCE(ledger.balance, 0), 0)
                    """.formatted(BALANCE_DELTA_CASE));
            stmt.executeUpdate("""
                    UPDATE customer_accounts ca
                    SET current_balance = 0,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE NOT EXISTS (
                        SELECT 1
                        FROM customer_account_transactions t
                        WHERE t.customer_id = ca.customer_id
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
                .replace("amount", tableAlias + ".amount");
    }

    private static BigDecimal calculateCustomerBalance(Connection conn, int customerId) throws SQLException {
        String sql = """
                SELECT GREATEST(COALESCE(SUM(%s), 0), 0) AS balance
                FROM customer_account_transactions
                WHERE customer_id = ?
                """.formatted(BALANCE_DELTA_CASE);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal balance = rs.getBigDecimal("balance");
                    return balance == null ? BigDecimal.ZERO : balance;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static void ensureUpdatedAtTableSchema(Statement stmt, String table) throws SQLException {
        stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
        stmt.executeUpdate("""
                CREATE OR REPLACE FUNCTION set_%s_updated_at()
                RETURNS TRIGGER AS $$
                BEGIN
                    IF TG_OP = 'INSERT' THEN
                        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                        NEW.updated_at = CURRENT_TIMESTAMP;
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """.formatted(table));
        stmt.executeUpdate("DROP TRIGGER IF EXISTS " + table + "_set_updated_at ON " + table);
        stmt.executeUpdate("""
                CREATE OR REPLACE TRIGGER %s_set_updated_at
                BEFORE INSERT OR UPDATE ON %s
                FOR EACH ROW
                EXECUTE FUNCTION set_%s_updated_at()
                """.formatted(table, table, table));
        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS " + table + "_updated_at_idx ON " + table + "(updated_at DESC)");
    }

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
