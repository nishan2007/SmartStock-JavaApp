package ui.screens;

import data.DB;
import managers.SessionManager;
import models.CashDrawer;
import models.CashDrawerAssignment;
import services.CashDrawerService;
import services.CashDrawerService.DeviceOption;
import services.CashDrawerService.StoreOption;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CashDrawerManagementPanel extends JPanel {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final int[] FLOAT_DENOMINATIONS = {5000, 2000, 1000, 500, 100, 50, 20};

    private final JComboBox<StoreOption> storeBox = new JComboBox<>();
    private final JCheckBox includeInactiveBox = new JCheckBox("Show inactive");
    private final DefaultTableModel drawerTableModel = new DefaultTableModel(
            new Object[]{"ID", "Drawer", "Store", "Devices", "Status"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel assignmentTableModel = new DefaultTableModel(
            new Object[]{"Assignment ID", "Device", "Drawer", "Assigned", "By", "Notes"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable drawerTable = new JTable(drawerTableModel);
    private final JTable assignmentTable = new JTable(assignmentTableModel);
    private final JTextField drawerNameField = new JTextField();
    private final JTextField startingCashField = new JTextField("20000.00");
    private final Map<Integer, JTextField> floatMixFields = new HashMap<>();
    private final JLabel floatMixTotalLabel = new JLabel("$0.00");
    private final JTextArea descriptionArea = new JTextArea(4, 24);
    private final JCheckBox activeBox = new JCheckBox("Active");
    private final JComboBox<DeviceOption> deviceBox = new JComboBox<>();
    private final JTextArea assignmentNotesArea = new JTextArea(3, 24);
    private final JLabel summaryLabel = new JLabel("Loading cash drawers...");

    private final List<CashDrawer> drawers = new ArrayList<>();
    private final List<CashDrawerAssignment> assignments = new ArrayList<>();
    private Long selectedDrawerId;

    public CashDrawerManagementPanel() {
        setLayout(new BorderLayout(16, 16));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setOpaque(false);
        add(buildHeaderPanel(), BorderLayout.NORTH);
        add(buildMainPanel(), BorderLayout.CENTER);
        add(buildFooterPanel(), BorderLayout.SOUTH);

        drawerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        drawerTable.setRowHeight(28);
        drawerTable.getTableHeader().setReorderingAllowed(false);
        drawerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectDrawerFromTable();
            }
        });
        assignmentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assignmentTable.setRowHeight(28);
        assignmentTable.getTableHeader().setReorderingAllowed(false);

        activeBox.setSelected(true);
        includeInactiveBox.setOpaque(false);
        startingCashField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                refreshFloatMixTotal();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                refreshFloatMixTotal();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                refreshFloatMixTotal();
            }
        });
        setFloatMixFields(CashDrawerService.DEFAULT_FLOAT_MIX);
        loadInitialData();
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Cash Drawer Management");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        titleLabel.setForeground(new Color(31, 41, 55));

        JLabel subtitleLabel = new JLabel("Create store drawers and assign one or more approved devices to each drawer.");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(75, 85, 99));

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(titleLabel);
        titleStack.add(Box.createVerticalStrut(5));
        titleStack.add(subtitleLabel);

        JButton refreshButton = new JButton("Refresh");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Store:"));
        controls.add(storeBox);
        controls.add(includeInactiveBox);
        controls.add(refreshButton);

        storeBox.addActionListener(e -> loadDrawersAndAssignments());
        includeInactiveBox.addActionListener(e -> loadDrawersAndAssignments());
        refreshButton.addActionListener(e -> loadInitialData());

        panel.add(titleStack, BorderLayout.WEST);
        panel.add(controls, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setOpaque(false);

        JScrollPane drawerScroll = new JScrollPane(drawerTable);
        drawerScroll.setBorder(BorderFactory.createTitledBorder("Cash Drawers"));

        JScrollPane assignmentScroll = new JScrollPane(assignmentTable);
        assignmentScroll.setBorder(BorderFactory.createTitledBorder("Active Device Assignments"));

        JSplitPane tablesSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, drawerScroll, assignmentScroll);
        tablesSplit.setResizeWeight(0.55);
        tablesSplit.setBorder(BorderFactory.createEmptyBorder());

        panel.add(tablesSplit, BorderLayout.CENTER);
        panel.add(buildEditorPanel(), BorderLayout.EAST);
        return panel;
    }

    private JPanel buildEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(390, 0));

        JPanel drawerEditor = new JPanel(new GridBagLayout());
        drawerEditor.setBackground(Color.WHITE);
        drawerEditor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(14, 14, 14, 14)
        ));
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        JPanel floatMixPanel = buildFloatMixPanel();

        JButton newButton = new JButton("New");
        JButton saveButton = new JButton("Save Drawer");
        JButton clearButton = new JButton("Clear");
        activeBox.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(0, 0, 12, 0);
        JLabel drawerTitle = new JLabel("Drawer Details");
        drawerTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        drawerEditor.add(drawerTitle, gbc);

        addFormRow(drawerEditor, gbc, 1, "Name:", drawerNameField);
        addFormRow(drawerEditor, gbc, 2, "Starting Cash:", startingCashField);
        addFormRow(drawerEditor, gbc, 3, "Float Mix:", floatMixPanel);
        addFormRow(drawerEditor, gbc, 4, "Description:", new JScrollPane(descriptionArea));
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(0, 0, 12, 0);
        drawerEditor.add(activeBox, gbc);

        JPanel drawerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        drawerButtons.setOpaque(false);
        drawerButtons.add(newButton);
        drawerButtons.add(clearButton);
        drawerButtons.add(saveButton);
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        drawerEditor.add(drawerButtons, gbc);

        JPanel assignmentEditor = new JPanel(new GridBagLayout());
        assignmentEditor.setBackground(Color.WHITE);
        assignmentEditor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(14, 14, 14, 14)
        ));
        assignmentNotesArea.setLineWrap(true);
        assignmentNotesArea.setWrapStyleWord(true);

        JButton assignButton = new JButton("Assign Device");
        JButton unassignButton = new JButton("Unassign Selected");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(0, 0, 12, 0);
        JLabel assignmentTitle = new JLabel("Device Assignment");
        assignmentTitle.setFont(new Font("SansSerif", Font.BOLD, 18));
        assignmentEditor.add(assignmentTitle, gbc);
        addFormRow(assignmentEditor, gbc, 1, "Device:", deviceBox);
        addFormRow(assignmentEditor, gbc, 2, "Notes:", new JScrollPane(assignmentNotesArea));

        JPanel assignmentButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        assignmentButtons.setOpaque(false);
        assignmentButtons.add(unassignButton);
        assignmentButtons.add(assignButton);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        assignmentEditor.add(assignmentButtons, gbc);

        newButton.addActionListener(e -> clearDrawerForm());
        clearButton.addActionListener(e -> clearDrawerForm());
        saveButton.addActionListener(e -> saveDrawer());
        assignButton.addActionListener(e -> assignDevice());
        unassignButton.addActionListener(e -> unassignSelectedAssignment());

        panel.add(drawerEditor, BorderLayout.NORTH);
        panel.add(assignmentEditor, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFloatMixPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        for (int i = 0; i < FLOAT_DENOMINATIONS.length; i++) {
            int denomination = FLOAT_DENOMINATIONS[i];
            gbc.gridx = (i % 2) * 2;
            gbc.gridy = i / 2;
            gbc.weightx = 0;
            panel.add(new JLabel("$" + String.format("%,d", denomination)), gbc);

            JTextField field = new JTextField(4);
            field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    refreshFloatMixTotal();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    refreshFloatMixTotal();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    refreshFloatMixTotal();
                }
            });
            floatMixFields.put(denomination, field);
            gbc.gridx = (i % 2) * 2 + 1;
            gbc.weightx = 1;
            panel.add(field, gbc);
        }

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 4;
        gbc.weightx = 1;
        floatMixTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        panel.add(floatMixTotalLabel, gbc);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(summaryLabel, BorderLayout.WEST);
        return panel;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent component) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 10, 8);
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(component, gbc);
    }

    private void loadInitialData() {
        try (Connection conn = DB.getConnection()) {
            loadStores(conn);
            loadDevices(conn);
            loadDrawersAndAssignments(conn);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load cash drawer data: " + ex.getMessage(),
                    "Cash Drawer Management", JOptionPane.ERROR_MESSAGE);
            summaryLabel.setText("Unable to load cash drawers.");
        }
    }

    private void loadStores(Connection conn) throws Exception {
        StoreOption preserve = (StoreOption) storeBox.getSelectedItem();
        storeBox.removeAllItems();
        for (StoreOption store : CashDrawerService.listStores(conn)) {
            storeBox.addItem(store);
            if ((preserve != null && preserve.id().equals(store.id()))
                    || (preserve == null && SessionManager.getCurrentLocationId() != null
                    && SessionManager.getCurrentLocationId().equals(store.id()))) {
                storeBox.setSelectedItem(store);
            }
        }
    }

    private void loadDevices(Connection conn) throws Exception {
        deviceBox.removeAllItems();
        for (DeviceOption device : CashDrawerService.listApprovedDevices(conn)) {
            deviceBox.addItem(device);
        }
    }

    private void loadDrawersAndAssignments() {
        try (Connection conn = DB.getConnection()) {
            loadDrawersAndAssignments(conn);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to refresh cash drawers: " + ex.getMessage(),
                    "Cash Drawer Management", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadDrawersAndAssignments(Connection conn) throws Exception {
        StoreOption store = (StoreOption) storeBox.getSelectedItem();
        Integer locationId = store == null ? null : store.id();
        Long preserveDrawerId = selectedDrawerId;

        drawers.clear();
        drawers.addAll(CashDrawerService.listDrawers(conn, locationId, includeInactiveBox.isSelected()));
        drawerTableModel.setRowCount(0);
        for (CashDrawer drawer : drawers) {
            drawerTableModel.addRow(new Object[]{
                    drawer.getCashDrawerId(),
                    drawer.getDrawerName(),
                    drawer.getLocationName(),
                    drawer.getActiveDeviceCount(),
                    drawer.isActive() ? "Active" : "Inactive"
            });
        }

        assignments.clear();
        assignments.addAll(CashDrawerService.listAssignments(conn, locationId, null));
        assignmentTableModel.setRowCount(0);
        for (CashDrawerAssignment assignment : assignments) {
            assignmentTableModel.addRow(new Object[]{
                    assignment.getAssignmentId(),
                    assignment.getDeviceDisplayName(),
                    assignment.getDrawerName(),
                    assignment.getAssignedAt() == null ? "" : DATE_FORMAT.format(assignment.getAssignedAt().toLocalDateTime()),
                    assignment.getAssignedByName(),
                    assignment.getNotes()
            });
        }

        restoreDrawerSelection(preserveDrawerId);
        summaryLabel.setText(drawers.size() + " drawer(s), " + assignments.size() + " active assignment(s)");
    }

    private void restoreDrawerSelection(Long drawerId) {
        if (drawerId == null) {
            if (drawerTableModel.getRowCount() > 0) {
                drawerTable.setRowSelectionInterval(0, 0);
            } else {
                clearDrawerForm();
            }
            return;
        }
        for (int i = 0; i < drawerTableModel.getRowCount(); i++) {
            if (drawerId.equals(((Number) drawerTableModel.getValueAt(i, 0)).longValue())) {
                drawerTable.setRowSelectionInterval(i, i);
                return;
            }
        }
        clearDrawerForm();
    }

    private void selectDrawerFromTable() {
        int row = drawerTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        int modelRow = drawerTable.convertRowIndexToModel(row);
                selectedDrawerId = ((Number) drawerTableModel.getValueAt(modelRow, 0)).longValue();
        for (CashDrawer drawer : drawers) {
            if (drawer.getCashDrawerId() == selectedDrawerId) {
                drawerNameField.setText(drawer.getDrawerName());
                startingCashField.setText(drawer.getStartingCashAmount().toPlainString());
                setFloatMixFields(drawer.getFloatMix());
                descriptionArea.setText(drawer.getDescription());
                activeBox.setSelected(drawer.isActive());
                break;
            }
        }
    }

    private void clearDrawerForm() {
        selectedDrawerId = null;
        drawerTable.clearSelection();
        drawerNameField.setText("");
        startingCashField.setText("20000.00");
        setFloatMixFields(CashDrawerService.DEFAULT_FLOAT_MIX);
        descriptionArea.setText("");
        activeBox.setSelected(true);
    }

    private void saveDrawer() {
        StoreOption store = (StoreOption) storeBox.getSelectedItem();
        if (store == null) {
            JOptionPane.showMessageDialog(this, "Select a store before saving a drawer.");
            return;
        }
        try (Connection conn = DB.getConnection()) {
            selectedDrawerId = CashDrawerService.saveDrawer(
                    conn,
                    selectedDrawerId,
                    store.id(),
                    drawerNameField.getText(),
                    descriptionArea.getText(),
                    parseMoney(startingCashField.getText()),
                    parseFloatMix(),
                    activeBox.isSelected(),
                    SessionManager.getCurrentUserId()
            );
            loadDrawersAndAssignments(conn);
            JOptionPane.showMessageDialog(this, "Cash drawer saved.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save cash drawer: " + ex.getMessage(),
                    "Cash Drawer Management", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void assignDevice() {
        if (selectedDrawerId == null) {
            JOptionPane.showMessageDialog(this, "Select or save a drawer before assigning a device.");
            return;
        }
        StoreOption store = (StoreOption) storeBox.getSelectedItem();
        DeviceOption device = (DeviceOption) deviceBox.getSelectedItem();
        if (store == null || device == null) {
            JOptionPane.showMessageDialog(this, "Select a store and device.");
            return;
        }
        try (Connection conn = DB.getConnection()) {
            CashDrawerService.assignDevice(
                    conn,
                    selectedDrawerId,
                    store.id(),
                    device.id(),
                    SessionManager.getCurrentUserId(),
                    assignmentNotesArea.getText()
            );
            assignmentNotesArea.setText("");
            loadDrawersAndAssignments(conn);
            JOptionPane.showMessageDialog(this, "Device assigned to cash drawer.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to assign device: " + ex.getMessage(),
                    "Cash Drawer Management", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void unassignSelectedAssignment() {
        int row = assignmentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an assignment to remove.");
            return;
        }
        int modelRow = assignmentTable.convertRowIndexToModel(row);
        long assignmentId = ((Number) assignmentTableModel.getValueAt(modelRow, 0)).longValue();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Remove this device's active cash drawer assignment?",
                "Unassign Device",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        try (Connection conn = DB.getConnection()) {
            CashDrawerService.unassignAssignment(conn, assignmentId, SessionManager.getCurrentUserId());
            loadDrawersAndAssignments(conn);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to unassign device: " + ex.getMessage(),
                    "Cash Drawer Management", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BigDecimal parseMoney(String value) {
        String clean = value == null ? "" : value.replace("$", "").replace(",", "").trim();
        if (clean.isBlank()) {
            return new BigDecimal("20000.00");
        }
        BigDecimal amount = new BigDecimal(clean);
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Starting cash cannot be negative.");
        }
        return amount;
    }

    private Map<Integer, Integer> parseFloatMix() {
        Map<Integer, Integer> mix = new HashMap<>();
        for (int denomination : FLOAT_DENOMINATIONS) {
            JTextField field = floatMixFields.get(denomination);
            String clean = field == null ? "" : field.getText().trim();
            if (clean.isBlank()) {
                clean = "0";
            }
            int quantity = Integer.parseInt(clean);
            if (quantity < 0) {
                throw new IllegalArgumentException("Float mix quantities cannot be negative.");
            }
            if (quantity > 0) {
                mix.put(denomination, quantity);
            }
        }
        return mix;
    }

    private void setFloatMixFields(Map<Integer, Integer> floatMix) {
        Map<Integer, Integer> source = floatMix == null || floatMix.isEmpty()
                ? CashDrawerService.DEFAULT_FLOAT_MIX
                : floatMix;
        for (int denomination : FLOAT_DENOMINATIONS) {
            JTextField field = floatMixFields.get(denomination);
            if (field != null) {
                field.setText(String.valueOf(source.getOrDefault(denomination, 0)));
            }
        }
        refreshFloatMixTotal();
    }

    private void refreshFloatMixTotal() {
        try {
            BigDecimal mixTotal = CashDrawerService.floatMixTotal(parseFloatMix());
            BigDecimal startingCash = parseMoney(startingCashField.getText());
            floatMixTotalLabel.setText("Mix total: $" + String.format("%,.2f", mixTotal)
                    + " / Starting cash: $" + String.format("%,.2f", startingCash));
            floatMixTotalLabel.setForeground(mixTotal.compareTo(startingCash) == 0
                    ? new Color(21, 128, 61)
                    : new Color(185, 28, 28));
        } catch (Exception ex) {
            floatMixTotalLabel.setText("Mix total: invalid quantity");
            floatMixTotalLabel.setForeground(new Color(185, 28, 28));
        }
    }
}
