package ui.screens;

import data.DB;
import managers.NavigationManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class MaintenanceManagement extends JFrame {
    private final JTextField machineSearchField = new JTextField();
    private final DefaultTableModel machineTableModel = readOnlyModel("ID", "Machine", "Asset Tag", "Type", "Status", "Location", "Next Service");
    private final JTable machineTable = new JTable(machineTableModel);
    private final JTextField machineNameField = new JTextField();
    private final JTextField assetTagField = new JTextField();
    private final JTextField serialNumberField = new JTextField();
    private final JTextField manufacturerField = new JTextField();
    private final JTextField modelField = new JTextField();
    private final JTextField machineTypeField = new JTextField();
    private final JTextField machineLocationField = new JTextField();
    private final JTextField purchaseDateField = new JTextField();
    private final JTextField warrantyDateField = new JTextField();
    private final JTextField lastServiceDateField = new JTextField();
    private final JTextField nextServiceDateField = new JTextField();
    private final JComboBox<String> machineStatusBox = new JComboBox<>(new String[]{"ACTIVE", "NEEDS_SERVICE", "DOWN", "RETIRED"});
    private final JTextArea machineNotesArea = textArea(4, 24);
    private Integer selectedMachineId;

    private final JTextField partSearchField = new JTextField();
    private final DefaultTableModel partTableModel = readOnlyModel("ID", "Part", "Part #", "On Hand", "Reorder", "Vendor", "Active");
    private final JTable partTable = new JTable(partTableModel);
    private final JTextField partNameField = new JTextField();
    private final JTextField partNumberField = new JTextField();
    private final JTextField categoryField = new JTextField();
    private final JTextField quantityField = new JTextField("0");
    private final JTextField reorderPointField = new JTextField("0");
    private final JTextField reorderQuantityField = new JTextField("0");
    private final JTextField unitCostField = new JTextField("0.00");
    private final JTextField vendorField = new JTextField();
    private final JTextField binLocationField = new JTextField();
    private final JCheckBox partActiveBox = new JCheckBox("Active", true);
    private final JTextArea partNotesArea = textArea(4, 24);
    private Integer selectedPartId;

    private final DefaultTableModel logTableModel = readOnlyModel("ID", "Date", "Machine", "Type", "Technician", "Hours", "Cost", "Summary");
    private final JTable logTable = new JTable(logTableModel);
    private final JComboBox<ItemOption> logMachineBox = new JComboBox<>();
    private final JTextField logDateField = new JTextField(LocalDate.now().toString());
    private final JComboBox<String> logTypeBox = new JComboBox<>(new String[]{"PREVENTIVE", "REPAIR", "INSPECTION", "CLEANING", "CALIBRATION", "OTHER"});
    private final JTextField technicianField = new JTextField();
    private final JTextField laborHoursField = new JTextField("0.00");
    private final JTextField logCostField = new JTextField("0.00");
    private final JTextArea logSummaryArea = textArea(3, 24);
    private final JTextArea logDetailsArea = textArea(4, 24);
    private final JTextArea partsUsedArea = textArea(3, 24);
    private Integer selectedLogId;

    private final JTextField ticketSearchField = new JTextField();
    private final DefaultTableModel ticketTableModel = readOnlyModel("ID", "Opened", "Machine", "Priority", "Status", "Problem", "Assigned", "Due");
    private final JTable ticketTable = new JTable(ticketTableModel);
    private final JComboBox<ItemOption> ticketMachineBox = new JComboBox<>();
    private final JComboBox<String> priorityBox = new JComboBox<>(new String[]{"LOW", "NORMAL", "HIGH", "URGENT"});
    private final JComboBox<String> ticketStatusBox = new JComboBox<>(new String[]{"OPEN", "IN_PROGRESS", "WAITING_PARTS", "RESOLVED", "CLOSED", "CANCELED"});
    private final JTextField assignedToField = new JTextField();
    private final JTextField dueDateField = new JTextField();
    private final JTextArea problemArea = textArea(4, 24);
    private final JTextArea resolutionArea = textArea(4, 24);
    private final JTextArea ticketNotesArea = textArea(3, 24);
    private Integer selectedTicketId;

    public MaintenanceManagement() {
        setTitle("Maintenance Management");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "MaintenanceManagement"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Maintenance Management");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));

        JLabel subtitleLabel = new JLabel("Track machines, spare parts, service history, and problem tickets.");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
        subtitleLabel.setForeground(new Color(75, 85, 99));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(titleLabel);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitleLabel);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Machines", buildMachinesTab());
        tabs.addTab("Parts", buildPartsTab());
        tabs.addTab("Maintenance Logs", buildLogsTab());
        tabs.addTab("Tickets", buildTicketsTab());

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(closeButton);

        root.add(header, BorderLayout.NORTH);
        root.add(tabs, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        add(root);

        configureTable(machineTable);
        configureTable(partTable);
        configureTable(logTable);
        configureTable(ticketTable);
        logTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedLog();
            }
        });
        ticketTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedTicket();
            }
        });

        refreshAll();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildMachinesTab() {
        JPanel panel = tabPanel();
        panel.add(buildSearchPanel(machineSearchField, "Search machines:", this::loadMachines, () -> {
            machineSearchField.setText("");
            loadMachines();
        }), BorderLayout.NORTH);
        panel.add(new JScrollPane(machineTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPartsTab() {
        JPanel panel = tabPanel();
        panel.add(buildSearchPanel(partSearchField, "Search parts:", this::loadParts, () -> {
            partSearchField.setText("");
            loadParts();
        }), BorderLayout.NORTH);
        panel.add(new JScrollPane(partTable), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildLogsTab() {
        JPanel panel = tabPanel();
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadLogs());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        top.setOpaque(false);
        top.add(refreshButton);
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(logTable), BorderLayout.CENTER);
        panel.add(buildLogEditor(), BorderLayout.EAST);
        return panel;
    }

    private JPanel buildTicketsTab() {
        JPanel panel = tabPanel();
        panel.add(buildSearchPanel(ticketSearchField, "Search tickets:", this::loadTickets, () -> {
            ticketSearchField.setText("");
            loadTickets();
        }), BorderLayout.NORTH);
        panel.add(new JScrollPane(ticketTable), BorderLayout.CENTER);
        panel.add(buildTicketEditor(), BorderLayout.EAST);
        return panel;
    }

    private JPanel buildMachineEditor() {
        JPanel panel = editorPanel(390);
        GridBagConstraints gbc = editorConstraints();
        addEditorTitle(panel, gbc, "Machine Details");
        addFormRow(panel, gbc, 1, "Name:", machineNameField);
        addFormRow(panel, gbc, 2, "Asset Tag:", assetTagField);
        addFormRow(panel, gbc, 3, "Serial #:", serialNumberField);
        addFormRow(panel, gbc, 4, "Manufacturer:", manufacturerField);
        addFormRow(panel, gbc, 5, "Model:", modelField);
        addFormRow(panel, gbc, 6, "Type:", machineTypeField);
        addFormRow(panel, gbc, 7, "Location:", machineLocationField);
        addFormRow(panel, gbc, 8, "Status:", machineStatusBox);
        addFormRow(panel, gbc, 9, "Purchased:", purchaseDateField);
        addFormRow(panel, gbc, 10, "Warranty Ends:", warrantyDateField);
        addFormRow(panel, gbc, 11, "Last Service:", lastServiceDateField);
        addFormRow(panel, gbc, 12, "Next Service:", nextServiceDateField);
        addFormRow(panel, gbc, 13, "Notes:", new JScrollPane(machineNotesArea));
        addButtons(panel, gbc, 14, this::clearMachineEditor, this::saveMachine);
        return panel;
    }

    private JPanel buildPartEditor() {
        JPanel panel = editorPanel(390);
        GridBagConstraints gbc = editorConstraints();
        addEditorTitle(panel, gbc, "Part Details");
        addFormRow(panel, gbc, 1, "Name:", partNameField);
        addFormRow(panel, gbc, 2, "Part #:", partNumberField);
        addFormRow(panel, gbc, 3, "Category:", categoryField);
        addFormRow(panel, gbc, 4, "On Hand:", quantityField);
        addFormRow(panel, gbc, 5, "Reorder Point:", reorderPointField);
        addFormRow(panel, gbc, 6, "Reorder Qty:", reorderQuantityField);
        addFormRow(panel, gbc, 7, "Unit Cost:", unitCostField);
        addFormRow(panel, gbc, 8, "Vendor:", vendorField);
        addFormRow(panel, gbc, 9, "Bin:", binLocationField);
        addFormRow(panel, gbc, 10, "", partActiveBox);
        addFormRow(panel, gbc, 11, "Notes:", new JScrollPane(partNotesArea));
        addButtons(panel, gbc, 12, this::clearPartEditor, this::savePart);
        return panel;
    }

    private JPanel buildLogEditor() {
        JPanel panel = editorPanel(410);
        GridBagConstraints gbc = editorConstraints();
        addEditorTitle(panel, gbc, "Maintenance Log");
        addFormRow(panel, gbc, 1, "Machine:", logMachineBox);
        addFormRow(panel, gbc, 2, "Date:", logDateField);
        addFormRow(panel, gbc, 3, "Type:", logTypeBox);
        addFormRow(panel, gbc, 4, "Technician:", technicianField);
        addFormRow(panel, gbc, 5, "Labor Hours:", laborHoursField);
        addFormRow(panel, gbc, 6, "Cost:", logCostField);
        addFormRow(panel, gbc, 7, "Summary:", new JScrollPane(logSummaryArea));
        addFormRow(panel, gbc, 8, "Details:", new JScrollPane(logDetailsArea));
        addFormRow(panel, gbc, 9, "Parts Used:", new JScrollPane(partsUsedArea));
        addButtons(panel, gbc, 10, this::clearLogEditor, this::saveLog);
        return panel;
    }

    private JPanel buildTicketEditor() {
        JPanel panel = editorPanel(430);
        GridBagConstraints gbc = editorConstraints();
        addEditorTitle(panel, gbc, "Problem Ticket");
        addFormRow(panel, gbc, 1, "Machine:", ticketMachineBox);
        addFormRow(panel, gbc, 2, "Priority:", priorityBox);
        addFormRow(panel, gbc, 3, "Status:", ticketStatusBox);
        addFormRow(panel, gbc, 4, "Assigned To:", assignedToField);
        addFormRow(panel, gbc, 5, "Due Date:", dueDateField);
        addFormRow(panel, gbc, 6, "Problem:", new JScrollPane(problemArea));
        addFormRow(panel, gbc, 7, "Resolution:", new JScrollPane(resolutionArea));
        addFormRow(panel, gbc, 8, "Notes:", new JScrollPane(ticketNotesArea));
        addButtons(panel, gbc, 9, this::clearTicketEditor, this::saveTicket);
        return panel;
    }

    private void refreshAll() {
        loadMachineOptions();
        loadMachines();
        loadParts();
        loadLogs();
        loadTickets();
    }

    private void loadMachines() {
        machineTableModel.setRowCount(0);
        String search = machineSearchField.getText().trim();
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
                    machineTableModel.addRow(new Object[]{
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
            showDatabaseError("load machines", ex);
        }
    }

    private void loadParts() {
        partTableModel.setRowCount(0);
        String search = partSearchField.getText().trim();
        String sql = """
                SELECT part_id, part_name, COALESCE(part_number, '') AS part_number,
                       quantity_on_hand, reorder_point, COALESCE(vendor_name, '') AS vendor_name, is_active
                FROM maintenance_parts
                """ + (search.isBlank() ? "" : """
                WHERE part_name ILIKE ?
                   OR COALESCE(part_number, '') ILIKE ?
                   OR COALESCE(category, '') ILIKE ?
                   OR COALESCE(vendor_name, '') ILIKE ?
                   OR COALESCE(bin_location, '') ILIKE ?
                """) + " ORDER BY part_name";

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
                    partTableModel.addRow(new Object[]{
                            rs.getInt("part_id"),
                            rs.getString("part_name"),
                            rs.getString("part_number"),
                            rs.getBigDecimal("quantity_on_hand"),
                            rs.getBigDecimal("reorder_point"),
                            rs.getString("vendor_name"),
                            rs.getBoolean("is_active") ? "Yes" : "No"
                    });
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("load parts", ex);
        }
    }

    private void loadLogs() {
        logTableModel.setRowCount(0);
        String sql = """
                SELECT l.log_id, l.service_date, m.machine_name, l.service_type,
                       COALESCE(l.technician_name, '') AS technician_name,
                       l.labor_hours, l.total_cost, COALESCE(l.summary, '') AS summary
                FROM maintenance_logs l
                JOIN maintenance_machines m ON m.machine_id = l.machine_id
                ORDER BY l.service_date DESC, l.log_id DESC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                logTableModel.addRow(new Object[]{
                        rs.getInt("log_id"),
                        formatDate(rs.getDate("service_date")),
                        rs.getString("machine_name"),
                        rs.getString("service_type"),
                        rs.getString("technician_name"),
                        rs.getBigDecimal("labor_hours"),
                        rs.getBigDecimal("total_cost"),
                        rs.getString("summary")
                });
            }
        } catch (SQLException ex) {
            showDatabaseError("load maintenance logs", ex);
        }
    }

    private void loadTickets() {
        ticketTableModel.setRowCount(0);
        String search = ticketSearchField.getText().trim();
        String sql = """
                SELECT t.ticket_id, t.opened_at, m.machine_name, t.priority, t.status,
                       COALESCE(t.problem_summary, '') AS problem_summary,
                       COALESCE(t.assigned_to_name, '') AS assigned_to_name, t.due_date
                FROM maintenance_tickets t
                LEFT JOIN maintenance_machines m ON m.machine_id = t.machine_id
                """ + (search.isBlank() ? "" : """
                WHERE COALESCE(m.machine_name, '') ILIKE ?
                   OR COALESCE(t.problem_summary, '') ILIKE ?
                   OR COALESCE(t.assigned_to_name, '') ILIKE ?
                   OR COALESCE(t.status, '') ILIKE ?
                   OR COALESCE(t.priority, '') ILIKE ?
                """) + " ORDER BY t.opened_at DESC, t.ticket_id DESC";

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
                    ticketTableModel.addRow(new Object[]{
                            rs.getInt("ticket_id"),
                            formatTimestamp(rs.getTimestamp("opened_at")),
                            rs.getString("machine_name"),
                            rs.getString("priority"),
                            rs.getString("status"),
                            rs.getString("problem_summary"),
                            rs.getString("assigned_to_name"),
                            formatDate(rs.getDate("due_date"))
                    });
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("load tickets", ex);
        }
    }

    private void loadMachineOptions() {
        logMachineBox.removeAllItems();
        ticketMachineBox.removeAllItems();
        String sql = "SELECT machine_id, machine_name FROM maintenance_machines ORDER BY machine_name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ItemOption option = new ItemOption(rs.getInt("machine_id"), rs.getString("machine_name"));
                logMachineBox.addItem(option);
                ticketMachineBox.addItem(option);
            }
        } catch (SQLException ex) {
            showDatabaseError("load machine choices", ex);
        }
    }

    private void saveMachine() {
        String name = machineNameField.getText().trim();
        if (name.isBlank()) {
            showValidation("Machine name is required.");
            return;
        }

        String insertSql = """
                INSERT INTO maintenance_machines (
                    machine_name, asset_tag, serial_number, manufacturer, model, machine_type,
                    location_name, status, purchase_date, warranty_expiration_date,
                    last_service_date, next_service_date, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE maintenance_machines
                SET machine_name = ?, asset_tag = ?, serial_number = ?, manufacturer = ?, model = ?,
                    machine_type = ?, location_name = ?, status = ?, purchase_date = ?,
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
            ps.setString(6, nullable(machineTypeField));
            ps.setString(7, nullable(machineLocationField));
            ps.setString(8, selected(machineStatusBox));
            setDate(ps, 9, purchaseDateField);
            setDate(ps, 10, warrantyDateField);
            setDate(ps, 11, lastServiceDateField);
            setDate(ps, 12, nextServiceDateField);
            ps.setString(13, nullable(machineNotesArea));
            if (selectedMachineId != null) {
                ps.setInt(14, selectedMachineId);
            }
            ps.executeUpdate();
            clearMachineEditor();
            refreshAll();
        } catch (SQLException | IllegalArgumentException ex) {
            showDatabaseError("save machine", ex);
        }
    }

    private void savePart() {
        String name = partNameField.getText().trim();
        if (name.isBlank()) {
            showValidation("Part name is required.");
            return;
        }

        String insertSql = """
                INSERT INTO maintenance_parts (
                    part_name, part_number, category, quantity_on_hand, reorder_point,
                    reorder_quantity, unit_cost, vendor_name, bin_location, is_active, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE maintenance_parts
                SET part_name = ?, part_number = ?, category = ?, quantity_on_hand = ?,
                    reorder_point = ?, reorder_quantity = ?, unit_cost = ?, vendor_name = ?,
                    bin_location = ?, is_active = ?, notes = ?, updated_at = CURRENT_TIMESTAMP
                WHERE part_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectedPartId == null ? insertSql : updateSql)) {
            ps.setString(1, name);
            ps.setString(2, nullable(partNumberField));
            ps.setString(3, nullable(categoryField));
            ps.setBigDecimal(4, decimal(quantityField, "On hand"));
            ps.setBigDecimal(5, decimal(reorderPointField, "Reorder point"));
            ps.setBigDecimal(6, decimal(reorderQuantityField, "Reorder quantity"));
            ps.setBigDecimal(7, decimal(unitCostField, "Unit cost"));
            ps.setString(8, nullable(vendorField));
            ps.setString(9, nullable(binLocationField));
            ps.setBoolean(10, partActiveBox.isSelected());
            ps.setString(11, nullable(partNotesArea));
            if (selectedPartId != null) {
                ps.setInt(12, selectedPartId);
            }
            ps.executeUpdate();
            clearPartEditor();
            loadParts();
        } catch (SQLException | IllegalArgumentException ex) {
            showDatabaseError("save part", ex);
        }
    }

    private void saveLog() {
        ItemOption machine = (ItemOption) logMachineBox.getSelectedItem();
        if (machine == null) {
            showValidation("Add or select a machine before saving a maintenance log.");
            return;
        }

        String insertSql = """
                INSERT INTO maintenance_logs (
                    machine_id, service_date, service_type, technician_name,
                    labor_hours, total_cost, summary, details, parts_used, created_by_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE maintenance_logs
                SET machine_id = ?, service_date = ?, service_type = ?, technician_name = ?,
                    labor_hours = ?, total_cost = ?, summary = ?, details = ?, parts_used = ?
                WHERE log_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectedLogId == null ? insertSql : updateSql)) {
            ps.setInt(1, machine.id);
            setRequiredDate(ps, 2, logDateField, "Service date");
            ps.setString(3, selected(logTypeBox));
            ps.setString(4, nullable(technicianField));
            ps.setBigDecimal(5, decimal(laborHoursField, "Labor hours"));
            ps.setBigDecimal(6, decimal(logCostField, "Cost"));
            ps.setString(7, nullable(logSummaryArea));
            ps.setString(8, nullable(logDetailsArea));
            ps.setString(9, nullable(partsUsedArea));
            if (selectedLogId == null) {
                setInteger(ps, 10, SessionManager.getCurrentUserId());
            } else {
                ps.setInt(10, selectedLogId);
            }
            ps.executeUpdate();
            clearLogEditor();
            loadLogs();
            loadMachines();
        } catch (SQLException | IllegalArgumentException ex) {
            showDatabaseError("save maintenance log", ex);
        }
    }

    private void saveTicket() {
        String problem = problemArea.getText().trim();
        if (problem.isBlank()) {
            showValidation("Problem summary is required.");
            return;
        }
        ItemOption machine = (ItemOption) ticketMachineBox.getSelectedItem();

        String insertSql = """
                INSERT INTO maintenance_tickets (
                    machine_id, priority, status, assigned_to_name, due_date,
                    problem_summary, resolution_summary, notes, opened_by_user_id,
                    resolved_at, closed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateSql = """
                UPDATE maintenance_tickets
                SET machine_id = ?, priority = ?, status = ?, assigned_to_name = ?, due_date = ?,
                    problem_summary = ?, resolution_summary = ?, notes = ?,
                    resolved_at = CASE WHEN ? IN ('RESOLVED', 'CLOSED') AND resolved_at IS NULL THEN CURRENT_TIMESTAMP ELSE resolved_at END,
                    closed_at = CASE WHEN ? = 'CLOSED' AND closed_at IS NULL THEN CURRENT_TIMESTAMP
                                     WHEN ? <> 'CLOSED' THEN NULL ELSE closed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE ticket_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectedTicketId == null ? insertSql : updateSql)) {
            if (machine == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, machine.id);
            }
            String status = selected(ticketStatusBox);
            ps.setString(2, selected(priorityBox));
            ps.setString(3, status);
            ps.setString(4, nullable(assignedToField));
            setDate(ps, 5, dueDateField);
            ps.setString(6, problem);
            ps.setString(7, nullable(resolutionArea));
            ps.setString(8, nullable(ticketNotesArea));
            if (selectedTicketId == null) {
                setInteger(ps, 9, SessionManager.getCurrentUserId());
                if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
                    ps.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
                } else {
                    ps.setNull(10, java.sql.Types.TIMESTAMP);
                }
                if ("CLOSED".equals(status)) {
                    ps.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
                } else {
                    ps.setNull(11, java.sql.Types.TIMESTAMP);
                }
            } else {
                ps.setString(9, status);
                ps.setString(10, status);
                ps.setString(11, status);
                ps.setInt(12, selectedTicketId);
            }
            ps.executeUpdate();
            clearTicketEditor();
            loadTickets();
        } catch (SQLException | IllegalArgumentException ex) {
            showDatabaseError("save ticket", ex);
        }
    }

    private void loadSelectedMachine() {
        int row = machineTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) machineTableModel.getValueAt(machineTable.convertRowIndexToModel(row), 0);
        String sql = "SELECT * FROM maintenance_machines WHERE machine_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectedMachineId = id;
                    machineNameField.setText(rs.getString("machine_name"));
                    assetTagField.setText(value(rs.getString("asset_tag")));
                    serialNumberField.setText(value(rs.getString("serial_number")));
                    manufacturerField.setText(value(rs.getString("manufacturer")));
                    modelField.setText(value(rs.getString("model")));
                    machineTypeField.setText(value(rs.getString("machine_type")));
                    machineLocationField.setText(value(rs.getString("location_name")));
                    machineStatusBox.setSelectedItem(rs.getString("status"));
                    purchaseDateField.setText(formatDate(rs.getDate("purchase_date")));
                    warrantyDateField.setText(formatDate(rs.getDate("warranty_expiration_date")));
                    lastServiceDateField.setText(formatDate(rs.getDate("last_service_date")));
                    nextServiceDateField.setText(formatDate(rs.getDate("next_service_date")));
                    machineNotesArea.setText(value(rs.getString("notes")));
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("load selected machine", ex);
        }
    }

    private void loadSelectedPart() {
        int row = partTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) partTableModel.getValueAt(partTable.convertRowIndexToModel(row), 0);
        String sql = "SELECT * FROM maintenance_parts WHERE part_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectedPartId = id;
                    partNameField.setText(rs.getString("part_name"));
                    partNumberField.setText(value(rs.getString("part_number")));
                    categoryField.setText(value(rs.getString("category")));
                    quantityField.setText(rs.getBigDecimal("quantity_on_hand").toPlainString());
                    reorderPointField.setText(rs.getBigDecimal("reorder_point").toPlainString());
                    reorderQuantityField.setText(rs.getBigDecimal("reorder_quantity").toPlainString());
                    unitCostField.setText(rs.getBigDecimal("unit_cost").toPlainString());
                    vendorField.setText(value(rs.getString("vendor_name")));
                    binLocationField.setText(value(rs.getString("bin_location")));
                    partActiveBox.setSelected(rs.getBoolean("is_active"));
                    partNotesArea.setText(value(rs.getString("notes")));
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("load selected part", ex);
        }
    }

    private void loadSelectedLog() {
        int row = logTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) logTableModel.getValueAt(logTable.convertRowIndexToModel(row), 0);
        String sql = "SELECT * FROM maintenance_logs WHERE log_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectedLogId = id;
                    selectItem(logMachineBox, rs.getInt("machine_id"));
                    logDateField.setText(formatDate(rs.getDate("service_date")));
                    logTypeBox.setSelectedItem(rs.getString("service_type"));
                    technicianField.setText(value(rs.getString("technician_name")));
                    laborHoursField.setText(rs.getBigDecimal("labor_hours").toPlainString());
                    logCostField.setText(rs.getBigDecimal("total_cost").toPlainString());
                    logSummaryArea.setText(value(rs.getString("summary")));
                    logDetailsArea.setText(value(rs.getString("details")));
                    partsUsedArea.setText(value(rs.getString("parts_used")));
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("load selected log", ex);
        }
    }

    private void loadSelectedTicket() {
        int row = ticketTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) ticketTableModel.getValueAt(ticketTable.convertRowIndexToModel(row), 0);
        String sql = "SELECT * FROM maintenance_tickets WHERE ticket_id = ?";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    selectedTicketId = id;
                    int machineId = rs.getInt("machine_id");
                    if (rs.wasNull()) {
                        ticketMachineBox.setSelectedIndex(ticketMachineBox.getItemCount() > 0 ? 0 : -1);
                    } else {
                        selectItem(ticketMachineBox, machineId);
                    }
                    priorityBox.setSelectedItem(rs.getString("priority"));
                    ticketStatusBox.setSelectedItem(rs.getString("status"));
                    assignedToField.setText(value(rs.getString("assigned_to_name")));
                    dueDateField.setText(formatDate(rs.getDate("due_date")));
                    problemArea.setText(value(rs.getString("problem_summary")));
                    resolutionArea.setText(value(rs.getString("resolution_summary")));
                    ticketNotesArea.setText(value(rs.getString("notes")));
                }
            }
        } catch (SQLException ex) {
            showDatabaseError("load selected ticket", ex);
        }
    }

    private void clearMachineEditor() {
        selectedMachineId = null;
        machineTable.clearSelection();
        machineNameField.setText("");
        assetTagField.setText("");
        serialNumberField.setText("");
        manufacturerField.setText("");
        modelField.setText("");
        machineTypeField.setText("");
        machineLocationField.setText("");
        machineStatusBox.setSelectedItem("ACTIVE");
        purchaseDateField.setText("");
        warrantyDateField.setText("");
        lastServiceDateField.setText("");
        nextServiceDateField.setText("");
        machineNotesArea.setText("");
    }

    private void clearPartEditor() {
        selectedPartId = null;
        partTable.clearSelection();
        partNameField.setText("");
        partNumberField.setText("");
        categoryField.setText("");
        quantityField.setText("0");
        reorderPointField.setText("0");
        reorderQuantityField.setText("0");
        unitCostField.setText("0.00");
        vendorField.setText("");
        binLocationField.setText("");
        partActiveBox.setSelected(true);
        partNotesArea.setText("");
    }

    private void clearLogEditor() {
        selectedLogId = null;
        logTable.clearSelection();
        if (logMachineBox.getItemCount() > 0) {
            logMachineBox.setSelectedIndex(0);
        }
        logDateField.setText(LocalDate.now().toString());
        logTypeBox.setSelectedItem("PREVENTIVE");
        technicianField.setText("");
        laborHoursField.setText("0.00");
        logCostField.setText("0.00");
        logSummaryArea.setText("");
        logDetailsArea.setText("");
        partsUsedArea.setText("");
    }

    private void clearTicketEditor() {
        selectedTicketId = null;
        ticketTable.clearSelection();
        if (ticketMachineBox.getItemCount() > 0) {
            ticketMachineBox.setSelectedIndex(0);
        }
        priorityBox.setSelectedItem("NORMAL");
        ticketStatusBox.setSelectedItem("OPEN");
        assignedToField.setText("");
        dueDateField.setText("");
        problemArea.setText("");
        resolutionArea.setText("");
        ticketNotesArea.setText("");
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JPanel tabPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(12, 0, 0, 0));
        return panel;
    }

    private static JPanel buildSearchPanel(JTextField field, String label, Runnable searchAction, Runnable refreshAction) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        JButton searchButton = new JButton("Search");
        JButton refreshButton = new JButton("Refresh");
        panel.add(new JLabel(label), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(searchButton);
        buttons.add(refreshButton);
        panel.add(buttons, BorderLayout.EAST);
        field.addActionListener(e -> searchAction.run());
        searchButton.addActionListener(e -> searchAction.run());
        refreshButton.addActionListener(e -> refreshAction.run());
        return panel;
    }

    private static JPanel editorPanel(int width) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(16, 16, 16, 16)
        ));
        panel.setPreferredSize(new Dimension(width, 0));
        return panel;
    }

    private static GridBagConstraints editorConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 12, 0);
        return gbc;
    }

    private static void addEditorTitle(JPanel panel, GridBagConstraints gbc, String title) {
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(label, gbc);
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

    private static void addButtons(JPanel panel, GridBagConstraints gbc, int row, Runnable clearAction, Runnable saveAction) {
        JButton newButton = new JButton("New");
        JButton clearButton = new JButton("Clear");
        JButton saveButton = new JButton("Save");
        newButton.addActionListener(e -> clearAction.run());
        clearButton.addActionListener(e -> clearAction.run());
        saveButton.addActionListener(e -> saveAction.run());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(newButton);
        buttons.add(clearButton);
        buttons.add(saveButton);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(buttons, gbc);
    }

    private static void configureTable(JTable table) {
        table.setRowHeight(28);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);
    }

    private static String nullable(JTextField field) {
        String value = field.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String nullable(JTextArea area) {
        String value = area.getText().trim();
        return value.isEmpty() ? null : value;
    }

    private static String selected(JComboBox<String> box) {
        Object value = box.getSelectedItem();
        return value == null ? null : value.toString();
    }

    private static BigDecimal decimal(JTextField field, String label) {
        String value = field.getText().trim();
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private static void setDate(PreparedStatement ps, int index, JTextField field) throws SQLException {
        String value = field.getText().trim();
        if (value.isBlank()) {
            ps.setNull(index, java.sql.Types.DATE);
            return;
        }
        ps.setDate(index, Date.valueOf(parseDate(value)));
    }

    private static void setRequiredDate(PreparedStatement ps, int index, JTextField field, String label) throws SQLException {
        String value = field.getText().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " is required. Use YYYY-MM-DD.");
        }
        ps.setDate(index, Date.valueOf(parseDate(value)));
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
        }
    }

    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static String formatDate(Date date) {
        return date == null ? "" : date.toLocalDate().toString();
    }

    private static String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().toLocalDate().toString();
    }

    private static void selectItem(JComboBox<ItemOption> box, int id) {
        for (int i = 0; i < box.getItemCount(); i++) {
            ItemOption option = box.getItemAt(i);
            if (option.id == id) {
                box.setSelectedIndex(i);
                return;
            }
        }
    }

    private void showValidation(String message) {
        JOptionPane.showMessageDialog(this, message, "Maintenance Management", JOptionPane.WARNING_MESSAGE);
    }

    private void showDatabaseError(String action, Exception ex) {
        JOptionPane.showMessageDialog(this, "Could not " + action + ".\n\n" + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
    }

    private record ItemOption(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
