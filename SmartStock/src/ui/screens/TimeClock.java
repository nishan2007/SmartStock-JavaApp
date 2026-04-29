package ui.screens;

import managers.PermissionManager;
import managers.SessionManager;
import managers.TimeClockManager;
import managers.TimeClockManager.ClockState;
import managers.TimeClockManager.ClockStatus;
import managers.TimeClockManager.TimeClockDashboard;
import managers.TimeClockManager.TimeClockException;
import managers.TimeClockManager.TimeClockRow;
import ui.components.AppMenuBar;
import ui.helpers.ThemeManager;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class TimeClock extends JFrame {

    private JLabel statusLabel;
    private JButton clockInButton;
    private JButton lunchStartButton;
    private JButton lunchEndButton;
    private JButton clockOutButton;
    private JButton refreshButton;
    private JButton prevMonthButton;
    private JButton nextMonthButton;
    private JButton todayButton;
    private JLabel monthYearLabel;
    private JPanel calendarPanel;
    private JPanel detailsPanel;
    private DefaultTableModel detailsModel;
    private JTable detailsTable;
    private JPanel currentSessionPanel;
    private JLabel sessionTimeLabel;
    private JLabel sessionClockInLabel;
    private JLabel sessionLunchStartLabel;
    private JLabel sessionLunchEndLabel;
    private JLabel sessionClockOutLabel;
    private JLabel monthHoursLabel;
    private JLabel monthPayLabel;
    private JLabel monthDaysLabel;
    private javax.swing.Timer sessionTimer;
    private javax.swing.Timer pulseTimer;

    private final Color clockInColor = new Color(22, 163, 74);
    private final Color lunchStartColor = new Color(217, 119, 6);
    private final Color lunchEndColor = new Color(37, 99, 235);
    private final Color clockOutColor = new Color(220, 38, 38);

    private YearMonth currentMonth;
    private List<TimeClockRow> allRows = new ArrayList<>();
    private Map<LocalDate, DayData> dayDataMap = new HashMap<>();
    private LocalDateTime currentClockIn;
    private LocalDateTime currentLunchStart;
    private LocalDateTime currentLunchEnd;
    private boolean isCurrentlyWorking = false;
    private float pulseAlpha = 1.0f;
    private boolean pulseIncreasing = false;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    public TimeClock() {
        setTitle("Time Clock");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(16, 16));
        setJMenuBar(AppMenuBar.create(this, "TimeClock"));

        if (!PermissionManager.hasPermission("TIME_CLOCK")) {
            JOptionPane.showMessageDialog(this,
                "You do not have permission to access the Time Clock.",
                "Access Denied",
                JOptionPane.WARNING_MESSAGE);
            dispose();
            return;
        }

        currentMonth = YearMonth.now();

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
        preserveForeground(titleLabel);

        JLabel employeeLabel = new JLabel("Employee: " + SessionManager.getCurrentUserDisplayName());
        employeeLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        employeeLabel.setForeground(new Color(75, 85, 99));
        preserveForeground(employeeLabel);

        JLabel locationLabel = new JLabel("Location: " + safeText(SessionManager.getCurrentLocationName()));
        locationLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        locationLabel.setForeground(new Color(75, 85, 99));
        preserveForeground(locationLabel);

        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(employeeLabel);
        titlePanel.add(Box.createVerticalStrut(2));
        titlePanel.add(locationLabel);

        // Current Session Panel with rounded corners
        currentSessionPanel = new RoundedPanel(12);
        currentSessionPanel.setLayout(new BoxLayout(currentSessionPanel, BoxLayout.Y_AXIS));
        currentSessionPanel.setBackground(new Color(254, 249, 195));
        preserveBackground(currentSessionPanel);
        currentSessionPanel.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(250, 204, 21), 2, 12),
                new EmptyBorder(8, 12, 8, 12)
        ));
        currentSessionPanel.setVisible(false);

        JLabel sessionTitle = new JLabel("Current Session");
        sessionTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        sessionTitle.setForeground(new Color(113, 63, 18));
        preserveForeground(sessionTitle);
        sessionTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        sessionTimeLabel = new JLabel("Session Time: 0:00:00");
        sessionTimeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        sessionTimeLabel.setForeground(new Color(113, 63, 18));
        preserveForeground(sessionTimeLabel);
        sessionTimeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel sessionDetailsGrid = new JPanel(new GridLayout(2, 2, 8, 4));
        sessionDetailsGrid.setOpaque(false);

        sessionClockInLabel = createSessionDetailLabel("Clock In: --");
        sessionLunchStartLabel = createSessionDetailLabel("Lunch Start: --");
        sessionLunchEndLabel = createSessionDetailLabel("Lunch End: --");
        sessionClockOutLabel = createSessionDetailLabel("Clock Out: --");

        sessionDetailsGrid.add(sessionClockInLabel);
        sessionDetailsGrid.add(sessionLunchStartLabel);
        sessionDetailsGrid.add(sessionLunchEndLabel);
        sessionDetailsGrid.add(sessionClockOutLabel);

        currentSessionPanel.add(sessionTitle);
        currentSessionPanel.add(Box.createVerticalStrut(4));
        currentSessionPanel.add(sessionTimeLabel);
        currentSessionPanel.add(Box.createVerticalStrut(6));
        currentSessionPanel.add(sessionDetailsGrid);

        statusLabel = new JLabel("Status: Loading");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        statusLabel.setForeground(new Color(37, 99, 235));
        preserveForeground(statusLabel);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(209, 213, 219), 1, 8),
                new EmptyBorder(12, 14, 12, 14)
        ));
        statusLabel.setOpaque(false);

        headerPanel.add(titlePanel, BorderLayout.WEST);
        headerPanel.add(currentSessionPanel, BorderLayout.CENTER);
        headerPanel.add(statusLabel, BorderLayout.EAST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionPanel.setOpaque(false);
        clockInButton = createActionButton("Clock In", clockInColor);
        lunchStartButton = createActionButton("Lunch Start", lunchStartColor);
        lunchEndButton = createActionButton("Lunch End", lunchEndColor);
        clockOutButton = createActionButton("Clock Out", clockOutColor);
        refreshButton = createStyledButton("Refresh", new Color(107, 114, 128));
        actionPanel.add(clockInButton);
        actionPanel.add(lunchStartButton);
        actionPanel.add(lunchEndButton);
        actionPanel.add(clockOutButton);
        actionPanel.add(refreshButton);

        JPanel topPanel = new JPanel(new BorderLayout(0, 14));
        topPanel.setOpaque(false);
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(actionPanel, BorderLayout.SOUTH);

        // Calendar navigation panel with Today button
        JPanel calendarNavPanel = new JPanel(new BorderLayout());
        calendarNavPanel.setOpaque(false);
        calendarNavPanel.setBorder(new EmptyBorder(0, 0, 12, 0));

        prevMonthButton = createNavButton("←");
        nextMonthButton = createNavButton("→");
        todayButton = createStyledButton("Today", new Color(59, 130, 246));
        todayButton.setPreferredSize(new Dimension(100, 32));

        monthYearLabel = new JLabel(currentMonth.format(MONTH_YEAR_FORMAT), SwingConstants.CENTER);
        monthYearLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        monthYearLabel.setForeground(new Color(31, 41, 55));
        preserveForeground(monthYearLabel);

        JPanel navButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        navButtonPanel.setOpaque(false);
        navButtonPanel.add(prevMonthButton);
        navButtonPanel.add(nextMonthButton);
        navButtonPanel.add(todayButton);

        calendarNavPanel.add(navButtonPanel, BorderLayout.WEST);
        calendarNavPanel.add(monthYearLabel, BorderLayout.CENTER);

        // Calendar panel
        calendarPanel = new JPanel(new GridLayout(0, 7, 3, 3));
        calendarPanel.setOpaque(false);

        // Monthly stats panel
        JPanel monthStatsPanel = new JPanel(new GridLayout(1, 3, 12, 0));
        monthStatsPanel.setOpaque(false);
        monthStatsPanel.setBorder(new EmptyBorder(12, 0, 0, 0));

        monthDaysLabel = createStatCard("Days Worked", "0");
        monthHoursLabel = createStatCard("Total Hours", "0.00");
        monthPayLabel = createStatCard("Total Pay", "$0.00");

        monthStatsPanel.add(monthDaysLabel);
        monthStatsPanel.add(monthHoursLabel);
        monthStatsPanel.add(monthPayLabel);

        // Calendar container with navigation and stats
        JPanel calendarContainer = new JPanel(new BorderLayout(0, 8));
        calendarContainer.setOpaque(false);
        calendarContainer.setPreferredSize(new Dimension(600, 0));

        RoundedPanel calendarCard = new RoundedPanel(12);
        calendarCard.setLayout(new BorderLayout(0, 8));
        calendarCard.setBackground(Color.WHITE);
        preserveBackground(calendarCard);
        calendarCard.setBorder(new EmptyBorder(16, 16, 16, 16));
        calendarCard.add(calendarNavPanel, BorderLayout.NORTH);
        calendarCard.add(calendarPanel, BorderLayout.CENTER);
        calendarCard.add(monthStatsPanel, BorderLayout.SOUTH);

        calendarContainer.add(calendarCard, BorderLayout.CENTER);

        // Details panel with card styling
        RoundedPanel detailsCard = new RoundedPanel(12);
        detailsCard.setLayout(new BorderLayout(0, 12));
        detailsCard.setBackground(Color.WHITE);
        preserveBackground(detailsCard);
        detailsCard.setBorder(new EmptyBorder(16, 16, 16, 16));

        JLabel detailsTitle = new JLabel("Time Clock Details");
        detailsTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        detailsTitle.setForeground(new Color(31, 41, 55));
        preserveForeground(detailsTitle);

        detailsModel = new DefaultTableModel(
                new Object[]{"Date", "Clock In", "Lunch Start", "Lunch End", "Clock Out", "Hours", "Pay", "Type", "Location"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        detailsTable = new JTable(detailsModel);
        detailsTable.setRowHeight(32);
        detailsTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        detailsTable.setShowGrid(false);
        detailsTable.setIntercellSpacing(new Dimension(0, 0));
        detailsTable.setSelectionBackground(new Color(219, 234, 254));
        detailsTable.setSelectionForeground(new Color(30, 64, 175));

        // Alternating row colors
        detailsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(249, 250, 251));
                }
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return c;
            }
        });

        JScrollPane detailsScroll = new JScrollPane(detailsTable);
        detailsScroll.setBorder(null);

        detailsCard.add(detailsTitle, BorderLayout.NORTH);
        detailsCard.add(detailsScroll, BorderLayout.CENTER);

        detailsPanel = detailsCard;

        // Split layout: Calendar on left, Details on right
        JPanel calendarAndDetailsPanel = new JPanel(new BorderLayout(16, 0));
        calendarAndDetailsPanel.setOpaque(false);
        calendarAndDetailsPanel.add(calendarContainer, BorderLayout.WEST);
        calendarAndDetailsPanel.add(detailsPanel, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(calendarAndDetailsPanel, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        wireActions();
        loadTimeClock();
        WindowHelper.configurePosWindow(this);
    }

    private JButton createActionButton(String text, Color color) {
        JButton button = new RoundedButton(text, 8);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setPreferredSize(new Dimension(130, 42));
        return button;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new RoundedButton(text, 8);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setPreferredSize(new Dimension(120, 42));
        return button;
    }

    private JButton createNavButton(String text) {
        JButton button = new RoundedButton(text, 6);
        button.setFont(new Font("SansSerif", Font.BOLD, 18));
        button.setForeground(new Color(75, 85, 99));
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(209, 213, 219), 1, 6),
                new EmptyBorder(4, 12, 4, 12)
        ));
        button.setPreferredSize(new Dimension(50, 32));
        return button;
    }

    private JLabel createSessionDetailLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        label.setForeground(new Color(120, 53, 15));
        preserveForeground(label);
        return label;
    }

    private JLabel createStatCard(String title, String value) {
        JPanel card = new RoundedPanel(8);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(249, 250, 251));
        preserveBackground(card);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleLabel.setForeground(new Color(107, 114, 128));
        preserveForeground(titleLabel);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        valueLabel.setForeground(new Color(31, 41, 55));
        preserveForeground(valueLabel);
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(titleLabel);
        card.add(Box.createVerticalStrut(4));
        card.add(valueLabel);

        JLabel wrapper = new JLabel();
        wrapper.setLayout(new BorderLayout());
        wrapper.add(card, BorderLayout.CENTER);

        return wrapper;
    }

    private void wireActions() {
        clockInButton.addActionListener(e -> runPunch(TimeClockManager::clockIn, "Failed to clock in."));
        lunchStartButton.addActionListener(e -> runPunch(TimeClockManager::lunchStart, "Failed to punch lunch start."));
        lunchEndButton.addActionListener(e -> runPunch(TimeClockManager::lunchEnd, "Failed to punch lunch end."));
        clockOutButton.addActionListener(e -> runPunch(TimeClockManager::clockOut, "Failed to clock out."));
        refreshButton.addActionListener(e -> loadTimeClock());
        prevMonthButton.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1);
            renderCalendar();
        });
        nextMonthButton.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1);
            renderCalendar();
        });
        todayButton.addActionListener(e -> {
            currentMonth = YearMonth.now();
            renderCalendar();
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
            TimeClockDashboard dashboard = TimeClockManager.loadDashboard(false);
            allRows = dashboard.rows();
            updateClockStatus(dashboard.status());
            updateCurrentSession();
            processDayData();
            renderCalendar();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load time clock: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            updateButtons(false, false, false, false);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage());
            updateButtons(false, false, false, false);
        }
    }

    private void updateCurrentSession() {
        LocalDate today = LocalDate.now();
        TimeClockRow currentRecord = null;

        for (TimeClockRow row : allRows) {
            if (row.workDate().equals(today) && row.clockOut() == null) {
                currentRecord = row;
                break;
            }
        }

        if (currentRecord != null) {
            isCurrentlyWorking = true;
            currentClockIn = currentRecord.clockIn();
            currentLunchStart = currentRecord.lunchStart();
            currentLunchEnd = currentRecord.lunchEnd();

            sessionClockInLabel.setText("Clock In: " + formatTime(currentClockIn));
            sessionLunchStartLabel.setText("Lunch Start: " + (currentLunchStart != null ? formatTime(currentLunchStart) : "--"));
            sessionLunchEndLabel.setText("Lunch End: " + (currentLunchEnd != null ? formatTime(currentLunchEnd) : "--"));
            sessionClockOutLabel.setText("Clock Out: --");

            currentSessionPanel.setVisible(true);
            startSessionTimer();
            startPulseAnimation();
        } else {
            isCurrentlyWorking = false;
            currentSessionPanel.setVisible(false);
            stopSessionTimer();
            stopPulseAnimation();
        }
    }

    private void startSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
        }

        sessionTimer = new javax.swing.Timer(1000, e -> updateSessionTime());
        sessionTimer.start();
        updateSessionTime();
    }

    private void stopSessionTimer() {
        if (sessionTimer != null) {
            sessionTimer.stop();
            sessionTimer = null;
        }
    }

    private void startPulseAnimation() {
        if (pulseTimer != null) {
            pulseTimer.stop();
        }

        pulseTimer = new javax.swing.Timer(50, e -> {
            if (pulseIncreasing) {
                pulseAlpha += 0.05f;
                if (pulseAlpha >= 1.0f) {
                    pulseAlpha = 1.0f;
                    pulseIncreasing = false;
                }
            } else {
                pulseAlpha -= 0.05f;
                if (pulseAlpha <= 0.6f) {
                    pulseAlpha = 0.6f;
                    pulseIncreasing = true;
                }
            }
            sessionTimeLabel.setForeground(new Color(113, 63, 18, (int)(255 * pulseAlpha)));
        });
        pulseTimer.start();
    }

    private void stopPulseAnimation() {
        if (pulseTimer != null) {
            pulseTimer.stop();
            pulseTimer = null;
        }
        pulseAlpha = 1.0f;
    }

    private void updateSessionTime() {
        if (!isCurrentlyWorking || currentClockIn == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long totalSeconds = java.time.Duration.between(currentClockIn, now).getSeconds();

        if (currentLunchStart != null) {
            LocalDateTime lunchEnd = currentLunchEnd != null ? currentLunchEnd : now;
            long lunchSeconds = java.time.Duration.between(currentLunchStart, lunchEnd).getSeconds();
            totalSeconds -= lunchSeconds;
        }

        if (totalSeconds < 0) {
            totalSeconds = 0;
        }

        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        sessionTimeLabel.setText(String.format("Session Time: %d:%02d:%02d", hours, minutes, seconds));
    }

    private void processDayData() {
        dayDataMap.clear();

        for (TimeClockRow row : allRows) {
            LocalDate date = row.workDate();
            DayData dayData = dayDataMap.computeIfAbsent(date, k -> new DayData());
            dayData.rows.add(row);
            dayData.totalHours = dayData.totalHours.add(row.dailyHours());
            dayData.totalPay = dayData.totalPay.add(row.totalPay());
        }
    }

    private void renderCalendar() {
        calendarPanel.removeAll();
        monthYearLabel.setText(currentMonth.format(MONTH_YEAR_FORMAT));

        // Add day headers
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < dayNames.length; i++) {
            JLabel dayLabel = new JLabel(dayNames[i], SwingConstants.CENTER);
            dayLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            dayLabel.setForeground(new Color(107, 114, 128));
            preserveForeground(dayLabel);
            dayLabel.setBorder(new EmptyBorder(4, 0, 4, 0));

            // Highlight weekends
            if (i == 0 || i == 6) {
                dayLabel.setForeground(new Color(239, 68, 68));
            }

            calendarPanel.add(dayLabel);
        }

        LocalDate firstOfMonth = currentMonth.atDay(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7;
        int daysInMonth = currentMonth.lengthOfMonth();

        // Add empty cells before first day
        for (int i = 0; i < dayOfWeek; i++) {
            calendarPanel.add(new JLabel(""));
        }

        // Calculate monthly stats
        BigDecimal monthHours = BigDecimal.ZERO;
        BigDecimal monthPay = BigDecimal.ZERO;
        int monthDays = 0;

        // Add day cells
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = currentMonth.atDay(day);
            DayData dayData = dayDataMap.get(date);

            if (dayData != null) {
                monthHours = monthHours.add(dayData.totalHours);
                monthPay = monthPay.add(dayData.totalPay);
                monthDays++;
            }

            JPanel dayCell = createDayCell(date, dayData);
            calendarPanel.add(dayCell);
        }

        // Update monthly stats
        updateStatCard(monthDaysLabel, String.valueOf(monthDays));
        updateStatCard(monthHoursLabel, formatHours(monthHours));
        updateStatCard(monthPayLabel, CURRENCY_FORMAT.format(monthPay));

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    private void updateStatCard(JLabel wrapper, String value) {
        Component card = wrapper.getComponent(0);
        if (card instanceof JPanel) {
            JPanel panel = (JPanel) card;
            if (panel.getComponentCount() >= 3) {
                Component valueComp = panel.getComponent(2);
                if (valueComp instanceof JLabel) {
                    ((JLabel) valueComp).setText(value);
                }
            }
        }
    }

    private JPanel createDayCell(LocalDate date, DayData dayData) {
        RoundedPanel cell = new RoundedPanel(8);
        cell.setLayout(new BorderLayout());
        cell.setBorder(new EmptyBorder(6, 4, 6, 4));
        cell.setPreferredSize(new Dimension(80, 65));
        preserveBackground(cell);

        boolean isToday = date.equals(LocalDate.now());
        boolean hasData = dayData != null && !dayData.rows.isEmpty();
        boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;

        // Background colors
        if (isToday && hasData) {
            cell.setBackground(new Color(187, 247, 208)); // Bright green for today with data
        } else if (isToday) {
            cell.setBackground(new Color(219, 234, 254)); // Light blue for today
        } else if (hasData) {
            cell.setBackground(new Color(240, 253, 244)); // Light green for work days
        } else if (isWeekend) {
            cell.setBackground(new Color(254, 242, 242)); // Light red for weekends
        } else {
            cell.setBackground(Color.WHITE);
        }

        JLabel dayNumber = new JLabel(String.valueOf(date.getDayOfMonth()), SwingConstants.CENTER);
        dayNumber.setFont(new Font("SansSerif", isToday ? Font.BOLD : Font.PLAIN, 14));
        dayNumber.setForeground(isToday ? new Color(37, 99, 235) : new Color(31, 41, 55));
        preserveForeground(dayNumber);
        dayNumber.setBorder(new EmptyBorder(2, 0, 2, 0));

        if (hasData) {
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel hoursLabel = new JLabel(formatHours(dayData.totalHours) + " hrs", SwingConstants.CENTER);
            hoursLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            hoursLabel.setForeground(new Color(75, 85, 99));
            preserveForeground(hoursLabel);

            // Multiple entries indicator
            JLabel countLabel = null;
            if (dayData.rows.size() > 1) {
                countLabel = new JLabel("●".repeat(Math.min(dayData.rows.size(), 3)), SwingConstants.CENTER);
                countLabel.setFont(new Font("SansSerif", Font.PLAIN, 8));
                countLabel.setForeground(new Color(59, 130, 246));
                preserveForeground(countLabel);
            }

            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setOpaque(false);
            infoPanel.add(dayNumber);
            infoPanel.add(hoursLabel);
            if (countLabel != null) {
                infoPanel.add(countLabel);
            }

            cell.add(infoPanel, BorderLayout.CENTER);

            // Enhanced tooltip
            String tooltip = String.format(
                "<html><div style='padding:5px;'>" +
                "<b>%s</b><br>" +
                "Hours: <b>%s</b><br>" +
                "Pay: <b>%s</b><br>" +
                "Entries: <b>%d</b>" +
                "</div></html>",
                date.format(DATE_FORMAT),
                formatHours(dayData.totalHours),
                CURRENCY_FORMAT.format(dayData.totalPay),
                dayData.rows.size()
            );
            cell.setToolTipText(tooltip);

            // Click handler
            Color originalBg = cell.getBackground();
            cell.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showDayDetails(date, dayData);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    cell.setBackground(adjustBrightness(originalBg, 0.9f));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    cell.setBackground(originalBg);
                }
            });
        } else {
            cell.add(dayNumber, BorderLayout.NORTH);
        }

        return cell;
    }

    private Color adjustBrightness(Color color, float factor) {
        int r = Math.min(255, (int)(color.getRed() * factor));
        int g = Math.min(255, (int)(color.getGreen() * factor));
        int b = Math.min(255, (int)(color.getBlue() * factor));
        return new Color(r, g, b);
    }

    private void showDayDetails(LocalDate date, DayData dayData) {
        detailsModel.setRowCount(0);

        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalPay = BigDecimal.ZERO;

        for (TimeClockRow row : dayData.rows) {
            BigDecimal dayPay = row.totalPay();

            totalHours = totalHours.add(row.dailyHours());
            totalPay = totalPay.add(dayPay);

            String payType = switch (row.compensationType().toUpperCase()) {
                case "HOURLY" -> "Hourly";
                case "SALARY" -> "Salary";
                case "DAILY" -> "Daily";
                default -> "";
            };

            detailsModel.addRow(new Object[]{
                    row.workDate().format(DATE_FORMAT),
                    formatTime(row.clockIn()),
                    formatTime(row.lunchStart()),
                    formatTime(row.lunchEnd()),
                    formatTime(row.clockOut()),
                    formatHours(row.dailyHours()),
                    formatPay(row),
                    payType,
                    row.locationName()
            });
        }

        // Add summary row
        if (dayData.rows.size() > 1) {
            detailsModel.addRow(new Object[]{
                    "TOTAL",
                    "",
                    "",
                    "",
                    "",
                    formatHours(totalHours),
                    formatTotalPay(dayData.rows, totalPay),
                    "",
                    ""
            });
        }
    }

    private void updateClockStatus(ClockStatus status) {
        statusLabel.setText("Status: " + statusText(status.state()));
        updateStatusLabelColors(status.state());
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

    private void updateStatusLabelColors(ClockState state) {
        boolean dark = ThemeManager.isDarkModeEnabled();

        Color backgroundColor;
        Color textColor;
        Color borderColor;

        switch (state) {
            case CLOCKED_IN -> {
                backgroundColor = dark ? new Color(20, 83, 45) : new Color(240, 253, 244);
                textColor = dark ? new Color(134, 239, 172) : new Color(21, 128, 61);
                borderColor = dark ? new Color(34, 197, 94) : new Color(134, 239, 172);
            }
            case ON_LUNCH -> {
                backgroundColor = dark ? new Color(120, 53, 15) : new Color(254, 249, 195);
                textColor = dark ? new Color(253, 224, 71) : new Color(161, 98, 7);
                borderColor = dark ? new Color(250, 204, 21) : new Color(253, 224, 71);
            }
            case CLOCKED_OUT -> {
                backgroundColor = dark ? new Color(55, 65, 81) : new Color(243, 244, 246);
                textColor = dark ? new Color(209, 213, 219) : new Color(75, 85, 99);
                borderColor = dark ? new Color(107, 114, 128) : new Color(209, 213, 219);
            }
            default -> {
                backgroundColor = dark ? new Color(30, 58, 138) : new Color(239, 246, 255);
                textColor = dark ? new Color(147, 197, 253) : new Color(29, 78, 216);
                borderColor = dark ? new Color(59, 130, 246) : new Color(147, 197, 253);
            }
        }

        if (statusLabel instanceof JLabel) {
            JLabel label = (JLabel) statusLabel;
            label.setForeground(textColor);
            label.setBorder(BorderFactory.createCompoundBorder(
                    new RoundedBorder(borderColor, 1, 8),
                    new EmptyBorder(12, 14, 12, 14)
            ));
        }

        // Set background by creating a custom panel
        statusLabel.setOpaque(true);
        statusLabel.setBackground(backgroundColor);
    }

    private void updateButtons(boolean canClockIn, boolean canLunchStart, boolean canLunchEnd, boolean canClockOut) {
        setActionButtonState(clockInButton, clockInColor, canClockIn);
        setActionButtonState(lunchStartButton, lunchStartColor, canLunchStart);
        setActionButtonState(lunchEndButton, lunchEndColor, canLunchEnd);
        setActionButtonState(clockOutButton, clockOutColor, canClockOut);
    }

    private void setActionButtonState(JButton button, Color enabledColor, boolean enabled) {
        boolean dark = ThemeManager.isDarkModeEnabled();
        Color disabledBackground = mutedButtonColor(enabledColor, dark);
        Color disabledText = dark ? new Color(225, 225, 225) : new Color(75, 85, 99);

        button.setEnabled(enabled);
        button.setBackground(enabled ? enabledColor : disabledBackground);
        button.setForeground(enabled ? Color.WHITE : disabledText);
    }

    private Color mutedButtonColor(Color color, boolean dark) {
        Color base = dark ? new Color(42, 42, 42) : new Color(229, 231, 235);
        double colorWeight = dark ? 0.46 : 0.28;
        int red = (int) Math.round((color.getRed() * colorWeight) + (base.getRed() * (1 - colorWeight)));
        int green = (int) Math.round((color.getGreen() * colorWeight) + (base.getGreen() * (1 - colorWeight)));
        int blue = (int) Math.round((color.getBlue() * colorWeight) + (base.getBlue() * (1 - colorWeight)));
        return new Color(red, green, blue);
    }

    private static String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(TIME_FORMAT);
    }

    private static String formatHours(BigDecimal hours) {
        return hours.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatPay(TimeClockRow row) {
        if ("SALARY".equalsIgnoreCase(row.compensationType())) {
            return "Salary";
        }
        if ("DAILY".equalsIgnoreCase(row.compensationType())
                && row.totalPay().compareTo(BigDecimal.ZERO) == 0) {
            return "";
        }
        return CURRENCY_FORMAT.format(row.totalPay());
    }

    private static String formatTotalPay(List<TimeClockRow> rows, BigDecimal totalPay) {
        boolean allSalary = !rows.isEmpty()
                && rows.stream().allMatch(row -> "SALARY".equalsIgnoreCase(row.compensationType()));
        return allSalary ? "Salary" : CURRENCY_FORMAT.format(totalPay);
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private static void preserveForeground(JComponent component) {
        component.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
    }

    private static void preserveBackground(JComponent component) {
        component.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
    }

    private static class DayData {
        List<TimeClockRow> rows = new ArrayList<>();
        BigDecimal totalHours = BigDecimal.ZERO;
        BigDecimal totalPay = BigDecimal.ZERO;
    }

    @FunctionalInterface
    private interface PunchAction {
        void run() throws SQLException, TimeClockException;
    }

    // Custom rounded panel
    private static class RoundedPanel extends JPanel {
        private final int radius;

        public RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Custom rounded button
    private static class RoundedButton extends JButton {
        private final int radius;

        public RoundedButton(String text, int radius) {
            super(text);
            this.radius = radius;
            putClientProperty("SmartStock.customPaintedButton", Boolean.TRUE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (getModel().isPressed()) {
                g2.setColor(adjustBrightness(getBackground(), 0.9f));
            } else if (getModel().isRollover()) {
                g2.setColor(adjustBrightness(getBackground(), 1.1f));
            } else {
                g2.setColor(getBackground());
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }

        private static Color adjustBrightness(Color color, float factor) {
            int r = Math.min(255, (int)(color.getRed() * factor));
            int g = Math.min(255, (int)(color.getGreen() * factor));
            int b = Math.min(255, (int)(color.getBlue() * factor));
            return new Color(r, g, b);
        }
    }

    // Custom rounded border
    private static class RoundedBorder extends LineBorder {
        private final int radius;

        public RoundedBorder(Color color, int thickness, int radius) {
            super(color, thickness, true);
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getLineColor());
            g2.setStroke(new BasicStroke(getThickness()));
            g2.draw(new RoundRectangle2D.Double(x + getThickness() / 2.0, y + getThickness() / 2.0,
                    width - getThickness(), height - getThickness(), radius, radius));
            g2.dispose();
        }
    }
}
