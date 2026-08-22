package services;

import com.google.gson.Gson;
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
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerBalanceSheetService {
    private static final long SCHEMA_LOCK_KEY = 7_340_210_001L;
    private static final DateTimeFormatter ACCOUNT_PAYMENT_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd h:mm a");
    private static final Gson GSON = LanJson.create();
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
        SchemaContractService.requireLocalReady(conn);
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
        SchemaContractService.requireLocalReady(conn);
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
            BigDecimal balanceBf = loadBalanceBfOverride(conn, from, locationId);
            if (balanceBf == null) {
                balanceBf = loadPreviousBalanceCf(conn, from, locationId);
            }
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

    public static void setBalanceBf(Connection conn, LocalDate periodStart, BigDecimal amount) throws SQLException {
        ensureSchema(conn);
        Integer locationId = ServerRequestIdentity.locationId();
        if (locationId == null) {
            throw new SQLException("A store location is required to set Balance B/F.");
        }
        if (periodStart == null || amount == null) {
            throw new SQLException("The period start and Balance B/F amount are required.");
        }
        if (amount.scale() > 2 || amount.precision() - amount.scale() > 10) {
            throw new SQLException("Balance B/F must fit within 10 whole digits and 2 decimal places.");
        }
        String sql = """
                INSERT INTO balance_sheet_bf_overrides (
                    location_id, period_start, amount, updated_by_user_id, updated_by_name
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (location_id, period_start) DO UPDATE SET
                    amount = EXCLUDED.amount,
                    updated_by_user_id = EXCLUDED.updated_by_user_id,
                    updated_by_name = EXCLUDED.updated_by_name,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setDate(2, Date.valueOf(periodStart));
            ps.setBigDecimal(3, amount);
            setNullableInteger(ps, 4, ServerRequestIdentity.userId());
            ps.setString(5, ServerRequestIdentity.userName());
            ps.executeUpdate();
        }
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

    public static void addOtherIncome(Connection conn, OtherIncomeEntry entry) throws SQLException {
        ensureSchema(conn);
        validateOtherIncome(entry);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO other_income_entries (
                    location_id, income_date, source_name, description, amount, payment_method,
                    payment_reference, created_by_user_id, created_by_name
                ) VALUES (?, ?, ?, ?, ?, 'CASH', ?, ?, ?)
                RETURNING other_income_id
                """)) {
            setNullableInteger(ps, 1, ServerRequestIdentity.locationId());
            ps.setDate(2, Date.valueOf(entry.incomeDate()));
            ps.setString(3, entry.sourceName().trim());
            ps.setString(4, blankToNull(entry.description()));
            ps.setBigDecimal(5, entry.amount());
            ps.setString(6, blankToNull(entry.paymentReference()));
            setNullableInteger(ps, 7, ServerRequestIdentity.userId());
            ps.setString(8, ServerRequestIdentity.userName());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SyncOutboxService.recordEvent(conn, "OTHER_INCOME_CREATED", Map.of(
                            "other_income_id", rs.getLong(1),
                            "location_id", ServerRequestIdentity.locationId() == null ? "" : ServerRequestIdentity.locationId(),
                            "income_date", entry.incomeDate(),
                            "source_name", entry.sourceName().trim(),
                            "amount", entry.amount(),
                            "payment_method", "CASH"));
                }
            }
        }
    }

    public static List<OtherIncomeOption> listDeletableOtherIncome(LocalDate from, LocalDate to) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT other_income_id, income_date, source_name, description, amount, payment_reference
                    FROM other_income_entries
                    WHERE (? IS NULL OR location_id = ?)
                      AND income_date BETWEEN ? AND ?
                    ORDER BY income_date DESC, other_income_id DESC
                    """)) {
                setNullableInteger(ps, 1, ServerRequestIdentity.locationId());
                setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
                ps.setDate(3, Date.valueOf(from));
                ps.setDate(4, Date.valueOf(to));
                List<OtherIncomeOption> rows = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new OtherIncomeOption(rs.getLong(1), rs.getDate(2).toLocalDate(),
                                rs.getString(3), rs.getString(4), defaultZero(rs.getBigDecimal(5)), rs.getString(6)));
                    }
                }
                return rows;
            }
        }
    }

    public static void deleteOtherIncome(Connection conn, long otherIncomeId, LocalDate from, LocalDate to) throws SQLException {
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                DELETE FROM other_income_entries
                WHERE other_income_id = ?
                  AND (? IS NULL OR location_id = ?)
                  AND income_date BETWEEN ? AND ?
                RETURNING other_income_id, income_date, source_name, amount
                """)) {
            ps.setLong(1, otherIncomeId);
            setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
            setNullableInteger(ps, 3, ServerRequestIdentity.locationId());
            ps.setDate(4, Date.valueOf(from));
            ps.setDate(5, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SyncOutboxService.recordEvent(conn, "OTHER_INCOME_DELETED", Map.of(
                            "other_income_id", rs.getLong(1),
                            "location_id", ServerRequestIdentity.locationId() == null ? "" : ServerRequestIdentity.locationId(),
                            "income_date", rs.getDate(2).toLocalDate(),
                            "source_name", rs.getString(3),
                            "amount", defaultZero(rs.getBigDecimal(4))));
                    ReferenceDataSyncService.recordTombstone(conn, "other_income_entries",
                            Map.of("other_income_id", rs.getLong(1)));
                    return;
                }
            }
        }
        throw new SQLException("Only manual Other income from the current balance sheet can be deleted.");
    }

    private static void validateOtherIncome(OtherIncomeEntry entry) throws SQLException {
        if (entry == null || entry.incomeDate() == null) throw new SQLException("Income date is required.");
        if (entry.sourceName() == null || entry.sourceName().isBlank()) throw new SQLException("Income source is required.");
        if (entry.sourceName().trim().length() > 200) throw new SQLException("Income source cannot exceed 200 characters.");
        if (entry.description() != null && entry.description().length() > 3000) throw new SQLException("Description cannot exceed 3000 characters.");
        if (entry.paymentReference() != null && entry.paymentReference().length() > 500) throw new SQLException("Reference cannot exceed 500 characters.");
        if (entry.amount() == null || entry.amount().signum() <= 0) throw new SQLException("Income amount must be greater than zero.");
        if (entry.amount().remainder(BigDecimal.ONE).signum() != 0) {
            throw new SQLException("Income amount must be entered in whole GYD; cents are not used.");
        }
        if (entry.amount().precision() - entry.amount().scale() > 10) {
            throw new SQLException("Income amount cannot exceed 10 whole digits.");
        }
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
                           balance_cf, revision_no, last_edited_at, last_edited_by_name,
                           (submitted_at + INTERVAL '48 hours') AS edit_expires_at,
                           (CURRENT_TIMESTAMP < submitted_at + INTERVAL '48 hours'
                            AND NOT EXISTS (SELECT 1 FROM balance_sheet_submissions newer
                                            WHERE newer.location_id = balance_sheet_submissions.location_id
                                              AND (newer.submitted_at > balance_sheet_submissions.submitted_at
                                                   OR (newer.submitted_at = balance_sheet_submissions.submitted_at
                                                       AND newer.balance_sheet_submission_id > balance_sheet_submissions.balance_sheet_submission_id)))) AS latest_within_window
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
                                defaultZero(rs.getBigDecimal("balance_cf")), rs.getInt("revision_no"),
                                rs.getTimestamp("last_edited_at")==null?null:rs.getTimestamp("last_edited_at").toLocalDateTime(),
                                rs.getString("last_edited_by_name"),rs.getTimestamp("edit_expires_at").toLocalDateTime(),
                                rs.getBoolean("latest_within_window")
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
                    if (rs.next()) return snapshot(rs);
                }
            }
        }
        throw new SQLException("Saved balance sheet was not found.");
    }

    private static BalanceSheet snapshot(ResultSet rs) throws SQLException {
        return new BalanceSheet(rs.getLong("balance_sheet_submission_id"), rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(), rs.getTimestamp("submitted_at").toLocalDateTime(),
                rs.getString("submitted_by_name"), rs.getString("notes"), decodeLines(rs.getString("income_lines")),
                decodeLines(rs.getString("receivable_lines")), decodeLines(rs.getString("expense_lines")),
                decodeLines(rs.getString("payable_lines")), decodeLines(rs.getString("drawer_cash_lines")),
                decodeLines(rs.getString("device_sales_lines")), decodeLines(rs.getString("device_order_lines")),
                decodeLines(rs.getString("device_payment_lines")), decodeLines(rs.getString("account_payment_lines")),
                decodeBankTransactions(rs.getString("bank_transaction_lines")), decodeCheques(rs.getString("pending_cheque_lines")),
                decodeLines(rs.getString("drawer_check_lines")), defaultZero(rs.getBigDecimal("cash_in_hand")),
                defaultZero(rs.getBigDecimal("balance_bf")), defaultZero(rs.getBigDecimal("total_income")),
                defaultZero(rs.getBigDecimal("total_receivables")), defaultZero(rs.getBigDecimal("total_expenses")),
                defaultZero(rs.getBigDecimal("total_payables")), defaultZero(rs.getBigDecimal("balance_cf")));
    }

    private static LockedSubmission findSubmission(Connection conn, long id, boolean lock) throws SQLException {
        String sql = "SELECT * FROM balance_sheet_submissions WHERE balance_sheet_submission_id=? AND location_id=?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id); setNullableInteger(ps, 2, ServerRequestIdentity.locationId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new LockedSubmission(id, rs.getInt("location_id"), rs.getDate("period_start").toLocalDate(),
                        rs.getDate("period_end").toLocalDate(), rs.getTimestamp("submitted_at").toLocalDateTime(),
                        rs.getInt("revision_no"), rs.getString("notes"), snapshot(rs));
            }
        }
    }

    private static EditEligibility eligibility(Connection conn, LockedSubmission value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT submitted_at + INTERVAL '48 hours' AS expires_at,
                       CURRENT_TIMESTAMP < submitted_at + INTERVAL '48 hours' AS within_window,
                       NOT EXISTS (SELECT 1 FROM balance_sheet_submissions newer
                                   WHERE newer.location_id=current_sheet.location_id
                                     AND (newer.submitted_at>current_sheet.submitted_at OR
                                          (newer.submitted_at=current_sheet.submitted_at AND newer.balance_sheet_submission_id>current_sheet.balance_sheet_submission_id))) AS newest
                FROM balance_sheet_submissions current_sheet WHERE balance_sheet_submission_id=? AND location_id=?
                """)) {
            ps.setLong(1,value.submissionId());ps.setInt(2,value.locationId());
            try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Saved balance sheet was not found for this store.");
                LocalDateTime expiresAt=rs.getTimestamp("expires_at").toLocalDateTime();boolean newest=rs.getBoolean("newest"),within=rs.getBoolean("within_window");
                String reason=!newest?"A newer Balance Sheet has already been submitted for this store.":!within?"The 48-hour Balance Sheet edit window has expired.":null;
                return new EditEligibility(reason==null,expiresAt,value.revisionNo(),reason);}
        }
    }

    private static List<EditableExpense> editableExpenses(Connection conn, LockedSubmission s) throws SQLException {
        List<EditableExpense> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT expense_id,expense_date,category,payee,description,amount,payment_method,payment_reference,status
                FROM expenses WHERE location_id=? AND expense_date BETWEEN ? AND ? AND source_type IS NULL ORDER BY expense_date,expense_id
                """)) {
            ps.setInt(1,s.locationId()); ps.setDate(2,Date.valueOf(s.periodStart())); ps.setDate(3,Date.valueOf(s.periodEnd()));
            try(ResultSet rs=ps.executeQuery()){while(rs.next()) rows.add(new EditableExpense(rs.getLong(1),rs.getDate(2).toLocalDate(),rs.getString(3),rs.getString(4),rs.getString(5),defaultZero(rs.getBigDecimal(6)),rs.getString(7),rs.getString(8),rs.getString(9)));}
        }
        return rows;
    }

    private static List<EditableOtherIncome> editableIncome(Connection conn, LockedSubmission s) throws SQLException {
        List<EditableOtherIncome> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT other_income_id,income_date,source_name,description,amount,payment_reference
                FROM other_income_entries WHERE location_id=? AND income_date BETWEEN ? AND ? ORDER BY income_date,other_income_id
                """)) {
            ps.setInt(1,s.locationId()); ps.setDate(2,Date.valueOf(s.periodStart())); ps.setDate(3,Date.valueOf(s.periodEnd()));
            try(ResultSet rs=ps.executeQuery()){while(rs.next()) rows.add(new EditableOtherIncome(rs.getLong(1),rs.getDate(2).toLocalDate(),rs.getString(3),rs.getString(4),defaultZero(rs.getBigDecimal(5)),rs.getString(6)));}
        }
        return rows;
    }

    private static List<RevisionAudit> auditHistory(Connection conn,long id)throws SQLException{
        List<RevisionAudit> rows=new ArrayList<>();try(PreparedStatement ps=conn.prepareStatement("SELECT revision_no,changed_at,changed_by_name,device_name,reason,change_summary FROM balance_sheet_submission_revisions WHERE balance_sheet_submission_id=? ORDER BY revision_no DESC")){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(new RevisionAudit(rs.getInt(1),rs.getTimestamp(2).toLocalDateTime(),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)));}}return rows;
    }

    private static String describeChanges(AuditSnapshot before,AuditSnapshot after){List<String> changes=new ArrayList<>();if(!java.util.Objects.equals(before.sheet().notes(),after.sheet().notes()))changes.add("Notes: '"+defaultText(before.sheet().notes())+"' -> '"+defaultText(after.sheet().notes())+"'");appendRowChanges(changes,"Expense",before.expenses(),after.expenses(),EditableExpense::expenseId,e->e.expenseDate()+" / "+e.category()+" / "+defaultZero(e.amount())+" / "+e.status());appendRowChanges(changes,"Other Income",before.otherIncome(),after.otherIncome(),EditableOtherIncome::otherIncomeId,i->i.incomeDate()+" / "+i.sourceName()+" / "+defaultZero(i.amount()));changes.add("Balance C/F: "+before.sheet().balanceCf()+" -> "+after.sheet().balanceCf());String value=String.join("; ",changes);return value.length()>5000?value.substring(0,4997)+"...":value;}
    private static <T>void appendRowChanges(List<String>out,String type,List<T>before,List<T>after,java.util.function.Function<T,Long>id,java.util.function.Function<T,String>text){Map<Long,T>old=new LinkedHashMap<>(),now=new LinkedHashMap<>();for(T row:before)old.put(id.apply(row),row);for(T row:after)now.put(id.apply(row),row);for(var entry:old.entrySet()){T current=now.get(entry.getKey());if(current==null)out.add(type+" removed: "+text.apply(entry.getValue()));else if(!GSON.toJson(entry.getValue()).equals(GSON.toJson(current)))out.add(type+" changed: "+text.apply(entry.getValue())+" -> "+text.apply(current));}for(var entry:now.entrySet())if(!old.containsKey(entry.getKey()))out.add(type+" added: "+text.apply(entry.getValue()));}

    private static void validateEditRows(LockedSubmission s,List<EditableExpense> expenses,List<EditableOtherIncome> incomes)throws SQLException{
        if(expenses==null||incomes==null)throw new SQLException("Expense and Other Income rows are required.");
        for(EditableExpense e:expenses){if(e.expenseDate()==null||e.expenseDate().isBefore(s.periodStart())||e.expenseDate().isAfter(s.periodEnd()))throw new SQLException("Every expense date must be inside the submitted period.");if(e.category()==null||e.category().isBlank()||e.amount()==null||e.amount().signum()<0)throw new SQLException("Every expense needs a category and a valid amount.");if(!"PAID".equalsIgnoreCase(e.status())&&!"UNPAID".equalsIgnoreCase(e.status()))throw new SQLException("Expense status must be PAID or UNPAID.");}
        for(EditableOtherIncome i:incomes){if(i.incomeDate()==null||i.incomeDate().isBefore(s.periodStart())||i.incomeDate().isAfter(s.periodEnd()))throw new SQLException("Every Other Income date must be inside the submitted period.");if(i.sourceName()==null||i.sourceName().isBlank()||i.amount()==null||i.amount().signum()<=0||i.amount().stripTrailingZeros().scale()>0)throw new SQLException("Other Income requires a source and a positive whole-GYD amount.");}
    }

    private static void applyExpenses(Connection conn,LockedSubmission s,List<EditableExpense> rows)throws SQLException{
        List<Long> keep=new ArrayList<>();for(EditableExpense e:rows)if(e.expenseId()!=null)keep.add(e.expenseId());
        try(PreparedStatement ps=conn.prepareStatement("DELETE FROM expenses WHERE location_id=? AND expense_date BETWEEN ? AND ? AND source_type IS NULL AND NOT (expense_id = ANY(?))")){ps.setInt(1,s.locationId());ps.setDate(2,Date.valueOf(s.periodStart()));ps.setDate(3,Date.valueOf(s.periodEnd()));ps.setArray(4,conn.createArrayOf("bigint",keep.toArray()));ps.executeUpdate();}
        for(EditableExpense e:rows){if(e.expenseId()==null){addManualExpense(conn,new ExpenseEntry(e.expenseDate(),e.category(),e.payee(),e.description(),e.amount(),e.paymentMethod(),e.paymentReference(),e.status()));}else try(PreparedStatement ps=conn.prepareStatement("UPDATE expenses SET expense_date=?,category=?,payee=?,description=?,amount=?,payment_method=?,payment_reference=?,status=?,updated_at=CURRENT_TIMESTAMP WHERE expense_id=? AND location_id=? AND source_type IS NULL")){ps.setDate(1,Date.valueOf(e.expenseDate()));ps.setString(2,e.category().trim());ps.setString(3,blankToNull(e.payee()));ps.setString(4,blankToNull(e.description()));ps.setBigDecimal(5,e.amount());ps.setString(6,blankToNull(e.paymentMethod()));ps.setString(7,blankToNull(e.paymentReference()));ps.setString(8,e.status().toUpperCase());ps.setLong(9,e.expenseId());ps.setInt(10,s.locationId());if(ps.executeUpdate()!=1)throw new SQLException("An expense row is no longer available for editing.");}}
    }

    private static void applyOtherIncome(Connection conn,LockedSubmission s,List<EditableOtherIncome> rows)throws SQLException{
        List<Long> keep=new ArrayList<>();for(EditableOtherIncome i:rows)if(i.otherIncomeId()!=null)keep.add(i.otherIncomeId());
        try(PreparedStatement ps=conn.prepareStatement("DELETE FROM other_income_entries WHERE location_id=? AND income_date BETWEEN ? AND ? AND NOT (other_income_id = ANY(?))")){ps.setInt(1,s.locationId());ps.setDate(2,Date.valueOf(s.periodStart()));ps.setDate(3,Date.valueOf(s.periodEnd()));ps.setArray(4,conn.createArrayOf("bigint",keep.toArray()));ps.executeUpdate();}
        for(EditableOtherIncome i:rows){if(i.otherIncomeId()==null){addOtherIncome(conn,new OtherIncomeEntry(i.incomeDate(),i.sourceName(),i.description(),i.amount(),i.paymentReference()));}else try(PreparedStatement ps=conn.prepareStatement("UPDATE other_income_entries SET income_date=?,source_name=?,description=?,amount=?,payment_reference=?,updated_at=CURRENT_TIMESTAMP WHERE other_income_id=? AND location_id=?")){ps.setDate(1,Date.valueOf(i.incomeDate()));ps.setString(2,i.sourceName().trim());ps.setString(3,blankToNull(i.description()));ps.setBigDecimal(4,i.amount());ps.setString(5,blankToNull(i.paymentReference()));ps.setLong(6,i.otherIncomeId());ps.setInt(7,s.locationId());if(ps.executeUpdate()!=1)throw new SQLException("An Other Income row is no longer available for editing.");}}
    }

    static List<SheetLine> replaceOtherCash(List<SheetLine> original,BigDecimal amount){List<SheetLine> out=new ArrayList<>();for(SheetLine line:original)if(!"OTHER CASH".equalsIgnoreCase(line.label()))out.add(line);if(amount.signum()!=0)out.add(new SheetLine("OTHER CASH",amount));return out;}
    private static BigDecimal totalOtherIncome(List<EditableOtherIncome> rows){BigDecimal value=BigDecimal.ZERO;for(EditableOtherIncome row:rows)value=value.add(defaultZero(row.amount()));return value;}
    static List<SheetLine> replaceManualExpenseLines(List<SheetLine> snapshot,List<EditableExpense> before,List<EditableExpense> after,String status,String emptyLabel){Map<String,BigDecimal> amounts=new LinkedHashMap<>();for(SheetLine line:snapshot)if(!emptyLabel.equals(line.label()))amounts.merge(line.label(),defaultZero(line.amount()),BigDecimal::add);for(var entry:manualExpenseTotals(before,status).entrySet())amounts.merge(entry.getKey(),entry.getValue().negate(),BigDecimal::add);for(var entry:manualExpenseTotals(after,status).entrySet())amounts.merge(entry.getKey(),entry.getValue(),BigDecimal::add);List<SheetLine> out=new ArrayList<>();for(var entry:amounts.entrySet())if(entry.getValue().signum()!=0)out.add(new SheetLine(entry.getKey(),entry.getValue()));if(out.isEmpty())out.add(new SheetLine(emptyLabel,BigDecimal.ZERO));return out;}
    private static Map<String,BigDecimal>manualExpenseTotals(List<EditableExpense>rows,String status){Map<String,BigDecimal>totals=new LinkedHashMap<>();for(EditableExpense row:rows)if(status.equalsIgnoreCase(row.status())){String detail=row.payee()==null||row.payee().isBlank()?(row.description()==null||row.description().isBlank()?row.category():row.description()):row.payee();totals.merge(row.category()+" - "+detail,defaultZero(row.amount()),BigDecimal::add);}return totals;}

    public static EditContext loadEditContext(Connection conn, long submissionId) throws SQLException {
        ensureSchema(conn);
        LockedSubmission locked = findSubmission(conn, submissionId, false);
        if (locked == null) throw new SQLException("Saved balance sheet was not found for this store.");
        EditEligibility eligibility = eligibility(conn, locked);
        return new EditContext(submissionId, locked.periodStart(), locked.periodEnd(), locked.notes(),
                eligibility, editableExpenses(conn, locked), editableIncome(conn, locked), auditHistory(conn, submissionId));
    }

    public static List<RevisionAudit> loadRevisionHistory(Connection conn,long submissionId)throws SQLException{
        ensureSchema(conn);if(findSubmission(conn,submissionId,false)==null)throw new SQLException("Saved balance sheet was not found for this store.");return auditHistory(conn,submissionId);
    }

    public static EditResult reviseSubmission(Connection conn, EditRequest request) throws SQLException {
        if (request == null || request.reason() == null || request.reason().trim().isEmpty())
            throw new SQLException("A reason for the Balance Sheet change is required.");
        if(request.reason().trim().length()>1000)throw new SQLException("The Balance Sheet change reason cannot exceed 1,000 characters.");
        if(request.notes()!=null&&request.notes().length()>5000)throw new SQLException("Balance Sheet notes cannot exceed 5,000 characters.");
        LockedSubmission locked = findSubmission(conn, request.submissionId(), true);
        if (locked == null) throw new SQLException("Saved balance sheet was not found for this store.");
        EditEligibility eligibility = eligibility(conn, locked);
        if (!eligibility.editable()) throw new SQLException(eligibility.lockReason());
        if (request.expectedRevision() != locked.revisionNo())
            throw new SQLException("This Balance Sheet was changed by another user. Reopen it before saving.");
        validateEditRows(locked, request.expenses(), request.otherIncome());

        List<EditableExpense> beforeExpenses = editableExpenses(conn, locked);
        List<EditableOtherIncome> beforeIncome = editableIncome(conn, locked);
        BalanceSheet beforeSheet = locked.sheet();
        applyExpenses(conn, locked, request.expenses());
        applyOtherIncome(conn, locked, request.otherIncome());

        List<EditableExpense> afterExpenses=editableExpenses(conn,locked);
        List<EditableOtherIncome> afterIncome=editableIncome(conn,locked);
        List<SheetLine> income = replaceOtherCash(beforeSheet.income(),totalOtherIncome(afterIncome));
        List<SheetLine> expenses = replaceManualExpenseLines(beforeSheet.expenses(),beforeExpenses,afterExpenses,"PAID","No expenses");
        List<SheetLine> payables = replaceManualExpenseLines(beforeSheet.payables(),beforeExpenses,afterExpenses,"UNPAID","No payables");
        BigDecimal totalIncome = total(income), totalExpenses = total(expenses), totalPayables = total(payables);
        BigDecimal cf = beforeSheet.balanceBf().add(totalIncome).subtract(totalExpenses).subtract(totalPayables);
        int nextRevision = locked.revisionNo() + 1;
        String notes = blankToNull(request.notes());
        try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE balance_sheet_submissions SET income_lines=?, expense_lines=?, payable_lines=?,
                  total_income=?, total_expenses=?, total_payables=?, balance_cf=?, notes=?, revision_no=?,
                  last_edited_at=CURRENT_TIMESTAMP, last_edited_by_user_id=?, last_edited_by_name=?
                WHERE balance_sheet_submission_id=? AND revision_no=?
                """)) {
            ps.setString(1, encodeLines(income)); ps.setString(2, encodeLines(expenses)); ps.setString(3, encodeLines(payables));
            ps.setBigDecimal(4, totalIncome); ps.setBigDecimal(5, totalExpenses); ps.setBigDecimal(6, totalPayables);
            ps.setBigDecimal(7, cf); ps.setString(8, notes); ps.setInt(9, nextRevision);
            setNullableInteger(ps, 10, ServerRequestIdentity.userId()); ps.setString(11, ServerRequestIdentity.userName());
            ps.setLong(12, request.submissionId()); ps.setInt(13, locked.revisionNo());
            if (ps.executeUpdate() != 1) throw new SQLException("This Balance Sheet was changed by another user. Reopen it before saving.");
        }
        BalanceSheet afterSheet = new BalanceSheet(beforeSheet.submissionId(), beforeSheet.periodStart(), beforeSheet.periodEnd(),
                beforeSheet.submittedAt(), beforeSheet.submittedByName(), notes, income, beforeSheet.receivables(), expenses,
                payables, beforeSheet.drawerCash(), beforeSheet.deviceSales(), beforeSheet.deviceOrders(), beforeSheet.devicePayments(),
                beforeSheet.accountPayments(), beforeSheet.bankTransactions(), beforeSheet.pendingCheques(), beforeSheet.drawerChecks(),
                beforeSheet.cashInHand(), beforeSheet.balanceBf(), totalIncome, beforeSheet.totalReceivables(), totalExpenses,
                totalPayables, cf);
        AuditSnapshot before = new AuditSnapshot(beforeSheet, beforeExpenses, beforeIncome);
        AuditSnapshot after = new AuditSnapshot(afterSheet, afterExpenses, afterIncome);
        String changeSummary=describeChanges(before,after);
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO balance_sheet_submission_revisions
                  (balance_sheet_submission_id,location_id,revision_no,reason,change_summary,before_snapshot,after_snapshot,
                   changed_by_user_id,changed_by_name,device_id,device_name)
                VALUES (?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?)
                """)) {
            ps.setLong(1, request.submissionId()); ps.setInt(2, locked.locationId()); ps.setInt(3, nextRevision);
            ps.setString(4, request.reason().trim());ps.setString(5,changeSummary);ps.setString(6, GSON.toJson(before)); ps.setString(7, GSON.toJson(after));
            setNullableInteger(ps, 8, ServerRequestIdentity.userId()); ps.setString(9, ServerRequestIdentity.userName());
            ps.setString(10, ServerRequestIdentity.deviceId()); ps.setString(11, ServerRequestIdentity.deviceName()); ps.executeUpdate();
        }
        SyncOutboxService.recordEvent(conn, "BALANCE_SHEET_REVISED", Map.of("balance_sheet_submission_id", request.submissionId(),
                "location_id", locked.locationId(), "revision_no", nextRevision, "reason", request.reason().trim()));
        return new EditResult(request.submissionId(), nextRevision, afterSheet);
    }

    private static void syncPaidPayrollExpenses(Connection conn, String storeZoneId, Integer fallbackLocationId) throws SQLException {
        if (!tableExists(conn, "payroll_payments")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
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
        if (tableExists(conn, "other_income_entries")) {
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT SUM(COALESCE(amount, 0)) AS amount
                    FROM other_income_entries
                    WHERE (? IS NULL OR location_id = ?)
                      AND income_date BETWEEN ? AND ?
                      AND payment_method = 'CASH'
                    """)) {
                setNullableInteger(ps, 1, locationId);
                setNullableInteger(ps, 2, locationId);
                ps.setDate(3, Date.valueOf(from));
                ps.setDate(4, Date.valueOf(to));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BigDecimal amount = defaultZero(rs.getBigDecimal("amount"));
                        if (amount.signum() != 0) lines.add(new SheetLine("OTHER CASH", amount));
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
            Map<Long,DrawSessionRange> ranges = new LinkedHashMap<>();
            LocalDate expandedFrom=from, expandedTo=to;
            boolean changed;
            do {
                changed=false;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, storeZoneId);
                    ps.setString(2, storeZoneId);
                    setNullableInteger(ps, 3, locationId);
                    setNullableInteger(ps, 4, locationId);
                    ps.setString(5, storeZoneId);
                    ps.setDate(6, Date.valueOf(expandedTo));
                    ps.setString(7, storeZoneId);
                    ps.setDate(8, Date.valueOf(expandedFrom));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            DrawSessionRange range=new DrawSessionRange(rs.getLong("cash_drawer_session_id"),
                                    rs.getDate("opened_date").toLocalDate(),rs.getDate("closed_date").toLocalDate(),
                                    rs.getString("device_label")+" / "+rs.getString("drawer_label"),rs.getString("status"));
                            ranges.putIfAbsent(range.sessionId(),range);
                            if(range.openedDate().isBefore(expandedFrom)){expandedFrom=range.openedDate();changed=true;}
                            if(range.closedDate().isAfter(expandedTo)){expandedTo=range.closedDate();changed=true;}
                        }
                    }
                }
            } while(changed);
            return new ArrayList<>(ranges.values());
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
            lines.add(new SheetLine("Match a drawer session to run cash drawer checks", BigDecimal.ZERO));
            return lines;
        }

        String salesSql = """
                SELECT sale_id, receipt_number, created_at, cash_drawer_session_id,
                       COALESCE(NULLIF(TRIM(receipt_device_id), ''), NULLIF(TRIM(device_name), ''), NULLIF(TRIM(device_id), ''), 'Unassigned Device') AS device_label,
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
                            "Cash sale " + rs.getString("receipt_number") + " at " + rs.getTimestamp("created_at")
                                    + " - " + rs.getString("device_label") + " / " + rs.getString("drawer_label") + " - "
                                    + sessionMismatchLabel(rs, "cash_drawer_session_id"),
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
        }

        String orderSql = """
                SELECT p.custom_order_payment_id, co.order_number, p.created_at, p.cash_drawer_session_id,
                       COALESCE(NULLIF(TRIM(p.device_name), ''), NULLIF(TRIM(p.device_id), ''), 'Unassigned Device') AS device_label,
                       COALESCE(NULLIF(TRIM(cash_drawer_name), ''), 'No Drawer') AS drawer_label,
                       CASE
                            WHEN payment_action = 'PAYMENT' THEN COALESCE(payment_amount, 0)
                            WHEN payment_action IN ('REFUND', 'REVERSAL') THEN -COALESCE(payment_amount, 0)
                            ELSE 0
                       END AS amount
                FROM custom_order_payments p
                JOIN custom_orders co ON co.custom_order_id=p.custom_order_id
                WHERE (? IS NULL OR co.location_id = ?)
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
                            "Cash order " + rs.getString("order_number") + " at " + rs.getTimestamp("created_at")
                                    + " - " + rs.getString("device_label") + " / " + rs.getString("drawer_label") + " - "
                                    + sessionMismatchLabel(rs, "cash_drawer_session_id"),
                            defaultZero(rs.getBigDecimal("amount"))
                    ));
                }
            }
        }

        if (lines.isEmpty()) {
            lines.add(new SheetLine("All cash sales and orders match the selected drawer sessions", BigDecimal.ZERO));
        }
        return lines;
    }

    private static String sessionMismatchLabel(ResultSet rs,String column)throws SQLException{
        long id=rs.getLong(column);return rs.wasNull()?"session unassigned":"unselected session "+id;
    }

    public static LegacyCashRecovery recoverLegacyCash(Connection conn,String sourceType,long sourceId,String reason)throws SQLException{
        String why=reason==null?"":reason.trim();
        if(why.length()<10)throw new SQLException("An audit reason of at least 10 characters is required.");
        LegacyCashSource source=LegacyCashSource.from(sourceType);
        String deviceId=ServerRequestIdentity.deviceId();
        Integer locationId=ServerRequestIdentity.locationId(),userId=ServerRequestIdentity.userId();
        if(deviceId==null||locationId==null||userId==null)throw new SQLException("Authenticated register identity is required.");
        long sessionId,drawerId;String drawerName;
        try(PreparedStatement ps=conn.prepareStatement("SELECT cash_drawer_session_id,cash_drawer_id,drawer_name FROM cash_drawer_sessions WHERE device_id=?::uuid AND location_id=? AND status='OPEN' FOR UPDATE")){
            ps.setString(1,deviceId);ps.setInt(2,locationId);try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw new SQLException("This register does not have an open cash drawer session.");
                sessionId=rs.getLong(1);drawerId=rs.getLong(2);drawerName=rs.getString(3);
                if(rs.next())throw new SQLException("This register has more than one open drawer session.");
            }
        }
        Long originalDrawerId;String paymentMethod,originalDrawerName;
        String select="SELECT t.payment_method,t.cash_drawer_id,t.cash_drawer_name,t.cash_drawer_session_id,"+source.locationExpression
                +" FROM "+source.fromSql+" WHERE t."+source.idColumn+"=? FOR UPDATE OF t";
        try(PreparedStatement ps=conn.prepareStatement(select)){ps.setLong(1,sourceId);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next())throw new SQLException("The legacy cash row was not found.");
            paymentMethod=rs.getString(1);long value=rs.getLong(2);originalDrawerId=rs.wasNull()?null:value;originalDrawerName=rs.getString(3);
            rs.getLong(4);if(!rs.wasNull())throw new SQLException("This row already belongs to a drawer session and cannot be reassigned.");
            if(rs.getInt(5)!=locationId)throw new SQLException("The row belongs to a different store location.");
        }}
        if(!"CASH".equalsIgnoreCase(paymentMethod))throw new SQLException("Only cash rows can be recovered to a drawer session.");
        if(originalDrawerId!=null&&originalDrawerId.longValue()!=drawerId)throw new SQLException("The row belongs to a different cash drawer.");
        String details="source="+source.apiName+", primary_id="+sourceId+", before_session=NULL, after_session="+sessionId
                +", original_drawer_id="+originalDrawerId+", original_drawer_name="+originalDrawerName+", target_drawer_id="+drawerId
                +", target_drawer_name="+drawerName+", reason="+why;
        try(PreparedStatement ps=conn.prepareStatement("INSERT INTO security_audit_events(event_type,device_id,actor_user_id,details) VALUES('LEGACY_CASH_SESSION_RECOVERED',?::uuid,?,?)")){
            ps.setString(1,deviceId);ps.setInt(2,userId);ps.setString(3,details);ps.executeUpdate();
        }
        try(PreparedStatement ps=conn.prepareStatement("UPDATE "+source.table+" SET cash_drawer_session_id=? WHERE "+source.idColumn+"=? AND cash_drawer_session_id IS NULL")){
            ps.setLong(1,sessionId);ps.setLong(2,sourceId);if(ps.executeUpdate()!=1)throw new SQLException("The row changed before recovery; no update was made.");
        }
        SyncOutboxService.recordEvent(conn,"LEGACY_CASH_SESSION_RECOVERED",Map.of("source_type",source.apiName,"source_id",sourceId,"cash_drawer_session_id",sessionId,"reason",why));
        return new LegacyCashRecovery(source.apiName,sourceId,null,sessionId,drawerId,drawerName);
    }

    private enum LegacyCashSource{
        SALES("SALES","sales","sale_id","sales t","t.location_id"),
        CUSTOM_ORDER_PAYMENT("CUSTOM_ORDER_PAYMENT","custom_order_payments","custom_order_payment_id","custom_order_payments t JOIN custom_orders owner ON owner.custom_order_id=t.custom_order_id","owner.location_id"),
        INVOICE_PAYMENT("INVOICE_PAYMENT","invoice_payments","invoice_payment_id","invoice_payments t","t.location_id"),
        CUSTOMER_ACCOUNT_TRANSACTION("CUSTOMER_ACCOUNT_TRANSACTION","customer_account_transactions","transaction_id","customer_account_transactions t","t.location_id");
        final String apiName,table,idColumn,fromSql,locationExpression;
        LegacyCashSource(String apiName,String table,String idColumn,String fromSql,String locationExpression){this.apiName=apiName;this.table=table;this.idColumn=idColumn;this.fromSql=fromSql;this.locationExpression=locationExpression;}
        static LegacyCashSource from(String value)throws SQLException{try{return valueOf(value==null?"":value.trim().toUpperCase(Locale.ROOT));}catch(Exception ex){throw new SQLException("Unsupported legacy cash source.");}}
    }

    public record LegacyCashRecovery(String sourceType,long sourceId,Long beforeSessionId,long afterSessionId,long drawerId,String drawerName){}

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

    private static BigDecimal loadBalanceBfOverride(Connection conn, LocalDate periodStart, Integer locationId) throws SQLException {
        if (locationId == null) {
            return null;
        }
        String sql = """
                SELECT amount
                FROM balance_sheet_bf_overrides
                WHERE location_id = ?
                  AND period_start = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setDate(2, Date.valueOf(periodStart));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("amount") : null;
            }
        }
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

    public record OtherIncomeEntry(LocalDate incomeDate, String sourceName, String description,
                                   BigDecimal amount, String paymentReference) {
    }

    public record OtherIncomeOption(long otherIncomeId, LocalDate incomeDate, String sourceName,
                                    String description, BigDecimal amount, String paymentReference) {
        @Override
        public String toString() {
            String details = description == null || description.isBlank() ? sourceName : sourceName + " - " + description;
            return incomeDate + " - " + details + " - " + defaultZero(amount).toPlainString();
        }
    }

    public record EditableExpense(Long expenseId, LocalDate expenseDate, String category, String payee,
                                  String description, BigDecimal amount, String paymentMethod,
                                  String paymentReference, String status) { }
    public record EditableOtherIncome(Long otherIncomeId, LocalDate incomeDate, String sourceName,
                                      String description, BigDecimal amount, String paymentReference) { }
    public record EditEligibility(boolean editable, LocalDateTime expiresAt, int currentRevision, String lockReason) { }
    public record RevisionAudit(int revisionNo, LocalDateTime changedAt, String changedByName,
                                String deviceName, String reason, String changeSummary) { }
    public record EditContext(long submissionId, LocalDate periodStart, LocalDate periodEnd, String notes,
                              EditEligibility eligibility, List<EditableExpense> expenses,
                              List<EditableOtherIncome> otherIncome, List<RevisionAudit> auditHistory) { }
    public record EditRequest(long submissionId, int expectedRevision, String notes, String reason,
                              List<EditableExpense> expenses, List<EditableOtherIncome> otherIncome) { }
    public record EditResult(long submissionId, int revisionNo, BalanceSheet sheet) { }
    private record AuditSnapshot(BalanceSheet sheet, List<EditableExpense> expenses,
                                 List<EditableOtherIncome> otherIncome) { }
    private record LockedSubmission(long submissionId, int locationId, LocalDate periodStart, LocalDate periodEnd,
                                    LocalDateTime submittedAt, int revisionNo, String notes, BalanceSheet sheet) { }

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
                                   BigDecimal balanceCf, int revisionNo, LocalDateTime lastEditedAt,
                                   String lastEditedByName, LocalDateTime editExpiresAt, boolean latestWithinWindow) {
        @Override
        public String toString() {
            return periodStart + " to " + periodEnd
                    + " - " + defaultText(submittedByName)
                    + " - CF " + defaultZero(balanceCf).toPlainString() + (revisionNo > 0 ? " - Rev " + revisionNo : "");
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
