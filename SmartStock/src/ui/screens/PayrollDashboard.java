package ui.screens;

import utils.CurrencyFormatter;
import managers.TimeClockManager;
import managers.TimeClockManager.PayrollSummary;
import managers.TimeClockManager.TimeClockRow;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PayrollDashboard extends JFrame {
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    private final MetricCard totalEmployeesCard;
    private final MetricCard totalHoursCard;
    private final MetricCard totalPayCard;
    private final JComboBox<PayPeriodOption> payPeriodBox;
    private final JComboBox<EmployeeOption> employeeBox;
    private final JTextField searchField;
    private final DefaultTableModel summaryModel;
    private final DefaultTableModel detailModel;
    private final TableRowSorter<DefaultTableModel> summarySorter;
    private final TableRowSorter<DefaultTableModel> detailSorter;
    private final JTable summaryTable;
    private final JTabbedPane tabbedPane;
    private List<PayrollSummary> allSummaries = new ArrayList<>();
    private List<TimeClockRow> allRows = new ArrayList<>();
    private final List<PayrollSummary> renderedSummaries = new ArrayList<>();
    private boolean updatingPayPeriodOptions;
    private boolean updatingEmployeeOptions;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final NumberFormat CURRENCY_FORMAT = CurrencyFormatter.create(Locale.US);

    public PayrollDashboard() {
        setTitle("Payroll Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(16, 16));
        setJMenuBar(AppMenuBar.create(this, "PayrollDashboard"));

        JPanel mainPanel = new JPanel(new BorderLayout(16, 16));
        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        mainPanel.setBackground(DeckersPalette.background());

        JLabel titleLabel = new JLabel("Payroll Dashboard");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(DeckersPalette.text());
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JPanel filterPanel = new JPanel(new GridBagLayout());
        DeckersSwing.styleBand(filterPanel, DeckersPalette.ORANGE, new Insets(12, 12, 12, 12));
        payPeriodBox = new JComboBox<>();
        employeeBox = new JComboBox<>();
        searchField = new JTextField();
        JButton generateCurrentButton = new JButton("Generate Current Payroll");
        JButton bonusSelectedButton = new JButton("Bonus Selected");
        JButton bonusAllButton = new JButton("Bonus All in Period");
        JButton markPaidButton = new JButton("Mark Selected Paid");
        JButton refreshButton = new JButton("Refresh");
        JPanel leftFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftFilterPanel.setOpaque(false);
        leftFilterPanel.add(createFilterLabel("Pay Period:"));
        leftFilterPanel.add(payPeriodBox);
        leftFilterPanel.add(createFilterLabel("Employee:"));
        leftFilterPanel.add(employeeBox);
        payPeriodBox.setPreferredSize(new Dimension(250, 30));
        employeeBox.setPreferredSize(new Dimension(220, 30));
        styleComboBox(payPeriodBox);
        styleComboBox(employeeBox);
        searchField.setColumns(22);
        searchField.setPreferredSize(new Dimension(260, 30));
        searchField.setMinimumSize(new Dimension(220, 30));
        DeckersSwing.styleField(searchField);
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(createFilterLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        DeckersSwing.styleUtilityButton(generateCurrentButton, DeckersPalette.LIME);
        DeckersSwing.styleUtilityButton(bonusSelectedButton, DeckersPalette.YELLOW);
        DeckersSwing.styleUtilityButton(bonusAllButton, DeckersPalette.ORANGE);
        DeckersSwing.styleUtilityButton(markPaidButton, DeckersPalette.CORAL);
        DeckersSwing.styleUtilityButton(refreshButton, DeckersPalette.PURPLE);
        buttonPanel.add(generateCurrentButton);
        buttonPanel.add(bonusSelectedButton);
        buttonPanel.add(bonusAllButton);
        buttonPanel.add(markPaidButton);
        buttonPanel.add(refreshButton);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 12);
        gbc.anchor = GridBagConstraints.WEST;
        filterPanel.add(leftFilterPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        filterPanel.add(searchPanel, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.anchor = GridBagConstraints.EAST;
        filterPanel.add(buttonPanel, gbc);

        JPanel headerPanel = new JPanel(new BorderLayout(0, 14));
        headerPanel.setOpaque(false);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.SOUTH);

        totalEmployeesCard = new MetricCard("Employees", DeckersPalette.ORANGE);
        totalHoursCard = new MetricCard("Hours", DeckersPalette.MAGENTA);
        totalPayCard = new MetricCard("Total Pay", DeckersPalette.LIME);
        JPanel metricsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        metricsPanel.setOpaque(false);
        metricsPanel.add(totalEmployeesCard);
        metricsPanel.add(totalHoursCard);
        metricsPanel.add(totalPayCard);

        summaryModel = new DefaultTableModel(
                new Object[]{"Employee ID", "Employee", "Role", "Pay Period", "Period Type", "Hour Limit", "Pay Date", "Days Worked", "Total Hours", "Regular Hours", "OT Hours", "Regular Pay", "OT Pay", "Bonus", "Total Pay", "Paid Amount", "Amount Due", "Status", "Paid At", "Paid By", "Pay Type", "Records", "Location"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        summaryTable = new JTable(summaryModel);
        summaryTable.setRowHeight(30);
        summaryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        DeckersSwing.styleTable(summaryTable, DeckersPalette.YELLOW);
        summaryTable.setDefaultRenderer(Object.class, new PayrollCellRenderer());
        summarySorter = new TableRowSorter<>(summaryModel);
        summaryTable.setRowSorter(summarySorter);

        detailModel = new DefaultTableModel(
                new Object[]{"Clock ID", "Employee", "Role", "Date", "Clock In", "Lunch Start", "Lunch End", "Clock Out", "Daily Hours", "Regular Hours", "OT Hours", "Regular Pay", "OT Pay", "Total Pay", "Pay Period", "Period Type", "Hour Limit", "Pay Date", "Location", "Clock Review"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable detailTable = new JTable(detailModel);
        detailTable.setRowHeight(28);
        DeckersSwing.styleTable(detailTable, DeckersPalette.MAGENTA);
        detailSorter = new TableRowSorter<>(detailModel);
        detailTable.setRowSorter(detailSorter);

        tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(DeckersPalette.background());
        tabbedPane.setForeground(DeckersPalette.text());
        tabbedPane.addTab("Payroll Summary", createTableScrollPane(summaryTable));
        tabbedPane.addTab("Time Records", createTableScrollPane(detailTable));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
        centerPanel.setOpaque(false);
        centerPanel.add(metricsPanel, BorderLayout.NORTH);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(loadingState, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);

        payPeriodBox.addActionListener(e -> {
            if (!updatingPayPeriodOptions) {
                renderTables();
            }
        });
        employeeBox.addActionListener(e -> {
            if (!updatingEmployeeOptions) {
                renderTables();
            }
        });
        generateCurrentButton.addActionListener(e -> generateCurrentPayroll());
        bonusSelectedButton.addActionListener(e -> giveSelectedEmployeeBonus());
        bonusAllButton.addActionListener(e -> giveAllEmployeesBonus());
        markPaidButton.addActionListener(e -> markSelectedPayrollPaid());
        refreshButton.addActionListener(e -> loadPayroll());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilter();
            }
        });

        loadPayroll();
        WindowHelper.configurePosWindow(this);
    }

    private JLabel createFilterLabel(String text) {
        JLabel label = DeckersSwing.metaLabel(text);
        return label;
    }

    private JScrollPane createTableScrollPane(JTable table) {
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(DeckersPalette.surface());
        scrollPane.setBackground(DeckersPalette.surface());
        scrollPane.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
        return scrollPane;
    }

    private <T> void styleComboBox(JComboBox<T> comboBox) {
        comboBox.setFont(new Font("SansSerif", Font.PLAIN, 14));
        comboBox.setForeground(DeckersPalette.text());
        comboBox.setBackground(DeckersPalette.fieldBackground());
        comboBox.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
    }

    private void loadPayroll() {
        CachedUiLoader.load(this, "payroll:dashboard", TimeClockManager.PayrollDashboard.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                TimeClockManager::loadPayrollDashboard, this::applyPayroll);
    }

    private void applyPayroll(TimeClockManager.PayrollDashboard dashboard) {
        allRows = dashboard.timeRows();
        allSummaries = dashboard.summaries();
        populatePayPeriods();
        populateEmployees();
        renderTables();
    }

    private void populatePayPeriods() {
        Object selected = payPeriodBox.getSelectedItem();
        String selectedKey = selected instanceof PayPeriodOption option ? option.key() : "DEFAULT";
        updatingPayPeriodOptions = true;
        payPeriodBox.removeAllItems();
        payPeriodBox.addItem(new PayPeriodOption("ALL", "All Pay Periods", null, null));
        payPeriodBox.addItem(new PayPeriodOption("CURRENT", "Current Payroll — All Employee Settings", null, null));

        Map<String, PayPeriodOption> options = new LinkedHashMap<>();
        for (PayrollSummary summary : allSummaries) {
            String key = summary.payPeriodStart() + "|" + summary.payPeriodEnd();
            options.putIfAbsent(key, new PayPeriodOption(
                    key,
                    summary.payPeriodStart().format(DATE_FORMAT) + " - " + summary.payPeriodEnd().format(DATE_FORMAT)
                            + "  Paid " + summary.payDate().format(DATE_FORMAT),
                    summary.payPeriodStart(),
                    summary.payPeriodEnd()
            ));
        }

        for (PayPeriodOption option : options.values()) {
            payPeriodBox.addItem(option);
        }

        for (int i = 0; i < payPeriodBox.getItemCount(); i++) {
            if (payPeriodBox.getItemAt(i).key().equals(selectedKey)) {
                payPeriodBox.setSelectedIndex(i);
                updatingPayPeriodOptions = false;
                return;
            }
        }
        payPeriodBox.setSelectedIndex(1);
        updatingPayPeriodOptions = false;
    }

    private void populateEmployees() {
        Object selected = employeeBox.getSelectedItem();
        int selectedUserId = selected instanceof EmployeeOption option ? option.userId() : 0;
        updatingEmployeeOptions = true;
        employeeBox.removeAllItems();
        employeeBox.addItem(new EmployeeOption(0, "All Employees"));

        Map<Integer, String> employees = new LinkedHashMap<>();
        for (PayrollSummary summary : allSummaries) {
            employees.putIfAbsent(summary.userId(), summary.employeeName());
        }

        for (Map.Entry<Integer, String> employee : employees.entrySet()) {
            employeeBox.addItem(new EmployeeOption(employee.getKey(), employee.getValue()));
        }

        for (int i = 0; i < employeeBox.getItemCount(); i++) {
            if (employeeBox.getItemAt(i).userId() == selectedUserId) {
                employeeBox.setSelectedIndex(i);
                updatingEmployeeOptions = false;
                return;
            }
        }
        employeeBox.setSelectedIndex(0);
        updatingEmployeeOptions = false;
    }

    private void renderTables() {
        summaryModel.setRowCount(0);
        detailModel.setRowCount(0);
        renderedSummaries.clear();

        PayPeriodOption selectedPeriod = (PayPeriodOption) payPeriodBox.getSelectedItem();
        EmployeeOption selectedEmployee = (EmployeeOption) employeeBox.getSelectedItem();
        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalPay = BigDecimal.ZERO;
        Set<Integer> employeeIds = new HashSet<>();

        for (PayrollSummary summary : allSummaries) {
            if (!matchesPeriod(summary.payPeriodStart(), summary.payPeriodEnd(), selectedPeriod)) {
                continue;
            }
            if (!matchesEmployee(summary.userId(), selectedEmployee)) {
                continue;
            }
            employeeIds.add(summary.userId());
            totalHours = totalHours.add(summary.totalHours());
            totalPay = totalPay.add(summary.totalPay());
            renderedSummaries.add(summary);
            summaryModel.addRow(new Object[]{
                    summary.userId(),
                    summary.employeeName(),
                    formatRole(summary.employeeRole()),
                    formatPayPeriod(summary.payPeriodStart(), summary.payPeriodEnd()),
                    formatPeriodType(summary.payPeriodType()),
                    formatHours(summary.workHourLimit()),
                    summary.payDate().format(DATE_FORMAT),
                    summary.daysWorked(),
                    formatHours(summary.totalHours()),
                    formatHours(summary.regularHours()),
                    formatHours(summary.overtimeHours()),
                    CURRENCY_FORMAT.format(summary.regularPay()),
                    CURRENCY_FORMAT.format(summary.overtimePay()),
                    CURRENCY_FORMAT.format(summary.bonusAmount()),
                    CURRENCY_FORMAT.format(summary.totalPay()),
                    CURRENCY_FORMAT.format(summary.paidAmount()),
                    CURRENCY_FORMAT.format(summary.amountDue()),
                    payrollStatus(summary),
                    formatDateTime(summary.paidAt()),
                    summary.paidByName(),
                    formatCompensationType(summary.compensationType()),
                    summary.recordCount(),
                    summary.locationName()
            });
        }

        for (TimeClockRow row : allRows) {
            if (!matchesPeriod(row.payPeriodStart(), row.payPeriodEnd(), selectedPeriod)) {
                continue;
            }
            if (!matchesEmployee(row.userId(), selectedEmployee)) {
                continue;
            }
            detailModel.addRow(new Object[]{
                    row.clockId(),
                    row.employeeName(),
                    formatRole(row.employeeRole()),
                    row.workDate().format(DATE_FORMAT),
                    formatTime(row.clockIn()),
                    formatTime(row.lunchStart()),
                    formatTime(row.lunchEnd()),
                    formatTime(row.clockOut()),
                    formatHours(row.dailyHours()),
                    formatHours(row.regularHours()),
                    formatHours(row.overtimeHours()),
                    CURRENCY_FORMAT.format(row.regularPay()),
                    CURRENCY_FORMAT.format(row.overtimePay()),
                    CURRENCY_FORMAT.format(row.totalPay()),
                    formatPayPeriod(row.payPeriodStart(), row.payPeriodEnd()),
                    formatPeriodType(row.payPeriodType()),
                    formatHours(row.workHourLimit()),
                    row.payDate().format(DATE_FORMAT),
                    row.locationName(),
                    row.autoClockOut() ? "Auto clock-out — " + (row.autoClockOutReviewStatus() == null
                            ? "Pending" : row.autoClockOutReviewStatus()) : ""
            });
        }

        int totalEmployees = totalEmployeeCount();
        BigDecimal allHours = totalDashboardHours();
        BigDecimal allPay = totalDashboardPay();
        totalEmployeesCard.setMetric(String.valueOf(employeeIds.size()), ratio(BigDecimal.valueOf(employeeIds.size()), BigDecimal.valueOf(totalEmployees)));
        totalHoursCard.setMetric(formatHours(totalHours), ratio(totalHours, allHours));
        totalPayCard.setMetric(CURRENCY_FORMAT.format(totalPay), ratio(totalPay, allPay));
        applyFilter();
    }

    private void giveSelectedEmployeeBonus() {
        int viewRow = summaryTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select the employee pay period that should receive the bonus.", "Payroll Bonus", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = summaryTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= renderedSummaries.size()) {
            JOptionPane.showMessageDialog(this, "The selected payroll row could not be found. Refresh and try again.", "Payroll Bonus", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PayrollSummary summary = renderedSummaries.get(modelRow);
        BonusInput input = showBonusDialog("Bonus for " + summary.employeeName(), 1);
        if (input == null) {
            return;
        }
        try {
            TimeClockManager.addPayrollBonus(summary, input.amount(), input.reason());
            loadPayroll();
            JOptionPane.showMessageDialog(this, "Bonus added for " + summary.employeeName() + ".", "Payroll Bonus", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to add payroll bonus: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void giveAllEmployeesBonus() {
        PayPeriodOption selectedPeriod = (PayPeriodOption) payPeriodBox.getSelectedItem();
        if (selectedPeriod == null || selectedPeriod.start() == null || selectedPeriod.end() == null) {
            JOptionPane.showMessageDialog(this, "Select one specific pay period before giving all employees a bonus.", "Payroll Bonus", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<PayrollSummary> periodSummaries = new ArrayList<>();
        for (PayrollSummary summary : allSummaries) {
            if (matchesPeriod(summary.payPeriodStart(), summary.payPeriodEnd(), selectedPeriod)) {
                periodSummaries.add(summary);
            }
        }
        if (periodSummaries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No employees were found in the selected pay period.", "Payroll Bonus", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        BonusInput input = showBonusDialog("Bonus for All Employees", periodSummaries.size());
        if (input == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Give " + CURRENCY_FORMAT.format(input.amount()) + " to each of " + periodSummaries.size()
                        + " employees?\n\nTotal bonuses: "
                        + CURRENCY_FORMAT.format(input.amount().multiply(BigDecimal.valueOf(periodSummaries.size()))),
                "Confirm Bonuses",
                JOptionPane.YES_NO_OPTION
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            TimeClockManager.addPayrollBonuses(periodSummaries, input.amount(), input.reason());
            loadPayroll();
            JOptionPane.showMessageDialog(this, "Bonus added for all " + periodSummaries.size() + " employees.", "Payroll Bonus", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to add payroll bonuses: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BonusInput showBonusDialog(String title, int employeeCount) {
        JTextField amountField = new JTextField(14);
        JTextField reasonField = new JTextField(24);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel(employeeCount == 1 ? "Bonus amount:" : "Bonus amount for each employee:"));
        panel.add(amountField);
        panel.add(new JLabel("Reason / note (optional):"));
        panel.add(reasonField);
        int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        try {
            String rawAmount = amountField.getText().replace("$", "").replace(",", "").trim();
            BigDecimal amount = CurrencyFormatter.normalize(new BigDecimal(rawAmount));
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
            return new BonusInput(amount, reasonField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid bonus amount greater than zero.", "Payroll Bonus", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private void markSelectedPayrollPaid() {
        int viewRow = summaryTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select the employee pay period to mark as paid.", "Payroll", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int modelRow = summaryTable.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= renderedSummaries.size()) {
            JOptionPane.showMessageDialog(this, "The selected payroll row could not be found. Refresh and try again.", "Payroll", JOptionPane.WARNING_MESSAGE);
            return;
        }

        PayrollSummary summary = renderedSummaries.get(modelRow);
        if (summary.amountDue().compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this, "This payroll period is already fully paid.", "Payroll", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String message = "Mark payroll as paid for:\n\n"
                + summary.employeeName() + "\n"
                + formatPayPeriod(summary.payPeriodStart(), summary.payPeriodEnd()) + "\n"
                + "Total Earned: " + CURRENCY_FORMAT.format(summary.totalPay()) + "\n"
                + "Already Paid: " + CURRENCY_FORMAT.format(summary.paidAmount()) + "\n"
                + "Amount Due: " + CURRENCY_FORMAT.format(summary.amountDue());
        if (!summary.paid() && summary.paidAmount().compareTo(BigDecimal.ZERO) > 0) {
            message += "\n\nThis will create a supplemental payroll payment for the additional amount due.";
        }

        JComboBox<String> paymentMethodBox = new JComboBox<>(new String[]{"Cash in Hand", "Bank Account"});
        JTextField bankReferenceField = new JTextField(22);
        bankReferenceField.setEnabled(false);
        paymentMethodBox.addActionListener(e -> bankReferenceField.setEnabled(paymentMethodBox.getSelectedIndex() == 1));
        JPanel paymentPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        paymentPanel.add(new JLabel("Payment method:"));
        paymentPanel.add(paymentMethodBox);
        paymentPanel.add(new JLabel("Bank reference (required for Bank Account):"));
        paymentPanel.add(bankReferenceField);

        Object[] prompt = {message, paymentPanel};
        int result = JOptionPane.showConfirmDialog(this, prompt, "Mark Payroll Paid", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        boolean bankPayment = paymentMethodBox.getSelectedIndex() == 1;
        String bankReference = bankReferenceField.getText().trim();
        if (bankPayment && bankReference.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter the bank transaction reference.", "Payroll", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            TimeClockManager.markPayrollPaid(summary, bankPayment ? "BANK" : "CASH", bankReference);
            loadPayroll();
            JOptionPane.showMessageDialog(this,
                    "Payroll marked as paid through " + (bankPayment ? "Bank Account." : "Cash in Hand."),
                    "Payroll", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to mark payroll as paid: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateCurrentPayroll() {
        loadPayroll();
        searchField.setText("");

        for (int i = 0; i < payPeriodBox.getItemCount(); i++) {
            PayPeriodOption option = payPeriodBox.getItemAt(i);
            if ("CURRENT".equals(option.key())) {
                payPeriodBox.setSelectedIndex(i);
                renderTables();
                tabbedPane.setSelectedIndex(0);
                if (summaryModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(
                            this,
                            "No time records were found for the current pay period.",
                            "Current Payroll",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
                return;
            }
        }
    }

    private boolean matchesPeriod(LocalDate start, LocalDate end, PayPeriodOption selectedPeriod) {
        if (selectedPeriod == null || "ALL".equals(selectedPeriod.key())) return true;
        if ("CURRENT".equals(selectedPeriod.key())) {
            LocalDate today = StoreTimeZoneHelper.today();
            return !today.isBefore(start) && !today.isAfter(end);
        }
        return selectedPeriod.start() != null && selectedPeriod.start().equals(start)
                && selectedPeriod.end().equals(end);
    }

    private boolean matchesEmployee(int userId, EmployeeOption selectedEmployee) {
        return selectedEmployee == null || selectedEmployee.userId() == 0 || selectedEmployee.userId() == userId;
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        RowFilter<DefaultTableModel, Object> filter = text.isEmpty()
                ? null
                : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text));
        summarySorter.setRowFilter(filter);
        detailSorter.setRowFilter(filter);
    }

    private static String formatPayPeriod(LocalDate start, LocalDate end) {
        return start.format(DATE_FORMAT) + " - " + end.format(DATE_FORMAT);
    }

    private static String formatRole(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }

        String[] words = roleName.trim().replace("_", " ").split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                formatted.append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }

    private static String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMAT);
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DATE_TIME_FORMAT);
    }

    private static String payrollStatus(PayrollSummary summary) {
        if (summary.paid()) {
            return "Paid";
        }
        if (summary.paidAmount().compareTo(BigDecimal.ZERO) > 0) {
            return "Additional Due";
        }
        return "Unpaid";
    }

    private static String formatHours(BigDecimal hours) {
        return hours.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatCompensationType(String compensationType) {
        if ("SALARY".equalsIgnoreCase(compensationType)) {
            return "Fixed Salary";
        }
        if ("DAILY".equalsIgnoreCase(compensationType)) {
            return "Day Salary";
        }
        return "Hourly";
    }

    private static String formatPeriodType(String periodType) {
        if ("WEEKLY".equalsIgnoreCase(periodType)) return "Weekly";
        return "FOUR_MONTH_BLOCKS".equalsIgnoreCase(periodType) ? "Four month blocks" : "Semi-monthly";
    }

    private int totalEmployeeCount() {
        Set<Integer> ids = new HashSet<>();
        for (PayrollSummary summary : allSummaries) {
            ids.add(summary.userId());
        }
        return ids.size();
    }

    private BigDecimal totalDashboardHours() {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollSummary summary : allSummaries) {
            total = total.add(summary.totalHours());
        }
        return total;
    }

    private BigDecimal totalDashboardPay() {
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollSummary summary : allSummaries) {
            total = total.add(summary.totalPay());
        }
        return total;
    }

    private static int ratio(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return value.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .min(BigDecimal.valueOf(100))
                .intValue();
    }

    private class PayrollCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int row,
                int column
        ) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            component.setForeground(DeckersPalette.text());
            component.setBackground(isSelected ? DeckersPalette.tilePressed(DeckersPalette.YELLOW) : DeckersPalette.tableBody(DeckersPalette.YELLOW));
            if (!isSelected && column == 17) {
                String status = value == null ? "" : value.toString();
                if ("Paid".equals(status)) {
                    component.setBackground(DeckersPalette.tileFill(DeckersPalette.LIME));
                } else if ("Additional Due".equals(status)) {
                    component.setBackground(DeckersPalette.tileFill(DeckersPalette.YELLOW));
                } else if ("Unpaid".equals(status)) {
                    component.setBackground(DeckersPalette.tileFill(DeckersPalette.CORAL));
                }
            }
            return component;
        }
    }

    private static class MetricCard extends JPanel {
        private final JLabel titleLabel;
        private final JLabel valueLabel;
        private final JProgressBar progressBar;

        MetricCard(String title, Color accent) {
            super(new BorderLayout(0, 8));
            setOpaque(true);
            setBackground(DeckersPalette.sectionFill(accent));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DeckersPalette.sectionBorder(accent), 1),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 5, 0, 0, accent),
                            new EmptyBorder(12, 14, 12, 14)
                    )
            ));

            titleLabel = new JLabel(title);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            titleLabel.setForeground(DeckersPalette.muted());
            titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

            valueLabel = new JLabel("0");
            valueLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
            valueLabel.setForeground(DeckersPalette.text());
            valueLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

            progressBar = new JProgressBar(0, 100);
            progressBar.setValue(0);
            progressBar.setStringPainted(false);
            progressBar.setForeground(accent);
            progressBar.setBackground(DeckersPalette.tileFill(accent));
            progressBar.setBorderPainted(false);
            progressBar.setPreferredSize(new Dimension(1, 8));

            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
            add(progressBar, BorderLayout.SOUTH);
        }

        void setMetric(String value, int percent) {
            valueLabel.setText(value);
            progressBar.setValue(Math.max(0, Math.min(100, percent)));
        }
    }

    private record PayPeriodOption(String key, String label, LocalDate start, LocalDate end) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record EmployeeOption(int userId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record BonusInput(BigDecimal amount, String reason) {
    }
}
