package ui.screens;

import managers.SessionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class ReceivingHistory extends JFrame {
    private final JTextField searchField = new JTextField();
    private final JTextField fromDateField = new JTextField();
    private final JTextField toDateField = new JTextField();
    private final JLabel storeLabel = new JLabel();
    private final JLabel summaryLabel = new JLabel("Records: 0   Units: 0");
    private final DefaultTableModel tableModel;
    private final JTable historyTable;

    public ReceivingHistory() {
        setTitle("Receiving History");
        setSize(1100, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "ReceivingHistory"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        root.add(buildHeaderPanel(), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
                new Object[]{"Receive ID", "Movement ID", "Date / Time", "Product", "SKU", "Store", "Qty Received", "Received By", "Note"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        historyTable = new JTable(tableModel);
        historyTable.setRowHeight(28);
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(210);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(170);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(220);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(140);
        historyTable.getColumnModel().getColumn(5).setPreferredWidth(170);
        historyTable.getColumnModel().getColumn(6).setPreferredWidth(110);
        historyTable.getColumnModel().getColumn(7).setPreferredWidth(170);
        historyTable.getColumnModel().getColumn(8).setPreferredWidth(260);

        root.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        add(root);

        loadReceivingHistory();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 14));
        headerPanel.setOpaque(false);

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Receiving History");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(32, 41, 57));

        updateStoreLabel();
        storeLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        storeLabel.setForeground(new Color(101, 116, 139));

        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(storeLabel, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBackground(Color.WHITE);
        filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JButton searchButton = new JButton("Search");
        JButton refreshButton = new JButton("Refresh");
        JButton clearButton = new JButton("Clear");

        addFilterField(filterPanel, 0, "Search", searchField, 1.0);
        addFilterField(filterPanel, 2, "From", fromDateField, 0.0);
        addFilterField(filterPanel, 4, "To", toDateField, 0.0);

        GridBagConstraints buttonGbc = new GridBagConstraints();
        buttonGbc.gridx = 6;
        buttonGbc.gridy = 0;
        buttonGbc.insets = new Insets(0, 10, 0, 0);
        filterPanel.add(searchButton, buttonGbc);

        buttonGbc.gridx = 7;
        filterPanel.add(refreshButton, buttonGbc);

        buttonGbc.gridx = 8;
        filterPanel.add(clearButton, buttonGbc);

        summaryLabel.setForeground(new Color(71, 85, 105));
        summaryLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        buttonGbc.gridx = 9;
        buttonGbc.weightx = 0;
        filterPanel.add(summaryLabel, buttonGbc);

        searchButton.addActionListener(e -> loadReceivingHistory());
        refreshButton.addActionListener(e -> loadReceivingHistory());
        clearButton.addActionListener(e -> {
            searchField.setText("");
            fromDateField.setText("");
            toDateField.setText("");
            loadReceivingHistory();
        });
        searchField.addActionListener(e -> loadReceivingHistory());
        fromDateField.addActionListener(e -> loadReceivingHistory());
        toDateField.addActionListener(e -> loadReceivingHistory());

        headerPanel.add(titlePanel, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private void addFilterField(JPanel panel, int gridX, String label, JTextField field, double weightX) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = gridX;
        labelGbc.gridy = 0;
        labelGbc.insets = new Insets(0, 0, 0, 6);
        labelGbc.anchor = GridBagConstraints.WEST;
        panel.add(fieldLabel, labelGbc);

        field.setPreferredSize(new Dimension(weightX > 0 ? 250 : 105, 30));

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.gridx = gridX + 1;
        fieldGbc.gridy = 0;
        fieldGbc.weightx = weightX;
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.insets = new Insets(0, 0, 0, 8);
        panel.add(field, fieldGbc);
    }

    private void loadReceivingHistory() {
        tableModel.setRowCount(0);
        updateStoreLabel();
        LocalDate fromDate = parseDate(fromDateField.getText().trim(), "From");
        LocalDate toDate = parseDate(toDateField.getText().trim(), "To");
        if (fromDate == null && !fromDateField.getText().trim().isEmpty()) return;
        if (toDate == null && !toDateField.getText().trim().isEmpty()) return;
        int totalUnits = 0;
        try {
            for (LanApiClient.ReceivingHistoryRow row : LanApiClient.loadReceivingHistory(
                    searchField.getText().trim(), fromDate == null ? "" : fromDate.toString(),
                    toDate == null ? "" : toDate.toString())) {
                totalUnits += row.changeQuantity();
                tableModel.addRow(new Object[]{row.receiveId(), row.movementId(),
                        formatTimestamp(row.createdAtEpochMillis()), row.productName(), row.sku(),
                        row.storeName(), row.changeQuantity(),
                        formatReceivedBy(row.receivedBy(), row.note()), row.note()});
            }
            summaryLabel.setText("Records: " + tableModel.getRowCount() + "   Units: " + totalUnits);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load receiving history.\n" + ex.getMessage(),
                    "LAN Service", JOptionPane.ERROR_MESSAGE);
        }
    }

    private LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, label + " date must use YYYY-MM-DD.");
            return null;
        }
    }

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) return "";
        return Instant.ofEpochMilli(epochMillis).atZone(StoreTimeZoneHelper.getStoreZone())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a"));
    }

    private String formatReceivedBy(String receivedBy, String note) {
        if (receivedBy != null && !receivedBy.isBlank()) {
            return receivedBy;
        }
        String userId = extractNoteValue(note, "entered_by_user_id=");
        return userId.isBlank() ? "" : "User #" + userId;
    }

    private String extractNoteValue(String note, String key) {
        if (note == null || key == null || key.isBlank()) {
            return "";
        }
        int start = note.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        int end = note.indexOf(';', start);
        if (end < 0) {
            end = note.length();
        }
        return note.substring(start, end).trim();
    }

    private void updateStoreLabel() {
        String storeName = SessionManager.getCurrentLocationName();
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            storeLabel.setText("Store: All");
        } else if (storeName == null || storeName.isBlank()) {
            storeLabel.setText("Store ID: " + locationId);
        } else {
            storeLabel.setText("Store: " + storeName);
        }
    }
}
