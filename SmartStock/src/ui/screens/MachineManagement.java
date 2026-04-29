package ui.screens;

import data.DB;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class MachineManagement extends JFrame {
    private final JTextField searchField = new JTextField();
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"ID", "Machine", "Asset Tag", "Type", "Status", "Location", "Next Service"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JTextField nameField = new JTextField();
    private final JTextField assetTagField = new JTextField();
    private final JTextField serialNumberField = new JTextField();
    private final JTextField manufacturerField = new JTextField();
    private final JTextField modelField = new JTextField();
    private final JTextField typeField = new JTextField();
    private final JComboBox<LocationOption> locationBox = new JComboBox<>();
    private final JComboBox<String> statusBox = new JComboBox<>(new String[]{"ACTIVE", "NEEDS_SERVICE", "DOWN", "RETIRED"});
    private final JTextField purchaseDateField = new JTextField();
    private final JTextField warrantyDateField = new JTextField();
    private final JTextField lastServiceDateField = new JTextField();
    private final JTextField nextServiceDateField = new JTextField();
    private final JTextArea notesArea = new JTextArea(4, 24);
    private final DefaultTableModel associatedPartsModel = new DefaultTableModel(
            new Object[]{"Link ID", "Part", "Part #", "Notes"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable associatedPartsTable = new JTable(associatedPartsModel);
    private final JComboBox<PartOption> partBox = new JComboBox<>();
    private final JTextField partNotesField = new JTextField();
    private Integer selectedMachineId;

    public MachineManagement() {
        setTitle("Machine List");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "MachineManagement"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JScrollPane editorScrollPane = new JScrollPane(buildEditor());
        editorScrollPane.setPreferredSize(new Dimension(520, 0));
        editorScrollPane.setBorder(BorderFactory.createEmptyBorder());
        editorScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        editorScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(editorScrollPane, BorderLayout.EAST);
        add(root);

        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedMachine();
            }
        });
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        associatedPartsTable.setRowHeight(24);
        associatedPartsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        associatedPartsTable.removeColumn(associatedPartsTable.getColumnModel().getColumn(0));
        associatedPartsTable.getColumnModel().getColumn(0).setPreferredWidth(150);
        associatedPartsTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        associatedPartsTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        associatedPartsTable.setFillsViewportHeight(true);

        loadLocationOptions();
        loadPartOptions();
        loadMachines();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);

        JLabel titleLabel = new JLabel("Machine List");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JButton searchButton = new JButton("Search");
        JButton refreshButton = new JButton("Refresh");
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(searchButton);
        buttons.add(refreshButton);
        searchPanel.add(buttons, BorderLayout.EAST);

        searchField.addActionListener(e -> loadMachines());
        searchButton.addActionListener(e -> loadMachines());
        refreshButton.addActionListener(e -> {
            searchField.setText("");
            loadMachines();
        });

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(searchPanel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildEditor() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(16, 16, 16, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 12, 0);
        JLabel title = new JLabel("Machine Details");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(title, gbc);

        addFormRow(panel, gbc, 1, "Name:", nameField);
        addFormRow(panel, gbc, 2, "Asset Tag:", assetTagField);
        addFormRow(panel, gbc, 3, "Serial #:", serialNumberField);
        addFormRow(panel, gbc, 4, "Manufacturer:", manufacturerField);
        addFormRow(panel, gbc, 5, "Model:", modelField);
        addFormRow(panel, gbc, 6, "Type:", typeField);
        addFormRow(panel, gbc, 7, "Store:", locationBox);
        addFormRow(panel, gbc, 8, "Status:", statusBox);
        addFormRow(panel, gbc, 9, "Purchased:", purchaseDateField);
        addFormRow(panel, gbc, 10, "Warranty Ends:", warrantyDateField);
        addFormRow(panel, gbc, 11, "Last Service:", lastServiceDateField);
        addFormRow(panel, gbc, 12, "Next Service:", nextServiceDateField);
        addFormRow(panel, gbc, 13, "Notes:", new JScrollPane(notesArea));
        addWideRow(panel, gbc, 14, buildAssociatedPartsPanel());

        JButton newButton = new JButton("New");
        JButton deleteButton = new JButton("Delete");
        JButton saveButton = new JButton("Save");
        newButton.addActionListener(e -> clearEditor());
        deleteButton.addActionListener(e -> deleteMachine());
        saveButton.addActionListener(e -> saveMachine());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.add(newButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = 15;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(buttonPanel, gbc);
        return panel;
    }

    private JPanel buildAssociatedPartsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Associated Parts"),
                new EmptyBorder(8, 8, 8, 8)
        ));
        panel.setPreferredSize(new Dimension(430, 360));
        panel.setMinimumSize(new Dimension(430, 320));

        JScrollPane scrollPane = new JScrollPane(associatedPartsTable);
        scrollPane.setPreferredSize(new Dimension(410, 190));
        associatedPartsTable.setPreferredScrollableViewportSize(new Dimension(410, 190));

        JPanel addPanel = new JPanel(new GridBagLayout());
        addPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        addPanel.add(new JLabel("Part:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        addPanel.add(partBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        addPanel.add(new JLabel("Notes:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        addPanel.add(partNotesField, gbc);

        JButton addButton = new JButton("Add Part");
        JButton removeButton = new JButton("Remove");
        addButton.addActionListener(e -> addAssociatedPart());
        removeButton.addActionListener(e -> removeAssociatedPart());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(removeButton);
        buttons.add(addButton);

        JPanel controls = new JPanel(new BorderLayout(0, 8));
        controls.setOpaque(false);
        controls.add(addPanel, BorderLayout.CENTER);
        controls.add(buttons, BorderLayout.SOUTH);
        panel.add(controls, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void loadMachines() {
        tableModel.setRowCount(0);
        String search = searchField.getText().trim();
        String sql = """
                SELECT machine_id, machine_name, COALESCE(asset_tag, '') AS asset_tag,
                       COALESCE(machine_type, '') AS machine_type, status,
                       COALESCE(l.name, mm.location_name, '') AS location_name, next_service_date
                FROM maintenance_machines mm
                LEFT JOIN locations l ON l.location_id = mm.location_id
                """ + (search.isBlank() ? "" : """
                WHERE machine_name ILIKE ?
                   OR COALESCE(asset_tag, '') ILIKE ?
                   OR COALESCE(serial_number, '') ILIKE ?
                   OR COALESCE(machine_type, '') ILIKE ?
                   OR COALESCE(l.name, mm.location_name, '') ILIKE ?
                """) + " ORDER BY machine_name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!search.isBlank()) {
                String pattern = "%" + search + "%";
                for (int i = 1; i <= 5; i++) {
                    ps.setString(i, pattern);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tableModel.addRow(new Object[]{
                            rs.getInt("machine_id"),
                            rs.getString("machine_name"),
                            rs.getString("asset_tag"),
                            rs.getString("machine_type"),
                            rs.getString("status"),
                            rs.getString("location_name"),
                            formatDate(rs.getDate("next_service_date"))
                    });
                }
            }
        } catch (SQLException ex) {
            showError("load machines", ex);
        }
    }

    private void loadSelectedMachine() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) tableModel.getValueAt(table.convertRowIndexToModel(row), 0);
        String sql = """
                SELECT mm.*, COALESCE(l.name, mm.location_name, '') AS resolved_location_name
                FROM maintenance_machines mm
                LEFT JOIN locations l ON l.location_id = mm.location_id
                WHERE mm.machine_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectedMachineId = id;
                    nameField.setText(rs.getString("machine_name"));
                    assetTagField.setText(value(rs.getString("asset_tag")));
                    serialNumberField.setText(value(rs.getString("serial_number")));
                    manufacturerField.setText(value(rs.getString("manufacturer")));
                    modelField.setText(value(rs.getString("model")));
                    typeField.setText(value(rs.getString("machine_type")));
                    int locationId = rs.getInt("location_id");
                    if (rs.wasNull()) {
                        selectLocationByName(rs.getString("resolved_location_name"));
                    } else {
                        selectLocation(locationId);
                    }
                    statusBox.setSelectedItem(rs.getString("status"));
                    purchaseDateField.setText(formatDate(rs.getDate("purchase_date")));
                    warrantyDateField.setText(formatDate(rs.getDate("warranty_expiration_date")));
                    lastServiceDateField.setText(formatDate(rs.getDate("last_service_date")));
                    nextServiceDateField.setText(formatDate(rs.getDate("next_service_date")));
                    notesArea.setText(value(rs.getString("notes")));
                    loadAssociatedParts();
                }
            }
        } catch (SQLException ex) {
            showError("load selected machine", ex);
        }
    }

    private void saveMachine() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            showWarning("Machine name is required.");
            return;
        }
        String insertSql = """
                INSERT INTO maintenance_machines (
                    machine_name, asset_tag, serial_number, manufacturer, model, machine_type,
                    location_id, location_name, status, purchase_date, warranty_expiration_date,
                    last_service_date, next_service_date, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE maintenance_machines
                SET machine_name = ?, asset_tag = ?, serial_number = ?, manufacturer = ?, model = ?,
                    machine_type = ?, location_id = ?, location_name = ?, status = ?, purchase_date = ?,
                    warranty_expiration_date = ?, last_service_date = ?, next_service_date = ?,
                    notes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE machine_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectedMachineId == null ? insertSql : updateSql)) {
            ps.setString(1, name);
            ps.setString(2, nullable(assetTagField));
            ps.setString(3, nullable(serialNumberField));
            ps.setString(4, nullable(manufacturerField));
            ps.setString(5, nullable(modelField));
            ps.setString(6, nullable(typeField));
            LocationOption location = (LocationOption) locationBox.getSelectedItem();
            setLocationId(ps, 7, location);
            ps.setString(8, location == null || location.id == null ? null : location.name);
            ps.setString(9, String.valueOf(statusBox.getSelectedItem()));
            setDate(ps, 10, purchaseDateField);
            setDate(ps, 11, warrantyDateField);
            setDate(ps, 12, lastServiceDateField);
            setDate(ps, 13, nextServiceDateField);
            ps.setString(14, nullable(notesArea));
            if (selectedMachineId != null) {
                ps.setInt(15, selectedMachineId);
            }
            ps.executeUpdate();
            clearEditor();
            loadMachines();
        } catch (SQLException | IllegalArgumentException ex) {
            showError("save machine", ex);
        }
    }

    private void deleteMachine() {
        if (selectedMachineId == null) {
            showWarning("Select a machine to delete.");
            return;
        }
        int result = JOptionPane.showConfirmDialog(
                this,
                "Delete this machine? Existing logs or tickets can prevent deletion.",
                "Delete Machine",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM maintenance_machines WHERE machine_id = ?")) {
            ps.setInt(1, selectedMachineId);
            ps.executeUpdate();
            clearEditor();
            loadMachines();
        } catch (SQLException ex) {
            showError("delete machine", ex);
        }
    }

    private void clearEditor() {
        selectedMachineId = null;
        table.clearSelection();
        nameField.setText("");
        assetTagField.setText("");
        serialNumberField.setText("");
        manufacturerField.setText("");
        modelField.setText("");
        typeField.setText("");
        if (locationBox.getItemCount() > 0) {
            locationBox.setSelectedIndex(0);
        }
        statusBox.setSelectedItem("ACTIVE");
        purchaseDateField.setText("");
        warrantyDateField.setText("");
        lastServiceDateField.setText("");
        nextServiceDateField.setText("");
        notesArea.setText("");
        associatedPartsModel.setRowCount(0);
        partNotesField.setText("");
    }

    private void loadPartOptions() {
        partBox.removeAllItems();
        String sql = """
                SELECT part_id, part_name, COALESCE(part_number, '') AS part_number
                FROM maintenance_parts
                WHERE is_active = TRUE
                ORDER BY part_name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                partBox.addItem(new PartOption(
                        rs.getInt("part_id"),
                        rs.getString("part_name"),
                        rs.getString("part_number")
                ));
            }
        } catch (SQLException ex) {
            showError("load parts", ex);
        }
    }

    private void loadLocationOptions() {
        locationBox.removeAllItems();
        locationBox.addItem(new LocationOption(null, "Unassigned"));

        String sql = "SELECT location_id, name FROM locations ORDER BY name";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                locationBox.addItem(new LocationOption(rs.getInt("location_id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            showError("load stores", ex);
        }
    }

    private void loadAssociatedParts() {
        associatedPartsModel.setRowCount(0);
        if (selectedMachineId == null) {
            return;
        }

        String sql = """
                SELECT mp.machine_part_id,
                       p.part_name,
                       COALESCE(p.part_number, '') AS part_number,
                       COALESCE(mp.notes, '') AS notes
                FROM maintenance_machine_parts mp
                JOIN maintenance_parts p ON p.part_id = mp.part_id
                WHERE mp.machine_id = ?
                ORDER BY p.part_name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selectedMachineId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    associatedPartsModel.addRow(new Object[]{
                            rs.getLong("machine_part_id"),
                            rs.getString("part_name"),
                            rs.getString("part_number"),
                            rs.getString("notes")
                    });
                }
            }
        } catch (SQLException ex) {
            showError("load associated parts", ex);
        }
    }

    private void addAssociatedPart() {
        if (selectedMachineId == null) {
            showWarning("Save or select a machine before adding parts.");
            return;
        }
        PartOption selectedPart = (PartOption) partBox.getSelectedItem();
        if (selectedPart == null) {
            showWarning("Add a part in the Parts List before associating it with a machine.");
            return;
        }

        String sql = """
                INSERT INTO maintenance_machine_parts (machine_id, part_id, notes)
                VALUES (?, ?, ?)
                ON CONFLICT (machine_id, part_id)
                DO UPDATE SET notes = EXCLUDED.notes,
                              updated_at = CURRENT_TIMESTAMP
                """;

        try (Connection conn = DB.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, selectedMachineId);
            ps.setInt(2, selectedPart.id);
            ps.setString(3, nullable(partNotesField));
            ps.executeUpdate();
            partNotesField.setText("");
            loadAssociatedParts();
        } catch (SQLException | IllegalArgumentException ex) {
            showError("associate part", ex);
        }
    }

    private void removeAssociatedPart() {
        int row = associatedPartsTable.getSelectedRow();
        if (row < 0) {
            showWarning("Select an associated part to remove.");
            return;
        }
        int modelRow = associatedPartsTable.convertRowIndexToModel(row);
        Long machinePartId = ((Number) associatedPartsModel.getValueAt(modelRow, 0)).longValue();

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM maintenance_machine_parts WHERE machine_part_id = ?")) {
            ps.setLong(1, machinePartId);
            ps.executeUpdate();
            loadAssociatedParts();
        } catch (SQLException ex) {
            showError("remove associated part", ex);
        }
    }

    private static void addFormRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 10, 8);
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(field, gbc);
    }

    private static void addWideRow(JPanel panel, GridBagConstraints gbc, int row, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(6, 0, 12, 0);
        panel.add(field, gbc);
    }

    private static String nullable(JTextField field) {
        String value = field.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String nullable(JTextArea area) {
        String value = area.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private static void setDate(PreparedStatement ps, int index, JTextField field) throws SQLException {
        String value = field.getText().trim();
        if (value.isBlank()) {
            ps.setNull(index, java.sql.Types.DATE);
            return;
        }
        try {
            ps.setDate(index, Date.valueOf(LocalDate.parse(value)));
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
        }
    }

    private static void setLocationId(PreparedStatement ps, int index, LocationOption location) throws SQLException {
        if (location == null || location.id == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, location.id);
        }
    }

    private void selectLocation(int locationId) {
        for (int i = 0; i < locationBox.getItemCount(); i++) {
            LocationOption option = locationBox.getItemAt(i);
            if (option.id != null && option.id == locationId) {
                locationBox.setSelectedIndex(i);
                return;
            }
        }
        locationBox.setSelectedIndex(0);
    }

    private void selectLocationByName(String name) {
        if (name != null && !name.isBlank()) {
            for (int i = 0; i < locationBox.getItemCount(); i++) {
                LocationOption option = locationBox.getItemAt(i);
                if (option.name.equalsIgnoreCase(name.trim())) {
                    locationBox.setSelectedIndex(i);
                    return;
                }
            }
        }
        locationBox.setSelectedIndex(0);
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static String formatDate(Date date) {
        return date == null ? "" : date.toLocalDate().toString();
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Machine List", JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String action, Exception ex) {
        JOptionPane.showMessageDialog(this, "Could not " + action + ".\n\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    private record PartOption(int id, String name, String partNumber) {
        @Override
        public String toString() {
            if (partNumber == null || partNumber.isBlank()) {
                return name;
            }
            return name + " (" + partNumber + ")";
        }
    }

    private record LocationOption(Integer id, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
