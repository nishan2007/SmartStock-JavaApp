package ui.screens;

import utils.CurrencyFormatter;
import managers.PermissionManager;
import managers.SessionManager;
import managers.TimeClockManager;
import managers.TimeClockManager.ClockState;
import managers.TimeClockManager.ClockStatus;
import managers.TimeClockManager.TimeClockDashboard;
import managers.TimeClockManager.TimeClockException;
import managers.TimeClockManager.TimeClockRow;
import services.ManagerApprovalService;
import services.EmployeeScheduleService;
import services.EmployeeScheduleService.Holiday;
import services.TimeClockAutoCloseService;
import services.TimeClockAutoCloseService.EmployeeAutoCloseNotice;
import services.TimeClockAutoCloseService.PendingReview;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.Border;
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
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    private JLabel statusLabel;
    private JButton clockInButton;
    private JButton lunchStartButton;
    private JButton lunchEndButton;
    private JButton clockOutButton;
    private JButton refreshButton;
    private JButton autoClockOutReviewsButton;
    private JLabel autoClockOutNoticeLabel;
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

    private static final Color EMPLOYEE_ACCENT = DeckersPalette.YELLOW;

    private final Color clockInColor = DeckersPalette.LIME;
    private final Color lunchStartColor = DeckersPalette.ORANGE;
    private final Color lunchEndColor = DeckersPalette.MAGENTA;
    private final Color clockOutColor = DeckersPalette.CORAL;

    private YearMonth currentMonth;
    private List<TimeClockRow> allRows = new ArrayList<>();
    private Map<LocalDate, DayData> dayDataMap = new HashMap<>();
    private Map<LocalDate, Holiday> holidays = Map.of();
    private LocalDateTime currentClockIn;
    private LocalDateTime currentLunchStart;
    private LocalDateTime currentLunchEnd;
    private boolean isCurrentlyWorking = false;
    private float pulseAlpha = 1.0f;
    private boolean pulseIncreasing = false;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy");
    private static final DateTimeFormatter CORRECTION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat CURRENCY_FORMAT = CurrencyFormatter.create(Locale.US);

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
        mainPanel.setBackground(DeckersPalette.background());

        JPanel headerPanel = new JPanel(new BorderLayout(12, 12));
        headerPanel.setOpaque(false);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Employee Time Clock");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(DeckersPalette.text());
        preserveForeground(titleLabel);

        JLabel employeeLabel = new JLabel("Employee: " + SessionManager.getCurrentUserDisplayName());
        employeeLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        employeeLabel.setForeground(DeckersPalette.muted());
        preserveForeground(employeeLabel);

        JLabel locationLabel = new JLabel("Location: " + safeText(SessionManager.getCurrentLocationName()));
        locationLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        locationLabel.setForeground(DeckersPalette.muted());
        preserveForeground(locationLabel);

        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(6));
        titlePanel.add(employeeLabel);
        titlePanel.add(Box.createVerticalStrut(2));
        titlePanel.add(locationLabel);

        // Current Session Panel with rounded corners
        currentSessionPanel = new RoundedPanel(12);
        currentSessionPanel.setLayout(new BoxLayout(currentSessionPanel, BoxLayout.Y_AXIS));
        preserveBackground(currentSessionPanel);
        DeckersSwing.styleBand(currentSessionPanel, DeckersPalette.LIME, new Insets(8, 12, 8, 12));
        currentSessionPanel.setVisible(false);

        JLabel sessionTitle = new JLabel("Current Session");
        sessionTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        sessionTitle.setForeground(DeckersPalette.text());
        preserveForeground(sessionTitle);
        sessionTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        sessionTimeLabel = new JLabel("Session Time: 0:00:00");
        sessionTimeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        sessionTimeLabel.setForeground(DeckersPalette.text());
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
        statusLabel.setForeground(DeckersPalette.text());
        preserveForeground(statusLabel);
        statusLabel.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(DeckersPalette.sectionBorder(EMPLOYEE_ACCENT), 1, 8),
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
        refreshButton = createStyledButton("Refresh", DeckersPalette.PURPLE);
        actionPanel.add(clockInButton);
        actionPanel.add(lunchStartButton);
        actionPanel.add(lunchEndButton);
        actionPanel.add(clockOutButton);
        actionPanel.add(refreshButton);
        if (PermissionManager.hasPermission("TIME_CLOCK_MANAGEMENT")) {
            autoClockOutReviewsButton = createStyledButton("Auto Clock-Out Reviews", DeckersPalette.ORANGE);
            autoClockOutReviewsButton.setPreferredSize(new Dimension(210, 42));
            actionPanel.add(autoClockOutReviewsButton);
        }

        autoClockOutNoticeLabel = new JLabel();
        autoClockOutNoticeLabel.setOpaque(true);
        autoClockOutNoticeLabel.setBackground(DeckersPalette.tileHover(DeckersPalette.ORANGE));
        autoClockOutNoticeLabel.setForeground(DeckersPalette.text());
        autoClockOutNoticeLabel.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(DeckersPalette.sectionBorder(DeckersPalette.ORANGE), 1, 8),
                new EmptyBorder(10, 12, 10, 12)));
        autoClockOutNoticeLabel.setVisible(false);

        JPanel actionsAndNotice = new JPanel(new BorderLayout(0, 8));
        actionsAndNotice.setOpaque(false);
        actionsAndNotice.add(autoClockOutNoticeLabel, BorderLayout.NORTH);
        actionsAndNotice.add(actionPanel, BorderLayout.SOUTH);

        JPanel topPanel = new JPanel(new BorderLayout(0, 14));
        topPanel.setOpaque(false);
        topPanel.add(headerPanel, BorderLayout.NORTH);
        topPanel.add(actionsAndNotice, BorderLayout.SOUTH);

        // Calendar navigation panel with Today button
        JPanel calendarNavPanel = new JPanel(new BorderLayout());
        calendarNavPanel.setOpaque(false);
        calendarNavPanel.setBorder(new EmptyBorder(0, 0, 12, 0));

        prevMonthButton = createNavButton("←");
        nextMonthButton = createNavButton("→");
        todayButton = createStyledButton("Today", EMPLOYEE_ACCENT);
        todayButton.setPreferredSize(new Dimension(100, 32));

        monthYearLabel = new JLabel(currentMonth.format(MONTH_YEAR_FORMAT), SwingConstants.CENTER);
        monthYearLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        monthYearLabel.setForeground(DeckersPalette.text());
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

        monthDaysLabel = createStatCard("Days Worked", "0", DeckersPalette.ORANGE);
        monthHoursLabel = createStatCard("Total Hours", "0.00", DeckersPalette.LIME);
        monthPayLabel = createStatCard("Total Pay", "$0", DeckersPalette.MAGENTA);

        monthStatsPanel.add(monthDaysLabel);
        monthStatsPanel.add(monthHoursLabel);
        monthStatsPanel.add(monthPayLabel);

        // Calendar container with navigation and stats
        JPanel calendarContainer = new JPanel(new BorderLayout(0, 8));
        calendarContainer.setOpaque(false);
        calendarContainer.setPreferredSize(new Dimension(600, 0));

        RoundedPanel calendarCard = new RoundedPanel(12);
        calendarCard.setLayout(new BorderLayout(0, 8));
        preserveBackground(calendarCard);
        DeckersSwing.styleBand(calendarCard, EMPLOYEE_ACCENT, new Insets(16, 16, 16, 16));
        calendarCard.add(calendarNavPanel, BorderLayout.NORTH);
        calendarCard.add(calendarPanel, BorderLayout.CENTER);
        calendarCard.add(monthStatsPanel, BorderLayout.SOUTH);

        calendarContainer.add(calendarCard, BorderLayout.CENTER);

        // Details panel with card styling
        RoundedPanel detailsCard = new RoundedPanel(12);
        detailsCard.setLayout(new BorderLayout(0, 12));
        preserveBackground(detailsCard);
        DeckersSwing.styleBand(detailsCard, DeckersPalette.CORAL, new Insets(16, 16, 16, 16));

        JLabel detailsTitle = new JLabel("Time Clock Details");
        detailsTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        detailsTitle.setForeground(DeckersPalette.text());
        preserveForeground(detailsTitle);

        detailsModel = new DefaultTableModel(
                new Object[]{"Date", "Clock In", "Lunch Start", "Lunch End", "Clock Out", "Hours", "Pay", "Type", "Location", "Review"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        detailsTable = new JTable(detailsModel);
        DeckersSwing.styleTable(detailsTable, DeckersPalette.CORAL);
        detailsTable.setRowHeight(32);
        detailsTable.setFont(new Font("SansSerif", Font.PLAIN, 13));
        detailsTable.setShowGrid(false);
        detailsTable.setIntercellSpacing(new Dimension(0, 0));

        // Alternating row colors
        detailsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0
                            ? DeckersPalette.tableBody(DeckersPalette.CORAL)
                            : DeckersPalette.tableStripe());
                    c.setForeground(DeckersPalette.text());
                }
                setBorder(new EmptyBorder(4, 8, 4, 8));
                return c;
            }
        });

        JScrollPane detailsScroll = new JScrollPane(detailsTable);
        detailsScroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.CORAL)));
        detailsScroll.getViewport().setBackground(DeckersPalette.tableBody(DeckersPalette.CORAL));

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
        mainPanel.add(loadingState, BorderLayout.SOUTH);
        add(mainPanel, BorderLayout.CENTER);

        wireActions();
        loadTimeClock();
        WindowHelper.configurePosWindow(this);
    }

    private JButton createActionButton(String text, Color color) {
        JButton button = new RoundedButton(text, 8);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileHover(color));
        button.setBorder(deckersButtonBorder(color, new Insets(10, 16, 10, 16)));
        button.setPreferredSize(new Dimension(130, 42));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createStyledButton(String text, Color color) {
        JButton button = new RoundedButton(text, 8);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileHover(color));
        button.setBorder(deckersButtonBorder(color, new Insets(10, 16, 10, 16)));
        button.setPreferredSize(new Dimension(120, 42));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createNavButton(String text) {
        JButton button = new RoundedButton(text, 6);
        button.setFont(new Font("SansSerif", Font.BOLD, 18));
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileFill(EMPLOYEE_ACCENT));
        button.setBorder(deckersButtonBorder(EMPLOYEE_ACCENT, new Insets(4, 12, 4, 12)));
        button.setPreferredSize(new Dimension(50, 32));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static Border deckersButtonBorder(Color accent, Insets padding) {
        return BorderFactory.createCompoundBorder(
                new RoundedBorder(DeckersPalette.sectionBorder(accent), 1, 8),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                        new EmptyBorder(padding)
                )
        );
    }

    private JLabel createSessionDetailLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        label.setForeground(DeckersPalette.muted());
        preserveForeground(label);
        return label;
    }

    private JLabel createStatCard(String title, String value, Color accent) {
        JPanel card = new RoundedPanel(8);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        preserveBackground(card);
        DeckersSwing.styleBand(card, accent, new Insets(10, 12, 10, 12));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleLabel.setForeground(DeckersPalette.muted());
        preserveForeground(titleLabel);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        valueLabel.setForeground(DeckersPalette.text());
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
        clockInButton.addActionListener(e -> runPunch(this::clockInWithRequiredOverride, "Failed to clock in."));
        lunchStartButton.addActionListener(e -> runPunch(TimeClockManager::lunchStart, "Failed to punch lunch start."));
        lunchEndButton.addActionListener(e -> runPunch(TimeClockManager::lunchEnd, "Failed to punch lunch end."));
        clockOutButton.addActionListener(e -> runPunch(TimeClockManager::clockOut, "Failed to clock out."));
        refreshButton.addActionListener(e -> loadTimeClock());
        if (autoClockOutReviewsButton != null) {
            autoClockOutReviewsButton.addActionListener(e -> showAutoClockOutReviews());
        }
        prevMonthButton.addActionListener(e -> {
            currentMonth = currentMonth.minusMonths(1);
            loadTimeClock();
        });
        nextMonthButton.addActionListener(e -> {
            currentMonth = currentMonth.plusMonths(1);
            loadTimeClock();
        });
        todayButton.addActionListener(e -> {
            currentMonth = YearMonth.now();
            loadTimeClock();
        });
    }

    private void clockInWithRequiredOverride() throws SQLException, TimeClockException {
        if (!TimeClockManager.requiresMultipleSessionOverride()) {
            TimeClockManager.clockIn();
            return;
        }

        if (TimeClockManager.currentUserCanApproveMultipleSessionOverride()) {
            TimeClockManager.clockIn();
            return;
        }

        ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                this,
                TimeClockManager.MULTIPLE_SESSION_OVERRIDE_PERMISSION,
                "Time Clock Multiple Session Override",
                "Reason for allowing another time clock session today:"
        );
        if (approval == null) {
            throw new TimeClockException("Clock in canceled. Manager approval is required after a completed session today.");
        }
        TimeClockManager.clockIn(approval);
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
        YearMonth month = currentMonth;
        boolean loadReviews = autoClockOutReviewsButton != null;
        String cacheKey = "time-clock:" + month;
        CachedUiLoader.load(this, "time-clock.load", cacheKey, TimeClockSnapshot.class, SessionDataCache.SCREEN_TTL,
                loadingState, () -> loadTimeClockSnapshot(month, loadReviews), this::applyTimeClockSnapshot);
    }

    private TimeClockSnapshot loadTimeClockSnapshot(YearMonth month, boolean loadReviews) throws SQLException {
        TimeClockDashboard dashboard = TimeClockManager.loadDashboard(false);
        Map<LocalDate, Holiday> loadedHolidays = EmployeeScheduleService.loadCurrentStoreHolidaysForTimeClock(
                month.atDay(1), month.atEndOfMonth());
        Integer userId = SessionManager.getCurrentUserId();
        EmployeeAutoCloseNotice notice = userId == null ? null : TimeClockAutoCloseService.latestPendingNotice(userId);
        int reviewCount = loadReviews ? TimeClockAutoCloseService.loadPendingReviews().size() : 0;
        return new TimeClockSnapshot(dashboard, loadedHolidays, notice, reviewCount);
    }

    private void applyTimeClockSnapshot(TimeClockSnapshot snapshot) {
        TimeClockDashboard dashboard = snapshot.dashboard();
        allRows = dashboard.rows();
        holidays = snapshot.holidays();
        updateClockStatus(dashboard.status());
        updateCurrentSession();
        processDayData();
        renderCalendar();
        applyAutoClockOutNotice(snapshot.notice(), snapshot.reviewCount());
    }

    private void refreshAutoClockOutNotice() {
        try {
            Integer userId = SessionManager.getCurrentUserId();
            EmployeeAutoCloseNotice notice = userId == null ? null
                    : TimeClockAutoCloseService.latestPendingNotice(userId);
            int count = autoClockOutReviewsButton == null ? 0 : TimeClockAutoCloseService.loadPendingReviews().size();
            applyAutoClockOutNotice(notice, count);
        } catch (SQLException ex) {
            autoClockOutNoticeLabel.setVisible(false);
        }
    }

    private void applyAutoClockOutNotice(EmployeeAutoCloseNotice notice, int reviewCount) {
        if (notice == null) {
            autoClockOutNoticeLabel.setVisible(false);
        } else {
            String rule = "SCHEDULED".equals(notice.rule())
                    ? "the scheduled-shift safety rule" : "the unscheduled 12-hour safety rule";
            autoClockOutNoticeLabel.setText("SmartStock automatically closed your session at "
                    + formatTime(notice.clockOut()) + " using " + rule
                    + ". It is included in payroll and awaiting manager review.");
            autoClockOutNoticeLabel.setVisible(true);
        }
        if (autoClockOutReviewsButton != null) {
            autoClockOutReviewsButton.setText("Auto Clock-Out Reviews (" + reviewCount + ")");
        }
    }

    private record TimeClockSnapshot(TimeClockDashboard dashboard, Map<LocalDate, Holiday> holidays,
                                     EmployeeAutoCloseNotice notice, int reviewCount) { }

    private void showAutoClockOutReviews() {
        try {
            List<PendingReview> reviews = TimeClockAutoCloseService.loadPendingReviews();
            if (reviews.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no automatic clock-outs awaiting review.");
                refreshAutoClockOutNotice();
                return;
            }
            DefaultTableModel model = new DefaultTableModel(
                    new Object[]{"Employee", "Store", "Work Date", "Clock In", "Lunch", "Clock Out", "Hours", "Rule"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (PendingReview review : reviews) {
                String lunch = review.lunchStart() == null ? "—"
                        : formatTime(review.lunchStart()) + " – " + formatTime(review.lunchEnd());
                model.addRow(new Object[]{review.employeeName(), review.locationName(), review.workDate(),
                        formatTime(review.clockIn()), lunch, formatTime(review.clockOut()),
                        formatHours(review.workedHours()), review.rule()});
            }
            JTable table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setRowHeight(28);
            if (!reviews.isEmpty()) table.setRowSelectionInterval(0, 0);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(980, 320));
            Object[] options = {"Confirm", "Correct", "Close"};
            int choice = JOptionPane.showOptionDialog(this, scroll, "Auto Clock-Out Reviews",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            int selected = table.getSelectedRow();
            if (selected < 0 || choice == 2 || choice == JOptionPane.CLOSED_OPTION) return;
            PendingReview review = reviews.get(table.convertRowIndexToModel(selected));
            if (choice == 0) {
                TimeClockAutoCloseService.confirm(review.clockId(), "Automatic clock-out confirmed by manager.");
            } else if (choice == 1) {
                correctAutoClockOut(review);
            }
            loadTimeClock();
            SwingUtilities.invokeLater(this::showAutoClockOutReviews);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Unable to review automatic clock-outs.\n\n" + ex.getMessage(),
                    "Time Clock Review", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void correctAutoClockOut(PendingReview review) throws SQLException {
        JTextField clockIn = new JTextField(review.clockIn().format(CORRECTION_FORMAT));
        JTextField lunchStart = new JTextField(review.lunchStart() == null ? "" : review.lunchStart().format(CORRECTION_FORMAT));
        JTextField lunchEnd = new JTextField(review.lunchEnd() == null ? "" : review.lunchEnd().format(CORRECTION_FORMAT));
        JTextField clockOut = new JTextField(review.clockOut().format(CORRECTION_FORMAT));
        JTextArea reason = new JTextArea(3, 28);
        reason.setLineWrap(true);
        reason.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Clock in (yyyy-MM-dd HH:mm)")); form.add(clockIn);
        form.add(new JLabel("Lunch start (optional)")); form.add(lunchStart);
        form.add(new JLabel("Lunch end (optional)")); form.add(lunchEnd);
        form.add(new JLabel("Clock out (yyyy-MM-dd HH:mm)")); form.add(clockOut);
        form.add(new JLabel("Required reason")); form.add(new JScrollPane(reason));
        if (JOptionPane.showConfirmDialog(this, form, "Correct Automatic Clock-Out",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            TimeClockAutoCloseService.correct(review.clockId(), review.locationZone(),
                    new TimeClockAutoCloseService.Correction(
                            LocalDateTime.parse(clockIn.getText().trim(), CORRECTION_FORMAT),
                            parseOptionalCorrectionTime(lunchStart.getText()),
                            parseOptionalCorrectionTime(lunchEnd.getText()),
                            LocalDateTime.parse(clockOut.getText().trim(), CORRECTION_FORMAT),
                            reason.getText()));
        } catch (java.time.format.DateTimeParseException ex) {
            throw new SQLException("Use yyyy-MM-dd HH:mm for every entered date and time.");
        }
    }

    private static LocalDateTime parseOptionalCorrectionTime(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : LocalDateTime.parse(trimmed, CORRECTION_FORMAT);
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
            currentClockIn = currentRecord.shiftClockIn();
            currentLunchStart = currentRecord.shiftLunchStart();
            currentLunchEnd = currentRecord.shiftLunchEnd();

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
            Color text = DeckersPalette.text();
            sessionTimeLabel.setForeground(new Color(text.getRed(), text.getGreen(), text.getBlue(), (int)(255 * pulseAlpha)));
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
            dayLabel.setForeground(DeckersPalette.muted());
            preserveForeground(dayLabel);
            dayLabel.setBorder(new EmptyBorder(4, 0, 4, 0));

            // Sunday is the only recurring red day; Saturdays use the normal header color.
            if (i == 0) {
                dayLabel.setForeground(DeckersPalette.CORAL);
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
        cell.setPreferredSize(new Dimension(80, 65));
        preserveBackground(cell);

        boolean isToday = date.equals(LocalDate.now());
        boolean hasData = dayData != null && !dayData.rows.isEmpty();
        Holiday holiday = holidays.get(date);
        boolean isSunday = date.getDayOfWeek() == DayOfWeek.SUNDAY;

        Color accent = holiday != null ? DeckersPalette.CORAL
                : isToday ? EMPLOYEE_ACCENT : hasData ? DeckersPalette.LIME
                : isSunday ? DeckersPalette.CORAL : EMPLOYEE_ACCENT;

        // Background colors
        if (holiday != null) {
            cell.setBackground(DeckersPalette.tileFill(DeckersPalette.CORAL));
        } else if (isToday && hasData) {
            cell.setBackground(DeckersPalette.tilePressed(DeckersPalette.LIME));
        } else if (isToday) {
            cell.setBackground(DeckersPalette.tilePressed(EMPLOYEE_ACCENT));
        } else if (hasData) {
            cell.setBackground(DeckersPalette.tileHover(DeckersPalette.LIME));
        } else if (isSunday) {
            cell.setBackground(DeckersPalette.tileFill(DeckersPalette.CORAL));
        } else {
            cell.setBackground(DeckersPalette.surface());
        }
        cell.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(DeckersPalette.sectionBorder(accent), 1, 8),
                new EmptyBorder(6, 4, 6, 4)
        ));

        JLabel dayNumber = new JLabel(String.valueOf(date.getDayOfMonth()), SwingConstants.CENTER);
        dayNumber.setFont(new Font("SansSerif", isToday ? Font.BOLD : Font.PLAIN, 14));
        dayNumber.setForeground(DeckersPalette.text());
        preserveForeground(dayNumber);
        dayNumber.setBorder(new EmptyBorder(2, 0, 2, 0));

        if (hasData) {
            cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel hoursLabel = new JLabel(formatHours(dayData.totalHours) + " hrs", SwingConstants.CENTER);
            hoursLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            hoursLabel.setForeground(DeckersPalette.muted());
            preserveForeground(hoursLabel);

            // Multiple entries indicator
            JLabel countLabel = null;
            if (dayData.rows.size() > 1) {
                countLabel = new JLabel("●".repeat(Math.min(dayData.rows.size(), 3)), SwingConstants.CENTER);
                countLabel.setFont(new Font("SansSerif", Font.PLAIN, 8));
                countLabel.setForeground(DeckersPalette.MAGENTA);
                preserveForeground(countLabel);
            }

            JPanel infoPanel = new JPanel();
            infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
            infoPanel.setOpaque(false);
            infoPanel.add(dayNumber);
            if (holiday != null) {
                JLabel holidayLabel = new JLabel(holiday.name(), SwingConstants.CENTER);
                holidayLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
                holidayLabel.setForeground(DeckersPalette.CORAL);
                preserveForeground(holidayLabel);
                infoPanel.add(holidayLabel);
            }
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
                "%s" +
                "Entries: <b>%d</b>" +
                "</div></html>",
                date.format(DATE_FORMAT),
                formatHours(dayData.totalHours),
                CURRENCY_FORMAT.format(dayData.totalPay),
                holiday == null ? "" : "Holiday: <b>" + holiday.name() + "</b><br>",
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
        } else if (holiday != null) {
            JPanel holidayPanel = new JPanel();
            holidayPanel.setOpaque(false);
            holidayPanel.setLayout(new BoxLayout(holidayPanel, BoxLayout.Y_AXIS));
            dayNumber.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel holidayLabel = new JLabel(holiday.name());
            holidayLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            holidayLabel.setFont(new Font("SansSerif", Font.BOLD, 9));
            holidayLabel.setForeground(DeckersPalette.CORAL);
            preserveForeground(holidayLabel);
            holidayPanel.add(dayNumber);
            holidayPanel.add(holidayLabel);
            cell.add(holidayPanel, BorderLayout.CENTER);
            cell.setToolTipText(date.format(DATE_FORMAT) + " • " + holiday.name()
                    + " • Manual scheduling only");
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
                    row.locationName(),
                    row.autoClockOut() ? "Auto — " + safeText(row.autoClockOutReviewStatus()) : ""
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
        Color accent = switch (state) {
            case CLOCKED_IN -> DeckersPalette.LIME;
            case ON_LUNCH -> DeckersPalette.ORANGE;
            case CLOCKED_OUT -> DeckersPalette.CORAL;
            default -> EMPLOYEE_ACCENT;
        };

        Color backgroundColor = DeckersPalette.tileHover(accent);
        Color textColor = DeckersPalette.text();
        Color borderColor = DeckersPalette.sectionBorder(accent);

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
        button.setEnabled(enabled);
        button.setBackground(enabled ? DeckersPalette.tileHover(enabledColor) : DeckersPalette.sectionFill(enabledColor));
        button.setForeground(enabled ? DeckersPalette.text() : DeckersPalette.muted());
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
            setBorderPainted(true);
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
