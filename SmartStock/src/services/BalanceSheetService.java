package services;

import data.DB;
import managers.SessionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class BalanceSheetService {
    private static final long SCHEMA_LOCK_KEY = 7_340_210_001L;
    private static final Set<String> SCHEMA_READY = ConcurrentHashMap.newKeySet();

    private BalanceSheetService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        String cacheKey = databaseCacheKey(conn);
        if (SCHEMA_READY.contains(cacheKey)) {
            return;
        }
        try (Statement lock = conn.createStatement()) {
            lock.execute("SELECT pg_advisory_lock(" + SCHEMA_LOCK_KEY + ")");
        }
        try {
            if (SCHEMA_READY.contains(cacheKey)) {
                return;
            }
            ensureSchemaUnlocked(conn);
            SCHEMA_READY.add(cacheKey);
        } finally {
            try (Statement unlock = conn.createStatement()) {
                unlock.execute("SELECT pg_advisory_unlock(" + SCHEMA_LOCK_KEY + ")");
            }
        }
    }

    private static void ensureSchemaUnlocked(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS expenses (
                        expense_id BIGSERIAL PRIMARY KEY,
                        location_id INTEGER REFERENCES locations(location_id),
                        expense_date DATE NOT NULL DEFAULT CURRENT_DATE,
                        category TEXT NOT NULL,
                        payee TEXT,
                        description TEXT,
                        amount NUMERIC(12, 2) NOT NULL,
                        payment_method TEXT,
                        payment_reference TEXT,
                        status TEXT NOT NULL DEFAULT 'PAID',
                        source_type TEXT,
                        source_id TEXT,
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT expenses_amount_chk CHECK (amount >= 0),
                        CONSTRAINT expenses_status_chk CHECK (status IN ('PAID', 'UNPAID'))
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE expenses ADD COLUMN IF NOT EXISTS payment_reference TEXT");
            stmt.executeUpdate("ALTER TABLE expenses ADD COLUMN IF NOT EXISTS source_type TEXT");
            stmt.executeUpdate("ALTER TABLE expenses ADD COLUMN IF NOT EXISTS source_id TEXT");
            stmt.executeUpdate("ALTER TABLE expenses ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS expenses_source_unique_idx ON expenses(source_type, source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS expenses_location_date_idx ON expenses(location_id, expense_date DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS expenses_created_by_user_idx ON expenses(created_by_user_id)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS balance_sheet_submissions (
                        balance_sheet_submission_id BIGSERIAL PRIMARY KEY,
                        location_id INTEGER REFERENCES locations(location_id),
                        location_name TEXT,
                        period_start DATE NOT NULL,
                        period_end DATE NOT NULL,
                        store_timezone TEXT,
                        balance_bf NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        cash_in_hand NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        total_income NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        total_receivables NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        total_expenses NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        total_payables NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        balance_cf NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        income_lines TEXT,
                        receivable_lines TEXT,
                        expense_lines TEXT,
                        payable_lines TEXT,
                        drawer_cash_lines TEXT,
                        device_sales_lines TEXT,
                        device_order_lines TEXT,
                        drawer_check_lines TEXT,
                        submitted_by_user_id INTEGER REFERENCES users(user_id),
                        submitted_by_name TEXT,
                        submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        notes TEXT
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS drawer_cash_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS device_sales_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS device_order_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS drawer_check_lines TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS balance_sheet_submissions_location_period_idx ON balance_sheet_submissions(location_id, period_start DESC, period_end DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS balance_sheet_submissions_submitted_by_user_idx ON balance_sheet_submissions(submitted_by_user_id)");
        }
        SupabaseSecurityHardening.protectInternalTable(conn, "balance_sheet_submissions");
        SupabaseSecurityHardening.protectInternalTable(conn, "expenses");
    }

    private static String databaseCacheKey(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            return url == null || url.isBlank() ? "unknown" : url;
        } catch (SQLException ex) {
            return "unknown";
        }
    }

    public static void recordPayrollExpense(long payrollPaymentId, BigDecimal amount, String employeeName,
                                            LocalDate expenseDate, String reference) throws SQLException {
        recordPayrollExpense(payrollPaymentId, amount, employeeName, expenseDate, reference,
                SessionManager.getCurrentLocationId());
    }

    public static void recordPayrollExpense(long payrollPaymentId, BigDecimal amount, String employeeName,
                                            LocalDate expenseDate, String reference, Integer locationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    INSERT INTO expenses (
                        location_id, expense_date, category, payee, description, amount,
                        payment_method, payment_reference, status, source_type, source_id,
                        created_by_user_id, created_by_name
                    )
                    VALUES (?, ?, 'Payroll', ?, ?, ?, 'PAYROLL', ?, 'PAID', 'PAYROLL', ?, ?, ?)
                    ON CONFLICT (source_type, source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL
                    DO UPDATE SET
                        location_id = EXCLUDED.location_id,
                        expense_date = EXCLUDED.expense_date,
                        payee = EXCLUDED.payee,
                        description = EXCLUDED.description,
                        amount = EXCLUDED.amount,
                        payment_reference = EXCLUDED.payment_reference,
                        status = EXCLUDED.status,
                        updated_at = CURRENT_TIMESTAMP
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, locationId);
                ps.setDate(2, Date.valueOf(expenseDate));
                ps.setString(3, employeeName);
                ps.setString(4, "Payroll payment for " + employeeName);
                ps.setBigDecimal(5, defaultZero(amount));
                ps.setString(6, reference);
                ps.setString(7, String.valueOf(payrollPaymentId));
                setNullableInteger(ps, 8, SessionManager.getCurrentUserId());
                ps.setString(9, SessionManager.getCurrentUserDisplayName());
                ps.executeUpdate();
            }
        }
    }

    public static void addManualExpense(ExpenseEntry entry) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    INSERT INTO expenses (
                        location_id, expense_date, category, payee, description, amount,
                        payment_method, payment_reference, status, created_by_user_id, created_by_name
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING expense_id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, SessionManager.getCurrentLocationId());
                ps.setDate(2, Date.valueOf(entry.expenseDate()));
                ps.setString(3, entry.category());
                ps.setString(4, entry.payee());
                ps.setString(5, entry.description());
                ps.setBigDecimal(6, defaultZero(entry.amount()));
                ps.setString(7, entry.paymentMethod());
                ps.setString(8, entry.paymentReference());
                ps.setString(9, entry.status());
                setNullableInteger(ps, 10, SessionManager.getCurrentUserId());
                ps.setString(11, SessionManager.getCurrentUserDisplayName());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        SyncOutboxService.recordEvent(conn, "EXPENSE_CREATED", Map.of(
                                "expense_id", rs.getLong("expense_id"),
                                "location_id", SessionManager.getCurrentLocationId() == null ? "" : SessionManager.getCurrentLocationId(),
                                "expense_date", entry.expenseDate(),
                                "category", entry.category(),
                                "amount", defaultZero(entry.amount()),
                                "status", entry.status()
                        ));
                    }
                }
            }
        }
    }

    public static BalanceSheet loadBalanceSheet(LocalDate from, LocalDate to, String storeZoneId) throws SQLException {
        return loadBalanceSheet(from, to, storeZoneId, List.of());
    }

    public static BalanceSheet loadBalanceSheet(LocalDate from, LocalDate to, String storeZoneId, List<Long> cashDrawerSessionIds) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            Integer locationId = SessionManager.getCurrentLocationId();
            syncPaidPayrollExpenses(conn, storeZoneId, locationId);
            List<SheetLine> income = loadIncome(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> receivables = loadReceivables(conn, locationId);
            List<SheetLine> expenses = loadExpenses(conn, from, to, locationId, "PAID");
            List<SheetLine> payables = loadExpenses(conn, from, to, locationId, "UNPAID");
            List<SheetLine> drawerCash = loadDrawerCash(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> deviceSales = loadDeviceSales(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> deviceOrders = loadDeviceOrders(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> drawerChecks = loadDrawerMatchChecks(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            BigDecimal cashInHand = total(drawerCash);
            BigDecimal totalIncome = total(income);
            BigDecimal totalReceivables = total(receivables);
            BigDecimal totalExpenses = total(expenses);
            BigDecimal totalPayables = total(payables);
            BigDecimal balanceBf = loadPreviousBalanceCf(conn, from, locationId);
            if (balanceBf == null) {
                balanceBf = BigDecimal.ZERO;
            }
            BigDecimal balanceCf = balanceBf.add(totalIncome).subtract(totalExpenses).subtract(totalPayables);
            return new BalanceSheet(null, null, null, null, null, null,
                    income, receivables, expenses, payables, drawerCash, deviceSales, deviceOrders, drawerChecks, cashInHand,
                    balanceBf, totalIncome, totalReceivables, totalExpenses, totalPayables, balanceCf);
        }
    }

    public static long submitBalanceSheet(LocalDate from, LocalDate to, String storeZoneId, String notes) throws SQLException {
        return submitBalanceSheet(from, to, storeZoneId, notes, List.of());
    }

    public static long submitBalanceSheet(LocalDate from, LocalDate to, String storeZoneId, String notes, List<Long> cashDrawerSessionIds) throws SQLException {
        BalanceSheet sheet = loadBalanceSheet(from, to, storeZoneId, cashDrawerSessionIds);
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    INSERT INTO balance_sheet_submissions (
                        location_id, location_name, period_start, period_end, store_timezone,
                        balance_bf, cash_in_hand, total_income, total_receivables, total_expenses,
                        total_payables, balance_cf, income_lines, receivable_lines, expense_lines,
                        payable_lines, drawer_cash_lines, device_sales_lines, device_order_lines, drawer_check_lines, submitted_by_user_id,
                        submitted_by_name, notes
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING balance_sheet_submission_id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, SessionManager.getCurrentLocationId());
                ps.setString(2, SessionManager.getCurrentLocationName());
                ps.setDate(3, Date.valueOf(from));
                ps.setDate(4, Date.valueOf(to));
                ps.setString(5, storeZoneId);
                ps.setBigDecimal(6, sheet.balanceBf());
                ps.setBigDecimal(7, sheet.cashInHand());
                ps.setBigDecimal(8, sheet.totalIncome());
                ps.setBigDecimal(9, sheet.totalReceivables());
                ps.setBigDecimal(10, sheet.totalExpenses());
                ps.setBigDecimal(11, sheet.totalPayables());
                ps.setBigDecimal(12, sheet.balanceCf());
                ps.setString(13, encodeLines(sheet.income()));
                ps.setString(14, encodeLines(sheet.receivables()));
                ps.setString(15, encodeLines(sheet.expenses()));
                ps.setString(16, encodeLines(sheet.payables()));
                ps.setString(17, encodeLines(sheet.drawerCash()));
                ps.setString(18, encodeLines(sheet.deviceSales()));
                ps.setString(19, encodeLines(sheet.deviceOrders()));
                ps.setString(20, encodeLines(sheet.drawerChecks()));
                setNullableInteger(ps, 21, SessionManager.getCurrentUserId());
                ps.setString(22, SessionManager.getCurrentUserDisplayName());
                ps.setString(23, blankToNull(notes));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long submissionId = rs.getLong("balance_sheet_submission_id");
                        SyncOutboxService.recordEvent(conn, "BALANCE_SHEET_SUBMITTED", Map.of(
                                "balance_sheet_submission_id", submissionId,
                                "location_id", SessionManager.getCurrentLocationId() == null ? "" : SessionManager.getCurrentLocationId(),
                                "period_start", from,
                                "period_end", to,
                                "cash_in_hand", sheet.cashInHand(),
                                "balance_cf", sheet.balanceCf(),
                                "submitted_by_user_id", SessionManager.getCurrentUserId() == null ? "" : SessionManager.getCurrentUserId()
                        ));
                        return submissionId;
                    }
                }
            }
        }
        throw new SQLException("Balance sheet submission did not return an id.");
    }

    public static List<SubmissionOption> listSubmissions() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            Integer locationId = SessionManager.getCurrentLocationId();
            String sql = """
                    SELECT balance_sheet_submission_id,
                           period_start,
                           period_end,
                           submitted_by_name,
                           submitted_at,
                           balance_cf
                    FROM balance_sheet_submissions
                    WHERE (? IS NULL OR location_id = ?)
                    ORDER BY submitted_at DESC
                    LIMIT 60
                    """;
            List<SubmissionOption> submissions = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, locationId);
                setNullableInteger(ps, 2, locationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        submissions.add(new SubmissionOption(
                                rs.getLong("balance_sheet_submission_id"),
                                rs.getDate("period_start").toLocalDate(),
                                rs.getDate("period_end").toLocalDate(),
                                rs.getTimestamp("submitted_at").toLocalDateTime(),
                                rs.getString("submitted_by_name"),
                                defaultZero(rs.getBigDecimal("balance_cf"))
                        ));
                    }
                }
            }
            return submissions;
        }
    }

    public static BalanceSheet loadSubmission(long submissionId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    SELECT *
                    FROM balance_sheet_submissions
                    WHERE balance_sheet_submission_id = ?
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, submissionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new BalanceSheet(
                                rs.getLong("balance_sheet_submission_id"),
                                rs.getDate("period_start").toLocalDate(),
                                rs.getDate("period_end").toLocalDate(),
                                rs.getTimestamp("submitted_at").toLocalDateTime(),
                                rs.getString("submitted_by_name"),
                                rs.getString("notes"),
                                decodeLines(rs.getString("income_lines")),
                                decodeLines(rs.getString("receivable_lines")),
                                decodeLines(rs.getString("expense_lines")),
                                decodeLines(rs.getString("payable_lines")),
                                decodeLines(rs.getString("drawer_cash_lines")),
                                decodeLines(rs.getString("device_sales_lines")),
                                decodeLines(rs.getString("device_order_lines")),
                                decodeLines(rs.getString("drawer_check_lines")),
                                defaultZero(rs.getBigDecimal("cash_in_hand")),
                                defaultZero(rs.getBigDecimal("balance_bf")),
                                defaultZero(rs.getBigDecimal("total_income")),
                                defaultZero(rs.getBigDecimal("total_receivables")),
                                defaultZero(rs.getBigDecimal("total_expenses")),
                                defaultZero(rs.getBigDecimal("total_payables")),
                                defaultZero(rs.getBigDecimal("balance_cf"))
                        );
                    }
                }
            }
        }
        throw new SQLException("Saved balance sheet was not found.");
    }

    private static void syncPaidPayrollExpenses(Connection conn, String storeZoneId, Integer fallbackLocationId) throws SQLException {
        if (!tableExists(conn, "payroll_payments")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS payroll_payments_location_paid_idx ON payroll_payments(location_id, paid_at DESC)");
        }

        String updateSql = """
                UPDATE expenses e
                SET location_id = COALESCE(pp.location_id, e.location_id, ?),
                    expense_date = (pp.paid_at AT TIME ZONE ?)::date,
                    payee = pp.employee_name,
                    description = 'Payroll payment for ' || COALESCE(NULLIF(TRIM(pp.employee_name), ''), 'employee'),
                    amount = COALESCE(pp.total_pay, 0),
                    payment_method = 'PAYROLL',
                    payment_reference = 'Pay period ' || pp.pay_period_start || ' - ' || pp.pay_period_end
                        || '; scheduled pay date ' || COALESCE(pp.pay_date::text, ''),
                    status = 'PAID',
                    updated_at = CURRENT_TIMESTAMP
                FROM payroll_payments pp
                WHERE e.source_type = 'PAYROLL'
                  AND e.source_id = pp.payroll_payment_id::text
                """;
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            setNullableInteger(ps, 1, fallbackLocationId);
            ps.setString(2, storeZoneId);
            ps.executeUpdate();
        }

        String insertSql = """
                INSERT INTO expenses (
                    location_id, expense_date, category, payee, description, amount,
                    payment_method, payment_reference, status, source_type, source_id,
                    created_by_user_id, created_by_name
                )
                SELECT COALESCE(pp.location_id, ?),
                       (pp.paid_at AT TIME ZONE ?)::date,
                       'Payroll',
                       pp.employee_name,
                       'Payroll payment for ' || COALESCE(NULLIF(TRIM(pp.employee_name), ''), 'employee'),
                       COALESCE(pp.total_pay, 0),
                       'PAYROLL',
                       'Pay period ' || pp.pay_period_start || ' - ' || pp.pay_period_end
                           || '; scheduled pay date ' || COALESCE(pp.pay_date::text, ''),
                       'PAID',
                       'PAYROLL',
                       pp.payroll_payment_id::text,
                       pp.paid_by_user_id,
                       pp.paid_by_name
                FROM payroll_payments pp
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM expenses e
                    WHERE e.source_type = 'PAYROLL'
                      AND e.source_id = pp.payroll_payment_id::text
                )
                """;
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            setNullableInteger(ps, 1, fallbackLocationId);
            ps.setString(2, storeZoneId);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private static List<SheetLine> loadIncome(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                              Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String sessionFilter = cashDrawerFilterSql(cashDrawerSessionIds, "cash_drawer_session_id", "payment_method");
        String salesSql = """
                SELECT COALESCE(NULLIF(TRIM(payment_method), ''), 'UNKNOWN') AS label,
                       SUM(COALESCE(amount_paid, 0)) AS amount
                FROM sales
                WHERE (? IS NULL OR location_id = ?)
                  AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + sessionFilter + """
                GROUP BY COALESCE(NULLIF(TRIM(payment_method), ''), 'UNKNOWN')
                """;
        try (PreparedStatement ps = conn.prepareStatement(salesSql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(formatIncomeLabel(rs.getString("label")), defaultZero(rs.getBigDecimal("amount"))));
                }
            }
        }

        String customOrderSql = """
                SELECT 'ORDER BOOK' AS label,
                       SUM(CASE
                            WHEN payment_action = 'PAYMENT' THEN COALESCE(payment_amount, 0)
                            WHEN payment_action IN ('REFUND', 'REVERSAL') THEN -COALESCE(payment_amount, 0)
                            ELSE 0
                       END) AS amount
                FROM custom_order_payments
                WHERE (? IS NULL OR EXISTS (
                    SELECT 1 FROM custom_orders co
                    WHERE co.custom_order_id = custom_order_payments.custom_order_id
                      AND co.location_id = ?
                ))
                  AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + sessionFilter + """
                """;
        try (PreparedStatement ps = conn.prepareStatement(customOrderSql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal amount = defaultZero(rs.getBigDecimal("amount"));
                    if (amount.compareTo(BigDecimal.ZERO) != 0) {
                        lines.add(new SheetLine(rs.getString("label"), amount));
                    }
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(new SheetLine("No income logged", BigDecimal.ZERO));
        }
        return lines;
    }

    private static List<SheetLine> loadReceivables(Connection conn, Integer locationId) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String sql = """
                SELECT COALESCE(NULLIF(TRIM(name), ''), 'Customer Account') AS label,
                       COALESCE(current_balance, 0) AS amount
                FROM customer_accounts
                WHERE COALESCE(current_balance, 0) > 0
                ORDER BY current_balance DESC, name ASC
                LIMIT 30
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lines.add(new SheetLine(rs.getString("label"), defaultZero(rs.getBigDecimal("amount"))));
            }
        }
        if (lines.isEmpty()) {
            lines.add(new SheetLine("No receivables", BigDecimal.ZERO));
        }
        return lines;
    }

    private static List<SheetLine> loadExpenses(Connection conn, LocalDate from, LocalDate to, Integer locationId,
                                                String status) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String sql = """
                SELECT category,
                       COALESCE(NULLIF(TRIM(payee), ''), NULLIF(TRIM(description), ''), category) AS label,
                       SUM(COALESCE(amount, 0)) AS amount
                FROM expenses
                WHERE status = ?
                  AND (? IS NULL OR location_id = ?)
                  AND expense_date BETWEEN ? AND ?
                GROUP BY category, COALESCE(NULLIF(TRIM(payee), ''), NULLIF(TRIM(description), ''), category)
                ORDER BY category ASC, label ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            setNullableInteger(ps, 2, locationId);
            setNullableInteger(ps, 3, locationId);
            ps.setDate(4, Date.valueOf(from));
            ps.setDate(5, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String label = rs.getString("category") + " - " + rs.getString("label");
                    lines.add(new SheetLine(label, defaultZero(rs.getBigDecimal("amount"))));
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(new SheetLine("PAID".equals(status) ? "No expenses" : "No payables", BigDecimal.ZERO));
        }
        return lines;
    }

    private static List<SheetLine> loadDrawerCash(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                                  Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String sql = """
                SELECT COALESCE(NULLIF(TRIM(device_name), ''), device_id::text, 'Device') AS device_label,
                       COALESCE(NULLIF(TRIM(drawer_name), ''), 'Drawer') AS drawer_label,
                       GREATEST(COALESCE(cash_to_remove, COALESCE(counted_cash, 0) - COALESCE(opening_cash, 0), 0), 0) AS amount
                FROM cash_drawer_sessions
                WHERE (? IS NULL OR location_id = ?)
                  AND status = 'CLOSED'
                  AND closed_at IS NOT NULL
                  AND (closed_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + sessionFilterSql(cashDrawerSessionIds, "cash_drawer_session_id") + """
                ORDER BY closed_at ASC, drawer_name ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(
                            rs.getString("device_label") + " / " + rs.getString("drawer_label") + " CIH",
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
        }
        if (lines.isEmpty()) {
            lines.add(new SheetLine("No submitted drawer balances", BigDecimal.ZERO));
        }
        return lines;
    }

    public static DrawSessionRange findDrawSessionRange(String storeZoneId, LocalDate selectedDate) throws SQLException {
        List<DrawSessionRange> ranges = findDrawSessionRanges(storeZoneId, selectedDate, selectedDate);
        return ranges.isEmpty() ? null : ranges.get(0);
    }

    public static List<DrawSessionRange> findDrawSessionRanges(String storeZoneId, LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            Integer locationId = SessionManager.getCurrentLocationId();
            String sql = """
                    SELECT cash_drawer_session_id,
                           COALESCE(NULLIF(TRIM(device_name), ''), device_id::text, 'Device') AS device_label,
                           COALESCE(NULLIF(TRIM(drawer_name), ''), 'Drawer') AS drawer_label,
                           (opened_at AT TIME ZONE ?)::date AS opened_date,
                           (COALESCE(closed_at, CURRENT_TIMESTAMP) AT TIME ZONE ?)::date AS closed_date,
                           status
                    FROM cash_drawer_sessions
                    WHERE (? IS NULL OR location_id = ?)
                      AND (opened_at AT TIME ZONE ?)::date <= ?
                      AND (COALESCE(closed_at, CURRENT_TIMESTAMP) AT TIME ZONE ?)::date >= ?
                    ORDER BY CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END,
                             COALESCE(closed_at, opened_at) DESC,
                             cash_drawer_session_id DESC
                    """;
            List<DrawSessionRange> ranges = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, storeZoneId);
                ps.setString(2, storeZoneId);
                setNullableInteger(ps, 3, locationId);
                setNullableInteger(ps, 4, locationId);
                ps.setString(5, storeZoneId);
                ps.setDate(6, Date.valueOf(to));
                ps.setString(7, storeZoneId);
                ps.setDate(8, Date.valueOf(from));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ranges.add(new DrawSessionRange(
                                rs.getLong("cash_drawer_session_id"),
                                rs.getDate("opened_date").toLocalDate(),
                                rs.getDate("closed_date").toLocalDate(),
                                rs.getString("device_label") + " / " + rs.getString("drawer_label"),
                                rs.getString("status")
                        ));
                    }
                }
            }
            return ranges;
        }
    }

    private static List<SheetLine> loadDeviceSales(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                                   Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String normalSalesSql = """
                SELECT COALESCE(NULLIF(TRIM(receipt_device_id), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device') AS device_label,
                       SUM(COALESCE(amount_paid, 0)) AS amount
                FROM sales
                WHERE (? IS NULL OR location_id = ?)
                  AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + cashDrawerFilterSql(cashDrawerSessionIds, "cash_drawer_session_id", "payment_method") + """
                GROUP BY COALESCE(NULLIF(TRIM(receipt_device_id), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device')
                ORDER BY device_label
                """;
        try (PreparedStatement ps = conn.prepareStatement(normalSalesSql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(rs.getString("device_label"), defaultZero(rs.getBigDecimal("amount"))));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(new SheetLine("No device sales logged", BigDecimal.ZERO));
        }
        return lines;
    }

    private static List<SheetLine> loadDeviceOrders(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                                    Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String orderSql = """
                SELECT COALESCE(NULLIF(TRIM(device_name), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device') AS device_label,
                       SUM(CASE
                            WHEN payment_action = 'PAYMENT' THEN COALESCE(payment_amount, 0)
                            WHEN payment_action IN ('REFUND', 'REVERSAL') THEN -COALESCE(payment_amount, 0)
                            ELSE 0
                       END) AS amount
                FROM custom_order_payments p
                WHERE (? IS NULL OR EXISTS (
                    SELECT 1 FROM custom_orders co
                    WHERE co.custom_order_id = p.custom_order_id
                      AND co.location_id = ?
                ))
                  AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + cashDrawerFilterSql(cashDrawerSessionIds, "cash_drawer_session_id", "payment_method") + """
                GROUP BY COALESCE(NULLIF(TRIM(device_name), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device')
                ORDER BY device_label
                """;
        try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(rs.getString("device_label"), defaultZero(rs.getBigDecimal("amount"))));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(new SheetLine("No device orders logged", BigDecimal.ZERO));
        }
        return lines;
    }

    private static List<SheetLine> loadDrawerMatchChecks(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                                         Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        if (cashDrawerSessionIds == null || cashDrawerSessionIds.isEmpty()) {
            lines.add(new SheetLine("Match a draw session to run cash drawer checks", BigDecimal.ZERO));
            return lines;
        }

        String salesSql = """
                SELECT COALESCE(NULLIF(TRIM(receipt_device_id), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device') AS device_label,
                       COALESCE(NULLIF(TRIM(cash_drawer_name), ''), 'No Drawer') AS drawer_label,
                       COALESCE(amount_paid, 0) AS amount
                FROM sales
                WHERE (? IS NULL OR location_id = ?)
                  AND UPPER(COALESCE(payment_method, '')) = 'CASH'
                  AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + notInSessionFilterSql(cashDrawerSessionIds, "cash_drawer_session_id") + """
                ORDER BY created_at ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(salesSql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(
                            "Cash sale outside matched draw - " + rs.getString("device_label") + " / " + rs.getString("drawer_label"),
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
        }

        String orderSql = """
                SELECT COALESCE(NULLIF(TRIM(device_name), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device') AS device_label,
                       COALESCE(NULLIF(TRIM(cash_drawer_name), ''), 'No Drawer') AS drawer_label,
                       CASE
                            WHEN payment_action = 'PAYMENT' THEN COALESCE(payment_amount, 0)
                            WHEN payment_action IN ('REFUND', 'REVERSAL') THEN -COALESCE(payment_amount, 0)
                            ELSE 0
                       END AS amount
                FROM custom_order_payments p
                WHERE (? IS NULL OR EXISTS (
                    SELECT 1 FROM custom_orders co
                    WHERE co.custom_order_id = p.custom_order_id
                      AND co.location_id = ?
                ))
                  AND UPPER(COALESCE(payment_method, '')) = 'CASH'
                  AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + notInSessionFilterSql(cashDrawerSessionIds, "cash_drawer_session_id") + """
                ORDER BY created_at ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(orderSql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            index = bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(
                            "Cash order outside matched draw - " + rs.getString("device_label") + " / " + rs.getString("drawer_label"),
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(new SheetLine("All cash sales and orders match the selected draw sessions", BigDecimal.ZERO));
        }
        return lines;
    }

    private static BigDecimal loadPreviousBalanceCf(Connection conn, LocalDate periodStart, Integer locationId) throws SQLException {
        String sql = """
                SELECT balance_cf
                FROM balance_sheet_submissions
                WHERE (? IS NULL OR location_id = ?)
                  AND period_end < ?
                ORDER BY period_end DESC, submitted_at DESC, balance_sheet_submission_id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, locationId);
            setNullableInteger(ps, 2, locationId);
            ps.setDate(3, Date.valueOf(periodStart));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return defaultZero(rs.getBigDecimal("balance_cf"));
                }
            }
        }
        return null;
    }

    private static BigDecimal total(List<SheetLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SheetLine line : lines) {
            total = total.add(defaultZero(line.amount()));
        }
        return total;
    }

    private static String formatIncomeLabel(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "UNKNOWN";
        }
        return switch (paymentMethod.trim().toUpperCase()) {
            case "CASH" -> "POS CASH";
            case "CARD" -> "POS CARD";
            case "MMG" -> "MMG";
            case "CHEQUE" -> "CHEQUES";
            case "ACCOUNT" -> "ACCOUNT CHARGES";
            default -> paymentMethod.trim().toUpperCase();
        };
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String sessionFilterSql(List<Long> sessionIds, String columnName) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return "";
        }
        StringJoiner placeholders = new StringJoiner(", ", " AND " + columnName + " IN (", ") ");
        for (int i = 0; i < sessionIds.size(); i++) {
            placeholders.add("?");
        }
        return placeholders.toString();
    }

    private static String cashDrawerFilterSql(List<Long> sessionIds, String sessionColumnName, String paymentColumnName) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return "";
        }
        StringJoiner placeholders = new StringJoiner(", ", " AND (UPPER(COALESCE(" + paymentColumnName + ", '')) <> 'CASH' OR " + sessionColumnName + " IN (", ")) ");
        for (int i = 0; i < sessionIds.size(); i++) {
            placeholders.add("?");
        }
        return placeholders.toString();
    }

    private static String notInSessionFilterSql(List<Long> sessionIds, String columnName) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return "";
        }
        StringJoiner placeholders = new StringJoiner(", ", " AND (" + columnName + " IS NULL OR " + columnName + " NOT IN (", ")) ");
        for (int i = 0; i < sessionIds.size(); i++) {
            placeholders.add("?");
        }
        return placeholders.toString();
    }

    private static int bindSessionIds(PreparedStatement ps, int startIndex, List<Long> sessionIds) throws SQLException {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return startIndex;
        }
        int index = startIndex;
        for (Long sessionId : sessionIds) {
            ps.setLong(index++, sessionId);
        }
        return index;
    }

    private static String encodeLines(List<SheetLine> lines) {
        StringBuilder encoded = new StringBuilder();
        for (SheetLine line : lines) {
            if (!encoded.isEmpty()) {
                encoded.append('\n');
            }
            encoded.append(escapeLine(line.label()))
                    .append('\t')
                    .append(defaultZero(line.amount()).toPlainString());
        }
        return encoded.toString();
    }

    private static List<SheetLine> decodeLines(String encoded) {
        List<SheetLine> lines = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return lines;
        }
        for (String row : encoded.split("\\n")) {
            String[] parts = row.split("\\t", 2);
            if (parts.length != 2) {
                continue;
            }
            try {
                lines.add(new SheetLine(unescapeLine(parts[0]), new BigDecimal(parts[1])));
            } catch (NumberFormatException ignored) {
                lines.add(new SheetLine(unescapeLine(parts[0]), BigDecimal.ZERO));
            }
        }
        return lines;
    }

    private static String escapeLine(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    private static String unescapeLine(String value) {
        return value == null ? "" : value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\");
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isBlank() ? null : value.trim();
    }

    private static String defaultText(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    public record ExpenseEntry(LocalDate expenseDate, String category, String payee, String description,
                               BigDecimal amount, String paymentMethod, String paymentReference, String status) {
    }

    public record SheetLine(String label, BigDecimal amount) {
    }

    public record DrawSessionRange(long sessionId, LocalDate openedDate, LocalDate closedDate, String label, String status) {
    }

    public record SubmissionOption(long submissionId, LocalDate periodStart, LocalDate periodEnd,
                                   java.time.LocalDateTime submittedAt, String submittedByName,
                                   BigDecimal balanceCf) {
        @Override
        public String toString() {
            return periodStart + " to " + periodEnd
                    + " - " + defaultText(submittedByName)
                    + " - CF " + defaultZero(balanceCf).toPlainString();
        }
    }

    public record BalanceSheet(Long submissionId,
                               LocalDate periodStart,
                               LocalDate periodEnd,
                               java.time.LocalDateTime submittedAt,
                               String submittedByName,
                               String notes,
                               List<SheetLine> income,
                               List<SheetLine> receivables,
                               List<SheetLine> expenses,
                               List<SheetLine> payables,
                               List<SheetLine> drawerCash,
                               List<SheetLine> deviceSales,
                               List<SheetLine> deviceOrders,
                               List<SheetLine> drawerChecks,
                               BigDecimal cashInHand,
                               BigDecimal balanceBf,
                               BigDecimal totalIncome,
                               BigDecimal totalReceivables,
                               BigDecimal totalExpenses,
                               BigDecimal totalPayables,
                               BigDecimal balanceCf) {
    }
}
