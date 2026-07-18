package ui.screens;

import services.LanApiClient;
import services.LanMachineService;
import services.LanMaintenanceWorkflowService;
import managers.NavigationManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
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
    private final JTextField unitCostField = new JTextField("0");
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
    private final JTextField logCostField = new JTextField("0");
    private final JTextArea logSummaryArea = textArea(3, 24);
    private final JTextArea logDetailsArea = textArea(4, 24);
    private final JTextArea partsUsedArea = textArea(3, 24);
    private Integer selectedLogId;

    private final JTextField ticketSearchField = new JTextField();
    private final DefaultTableModel ticketTableModel = readOnlyModel("ID", "Opened", "Machine", "Created By", "Priority", "Status", "Problem", "Assigned", "Due");
    private final JTable ticketTable = new JTable(ticketTableModel);
    private final JComboBox<ItemOption> ticketMachineBox = new JComboBox<>();
    private final JComboBox<String> priorityBox = new JComboBox<>(new String[]{"LOW", "NORMAL", "HIGH", "URGENT"});
    private final JComboBox<String> ticketStatusBox = new JComboBox<>(new String[]{"OPEN", "IN_PROGRESS", "WAITING_PARTS", "RESOLVED", "CANCELED"});
    private final JComboBox<String> ticketFilterBox = new JComboBox<>(new String[]{"Active", "Resolved", "Ticket History", "All"});
    private final JTextField createdByField = new JTextField();
    private final JTextField assignedToField = new JTextField();
    private final JTextField dueDateField = new JTextField();
    private final JTextArea problemArea = textArea(4, 24);
    private final JTextArea resolutionArea = textArea(4, 24);
    private final JTextArea ticketNotesArea = textArea(3, 24);
    private final JButton markResolvedButton = new JButton("Mark Resolved");
    private final JButton closeResolvedButton = new JButton("Close Resolved");
    private Integer selectedTicketId;
    private String selectedTicketStatus;

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
        JPanel filterPanel = buildSearchPanel(ticketSearchField, "Search tickets:", this::loadTickets, () -> {
            ticketSearchField.setText("");
            ticketFilterBox.setSelectedItem("Active");
            loadTickets();
        });
        JPanel filterControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterControls.setOpaque(false);
        filterControls.add(new JLabel("View:"));
        filterControls.add(ticketFilterBox);
        filterPanel.add(filterControls, BorderLayout.SOUTH);
        ticketFilterBox.addActionListener(e -> loadTickets());
        panel.add(filterPanel, BorderLayout.NORTH);
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
        createdByField.setEditable(false);
        addFormRow(panel, gbc, 4, "Created By:", createdByField);
        addFormRow(panel, gbc, 5, "Assigned To:", assignedToField);
        addFormRow(panel, gbc, 6, "Due Date:", dueDateField);
        addFormRow(panel, gbc, 7, "Problem:", new JScrollPane(problemArea));
        addFormRow(panel, gbc, 8, "Resolution:", new JScrollPane(resolutionArea));
        addFormRow(panel, gbc, 9, "Notes:", new JScrollPane(ticketNotesArea));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        JButton newButton = new JButton("New");
        JButton saveButton = new JButton("Save");
        newButton.addActionListener(e -> clearTicketEditor());
        saveButton.addActionListener(e -> saveTicket());
        markResolvedButton.addActionListener(e -> markSelectedTicketResolved());
        closeResolvedButton.addActionListener(e -> closeResolvedTicket());
        buttonPanel.add(newButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(markResolvedButton);
        buttonPanel.add(closeResolvedButton);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.anchor = GridBagConstraints.SOUTH;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 0, 0, 0);
        panel.add(buttonPanel, gbc);
        setTicketActionState();
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
        try {for(var row:LanApiClient.loadMachineState(machineSearchField.getText().trim()).machines())machineTableModel.addRow(new Object[]{row.id(),row.name(),row.assetTag(),row.type(),row.status(),row.location(),dateText(row.nextServiceDate())});}
        catch (Exception ex) {
            showDatabaseError("load machines", ex);
        }
    }

    private void loadParts() {
        partTableModel.setRowCount(0);
        try {for(var row:LanApiClient.loadMaintenanceParts(partSearchField.getText().trim()))partTableModel.addRow(new Object[]{row.partId(),row.name(),row.partNumber(),row.quantity(),row.reorderPoint(),row.vendor(),row.active()?"Yes":"No"});}
        catch (Exception ex) {
            showDatabaseError("load parts", ex);
        }
    }

    private void loadLogs() {
        logTableModel.setRowCount(0);
        try {for(var row:LanApiClient.loadMaintenanceWorkflow("","All").logs())logTableModel.addRow(new Object[]{row.id(),dateText(row.date()),row.machine(),row.type(),row.technician(),row.hours(),row.cost(),row.summary()});}
        catch (Exception ex) {
            showDatabaseError("load maintenance logs", ex);
        }
    }

    private void loadTickets() {
        ticketTableModel.setRowCount(0);
        try {var state=LanApiClient.loadMaintenanceWorkflow(ticketSearchField.getText().trim(),String.valueOf(ticketFilterBox.getSelectedItem()));for(var row:state.tickets())ticketTableModel.addRow(new Object[]{row.id(),formatTimestamp(new Timestamp(row.openedEpochMillis())),row.machine(),"Created by: "+row.creator(),row.priority(),row.status(),row.problem(),row.assigned(),dateText(row.dueDate())});}
        catch (Exception ex) {
            showDatabaseError("load tickets", ex);
        }
    }

    private void autoCloseResolvedTickets() {
        // Performed atomically by the server whenever the ticket state is loaded.
    }

    private void loadMachineOptions() {
        logMachineBox.removeAllItems();
        ticketMachineBox.removeAllItems();
        try {for(var row:LanApiClient.loadMaintenanceWorkflow("","All").machines()) {
                ItemOption option = new ItemOption(row.id(), row.name());
                logMachineBox.addItem(option);
                ticketMachineBox.addItem(option);
            }} catch (Exception ex) {
            showDatabaseError("load machine choices", ex);
        }
    }

    private void saveMachine() {
        String name = machineNameField.getText().trim();
        if (name.isBlank()) {
            showValidation("Machine name is required.");
            return;
        }

        try {
            LanApiClient.saveMachine(new LanMachineService.Machine(selectedMachineId,name,nullable(assetTagField),nullable(serialNumberField),nullable(manufacturerField),nullable(modelField),nullable(machineTypeField),null,nullable(machineLocationField),selected(machineStatusBox),parseOptionalDate(purchaseDateField),parseOptionalDate(warrantyDateField),parseOptionalDate(lastServiceDateField),parseOptionalDate(nextServiceDateField),nullable(machineNotesArea)),java.util.UUID.randomUUID().toString());
            clearMachineEditor();
            refreshAll();
        } catch (Exception ex) {
            showDatabaseError("save machine", ex);
        }
    }

    private void savePart() {
        String name = partNameField.getText().trim();
        if (name.isBlank()) {
            showValidation("Part name is required.");
            return;
        }

        try {
            LanApiClient.saveMaintenancePart(new LanApiClient.MaintenancePart(selectedPartId,name,nullable(partNumberField),nullable(categoryField),decimal(quantityField,"On hand"),decimal(reorderPointField,"Reorder point"),decimal(reorderQuantityField,"Reorder quantity"),moneyDecimal(unitCostField,"Unit cost"),nullable(vendorField),nullable(binLocationField),partActiveBox.isSelected(),nullable(partNotesArea)),java.util.UUID.randomUUID().toString());
            clearPartEditor();
            loadParts();
        } catch (Exception ex) {
            showDatabaseError("save part", ex);
        }
    }

    private void saveLog() {
        ItemOption machine = (ItemOption) logMachineBox.getSelectedItem();
        if (machine == null) {
            showValidation("Add or select a machine before saving a maintenance log.");
            return;
        }

        try {
            LanApiClient.saveMaintenanceWorkflow("SAVE_LOG",new LanMaintenanceWorkflowService.Log(selectedLogId,machine.id,parseRequiredDate(logDateField,"Service date"),selected(logTypeBox),nullable(technicianField),decimal(laborHoursField,"Labor hours"),moneyDecimal(logCostField,"Cost"),nullable(logSummaryArea),nullable(logDetailsArea),nullable(partsUsedArea)),java.util.UUID.randomUUID().toString());
            clearLogEditor();
            loadLogs();
            loadMachines();
        } catch (Exception ex) {
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
        String status = selected(ticketStatusBox);
        if ("CLOSED".equals(status)) {
            showValidation("Closed tickets are finalized history. Use Close Resolved only for resolved tickets.");
            return;
        }
        String resolutionSummary = resolutionArea.getText().trim();
        if ("RESOLVED".equals(status) && resolutionSummary.isBlank()) {
            showValidation("A resolution summary is required before marking a ticket resolved.");
            return;
        }

        try {
            LanApiClient.saveMaintenanceWorkflow("SAVE_TICKET",new LanMaintenanceWorkflowService.Ticket(selectedTicketId,machine==null?null:machine.id,selected(priorityBox),status,nullable(assignedToField),parseOptionalDate(dueDateField),problem,resolutionSummary.isBlank()?null:resolutionSummary,nullable(ticketNotesArea),createdByField.getText()),java.util.UUID.randomUUID().toString());
            clearTicketEditor();
            loadTickets();
        } catch (Exception ex) {
            showDatabaseError("save ticket", ex);
        }
    }

    private void markSelectedTicketResolved() {
        if (selectedTicketId == null) {
            showValidation("Select a ticket before marking it resolved.");
            return;
        }
        if (resolutionArea.getText().trim().isBlank()) {
            showValidation("Enter a resolution summary before marking this ticket resolved.");
            resolutionArea.requestFocusInWindow();
            return;
        }
        ticketStatusBox.setSelectedItem("RESOLVED");
        saveTicket();
    }

    private void closeResolvedTicket() {
        if (selectedTicketId == null) {
            showValidation("Select a resolved ticket to close.");
            return;
        }
        if (!"RESOLVED".equals(selectedTicketStatus)) {
            showValidation("Only resolved tickets can be manually closed.");
            return;
        }

        try {
            LanApiClient.closeMaintenanceTicket(selectedTicketId,java.util.UUID.randomUUID().toString());
            clearTicketEditor();
            ticketFilterBox.setSelectedItem("Ticket History");
            loadTickets();
        } catch (Exception ex) {
            showDatabaseError("close resolved ticket", ex);
        }
    }

    private void setTicketActionState() {
        boolean hasTicket = selectedTicketId != null;
        boolean isResolved = "RESOLVED".equals(selectedTicketStatus);
        markResolvedButton.setEnabled(hasTicket && !isResolved && !"CLOSED".equals(selectedTicketStatus));
        closeResolvedButton.setEnabled(hasTicket && isResolved);
    }

    private void loadSelectedMachine() {
        int row = machineTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) machineTableModel.getValueAt(machineTable.convertRowIndexToModel(row), 0);
        try {var m=LanApiClient.loadMachineDetail(id).machine();selectedMachineId=id;machineNameField.setText(m.name());assetTagField.setText(value(m.assetTag()));serialNumberField.setText(value(m.serialNumber()));manufacturerField.setText(value(m.manufacturer()));modelField.setText(value(m.model()));machineTypeField.setText(value(m.type()));machineLocationField.setText(value(m.locationName()));machineStatusBox.setSelectedItem(m.status());purchaseDateField.setText(dateText(m.purchaseDate()));warrantyDateField.setText(dateText(m.warrantyDate()));lastServiceDateField.setText(dateText(m.lastServiceDate()));nextServiceDateField.setText(dateText(m.nextServiceDate()));machineNotesArea.setText(value(m.notes()));}
        catch (Exception ex) {
            showDatabaseError("load selected machine", ex);
        }
    }

    private void loadSelectedPart() {
        int row = partTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) partTableModel.getValueAt(partTable.convertRowIndexToModel(row), 0);
        try {var p=LanApiClient.loadMaintenanceParts("").stream().filter(v->v.partId()!=null&&v.partId().equals(id)).findFirst().orElseThrow(()->new IllegalStateException("Part not found."));selectedPartId=id;partNameField.setText(p.name());partNumberField.setText(value(p.partNumber()));categoryField.setText(value(p.category()));quantityField.setText(p.quantity().toPlainString());reorderPointField.setText(p.reorderPoint().toPlainString());reorderQuantityField.setText(p.reorderQuantity().toPlainString());unitCostField.setText(p.unitCost().toPlainString());vendorField.setText(value(p.vendor()));binLocationField.setText(value(p.binLocation()));partActiveBox.setSelected(p.active());partNotesArea.setText(value(p.notes()));}
        catch (Exception ex) {
            showDatabaseError("load selected part", ex);
        }
    }

    private void loadSelectedLog() {
        int row = logTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) logTableModel.getValueAt(logTable.convertRowIndexToModel(row), 0);
        try {var l=LanApiClient.loadMaintenanceDetail("LOG",id).log();selectedLogId=id;selectItem(logMachineBox,l.machineId());logDateField.setText(dateText(l.serviceDate()));logTypeBox.setSelectedItem(l.type());technicianField.setText(value(l.technician()));laborHoursField.setText(l.hours().toPlainString());logCostField.setText(l.cost().toPlainString());logSummaryArea.setText(value(l.summary()));logDetailsArea.setText(value(l.details()));partsUsedArea.setText(value(l.partsUsed()));}
        catch (Exception ex) {
            showDatabaseError("load selected log", ex);
        }
    }

    private void loadSelectedTicket() {
        int row = ticketTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        Integer id = (Integer) ticketTableModel.getValueAt(ticketTable.convertRowIndexToModel(row), 0);
        try {var t=LanApiClient.loadMaintenanceDetail("TICKET",id).ticket();selectedTicketId=id;if(t.machineId()==null)ticketMachineBox.setSelectedIndex(ticketMachineBox.getItemCount()>0?0:-1);else selectItem(ticketMachineBox,t.machineId());priorityBox.setSelectedItem(t.priority());selectedTicketStatus=t.status();ticketStatusBox.setSelectedItem(selectedTicketStatus);createdByField.setText(t.creator());assignedToField.setText(value(t.assigned()));dueDateField.setText(dateText(t.dueDate()));problemArea.setText(value(t.problem()));resolutionArea.setText(value(t.resolution()));ticketNotesArea.setText(value(t.notes()));setTicketActionState();}
        catch (Exception ex) {
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
        unitCostField.setText("0");
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
        logCostField.setText("0");
        logSummaryArea.setText("");
        logDetailsArea.setText("");
        partsUsedArea.setText("");
    }

    private void clearTicketEditor() {
        selectedTicketId = null;
        selectedTicketStatus = null;
        ticketTable.clearSelection();
        if (ticketMachineBox.getItemCount() > 0) {
            ticketMachineBox.setSelectedIndex(0);
        }
        priorityBox.setSelectedItem("NORMAL");
        ticketStatusBox.setSelectedItem("OPEN");
        createdByField.setText("");
        assignedToField.setText("");
        dueDateField.setText("");
        problemArea.setText("");
        resolutionArea.setText("");
        ticketNotesArea.setText("");
        setTicketActionState();
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

    private static BigDecimal moneyDecimal(JTextField field, String label) {
        return utils.CurrencyFormatter.normalize(decimal(field, label));
    }

    private static LocalDate parseOptionalDate(JTextField field) {
        String value = field.getText().trim();
        return value.isBlank()?null:parseDate(value);
    }

    private static LocalDate parseRequiredDate(JTextField field, String label) {
        String value = field.getText().trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " is required. Use YYYY-MM-DD.");
        }
        return parseDate(value);
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD.");
        }
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static String dateText(LocalDate date) { return date == null ? "" : date.toString(); }

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
