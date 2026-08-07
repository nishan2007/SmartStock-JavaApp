package ui.screens;

import services.LanApiClient;
import ui.components.LoadingStatePanel;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiDebouncer;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Read-only, local-cache-backed inventory lookup for other stores. */
public final class CrossStoreInventory extends JFrame {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private final JTextField searchField = new JTextField(28);
    private final JComboBox<StoreChoice> storeFilter = new JComboBox<>();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"Store", "Item", "Size", "SKU", "Barcode", "Available",
                    "Inventory Updated", "Cache Updated", "Sync Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int column) {
            return column == 5 ? Integer.class : String.class;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private final JLabel summaryLabel = new JLabel("Products: 0");
    private boolean loadingStores;

    public CrossStoreInventory() {
        setTitle("Store Stock");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1280, 700);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));
        content.add(buildHeader(), BorderLayout.NORTH);
        content.add(buildTable(), BorderLayout.CENTER);
        content.add(buildFooter(), BorderLayout.SOUTH);
        add(content);

        searchField.putClientProperty("JTextField.placeholderText", "Name, barcode, SKU, or description");
        searchField.addActionListener(event -> loadResults());
        UiDebouncer.bind(searchField, 300, this::loadResults);
        storeFilter.addActionListener(event -> { if (!loadingStores) loadResults(); });
        WindowHelper.configurePosWindow(this);
        loadResults();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Inventory at Other Stores");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        JLabel note = new JLabel("Read-only — quantities are the latest values synchronized from each store.");
        note.setFont(new Font("SansSerif", Font.PLAIN, 13));
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        filters.add(new JLabel("Search:"));
        filters.add(searchField);
        filters.add(new JLabel("Store:"));
        storeFilter.setPrototypeDisplayValue(new StoreChoice(null, "All Stores", ""));
        filters.add(storeFilter);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(event -> loadResults());
        filters.add(refresh);
        header.add(title);
        header.add(Box.createVerticalStrut(3));
        header.add(note);
        header.add(filters);
        return header;
    }

    private JComponent buildTable() {
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(loadingState, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(summaryLabel, BorderLayout.WEST);
        JButton close = new JButton("Close");
        close.addActionListener(event -> dispose());
        footer.add(close, BorderLayout.EAST);
        return footer;
    }

    private void loadResults() {
        StoreChoice selected = (StoreChoice) storeFilter.getSelectedItem();
        Integer locationId = selected == null ? null : selected.locationId();
        String query = searchField.getText().trim();
        loadingState.loading(tableModel.getRowCount() > 0, Instant.now());
        UiTaskRunner.submit(this, "cross-store-inventory.search",
                () -> LanApiClient.loadCrossStoreInventory(query, locationId),
                this::showResults,
                error -> loadingState.failed(error.getMessage(), tableModel.getRowCount() > 0, this::loadResults));
    }

    private void showResults(LanApiClient.CrossStoreInventoryResult result) {
        updateStores(result.stores());
        tableModel.setRowCount(0);
        List<LanApiClient.CrossStoreInventoryItem> items = result.items() == null ? List.of() : result.items();
        for (LanApiClient.CrossStoreInventoryItem item : items) {
            tableModel.addRow(new Object[]{item.storeName(), item.productName(), item.size(), item.sku(),
                    item.barcode(), item.quantityOnHand(), formatTime(item.sourceUpdatedAtEpochMillis()),
                    formatTime(item.cacheRefreshedAtEpochMillis()), item.cacheStatus()});
        }
        summaryLabel.setText("Products: " + items.size());
        loadingState.ready(Instant.now());
    }

    private void updateStores(List<LanApiClient.CrossStoreStoreOption> stores) {
        Integer selectedId = storeFilter.getSelectedItem() instanceof StoreChoice choice ? choice.locationId() : null;
        loadingStores = true;
        try {
            storeFilter.removeAllItems();
            storeFilter.addItem(new StoreChoice(null, "All Stores", ""));
            if (stores != null) for (LanApiClient.CrossStoreStoreOption store : stores) {
                StoreChoice choice = new StoreChoice(store.locationId(), store.name(), store.status());
                storeFilter.addItem(choice);
                if (selectedId != null && selectedId == store.locationId()) storeFilter.setSelectedItem(choice);
            }
        } finally {
            loadingStores = false;
        }
    }

    private static String formatTime(long epochMillis) {
        return epochMillis <= 0 ? "Not available" : Instant.ofEpochMilli(epochMillis)
                .atZone(StoreTimeZoneHelper.getStoreZone()).format(TIME_FORMAT);
    }

    private record StoreChoice(Integer locationId, String name, String status) {
        @Override public String toString() {
            return status == null || status.isBlank() || "CURRENT".equals(status) ? name : name + " (" + status + ")";
        }
    }
}
