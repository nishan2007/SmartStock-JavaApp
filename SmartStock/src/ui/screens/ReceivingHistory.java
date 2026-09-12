package ui.screens;

import managers.SessionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiDebouncer;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class ReceivingHistory extends JFrame {
    private List<List<LanApiClient.ReceivingHistoryRow>> slips = List.of();
    private final JTextField searchField = new JTextField();
    private final JTextField fromDateField = new JTextField();
    private final JTextField toDateField = new JTextField();
    private final JLabel storeLabel = new JLabel();
    private final JLabel summaryLabel = new JLabel("Records: 0   Units: 0");
    private final DefaultTableModel tableModel;
    private final JTable historyTable;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

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
                new Object[]{"Receive ID", "Date / Time", "Store", "Items", "Units Received", "Received By"},
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
        for(int c=0;c<6;c++) historyTable.getColumnModel().getColumn(c).setPreferredWidth(180);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        historyTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if(e.getClickCount()==2 && SwingUtilities.isLeftMouseButton(e) && historyTable.rowAtPoint(e.getPoint())>=0) showDetails();
            }
        });
        JButton detailsButton = new JButton("View Details");
        detailsButton.addActionListener(e -> showDetails());
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(detailsButton,BorderLayout.WEST);
        footer.add(loadingState,BorderLayout.CENTER);
        root.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
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
        UiDebouncer.bind(searchField, 300, this::loadReceivingHistory);
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
        updateStoreLabel();
        LocalDate fromDate = parseDate(fromDateField.getText().trim(), "From");
        LocalDate toDate = parseDate(toDateField.getText().trim(), "To");
        if (fromDate == null && !fromDateField.getText().trim().isEmpty()) return;
        if (toDate == null && !toDateField.getText().trim().isEmpty()) return;
        String search = searchField.getText().trim();
        String from = fromDate == null ? "" : fromDate.toString();
        String to = toDate == null ? "" : toDate.toString();
        CachedUiLoader.load(this, "receiving-history.search", "receiving-history:" + search + ":" + from + ":" + to,
                ReceivingSnapshot.class, SessionDataCache.SCREEN_TTL, loadingState,
                () -> new ReceivingSnapshot(LanApiClient.loadReceivingHistory(search, from, to)),
                this::applyReceivingHistory);
    }

    private void applyReceivingHistory(ReceivingSnapshot snapshot) {
        tableModel.setRowCount(0);
        var grouped = new java.util.LinkedHashMap<String,List<LanApiClient.ReceivingHistoryRow>>();
        for(var row:snapshot.rows()) grouped.computeIfAbsent(slipId(row),k -> new java.util.ArrayList<>()).add(row);
        slips = new java.util.ArrayList<>(grouped.values());
        long totalUnits = 0;
        for(var lines:slips) {
            var row=lines.get(0);
            long units=lines.stream().mapToLong(LanApiClient.ReceivingHistoryRow::changeQuantity).sum();
            totalUnits+=units;
            tableModel.addRow(new Object[]{slipId(row),formatTimestamp(row.createdAtEpochMillis()),row.storeName(),lines.size(),units,formatReceivedBy(row.receivedBy(),row.note())});
        }
        summaryLabel.setText("Slips: "+slips.size()+"   Units: "+totalUnits);
    }

    private String slipId(LanApiClient.ReceivingHistoryRow row) {
        return row.receiveId()==null || row.receiveId().isBlank() ? "Legacy movement #"+row.movementId() : row.receiveId();
    }

    private void showDetails() {
        int selected=historyTable.getSelectedRow();
        if(selected<0) { JOptionPane.showMessageDialog(this,"Select a receiving slip first."); return; }
        var lines=slips.get(historyTable.convertRowIndexToModel(selected));
        var first=lines.get(0);
        DefaultTableModel details=new DefaultTableModel(new Object[]{"Movement ID","Product","SKU","Qty Received","Note"},0) {
            public boolean isCellEditable(int r,int c) { return false; }
        };
        for(var row:lines) details.addRow(new Object[]{row.movementId(),row.productName(),row.sku(),row.changeQuantity(),row.note()});
        JTable items=new JTable(details);
        items.setRowHeight(28);
        JDialog dialog=new JDialog(this,"Receiving Slip — "+slipId(first),true);
        JPanel content=new JPanel(new BorderLayout(10,10));
        content.setBorder(new EmptyBorder(12,12,12,12));
        content.add(new JLabel(slipId(first)+" | "+formatTimestamp(first.createdAtEpochMillis())+" | "+first.storeName()+" | "+formatReceivedBy(first.receivedBy(),first.note())),BorderLayout.NORTH);
        content.add(new JScrollPane(items),BorderLayout.CENTER);
        JButton close=new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        content.add(close,BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.setSize(Math.min(1000,getWidth()),Math.min(550,getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private record ReceivingSnapshot(List<LanApiClient.ReceivingHistoryRow> rows) { }

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
