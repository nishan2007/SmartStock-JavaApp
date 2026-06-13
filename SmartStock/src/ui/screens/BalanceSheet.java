package ui.screens;

import services.BalanceSheetService;
import services.BalanceSheetService.DrawSessionRange;
import services.BalanceSheetService.ExpenseEntry;
import services.BalanceSheetService.SheetLine;
import services.BalanceSheetService.SubmissionOption;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BalanceSheet extends JFrame {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");
    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final Color LIGHT_BACKGROUND = new Color(245, 247, 250);
    private static final Color LIGHT_SURFACE = Color.WHITE;
    private static final Color LIGHT_TEXT = new Color(17, 24, 39);
    private static final Color LIGHT_BORDER = new Color(148, 163, 184);
    private static final Color DARK_BACKGROUND = new Color(18, 18, 18);
    private static final Color DARK_SURFACE = new Color(30, 30, 30);
    private static final Color DARK_TEXT = new Color(235, 235, 235);
    private static final Color DARK_BORDER = new Color(75, 85, 99);

    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final JComboBox<SubmissionOption> savedSheetBox = new JComboBox<>();
    private final JLabel statusLabel = new JLabel("Current draft");
    private final JLabel balanceBfLabel = metricLabel("Balance BF: $0.00");
    private final JLabel cashInHandLabel = metricLabel("Cash In Hand: $0.00");
    private final JLabel balanceCfLabel = metricLabel("Balance CF: $0.00");
    private final JLabel netLabel = metricLabel("Surplus: $0.00");
    private final DefaultTableModel incomeModel = tableModel();
    private final DefaultTableModel receivableModel = tableModel();
    private final DefaultTableModel expenseModel = tableModel();
    private final DefaultTableModel payableModel = tableModel();
    private final DefaultTableModel drawerCashModel = tableModel();
    private final DefaultTableModel deviceSalesModel = tableModel();
    private final DefaultTableModel deviceOrdersModel = tableModel();
    private final DefaultTableModel accountPaymentsModel = tableModel();
    private final DefaultTableModel drawerChecksModel = tableModel();
    private services.BalanceSheetService.BalanceSheet currentSheet;
    private SwingWorker<services.BalanceSheetService.BalanceSheet, Void> sheetWorker;
    private SwingWorker<List<SubmissionOption>, Void> historyWorker;
    private SwingWorker<services.BalanceSheetService.BalanceSheet, Void> savedSheetWorker;
    private SwingWorker<List<DrawSessionRange>, Void> drawRangeWorker;
    private List<Long> matchedDrawerSessionIds = List.of();

    public BalanceSheet() {
        setTitle("Balance Sheet");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "BalanceSheet"));

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        mainPanel.setBackground(backgroundColor());

        JLabel titleLabel = new JLabel("Balance Sheet");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(textColor());
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        LocalDate today = StoreTimeZoneHelper.today();
        fromField.setText(today.toString());
        toField.setText(today.toString());

        JButton refreshButton = new JButton("Refresh");
        JButton addExpenseButton = new JButton("Log Expense");
        JButton submitButton = new JButton("Submit Balance Sheet");
        JButton matchDrawButton = new JButton("Match Draw Session");
        JButton openSavedButton = new JButton("Open Saved");
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        filters.add(label("From:"));
        filters.add(fromField);
        filters.add(label("To:"));
        filters.add(toField);
        filters.add(matchDrawButton);
        filters.add(submitButton);
        filters.add(addExpenseButton);
        filters.add(refreshButton);

        savedSheetBox.setPreferredSize(new Dimension(310, 30));
        JPanel savedPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        savedPanel.setOpaque(false);
        savedPanel.add(label("Previous:"));
        savedPanel.add(savedSheetBox);
        savedPanel.add(openSavedButton);

        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(filters, BorderLayout.EAST);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        statusLabel.setForeground(textColor());

        JPanel headerStack = new JPanel(new BorderLayout(0, 8));
        headerStack.setOpaque(false);
        headerStack.add(header, BorderLayout.NORTH);
        headerStack.add(statusLabel, BorderLayout.WEST);
        headerStack.add(savedPanel, BorderLayout.EAST);

        JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 0));
        metrics.setOpaque(false);
        metrics.add(balanceBfLabel);
        metrics.add(cashInHandLabel);
        metrics.add(balanceCfLabel);
        metrics.add(netLabel);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(headerStack, BorderLayout.NORTH);
        top.add(metrics, BorderLayout.SOUTH);

        JPanel grid = new JPanel(new GridLayout(5, 2, 12, 12));
        grid.setOpaque(false);
        grid.add(section("Income", new JTable(incomeModel), new Color(219, 234, 254)));
        grid.add(section("Accounts Receivable", new JTable(receivableModel), new Color(220, 252, 231)));
        grid.add(section("Drawer Cash In Hand", new JTable(drawerCashModel), new Color(226, 232, 240)));
        grid.add(section("Account Payments", new JTable(accountPaymentsModel), new Color(204, 251, 241)));
        grid.add(section("Sales By Device", new JTable(deviceSalesModel), new Color(224, 231, 255)));
        grid.add(section("Orders By Device", new JTable(deviceOrdersModel), new Color(240, 253, 244)));
        grid.add(section("Accounts Payable", new JTable(payableModel), new Color(255, 237, 213)));
        grid.add(section("Expenses", new JTable(expenseModel), new Color(254, 226, 226)));
        grid.add(section("Drawer Match Checks", new JTable(drawerChecksModel), new Color(254, 249, 195)));

        mainPanel.add(top, BorderLayout.NORTH);
        mainPanel.add(grid, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadSheet());
        submitButton.addActionListener(e -> submitSheet());
        matchDrawButton.addActionListener(e -> matchDrawSessionRange(true));
        openSavedButton.addActionListener(e -> loadSelectedSubmission());
        addExpenseButton.addActionListener(e -> showExpenseDialog());

        WindowHelper.configurePosWindow(this);
        ThemeManager.applyToWindow(this);
        showLoadingState("Loading balance sheet...");
        SwingUtilities.invokeLater(() -> {
            applyContinuingDrawSessionRange();
            loadSheet();
            loadSubmissionHistory();
        });
    }

    private JPanel section(String title, JTable table, Color headerColor) {
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(240);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        configureTable(table);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(headerColor);
        titleLabel.setForeground(new Color(17, 24, 39));
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titleLabel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel panel = new JPanel(new BorderLayout());
        panel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        panel.setBackground(surfaceColor());
        panel.setBorder(BorderFactory.createLineBorder(borderColor()));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void loadSheet() {
        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null) {
            return;
        }
        if (to.isBefore(from)) {
            JOptionPane.showMessageDialog(this, "To date must be on or after From date.", "Balance Sheet", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (sheetWorker != null && !sheetWorker.isDone()) {
            sheetWorker.cancel(true);
        }
        String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
        List<Long> cashDrawerSessionIds = List.copyOf(matchedDrawerSessionIds);
        showLoadingState("Loading balance sheet...");
        sheetWorker = new SwingWorker<>() {
            @Override
            protected services.BalanceSheetService.BalanceSheet doInBackground() throws Exception {
                return BalanceSheetService.loadBalanceSheet(from, to, storeZoneId, cashDrawerSessionIds);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    currentSheet = get();
                    renderSheet(currentSheet);
                    String sessionText = cashDrawerSessionIds.isEmpty() ? "" : " filtered to " + cashDrawerSessionIds.size() + " draw session(s)";
                    statusLabel.setText("Current draft for " + from + " to " + to + sessionText);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to load balance sheet: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        sheetWorker.execute();
    }

    private void renderSheet(services.BalanceSheetService.BalanceSheet sheet) {
        fill(incomeModel, sheet.income(), sheet.totalIncome());
        fill(receivableModel, sheet.receivables(), sheet.totalReceivables());
        fill(expenseModel, sheet.expenses(), sheet.totalExpenses());
        fill(payableModel, sheet.payables(), sheet.totalPayables());
        fill(drawerCashModel, sheet.drawerCash(), sheet.cashInHand());
        fill(deviceSalesModel, sheet.deviceSales(), totalLines(sheet.deviceSales()));
        fill(deviceOrdersModel, sheet.deviceOrders(), totalLines(sheet.deviceOrders()));
        fill(accountPaymentsModel, sheet.accountPayments(), totalLines(sheet.accountPayments()));
        fill(drawerChecksModel, sheet.drawerChecks(), totalLines(sheet.drawerChecks()));
        balanceBfLabel.setText("Balance BF: " + money(sheet.balanceBf()));
        cashInHandLabel.setText("Cash In Hand: " + money(sheet.cashInHand()));
        balanceCfLabel.setText("Balance CF: " + money(sheet.balanceCf()));
        BigDecimal net = sheet.totalIncome().subtract(sheet.totalExpenses()).subtract(sheet.totalPayables());
        netLabel.setText((net.compareTo(BigDecimal.ZERO) < 0 ? "Deficit: " : "Surplus: ") + money(net.abs()));
    }

    private void submitSheet() {
        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }

        JTextArea notesArea = new JTextArea(3, 28);
        int confirm = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(notesArea),
                "Submit Balance Sheet",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            long submissionId = BalanceSheetService.submitBalanceSheet(from, to, StoreTimeZoneHelper.getStoreZoneId(), notesArea.getText(), matchedDrawerSessionIds);
            loadSubmissionHistory();
            loadSubmissionById(submissionId);
            JOptionPane.showMessageDialog(this, "Balance sheet submitted.", "Balance Sheet", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to submit balance sheet: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSubmissionHistory() {
        Object selected = savedSheetBox.getSelectedItem();
        Long selectedId = selected instanceof SubmissionOption option ? option.submissionId() : null;
        savedSheetBox.removeAllItems();
        savedSheetBox.setEnabled(false);
        if (historyWorker != null && !historyWorker.isDone()) {
            historyWorker.cancel(true);
        }
        historyWorker = new SwingWorker<>() {
            @Override
            protected List<SubmissionOption> doInBackground() throws Exception {
                return BalanceSheetService.listSubmissions();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    for (SubmissionOption option : get()) {
                        savedSheetBox.addItem(option);
                        if (selectedId != null && option.submissionId() == selectedId) {
                            savedSheetBox.setSelectedItem(option);
                        }
                    }
                    savedSheetBox.setEnabled(true);
                } catch (Exception ex) {
                    savedSheetBox.setEnabled(true);
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to load saved balance sheets: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        historyWorker.execute();
    }

    private void loadSubmissionById(long submissionId) {
        if (savedSheetWorker != null && !savedSheetWorker.isDone()) {
            savedSheetWorker.cancel(true);
        }
        statusLabel.setText("Opening saved balance sheet...");
        savedSheetWorker = new SwingWorker<>() {
            @Override
            protected services.BalanceSheetService.BalanceSheet doInBackground() throws Exception {
                return BalanceSheetService.loadSubmission(submissionId);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    currentSheet = get();
                    matchedDrawerSessionIds = List.of();
                    fromField.setText(currentSheet.periodStart().toString());
                    toField.setText(currentSheet.periodEnd().toString());
                    renderSheet(currentSheet);
                    statusLabel.setText("Submitted by " + safeText(currentSheet.submittedByName()) + " at " + currentSheet.submittedAt().format(DATE_TIME_FORMAT));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to open saved balance sheet: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        savedSheetWorker.execute();
    }

    private void loadSelectedSubmission() {
        SubmissionOption selected = (SubmissionOption) savedSheetBox.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No saved balance sheet is selected.", "Balance Sheet", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        loadSubmissionById(selected.submissionId());
    }

    private void applyContinuingDrawSessionRange() {
        LocalDate selectedDate = parseDate(fromField.getText().trim(), "From");
        if (selectedDate == null) {
            return;
        }
        String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
        if (drawRangeWorker != null && !drawRangeWorker.isDone()) {
            drawRangeWorker.cancel(true);
        }
        drawRangeWorker = new SwingWorker<>() {
            @Override
            protected List<DrawSessionRange> doInBackground() throws Exception {
                return BalanceSheetService.findDrawSessionRanges(storeZoneId, selectedDate, selectedDate);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    List<DrawSessionRange> ranges = get();
                    DrawSessionSelection selection = summarizeRanges(ranges);
                    if (selection != null && !selection.from().equals(selection.to())) {
                        matchedDrawerSessionIds = selection.sessionIds();
                        fromField.setText(selection.from().toString());
                        toField.setText(selection.to().toString());
                        statusLabel.setText("Matched continuing draw sessions: " + selection.label());
                        loadSheet();
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to check continuing draw session: " + ex.getMessage());
                }
            }
        };
        drawRangeWorker.execute();
    }

    private void matchDrawSessionRange(boolean showMessage) {
        LocalDate selectedDate = parseDate(fromField.getText().trim(), "From");
        if (selectedDate == null) {
            return;
        }
        LocalDate toDate = parseDate(toField.getText().trim(), "To");
        if (toDate == null) {
            toDate = selectedDate;
        }
        LocalDate rangeEnd = toDate;
        String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
        statusLabel.setText("Finding matching draw session...");
        new SwingWorker<List<DrawSessionRange>, Void>() {
            @Override
            protected List<DrawSessionRange> doInBackground() throws Exception {
                return BalanceSheetService.findDrawSessionRanges(storeZoneId, selectedDate, rangeEnd);
            }

            @Override
            protected void done() {
                try {
                    List<DrawSessionRange> ranges = get();
                    DrawSessionSelection selection = summarizeRanges(ranges);
                    if (selection == null) {
                        matchedDrawerSessionIds = List.of();
                        statusLabel.setText("No draw session matched " + selectedDate);
                        if (showMessage) {
                            JOptionPane.showMessageDialog(BalanceSheet.this, "No draw session overlaps the selected date.", "Draw Session", JOptionPane.INFORMATION_MESSAGE);
                        }
                        return;
                    }
                    matchedDrawerSessionIds = selection.sessionIds();
                    fromField.setText(selection.from().toString());
                    toField.setText(selection.to().toString());
                    loadSheet();
                    statusLabel.setText("Matched draw sessions: " + selection.label());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to match draw session: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static DrawSessionSelection summarizeRanges(List<DrawSessionRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }
        LocalDate from = null;
        LocalDate to = null;
        List<Long> sessionIds = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (DrawSessionRange range : ranges) {
            sessionIds.add(range.sessionId());
            if (from == null || range.openedDate().isBefore(from)) {
                from = range.openedDate();
            }
            if (to == null || range.closedDate().isAfter(to)) {
                to = range.closedDate();
            }
            labels.add(range.label());
        }
        String label = labels.size() == 1 ? labels.get(0) : labels.size() + " drawers";
        return new DrawSessionSelection(from, to, sessionIds, label);
    }

    private void showExpenseDialog() {
        JTextField dateField = new JTextField(StoreTimeZoneHelper.today().toString(), 12);
        JTextField categoryField = new JTextField("General", 18);
        JTextField payeeField = new JTextField(18);
        JTextField amountField = new JTextField(12);
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"PAID", "UNPAID"});
        JComboBox<String> methodBox = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "MMG", "BANK", "OTHER"});
        JTextField referenceField = new JTextField(18);
        JTextArea descriptionArea = new JTextArea(3, 24);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        addRow(panel, gbc, 0, "Date:", dateField);
        addRow(panel, gbc, 1, "Category:", categoryField);
        addRow(panel, gbc, 2, "Payee:", payeeField);
        addRow(panel, gbc, 3, "Amount:", amountField);
        addRow(panel, gbc, 4, "Status:", statusBox);
        addRow(panel, gbc, 5, "Method:", methodBox);
        addRow(panel, gbc, 6, "Reference:", referenceField);
        addRow(panel, gbc, 7, "Description:", new JScrollPane(descriptionArea));

        int result = JOptionPane.showConfirmDialog(this, panel, "Log Expense", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        LocalDate date = parseDate(dateField.getText().trim(), "Expense date");
        BigDecimal amount = parseMoney(amountField.getText().trim());
        String category = categoryField.getText().trim();
        if (date == null || amount == null || category.isBlank()) {
            if (category.isBlank()) {
                JOptionPane.showMessageDialog(this, "Category is required.", "Log Expense", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        try {
            BalanceSheetService.addManualExpense(new ExpenseEntry(
                    date,
                    category,
                    blankToNull(payeeField.getText()),
                    blankToNull(descriptionArea.getText()),
                    amount,
                    String.valueOf(methodBox.getSelectedItem()),
                    blankToNull(referenceField.getText()),
                    String.valueOf(statusBox.getSelectedItem())
            ));
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to log expense: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private static void fill(DefaultTableModel model, List<SheetLine> lines, BigDecimal total) {
        model.setRowCount(0);
        for (SheetLine line : lines) {
            model.addRow(new Object[]{line.label(), money(line.amount())});
        }
        model.addRow(new Object[]{"TOTAL", money(total)});
    }

    private void showLoadingState(String message) {
        fillLoading(incomeModel);
        fillLoading(receivableModel);
        fillLoading(expenseModel);
        fillLoading(payableModel);
        fillLoading(drawerCashModel);
        fillLoading(deviceSalesModel);
        fillLoading(deviceOrdersModel);
        fillLoading(accountPaymentsModel);
        fillLoading(drawerChecksModel);
        statusLabel.setText(message);
    }

    private static void fillLoading(DefaultTableModel model) {
        model.setRowCount(0);
        model.addRow(new Object[]{"Loading...", ""});
    }

    private static BigDecimal totalLines(List<SheetLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SheetLine line : lines) {
            total = total.add(line.amount() == null ? BigDecimal.ZERO : line.amount());
        }
        return total;
    }

    private static DefaultTableModel tableModel() {
        return new DefaultTableModel(new Object[]{"Description", "Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JLabel metricLabel(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(surfaceColor());
        label.setForeground(textColor());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        label.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        label.setFont(new Font("SansSerif", Font.BOLD, 15));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return label;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(textColor());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    private static void configureTable(JTable table) {
        table.setBackground(surfaceColor());
        table.setForeground(textColor());
        table.setGridColor(borderColor());
        table.setSelectionBackground(ThemeManager.isDarkModeEnabled() ? new Color(37, 99, 235) : new Color(219, 234, 254));
        table.setSelectionForeground(ThemeManager.isDarkModeEnabled() ? Color.WHITE : LIGHT_TEXT);
        table.getTableHeader().setBackground(ThemeManager.isDarkModeEnabled() ? new Color(38, 38, 38) : new Color(241, 245, 249));
        table.getTableHeader().setForeground(textColor());
    }

    private static Color backgroundColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    private static Color surfaceColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    private static Color textColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_TEXT : LIGHT_TEXT;
    }

    private static Color borderColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_BORDER : LIGHT_BORDER;
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, label + " must use YYYY-MM-DD.", "Balance Sheet", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            JOptionPane.showMessageDialog(this, "Amount is required.", "Log Expense", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").replace("$", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Amount cannot be negative.", "Log Expense", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Amount must be a valid number.", "Log Expense", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private static String money(BigDecimal value) {
        return CURRENCY.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private record DrawSessionSelection(LocalDate from, LocalDate to, List<Long> sessionIds, String label) {
    }
}
