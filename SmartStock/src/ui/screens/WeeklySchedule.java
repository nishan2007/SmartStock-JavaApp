package ui.screens;

import managers.PermissionManager;
import managers.SessionManager;
import services.EmployeeScheduleService;
import services.EmployeeScheduleService.Assignment;
import services.EmployeeScheduleService.Employee;
import ui.components.AppMenuBar;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class WeeklySchedule extends JFrame {
    private static final DateTimeFormatter WEEK_RANGE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter DAY_DATE = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter LUNCH_TIME = DateTimeFormatter.ofPattern("h:mm a");
    private static final List<LocalTime> LUNCH_PRESETS = List.of(
            LocalTime.of(11, 30), LocalTime.NOON, LocalTime.of(12, 30),
            LocalTime.of(13, 0), LocalTime.of(13, 30), LocalTime.of(14, 0)
    );
    private static final Color ACCENT = DeckersPalette.YELLOW;
    private static final List<Color> EMPLOYEE_ACCENTS = List.of(
            DeckersPalette.ORANGE,
            DeckersPalette.MAGENTA,
            DeckersPalette.LIME,
            DeckersPalette.YELLOW,
            DeckersPalette.PURPLE,
            DeckersPalette.CORAL
    );

    private final boolean canEdit;
    private final int locationId;
    private LocalDate weekStart;
    private final JLabel weekLabel = new JLabel();
    private final JLabel statusLabel = new JLabel(" ");
    private final JPanel daysPanel = new JPanel(new GridLayout(1, 7, 10, 0));
    private final Map<Integer, Color> employeeAccents = new HashMap<>();

    public WeeklySchedule() {
        setTitle("Weekly Schedule");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 650));
        setSize(1320, 760);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "WeeklySchedule"));

        if (!PermissionManager.hasPermission("VIEW_EMPLOYEE_SCHEDULE")) {
            JOptionPane.showMessageDialog(this,
                    "You do not have permission to view the employee schedule.",
                    "Access Denied",
                    JOptionPane.WARNING_MESSAGE);
            dispose();
            canEdit = false;
            locationId = -1;
            return;
        }

        canEdit = PermissionManager.hasPermission("EDIT_EMPLOYEE_SCHEDULE");
        locationId = SessionManager.getCurrentLocationId() == null ? 1 : SessionManager.getCurrentLocationId();
        weekStart = startOfWeek(LocalDate.now());

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        root.setBackground(DeckersPalette.background());
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.add(buildHeader(), BorderLayout.NORTH);

        daysPanel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(daysPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(DeckersPalette.background());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scrollPane, BorderLayout.CENTER);

        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        statusLabel.setForeground(DeckersPalette.muted());
        statusLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        root.add(statusLabel, BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        loadWeek();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 12));
        header.setOpaque(false);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Weekly Schedule");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel subtitle = new JLabel(canEdit
                ? "Choose a day to add active employees. Remove a name if plans change."
                : "See who is scheduled to work each day.");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(DeckersPalette.muted());
        subtitle.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        navigation.setOpaque(false);
        JButton previous = new JButton("Previous week");
        JButton today = new JButton("This week");
        JButton next = new JButton("Next week");
        for (JButton button : List.of(previous, today, next)) {
            DeckersSwing.styleUtilityButton(button, ACCENT);
        }
        weekLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        weekLabel.setForeground(DeckersPalette.text());
        weekLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        previous.addActionListener(e -> {
            weekStart = weekStart.minusWeeks(1);
            loadWeek();
        });
        today.addActionListener(e -> {
            weekStart = startOfWeek(LocalDate.now());
            loadWeek();
        });
        next.addActionListener(e -> {
            weekStart = weekStart.plusWeeks(1);
            loadWeek();
        });

        navigation.add(previous);
        navigation.add(today);
        navigation.add(next);
        navigation.add(Box.createHorizontalStrut(8));
        navigation.add(weekLabel);
        header.add(titles, BorderLayout.WEST);
        header.add(navigation, BorderLayout.EAST);
        return header;
    }

    private void loadWeek() {
        weekLabel.setText(WEEK_RANGE.format(weekStart) + " – " + WEEK_RANGE.format(weekStart.plusDays(6))
                + ", " + weekStart.getYear());
        statusLabel.setText("Loading schedule…");
        try {
            Map<LocalDate, List<Assignment>> assignments = EmployeeScheduleService.loadWeek(locationId, weekStart);
            renderDays(assignments);
            int count = assignments.values().stream().mapToInt(List::size).sum();
            statusLabel.setText(count == 0
                    ? "No one is scheduled for this week yet."
                    : count + (count == 1 ? " scheduled work day" : " scheduled work days")
                    + " • " + safe(SessionManager.getCurrentLocationName()));
        } catch (SQLException ex) {
            daysPanel.removeAll();
            daysPanel.revalidate();
            daysPanel.repaint();
            statusLabel.setText("Schedule could not be loaded.");
            JOptionPane.showMessageDialog(this,
                    "Failed to load the employee schedule:\n" + ex.getMessage(),
                    "Schedule Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void renderDays(Map<LocalDate, List<Assignment>> assignments) {
        daysPanel.removeAll();
        LocalDate today = LocalDate.now();
        for (int index = 0; index < 7; index++) {
            LocalDate date = weekStart.plusDays(index);
            daysPanel.add(buildDayPanel(date, assignments.getOrDefault(date, List.of()), date.equals(today)));
        }
        daysPanel.revalidate();
        daysPanel.repaint();
    }

    private JPanel buildDayPanel(LocalDate date, List<Assignment> assignments, boolean today) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        card.setBackground(DeckersPalette.surface());
        Color borderColor = today ? ACCENT : DeckersPalette.border();
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, today ? 2 : 1),
                new EmptyBorder(12, 10, 12, 10)
        ));
        card.setPreferredSize(new Dimension(155, 470));

        JPanel dayHeader = new JPanel();
        dayHeader.setOpaque(false);
        dayHeader.setLayout(new BoxLayout(dayHeader, BoxLayout.Y_AXIS));
        JLabel dayName = new JLabel(date.getDayOfWeek().toString().substring(0, 1)
                + date.getDayOfWeek().toString().substring(1).toLowerCase());
        dayName.setFont(new Font("SansSerif", Font.BOLD, 17));
        dayName.setForeground(DeckersPalette.text());
        dayName.setAlignmentX(Component.LEFT_ALIGNMENT);
        dayName.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JLabel dateLabel = new JLabel(DAY_DATE.format(date) + (today ? " • Today" : ""));
        dateLabel.setFont(new Font("SansSerif", today ? Font.BOLD : Font.PLAIN, 13));
        dateLabel.setForeground(today ? DeckersPalette.blend(DeckersPalette.text(), ACCENT, 0.35) : DeckersPalette.muted());
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        dayHeader.add(dayName);
        dayHeader.add(Box.createVerticalStrut(2));
        dayHeader.add(dateLabel);
        card.add(dayHeader, BorderLayout.NORTH);

        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        if (assignments.isEmpty()) {
            JLabel empty = new JLabel("Not scheduled");
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
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        namesScroll.setBorder(BorderFactory.createEmptyBorder());
        namesScroll.setOpaque(false);
        namesScroll.getViewport().setOpaque(false);
        namesScroll.getVerticalScrollBar().setUnitIncrement(12);
        card.add(namesScroll, BorderLayout.CENTER);

        if (canEdit) {
            JButton add = new JButton("+ Add employee");
            DeckersSwing.styleUtilityButton(add, ACCENT);
            add.addActionListener(e -> showEmployeePicker(date, assignments));
            card.add(add, BorderLayout.SOUTH);
        }
        return card;
    }

    private JPanel buildAssignmentRow(Assignment assignment) {
        Color employeeAccent = colorForEmployee(assignment.userId());
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        row.setBackground(DeckersPalette.tileFill(employeeAccent));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(employeeAccent)),
                new EmptyBorder(7, 8, 7, 6)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(assignment.displayName());
        name.setFont(new Font("SansSerif", Font.BOLD, 13));
        name.setForeground(DeckersPalette.text());
        name.setToolTipText(assignment.username());
        name.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        details.add(name);
        JLabel lunch = new JLabel(lunchLabel(assignment.lunchStartTime()));
        lunch.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lunch.setForeground(assignment.lunchStartTime() == null ? DeckersPalette.CORAL
                : DeckersPalette.blend(DeckersPalette.muted(), employeeAccent, 0.35));
        lunch.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        details.add(Box.createVerticalStrut(2));
        details.add(lunch);
        row.add(details, BorderLayout.CENTER);
        if (canEdit) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            actions.setOpaque(false);
            JButton editLunch = new JButton("Edit");
            editLunch.setToolTipText("Change " + assignment.displayName() + "'s lunch time");
            editLunch.setFont(new Font("SansSerif", Font.BOLD, 11));
            editLunch.setForeground(DeckersPalette.text());
            editLunch.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
            editLunch.setContentAreaFilled(false);
            editLunch.setFocusPainted(false);
            editLunch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            editLunch.addActionListener(e -> editLunch(assignment));
            JButton remove = new JButton("×");
            remove.setToolTipText("Remove " + assignment.displayName() + " from this day");
            remove.setFont(new Font("SansSerif", Font.BOLD, 17));
            remove.setForeground(DeckersPalette.CORAL);
            remove.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
            remove.setContentAreaFilled(false);
            remove.setFocusPainted(false);
            remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            remove.addActionListener(e -> removeAssignment(assignment));
            actions.add(editLunch);
            actions.add(remove);
            row.add(actions, BorderLayout.EAST);
        }
        return row;
    }

    private void showEmployeePicker(LocalDate date, List<Assignment> currentAssignments) {
        try {
            Set<Integer> alreadyScheduled = new HashSet<>();
            for (Assignment assignment : currentAssignments) {
                alreadyScheduled.add(assignment.userId());
            }
            List<Employee> available = EmployeeScheduleService.loadActiveEmployees(locationId).stream()
                    .filter(employee -> !alreadyScheduled.contains(employee.userId()))
                    .toList();
            if (available.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "All active employees are already scheduled for " + date.getDayOfWeek().toString().toLowerCase() + ".",
                        "No Employees Available",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JList<Employee> employeeList = new JList<>(available.toArray(Employee[]::new));
            employeeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            employeeList.setVisibleRowCount(Math.min(10, available.size()));
            employeeList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Employee employee) {
                        label.setText(employee.displayName() + "  (" + employee.username() + ")");
                        label.setBorder(new EmptyBorder(7, 8, 7, 8));
                    }
                    return label;
                }
            });
            LunchStartControl lunchControl = new LunchStartControl(LocalTime.of(12, 30));
            JPanel picker = new JPanel(new BorderLayout(0, 10));
            JPanel pickerHeader = new JPanel();
            pickerHeader.setOpaque(false);
            pickerHeader.setLayout(new BoxLayout(pickerHeader, BoxLayout.Y_AXIS));
            pickerHeader.add(new JLabel("Select an active employee:"));
            pickerHeader.add(Box.createVerticalStrut(8));
            pickerHeader.add(lunchControl.panel());
            picker.add(pickerHeader, BorderLayout.NORTH);
            picker.add(new JScrollPane(employeeList), BorderLayout.CENTER);
            int result = JOptionPane.showConfirmDialog(this, picker,
                    "Add employees • " + date.getDayOfWeek().toString().toLowerCase() + " " + DAY_DATE.format(date),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            List<Employee> selected = new ArrayList<>(employeeList.getSelectedValuesList());
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Select an employee to add.");
                return;
            }
            LocalTime lunchStart = lunchControl.selectedTime();
            if (lunchStart == null) {
                JOptionPane.showMessageDialog(this,
                        "Choose a preset lunch start or enter a custom time such as 12:30 PM.",
                        "Lunch Time Needed",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            EmployeeScheduleService.addEmployees(locationId, date, selected, lunchStart);
            loadWeek();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to add employees:\n" + ex.getMessage(),
                    "Schedule Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editLunch(Assignment assignment) {
        LunchStartControl lunchControl = new LunchStartControl(assignment.lunchStartTime());
        int result = JOptionPane.showConfirmDialog(this, lunchControl.panel(),
                "Lunch • " + assignment.displayName(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        LocalTime lunchStart = lunchControl.selectedTime();
        if (lunchStart == null) {
            JOptionPane.showMessageDialog(this,
                    "Choose a preset lunch start or enter a custom time such as 12:30 PM.",
                    "Lunch Time Needed",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            EmployeeScheduleService.updateLunchStartTime(
                    locationId, assignment.userId(), assignment.workDate(), lunchStart);
            loadWeek();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to update the lunch time:\n" + ex.getMessage(),
                    "Schedule Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeAssignment(Assignment assignment) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove " + assignment.displayName() + " from "
                        + assignment.workDate().getDayOfWeek().toString().toLowerCase() + "?",
                "Remove from Schedule",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            EmployeeScheduleService.removeEmployee(locationId, assignment.userId(), assignment.workDate());
            loadWeek();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to remove the employee:\n" + ex.getMessage(),
                    "Schedule Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static LocalDate startOfWeek(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Current location" : value;
    }

    private Color colorForEmployee(int userId) {
        return employeeAccents.computeIfAbsent(userId, ignored -> {
            int colorIndex = employeeAccents.size();
            if (colorIndex < EMPLOYEE_ACCENTS.size()) {
                return EMPLOYEE_ACCENTS.get(colorIndex);
            }
            // Golden-ratio hue stepping keeps generated accents visibly distinct after the Deckers palette is used.
            float hue = (float) ((colorIndex * 0.61803398875d) % 1d);
            return Color.getHSBColor(hue, DeckersPalette.dark() ? 0.58f : 0.68f,
                    DeckersPalette.dark() ? 0.82f : 0.88f);
        });
    }

    private static String lunchLabel(LocalTime lunchStart) {
        if (lunchStart == null) {
            return "Lunch not set";
        }
        return "Lunch " + LUNCH_TIME.format(lunchStart) + "–"
                + LUNCH_TIME.format(lunchStart.plusMinutes(EmployeeScheduleService.LUNCH_DURATION_MINUTES));
    }

    private static final class LunchStartControl {
        private final JPanel panel = new JPanel(new GridBagLayout());
        private final JComboBox<String> presets = new JComboBox<>();
        private final JTextField customTime = new JTextField(10);
        private final JLabel endTime = new JLabel();

        private LunchStartControl(LocalTime current) {
            panel.setOpaque(false);
            for (LocalTime preset : LUNCH_PRESETS) {
                presets.addItem(LUNCH_TIME.format(preset));
            }
            presets.addItem("Custom time…");

            int presetIndex = current == null ? 2 : LUNCH_PRESETS.indexOf(current);
            if (presetIndex >= 0) {
                presets.setSelectedIndex(presetIndex);
            } else {
                presets.setSelectedItem("Custom time…");
                if (current != null) {
                    customTime.setText(LUNCH_TIME.format(current));
                }
            }
            customTime.setEnabled("Custom time…".equals(presets.getSelectedItem()));

            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(2, 0, 2, 8);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.gridx = 0;
            constraints.gridy = 0;
            panel.add(new JLabel("Lunch starts:"), constraints);
            constraints.gridx = 1;
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            panel.add(presets, constraints);
            constraints.gridx = 2;
            constraints.weightx = 0;
            panel.add(customTime, constraints);
            constraints.gridx = 0;
            constraints.gridy = 1;
            constraints.gridwidth = 3;
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

        private JPanel panel() {
            return panel;
        }

        private LocalTime selectedTime() {
            Object selection = presets.getSelectedItem();
            if (!"Custom time…".equals(selection)) {
                return parseLunchTime(String.valueOf(selection));
            }
            return parseLunchTime(customTime.getText());
        }

        private void updateEndTime() {
            LocalTime start = selectedTime();
            endTime.setText(start == null
                    ? "Enter a lunch time like 12:30 PM."
                    : "Lunch ends at " + LUNCH_TIME.format(start.plusMinutes(EmployeeScheduleService.LUNCH_DURATION_MINUTES))
                    + " • fixed 45 minutes");
        }

        private static LocalTime parseLunchTime(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return LocalTime.parse(value.trim().toUpperCase(), LUNCH_TIME);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
