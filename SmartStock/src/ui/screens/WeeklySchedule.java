package ui.screens;

import managers.PermissionManager;
import managers.SessionManager;
import managers.CompanyCustomizationManager;
import services.EmployeeScheduleService;
import services.EmployeeAutoScheduleService;
import services.ScheduleExportService;
import services.EmployeeAutoScheduleService.AutoScheduleProposal;
import services.EmployeeAutoScheduleService.DailyCoverage;
import services.EmployeeAutoScheduleService.ScheduleEntry;
import services.EmployeeAutoScheduleService.ScheduleWarning;
import services.EmployeeScheduleService.Assignment;
import services.EmployeeScheduleService.Employee;
import services.EmployeeScheduleService.Holiday;
import services.EmployeeScheduleService.Shift;
import services.EmployeeScheduleService.StoreLocation;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.WindowHelper;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WeeklySchedule extends JFrame {
    private enum ScheduleViewMode {
        SEMI_MONTHLY("Semi-monthly"), WEEKLY("Weekly");
        private final String label;
        ScheduleViewMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum ScheduleDisplayMode {
        DETAILED("Detailed"), COMPACT("Compact");
        private final String label;
        ScheduleDisplayMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private static final DateTimeFormatter WEEK_RANGE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DAY_DATE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter COMPACT_TIME = DateTimeFormatter.ofPattern("h a");
    private static final List<LocalTime> LUNCH_PRESETS = List.of(
            LocalTime.of(11, 30), LocalTime.NOON, LocalTime.of(12, 30),
            LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(14, 0)
    );
    private static final Color ACCENT = DeckersPalette.YELLOW;
    private static final List<Color> EMPLOYEE_ACCENTS = List.of(
            DeckersPalette.ORANGE, DeckersPalette.MAGENTA, DeckersPalette.LIME,
            DeckersPalette.YELLOW, DeckersPalette.PURPLE, DeckersPalette.CORAL
    );

    private final boolean canEdit;
    private final boolean canScheduleOtherStores;
    private int locationId;
    private StoreLocation selectedLocation;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private final JComboBox<StoreLocation> storeBox = new JComboBox<>();
    private final JComboBox<ScheduleViewMode> viewBox = new JComboBox<>(ScheduleViewMode.values());
    private final JComboBox<ScheduleDisplayMode> displayBox = new JComboBox<>(ScheduleDisplayMode.values());
    private final JLabel weekLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private final CalendarGridPanel daysPanel = new CalendarGridPanel();
    private final Map<Integer, Color> employeeAccents = new HashMap<>();
    private Map<LocalDate, Holiday> holidays = Map.of();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public WeeklySchedule() {
        setTitle("Employee Schedule");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 700));
        setSize(1500, 900);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "WeeklySchedule"));

        if (!PermissionManager.hasPermission("VIEW_EMPLOYEE_SCHEDULE")) {
            JOptionPane.showMessageDialog(this, "You do not have permission to view the employee schedule.",
                    "Access Denied", JOptionPane.WARNING_MESSAGE);
            canEdit = false;
            canScheduleOtherStores = false;
            dispose();
            return;
        }
        canEdit = PermissionManager.hasPermission("EDIT_EMPLOYEE_SCHEDULE");
        canScheduleOtherStores = PermissionManager.hasPermission("SCHEDULE_OTHER_STORES");
        setCurrentPeriod(LocalDate.now());

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        root.setBackground(DeckersPalette.background());
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(buildHeader(), BorderLayout.NORTH);

        daysPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(daysPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(DeckersPalette.background());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setForeground(DeckersPalette.muted());
        statusLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);footer.add(statusLabel,BorderLayout.NORTH);footer.add(loadingState,BorderLayout.SOUTH);root.add(footer, BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        storeBox.addActionListener(e -> {
            StoreLocation location = (StoreLocation) storeBox.getSelectedItem();
            if (location != null && (selectedLocation == null || location.locationId() != selectedLocation.locationId())) {
                selectedLocation = location;
                locationId = location.locationId();
                employeeAccents.clear();
                loadPeriod();
            }
        });
        viewBox.addActionListener(e -> {
            setCurrentPeriod(scheduleToday());
            loadPeriod();
        });
        displayBox.addActionListener(e -> loadPeriod());
        WindowHelper.configurePosWindow(this);
        loadLocations();
    }

    private void loadLocations() {
        CachedUiLoader.load(this,"weekly-schedule.locations","weekly-schedule:locations",LocationSnapshot.class,
                SessionDataCache.REFERENCE_TTL,loadingState,
                ()->new LocationSnapshot(EmployeeScheduleService.loadAccessibleLocations()),snapshot->{
                    storeBox.removeAllItems();
                    snapshot.locations().forEach(storeBox::addItem);
                    if(snapshot.locations().isEmpty()){statusLabel.setText("No accessible store schedules were found.");return;}
                    selectLoginLocation();
                    storeBox.setEnabled(canScheduleOtherStores&&storeBox.getItemCount()>1);
                    setCurrentPeriod(scheduleToday());
                    loadPeriod();
                });
    }

    private void selectLoginLocation() {
        Integer loginLocationId = SessionManager.getCurrentLocationId();
        for (int index = 0; index < storeBox.getItemCount(); index++) {
            StoreLocation location = storeBox.getItemAt(index);
            if (loginLocationId != null && location.locationId() == loginLocationId) {
                storeBox.setSelectedIndex(index);
                selectedLocation = location;
                locationId = location.locationId();
                return;
            }
        }
        selectedLocation = storeBox.getItemAt(0);
        locationId = selectedLocation.locationId();
        storeBox.setSelectedIndex(0);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Employee Schedule");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel subtitle = new JLabel(canEdit
                ? "Assign employees to a shift and lunch time for each day."
                : "See who is scheduled to work each day.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(DeckersPalette.muted());
        subtitle.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);

        JPanel topControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topControls.setOpaque(false);
        JLabel storeLabel = new JLabel(canScheduleOtherStores ? "Store:" : "Schedule for:");
        storeLabel.setForeground(DeckersPalette.text());
        storeLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        storeBox.setEnabled(canScheduleOtherStores && storeBox.getItemCount() > 1);
        storeBox.setPreferredSize(new Dimension(170, 34));
        topControls.add(storeLabel);
        topControls.add(storeBox);
        JLabel viewLabel = new JLabel("View:");
        viewLabel.setForeground(DeckersPalette.text());
        viewLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        topControls.add(viewLabel);
        viewBox.setPreferredSize(new Dimension(135, 34));
        topControls.add(viewBox);
        JLabel displayLabel = new JLabel("Layout:");
        displayLabel.setForeground(DeckersPalette.text());
        displayLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        topControls.add(displayLabel);
        displayBox.setPreferredSize(new Dimension(108, 34));
        topControls.add(displayBox);
        if (canEdit) {
            JButton autoSchedule = new JButton("Auto Schedule");
            DeckersSwing.styleUtilityButton(autoSchedule, ACCENT);
            autoSchedule.addActionListener(e -> showAutoSchedulePreview());
            topControls.add(autoSchedule);
            JButton manageShifts = new JButton("Manage Shifts");
            DeckersSwing.styleUtilityButton(manageShifts, ACCENT);
            manageShifts.addActionListener(e -> showShiftManager());
            topControls.add(manageShifts);
            JButton exportSchedule = new JButton("Export");
            DeckersSwing.styleUtilityButton(exportSchedule, ACCENT);
            exportSchedule.setToolTipText("Export the visible schedule as a PDF or PNG image");
            exportSchedule.addActionListener(e -> exportVisibleSchedule());
            topControls.add(exportSchedule);
            JButton clearSchedule = new JButton("Clear Schedule");
            DeckersSwing.styleUtilityButton(clearSchedule, DeckersPalette.CORAL);
            clearSchedule.setToolTipText("Remove every assignment shown for this store and period");
            clearSchedule.addActionListener(e -> clearVisibleSchedule());
            topControls.add(clearSchedule);
        }

        JPanel periodControls = new JPanel();
        periodControls.setOpaque(false);
        periodControls.setLayout(new BoxLayout(periodControls, BoxLayout.Y_AXIS));
        weekLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        weekLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        weekLabel.setForeground(DeckersPalette.text());
        weekLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        navigation.setAlignmentX(Component.RIGHT_ALIGNMENT);
        navigation.setOpaque(false);
        JButton previous = new JButton("Previous");
        JButton today = new JButton("Current");
        JButton next = new JButton("Next");
        for (JButton button : List.of(previous, today, next)) {
            DeckersSwing.styleUtilityButton(button, ACCENT);
        }
        previous.addActionListener(e -> { movePeriod(-1); loadPeriod(); });
        today.addActionListener(e -> { setCurrentPeriod(scheduleToday()); loadPeriod(); });
        next.addActionListener(e -> { movePeriod(1); loadPeriod(); });
        navigation.add(previous);
        navigation.add(today);
        navigation.add(next);
        periodControls.add(weekLabel);
        periodControls.add(Box.createVerticalStrut(8));
        periodControls.add(navigation);

        JPanel right = new JPanel(new BorderLayout(14, 0));
        right.setOpaque(false);
        right.add(topControls, BorderLayout.CENTER);
        right.add(periodControls, BorderLayout.EAST);

        header.add(titles, BorderLayout.WEST);
        header.add(right, BorderLayout.CENTER);
        return header;
    }

    private void loadPeriod() {
        if (selectedLocation == null) {
            return;
        }
        weekLabel.setText(WEEK_RANGE.format(periodStart) + " – " + WEEK_RANGE.format(periodEnd)
                + ", " + periodEnd.getYear());
        statusLabel.setText("Loading " + selectedLocation.name() + " schedule…");
        int requestedLocation=locationId;LocalDate requestedStart=periodStart,requestedEnd=periodEnd;String locationName=selectedLocation.name();
        String cacheKey="weekly-schedule:period:"+requestedLocation+":"+requestedStart+":"+requestedEnd;
        CachedUiLoader.load(this,"weekly-schedule.period",cacheKey,ScheduleSnapshot.class,
                SessionDataCache.SCREEN_TTL,loadingState,()->{
                    var period=EmployeeScheduleService.loadPeriod(requestedLocation,requestedStart,requestedEnd);
                    return new ScheduleSnapshot(period.assignments(),period.holidays(),locationName);
                },snapshot->{
            holidays = snapshot.holidays();
            renderDays(snapshot.assignments(), holidays);
            Map<LocalDate,List<Assignment>> assignments=snapshot.assignments();
            int count = assignments.values().stream().mapToInt(List::size).sum();
            statusLabel.setText((count == 0 ? "No one is scheduled for this period yet."
                    : count + (count == 1 ? " scheduled work day" : " scheduled work days"))
                    + " • " + snapshot.locationName());
        });
    }

    private record LocationSnapshot(List<StoreLocation> locations) { }
    private record ScheduleSnapshot(Map<LocalDate,List<Assignment>> assignments,
                                    Map<LocalDate,Holiday> holidays,String locationName) { }

    private void renderDays(Map<LocalDate, List<Assignment>> assignments, Map<LocalDate, Holiday> holidays) {
        daysPanel.removeAll();
        LocalDate today = scheduleToday();
        int columnCount = selectedViewMode() == ScheduleViewMode.SEMI_MONTHLY ? 8 : 7;
        int dayCount = (int) (periodEnd.toEpochDay() - periodStart.toEpochDay()) + 1;
        int rowCount = (dayCount + columnCount - 1) / columnCount;
        int totalHeight = 0;
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            LocalDate rowStart = periodStart.plusDays((long) rowIndex * columnCount);
            int busiestDay = 0;
            boolean rowHasHoliday = false;
            for (int offset = 0; offset < columnCount; offset++) {
                LocalDate date = rowStart.plusDays(offset);
                if (!date.isAfter(periodEnd)) {
                    busiestDay = Math.max(busiestDay, assignments.getOrDefault(date, List.of()).size());
                    rowHasHoliday |= holidays.containsKey(date);
                }
            }
            boolean compact = selectedDisplayMode() == ScheduleDisplayMode.COMPACT;
            int rowHeight = compact
                    ? Math.min(320, Math.max(150, 116 + (busiestDay * 34)))
                    : Math.min(510, Math.max(175, 128 + (busiestDay * 98)));
            if (rowHasHoliday) rowHeight = Math.min(compact ? 340 : 530, rowHeight + 24);
            JPanel weekRow = new JPanel(new GridLayout(1, columnCount, 10, 0));
            weekRow.setOpaque(false);
            weekRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            weekRow.setPreferredSize(new Dimension(CalendarGridPanel.MINIMUM_GRID_WIDTH, rowHeight));
            weekRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
            for (int offset = 0; offset < columnCount; offset++) {
                LocalDate date = rowStart.plusDays(offset);
                if (date.isAfter(periodEnd)) {
                    weekRow.add(buildOutsidePeriodSlot());
                } else {
                    weekRow.add(buildDayPanel(date, assignments.getOrDefault(date, List.of()),
                            holidays.get(date), date.equals(today)));
                }
            }
            daysPanel.add(weekRow);
            if (rowIndex < rowCount - 1) {
                daysPanel.add(Box.createVerticalStrut(10));
                totalHeight += 10;
            }
            totalHeight += rowHeight;
        }
        daysPanel.setPreferredSize(new Dimension(CalendarGridPanel.MINIMUM_GRID_WIDTH, totalHeight));
        daysPanel.revalidate();
        daysPanel.repaint();
    }

    private JPanel buildOutsidePeriodSlot() {
        JPanel slot = new JPanel();
        slot.setOpaque(false);
        slot.setBorder(BorderFactory.createEmptyBorder());
        return slot;
    }

    private JPanel buildDayPanel(LocalDate date, List<Assignment> assignments, Holiday holiday, boolean today) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        card.setBackground(holiday == null ? DeckersPalette.surface()
                : DeckersPalette.tileFill(DeckersPalette.MAGENTA));
        Color borderAccent = holiday != null ? DeckersPalette.MAGENTA : today ? ACCENT : DeckersPalette.border();
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderAccent, today || holiday != null ? 2 : 1),
                new EmptyBorder(12, 10, 12, 10)));
        card.setPreferredSize(new Dimension(215, 175));

        JPanel dayHeader = new JPanel();
        dayHeader.setOpaque(false);
        dayHeader.setLayout(new BoxLayout(dayHeader, BoxLayout.Y_AXIS));
        String dayText = date.getDayOfWeek().toString();
        JLabel dayName = new JLabel(dayText.substring(0, 1) + dayText.substring(1).toLowerCase());
        dayName.setFont(new Font("SansSerif", Font.BOLD, 17));
        dayName.setForeground(DeckersPalette.text());
        dayName.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        String dateText = DAY_DATE.format(date) + (today ? " • Today" : "");
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            dateText += " • Manual only";
        }
        if (holiday != null) {
            dateText += " • Holiday";
        }
        JLabel dateLabel = new JLabel(dateText);
        dateLabel.setFont(new Font("SansSerif", today ? Font.BOLD : Font.PLAIN, 13));
        dateLabel.setForeground(today ? DeckersPalette.blend(DeckersPalette.text(), ACCENT, 0.35) : DeckersPalette.muted());
        dateLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        dayHeader.add(dayName);
        dayHeader.add(Box.createVerticalStrut(2));
        dayHeader.add(dateLabel);
        if (holiday != null) {
            JLabel holidayLabel = detailLabel(holiday.name(), Font.BOLD, 12, DeckersPalette.MAGENTA);
            holidayLabel.setToolTipText("Auto Schedule leaves this day empty. Employees may still be added manually.");
            dayHeader.add(Box.createVerticalStrut(3));
            dayHeader.add(holidayLabel);
        }
        JPanel headerRow = new JPanel(new BorderLayout(6, 0));
        headerRow.setOpaque(false);
        headerRow.add(dayHeader, BorderLayout.CENTER);
        if (canEdit) {
            headerRow.add(buildHolidayIconButton(date, holiday), BorderLayout.EAST);
        }
        card.add(headerRow, BorderLayout.NORTH);

        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        if (assignments.isEmpty()) {
            JLabel empty = new JLabel(holiday == null ? "Not scheduled" : "Closed • manual scheduling only");
            empty.setFont(new Font("SansSerif", Font.ITALIC, 13));
            empty.setForeground(DeckersPalette.muted());
            empty.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
            names.add(empty);
        } else {
            for (Assignment assignment : assignments) {
                names.add(buildAssignmentRow(assignment));
                names.add(Box.createVerticalStrut(6));
            }
        }
        JScrollPane namesScroll = new JScrollPane(names,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        namesScroll.setBorder(BorderFactory.createEmptyBorder());
        namesScroll.setOpaque(false);
        namesScroll.getViewport().setOpaque(false);
        namesScroll.getVerticalScrollBar().setUnitIncrement(12);
        card.add(namesScroll, BorderLayout.CENTER);

        if (canEdit) {
            JPanel actions = new JPanel(new GridLayout(1, 1));
            actions.setOpaque(false);
            JButton add = new JButton("+ Add employee");
            DeckersSwing.styleUtilityButton(add, ACCENT);
            add.addActionListener(e -> showEmployeePicker(date, assignments));
            actions.add(add);
            card.add(actions, BorderLayout.SOUTH);
        }
        return card;
    }

    private JButton buildHolidayIconButton(LocalDate date, Holiday holiday) {
        Color accent = holiday == null ? DeckersPalette.muted() : DeckersPalette.MAGENTA;
        JButton button = new JButton(new HolidayCalendarIcon(accent));
        button.setPreferredSize(new Dimension(30, 28));
        button.setMinimumSize(new Dimension(30, 28));
        button.setMaximumSize(new Dimension(30, 28));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createLineBorder(
                holiday == null ? DeckersPalette.border() : DeckersPalette.sectionBorder(DeckersPalette.MAGENTA)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(holiday == null
                ? "Mark " + DAY_DATE.format(date) + " as a company-wide holiday"
                : holiday.name() + " • Click to edit or remove this holiday");
        button.addActionListener(e -> {
            if (holiday == null) {
                editHoliday(date, null);
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            JMenuItem edit = new JMenuItem("Edit holiday…");
            edit.addActionListener(event -> editHoliday(date, holiday));
            JMenuItem remove = new JMenuItem("Remove holiday");
            remove.addActionListener(event -> removeHoliday(date, holiday));
            menu.add(edit);
            menu.addSeparator();
            menu.add(remove);
            menu.show(button, button.getWidth() - menu.getPreferredSize().width, button.getHeight());
        });
        return button;
    }

    private static final class HolidayCalendarIcon implements Icon {
        private final Color color;

        private HolidayCalendarIcon(Color color) {
            this.color = color;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawRoundRect(x + 2, y + 3, 13, 12, 2, 2);
                g.drawLine(x + 2, y + 7, x + 15, y + 7);
                g.drawLine(x + 6, y + 1, x + 6, y + 5);
                g.drawLine(x + 11, y + 1, x + 11, y + 5);
                g.fillOval(x + 5, y + 9, 3, 3);
                g.fillOval(x + 10, y + 9, 3, 3);
            } finally {
                g.dispose();
            }
        }

        @Override public int getIconWidth() { return 17; }
        @Override public int getIconHeight() { return 17; }
    }

    private void editHoliday(LocalDate date, Holiday holiday) {
        String currentName = holiday == null ? "Holiday" : holiday.name();
        String name = JOptionPane.showInputDialog(this,
                "Holiday name for " + DAY_DATE.format(date) + ":\n"
                        + "Auto Schedule will leave this date blank, but manual scheduling remains available.",
                currentName);
        if (name == null) return;
        try {
            EmployeeScheduleService.saveHoliday(date, name);
            loadPeriod();
        } catch (SQLException ex) {
            showScheduleError("Failed to save the holiday", ex);
        }
    }

    private void removeHoliday(LocalDate date, Holiday holiday) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove “" + holiday.name() + "” from " + DAY_DATE.format(date) + "?\n"
                        + "Existing manual assignments will not be changed.",
                "Remove Holiday", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            EmployeeScheduleService.removeHoliday(date);
            loadPeriod();
        } catch (SQLException ex) {
            showScheduleError("Failed to remove the holiday", ex);
        }
    }

    private JPanel buildAssignmentRow(Assignment assignment) {
        if (selectedDisplayMode() == ScheduleDisplayMode.COMPACT) {
            return buildCompactAssignmentRow(assignment);
        }
        Color employeeAccent = colorForEmployee(assignment.userId());
        JPanel row = new JPanel(new BorderLayout(4, 6));
        row.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        row.setBackground(DeckersPalette.tileFill(employeeAccent));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(employeeAccent)),
                new EmptyBorder(7, 8, 7, 6)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = detailLabel(assignment.displayName(), Font.BOLD, 13, DeckersPalette.text());
        name.setToolTipText(assignment.username());
        details.add(name);
        JLabel shift = detailLabel(shiftNameLabel(assignment), Font.BOLD, 11,
                assignment.shiftId() == null ? DeckersPalette.CORAL : DeckersPalette.blend(DeckersPalette.muted(), employeeAccent, 0.35));
        details.add(Box.createVerticalStrut(2));
        details.add(shift);
        if (assignment.shiftId() != null && assignment.shiftStartTime() != null && assignment.shiftEndTime() != null) {
            details.add(Box.createVerticalStrut(1));
            details.add(detailLabel("Hours " + timeRange(assignment.shiftStartTime(), assignment.shiftEndTime()),
                    Font.PLAIN, 11, DeckersPalette.muted()));
        }
        details.add(Box.createVerticalStrut(1));
        details.add(detailLabel(lunchLabel(assignment.lunchStartTime()), Font.PLAIN, 11,
                assignment.lunchStartTime() == null ? DeckersPalette.CORAL : DeckersPalette.muted()));
        row.add(details, BorderLayout.CENTER);

        if (canEdit) {
            JPanel actions = new JPanel(new GridLayout(1, 2, 6, 0));
            actions.setOpaque(false);
            JButton edit = textActionButton("Edit", DeckersPalette.text(), 11);
            edit.setToolTipText("Change " + assignment.displayName() + "'s shift or lunch time");
            edit.addActionListener(e -> editAssignment(assignment));
            JButton remove = textActionButton("Remove", DeckersPalette.CORAL, 11);
            remove.setToolTipText("Remove " + assignment.displayName() + " from this day");
            remove.addActionListener(e -> removeAssignment(assignment));
            actions.add(edit);
            actions.add(remove);
            row.add(actions, BorderLayout.SOUTH);
        }
        return row;
    }

    private JPanel buildCompactAssignmentRow(Assignment assignment) {
        Color employeeAccent = colorForEmployee(assignment.userId());
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        row.setBackground(DeckersPalette.tileFill(employeeAccent));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(employeeAccent)),
                new EmptyBorder(5, 7, 5, 7)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel name = detailLabel(assignment.displayName(), Font.BOLD, 11, DeckersPalette.text());
        name.setToolTipText(assignment.displayName() + " • " + compactShiftTime(assignment));
        JLabel shiftTime = detailLabel(compactShiftTime(assignment), Font.BOLD, 10,
                assignment.shiftId() == null ? DeckersPalette.CORAL
                        : DeckersPalette.blend(DeckersPalette.muted(), employeeAccent, 0.35));
        row.add(name, BorderLayout.CENTER);
        row.add(shiftTime, BorderLayout.EAST);
        return row;
    }

    private void showEmployeePicker(LocalDate date, List<Assignment> currentAssignments) {
        try {
            Set<Integer> alreadyScheduled = new HashSet<>();
            currentAssignments.forEach(assignment -> alreadyScheduled.add(assignment.userId()));
            List<Employee> available = EmployeeScheduleService.loadActiveEmployees(locationId).stream()
                    .filter(employee -> !alreadyScheduled.contains(employee.userId())).toList();
            List<Shift> shifts = EmployeeScheduleService.loadShifts(locationId, false);
            if (available.isEmpty()) {
                JOptionPane.showMessageDialog(this, "All active employees are already scheduled for this day.",
                        "No Employees Available", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (shifts.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This store has no active shifts. Use Manage Shifts to add or reactivate one.",
                        "No Shifts Available", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JList<Employee> employeeList = createEmployeeList(available);
            JComboBox<Shift> shiftBox = createShiftBox(shifts);
            LunchStartControl lunchControl = new LunchStartControl(LocalTime.of(12, 30));
            JPanel picker = new JPanel(new BorderLayout(0, 10));
            JPanel fields = new JPanel(new GridLayout(0, 1, 4, 4));
            fields.add(new JLabel("Select an active employee:"));
            fields.add(new JLabel("Shift:"));
            fields.add(shiftBox);
            fields.add(lunchControl.panel());
            picker.add(fields, BorderLayout.NORTH);
            picker.add(new JScrollPane(employeeList), BorderLayout.CENTER);
            int result = JOptionPane.showConfirmDialog(this, picker,
                    "Add employee • " + selectedLocation.name() + " • " + DAY_DATE.format(date),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            Employee selectedEmployee = employeeList.getSelectedValue();
            Shift selectedShift = (Shift) shiftBox.getSelectedItem();
            LocalTime lunchStart = lunchControl.selectedTime();
            if (selectedEmployee == null || selectedShift == null || lunchStart == null) {
                JOptionPane.showMessageDialog(this, "Select an employee, shift, and valid lunch time.",
                        "Schedule Details Needed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            EmployeeScheduleService.addEmployees(locationId, date, List.of(selectedEmployee),
                    selectedShift.shiftId(), lunchStart);
            loadPeriod();
        } catch (SQLException ex) {
            showScheduleError("Failed to add the employee", ex);
        }
    }

    private void editAssignment(Assignment assignment) {
        try {
            List<Shift> shifts = EmployeeScheduleService.loadShifts(locationId, false);
            if (shifts.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This store has no active shifts.", "No Shifts Available", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JComboBox<Shift> shiftBox = createShiftBox(shifts);
            if (assignment.shiftId() != null) selectShift(shiftBox, assignment.shiftId());
            LunchStartControl lunchControl = new LunchStartControl(assignment.lunchStartTime());
            JPanel panel = new JPanel(new GridLayout(0, 1, 5, 5));
            panel.add(new JLabel("Shift:"));
            panel.add(shiftBox);
            panel.add(lunchControl.panel());
            int result = JOptionPane.showConfirmDialog(this, panel,
                    "Edit assignment • " + assignment.displayName(),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            Shift shift = (Shift) shiftBox.getSelectedItem();
            LocalTime lunch = lunchControl.selectedTime();
            if (shift == null || lunch == null) {
                JOptionPane.showMessageDialog(this, "Select a shift and valid lunch time.",
                        "Schedule Details Needed", JOptionPane.WARNING_MESSAGE);
                return;
            }
            EmployeeScheduleService.updateAssignment(locationId, assignment.userId(), assignment.workDate(),
                    shift.shiftId(), lunch);
            loadPeriod();
        } catch (SQLException ex) {
            showScheduleError("Failed to update the assignment", ex);
        }
    }

    private void showAutoSchedulePreview() {
        Set<Integer> selectedEmployeeIds;
        try {
            selectedEmployeeIds = selectAutoScheduleEmployees();
        } catch (SQLException ex) {
            showScheduleError("Failed to load employees for Auto Schedule", ex);
            return;
        }
        if (selectedEmployeeIds == null) return;
        if (selectedEmployeeIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select at least one employee to include in the automatic schedule.",
                    "Employees Needed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dialog = new JDialog(this, "Auto Schedule • " + selectedLocation.name(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getRootPane().setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("Balanced schedule preview • " + WEEK_RANGE.format(periodStart)
                + " – " + WEEK_RANGE.format(periodEnd));
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel status = new JLabel("Generating with " + selectedEmployeeIds.size() + " selected employee"
                + (selectedEmployeeIds.size() == 1 ? "" : "s") + "…");
        status.setForeground(DeckersPalette.muted());
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(4));
        heading.add(status);
        dialog.add(heading, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Day", "Employee", "Status", "Shift", "Lunch"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(120);
        table.getColumnModel().getColumn(1).setPreferredWidth(190);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(210);
        table.getColumnModel().getColumn(4).setPreferredWidth(110);

        JTextArea summary = new JTextArea();
        summary.setEditable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setFont(new Font("Monospaced", Font.PLAIN, 12));
        summary.setBorder(new EmptyBorder(8, 8, 8, 8));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(table), new JScrollPane(summary));
        split.setResizeWeight(0.68);
        split.setDividerLocation(410);
        dialog.add(split, BorderLayout.CENTER);

        JButton regenerate = new JButton("Regenerate");
        JButton apply = new JButton("Apply Schedule");
        JButton cancel = new JButton("Cancel");
        for (JButton button : List.of(regenerate, apply, cancel)) DeckersSwing.styleUtilityButton(button, ACCENT);
        apply.setEnabled(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(regenerate);
        actions.add(apply);
        actions.add(cancel);
        dialog.add(actions, BorderLayout.SOUTH);

        final AutoScheduleProposal[] current = new AutoScheduleProposal[1];
        Runnable generate = () -> {
            regenerate.setEnabled(false);
            apply.setEnabled(false);
            status.setText("Generating with " + selectedEmployeeIds.size() + " selected employee"
                    + (selectedEmployeeIds.size() == 1 ? "" : "s") + "…");
            model.setRowCount(0);
            summary.setText("");
            new SwingWorker<AutoScheduleProposal, Void>() {
                @Override
                protected AutoScheduleProposal doInBackground() throws Exception {
                    return EmployeeAutoScheduleService.generateRange(
                            locationId, periodStart, periodEnd, selectedEmployeeIds);
                }

                @Override
                protected void done() {
                    regenerate.setEnabled(true);
                    try {
                        AutoScheduleProposal proposal = get();
                        current[0] = proposal;
                        populateAutoSchedulePreview(model, summary, proposal);
                        int additions = proposal.proposedEntries().size();
                        long warningCount = proposal.warnings().stream()
                                .filter(warning -> warning.level() == EmployeeAutoScheduleService.WarningLevel.WARNING).count();
                        status.setText(additions + " new assignment" + (additions == 1 ? "" : "s")
                                + " proposed • " + warningCount + " warning" + (warningCount == 1 ? "" : "s")
                                + " • existing assignments will not change");
                        apply.setEnabled(additions > 0 && proposal.entries().stream()
                                .filter(ScheduleEntry::proposed).allMatch(entry -> entry.shiftId() != null && entry.lunchStartTime() != null));
                    } catch (Exception ex) {
                        current[0] = null;
                        status.setText("Automatic schedule could not be generated.");
                        JOptionPane.showMessageDialog(dialog, rootMessage(ex), "Auto Schedule Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        };

        regenerate.addActionListener(e -> generate.run());
        apply.addActionListener(e -> {
            AutoScheduleProposal proposal = current[0];
            if (proposal == null) return;
            int warningCount = (int) proposal.warnings().stream()
                    .filter(warning -> warning.level() == EmployeeAutoScheduleService.WarningLevel.WARNING).count();
            String message = "Add " + proposal.proposedEntries().size() + " assignments to " + selectedLocation.name() + "?\n"
                    + "Existing assignments will remain unchanged."
                    + (warningCount == 0 ? "" : "\n\nThis proposal has " + warningCount + " warning"
                    + (warningCount == 1 ? "." : "s. Review the summary before continuing."));
            int choice = JOptionPane.showConfirmDialog(dialog, message, "Apply Automatic Schedule",
                    JOptionPane.YES_NO_OPTION, warningCount == 0 ? JOptionPane.QUESTION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
            regenerate.setEnabled(false);
            apply.setEnabled(false);
            cancel.setEnabled(false);
            status.setText("Applying the exact preview…");
            new SwingWorker<Integer, Void>() {
                @Override
                protected Integer doInBackground() throws Exception {
                    return EmployeeAutoScheduleService.apply(proposal);
                }

                @Override
                protected void done() {
                    try {
                        int inserted = get();
                        dialog.dispose();
                        loadPeriod();
                        JOptionPane.showMessageDialog(WeeklySchedule.this,
                                "Automatic schedule applied.\n" + inserted + " assignment"
                                        + (inserted == 1 ? " was" : "s were") + " added.",
                                "Schedule Applied", JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception ex) {
                        regenerate.setEnabled(true);
                        cancel.setEnabled(true);
                        status.setText("The proposal was not applied. Regenerate before trying again.");
                        JOptionPane.showMessageDialog(dialog, rootMessage(ex), "Auto Schedule Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });
        cancel.addActionListener(e -> dialog.dispose());

        dialog.setSize(1040, 760);
        dialog.setLocationRelativeTo(this);
        generate.run();
        dialog.setVisible(true);
    }

    private void clearVisibleSchedule() {
        String range = WEEK_RANGE.format(periodStart) + " – " + WEEK_RANGE.format(periodEnd)
                + ", " + periodEnd.getYear();
        int choice = JOptionPane.showConfirmDialog(this,
                "Clear every scheduled employee from " + selectedLocation.name() + "\n"
                        + "for " + range + "?\n\n"
                        + "This removes manual and automatically generated assignments shown on this screen.\n"
                        + "Holidays, shifts, other stores, and other schedule periods will not change.",
                "Clear Current Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            int removed = EmployeeScheduleService.clearSchedule(locationId, periodStart, periodEnd);
            loadPeriod();
            JOptionPane.showMessageDialog(this,
                    removed == 0
                            ? "There were no assignments to clear in this period."
                            : removed + " schedule assignment" + (removed == 1 ? " was" : "s were") + " removed.",
                    "Schedule Cleared", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            showScheduleError("Failed to clear the current schedule", ex);
        }
    }

    private void exportVisibleSchedule() {
        Object[] formats = {"PDF", "PNG Image", "Cancel"};
        int formatChoice = JOptionPane.showOptionDialog(this,
                "Export " + selectedLocation.name() + " for the currently visible schedule period.",
                "Export Schedule", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, formats, formats[0]);
        if (formatChoice < 0 || formatChoice == 2) return;
        boolean pdf = formatChoice == 0;
        String extension = pdf ? "pdf" : "png";
        String safeStore = selectedLocation.name().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        String filename = "smartstock-schedule-" + safeStore + "-" + periodStart + "-to-" + periodEnd
                + "." + extension;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Schedule " + (pdf ? "PDF" : "Image"));
        chooser.setSelectedFile(new File(filename));
        chooser.setFileFilter(new FileNameExtensionFilter(
                pdf ? "PDF documents (*.pdf)" : "PNG images (*.png)", extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File output = chooser.getSelectedFile();
        if (!output.getName().toLowerCase().endsWith("." + extension)) {
            output = new File(output.getParentFile(), output.getName() + "." + extension);
        }
        if (output.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                    "Replace the existing file?\n" + output.getAbsolutePath(),
                    "Replace Export", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            Map<LocalDate, List<Assignment>> assignments =
                    EmployeeScheduleService.loadRange(locationId, periodStart, periodEnd);
            Map<LocalDate, Holiday> exportHolidays = EmployeeScheduleService.loadHolidays(periodStart, periodEnd);
            BufferedImage companyLogo = CompanyCustomizationManager.loadCompanyLogo(
                    CompanyCustomizationManager.loadReceiptSettings());
            int columns = selectedViewMode() == ScheduleViewMode.SEMI_MONTHLY ? 8 : 7;
            boolean compact = selectedDisplayMode() == ScheduleDisplayMode.COMPACT;
            if (pdf) {
                ScheduleExportService.writePdf(output, selectedLocation.name(), periodStart, periodEnd,
                        columns, compact, assignments, exportHolidays, companyLogo);
            } else {
                ScheduleExportService.writePng(output, selectedLocation.name(), periodStart, periodEnd,
                        columns, compact, assignments, exportHolidays, companyLogo);
            }
            JOptionPane.showMessageDialog(this,
                    "Schedule exported successfully.\n" + output.getAbsolutePath(),
                    "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException | IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to export the schedule:\n" + ex.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private Set<Integer> selectAutoScheduleEmployees() throws SQLException {
        List<Employee> employees = EmployeeScheduleService.loadActiveEmployees(locationId);
        if (employees.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "This store has no active employees available for automatic scheduling.",
                    "No Employees Available", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        JPanel employeeRows = new JPanel();
        employeeRows.setLayout(new BoxLayout(employeeRows, BoxLayout.Y_AXIS));
        List<JCheckBox> boxes = new ArrayList<>();
        Map<JCheckBox, Employee> employeesByBox = new LinkedHashMap<>();
        for (Employee employee : employees) {
            JCheckBox box = new JCheckBox(employee.displayName() + "  (" + employee.username() + ")", true);
            box.setBorder(new EmptyBorder(5, 6, 5, 6));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            boxes.add(box);
            employeesByBox.put(box, employee);
            employeeRows.add(box);
        }

        JButton selectAll = new JButton("Select All");
        JButton clear = new JButton("Clear");
        DeckersSwing.styleUtilityButton(selectAll, ACCENT);
        DeckersSwing.styleUtilityButton(clear, DeckersPalette.muted());
        selectAll.addActionListener(e -> boxes.forEach(box -> box.setSelected(true)));
        clear.addActionListener(e -> boxes.forEach(box -> box.setSelected(false)));

        JPanel selectionActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        selectionActions.add(selectAll);
        selectionActions.add(clear);

        JLabel explanation = new JLabel("<html>Select only the employees Auto Schedule should use at "
                + selectedLocation.name() + ".<br>Unselected employees can still be scheduled manually here or at another store."
                + "<br>Existing assignments will remain unchanged.</html>");
        explanation.setForeground(DeckersPalette.muted());
        explanation.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JScrollPane employeeScroll = new JScrollPane(employeeRows,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        employeeScroll.setPreferredSize(new Dimension(440, Math.min(360, 48 + employees.size() * 34)));
        employeeScroll.getVerticalScrollBar().setUnitIncrement(12);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(explanation, BorderLayout.NORTH);
        panel.add(employeeScroll, BorderLayout.CENTER);
        panel.add(selectionActions, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(this, panel,
                "Choose Employees • Auto Schedule", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return null;

        Set<Integer> selected = new HashSet<>();
        for (Map.Entry<JCheckBox, Employee> entry : employeesByBox.entrySet()) {
            if (entry.getKey().isSelected()) selected.add(entry.getValue().userId());
        }
        return Set.copyOf(selected);
    }

    private static void populateAutoSchedulePreview(DefaultTableModel model, JTextArea summary,
                                                    AutoScheduleProposal proposal) {
        model.setRowCount(0);
        for (ScheduleEntry entry : proposal.entries()) {
            model.addRow(new Object[]{
                    entry.workDate().getDayOfWeek().toString().substring(0, 3) + " " + DAY_DATE.format(entry.workDate()),
                    entry.displayName(), entry.proposed() ? "Proposed" : "Existing",
                    entry.shiftName() == null ? "Shift not assigned"
                            : entry.shiftName() + " (" + timeRange(entry.shiftStartTime(), entry.shiftEndTime()) + ")",
                    entry.lunchStartTime() == null ? "Lunch not set" : DISPLAY_TIME.format(entry.lunchStartTime())
            });
        }
        StringBuilder text = new StringBuilder("DAILY COVERAGE\n");
        for (DailyCoverage coverage : proposal.dailyCoverage()) {
            text.append(coverage.workDate().getDayOfWeek().toString().substring(0, 3)).append(' ')
                    .append(DAY_DATE.format(coverage.workDate())).append(": ")
                    .append(coverage.totalCount()).append(" staff (" )
                    .append(coverage.existingCount()).append(" existing + ")
                    .append(coverage.proposedCount()).append(" proposed) • ")
                    .append(coverage.shiftCounts()).append('\n');
        }
        text.append("\nWARNINGS AND NOTES\n");
        if (proposal.warnings().isEmpty()) {
            text.append("No warnings. Coverage, shifts, days off, and lunches are balanced.\n");
        } else {
            for (ScheduleWarning warning : proposal.warnings()) {
                text.append(warning.level() == EmployeeAutoScheduleService.WarningLevel.WARNING ? "WARNING" : "NOTE");
                if (warning.workDate() != null) text.append(" • ").append(DAY_DATE.format(warning.workDate()));
                text.append(": ").append(warning.message()).append('\n');
            }
        }
        summary.setText(text.toString());
        summary.setCaretPosition(0);
    }

    private static String rootMessage(Exception ex) {
        Throwable current = ex;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.toString() : message;
    }

    private void showShiftManager() {
        JDialog dialog = new JDialog(this, "Manage Shifts • " + selectedLocation.name(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.getRootPane().setBorder(new EmptyBorder(12, 12, 12, 12));
        DefaultListModel<Shift> model = new DefaultListModel<>();
        JList<Shift> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focus);
                if (value instanceof Shift shift) {
                    label.setText(shift.name() + "  •  " + timeRange(shift.startTime(), shift.endTime())
                            + (shift.active() ? "" : "  •  Inactive"));
                    label.setBorder(new EmptyBorder(9, 8, 9, 8));
                }
                return label;
            }
        });
        Runnable reload = () -> {
            try {
                model.clear();
                EmployeeScheduleService.loadShifts(locationId, true).forEach(model::addElement);
                if (!model.isEmpty() && list.getSelectedIndex() < 0) list.setSelectedIndex(0);
            } catch (SQLException ex) {
                showScheduleError("Failed to load shifts", ex);
            }
        };

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton add = new JButton("Add");
        JButton edit = new JButton("Edit");
        JButton toggle = new JButton("Deactivate / Reactivate");
        JButton up = new JButton("Move Up");
        JButton down = new JButton("Move Down");
        JButton close = new JButton("Close");
        for (JButton button : List.of(add, edit, toggle, up, down, close)) {
            DeckersSwing.styleUtilityButton(button, ACCENT);
            actions.add(button);
        }
        add.addActionListener(e -> { if (editShift(null)) reload.run(); });
        edit.addActionListener(e -> { Shift shift = list.getSelectedValue(); if (shift != null && editShift(shift)) reload.run(); });
        toggle.addActionListener(e -> {
            Shift shift = list.getSelectedValue();
            if (shift == null) return;
            try {
                EmployeeScheduleService.saveShift(locationId, shift.shiftId(), shift.name(), shift.startTime(),
                        shift.endTime(), !shift.active(), shift.displayOrder(), false);
                reload.run();
            } catch (SQLException ex) {
                showScheduleError("Failed to update the shift", ex);
            }
        });
        up.addActionListener(e -> moveShift(model, list, -1));
        down.addActionListener(e -> moveShift(model, list, 1));
        close.addActionListener(e -> dialog.dispose());
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.add(actions, BorderLayout.SOUTH);
        dialog.setSize(760, 440);
        dialog.setLocationRelativeTo(this);
        reload.run();
        dialog.setVisible(true);
        loadPeriod();
    }

    private boolean editShift(Shift shift) {
        JTextField name = new JTextField(shift == null ? "" : shift.name(), 18);
        JTextField start = new JTextField(shift == null ? "" : DISPLAY_TIME.format(shift.startTime()), 10);
        JTextField end = new JTextField(shift == null ? "" : DISPLAY_TIME.format(shift.endTime()), 10);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Name:")); form.add(name);
        form.add(new JLabel("Starts:")); form.add(start);
        form.add(new JLabel("Ends:")); form.add(end);
        int result = JOptionPane.showConfirmDialog(this, form, shift == null ? "Add Shift" : "Edit Shift",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return false;
        LocalTime startTime = parseTime(start.getText());
        LocalTime endTime = parseTime(end.getText());
        if (startTime == null || endTime == null) {
            JOptionPane.showMessageDialog(this, "Enter times such as 7:00 AM and 4:00 PM.",
                    "Valid Times Needed", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        boolean propagate = false;
        if (shift != null && (!name.getText().trim().equals(shift.name())
                || !startTime.equals(shift.startTime()) || !endTime.equals(shift.endTime()))) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Update today's and future assignments that use this shift?\nPast assignments will not change.",
                    "Update Scheduled Assignments", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return false;
            propagate = choice == JOptionPane.YES_OPTION;
        }
        try {
            int order = shift == null ? nextShiftOrder() : shift.displayOrder();
            EmployeeScheduleService.saveShift(locationId, shift == null ? null : shift.shiftId(), name.getText(),
                    startTime, endTime, shift == null || shift.active(), order, propagate);
            return true;
        } catch (SQLException ex) {
            showScheduleError("Failed to save the shift", ex);
            return false;
        }
    }

    private int nextShiftOrder() throws SQLException {
        return EmployeeScheduleService.loadShifts(locationId, true).stream()
                .mapToInt(Shift::displayOrder).max().orElse(0) + 10;
    }

    private void moveShift(DefaultListModel<Shift> model, JList<Shift> list, int direction) {
        int from = list.getSelectedIndex();
        int to = from + direction;
        if (from < 0 || to < 0 || to >= model.size()) return;
        Shift moved = model.remove(from);
        model.add(to, moved);
        list.setSelectedIndex(to);
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < model.size(); index++) ids.add(model.get(index).shiftId());
        try {
            EmployeeScheduleService.updateShiftOrder(locationId, ids);
        } catch (SQLException ex) {
            showScheduleError("Failed to reorder shifts", ex);
        }
    }

    private void removeAssignment(Assignment assignment) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove " + assignment.displayName() + " from "
                        + assignment.workDate().getDayOfWeek().toString().toLowerCase() + "?",
                "Remove from Schedule", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            EmployeeScheduleService.removeEmployee(locationId, assignment.userId(), assignment.workDate());
            loadPeriod();
        } catch (SQLException ex) {
            showScheduleError("Failed to remove the employee", ex);
        }
    }

    private JList<Employee> createEmployeeList(List<Employee> employees) {
        JList<Employee> list = new JList<>(employees.toArray(Employee[]::new));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(Math.min(10, employees.size()));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focus);
                if (value instanceof Employee employee) {
                    label.setText(employee.displayName() + "  (" + employee.username() + ")");
                    label.setBorder(new EmptyBorder(7, 8, 7, 8));
                }
                return label;
            }
        });
        return list;
    }

    private JComboBox<Shift> createShiftBox(List<Shift> shifts) {
        JComboBox<Shift> box = new JComboBox<>(shifts.toArray(Shift[]::new));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focus);
                if (value instanceof Shift shift) {
                    label.setText(shift.name() + "  (" + timeRange(shift.startTime(), shift.endTime()) + ")");
                }
                return label;
            }
        });
        return box;
    }

    private static void selectShift(JComboBox<Shift> box, UUID shiftId) {
        for (int index = 0; index < box.getItemCount(); index++) {
            if (shiftId.equals(box.getItemAt(index).shiftId())) {
                box.setSelectedIndex(index);
                return;
            }
        }
    }

    private Color colorForEmployee(int userId) {
        return employeeAccents.computeIfAbsent(userId, ignored -> {
            int colorIndex = employeeAccents.size();
            if (colorIndex < EMPLOYEE_ACCENTS.size()) return EMPLOYEE_ACCENTS.get(colorIndex);
            float hue = (float) ((colorIndex * 0.61803398875d) % 1d);
            return Color.getHSBColor(hue, DeckersPalette.dark() ? 0.58f : 0.68f,
                    DeckersPalette.dark() ? 0.82f : 0.88f);
        });
    }

    private static JLabel detailLabel(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", style, size));
        label.setForeground(color);
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    private static JButton textActionButton(String text, Color color, int size) {
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, size));
        button.setForeground(color);
        button.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static String shiftNameLabel(Assignment assignment) {
        if (assignment.shiftId() == null || assignment.shiftStartTime() == null || assignment.shiftEndTime() == null) {
            return "Shift not assigned";
        }
        return assignment.shiftName() == null || assignment.shiftName().isBlank() ? "Shift" : assignment.shiftName();
    }

    private static String lunchLabel(LocalTime lunchStart) {
        if (lunchStart == null) return "Lunch not set";
        return "Lunch " + timeRange(lunchStart, lunchStart.plusMinutes(EmployeeScheduleService.LUNCH_DURATION_MINUTES));
    }

    private static String compactShiftTime(Assignment assignment) {
        if (assignment.shiftId() == null || assignment.shiftStartTime() == null || assignment.shiftEndTime() == null) {
            return "No shift";
        }
        return compactTime(assignment.shiftStartTime()) + "–" + compactTime(assignment.shiftEndTime());
    }

    private static String compactTime(LocalTime time) {
        return (time.getMinute() == 0 ? COMPACT_TIME : DISPLAY_TIME).format(time);
    }

    private static String timeRange(LocalTime start, LocalTime end) {
        return DISPLAY_TIME.format(start) + "–" + DISPLAY_TIME.format(end);
    }

    private static LocalDate startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static final class CalendarGridPanel extends JPanel implements Scrollable {
        private static final int MINIMUM_GRID_WIDTH = 1440;

        private CalendarGridPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setMinimumSize(new Dimension(MINIMUM_GRID_WIDTH, 0));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.HORIZONTAL
                    ? Math.max(180, visibleRect.width - 180)
                    : Math.max(180, visibleRect.height - 120);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport viewport && viewport.getWidth() >= MINIMUM_GRID_WIDTH;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private ScheduleViewMode selectedViewMode() {
        Object selected = viewBox.getSelectedItem();
        return selected instanceof ScheduleViewMode mode ? mode : ScheduleViewMode.SEMI_MONTHLY;
    }

    private ScheduleDisplayMode selectedDisplayMode() {
        Object selected = displayBox.getSelectedItem();
        return selected instanceof ScheduleDisplayMode mode ? mode : ScheduleDisplayMode.DETAILED;
    }

    private LocalDate scheduleToday() {
        String timezone = selectedLocation == null ? SessionManager.getCurrentLocationTimezone() : selectedLocation.timezone();
        try {
            return LocalDate.now(ZoneId.of(timezone == null || timezone.isBlank()
                    ? ZoneId.systemDefault().getId() : timezone));
        } catch (Exception ignored) {
            return LocalDate.now();
        }
    }

    private void setCurrentPeriod(LocalDate date) {
        if (selectedViewMode() == ScheduleViewMode.WEEKLY) {
            periodStart = startOfWeek(date);
            periodEnd = periodStart.plusDays(6);
        } else if (date.getDayOfMonth() <= 15) {
            periodStart = date.withDayOfMonth(1);
            periodEnd = date.withDayOfMonth(15);
        } else {
            periodStart = date.withDayOfMonth(16);
            periodEnd = date.withDayOfMonth(date.lengthOfMonth());
        }
    }

    private void movePeriod(int direction) {
        if (selectedViewMode() == ScheduleViewMode.WEEKLY) {
            periodStart = periodStart.plusWeeks(direction);
            periodEnd = periodStart.plusDays(6);
            return;
        }
        if (direction < 0) {
            setCurrentPeriod(periodStart.getDayOfMonth() == 16
                    ? periodStart.withDayOfMonth(1)
                    : periodStart.minusMonths(1).withDayOfMonth(16));
        } else {
            setCurrentPeriod(periodStart.getDayOfMonth() == 1
                    ? periodStart.withDayOfMonth(16)
                    : periodStart.plusMonths(1).withDayOfMonth(1));
        }
    }

    private void showScheduleError(String action, SQLException ex) {
        JOptionPane.showMessageDialog(this, action + ":\n" + ex.getMessage(),
                "Schedule Error", JOptionPane.ERROR_MESSAGE);
    }

    private static LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim().toUpperCase();
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("h:mm a"), DateTimeFormatter.ofPattern("h a"),
                DateTimeFormatter.ofPattern("H:mm"), DateTimeFormatter.ofPattern("H"))) {
            try {
                return LocalTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private static final class LunchStartControl {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JComboBox<String> presets = new JComboBox<>();
        private final JTextField customTime = new JTextField(10);
        private final JLabel endTime = new JLabel();

        private LunchStartControl(LocalTime current) {
            panel.setOpaque(false);
            for (LocalTime preset : LUNCH_PRESETS) presets.addItem(DISPLAY_TIME.format(preset));
            presets.addItem("Custom time…");
            int presetIndex = current == null ? 2 : LUNCH_PRESETS.indexOf(current);
            if (presetIndex >= 0) {
                presets.setSelectedIndex(presetIndex);
            } else {
                presets.setSelectedItem("Custom time…");
                if (current != null) customTime.setText(DISPLAY_TIME.format(current));
            }
            customTime.setEnabled("Custom time…".equals(presets.getSelectedItem()));

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(2, 0, 2, 8);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.gridx = 0; constraints.gridy = 0;
            panel.add(new JLabel("Lunch starts:"), constraints);
            constraints.gridx = 1; constraints.weightx = 1; constraints.fill = GridBagConstraints.HORIZONTAL;
            panel.add(presets, constraints);
            constraints.gridx = 2; constraints.weightx = 0;
            panel.add(customTime, constraints);
            constraints.gridx = 0; constraints.gridy = 1; constraints.gridwidth = 3;
            constraints.insets = new Insets(2, 0, 0, 0);
            endTime.setFont(new Font("SansSerif", Font.PLAIN, 12));
            endTime.setForeground(DeckersPalette.muted());
            endTime.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
            panel.add(endTime, constraints);

            presets.addActionListener(e -> {
                customTime.setEnabled("Custom time…".equals(presets.getSelectedItem()));
                updateEndTime();
            });
            customTime.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { updateEndTime(); }
                @Override public void removeUpdate(DocumentEvent e) { updateEndTime(); }
                @Override public void changedUpdate(DocumentEvent e) { updateEndTime(); }
            });
            updateEndTime();
        }

        private JPanel panel() { return panel; }

        private LocalTime selectedTime() {
            Object selection = presets.getSelectedItem();
            if (!"Custom time…".equals(selection)) {
                int index = presets.getSelectedIndex();
                return index >= 0 && index < LUNCH_PRESETS.size() ? LUNCH_PRESETS.get(index) : null;
            }
            return parseTime(customTime.getText());
        }

        private void updateEndTime() {
            LocalTime start = selectedTime();
            endTime.setText(start == null ? "Enter a time such as 12:30 PM."
                    : "Lunch ends at " + DISPLAY_TIME.format(start.plusMinutes(EmployeeScheduleService.LUNCH_DURATION_MINUTES))
                    + " (" + EmployeeScheduleService.LUNCH_DURATION_MINUTES + " minutes)");
        }
    }
}
