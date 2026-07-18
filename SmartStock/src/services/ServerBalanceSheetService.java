package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerBalanceSheetService {
    private static final long SCHEMA_LOCK_KEY = 7_340_210_001L;
    private static final DateTimeFormatter ACCOUNT_PAYMENT_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd h:mm a");
    private static final Set<String> SCHEMA_READY = ConcurrentHashMap.newKeySet();

    private ServerBalanceSheetService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
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
                    CREATE TABLE IF NOT EXISTS cheque_bank_deposits (
                        cheque_bank_deposit_id BIGSERIAL PRIMARY KEY,
                        location_id INTEGER REFERENCES locations(location_id),
                        source_type TEXT NOT NULL,
                        source_id TEXT NOT NULL,
                        amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
                        payment_reference TEXT,
                        deposited_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deposited_by_user_id INTEGER REFERENCES users(user_id),
                        deposited_by_name TEXT,
                        notes TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT cheque_bank_deposits_amount_chk CHECK (amount >= 0),
                        CONSTRAINT cheque_bank_deposits_source_unique UNIQUE (source_type, source_id)
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS cheque_bank_deposits_location_deposited_idx ON cheque_bank_deposits(location_id, deposited_at DESC)");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bank_transactions (
                        bank_transaction_id BIGSERIAL PRIMARY KEY,
                        location_id INTEGER REFERENCES locations(location_id),
                        transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,
                        transaction_name TEXT NOT NULL,
                        transaction_direction TEXT NOT NULL,
                        amount NUMERIC(12, 2) NOT NULL,
                        payment_reference TEXT,
                        source_type TEXT,
                        source_id TEXT,
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT bank_transactions_amount_chk CHECK (amount >= 0),
                        CONSTRAINT bank_transactions_direction_chk CHECK (transaction_direction IN ('PAID', 'RECEIVED'))
                    )
                    """);
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS bank_transactions_source_unique_idx ON bank_transactions(source_type, source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS bank_transactions_location_date_idx ON bank_transactions(location_id, transaction_date DESC)");
            ensureBankTransactionIdDefault(stmt);
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
                        device_payment_lines TEXT,
                        account_payment_lines TEXT,
                        bank_transaction_lines TEXT,
                        pending_cheque_lines TEXT,
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
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS device_payment_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS account_payment_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS bank_transaction_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS pending_cheque_lines TEXT");
            stmt.executeUpdate("ALTER TABLE balance_sheet_submissions ADD COLUMN IF NOT EXISTS drawer_check_lines TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS balance_sheet_submissions_location_period_idx ON balance_sheet_submissions(location_id, period_start DESC, period_end DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS balance_sheet_submissions_submitted_by_user_idx ON balance_sheet_submissions(submitted_by_user_id)");
            if (tableExists(conn, "customer_account_transactions")) {
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS device_id TEXT");
                stmt.executeUpdate("ALTER TABLE customer_account_transactions ADD COLUMN IF NOT EXISTS device_name TEXT");
            }
        }
        SupabaseSecurityHardening.protectInternalTable(conn, "balance_sheet_submissions");
        SupabaseSecurityHardening.protectInternalTable(conn, "expenses");
        SupabaseSecurityHardening.protectInternalTable(conn, "cheque_bank_deposits");
        SupabaseSecurityHardening.protectInternalTable(conn, "bank_transactions");
    }

    private static String databaseCacheKey(Connection conn) {
        try {
            String url = conn.getMetaData().getURL();
            return url == null || url.isBlank() ? "unknown" : url;
        } catch (SQLException ex) {
            return "unknown";
        }
    }

    private static void ensureBankTransactionIdDefault(Connection conn) throws SQLException {
        if (!tableExists(conn, "bank_transactions")) {
            return;
        }
        String sql = """
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'bank_transactions'
                  AND column_name = 'bank_transaction_id'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String columnDefault = rs.getString("column_default");
                if (columnDefault != null && columnDefault.contains("bank_transactions_bank_transaction_id_seq")) {
                    return;
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            ensureBankTransactionIdDefault(stmt);
        }
    }

    private static void ensureBankTransactionIdDefault(Statement stmt) throws SQLException {
        stmt.executeUpdate("CREATE SEQUENCE IF NOT EXISTS bank_transactions_bank_transaction_id_seq");
        stmt.executeUpdate("ALTER SEQUENCE bank_transactions_bank_transaction_id_seq OWNED BY bank_transactions.bank_transaction_id");
        stmt.executeUpdate("ALTER TABLE bank_transactions ALTER COLUMN bank_transaction_id SET DEFAULT nextval('bank_transactions_bank_transaction_id_seq')");
        stmt.execute("""
                SELECT setval(
                    'bank_transactions_bank_transaction_id_seq',
                    GREATEST(COALESCE((SELECT MAX(bank_transaction_id) FROM bank_transactions), 0), 1),
                    COALESCE((SELECT MAX(bank_transaction_id) FROM bank_transactions), 0) > 0
                )
                """);
    }

    public static void recordPayrollExpense(long payrollPaymentId, BigDecimal amount, String employeeName,
                                            LocalDate expenseDate, String reference) throws SQLException {
        recordPayrollExpense(payrollPaymentId, amount, employeeName, expenseDate, reference,
                ServerRequestIdentity.locationId());
    }

    public static void recordPayrollBankTransaction(long payrollPaymentId, BigDecimal amount, String employeeName,
                                                    LocalDate transactionDate, String paymentReference,
                                                    Integer locationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            recordPayrollBankTransaction(conn,payrollPaymentId,amount,employeeName,transactionDate,paymentReference,
                    locationId,ServerRequestIdentity.userId(),ServerRequestIdentity.userName());
        }
    }

    public static void recordPayrollBankTransaction(Connection conn,long payrollPaymentId,BigDecimal amount,
                                                     String employeeName,LocalDate transactionDate,
                                                     String paymentReference,Integer locationId,
                                                     Integer actorUserId,String actorName)throws SQLException{
            ensureSchema(conn);ensureBankTransactionIdDefault(conn);
            String sql = """
                    INSERT INTO bank_transactions (
                        location_id, transaction_date, transaction_name, transaction_direction,
                        amount, payment_reference, source_type, source_id,
                        created_by_user_id, created_by_name
                    )
                    VALUES (?, ?, ?, 'PAID', ?, ?, 'PAYROLL', ?, ?, ?)
                    ON CONFLICT (source_type, source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL
                    DO UPDATE SET
                        location_id = EXCLUDED.location_id,
                        transaction_date = EXCLUDED.transaction_date,
                        transaction_name = EXCLUDED.transaction_name,
                        transaction_direction = EXCLUDED.transaction_direction,
                        amount = EXCLUDED.amount,
                        payment_reference = EXCLUDED.payment_reference
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, locationId);
                ps.setDate(2, Date.valueOf(transactionDate));
                ps.setString(3, "Payroll payment - " + defaultText(employeeName));
                ps.setBigDecimal(4, defaultZero(amount));
                ps.setString(5, blankToNull(paymentReference));
                ps.setString(6, String.valueOf(payrollPaymentId));
                setNullableInteger(ps, 7, actorUserId);
                ps.setString(8, actorName);
                ps.executeUpdate();
            }
    }

    public static void recordPayrollExpense(long payrollPaymentId, BigDecimal amount, String employeeName,
                                            LocalDate expenseDate, String reference, Integer locationId) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            recordPayrollExpense(conn,payrollPaymentId,amount,employeeName,expenseDate,reference,locationId,
                    ServerRequestIdentity.userId(),ServerRequestIdentity.userName());
        }
    }

    public static void recordPayrollExpense(Connection conn,long payrollPaymentId,BigDecimal amount,
                                            String employeeName,LocalDate expenseDate,String reference,
                                            Integer locationId,Integer actorUserId,String actorName)throws SQLException{
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
                setNullableInteger(ps, 8, actorUserId);
                ps.setString(9, actorName);
                ps.executeUpdate();
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
                setNullableInteger(ps, 1, ServerRequestIdentity.locationId());
                ps.setDate(2, Date.valueOf(entry.expenseDate()));
                ps.setString(3, entry.category());
                ps.setString(4, entry.payee());
                ps.setString(5, entry.description());
                ps.setBigDecimal(6, defaultZero(entry.amount()));
                ps.setString(7, entry.paymentMethod());
                ps.setString(8, entry.paymentReference());
                ps.setString(9, entry.status());
                setNullableInteger(ps, 10, ServerRequestIdentity.userId());
                ps.setString(11, ServerRequestIdentity.userName());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        SyncOutboxService.recordEvent(conn, "EXPENSE_CREATED", Map.of(
                                "expense_id", rs.getLong("expense_id"),
                                "location_id", ServerRequestIdentity.locationId() == null ? "" : ServerRequestIdentity.locationId(),
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

    public static List<ExpenseOption> listDeletableExpenses(LocalDate from, LocalDate to, String status) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    SELECT expense_id,
                           expense_date,
                           category,
                           COALESCE(payee, '') AS payee,
                           COALESCE(description, '') AS description,
                           COALESCE(amount, 0) AS amount,
                           status
                    FROM expenses
                    WHERE status = ?
                      AND (? IS NULL OR location_id = ?)
                      AND expense_date BETWEEN ? AND ?
                      AND source_type IS NULL
                    ORDER BY expense_date DESC, category ASC, payee ASC, expense_id DESC
                    """;
            List<ExpenseOption> expenses = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
                setNullableInteger(ps, 3, ServerRequestIdentity.locationId());
                ps.setDate(4, Date.valueOf(from));
                ps.setDate(5, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        expenses.add(new ExpenseOption(
                                rs.getLong("expense_id"),
                                rs.getDate("expense_date").toLocalDate(),
                                rs.getString("category"),
                                rs.getString("payee"),
                                rs.getString("description"),
                                defaultZero(rs.getBigDecimal("amount")),
                                rs.getString("status")
                        ));
                    }
                }
            }
            return expenses;
        }
    }

    public static void deleteManualExpense(long expenseId, LocalDate from, LocalDate to, String status) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    DELETE FROM expenses
                    WHERE expense_id = ?
                      AND (? IS NULL OR location_id = ?)
                      AND expense_date BETWEEN ? AND ?
                      AND status = ?
                      AND source_type IS NULL
                    RETURNING expense_id,
                              expense_date,
                              category,
                              COALESCE(payee, '') AS payee,
                              COALESCE(amount, 0) AS amount,
                              status
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, expenseId);
                setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
                setNullableInteger(ps, 3, ServerRequestIdentity.locationId());
                ps.setDate(4, Date.valueOf(from));
                ps.setDate(5, Date.valueOf(to));
                ps.setString(6, status);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        SyncOutboxService.recordEvent(conn, "EXPENSE_DELETED", Map.of(
                                "expense_id", rs.getLong("expense_id"),
                                "location_id", ServerRequestIdentity.locationId() == null ? "" : ServerRequestIdentity.locationId(),
                                "expense_date", rs.getDate("expense_date").toLocalDate(),
                                "category", rs.getString("category"),
                                "payee", rs.getString("payee"),
                                "amount", defaultZero(rs.getBigDecimal("amount")),
                                "status", rs.getString("status")
                        ));
                        return;
                    }
                }
            }
        }
        throw new SQLException("Only manually entered rows from the current balance sheet can be deleted.");
    }

    public static List<ChequeDepositOption> listPendingChequeDeposits() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            return loadPendingChequeDeposits(conn, ServerRequestIdentity.locationId());
        }
    }

    public static void markChequeDeposited(ChequeDepositOption cheque, String notes) throws SQLException {
        if (cheque == null) {
            throw new SQLException("No cheque was selected.");
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            ensureBankTransactionIdDefault(conn);
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            String depositSql = """
                    INSERT INTO cheque_bank_deposits (
                        location_id, source_type, source_id, amount, payment_reference,
                        deposited_by_user_id, deposited_by_name, notes
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_type, source_id) DO NOTHING
                    RETURNING cheque_bank_deposit_id
                    """;
            String bankTransactionSql = """
                    INSERT INTO bank_transactions (
                        location_id, transaction_date, transaction_name, transaction_direction,
                        amount, payment_reference, source_type, source_id,
                        created_by_user_id, created_by_name
                    )
                    VALUES (?, ?, ?, 'RECEIVED', ?, ?, 'CHEQUE_DEPOSIT', ?, ?, ?)
                    ON CONFLICT (source_type, source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL
                    DO UPDATE SET
                        location_id = EXCLUDED.location_id,
                        transaction_date = EXCLUDED.transaction_date,
                        transaction_name = EXCLUDED.transaction_name,
                        transaction_direction = EXCLUDED.transaction_direction,
                        amount = EXCLUDED.amount,
                        payment_reference = EXCLUDED.payment_reference
                    """;
            try (PreparedStatement deposit = conn.prepareStatement(depositSql);
                 PreparedStatement bankTransaction = conn.prepareStatement(bankTransactionSql)) {
                setNullableInteger(deposit, 1, ServerRequestIdentity.locationId());
                deposit.setString(2, cheque.sourceType());
                deposit.setString(3, cheque.sourceId());
                deposit.setBigDecimal(4, defaultZero(cheque.amount()));
                deposit.setString(5, blankToNull(cheque.reference()));
                setNullableInteger(deposit, 6, ServerRequestIdentity.userId());
                deposit.setString(7, ServerRequestIdentity.userName());
                deposit.setString(8, blankToNull(notes));
                try (ResultSet rs = deposit.executeQuery()) {
                    if (rs.next()) {
                        SyncOutboxService.recordEvent(conn, "CHEQUE_DEPOSITED", Map.of(
                                "cheque_bank_deposit_id", rs.getLong("cheque_bank_deposit_id"),
                                "location_id", ServerRequestIdentity.locationId() == null ? "" : ServerRequestIdentity.locationId(),
                                "source_type", cheque.sourceType(),
                                "source_id", cheque.sourceId(),
                                "amount", defaultZero(cheque.amount())
                        ));
                    }
                }
                setNullableInteger(bankTransaction, 1, ServerRequestIdentity.locationId());
                bankTransaction.setDate(2, Date.valueOf(LocalDate.now()));
                bankTransaction.setString(3, chequeDepositTransactionName(cheque));
                bankTransaction.setBigDecimal(4, defaultZero(cheque.amount()));
                bankTransaction.setString(5, blankToNull(cheque.reference()));
                bankTransaction.setString(6, cheque.sourceType() + ":" + cheque.sourceId());
                setNullableInteger(bankTransaction, 7, ServerRequestIdentity.userId());
                bankTransaction.setString(8, ServerRequestIdentity.userName());
                bankTransaction.executeUpdate();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    private static String chequeDepositTransactionName(ChequeDepositOption cheque) {
        String label = blankToNull(cheque.sourceLabel());
        String payer = blankToNull(cheque.payer());
        if (label == null) {
            return payer == null ? "Cheque deposit" : "Cheque deposit - " + payer;
        }
        return payer == null ? "Cheque deposit - " + label : "Cheque deposit - " + label + " - " + payer;
    }

    public static List<PayableOption> listUnpaidPayables(LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            String sql = """
                    SELECT expense_id, expense_date, category,
                           COALESCE(payee, '') AS payee,
                           COALESCE(description, '') AS description,
                           COALESCE(amount, 0) AS amount
                    FROM expenses
                    WHERE status = 'UNPAID'
                      AND (? IS NULL OR location_id = ?)
                      AND expense_date BETWEEN ? AND ?
                      AND COALESCE(amount, 0) > 0
                    ORDER BY expense_date DESC, category ASC, payee ASC, expense_id DESC
                    """;
            List<PayableOption> payables = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, ServerRequestIdentity.locationId());
                setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
                ps.setDate(3, Date.valueOf(from));
                ps.setDate(4, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        payables.add(new PayableOption(
                                rs.getLong("expense_id"),
                                rs.getDate("expense_date").toLocalDate(),
                                rs.getString("category"),
                                rs.getString("payee"),
                                rs.getString("description"),
                                defaultZero(rs.getBigDecimal("amount"))
                        ));
                    }
                }
            }
            return payables;
        }
    }

    public static void recordPayablePayment(long expenseId, LocalDate paymentDate, BigDecimal paymentAmount,
                                            String paymentMethod, String paymentReference) throws SQLException {
        if (paymentDate == null) {
            throw new SQLException("Payment date is required.");
        }
        BigDecimal cleanPayment = defaultZero(paymentAmount);
        if (cleanPayment.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("Payment amount must be greater than zero.");
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            conn.setAutoCommit(false);
            try {
                PayableOption payable = lockPayable(conn, expenseId);
                if (payable == null) {
                    throw new SQLException("Account payable was not found.");
                }
                if (cleanPayment.compareTo(payable.amount()) > 0) {
                    throw new SQLException("Payment amount cannot exceed the payable balance of " + payable.amount() + ".");
                }

                BigDecimal remaining = payable.amount().subtract(cleanPayment);
                if (remaining.compareTo(BigDecimal.ZERO) == 0) {
                    String updateSql = """
                            UPDATE expenses
                            SET expense_date = ?,
                                amount = ?,
                                payment_method = ?,
                                payment_reference = ?,
                                status = 'PAID',
                                updated_at = CURRENT_TIMESTAMP
                            WHERE expense_id = ?
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setDate(1, Date.valueOf(paymentDate));
                        ps.setBigDecimal(2, cleanPayment);
                        ps.setString(3, paymentMethod);
                        ps.setString(4, paymentReference);
                        ps.setLong(5, expenseId);
                        ps.executeUpdate();
                    }
                } else {
                    String reduceSql = """
                            UPDATE expenses
                            SET amount = ?,
                                updated_at = CURRENT_TIMESTAMP
                            WHERE expense_id = ?
                            """;
                    try (PreparedStatement ps = conn.prepareStatement(reduceSql)) {
                        ps.setBigDecimal(1, remaining);
                        ps.setLong(2, expenseId);
                        ps.executeUpdate();
                    }
                    insertPaidPayableExpense(conn, payable, paymentDate, cleanPayment, paymentMethod, paymentReference);
                }

                SyncOutboxService.recordEvent(conn, "ACCOUNT_PAYABLE_PAID", Map.of(
                        "expense_id", expenseId,
                        "payment_date", paymentDate,
                        "payment_amount", cleanPayment,
                        "remaining_amount", remaining,
                        "payment_method", paymentMethod == null ? "" : paymentMethod
                ));
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static BalanceSheet loadBalanceSheet(LocalDate from, LocalDate to, String storeZoneId) throws SQLException {
        return loadBalanceSheet(from, to, storeZoneId, List.of());
    }

    public static BalanceSheet loadBalanceSheet(LocalDate from, LocalDate to, String storeZoneId, List<Long> cashDrawerSessionIds) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            Integer locationId = ServerRequestIdentity.locationId();
            syncPaidPayrollExpenses(conn, storeZoneId, locationId);
            List<SheetLine> income = loadIncome(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> receivables = loadReceivables(conn, locationId);
            List<SheetLine> expenses = loadExpenses(conn, from, to, locationId, "PAID");
            List<SheetLine> payables = loadExpenses(conn, from, to, locationId, "UNPAID");
            List<SheetLine> drawerCash = loadDrawerCash(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> deviceSales = loadDeviceSales(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> deviceOrders = loadDeviceOrders(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> devicePayments = loadDevicePayments(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<SheetLine> accountPayments = loadAccountPayments(conn, from, to, storeZoneId, locationId, cashDrawerSessionIds);
            List<BankTransactionLine> bankTransactions = loadBankTransactions(conn, from, to, locationId);
            List<ChequeDepositOption> pendingCheques = loadPendingChequeDeposits(conn, locationId);
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
                    income, receivables, expenses, payables, drawerCash, deviceSales, deviceOrders, devicePayments, accountPayments, bankTransactions, pendingCheques, drawerChecks, cashInHand,
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
                        payable_lines, drawer_cash_lines, device_sales_lines, device_order_lines, device_payment_lines, account_payment_lines, bank_transaction_lines, pending_cheque_lines, drawer_check_lines, submitted_by_user_id,
                        submitted_by_name, notes
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING balance_sheet_submission_id
                    """;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                setNullableInteger(ps, 1, ServerRequestIdentity.locationId());
                ps.setString(2, ServerRequestIdentity.locationName());
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
                ps.setString(20, encodeLines(sheet.devicePayments()));
                ps.setString(21, encodeLines(sheet.accountPayments()));
                ps.setString(22, encodeBankTransactions(sheet.bankTransactions()));
                ps.setString(23, encodeCheques(sheet.pendingCheques()));
                ps.setString(24, encodeLines(sheet.drawerChecks()));
                setNullableInteger(ps, 25, ServerRequestIdentity.userId());
                ps.setString(26, ServerRequestIdentity.userName());
                ps.setString(27, blankToNull(notes));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long submissionId = rs.getLong("balance_sheet_submission_id");
                        SyncOutboxService.recordEvent(conn, "BALANCE_SHEET_SUBMITTED", Map.of(
                                "balance_sheet_submission_id", submissionId,
                                "location_id", ServerRequestIdentity.locationId() == null ? "" : ServerRequestIdentity.locationId(),
                                "period_start", from,
                                "period_end", to,
                                "cash_in_hand", sheet.cashInHand(),
                                "balance_cf", sheet.balanceCf(),
                                "submitted_by_user_id", ServerRequestIdentity.userId() == null ? "" : ServerRequestIdentity.userId()
                        ));
                        return submissionId;
                    }
                }
            }
        }
        throw new SQLException("Balance sheet submission did not return an id.");
    }

    /* Connection-bound mutations keep the accounting write, sync event and API
       idempotency row in one transaction. */
    public static void addManualExpense(Connection conn,ExpenseEntry entry)throws SQLException{
        ensureSchema(conn);try(PreparedStatement ps=conn.prepareStatement("""
                INSERT INTO expenses (location_id,expense_date,category,payee,description,amount,payment_method,payment_reference,status,created_by_user_id,created_by_name)
                VALUES (?,?,?,?,?,?,?,?,?,?,?) RETURNING expense_id
                """)){setNullableInteger(ps,1,ServerRequestIdentity.locationId());ps.setDate(2,Date.valueOf(entry.expenseDate()));ps.setString(3,entry.category());ps.setString(4,entry.payee());ps.setString(5,entry.description());ps.setBigDecimal(6,defaultZero(entry.amount()));ps.setString(7,entry.paymentMethod());ps.setString(8,entry.paymentReference());ps.setString(9,entry.status());setNullableInteger(ps,10,ServerRequestIdentity.userId());ps.setString(11,ServerRequestIdentity.userName());
            try(ResultSet rs=ps.executeQuery()){if(rs.next())SyncOutboxService.recordEvent(conn,"EXPENSE_CREATED",Map.of("expense_id",rs.getLong(1),"location_id",ServerRequestIdentity.locationId()==null?"":ServerRequestIdentity.locationId(),"expense_date",entry.expenseDate(),"category",entry.category(),"amount",defaultZero(entry.amount()),"status",entry.status()));}}
    }

    public static void deleteManualExpense(Connection conn,long expenseId,LocalDate from,LocalDate to,String status)throws SQLException{
        ensureSchema(conn);try(PreparedStatement ps=conn.prepareStatement("""
                DELETE FROM expenses WHERE expense_id=? AND (? IS NULL OR location_id=?) AND expense_date BETWEEN ? AND ? AND status=? AND source_type IS NULL
                RETURNING expense_id,expense_date,category,COALESCE(payee,''),COALESCE(amount,0),status
                """)){ps.setLong(1,expenseId);setNullableInteger(ps,2,ServerRequestIdentity.locationId());setNullableInteger(ps,3,ServerRequestIdentity.locationId());ps.setDate(4,Date.valueOf(from));ps.setDate(5,Date.valueOf(to));ps.setString(6,status);
            try(ResultSet rs=ps.executeQuery()){if(rs.next()){SyncOutboxService.recordEvent(conn,"EXPENSE_DELETED",Map.of("expense_id",rs.getLong(1),"location_id",ServerRequestIdentity.locationId()==null?"":ServerRequestIdentity.locationId(),"expense_date",rs.getDate(2).toLocalDate(),"category",rs.getString(3),"payee",rs.getString(4),"amount",defaultZero(rs.getBigDecimal(5)),"status",rs.getString(6)));return;}}}
        throw new SQLException("Only manually entered rows from the current balance sheet can be deleted.");
    }

    public static void markChequeDeposited(Connection conn,ChequeDepositOption cheque,String notes)throws SQLException{
        if(cheque==null)throw new SQLException("No cheque was selected.");ensureSchema(conn);ensureBankTransactionIdDefault(conn);
        try(PreparedStatement deposit=conn.prepareStatement("""
                INSERT INTO cheque_bank_deposits (location_id,source_type,source_id,amount,payment_reference,deposited_by_user_id,deposited_by_name,notes)
                VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (source_type,source_id) DO NOTHING RETURNING cheque_bank_deposit_id
                """);PreparedStatement bank=conn.prepareStatement("""
                INSERT INTO bank_transactions (location_id,transaction_date,transaction_name,transaction_direction,amount,payment_reference,source_type,source_id,created_by_user_id,created_by_name)
                VALUES (?,?,?,'RECEIVED',?,?,'CHEQUE_DEPOSIT',?,?,?)
                ON CONFLICT (source_type,source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL DO UPDATE SET location_id=EXCLUDED.location_id,transaction_date=EXCLUDED.transaction_date,transaction_name=EXCLUDED.transaction_name,transaction_direction=EXCLUDED.transaction_direction,amount=EXCLUDED.amount,payment_reference=EXCLUDED.payment_reference
                """)){
            setNullableInteger(deposit,1,ServerRequestIdentity.locationId());deposit.setString(2,cheque.sourceType());deposit.setString(3,cheque.sourceId());deposit.setBigDecimal(4,defaultZero(cheque.amount()));deposit.setString(5,blankToNull(cheque.reference()));setNullableInteger(deposit,6,ServerRequestIdentity.userId());deposit.setString(7,ServerRequestIdentity.userName());deposit.setString(8,blankToNull(notes));
            try(ResultSet rs=deposit.executeQuery()){if(rs.next())SyncOutboxService.recordEvent(conn,"CHEQUE_DEPOSITED",Map.of("cheque_bank_deposit_id",rs.getLong(1),"location_id",ServerRequestIdentity.locationId()==null?"":ServerRequestIdentity.locationId(),"source_type",cheque.sourceType(),"source_id",cheque.sourceId(),"amount",defaultZero(cheque.amount())));}
            setNullableInteger(bank,1,ServerRequestIdentity.locationId());bank.setDate(2,Date.valueOf(LocalDate.now()));bank.setString(3,chequeDepositTransactionName(cheque));bank.setBigDecimal(4,defaultZero(cheque.amount()));bank.setString(5,blankToNull(cheque.reference()));bank.setString(6,cheque.sourceType()+":"+cheque.sourceId());setNullableInteger(bank,7,ServerRequestIdentity.userId());bank.setString(8,ServerRequestIdentity.userName());bank.executeUpdate();
        }
    }

    public static void recordPayablePayment(Connection conn,long expenseId,LocalDate paymentDate,BigDecimal paymentAmount,String paymentMethod,String paymentReference)throws SQLException{
        if(paymentDate==null)throw new SQLException("Payment date is required.");BigDecimal clean=defaultZero(paymentAmount);if(clean.signum()<=0)throw new SQLException("Payment amount must be greater than zero.");ensureSchema(conn);
        PayableOption payable=lockPayable(conn,expenseId);if(payable==null)throw new SQLException("Account payable was not found.");if(clean.compareTo(payable.amount())>0)throw new SQLException("Payment amount cannot exceed the payable balance of "+payable.amount()+".");BigDecimal remaining=payable.amount().subtract(clean);
        if(remaining.signum()==0){try(PreparedStatement ps=conn.prepareStatement("UPDATE expenses SET expense_date=?,amount=?,payment_method=?,payment_reference=?,status='PAID',updated_at=CURRENT_TIMESTAMP WHERE expense_id=?")){ps.setDate(1,Date.valueOf(paymentDate));ps.setBigDecimal(2,clean);ps.setString(3,paymentMethod);ps.setString(4,paymentReference);ps.setLong(5,expenseId);ps.executeUpdate();}}
        else{try(PreparedStatement ps=conn.prepareStatement("UPDATE expenses SET amount=?,updated_at=CURRENT_TIMESTAMP WHERE expense_id=?")){ps.setBigDecimal(1,remaining);ps.setLong(2,expenseId);ps.executeUpdate();}insertPaidPayableExpense(conn,payable,paymentDate,clean,paymentMethod,paymentReference);}
        SyncOutboxService.recordEvent(conn,"ACCOUNT_PAYABLE_PAID",Map.of("expense_id",expenseId,"payment_date",paymentDate,"payment_amount",clean,"remaining_amount",remaining,"payment_method",paymentMethod==null?"":paymentMethod));
    }

    public static BalanceSheet loadBalanceSheet(Connection conn,LocalDate from,LocalDate to,String zone,List<Long>sessionIds)throws SQLException{
        ensureSchema(conn);Integer locationId=ServerRequestIdentity.locationId();syncPaidPayrollExpenses(conn,zone,locationId);
        List<SheetLine>income=loadIncome(conn,from,to,zone,locationId,sessionIds),receivables=loadReceivables(conn,locationId),expenses=loadExpenses(conn,from,to,locationId,"PAID"),payables=loadExpenses(conn,from,to,locationId,"UNPAID"),drawerCash=loadDrawerCash(conn,from,to,zone,locationId,sessionIds),deviceSales=loadDeviceSales(conn,from,to,zone,locationId,sessionIds),deviceOrders=loadDeviceOrders(conn,from,to,zone,locationId,sessionIds),devicePayments=loadDevicePayments(conn,from,to,zone,locationId,sessionIds),accountPayments=loadAccountPayments(conn,from,to,zone,locationId,sessionIds),drawerChecks=loadDrawerMatchChecks(conn,from,to,zone,locationId,sessionIds);
        List<BankTransactionLine>bank=loadBankTransactions(conn,from,to,locationId);List<ChequeDepositOption>cheques=loadPendingChequeDeposits(conn,locationId);BigDecimal cash=total(drawerCash),totalIncome=total(income),totalReceivables=total(receivables),totalExpenses=total(expenses),totalPayables=total(payables),bf=loadPreviousBalanceCf(conn,from,locationId);if(bf==null)bf=BigDecimal.ZERO;BigDecimal cf=bf.add(totalIncome).subtract(totalExpenses).subtract(totalPayables);
        return new BalanceSheet(null,null,null,null,null,null,income,receivables,expenses,payables,drawerCash,deviceSales,deviceOrders,devicePayments,accountPayments,bank,cheques,drawerChecks,cash,bf,totalIncome,totalReceivables,totalExpenses,totalPayables,cf);
    }

    public static long submitBalanceSheet(Connection conn,LocalDate from,LocalDate to,String zone,String notes,List<Long>sessionIds)throws SQLException{
        BalanceSheet sheet=loadBalanceSheet(conn,from,to,zone,sessionIds);try(PreparedStatement ps=conn.prepareStatement("""
                INSERT INTO balance_sheet_submissions (location_id,location_name,period_start,period_end,store_timezone,balance_bf,cash_in_hand,total_income,total_receivables,total_expenses,total_payables,balance_cf,income_lines,receivable_lines,expense_lines,payable_lines,drawer_cash_lines,device_sales_lines,device_order_lines,device_payment_lines,account_payment_lines,bank_transaction_lines,pending_cheque_lines,drawer_check_lines,submitted_by_user_id,submitted_by_name,notes)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING balance_sheet_submission_id
                """)){setNullableInteger(ps,1,ServerRequestIdentity.locationId());ps.setString(2,ServerRequestIdentity.locationName());ps.setDate(3,Date.valueOf(from));ps.setDate(4,Date.valueOf(to));ps.setString(5,zone);ps.setBigDecimal(6,sheet.balanceBf());ps.setBigDecimal(7,sheet.cashInHand());ps.setBigDecimal(8,sheet.totalIncome());ps.setBigDecimal(9,sheet.totalReceivables());ps.setBigDecimal(10,sheet.totalExpenses());ps.setBigDecimal(11,sheet.totalPayables());ps.setBigDecimal(12,sheet.balanceCf());ps.setString(13,encodeLines(sheet.income()));ps.setString(14,encodeLines(sheet.receivables()));ps.setString(15,encodeLines(sheet.expenses()));ps.setString(16,encodeLines(sheet.payables()));ps.setString(17,encodeLines(sheet.drawerCash()));ps.setString(18,encodeLines(sheet.deviceSales()));ps.setString(19,encodeLines(sheet.deviceOrders()));ps.setString(20,encodeLines(sheet.devicePayments()));ps.setString(21,encodeLines(sheet.accountPayments()));ps.setString(22,encodeBankTransactions(sheet.bankTransactions()));ps.setString(23,encodeCheques(sheet.pendingCheques()));ps.setString(24,encodeLines(sheet.drawerChecks()));setNullableInteger(ps,25,ServerRequestIdentity.userId());ps.setString(26,ServerRequestIdentity.userName());ps.setString(27,blankToNull(notes));
            try(ResultSet rs=ps.executeQuery()){if(rs.next()){long id=rs.getLong(1);SyncOutboxService.recordEvent(conn,"BALANCE_SHEET_SUBMITTED",Map.of("balance_sheet_submission_id",id,"location_id",ServerRequestIdentity.locationId()==null?"":ServerRequestIdentity.locationId(),"period_start",from,"period_end",to,"cash_in_hand",sheet.cashInHand(),"balance_cf",sheet.balanceCf(),"submitted_by_user_id",ServerRequestIdentity.userId()==null?"":ServerRequestIdentity.userId()));return id;}}}
        throw new SQLException("Balance sheet submission did not return an id.");
    }

    public static List<SubmissionOption> listSubmissions() throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            Integer locationId = ServerRequestIdentity.locationId();
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
                                decodeLines(rs.getString("device_payment_lines")),
                                decodeLines(rs.getString("account_payment_lines")),
                                decodeBankTransactions(rs.getString("bank_transaction_lines")),
                                decodeCheques(rs.getString("pending_cheque_lines")),
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
            stmt.executeUpdate("ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS payment_method TEXT NOT NULL DEFAULT 'CASH'");
            stmt.executeUpdate("ALTER TABLE payroll_payments ADD COLUMN IF NOT EXISTS payment_reference TEXT");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS payroll_payments_location_paid_idx ON payroll_payments(location_id, paid_at DESC)");
            stmt.executeUpdate("""
                    DELETE FROM expenses e
                    USING payroll_payments pp
                    WHERE e.source_type = 'PAYROLL'
                      AND e.source_id = pp.payroll_payment_id::text
                      AND UPPER(COALESCE(pp.payment_method, 'CASH')) = 'BANK'
                    """);
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
                  AND UPPER(COALESCE(pp.payment_method, 'CASH')) <> 'BANK'
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
                WHERE UPPER(COALESCE(pp.payment_method, 'CASH')) <> 'BANK'
                  AND NOT EXISTS (
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

        String bankInsertSql = """
                INSERT INTO bank_transactions (
                    location_id, transaction_date, transaction_name, transaction_direction,
                    amount, payment_reference, source_type, source_id,
                    created_by_user_id, created_by_name
                )
                SELECT COALESCE(pp.location_id, ?),
                       (pp.paid_at AT TIME ZONE ?)::date,
                       'Payroll payment - ' || COALESCE(NULLIF(TRIM(pp.employee_name), ''), 'employee'),
                       'PAID', COALESCE(pp.total_pay, 0), pp.payment_reference,
                       'PAYROLL', pp.payroll_payment_id::text,
                       pp.paid_by_user_id, pp.paid_by_name
                FROM payroll_payments pp
                WHERE UPPER(COALESCE(pp.payment_method, 'CASH')) = 'BANK'
                ON CONFLICT (source_type, source_id) WHERE source_type IS NOT NULL AND source_id IS NOT NULL
                DO UPDATE SET
                    location_id = EXCLUDED.location_id,
                    transaction_date = EXCLUDED.transaction_date,
                    transaction_name = EXCLUDED.transaction_name,
                    transaction_direction = EXCLUDED.transaction_direction,
                    amount = EXCLUDED.amount,
                    payment_reference = EXCLUDED.payment_reference
                """;
        try (PreparedStatement ps = conn.prepareStatement(bankInsertSql)) {
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

    private static PayableOption lockPayable(Connection conn, long expenseId) throws SQLException {
        String sql = """
                SELECT expense_id, expense_date, category,
                       COALESCE(payee, '') AS payee,
                       COALESCE(description, '') AS description,
                       COALESCE(amount, 0) AS amount
                FROM expenses
                WHERE expense_id = ?
                  AND status = 'UNPAID'
                  AND (? IS NULL OR location_id = ?)
                FOR UPDATE
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, expenseId);
            setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
            setNullableInteger(ps, 3, ServerRequestIdentity.locationId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PayableOption(
                            rs.getLong("expense_id"),
                            rs.getDate("expense_date").toLocalDate(),
                            rs.getString("category"),
                            rs.getString("payee"),
                            rs.getString("description"),
                            defaultZero(rs.getBigDecimal("amount"))
                    );
                }
            }
        }
        return null;
    }

    private static void insertPaidPayableExpense(Connection conn, PayableOption payable, LocalDate paymentDate,
                                                BigDecimal amount, String paymentMethod, String paymentReference) throws SQLException {
        String sql = """
                INSERT INTO expenses (
                    location_id, expense_date, category, payee, description, amount,
                    payment_method, payment_reference, status, source_type, source_id,
                    created_by_user_id, created_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PAID', 'PAYABLE_PAYMENT', ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, ServerRequestIdentity.locationId());
            ps.setDate(2, Date.valueOf(paymentDate));
            ps.setString(3, payable.category());
            ps.setString(4, blankToNull(payable.payee()));
            ps.setString(5, blankToNull(payable.description()));
            ps.setBigDecimal(6, amount);
            ps.setString(7, paymentMethod);
            ps.setString(8, paymentReference);
            ps.setString(9, payable.expenseId() + "-" + System.currentTimeMillis());
            setNullableInteger(ps, 10, ServerRequestIdentity.userId());
            ps.setString(11, ServerRequestIdentity.userName());
            ps.executeUpdate();
        }
    }

    private static List<ChequeDepositOption> loadPendingChequeDeposits(Connection conn, Integer locationId) throws SQLException {
        List<String> sources = new ArrayList<>();
        if (tableExists(conn, "sales")) {
            sources.add("""
                    SELECT 'SALE' AS source_type,
                           s.sale_id::text AS source_id,
                           s.created_at AS cheque_at,
                           'Sale ' || COALESCE(NULLIF(s.receipt_number, ''), s.sale_id::text) AS source_label,
                           COALESCE(NULLIF(ca.name, ''), '') AS payer,
                           COALESCE(s.payment_reference, '') AS reference,
                           COALESCE(s.amount_paid, 0) AS amount,
                           s.location_id AS location_id
                    FROM sales s
                    LEFT JOIN customer_accounts ca ON ca.customer_id = s.customer_id
                    WHERE UPPER(COALESCE(s.payment_method, '')) = 'CHEQUE'
                      AND COALESCE(s.amount_paid, 0) > 0
                    """);
        }
        if (tableExists(conn, "custom_order_payments")) {
            sources.add("""
                    SELECT 'CUSTOM_ORDER_PAYMENT' AS source_type,
                           p.custom_order_payment_id::text AS source_id,
                           p.created_at AS cheque_at,
                           'Order ' || COALESCE(NULLIF(co.order_number, ''), p.custom_order_id::text) AS source_label,
                           COALESCE(NULLIF(co.customer_name, ''), '') AS payer,
                           COALESCE(p.payment_reference, '') AS reference,
                           COALESCE(p.payment_amount, 0) AS amount,
                           co.location_id AS location_id
                    FROM custom_order_payments p
                    JOIN custom_orders co ON co.custom_order_id = p.custom_order_id
                    WHERE UPPER(COALESCE(p.payment_method, '')) = 'CHEQUE'
                      AND COALESCE(p.payment_action, 'PAYMENT') = 'PAYMENT'
                      AND COALESCE(p.payment_amount, 0) > 0
                      AND COALESCE(p.payment_reference, '') NOT ILIKE 'Account payment transaction #%'
                    """);
        }
        if (tableExists(conn, "invoice_payments")) {
            sources.add("""
                    SELECT 'INVOICE_PAYMENT' AS source_type,
                           p.invoice_payment_id::text AS source_id,
                           p.created_at AS cheque_at,
                           'Invoice ' || COALESCE(NULLIF(i.invoice_number, ''), p.invoice_id::text) AS source_label,
                           COALESCE(NULLIF(i.customer_name, ''), '') AS payer,
                           COALESCE(p.payment_reference, '') AS reference,
                           COALESCE(p.payment_amount, 0) AS amount,
                           COALESCE(p.location_id, i.location_id) AS location_id
                    FROM invoice_payments p
                    JOIN invoices i ON i.invoice_id = p.invoice_id
                    WHERE UPPER(COALESCE(p.payment_method, '')) = 'CHEQUE'
                      AND COALESCE(p.payment_action, 'PAYMENT') = 'PAYMENT'
                      AND COALESCE(p.payment_amount, 0) > 0
                      AND COALESCE(p.payment_reference, '') NOT ILIKE 'Account payment transaction #%'
                    """);
        }
        if (tableExists(conn, "customer_account_transactions")) {
            sources.add("""
                    SELECT 'ACCOUNT_PAYMENT' AS source_type,
                           t.transaction_id::text AS source_id,
                           t.created_at AS cheque_at,
                           'Account Payment' AS source_label,
                           COALESCE(NULLIF(ca.name, ''), '') AS payer,
                           COALESCE(t.payment_reference, '') AS reference,
                           COALESCE(t.amount, 0) AS amount,
                           t.location_id AS location_id
                    FROM customer_account_transactions t
                    LEFT JOIN customer_accounts ca ON ca.customer_id = t.customer_id
                    WHERE COALESCE(t.transaction_type, '') = 'PAYMENT'
                      AND UPPER(COALESCE(t.payment_method, '')) = 'CHEQUE'
                      AND COALESCE(t.amount, 0) > 0
                    """);
        }
        if (sources.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT src.source_type,
                       src.source_id,
                       src.cheque_at,
                       src.source_label,
                       src.payer,
                       src.reference,
                       src.amount
                FROM (
                """ + String.join("\nUNION ALL\n", sources) + """
                ) src
                LEFT JOIN cheque_bank_deposits d
                  ON d.source_type = src.source_type
                 AND d.source_id = src.source_id
                WHERE d.cheque_bank_deposit_id IS NULL
                  AND (? IS NULL OR src.location_id = ?)
                ORDER BY src.cheque_at ASC, src.source_label ASC
                """;
        List<ChequeDepositOption> cheques = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, locationId);
            setNullableInteger(ps, 2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp chequeAt = rs.getTimestamp("cheque_at");
                    cheques.add(new ChequeDepositOption(
                            rs.getString("source_type"),
                            rs.getString("source_id"),
                            chequeAt == null ? null : chequeAt.toLocalDateTime(),
                            rs.getString("source_label"),
                            rs.getString("payer"),
                            rs.getString("reference"),
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
        }
        return cheques;
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
                SELECT COALESCE(NULLIF(TRIM(payment_method), ''), 'UNKNOWN') AS label,
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
                GROUP BY COALESCE(NULLIF(TRIM(payment_method), ''), 'UNKNOWN')
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
                while (rs.next()) {
                    BigDecimal amount = defaultZero(rs.getBigDecimal("amount"));
                    if (amount.compareTo(BigDecimal.ZERO) != 0) {
                        lines.add(new SheetLine(formatOrderIncomeLabel(rs.getString("label")), amount));
                    }
                }
            }
        }
        if (tableExists(conn, "invoice_payments")) {
            String invoicePaymentSql = """
                    SELECT COALESCE(NULLIF(TRIM(payment_method), ''), 'UNKNOWN') AS label,
                           SUM(CASE
                                WHEN payment_action = 'PAYMENT' THEN COALESCE(payment_amount, 0)
                                WHEN payment_action IN ('REFUND', 'REVERSAL') THEN -COALESCE(payment_amount, 0)
                                ELSE 0
                           END) AS amount
                    FROM invoice_payments
                    WHERE (? IS NULL OR location_id = ?)
                      AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                    """ + cashDrawerFilterSql(cashDrawerSessionIds, "cash_drawer_session_id", "payment_method") + """
                    GROUP BY COALESCE(NULLIF(TRIM(payment_method), ''), 'UNKNOWN')
                    """;
            try (PreparedStatement ps = conn.prepareStatement(invoicePaymentSql)) {
                int index = 1;
                setNullableInteger(ps, index++, locationId);
                setNullableInteger(ps, index++, locationId);
                ps.setString(index++, storeZoneId);
                ps.setDate(index++, Date.valueOf(from));
                ps.setDate(index++, Date.valueOf(to));
                index = bindSessionIds(ps, index, cashDrawerSessionIds);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        BigDecimal amount = defaultZero(rs.getBigDecimal("amount"));
                        if (amount.compareTo(BigDecimal.ZERO) != 0) {
                            lines.add(new SheetLine(formatInvoiceIncomeLabel(rs.getString("label")), amount));
                        }
                    }
                }
            }
        }
        if (tableExists(conn, "customer_account_transactions")) {
            String invoiceAccountSql = """
                    SELECT SUM(COALESCE(amount, 0)) AS amount
                    FROM customer_account_transactions
                    WHERE COALESCE(transaction_type, '') = 'INVOICE_CREDIT'
                      AND (? IS NULL OR location_id = ?)
                      AND (created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                      AND COALESCE(note, '') <> 'Placed on customer account before direct invoice payment.'
                    """;
            try (PreparedStatement ps = conn.prepareStatement(invoiceAccountSql)) {
                setNullableInteger(ps, 1, locationId);
                setNullableInteger(ps, 2, locationId);
                ps.setString(3, storeZoneId);
                ps.setDate(4, Date.valueOf(from));
                ps.setDate(5, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal amount = defaultZero(rs.getBigDecimal("amount"));
                        if (amount.compareTo(BigDecimal.ZERO) != 0) {
                            lines.add(new SheetLine("INVOICE ACCOUNT", amount));
                        }
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

    private static List<BankTransactionLine> loadBankTransactions(Connection conn, LocalDate from, LocalDate to,
                                                                  Integer locationId) throws SQLException {
        List<BankTransactionLine> lines = new ArrayList<>();
        String sql = """
                SELECT transaction_name, transaction_direction, amount
                FROM bank_transactions
                WHERE (? IS NULL OR location_id = ?)
                  AND transaction_date BETWEEN ? AND ?
                ORDER BY transaction_date ASC, bank_transaction_id ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setNullableInteger(ps, 1, locationId);
            setNullableInteger(ps, 2, locationId);
            ps.setDate(3, Date.valueOf(from));
            ps.setDate(4, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new BankTransactionLine(
                            rs.getString("transaction_name"),
                            rs.getString("transaction_direction"),
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
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
            Integer locationId = ServerRequestIdentity.locationId();
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
        String deviceLabel = "COALESCE(NULLIF(TRIM(d.device_name), ''), NULLIF(TRIM(s.device_name), ''), NULLIF(TRIM(s.device_id), ''), NULLIF(TRIM(s.receipt_device_id), ''), 'Unassigned Device')";
        String normalSalesSql = """
                SELECT
                """ + deviceLabel + """
                       AS device_label,
                       SUM(COALESCE(s.amount_paid, 0)) AS amount
                FROM sales s
                LEFT JOIN devices d ON d.device_id::text = s.device_id
                WHERE (? IS NULL OR s.location_id = ?)
                  AND (s.created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + cashDrawerFilterSql(cashDrawerSessionIds, "s.cash_drawer_session_id", "s.payment_method") + """
                GROUP BY
                """ + deviceLabel + """
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
        String deviceLabel = "COALESCE(NULLIF(TRIM(d.device_name), ''), NULLIF(TRIM(p.device_name), ''), NULLIF(TRIM(p.device_id), ''), 'Unassigned Device')";
        String orderSql = """
                SELECT
                """ + deviceLabel + """
                       AS device_label,
                       SUM(CASE
                            WHEN p.payment_action = 'PAYMENT' THEN COALESCE(p.payment_amount, 0)
                            WHEN p.payment_action IN ('REFUND', 'REVERSAL') THEN -COALESCE(p.payment_amount, 0)
                            ELSE 0
                       END) AS amount
                FROM custom_order_payments p
                LEFT JOIN devices d ON d.device_id::text = p.device_id
                WHERE (? IS NULL OR EXISTS (
                    SELECT 1 FROM custom_orders co
                    WHERE co.custom_order_id = p.custom_order_id
                      AND co.location_id = ?
                ))
                  AND (p.created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + cashDrawerFilterSql(cashDrawerSessionIds, "p.cash_drawer_session_id", "p.payment_method") + """
                GROUP BY
                """ + deviceLabel + """
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

    private static List<SheetLine> loadDevicePayments(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                                      Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String deviceLabel = "COALESCE(NULLIF(TRIM(d.device_name), ''), NULLIF(TRIM(t.device_name), ''), NULLIF(TRIM(t.device_id), ''), 'Unassigned Device')";
        String sql = """
                SELECT
                """ + deviceLabel + """
                       AS device_label,
                       SUM(ABS(COALESCE(t.amount, 0))) AS amount
                FROM customer_account_transactions t
                LEFT JOIN devices d ON d.device_id::text = t.device_id
                WHERE COALESCE(t.transaction_type, '') = 'PAYMENT'
                  AND (? IS NULL OR t.location_id = ?)
                  AND (t.created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + sessionFilterSql(cashDrawerSessionIds, "t.cash_drawer_session_id") + """
                GROUP BY
                """ + deviceLabel + """
                ORDER BY device_label
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(rs.getString("device_label"), defaultZero(rs.getBigDecimal("amount"))));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(new SheetLine("No device payments logged", BigDecimal.ZERO));
        }
        return lines;
    }

    private static List<SheetLine> loadAccountPayments(Connection conn, LocalDate from, LocalDate to, String storeZoneId,
                                                       Integer locationId, List<Long> cashDrawerSessionIds) throws SQLException {
        List<SheetLine> lines = new ArrayList<>();
        String sql = """
                SELECT t.cash_drawer_session_id,
                       COALESCE(NULLIF(TRIM(t.payment_method), ''), 'UNKNOWN') AS payment_method,
                       COALESCE(NULLIF(TRIM(t.cash_drawer_name), ''), NULLIF(TRIM(cds.drawer_name), ''), 'No Drawer') AS drawer_label,
                       MIN(t.created_at) AS first_paid_at,
                       MAX(t.created_at) AS last_paid_at,
                       COUNT(*) AS payment_count,
                       SUM(ABS(COALESCE(t.amount, 0))) AS amount
                FROM customer_account_transactions t
                LEFT JOIN cash_drawer_sessions cds ON cds.cash_drawer_session_id = t.cash_drawer_session_id
                WHERE COALESCE(t.transaction_type, '') = 'PAYMENT'
                  AND (? IS NULL OR t.location_id = ?)
                  AND (t.created_at AT TIME ZONE ?)::date BETWEEN ? AND ?
                """ + sessionFilterSql(cashDrawerSessionIds, "t.cash_drawer_session_id") + """
                GROUP BY t.cash_drawer_session_id,
                         COALESCE(NULLIF(TRIM(t.payment_method), ''), 'UNKNOWN'),
                         COALESCE(NULLIF(TRIM(t.cash_drawer_name), ''), NULLIF(TRIM(cds.drawer_name), ''), 'No Drawer')
                ORDER BY MIN(t.created_at) ASC, drawer_label ASC, payment_method ASC
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            setNullableInteger(ps, index++, locationId);
            setNullableInteger(ps, index++, locationId);
            ps.setString(index++, storeZoneId);
            ps.setDate(index++, Date.valueOf(from));
            ps.setDate(index++, Date.valueOf(to));
            bindSessionIds(ps, index, cashDrawerSessionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new SheetLine(accountPaymentLabel(rs, storeZoneId), defaultZero(rs.getBigDecimal("amount"))));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(new SheetLine("No account payments collected", BigDecimal.ZERO));
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

    private static String formatOrderIncomeLabel(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "ORDER UNKNOWN";
        }
        return switch (paymentMethod.trim().toUpperCase()) {
            case "CASH" -> "ORDER CASH";
            case "CARD" -> "ORDER CARD";
            case "MMG" -> "ORDER MMG";
            case "CHEQUE" -> "ORDER CHEQUE";
            case "ACCOUNT" -> "ORDER ACCOUNT";
            default -> "ORDER " + paymentMethod.trim().toUpperCase();
        };
    }

    private static String formatInvoiceIncomeLabel(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "INVOICE UNKNOWN";
        }
        return switch (paymentMethod.trim().toUpperCase()) {
            case "CASH" -> "INVOICE CASH";
            case "CARD" -> "INVOICE CARD";
            case "MMG" -> "INVOICE MMG";
            case "CHEQUE" -> "INVOICE CHEQUE";
            case "ACCOUNT" -> "INVOICE ACCOUNT";
            default -> "INVOICE " + paymentMethod.trim().toUpperCase();
        };
    }

    private static String accountPaymentLabel(ResultSet rs, String storeZoneId) throws SQLException {
        long sessionId = rs.getLong("cash_drawer_session_id");
        boolean hasSession = !rs.wasNull();
        String drawerLabel = defaultText(rs.getString("drawer_label"));
        LocalDateTime firstPaidAt = toStoreTime(rs.getTimestamp("first_paid_at"), storeZoneId);
        LocalDateTime lastPaidAt = toStoreTime(rs.getTimestamp("last_paid_at"), storeZoneId);
        String timeLabel = firstPaidAt == null
                ? "Unknown time"
                : ACCOUNT_PAYMENT_TIME_FORMAT.format(firstPaidAt);
        if (firstPaidAt != null && lastPaidAt != null && !firstPaidAt.equals(lastPaidAt)) {
            timeLabel += " - " + ACCOUNT_PAYMENT_TIME_FORMAT.format(lastPaidAt);
        }
        int paymentCount = rs.getInt("payment_count");
        String drawLabel = hasSession ? "Draw #" + sessionId : "No draw";
        String paymentMethod = formatIncomeLabel(rs.getString("payment_method"));
        String paymentLabel = paymentCount == 1 ? "1 payment" : paymentCount + " payments";
        return paymentMethod + " / " + drawLabel + " / " + drawerLabel + " / " + timeLabel + " / " + paymentLabel;
    }

    private static LocalDateTime toStoreTime(Timestamp timestamp, String storeZoneId) {
        if (timestamp == null) {
            return null;
        }
        ZoneId zone = ZoneId.of(storeZoneId == null || storeZoneId.isBlank() ? "UTC" : storeZoneId);
        return timestamp.toInstant().atZone(zone).toLocalDateTime();
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

    private static String encodeBankTransactions(List<BankTransactionLine> lines) {
        StringBuilder encoded = new StringBuilder();
        for (BankTransactionLine line : lines) {
            if (!encoded.isEmpty()) {
                encoded.append('\n');
            }
            encoded.append(escapeLine(line.transaction()))
                    .append('\t')
                    .append(escapeLine(line.direction()))
                    .append('\t')
                    .append(defaultZero(line.amount()).toPlainString());
        }
        return encoded.toString();
    }

    private static List<BankTransactionLine> decodeBankTransactions(String encoded) {
        List<BankTransactionLine> lines = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return lines;
        }
        for (String row : encoded.split("\\n")) {
            String[] parts = row.split("\\t", 3);
            if (parts.length != 3) {
                continue;
            }
            try {
                lines.add(new BankTransactionLine(unescapeLine(parts[0]), unescapeLine(parts[1]), new BigDecimal(parts[2])));
            } catch (NumberFormatException ignored) {
                lines.add(new BankTransactionLine(unescapeLine(parts[0]), unescapeLine(parts[1]), BigDecimal.ZERO));
            }
        }
        return lines;
    }

    private static String encodeCheques(List<ChequeDepositOption> cheques) {
        StringBuilder encoded = new StringBuilder();
        for (ChequeDepositOption cheque : cheques) {
            if (!encoded.isEmpty()) {
                encoded.append('\n');
            }
            encoded.append(escapeLine(cheque.sourceType()))
                    .append('\t')
                    .append(escapeLine(cheque.sourceId()))
                    .append('\t')
                    .append(cheque.chequeAt() == null ? "" : cheque.chequeAt())
                    .append('\t')
                    .append(escapeLine(cheque.sourceLabel()))
                    .append('\t')
                    .append(escapeLine(cheque.payer()))
                    .append('\t')
                    .append(escapeLine(cheque.reference()))
                    .append('\t')
                    .append(defaultZero(cheque.amount()).toPlainString());
        }
        return encoded.toString();
    }

    private static List<ChequeDepositOption> decodeCheques(String encoded) {
        List<ChequeDepositOption> cheques = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return cheques;
        }
        for (String row : encoded.split("\\n")) {
            String[] parts = row.split("\\t", -1);
            if (parts.length != 7) {
                continue;
            }
            try {
                cheques.add(new ChequeDepositOption(
                        unescapeLine(parts[0]),
                        unescapeLine(parts[1]),
                        parts[2].isBlank() ? null : LocalDateTime.parse(parts[2]),
                        unescapeLine(parts[3]),
                        unescapeLine(parts[4]),
                        unescapeLine(parts[5]),
                        new BigDecimal(parts[6])
                ));
            } catch (Exception ignored) {
                cheques.add(new ChequeDepositOption(
                        unescapeLine(parts[0]),
                        unescapeLine(parts[1]),
                        null,
                        unescapeLine(parts[3]),
                        unescapeLine(parts[4]),
                        unescapeLine(parts[5]),
                        BigDecimal.ZERO
                ));
            }
        }
        return cheques;
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

    public record ExpenseOption(long expenseId, LocalDate expenseDate, String category, String payee,
                                String description, BigDecimal amount, String status) {
        @Override
        public String toString() {
            String name = payee == null || payee.isBlank()
                    ? (description == null || description.isBlank() ? category : description)
                    : payee;
            return expenseDate + " - " + name + " - " + defaultZero(amount).toPlainString();
        }
    }

    public record PayableOption(long expenseId, LocalDate expenseDate, String category, String payee,
                                String description, BigDecimal amount) {
        @Override
        public String toString() {
            String name = payee == null || payee.isBlank()
                    ? (description == null || description.isBlank() ? category : description)
                    : payee;
            return expenseDate + " - " + name + " - " + defaultZero(amount).toPlainString();
        }
    }

    public record ChequeDepositOption(String sourceType, String sourceId, LocalDateTime chequeAt,
                                      String sourceLabel, String payer, String reference, BigDecimal amount) {
        @Override
        public String toString() {
            String name = payer == null || payer.isBlank() ? sourceLabel : sourceLabel + " - " + payer;
            String ref = reference == null || reference.isBlank() ? "" : " / " + reference;
            return (chequeAt == null ? "" : chequeAt.toLocalDate() + " - ")
                    + name
                    + ref
                    + " - "
                    + defaultZero(amount).toPlainString();
        }
    }

    public record SheetLine(String label, BigDecimal amount) {
    }

    public record BankTransactionLine(String transaction, String direction, BigDecimal amount) {
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
                               List<SheetLine> devicePayments,
                               List<SheetLine> accountPayments,
                               List<BankTransactionLine> bankTransactions,
                               List<ChequeDepositOption> pendingCheques,
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
