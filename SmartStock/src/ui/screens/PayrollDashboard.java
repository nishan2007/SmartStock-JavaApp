package ui.screens;

import managers.TimeClockManager;
import managers.TimeClockManager.PayrollSummary;
import managers.TimeClockManager.TimeClockRow;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

    private final JLabel totalEmployeesLabel;
    private final JLabel totalHoursLabel;
    private final JLabel totalPayLabel;
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
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    public PayrollDashboard() {
        setTitle("Payroll Dashboard");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(16, 16));
        setJMenuBar(AppMenuBar.create(this, "PayrollDashboard"));

        JPanel mainPanel = new JPanel(new BorderLayout(16, 16));
        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        mainPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Payroll Dashboard");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setOpaque(false);
        payPeriodBox = new JComboBox<>();
        employeeBox = new JComboBox<>();
        searchField = new JTextField();
        JButton generateCurrentButton = new JButton("Generate Current Payroll");
        JButton markPaidButton = new JButton("Mark Selected Paid");
        JButton refreshButton = new JButton("Refresh");
        JPanel leftFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftFilterPanel.setOpaque(false);
        leftFilterPanel.add(new JLabel("Pay Period:"));
        leftFilterPanel.add(payPeriodBox);
        leftFilterPanel.add(new JLabel("Employee:"));
        leftFilterPanel.add(employeeBox);
        payPeriodBox.setPreferredSize(new Dimension(250, 30));
        employeeBox.setPreferredSize(new Dimension(220, 30));
        searchField.setColumns(22);
        searchField.setPreferredSize(new Dimension(260, 30));
        searchField.setMinimumSize(new Dimension(220, 30));
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(generateCurrentButton);
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

        totalEmployeesLabel = createMetricLabel("Employees: 0");
        totalHoursLabel = createMetricLabel("Hours: 0.00");
        totalPayLabel = createMetricLabel("Total Pay: $0.00");
        JPanel metricsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        metricsPanel.setOpaque(false);
        metricsPanel.add(totalEmployeesLabel);
        metricsPanel.add(totalHoursLabel);
        metricsPanel.add(totalPayLabel);

        summaryModel = new DefaultTableModel(
                new Object[]{"Employee ID", "Employee", "Role", "Pay Period", "Pay Date", "Total Hours", "Total Pay", "Paid", "Paid At", "Paid By", "Pay Type", "Records", "Location"},
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
        summarySorter = new TableRowSorter<>(summaryModel);
        summaryTable.setRowSorter(summarySorter);

        detailModel = new DefaultTableModel(
                new Object[]{"Clock ID", "Employee", "Role", "Date", "Clock In", "Lunch Start", "Lunch End", "Clock Out", "Daily Hours", "Pay Period", "Pay Date", "Location"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable detailTable = new JTable(detailModel);
        detailTable.setRowHeight(28);
        detailSorter = new TableRowSorter<>(detailModel);
        detailTable.setRowSorter(detailSorter);

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Payroll Summary", new JScrollPane(summaryTable));
        tabbedPane.addTab("Time Records", new JScrollPane(detailTable));

        JPanel centerPanel = new JPanel(new BorderLayout(0, 12));
        centerPanel.setOpaque(false);
        centerPanel.add(metricsPanel, BorderLayout.NORTH);
        centerPanel.add(tabbedPane, BorderLayout.CENTER);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
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

    private JLabel createMetricLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setForeground(new Color(31, 41, 55));
        label.setOpaque(true);
        label.setBackground(Color.WHITE);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));
        return label;
    }

    private void loadPayroll() {
        try {
            TimeClockManager.PayrollDashboard dashboard = TimeClockManager.loadPayrollDashboard();
            allRows = dashboard.timeRows();
            allSummaries = dashboard.summaries();
            populatePayPeriods();
            populateEmployees();
            renderTables();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load payroll: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populatePayPeriods() {
        Object selected = payPeriodBox.getSelectedItem();
        String selectedKey = selected instanceof PayPeriodOption option ? option.key() : "ALL";
        updatingPayPeriodOptions = true;
        payPeriodBox.removeAllItems();
        payPeriodBox.addItem(new PayPeriodOption("ALL", "All Pay Periods", null, null, false));
        payPeriodBox.addItem(new PayPeriodOption("CURRENT", "Current Payroll", null, null, true));

        Map<String, PayPeriodOption> options = new LinkedHashMap<>();
        for (PayrollSummary summary : allSummaries) {
            String key = summary.payPeriodStart() + "|" + summary.payPeriodEnd();
            options.putIfAbsent(key, new PayPeriodOption(
                    key,
                    summary.payPeriodStart().format(DATE_FORMAT) + " - " + summary.payPeriodEnd().format(DATE_FORMAT)
                            + "  Paid " + summary.payDate().format(DATE_FORMAT),
                    summary.payPeriodStart(),
                    summary.payPeriodEnd(),
                    false
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
        payPeriodBox.setSelectedIndex(0);
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
                    summary.payDate().format(DATE_FORMAT),
                    formatHours(summary.totalHours()),
                    CURRENCY_FORMAT.format(summary.totalPay()),
                    summary.paid() ? "Paid" : "Unpaid",
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
                    formatPayPeriod(row.payPeriodStart(), row.payPeriodEnd()),
                    row.payDate().format(DATE_FORMAT),
                    row.locationName()
            });
        }

        totalEmployeesLabel.setText("Employees: " + employeeIds.size());
        totalHoursLabel.setText("Hours: " + formatHours(totalHours));
        totalPayLabel.setText("Total Pay: " + CURRENCY_FORMAT.format(totalPay));
        applyFilter();
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
        String message = "Mark payroll as paid for:\n\n"
                + summary.employeeName() + "\n"
                + formatPayPeriod(summary.payPeriodStart(), summary.payPeriodEnd()) + "\n"
                + "Total Pay: " + CURRENCY_FORMAT.format(summary.totalPay());
        if (summary.paid()) {
            message += "\n\nThis pay period is already marked paid. Marking it again will update the paid time and totals.";
        }

        int result = JOptionPane.showConfirmDialog(this, message, "Mark Payroll Paid", JOptionPane.YES_NO_OPTION);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            TimeClockManager.markPayrollPaid(summary);
            loadPayroll();
            JOptionPane.showMessageDialog(this, "Payroll marked as paid.", "Payroll", JOptionPane.INFORMATION_MESSAGE);
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
        if (selectedPeriod != null && selectedPeriod.currentOnly()) {
            LocalDate today = StoreTimeZoneHelper.today();
            return !today.isBefore(start) && !today.isAfter(end);
        }

        return selectedPeriod == null
                || selectedPeriod.start() == null
                || (selectedPeriod.start().equals(start) && selectedPeriod.end().equals(end));
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

    private record PayPeriodOption(String key, String label, LocalDate start, LocalDate end, boolean currentOnly) {
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
}
