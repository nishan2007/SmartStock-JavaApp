package ui.screens.customorders;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

public class DatePickerField extends JTextField {
    private static final String PLACEHOLDER = "YYYY-MM-DD";

    public DatePickerField() {
        setPreferredSize(new Dimension(100, 30));
        clearDate();
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showDatePicker();
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (isPlaceholder()) {
                    setText("");
                    setForeground(Color.WHITE);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (getText().trim().isEmpty()) {
                    clearDate();
                }
            }
        });
    }

    public LocalDate getSelectedDate() throws DateTimeParseException {
        String text = getText().trim();
        if (text.isEmpty() || PLACEHOLDER.equals(text)) {
            return null;
        }
        return LocalDate.parse(text);
    }

    public void clearDate() {
        setText(PLACEHOLDER);
        setForeground(Color.GRAY);
    }

    private boolean isPlaceholder() {
        return PLACEHOLDER.equals(getText());
    }

    private void showDatePicker() {
        LocalDate selectedDate = LocalDate.now();
        try {
            LocalDate parsedDate = getSelectedDate();
            if (parsedDate != null) {
                selectedDate = parsedDate;
            }
        } catch (DateTimeParseException ignored) {
            selectedDate = LocalDate.now();
        }

        JDialog dialog = createDialog();
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setSize(340, 300);
        dialog.setLocationRelativeTo(this);

        final YearMonth[] visibleMonth = {YearMonth.from(selectedDate)};
        JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        JButton previousButton = new JButton("<");
        JButton nextButton = new JButton(">");
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(new EmptyBorder(8, 8, 0, 8));
        header.add(previousButton, BorderLayout.WEST);
        header.add(monthLabel, BorderLayout.CENTER);
        header.add(nextButton, BorderLayout.EAST);

        JPanel daysPanel = new JPanel(new GridLayout(7, 7, 4, 4));
        daysPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        for (String day : new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"}) {
            daysPanel.add(new JLabel(day, SwingConstants.CENTER));
        }
        JButton[] dayButtons = new JButton[42];
        for (int i = 0; i < dayButtons.length; i++) {
            JButton button = new JButton("");
            button.setFocusPainted(false);
            dayButtons[i] = button;
            daysPanel.add(button);
        }

        Runnable refreshCalendar = () -> {
            YearMonth month = visibleMonth[0];
            monthLabel.setText(month.getMonth() + " " + month.getYear());
            for (JButton button : dayButtons) {
                for (java.awt.event.ActionListener listener : button.getActionListeners()) {
                    button.removeActionListener(listener);
                }
                button.setText("");
                button.setEnabled(false);
            }
            int startIndex = month.atDay(1).getDayOfWeek().getValue() % 7;
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                JButton button = dayButtons[startIndex + day - 1];
                button.setText(String.valueOf(day));
                button.setEnabled(true);
                button.addActionListener(e -> {
                    setText(date.toString());
                    setForeground(Color.WHITE);
                    dialog.dispose();
                });
            }
        };

        previousButton.addActionListener(e -> {
            visibleMonth[0] = visibleMonth[0].minusMonths(1);
            refreshCalendar.run();
        });
        nextButton.addActionListener(e -> {
            visibleMonth[0] = visibleMonth[0].plusMonths(1);
            refreshCalendar.run();
        });

        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(e -> {
            clearDate();
            dialog.dispose();
        });
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottom.setBorder(new EmptyBorder(0, 8, 8, 8));
        bottom.add(clearButton);

        refreshCalendar.run();
        dialog.add(header, BorderLayout.NORTH);
        dialog.add(daysPanel, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JDialog createDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        if (owner instanceof Frame frame) {
            return new JDialog(frame, "Select Due Date", true);
        }
        if (owner instanceof Dialog dialog) {
            return new JDialog(dialog, "Select Due Date", true);
        }
        return new JDialog((Frame) null, "Select Due Date", true);
    }
}
