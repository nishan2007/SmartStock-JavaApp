package ui.screens;

import services.LanApiClient;
import services.LanMachineService;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
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
        try {
            LanMachineService.State state=LanApiClient.loadMachineState(searchField.getText().trim());
            for(var row:state.machines())tableModel.addRow(new Object[]{row.id(),row.name(),row.assetTag(),row.type(),row.status(),row.location(),row.nextServiceDate()==null?"":row.nextServiceDate().toString()});
        } catch (Exception ex) {
            showError("load machines", ex);
        }
    }

    private void loadSelectedMachine() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) tableModel.getValueAt(table.convertRowIndexToModel(row), 0);
        try {
            LanMachineService.Detail detail=LanApiClient.loadMachineDetail(id);LanMachineService.Machine machine=detail.machine();selectedMachineId=id;nameField.setText(machine.name());assetTagField.setText(value(machine.assetTag()));serialNumberField.setText(value(machine.serialNumber()));manufacturerField.setText(value(machine.manufacturer()));modelField.setText(value(machine.model()));typeField.setText(value(machine.type()));if(machine.locationId()==null)selectLocationByName(machine.locationName());else selectLocation(machine.locationId());statusBox.setSelectedItem(machine.status());purchaseDateField.setText(dateText(machine.purchaseDate()));warrantyDateField.setText(dateText(machine.warrantyDate()));lastServiceDateField.setText(dateText(machine.lastServiceDate()));nextServiceDateField.setText(dateText(machine.nextServiceDate()));notesArea.setText(value(machine.notes()));populateAssociatedParts(detail.parts());
        } catch (Exception ex) {
            showError("load selected machine", ex);
        }
    }

    private void saveMachine() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            showWarning("Machine name is required.");
            return;
        }
        try {
            LocationOption location = (LocationOption) locationBox.getSelectedItem();
            LanApiClient.saveMachine(new LanMachineService.Machine(selectedMachineId,name,nullable(assetTagField),nullable(serialNumberField),nullable(manufacturerField),nullable(modelField),nullable(typeField),location==null?null:location.id,location==null?null:location.name,String.valueOf(statusBox.getSelectedItem()),parseDate(purchaseDateField),parseDate(warrantyDateField),parseDate(lastServiceDateField),parseDate(nextServiceDateField),nullable(notesArea)),java.util.UUID.randomUUID().toString());
            clearEditor();
            loadMachines();
        } catch (Exception ex) {
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
        try {
            LanApiClient.updateMachineLink("DELETE",selectedMachineId,null,null,null,java.util.UUID.randomUUID().toString());
            clearEditor();
            loadMachines();
        } catch (Exception ex) {
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
        try {for(var part:LanApiClient.loadMachineState("").parts())partBox.addItem(new PartOption(part.id(),part.name(),part.partNumber()));}
        catch (Exception ex) {
            showError("load parts", ex);
        }
    }

    private void loadLocationOptions() {
        locationBox.removeAllItems();
        locationBox.addItem(new LocationOption(null, "Unassigned"));

        try {for(var location:LanApiClient.loadMachineState("").locations())locationBox.addItem(new LocationOption(location.id(),location.name()));}
        catch (Exception ex) {
            showError("load stores", ex);
        }
    }

    private void loadAssociatedParts() {
        associatedPartsModel.setRowCount(0);
        if (selectedMachineId == null) {
            return;
        }

        try {populateAssociatedParts(LanApiClient.loadMachineDetail(selectedMachineId).parts());}
        catch (Exception ex) {
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

        try {
            LanApiClient.updateMachineLink("LINK",selectedMachineId,selectedPart.id,null,nullable(partNotesField),java.util.UUID.randomUUID().toString());
            partNotesField.setText("");
            loadAssociatedParts();
        } catch (Exception ex) {
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

        try {
            LanApiClient.updateMachineLink("UNLINK",null,null,machinePartId,null,java.util.UUID.randomUUID().toString());
            loadAssociatedParts();
        } catch (Exception ex) {
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

    private static LocalDate parseDate(JTextField field) {
        String value = field.getText().trim();
        if (value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
        }
    }
    private void populateAssociatedParts(java.util.List<LanMachineService.PartLink> parts){associatedPartsModel.setRowCount(0);for(var p:parts)associatedPartsModel.addRow(new Object[]{p.linkId(),p.name(),p.partNumber(),p.notes()});}
    private static String dateText(LocalDate date){return date==null?"":date.toString();}

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
