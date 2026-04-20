package ui.screens;

import managers.SessionManager;
import managers.TimeClockManager;
import managers.TimeClockManager.ClockState;
import managers.TimeClockManager.ClockStatus;
import managers.TimeClockManager.TimeClockDashboard;
import managers.TimeClockManager.TimeClockException;
import managers.TimeClockManager.TimeClockRow;
import ui.components.AppMenuBar;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class TimeClock extends JFrame {

    private final JLabel statusLabel;
    private final JButton clockInButton;
    private final JButton lunchStartButton;
    private final JButton lunchEndButton;
    private final JButton clockOutButton;
    private final JButton refreshButton;
    private final JTextField searchField;
    private final DefaultTableModel timeClockModel;
    private final JTable timeClockTable;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final boolean canViewAllRecords;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    public TimeClock() {
        setTitle("Time Clock");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(16, 16));
        setJMenuBar(AppMenuBar.create(this, "TimeClock"));

        canViewAllRecords = TimeClockManager.canViewAllRecords();

        JPanel mainPanel = new JPanel(new BorderLayout(16, 16));
        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        mainPanel.setBackground(new Color(245, 247, 250));

        JPanel headerPanel = new JPanel(new BorderLayout(12, 12));
        headerPanel.setOpaque(false);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Employee Time Clock");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JLabel employeeLabel = new JLabel("Employee: " + SessionManager.getCurrentUserDisplayName());
        employeeLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        employeeLabel.setForeground(new Color(75, 85, 99));

        JLabel locationLabel = new JLabel("Location: " + safeText(SessionManager.getCurrentLocationName()));
        locationLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        locationLabel.setForeground(new Color(75, 85, 99));

        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(employeeLabel);
        titlePanel.add(Box.createVerticalStrut(2));
        titlePanel.add(locationLabel);

        statusLabel = new JLabel("Status: Loading");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel.setForeground(new Color(37, 99, 235));
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(209, 213, 219), 1),
                new EmptyBorder(12, 14, 12, 14)
        ));
        statusLabel.setOpaque(true);
        statusLabel.setBackground(Color.WHITE);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(statusLabel, BorderLayout.EAST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionPanel.setOpaque(false);
        clockInButton = createActionButton("Clock In", new Color(22, 163, 74));
        lunchStartButton = createActionButton("Lunch Start", new Color(217, 119, 6));
        lunchEndButton = createActionButton("Lunch End", new Color(37, 99, 235));
        clockOutButton = createActionButton("Clock Out", new Color(220, 38, 38));
        refreshButton = new JButton("Refresh");
        refreshButton.setPreferredSize(new Dimension(120, 42));
        actionPanel.add(clockInButton);
        actionPanel.add(lunchStartButton);
        actionPanel.add(lunchEndButton);
        actionPanel.add(clockOutButton);
        actionPanel.add(refreshButton);

        JPanel topPanel = new JPanel(new BorderLayout(0, 14));
        topPanel.setOpaque(false);
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(actionPanel, BorderLayout.SOUTH);

        JPanel tablePanel = new JPanel(new BorderLayout(8, 8));
        tablePanel.setOpaque(false);

        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
        filterPanel.setOpaque(false);
        searchField = new JTextField();
        filterPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterPanel.add(searchField, BorderLayout.CENTER);

        timeClockModel = new DefaultTableModel(
                new Object[]{
                        "Clock ID",
                        "Employee ID",
                        "Name",
                        "Date",
                        "Clock In",
                        "Lunch Start",
                        "Lunch End",
                        "Clock Out",
                        "Daily Hours",
                        "Pay Period",
                        "Pay Date",
                        "Total Hours",
                        "Pay Type",
                        "Hourly Wage",
                        "Salary",
                        "Day Salary",
                        "Total Pay",
                        "Location"
                },
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        timeClockTable = new JTable(timeClockModel);
        timeClockTable.setRowHeight(30);
        timeClockTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        sorter = new TableRowSorter<>(timeClockModel);
        timeClockTable.setRowSorter(sorter);
        configureColumns();

        tablePanel.add(filterPanel, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(timeClockTable), BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(tablePanel, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        wireActions();
        loadTimeClock();
        WindowHelper.configurePosWindow(this);
    }

    private JButton createActionButton(String text, Color color) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(130, 42));
        return button;
    }

    private void configureColumns() {
        int[] widths = {80, 95, 180, 105, 105, 105, 105, 105, 105, 180, 105, 105, 95, 105, 105, 105, 105, 180};
        for (int i = 0; i < widths.length; i++) {
            timeClockTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private void wireActions() {
        clockInButton.addActionListener(e -> runPunch(TimeClockManager::clockIn, "Failed to clock in."));
        lunchStartButton.addActionListener(e -> runPunch(TimeClockManager::lunchStart, "Failed to punch lunch start."));
        lunchEndButton.addActionListener(e -> runPunch(TimeClockManager::lunchEnd, "Failed to punch lunch end."));
        clockOutButton.addActionListener(e -> runPunch(TimeClockManager::clockOut, "Failed to clock out."));
        refreshButton.addActionListener(e -> loadTimeClock());
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
    }

    private void runPunch(PunchAction action, String databaseErrorMessage) {
        try {
            action.run();
            loadTimeClock();
        } catch (TimeClockException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
            loadTimeClock();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, databaseErrorMessage + "\n\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
        }
    }

    private void loadTimeClock() {
        try {
            TimeClockDashboard dashboard = TimeClockManager.loadDashboard(canViewAllRecords);
            renderRows(dashboard.rows());
            updateClockStatus(dashboard.status());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load time clock: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            updateButtons(false, false, false, false);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
            updateButtons(false, false, false, false);
        }
    }

    private void renderRows(List<TimeClockRow> rows) {
        timeClockModel.setRowCount(0);

        for (TimeClockRow row : rows) {
            timeClockModel.addRow(new Object[]{
                    row.clockId(),
                    row.userId(),
                    row.employeeName(),
                    row.workDate().format(DATE_FORMAT),
                    formatTime(row.clockIn()),
                    formatTime(row.lunchStart()),
                    formatTime(row.lunchEnd()),
                    formatTime(row.clockOut()),
                    formatHours(row.dailyHours()),
                    row.payPeriodStart().format(DATE_FORMAT) + " - " + row.payPeriodEnd().format(DATE_FORMAT),
                    row.payDate().format(DATE_FORMAT),
                    formatHours(row.totalHours()),
                    formatCompensationType(row.compensationType()),
                    CURRENCY_FORMAT.format(row.hourlyWage()),
                    CURRENCY_FORMAT.format(row.salaryAmount()),
                    CURRENCY_FORMAT.format(row.dailySalary()),
                    CURRENCY_FORMAT.format(row.totalPay()),
                    row.locationName()
            });
        }
    }

    private void updateClockStatus(ClockStatus status) {
        statusLabel.setText("Status: " + statusText(status.state()));
        updateButtons(status.canClockIn(), status.canLunchStart(), status.canLunchEnd(), status.canClockOut());
    }

    private String statusText(ClockState state) {
        return switch (state) {
            case NOT_CLOCKED_IN -> "Not clocked in";
            case CLOCKED_IN -> "Clocked in";
            case ON_LUNCH -> "On lunch";
            case CLOCKED_OUT -> "Clocked out today";
        };
    }

    private void updateButtons(boolean canClockIn, boolean canLunchStart, boolean canLunchEnd, boolean canClockOut) {
        clockInButton.setEnabled(canClockIn);
        lunchStartButton.setEnabled(canLunchStart);
        lunchEndButton.setEnabled(canLunchEnd);
        clockOutButton.setEnabled(canClockOut);
    }

    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text)));
        }
    }

    private static String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMAT);
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

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    @FunctionalInterface
    private interface PunchAction {
        void run() throws SQLException, TimeClockException;
    }
}
