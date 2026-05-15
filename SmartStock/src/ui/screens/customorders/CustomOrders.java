package ui.screens.customorders;

import data.DB;
import managers.PermissionManager;
import managers.SessionManager;
import services.CustomOrderDataService;
import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.CustomerOption;
import services.CustomOrderDataService.EmployeeOption;
import services.CustomOrderDataService.OrderLineRequest;
import services.CustomOrderDataService.OrderSaveRequest;
import services.CustomOrderDataService.PrintAddonRequest;
import services.CustomOrderDataService.PrintMaterialOption;
import services.CustomOrderDataService.PrintSizePresetOption;
import services.CustomOrderDataService.VariantOption;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CustomOrders extends JFrame {
    private static final String[] DEFAULT_DESIGN_PLACEMENTS = {
            "Line 1",
            "Line 2",
            "Line 3",
            "Top",
            "Middle",
            "Bottom",
            "Pocket",
            "Chest",
            "Left Chest",
            "Right Chest",
            "Front",
            "Back",
            "Left Sleeve",
            "Right Sleeve"
    };

    private final boolean canCreateOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER");
    private final boolean canManageOrders = PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS");
    private final boolean canViewAssignedOrders = PermissionManager.hasPermission("VIEW_ASSIGNED_CUSTOM_ORDERS");

    private CustomerInfoPanel customerInfoPanel;
    private DatePickerField dueDateField;
    private JComboBox<CustomItemOption> orderItemBox;
    private JComboBox<VariantOption> variantBox;
    private JTextField linePriceField;
    private JComboBox<PrintMaterialOption> printMaterialBox;
    private JComboBox<PrintSizePresetOption> printSizePresetBox;
    private JTextField printChargeField;
    private JTextField printLineCountField;
    private JTextField printDescriptionField;
    private final List<JComponent> printAddOnComponents = new ArrayList<>();
    private final List<JComponent> printLineComponents = new ArrayList<>();
    private DefaultTableModel printAddonModel;
    private JTable printAddonTable;
    private JTextField lineQuantityField;
    private JTextField widthField;
    private JTextField lengthField;
    private JLabel areaCalculationLabel;
    private JComboBox<String> designPlacementBox;
    private JTextField designPlacementField;
    private JTextArea lineNotesArea;
    private DefaultTableModel orderLineModel;
    private JTable orderLineTable;
    private JButton addLineButton;
    private int selectedOrderLineModelRow = -1;
    private JLabel orderTotalLabel;
    private ButtonGroup paymentMethodGroup;
    private JToggleButton cashPaymentButton;
    private JToggleButton cardPaymentButton;
    private JToggleButton chequePaymentButton;
    private JToggleButton accountPaymentButton;
    private String selectedPaymentMethod;
    private JTextField paymentReferenceField;
    private JTextField upfrontPaymentField;
    private JLabel balanceDueLabel;
    private final List<JComponent> areaLineComponents = new ArrayList<>();

    private DefaultTableModel ordersModel;
    private JTable ordersTable;
    private TableRowSorter<DefaultTableModel> ordersSorter;
    private JTextField orderSearchField;
    private JComboBox<String> statusFilterBox;
    private JComboBox<EmployeeOption> assignEmployeeBox;
    private JComboBox<String> manageStatusBox;
    private JTextArea selectedOrderDetailsArea;
    private Long selectedOrderId;
    private DefaultTableModel myOrdersModel;
    private JTable myOrdersTable;
    private TableRowSorter<DefaultTableModel> myOrdersSorter;
    private JTextField myOrderSearchField;
    private JComboBox<String> myStatusFilterBox;
    private JTextArea myOrderDetailsArea;
    private CustomOrdersLookupTabPanel orderLookupPanel;
    private boolean orderEntryDataLoaded;
    private boolean orderLookupLoaded;
    private boolean myOrdersLoaded;
    private boolean manageOrdersLoaded;

    public CustomOrders() {
        this(false);
    }

    protected CustomOrders(boolean orderManagementMode) {
        setTitle(orderManagementMode ? "Orders" : "Custom Orders");
        setSize(1180, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, orderManagementMode ? "Orders" : "CustomOrders"));

        JTabbedPane tabs = new JTabbedPane();
        if (!orderManagementMode && canCreateOrders) {
            tabs.addTab("New Order", buildOrderEntryPanel());
        }
        if (!orderManagementMode && canCreateOrders) {
            tabs.addTab("Order Lookup", buildOrderLookupPanel());
        }
        if (orderManagementMode && (canViewAssignedOrders || canManageOrders)) {
            tabs.addTab("My Orders", buildMyOrdersPanel());
        }
        if (orderManagementMode && canManageOrders) {
            tabs.addTab("Manage Orders", buildManageOrdersPanel());
        }
        if (tabs.getTabCount() == 0) {
            add(new JLabel("You do not have permission to access this screen.", SwingConstants.CENTER), BorderLayout.CENTER);
        } else {
            tabs.addChangeListener(e -> loadSelectedTabData(tabs));
            add(tabs, BorderLayout.CENTER);
        }

        WindowHelper.showPosWindow(this);
        if (tabs.getTabCount() > 0) {
            SwingUtilities.invokeLater(() -> loadSelectedTabData(tabs));
        }
    }

    private JPanel buildOrderEntryPanel() {
        CustomOrdersNewOrderTabPanel panel = new CustomOrdersNewOrderTabPanel(new CustomOrdersNewOrderTabPanel.Handler() {
            @Override public void orderItemChanged() { loadVariantsForSelectedItem(); applySelectedOrderItemPrice(); }
            @Override public void variantChanged() { applySelectedOrderItemPrice(); }
            @Override public void printMaterialChanged() { loadPrintSizePresets(); applySelectedPrintPresetPrice(); }
            @Override public void printPresetChanged() { applySelectedPrintPresetPrice(); }
            @Override public Runnable printLineCountChanged() { return () -> SwingUtilities.invokeLater(CustomOrders.this::applySelectedPrintPresetPrice); }
            @Override public void addPrintAddon() { CustomOrders.this.addPrintAddon(); }
            @Override public void removePrintAddon() { removeSelectedPrintAddon(); }
            @Override public Runnable areaChanged() { return CustomOrders.this::updateAreaCalculationPreview; }
            @Override public void addPlacement() { addDesignPlacementNote(); }
            @Override public void addOrderLine() { addOrderLine(); }
            @Override public void removeOrderLine() { removeSelectedOrderLine(); }
            @Override public void cartSelectionChanged() { loadSelectedCartLineIntoEditor(); }
            @Override public void selectPaymentMethod(String method) { CustomOrders.this.selectPaymentMethod(method); }
            @Override public Runnable upfrontChanged() { return CustomOrders.this::updatePaymentPreview; }
            @Override public void saveOrder() { saveCustomOrder(); }
            @Override public void clearOrder() { clearOrderEntry(); }
        });
        customerInfoPanel = panel.customerInfoPanel;
        dueDateField = panel.dueDateField;
        orderItemBox = panel.orderItemBox;
        variantBox = panel.variantBox;
        linePriceField = panel.linePriceField;
        printMaterialBox = panel.printMaterialBox;
        printSizePresetBox = panel.printSizePresetBox;
        printChargeField = panel.printChargeField;
        printLineCountField = panel.printLineCountField;
        printDescriptionField = panel.printDescriptionField;
        printAddOnComponents.clear();
        printAddOnComponents.addAll(panel.printAddOnComponents);
        printLineComponents.clear();
        printLineComponents.addAll(panel.printLineComponents);
        printAddonModel = panel.printAddonModel;
        printAddonTable = panel.printAddonTable;
        lineQuantityField = panel.lineQuantityField;
        widthField = panel.widthField;
        lengthField = panel.lengthField;
        areaCalculationLabel = panel.areaCalculationLabel;
        designPlacementBox = panel.designPlacementBox;
        designPlacementField = panel.designPlacementField;
        lineNotesArea = panel.lineNotesArea;
        orderLineModel = panel.orderLineModel;
        orderLineTable = panel.orderLineTable;
        addLineButton = panel.addLineButton;
        orderTotalLabel = panel.orderTotalLabel;
        paymentMethodGroup = panel.paymentMethodGroup;
        cashPaymentButton = panel.cashPaymentButton;
        cardPaymentButton = panel.cardPaymentButton;
        chequePaymentButton = panel.chequePaymentButton;
        accountPaymentButton = panel.accountPaymentButton;
        paymentReferenceField = panel.paymentReferenceField;
        upfrontPaymentField = panel.upfrontPaymentField;
        balanceDueLabel = panel.balanceDueLabel;
        areaLineComponents.clear();
        areaLineComponents.addAll(panel.areaLineComponents);
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
        return panel;
    }

    private JPanel buildManageOrdersPanel() {
        CustomOrdersManageTabPanel panel = new CustomOrdersManageTabPanel(new CustomOrdersManageTabPanel.Handler() {
            @Override public void loadSelectedOrder() { CustomOrders.this.loadSelectedOrder(); }
            @Override public Runnable applyFilter() { return CustomOrders.this::applyOrderFilter; }
            @Override public void saveAssignment() { CustomOrders.this.saveAssignment(); }
            @Override public void refreshOrders() { CustomOrders.this.loadOrders(); }
        });
        ordersModel = panel.ordersModel;
        ordersTable = panel.ordersTable;
        ordersSorter = panel.ordersSorter;
        orderSearchField = panel.orderSearchField;
        statusFilterBox = panel.statusFilterBox;
        assignEmployeeBox = panel.assignEmployeeBox;
        manageStatusBox = panel.manageStatusBox;
        selectedOrderDetailsArea = panel.selectedOrderDetailsArea;
        return panel;
    }

    private void selectPaymentMethod(String method) {
        selectedPaymentMethod = method;
        if ("CASH".equals(method) && cashPaymentButton != null) {
            cashPaymentButton.setSelected(true);
        } else if ("CARD".equals(method) && cardPaymentButton != null) {
            cardPaymentButton.setSelected(true);
        } else if ("CHEQUE".equals(method) && chequePaymentButton != null) {
            chequePaymentButton.setSelected(true);
        } else if ("ACCOUNT".equals(method) && accountPaymentButton != null) {
            accountPaymentButton.setSelected(true);
        }
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
    }

    private void updatePaymentButtonStyles() {
        stylePaymentButton(cashPaymentButton, "CASH".equals(selectedPaymentMethod));
        stylePaymentButton(cardPaymentButton, "CARD".equals(selectedPaymentMethod));
        stylePaymentButton(chequePaymentButton, "CHEQUE".equals(selectedPaymentMethod));
        stylePaymentButton(accountPaymentButton, "ACCOUNT".equals(selectedPaymentMethod));
    }

    private void stylePaymentButton(JToggleButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setBackground(selected ? new Color(30, 64, 175) : new Color(64, 64, 64));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(selected ? new Color(147, 197, 253) : new Color(120, 120, 120), selected ? 2 : 1));
        button.setOpaque(true);
    }

    private void updatePaymentReferenceState() {
        if (paymentReferenceField == null) {
            return;
        }
        boolean needsReference = selectedPaymentMethod != null && !"CASH".equals(selectedPaymentMethod);
        paymentReferenceField.setEnabled(needsReference);
        if (!needsReference) {
            paymentReferenceField.setText("");
        }
        paymentReferenceField.setToolTipText(needsReference ? "Enter check number, card transaction ID, or account reference." : "Reference is only used for non-cash payments.");
    }

    private void addDesignPlacementNote() {
        String placement = designPlacementBox.getSelectedItem() == null ? "" : designPlacementBox.getSelectedItem().toString();
        String instruction = designPlacementField.getText().trim();
        if (instruction.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the design/text needed for the selected placement.");
            return;
        }
        String entry = placement + ": " + instruction;
        if (lineNotesArea.getText().trim().isEmpty()) {
            lineNotesArea.setText(entry);
        } else {
            lineNotesArea.append("\n" + entry);
        }
        designPlacementField.setText("");
        designPlacementField.requestFocusInWindow();
    }

    private JPanel buildMyOrdersPanel() {
        CustomOrdersMyOrdersTabPanel panel = new CustomOrdersMyOrdersTabPanel(new CustomOrdersMyOrdersTabPanel.Handler() {
            @Override public void loadSelectedMyOrder() { CustomOrders.this.loadSelectedMyOrder(); }
            @Override public Runnable applyFilter() { return CustomOrders.this::applyMyOrderFilter; }
            @Override public void refreshMyOrders() { CustomOrders.this.loadMyOrders(); }
        });
        myOrdersModel = panel.myOrdersModel;
        myOrdersTable = panel.myOrdersTable;
        myOrdersSorter = panel.myOrdersSorter;
        myOrderSearchField = panel.myOrderSearchField;
        myStatusFilterBox = panel.myStatusFilterBox;
        myOrderDetailsArea = panel.myOrderDetailsArea;
        return panel;
    }

    private void loadSelectedTabData(JTabbedPane tabs) {
        if (tabs == null || tabs.getTabCount() == 0) {
            return;
        }
        String title = tabs.getTitleAt(tabs.getSelectedIndex());
        if ("New Order".equals(title) && !orderEntryDataLoaded) {
            loadOrderEntryDataAsync();
        } else if ("Order Lookup".equals(title) && !orderLookupLoaded) {
            if (orderLookupPanel != null) {
                orderLookupPanel.load(createLookupHandler());
            }
            orderLookupLoaded = true;
        } else if ("My Orders".equals(title) && !myOrdersLoaded) {
            loadMyOrdersAsync(() -> myOrdersLoaded = true);
        } else if ("Manage Orders".equals(title) && !manageOrdersLoaded) {
            loadEmployees();
            loadOrdersAsync(() -> manageOrdersLoaded = true);
        }
    }

    private void loadOrderEntryDataAsync() {
        if (orderItemBox == null) {
            return;
        }
        orderItemBox.removeAllItems();
        printMaterialBox.removeAllItems();
        designPlacementBox.removeAllItems();
        orderItemBox.addItem(new CustomItemOption(null, "Loading...", "INVENTORY", "VARIABLE", null, false, null, null, null, null, null));
        printMaterialBox.addItem(new PrintMaterialOption(null, "Loading..."));
        designPlacementBox.addItem("Loading...");
        new SwingWorker<OrderEntryData, Void>() {
            @Override
            protected OrderEntryData doInBackground() throws Exception {
                return new OrderEntryData(
                        CustomOrderDataService.listActiveItems(),
                        CustomOrderDataService.listActivePrintMaterials(),
                        CustomOrderDataService.listActiveDesignPlacements()
                );
            }

            @Override
            protected void done() {
                try {
                    OrderEntryData data = get();
                    orderItemBox.removeAllItems();
                    for (CustomItemOption option : data.items()) {
                        orderItemBox.addItem(option);
                    }
                    printMaterialBox.removeAllItems();
                    printMaterialBox.addItem(new PrintMaterialOption(null, "No Print"));
                    for (PrintMaterialOption material : data.printMaterials()) {
                        printMaterialBox.addItem(material);
                    }
                    designPlacementBox.removeAllItems();
                    for (String placement : data.designPlacements()) {
                        designPlacementBox.addItem(placement);
                    }
                    if (designPlacementBox.getItemCount() == 0) {
                        addDefaultDesignPlacements();
                    }
                    orderEntryDataLoaded = true;
                    loadVariantsForSelectedItem();
                    loadPrintSizePresets();
                    applySelectedOrderItemPrice();
                } catch (Exception ex) {
                    orderItemBox.removeAllItems();
                    printMaterialBox.removeAllItems();
                    designPlacementBox.removeAllItems();
                    printMaterialBox.addItem(new PrintMaterialOption(null, "No Print"));
                    addDefaultDesignPlacements();
                    JOptionPane.showMessageDialog(CustomOrders.this, "Failed to load custom order setup data: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadItems() {
        if (orderItemBox != null) {
            orderItemBox.removeAllItems();
            try {
                for (CustomItemOption option : CustomOrderDataService.listActiveItems()) {
                    orderItemBox.addItem(option);
                }
            } catch (SQLException ex) {
                showDatabaseSetupMessage(ex);
            }
            loadVariantsForSelectedItem();
            applySelectedOrderItemPrice();
        }
    }

    private void loadVariantsForSelectedItem() {
        if (variantBox == null) {
            return;
        }
        variantBox.removeAllItems();
        variantBox.addItem(new VariantOption(null, "No Variant", null));
        CustomItemOption item = orderItemBox == null ? null : (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null || item.customItemId() == null) {
            return;
        }
        try {
            for (VariantOption variant : CustomOrderDataService.listActiveVariants(item.customItemId())) {
                variantBox.addItem(variant);
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void loadPrintMaterials() {
        if (printMaterialBox == null) {
            return;
        }
        printMaterialBox.removeAllItems();
        printMaterialBox.addItem(new PrintMaterialOption(null, "No Print"));
        try {
            for (PrintMaterialOption material : CustomOrderDataService.listActivePrintMaterials()) {
                printMaterialBox.addItem(material);
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        loadPrintSizePresets();
    }

    private void loadDesignPlacements() {
        if (designPlacementBox == null) {
            return;
        }
        designPlacementBox.removeAllItems();
        try {
            for (String placement : CustomOrderDataService.listActiveDesignPlacements()) {
                designPlacementBox.addItem(placement);
            }
        } catch (SQLException ex) {
            addDefaultDesignPlacements();
            showDatabaseSetupMessage(ex);
            return;
        }
        if (designPlacementBox.getItemCount() == 0) {
            addDefaultDesignPlacements();
        }
    }

    private void addDefaultDesignPlacements() {
        if (designPlacementBox == null || designPlacementBox.getItemCount() > 0) {
            return;
        }
        for (String placement : DEFAULT_DESIGN_PLACEMENTS) {
            designPlacementBox.addItem(placement);
        }
    }

    private void loadPrintSizePresets() {
        if (printSizePresetBox == null) {
            return;
        }
        PrintMaterialOption material = printMaterialBox == null ? null : (PrintMaterialOption) printMaterialBox.getSelectedItem();
        printSizePresetBox.removeAllItems();
        printSizePresetBox.addItem(new PrintSizePresetOption(null, null, "Custom Print Price", "FIXED_PRESET", null));
        boolean hasMaterial = material != null && material.printMaterialId() != null;
        boolean hasAddedPrintAddons = printAddonModel != null && printAddonModel.getRowCount() > 0;
        setPrintAddOnFieldsVisible(hasMaterial || hasAddedPrintAddons);
        printSizePresetBox.setEnabled(hasMaterial);
        printChargeField.setEnabled(hasMaterial);
        printLineCountField.setEnabled(false);
        if (!hasMaterial) {
            printChargeField.setText("0.00");
            printLineCountField.setText("1");
            return;
        }
        try {
            for (PrintSizePresetOption preset : CustomOrderDataService.listActivePrintSizePresets(material.printMaterialId())) {
                printSizePresetBox.addItem(preset);
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void setPrintAddOnFieldsVisible(boolean visible) {
        for (JComponent component : printAddOnComponents) {
            component.setVisible(visible);
        }
        if (!visible) {
            setPrintLineFieldsVisible(false);
        }
        revalidate();
        repaint();
    }

    private void setPrintLineFieldsVisible(boolean visible) {
        for (JComponent component : printLineComponents) {
            component.setVisible(visible);
        }
        revalidate();
        repaint();
    }

    private void applySelectedPrintPresetPrice() {
        if (printChargeField == null || printSizePresetBox == null) {
            return;
        }
        PrintMaterialOption material = printMaterialBox == null ? null : (PrintMaterialOption) printMaterialBox.getSelectedItem();
        if (material == null || material.printMaterialId() == null) {
            setTextIfDifferent(printChargeField, "0.00");
            printChargeField.setEnabled(false);
            return;
        }
        printChargeField.setEnabled(true);
        PrintSizePresetOption preset = (PrintSizePresetOption) printSizePresetBox.getSelectedItem();
        boolean perLine = preset != null && "PER_LINE".equals(preset.pricingMode());
        setPrintLineFieldsVisible(perLine);
        printLineCountField.setEnabled(perLine);
        if (!perLine) {
            setTextIfDifferent(printLineCountField, "1");
        }
        if (preset != null && preset.printSizePresetId() != null && preset.fixedPrice() != null) {
            BigDecimal price = preset.fixedPrice();
            if (perLine) {
                int lineCount = parsePositiveInt(printLineCountField == null ? "1" : printLineCountField.getText().trim(), 1);
                price = price.multiply(new BigDecimal(lineCount));
            }
            setTextIfDifferent(printChargeField, formatMoney(price));
        }
    }

    private void addPrintAddon() {
        PrintAddonLine addon = currentPrintAddonLine(true);
        if (addon == null) {
            return;
        }
        printAddonModel.addRow(new Object[]{
                addon.printMaterialId(),
                addon.materialName(),
                addon.printSizePresetId(),
                addon.printSizeName(),
                addon.pricingMode(),
                addon.printDescription(),
                addon.printLineCount(),
                formatMoney(addon.printCharge())
        });
        if (printMaterialBox != null) {
            printMaterialBox.setSelectedIndex(0);
        }
        if (printSizePresetBox != null) {
            printSizePresetBox.setSelectedIndex(0);
        }
        printChargeField.setText("0.00");
        printLineCountField.setText("1");
        printDescriptionField.setText("");
        updateOrderTotal();
    }

    private PrintAddonLine currentPrintAddonLine(boolean showMessages) {
        PrintMaterialOption material = printMaterialBox == null ? null : (PrintMaterialOption) printMaterialBox.getSelectedItem();
        if (material == null || material.printMaterialId() == null) {
            if (showMessages) {
                JOptionPane.showMessageDialog(this, "Select a print add on first.");
            }
            return null;
        }
        PrintSizePresetOption preset = printSizePresetBox == null ? null : (PrintSizePresetOption) printSizePresetBox.getSelectedItem();
        int lineCount = preset != null && "PER_LINE".equals(preset.pricingMode())
                ? parsePositiveInt(printLineCountField.getText().trim(), -1)
                : 1;
        if (lineCount <= 0) {
            if (showMessages) {
                JOptionPane.showMessageDialog(this, "Print lines must be at least 1.");
            }
            return null;
        }
        BigDecimal charge = parseMoney(printChargeField.getText().trim().isEmpty() ? "0" : printChargeField.getText().trim(), "Print price");
        if (charge == null) {
            return null;
        }
        String description = printDescriptionField == null ? "" : printDescriptionField.getText().trim();
        if ((preset == null || preset.printSizePresetId() == null) && description.isBlank()) {
            if (showMessages) {
                JOptionPane.showMessageDialog(this, "Enter a print description for custom print price.");
            }
            return null;
        }
        return new PrintAddonLine(
                material.printMaterialId(),
                material.materialName(),
                preset == null ? null : preset.printSizePresetId(),
                preset == null || preset.printSizePresetId() == null ? "Custom" : preset.presetName(),
                preset == null ? "FIXED_PRESET" : preset.pricingMode(),
                description,
                lineCount,
                charge
        );
    }

    private void removeSelectedPrintAddon() {
        int selectedRow = printAddonTable == null ? -1 : printAddonTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        printAddonModel.removeRow(printAddonTable.convertRowIndexToModel(selectedRow));
        updateOrderTotal();
    }

    private List<PrintAddonLine> collectPrintAddonsFromEditor() {
        List<PrintAddonLine> addons = new ArrayList<>();
        if (printAddonModel == null) {
            return addons;
        }
        for (int i = 0; i < printAddonModel.getRowCount(); i++) {
            addons.add(new PrintAddonLine(
                    parseLongValue(printAddonModel.getValueAt(i, 0)),
                    valueAt(printAddonModel, i, 1),
                    parseLongValue(printAddonModel.getValueAt(i, 2)),
                    valueAt(printAddonModel, i, 3),
                    valueAt(printAddonModel, i, 4),
                    valueAt(printAddonModel, i, 5),
                    parsePositiveInt(valueAt(printAddonModel, i, 6), 1),
                    parseMoneyValue(valueAt(printAddonModel, i, 7))
            ));
        }
        return addons;
    }

    private BigDecimal sumPrintAddons(List<PrintAddonLine> addons) {
        BigDecimal total = BigDecimal.ZERO;
        for (PrintAddonLine addon : addons) {
            total = total.add(addon.printCharge());
        }
        return total;
    }

    private String printAddonSummary(List<PrintAddonLine> addons) {
        if (addons == null || addons.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        for (PrintAddonLine addon : addons) {
            if (summary.length() > 0) {
                summary.append("; ");
            }
            summary.append(addon.materialName()).append(" / ").append(addon.printSizeName());
            if ("PER_LINE".equals(addon.pricingMode())) {
                summary.append(" x ").append(addon.printLineCount()).append(" lines");
            }
            if (!addon.printDescription().isBlank()) {
                summary.append(" - ").append(addon.printDescription());
            }
            summary.append(" ").append(formatMoney(addon.printCharge()));
        }
        return summary.toString();
    }

    private String printAddonDetails(List<PrintAddonLine> addons, BigDecimal basePrice) {
        if (addons == null || addons.isEmpty()) {
            return "";
        }
        StringBuilder details = new StringBuilder();
        details.append("Base Item: ").append(formatMoney(basePrice));
        int index = 1;
        for (PrintAddonLine addon : addons) {
            details.append("\nPrint Add On ").append(index++).append(": ").append(addon.materialName())
                    .append(" / ").append(addon.printSizeName());
            if ("PER_LINE".equals(addon.pricingMode())) {
                details.append(" / ").append(addon.printLineCount()).append(" lines");
            }
            if (!addon.printDescription().isBlank()) {
                details.append(" / ").append(addon.printDescription());
            }
            details.append(" - ").append(formatMoney(addon.printCharge()));
        }
        return details.toString();
    }

    private void setTextIfDifferent(JTextField field, String value) {
        if (field != null && !field.getText().equals(value)) {
            field.setText(value);
        }
    }

    private void loadEmployees() {
        if (assignEmployeeBox == null) {
            return;
        }
        assignEmployeeBox.removeAllItems();
        assignEmployeeBox.addItem(new EmployeeOption(null, "Unassigned"));
        try {
            for (EmployeeOption employee : CustomOrderDataService.listActiveEmployees()) {
                assignEmployeeBox.addItem(employee);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load employees: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadOrders() {
        if (ordersModel == null) {
            return;
        }
        orderLookupLoaded = true;
        manageOrdersLoaded = true;
        ordersModel.setRowCount(0);
        String sql = """
                SELECT custom_order_id, order_number, status, customer_name, customer_phone, due_date,
                       total_amount, amount_paid, balance_due, payment_method, payment_reference,
                       payment_status, assigned_to_name, taken_by_name, created_at
                FROM custom_orders
                ORDER BY created_at DESC
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ordersModel.addRow(new Object[]{
                        rs.getLong("custom_order_id"),
                        rs.getString("order_number"),
                        rs.getString("status"),
                        rs.getString("customer_name"),
                        rs.getString("customer_phone"),
                        rs.getDate("due_date"),
                        formatMoney(rs.getBigDecimal("total_amount")),
                        formatMoney(rs.getBigDecimal("amount_paid")),
                        formatMoney(rs.getBigDecimal("balance_due")),
                        formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                        rs.getString("payment_reference"),
                        rs.getString("assigned_to_name"),
                        rs.getString("taken_by_name"),
                        rs.getTimestamp("created_at")
                });
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        applyOrderFilter();
    }

    private void loadOrdersAsync(Runnable afterLoad) {
        if (ordersModel == null) {
            return;
        }
        runTableLoadAsync(
                ordersModel,
                () -> {
                    List<Object[]> rows = new ArrayList<>();
                    String sql = """
                            SELECT custom_order_id, order_number, status, customer_name, customer_phone, due_date,
                                   total_amount, amount_paid, balance_due, payment_method, payment_reference,
                                   payment_status, assigned_to_name, taken_by_name, created_at
                            FROM custom_orders
                            ORDER BY created_at DESC
                            """;
                    try (Connection conn = DB.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rows.add(new Object[]{
                                    rs.getLong("custom_order_id"),
                                    rs.getString("order_number"),
                                    rs.getString("status"),
                                    rs.getString("customer_name"),
                                    rs.getString("customer_phone"),
                                    rs.getDate("due_date"),
                                    formatMoney(rs.getBigDecimal("total_amount")),
                                    formatMoney(rs.getBigDecimal("amount_paid")),
                                    formatMoney(rs.getBigDecimal("balance_due")),
                                    formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                                    rs.getString("payment_reference"),
                                    rs.getString("assigned_to_name"),
                                    rs.getString("taken_by_name"),
                                    rs.getTimestamp("created_at")
                            });
                        }
                    }
                    return rows;
                },
                () -> {
                    if (afterLoad != null) {
                        afterLoad.run();
                    }
                    applyOrderFilter();
                }
        );
    }

    private void loadMyOrders() {
        if (myOrdersModel == null) {
            return;
        }
        myOrdersLoaded = true;
        myOrdersModel.setRowCount(0);
        myOrderDetailsArea.setText("");

        if (SessionManager.getCurrentUserId() == null) {
            return;
        }

        String sql = """
                SELECT custom_order_id, order_number, status, customer_name, customer_phone, due_date,
                       total_amount, amount_paid, balance_due, payment_method, payment_reference,
                       payment_status, created_at
                FROM custom_orders
                WHERE assigned_to_user_id = ?
                ORDER BY
                    CASE status
                        WHEN 'ASSIGNED' THEN 1
                        WHEN 'IN_PROGRESS' THEN 2
                        WHEN 'READY' THEN 3
                        WHEN 'COMPLETED' THEN 4
                        WHEN 'DELIVERED' THEN 5
                        WHEN 'CANCELLED' THEN 6
                        ELSE 6
                    END,
                    due_date NULLS LAST,
                    created_at DESC
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SessionManager.getCurrentUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    myOrdersModel.addRow(new Object[]{
                            rs.getLong("custom_order_id"),
                            rs.getString("order_number"),
                            rs.getString("status"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            rs.getDate("due_date"),
                            formatMoney(rs.getBigDecimal("total_amount")),
                            formatMoney(rs.getBigDecimal("amount_paid")),
                            formatMoney(rs.getBigDecimal("balance_due")),
                            formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                            rs.getString("payment_reference"),
                            rs.getTimestamp("created_at")
                    });
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        applyMyOrderFilter();
    }

    private void loadMyOrdersAsync(Runnable afterLoad) {
        if (myOrdersModel == null) {
            return;
        }
        myOrdersModel.setRowCount(0);
        myOrderDetailsArea.setText("");

        if (SessionManager.getCurrentUserId() == null) {
            if (afterLoad != null) {
                afterLoad.run();
            }
            return;
        }

        runTableLoadAsync(
                myOrdersModel,
                () -> {
                    List<Object[]> rows = new ArrayList<>();
                    String sql = """
                            SELECT custom_order_id, order_number, status, customer_name, customer_phone, due_date,
                                   total_amount, amount_paid, balance_due, payment_method, payment_reference,
                                   payment_status, created_at
                            FROM custom_orders
                            WHERE assigned_to_user_id = ?
                            ORDER BY
                                CASE status
                                    WHEN 'ASSIGNED' THEN 1
                                    WHEN 'IN_PROGRESS' THEN 2
                                    WHEN 'READY' THEN 3
                                    WHEN 'COMPLETED' THEN 4
                                    WHEN 'DELIVERED' THEN 5
                                    WHEN 'CANCELLED' THEN 6
                                    ELSE 6
                                END,
                                due_date NULLS LAST,
                                created_at DESC
                            """;
                    try (Connection conn = DB.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, SessionManager.getCurrentUserId());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                rows.add(new Object[]{
                                        rs.getLong("custom_order_id"),
                                        rs.getString("order_number"),
                                        rs.getString("status"),
                                        rs.getString("customer_name"),
                                        rs.getString("customer_phone"),
                                        rs.getDate("due_date"),
                                        formatMoney(rs.getBigDecimal("total_amount")),
                                        formatMoney(rs.getBigDecimal("amount_paid")),
                                        formatMoney(rs.getBigDecimal("balance_due")),
                                        formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                                        rs.getString("payment_reference"),
                                        rs.getTimestamp("created_at")
                                });
                            }
                        }
                    }
                    return rows;
                },
                () -> {
                    if (afterLoad != null) {
                        afterLoad.run();
                    }
                    applyMyOrderFilter();
                }
        );
    }

    private void saveCustomOrder() {
        CustomerOption customer = customerInfoPanel == null ? null : customerInfoPanel.getSelectedCustomer();
        String customerName = customerInfoPanel == null ? "" : customerInfoPanel.getCustomerName();
        String customerPhone = customerInfoPanel == null ? "" : customerInfoPanel.getCustomerPhone();
        if (customerName.isBlank()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }
        if (customerPhone.isBlank()) {
            JOptionPane.showMessageDialog(this, "Customer phone number is required.");
            return;
        }
        if (orderLineModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Add at least one customized line item.");
            return;
        }
        LocalDate dueDate;
        try {
            dueDate = dueDateField == null ? null : dueDateField.getSelectedDate();
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Due date must use YYYY-MM-DD.");
            return;
        }

        BigDecimal total = calculateOrderTotal();
        BigDecimal upfrontPaid = parseMoney(upfrontPaymentField.getText().trim().isEmpty() ? "0" : upfrontPaymentField.getText().trim(), "Upfront payment");
        if (upfrontPaid == null) {
            return;
        }
        if (upfrontPaid.compareTo(total) > 0) {
            JOptionPane.showMessageDialog(this, "Upfront payment cannot be more than the order total.");
            return;
        }
        if (upfrontPaid.compareTo(BigDecimal.ZERO) > 0 && (selectedPaymentMethod == null || selectedPaymentMethod.isBlank())) {
            JOptionPane.showMessageDialog(this, "Select a payment method for the upfront payment.");
            return;
        }
        String paymentReference = paymentReferenceField.getText().trim();
        if (upfrontPaid.compareTo(BigDecimal.ZERO) > 0
                && ("CARD".equals(selectedPaymentMethod) || "CHEQUE".equals(selectedPaymentMethod))
                && paymentReference.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a payment reference for card or cheque payments.");
            return;
        }
        BigDecimal balanceDue = total.subtract(upfrontPaid);
        String paymentStatus = upfrontPaid.compareTo(BigDecimal.ZERO) == 0
                ? "UNPAID"
                : balanceDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "PARTIAL";
        try {
            String orderNumber = CustomOrderDataService.saveCustomOrder(new OrderSaveRequest(
                    customer,
                    customerName,
                    customerPhone,
                    dueDate,
                    total,
                    upfrontPaid,
                    balanceDue,
                    selectedPaymentMethod,
                    paymentReference,
                    paymentStatus,
                    SessionManager.getCurrentUserId(),
                    SessionManager.getCurrentUserDisplayName(),
                    buildOrderLineRequests()
            ));
                JOptionPane.showMessageDialog(this, "Custom order " + orderNumber + " saved.");
                clearOrderEntry();
                loadOrders();
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private List<OrderLineRequest> buildOrderLineRequests() {
        List<OrderLineRequest> lines = new ArrayList<>();
        for (int i = 0; i < orderLineModel.getRowCount(); i++) {
            lines.add(new OrderLineRequest(
                    parseLongValue(orderLineModel.getValueAt(i, 0)),
                    parseLongValue(orderLineModel.getValueAt(i, 1)),
                    valueAt(orderLineModel, i, 2),
                    valueAt(orderLineModel, i, 3),
                    valueAt(orderLineModel, i, 4),
                    parseMoneyValue(valueAt(orderLineModel, i, 5)),
                    valueAt(orderLineModel, i, 6),
                    valueAt(orderLineModel, i, 7),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 8)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 9)),
                    blankToNull(orderLineModel.getValueAt(i, 10)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 11)),
                    blankToNull(orderLineModel.getValueAt(i, 12)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 13)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 19)),
                    parseLongValue(orderLineModel.getValueAt(i, 14)),
                    blankToNull(orderLineModel.getValueAt(i, 15)),
                    parseLongValue(orderLineModel.getValueAt(i, 16)),
                    blankToNull(orderLineModel.getValueAt(i, 17)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 18)),
                    parsePositiveInt(valueAt(orderLineModel, i, 20), 1),
                    buildPrintAddonRequests(printAddonsForModelRow(i))
            ));
        }
        return lines;
    }

    private List<PrintAddonRequest> buildPrintAddonRequests(List<PrintAddonLine> addons) {
        List<PrintAddonRequest> requests = new ArrayList<>();
        for (PrintAddonLine addon : addons) {
            requests.add(new PrintAddonRequest(
                    addon.printMaterialId(),
                    addon.materialName(),
                    addon.printSizePresetId(),
                    addon.printSizeName(),
                    addon.pricingMode(),
                    addon.printDescription(),
                    addon.printLineCount(),
                    addon.printCharge()
            ));
        }
        return requests;
    }

    private void saveAssignment() {
        if (selectedOrderId == null) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        EmployeeOption employee = (EmployeeOption) assignEmployeeBox.getSelectedItem();
        String status = manageStatusBox.getSelectedItem() == null ? "NEW" : manageStatusBox.getSelectedItem().toString();
        boolean assigned = employee != null && employee.userId() != null;
        if (assigned && "NEW".equals(status)) {
            status = "ASSIGNED";
        }

        String sql = """
                UPDATE custom_orders
                SET assigned_to_user_id = ?, assigned_to_name = ?,
                    assigned_by_user_id = ?, assigned_by_name = ?,
                    assigned_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE assigned_at END,
                    status = ?,
                    completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (assigned) {
                ps.setInt(1, employee.userId());
                ps.setString(2, employee.name());
            } else {
                ps.setNull(1, Types.INTEGER);
                ps.setNull(2, Types.VARCHAR);
            }
            setNullableInteger(ps, 3, SessionManager.getCurrentUserId());
            ps.setString(4, SessionManager.getCurrentUserDisplayName());
            ps.setBoolean(5, assigned);
            ps.setString(6, status);
            ps.setString(7, status);
            ps.setString(8, status);
            ps.setLong(9, selectedOrderId);
            ps.executeUpdate();
            loadOrders();
            loadMyOrders();
            JOptionPane.showMessageDialog(this, "Order updated.");
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void openOrderLookupDialog() {
        JDialog dialog = new JDialog(this, "Order Lookup", true);
        dialog.setSize(980, 620);
        dialog.setLocationRelativeTo(this);
        CustomOrdersLookupTabPanel lookupPanel = new CustomOrdersLookupTabPanel(createLookupHandler());
        lookupPanel.load(createLookupHandler());
        dialog.add(lookupPanel, BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private JPanel buildOrderLookupPanel() {
        orderLookupPanel = new CustomOrdersLookupTabPanel(createLookupHandler());
        return orderLookupPanel;
    }

    private CustomOrdersLookupTabPanel.Handler createLookupHandler() {
        return new CustomOrdersLookupTabPanel.Handler() {
            @Override public void loadLookupOrders(DefaultTableModel model, String search) { CustomOrders.this.loadLookupOrders(model, search); }
            @Override public Long selectedLookupOrderId(JTable table, DefaultTableModel model) { return CustomOrders.this.selectedLookupOrderId(table, model); }
            @Override public void loadOrderDetails(Long orderId, JTextArea detailsArea) { CustomOrders.this.loadOrderDetails(orderId, detailsArea); }
            @Override public BigDecimal parseNullableMoneyValue(Object value) { return CustomOrders.this.parseNullableMoneyValue(value); }
            @Override public boolean applyLookupPayment(Long orderId, String amountText, String method, String reference, Component parent) { return CustomOrders.this.applyLookupPayment(orderId, amountText, method, reference, parent); }
            @Override public boolean markLookupOrderDelivered(Long orderId, Component parent) { return CustomOrders.this.markLookupOrderDelivered(orderId, parent); }
            @Override public void refreshRelatedOrders() { loadOrders(); loadMyOrders(); }
        };
    }

    private void loadLookupOrders(DefaultTableModel model, String search) {
        model.setRowCount(0);
        String sql = """
                SELECT custom_order_id, order_number, status, customer_name, customer_phone,
                       total_amount, amount_paid, balance_due, payment_method, payment_reference,
                       payment_status, created_at
                FROM custom_orders
                WHERE (? = ''
                   OR LOWER(order_number) LIKE LOWER(?)
                   OR LOWER(customer_name) LIKE LOWER(?)
                   OR COALESCE(customer_phone, '') LIKE ?)
                ORDER BY created_at DESC
                LIMIT 100
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + search + "%";
            ps.setString(1, search);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getLong("custom_order_id"),
                            rs.getString("order_number"),
                            rs.getString("status"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            formatMoney(rs.getBigDecimal("total_amount")),
                            formatMoney(rs.getBigDecimal("amount_paid")),
                            formatMoney(rs.getBigDecimal("balance_due")),
                            formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                            rs.getString("payment_reference"),
                            rs.getTimestamp("created_at")
                    });
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private Long selectedLookupOrderId(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return Long.parseLong(model.getValueAt(table.convertRowIndexToModel(row), 0).toString());
    }

    private boolean applyLookupPayment(Long orderId, String amountText, String methodLabel, String reference, Component parent) {
        BigDecimal amount = parseMoney(amountText, "Payment amount");
        if (amount == null) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(parent, "Payment amount must be greater than zero.");
            return false;
        }
        String method = methodLabel.toUpperCase().replace(" ", "_");
        if (("CARD".equals(method) || "CHEQUE".equals(method)) && reference.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Enter a payment reference for card or cheque payments.");
            return false;
        }
        String lockSql = "SELECT COALESCE(balance_due, total_amount) AS balance_due FROM custom_orders WHERE custom_order_id = ? FOR UPDATE";
        String updateSql = """
                UPDATE custom_orders
                SET amount_paid = COALESCE(amount_paid, 0) + ?,
                    balance_due = GREATEST(COALESCE(balance_due, total_amount) - ?, 0),
                    payment_method = ?,
                    payment_reference = ?,
                    payment_status = CASE WHEN GREATEST(COALESCE(balance_due, total_amount) - ?, 0) <= 0 THEN 'PAID' ELSE 'PARTIAL' END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        String paymentSql = """
                INSERT INTO custom_order_payments (
                    custom_order_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement paymentPs = conn.prepareStatement(paymentSql)) {
                lockPs.setLong(1, orderId);
                BigDecimal balanceDue;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(parent, "Order was not found.");
                        conn.rollback();
                        return false;
                    }
                    balanceDue = rs.getBigDecimal("balance_due");
                }
                if (amount.compareTo(balanceDue) > 0) {
                    JOptionPane.showMessageDialog(parent, "Payment cannot be more than the balance due.");
                    conn.rollback();
                    return false;
                }
                updatePs.setBigDecimal(1, amount);
                updatePs.setBigDecimal(2, amount);
                updatePs.setString(3, method);
                updatePs.setString(4, reference.isBlank() ? null : reference);
                updatePs.setBigDecimal(5, amount);
                updatePs.setLong(6, orderId);
                updatePs.executeUpdate();

                paymentPs.setLong(1, orderId);
                paymentPs.setBigDecimal(2, amount);
                paymentPs.setString(3, method);
                paymentPs.setString(4, reference.isBlank() ? null : reference);
                setNullableInteger(paymentPs, 5, SessionManager.getCurrentUserId());
                paymentPs.setString(6, SessionManager.getCurrentUserDisplayName());
                paymentPs.executeUpdate();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Payment applied.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean markLookupOrderDelivered(Long orderId, Component parent) {
        String sql = """
                UPDATE custom_orders
                SET status = 'DELIVERED',
                    delivered_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                  AND COALESCE(balance_due, 0) <= 0
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                JOptionPane.showMessageDialog(parent, "This order still has a balance due. Complete payment before marking it delivered.");
                return false;
            }
            JOptionPane.showMessageDialog(parent, "Order marked delivered.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private void addOrderLine() {
        CustomItemOption item = (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Select a custom item.");
            return;
        }
        VariantOption variant = (VariantOption) variantBox.getSelectedItem();
        if (item.hasVariants() && (variant == null || variant.variantId() == null)) {
            JOptionPane.showMessageDialog(this, "Select a size or variant for this item.");
            return;
        }
        int quantity = parseLineQuantity();
        if (quantity <= 0) {
            return;
        }
        AreaCalculation areaCalculation = null;
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        BigDecimal basePrice;
        if ("AREA".equals(item.pricingType())) {
            areaCalculation = calculateAreaPrice(item, configuredPrice, true);
            if (areaCalculation == null) {
                return;
            }
            basePrice = areaCalculation.totalPrice();
            linePriceField.setText(formatMoney(basePrice));
        } else {
            basePrice = parseMoney(linePriceField.getText().trim(), "Base price");
            if (basePrice == null) {
                return;
            }
        }
        if ("FIXED".equals(item.pricingType()) && configuredPrice != null && basePrice.compareTo(configuredPrice) != 0) {
            JOptionPane.showMessageDialog(this, "This selection has a fixed price of " + formatMoney(configuredPrice) + ".");
            linePriceField.setText(formatMoney(configuredPrice));
            return;
        }
        List<PrintAddonLine> printAddons = collectPrintAddonsFromEditor();
        PrintAddonLine pendingAddon = currentPrintAddonLine(false);
        if (pendingAddon != null) {
            printAddons.add(pendingAddon);
        }
        BigDecimal printCharge = sumPrintAddons(printAddons);
        BigDecimal lineTotal = basePrice.add(printCharge);
        String details = "";
        if (!printAddons.isEmpty()) {
            String printDetails = printAddonDetails(printAddons, basePrice);
            details = printDetails;
        }
        String notes = lineNotesArea.getText().trim();
        if (details.isEmpty() && notes.isEmpty() && areaCalculation == null && printAddons.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter order instructions, placement notes, size details, or a print add on.");
            return;
        }
        if (selectedOrderLineModelRow >= 0) {
            Object[] updatedRow = buildOrderLineRow(item, variant, item.pricingType(), lineTotal, details, notes, areaCalculation, printAddons, basePrice);
            for (int column = 0; column < updatedRow.length; column++) {
                orderLineModel.setValueAt(updatedRow[column], selectedOrderLineModelRow, column);
            }
            orderLineTable.clearSelection();
        } else {
            for (int i = 0; i < quantity; i++) {
                String lineDetails = quantity == 1 ? details : details + "\nCopy " + (i + 1) + " of " + quantity;
                orderLineModel.addRow(buildOrderLineRow(item, variant, item.pricingType(), lineTotal, lineDetails, notes, areaCalculation, printAddons, basePrice));
            }
        }
        clearLineEditor();
        updateAreaCalculationPreview();
        updateOrderTotal();
    }

    private Object[] buildOrderLineRow(CustomItemOption item, VariantOption variant, String pricingType, BigDecimal price, String details, String notes, AreaCalculation areaCalculation, List<PrintAddonLine> printAddons, BigDecimal basePrice) {
        PrintAddonLine firstAddon = printAddons == null || printAddons.isEmpty() ? null : printAddons.get(0);
        BigDecimal totalPrintCharge = sumPrintAddons(printAddons == null ? List.of() : printAddons);
        List<PrintAddonLine> addonCopy = printAddons == null ? new ArrayList<>() : new ArrayList<>(printAddons);
        return new Object[]{
                item.customItemId(),
                variant == null ? null : variant.variantId(),
                item.name(),
                variant == null || variant.variantId() == null ? "" : variant.name(),
                pricingType,
                formatMoney(price),
                details,
                notes,
                areaCalculation == null ? "" : areaCalculation.width(),
                areaCalculation == null ? "" : areaCalculation.length(),
                    areaCalculation == null ? "" : areaCalculation.dimensionUnit(),
                areaCalculation == null ? "" : areaCalculation.area(),
                areaCalculation == null ? "" : areaCalculation.areaUnit(),
                areaCalculation == null ? "" : areaCalculation.areaPrice(),
                firstAddon == null ? null : firstAddon.printMaterialId(),
                firstAddon == null ? "" : firstAddon.materialName(),
                firstAddon == null ? null : firstAddon.printSizePresetId(),
                firstAddon == null ? "" : firstAddon.printSizeName(),
                totalPrintCharge,
                basePrice == null ? "" : basePrice,
                firstAddon == null ? 1 : firstAddon.printLineCount(),
                printAddonSummary(addonCopy),
                addonCopy
        };
    }

    private void clearLineEditor() {
        selectedOrderLineModelRow = -1;
        if (addLineButton != null) {
            addLineButton.setText("Add to Order");
        }
        designPlacementField.setText("");
        lineNotesArea.setText("");
        lineQuantityField.setText("1");
        lineQuantityField.setEnabled(true);
        if (printMaterialBox != null) {
            printMaterialBox.setSelectedIndex(0);
        }
        if (printSizePresetBox != null) {
            printSizePresetBox.setSelectedIndex(0);
        }
        printChargeField.setText("0.00");
        printLineCountField.setText("1");
        printLineCountField.setEnabled(false);
        printDescriptionField.setText("");
        widthField.setText("");
        lengthField.setText("");
        CustomItemOption item = orderItemBox == null ? null : (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null || (!"FIXED".equals(item.pricingType()) && !"AREA".equals(item.pricingType()))) {
            linePriceField.setText("");
        }
    }

    private int parseLineQuantity() {
        String quantityText = lineQuantityField == null ? "1" : lineQuantityField.getText().trim();
        if (quantityText.isEmpty()) {
            quantityText = "1";
        }
        try {
            int quantity = Integer.parseInt(quantityText);
            if (quantity <= 0) {
                JOptionPane.showMessageDialog(this, "Quantity must be at least 1.");
                return -1;
            }
            if (quantity > 100) {
                JOptionPane.showMessageDialog(this, "Quantity cannot be more than 100 lines at once.");
                return -1;
            }
            return quantity;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Quantity must be a whole number.");
            return -1;
        }
    }

    private void removeSelectedOrderLine() {
        int selectedRow = orderLineTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        orderLineModel.removeRow(orderLineTable.convertRowIndexToModel(selectedRow));
        clearLineEditor();
        updateOrderTotal();
    }

    private void loadSelectedCartLineIntoEditor() {
        int viewRow = orderLineTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        selectedOrderLineModelRow = orderLineTable.convertRowIndexToModel(viewRow);
        selectOrderItemById(parseLongValue(orderLineModel.getValueAt(selectedOrderLineModelRow, 0)));
        selectVariantById(parseLongValue(orderLineModel.getValueAt(selectedOrderLineModelRow, 1)));
        linePriceField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 19).isBlank() ? valueAt(orderLineModel, selectedOrderLineModelRow, 5) : valueAt(orderLineModel, selectedOrderLineModelRow, 19));
        loadPrintAddonsIntoEditor(printAddonsForModelRow(selectedOrderLineModelRow));
        lineQuantityField.setText("1");
        lineQuantityField.setEnabled(false);
        lineNotesArea.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 7));
        widthField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 8));
        lengthField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 9));
        if (addLineButton != null) {
            addLineButton.setText("Update Item");
        }
        updateAreaCalculationPreview();
    }

    @SuppressWarnings("unchecked")
    private List<PrintAddonLine> printAddonsForModelRow(int modelRow) {
        if (modelRow < 0 || orderLineModel.getColumnCount() <= 22) {
            return new ArrayList<>();
        }
        Object value = orderLineModel.getValueAt(modelRow, 22);
        if (value instanceof List<?>) {
            return new ArrayList<>((List<PrintAddonLine>) value);
        }
        return new ArrayList<>();
    }

    private void loadPrintAddonsIntoEditor(List<PrintAddonLine> addons) {
        if (printAddonModel == null) {
            return;
        }
        printAddonModel.setRowCount(0);
        for (PrintAddonLine addon : addons) {
            printAddonModel.addRow(new Object[]{
                    addon.printMaterialId(),
                    addon.materialName(),
                addon.printSizePresetId(),
                addon.printSizeName(),
                addon.pricingMode(),
                addon.printDescription(),
                addon.printLineCount(),
                formatMoney(addon.printCharge())
            });
        }
        if (printMaterialBox != null) {
            printMaterialBox.setSelectedIndex(0);
        }
        if (printSizePresetBox != null) {
            printSizePresetBox.setSelectedIndex(0);
        }
        printChargeField.setText("0.00");
        printLineCountField.setText("1");
        printDescriptionField.setText("");
    }

    private void selectOrderItemById(Long itemId) {
        if (itemId == null || orderItemBox == null) {
            return;
        }
        for (int i = 0; i < orderItemBox.getItemCount(); i++) {
            CustomItemOption option = orderItemBox.getItemAt(i);
            if (itemId.equals(option.customItemId())) {
                orderItemBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectVariantById(Long variantId) {
        if (variantBox == null) {
            return;
        }
        for (int i = 0; i < variantBox.getItemCount(); i++) {
            VariantOption option = variantBox.getItemAt(i);
            if ((variantId == null && option.variantId() == null) || (variantId != null && variantId.equals(option.variantId()))) {
                variantBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectPrintMaterialById(Long materialId) {
        if (printMaterialBox == null) {
            return;
        }
        for (int i = 0; i < printMaterialBox.getItemCount(); i++) {
            PrintMaterialOption option = printMaterialBox.getItemAt(i);
            if ((materialId == null && option.printMaterialId() == null) || (materialId != null && materialId.equals(option.printMaterialId()))) {
                printMaterialBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectPrintPresetById(Long presetId) {
        if (printSizePresetBox == null) {
            return;
        }
        for (int i = 0; i < printSizePresetBox.getItemCount(); i++) {
            PrintSizePresetOption option = printSizePresetBox.getItemAt(i);
            if ((presetId == null && option.printSizePresetId() == null) || (presetId != null && presetId.equals(option.printSizePresetId()))) {
                printSizePresetBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private Long parseLongValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    private int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String stripGeneratedAreaDetails(String pricingType, String details) {
        if (!"AREA".equals(pricingType) || details == null) {
            return details == null ? "" : details;
        }
        String[] lines = details.split("\\R", -1);
        if (lines.length >= 3 && lines[0].startsWith("Size:") && lines[1].startsWith("Area:") && lines[2].startsWith("Rate:")) {
            StringBuilder remaining = new StringBuilder();
            for (int i = 3; i < lines.length; i++) {
                if (remaining.length() > 0) {
                    remaining.append("\n");
                }
                remaining.append(lines[i]);
            }
            return stripGeneratedPrintDetails(remaining.toString().trim());
        }
        return stripGeneratedPrintDetails(details);
    }

    private String stripGeneratedPrintDetails(String details) {
        if (details == null) {
            return "";
        }
        String[] lines = details.split("\\R", -1);
        if (lines.length >= 2 && lines[0].startsWith("Base Item:") && lines[1].startsWith("Print Add On")) {
            int firstCustomLine = 1;
            while (firstCustomLine < lines.length && lines[firstCustomLine].startsWith("Print Add On")) {
                firstCustomLine++;
            }
            StringBuilder remaining = new StringBuilder();
            for (int i = firstCustomLine; i < lines.length; i++) {
                if (remaining.length() > 0) {
                    remaining.append("\n");
                }
                remaining.append(lines[i]);
            }
            return remaining.toString().trim();
        }
        int baseIndex = -1;
        int priceIndex = -1;
        for (int i = 0; i < Math.min(lines.length, 5); i++) {
            if (lines[i].startsWith("Base Item:")) {
                baseIndex = i;
            }
            if (lines[i].startsWith("Print Price:")) {
                priceIndex = i;
            }
        }
        if (lines.length >= 4 && lines[0].startsWith("Print Material:") && lines[1].startsWith("Print Size:") && baseIndex >= 0 && priceIndex >= baseIndex) {
            StringBuilder remaining = new StringBuilder();
            for (int i = priceIndex + 1; i < lines.length; i++) {
                if (remaining.length() > 0) {
                    remaining.append("\n");
                }
                remaining.append(lines[i]);
            }
            return remaining.toString().trim();
        }
        return details;
    }

    private void loadSelectedOrder() {
        int row = ordersTable.getSelectedRow();
        if (row < 0) {
            selectedOrderId = null;
            selectedOrderDetailsArea.setText("");
            return;
        }
        int modelRow = ordersTable.convertRowIndexToModel(row);
        selectedOrderId = Long.parseLong(ordersModel.getValueAt(modelRow, 0).toString());
        manageStatusBox.setSelectedItem(ordersModel.getValueAt(modelRow, 2).toString());
        selectEmployeeByName(valueAt(ordersModel, modelRow, 11));
        loadOrderDetails(selectedOrderId, selectedOrderDetailsArea);
    }

    private void loadSelectedMyOrder() {
        int row = myOrdersTable.getSelectedRow();
        if (row < 0) {
            myOrderDetailsArea.setText("");
            return;
        }
        int modelRow = myOrdersTable.convertRowIndexToModel(row);
        long orderId = Long.parseLong(myOrdersModel.getValueAt(modelRow, 0).toString());
        loadOrderDetails(orderId, myOrderDetailsArea);
    }

    private void loadOrderDetails(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT co.order_number, co.customer_name, co.customer_phone, co.status, co.due_date,
                       co.order_notes, co.total_amount, co.amount_paid, co.balance_due,
                       co.payment_method, co.payment_reference, co.payment_status, co.assigned_to_name,
                       col.custom_order_line_id, col.item_name, col.variant_name, col.unit_price,
                       col.customization_details, col.order_instructions,
                       col.width_value, col.length_value, col.dimension_unit,
                       col.area_value, col.area_unit, col.area_price
                FROM custom_orders co
                LEFT JOIN custom_order_lines col ON col.custom_order_id = co.custom_order_id
                WHERE co.custom_order_id = ?
                ORDER BY col.sort_order
                """;
        StringBuilder details = new StringBuilder();
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                int lineNumber = 1;
                while (rs.next()) {
                    if (!headerWritten) {
                        details.append(rs.getString("order_number")).append("\n");
                        details.append(rs.getString("customer_name")).append(" - ").append(rs.getString("customer_phone")).append("\n");
                        details.append("Status: ").append(rs.getString("status")).append("\n");
                        details.append("Due: ").append(rs.getDate("due_date") == null ? "" : rs.getDate("due_date")).append("\n");
                        details.append("Assigned: ").append(rs.getString("assigned_to_name") == null ? "Unassigned" : rs.getString("assigned_to_name")).append("\n");
                        details.append("Payment: ").append(formatPayment(rs.getString("payment_method"), rs.getString("payment_status"))).append("\n");
                        String paymentReference = rs.getString("payment_reference");
                        if (paymentReference != null && !paymentReference.isBlank()) {
                            details.append("Payment Reference: ").append(paymentReference).append("\n");
                        }
                        details.append("Total: ").append(formatMoney(rs.getBigDecimal("total_amount"))).append("\n");
                        details.append("Paid: ").append(formatMoney(rs.getBigDecimal("amount_paid"))).append("\n");
                        details.append("Balance Due: ").append(formatMoney(rs.getBigDecimal("balance_due"))).append("\n");
                        String notes = rs.getString("order_notes");
                        if (notes != null && !notes.isBlank()) {
                            details.append("Notes: ").append(notes).append("\n");
                        }
                        details.append("\nItems\n");
                        headerWritten = true;
                    }
                    String itemName = rs.getString("item_name");
                    if (itemName != null) {
                        long lineId = rs.getLong("custom_order_line_id");
                        String variantName = rs.getString("variant_name");
                        details.append(lineNumber++).append(". ").append(itemName)
                                .append(variantName == null || variantName.isBlank() ? "" : " / " + variantName)
                                .append(" - ").append(formatMoney(rs.getBigDecimal("unit_price"))).append("\n");
                        String areaDetails = structuredAreaDetails(rs);
                        if (!areaDetails.isBlank()) {
                            details.append(areaDetails);
                        }
                        String customizationDetails = rs.getString("customization_details");
                        if (customizationDetails != null && !customizationDetails.isBlank()) {
                            details.append(customizationDetails).append("\n");
                        }
                        String addOns = customizationDetails != null && customizationDetails.contains("Print Add On")
                                ? ""
                                : loadPrintAddonsForOrderLine(conn, lineId);
                        if (!addOns.isBlank()) {
                            details.append(addOns);
                        }
                        String lineNotes = rs.getString("order_instructions");
                        if (lineNotes != null && !lineNotes.isBlank()) {
                            details.append("Notes: ").append(lineNotes).append("\n");
                        }
                        details.append("\n");
                    }
                }
            }
            detailsArea.setText(details.toString());
            appendPaymentHistory(orderId, detailsArea);
            detailsArea.setCaretPosition(0);
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private String structuredAreaDetails(ResultSet rs) throws SQLException {
        BigDecimal width = rs.getBigDecimal("width_value");
        BigDecimal length = rs.getBigDecimal("length_value");
        BigDecimal area = rs.getBigDecimal("area_value");
        BigDecimal areaPrice = rs.getBigDecimal("area_price");
        String dimensionUnit = rs.getString("dimension_unit");
        String areaUnit = rs.getString("area_unit");
        if (width == null && length == null && area == null && areaPrice == null) {
            return "";
        }
        StringBuilder details = new StringBuilder();
        if (width != null && length != null) {
            details.append("Size: ")
                    .append(stripTrailingZeros(width))
                    .append(" x ")
                    .append(stripTrailingZeros(length))
                    .append(" ")
                    .append(displayDimensionUnit(dimensionUnit))
                    .append("\n");
        }
        if (area != null) {
            details.append("Area: ")
                    .append(stripTrailingZeros(area))
                    .append(" ")
                    .append(displayAreaUnit(areaUnit))
                    .append("\n");
        }
        if (areaPrice != null) {
            details.append("Rate: ")
                    .append(formatMoney(areaPrice))
                    .append(" / ")
                    .append(displayAreaUnit(areaUnit))
                    .append("\n");
        }
        return details.toString();
    }

    private String loadPrintAddonsForOrderLine(Connection conn, long lineId) throws SQLException {
        String sql = """
                SELECT print_material_name, print_size_name, pricing_mode, print_description, print_charge, print_line_count
                FROM custom_order_line_print_addons
                WHERE custom_order_line_id = ?
                ORDER BY sort_order
                """;
        StringBuilder addOns = new StringBuilder();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lineId);
            try (ResultSet rs = ps.executeQuery()) {
                int index = 1;
                while (rs.next()) {
                    addOns.append("Print Add On ").append(index++).append(": ")
                            .append(rs.getString("print_material_name"))
                            .append(" / ")
                            .append(rs.getString("print_size_name") == null ? "Custom" : rs.getString("print_size_name"));
                    if ("PER_LINE".equals(rs.getString("pricing_mode"))) {
                        addOns.append(" / ").append(rs.getInt("print_line_count")).append(" lines");
                    }
                    String description = rs.getString("print_description");
                    if (description != null && !description.isBlank()) {
                        addOns.append(" / ").append(description);
                    }
                    addOns.append(" - ").append(formatMoney(rs.getBigDecimal("print_charge"))).append("\n");
                }
            }
        }
        return addOns.toString();
    }

    private void appendPaymentHistory(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT payment_amount, payment_method, payment_reference, taken_by_name, created_at
                FROM custom_order_payments
                WHERE custom_order_id = ?
                ORDER BY created_at
                """;
        StringBuilder payments = new StringBuilder(detailsArea.getText());
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                while (rs.next()) {
                    if (!headerWritten) {
                        payments.append("\nPayments\n");
                        headerWritten = true;
                    }
                    payments.append(formatMoney(rs.getBigDecimal("payment_amount")))
                            .append(" - ")
                            .append(formatPayment(rs.getString("payment_method"), null));
                    String reference = rs.getString("payment_reference");
                    if (reference != null && !reference.isBlank()) {
                        payments.append(" Ref: ").append(reference);
                    }
                    String takenBy = rs.getString("taken_by_name");
                    if (takenBy != null && !takenBy.isBlank()) {
                        payments.append(" By: ").append(takenBy);
                    }
                    payments.append(" At: ").append(rs.getTimestamp("created_at")).append("\n");
                }
            }
            detailsArea.setText(payments.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState())) {
                showDatabaseSetupMessage(ex);
            }
        }
    }

    private void applySelectedOrderItemPrice() {
        if (orderItemBox == null || linePriceField == null) {
            return;
        }
        CustomItemOption item = (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null) {
            linePriceField.setText("");
            linePriceField.setEditable(true);
            setAreaLineVisible(false);
            return;
        }
        boolean fixed = "FIXED".equals(item.pricingType());
        boolean area = "AREA".equals(item.pricingType());
        VariantOption variant = variantBox == null ? null : (VariantOption) variantBox.getSelectedItem();
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        linePriceField.setEditable(!fixed && !area);
        widthField.setEnabled(area);
        lengthField.setEnabled(area);
        setAreaLineVisible(area);
        if (fixed && configuredPrice != null) {
            linePriceField.setText(formatMoney(configuredPrice));
        } else if (!area) {
            linePriceField.setText("");
        }
        updateAreaCalculationPreview();
    }

    private BigDecimal configuredLinePrice(CustomItemOption item, VariantOption variant) {
        if (item == null) {
            return null;
        }
        if (item.hasVariants() && variant != null && variant.variantId() != null) {
            return variant.fixedPrice();
        }
        if ("AREA".equals(item.pricingType())) {
            return item.areaPrice();
        }
        return item.fixedPrice();
    }

    private void setAreaLineVisible(boolean visible) {
        for (JComponent component : areaLineComponents) {
            component.setVisible(visible);
        }
        revalidate();
        repaint();
    }

    private void updateAreaCalculationPreview() {
        if (areaCalculationLabel == null || linePriceField == null) {
            return;
        }
        CustomItemOption item = orderItemBox == null ? null : (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null || !"AREA".equals(item.pricingType())) {
            areaCalculationLabel.setText(" ");
            return;
        }
        VariantOption variant = variantBox == null ? null : (VariantOption) variantBox.getSelectedItem();
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        AreaCalculation calculation = calculateAreaPrice(item, configuredPrice, false);
        if (calculation == null) {
            areaCalculationLabel.setText("Rate: " + formatMoney(configuredPrice) + " / " + displayAreaUnit(item.areaPriceUnit()));
            return;
        }
        linePriceField.setText(formatMoney(calculation.totalPrice()));
        areaCalculationLabel.setText(stripTrailingZeros(calculation.area()) + " " + displayAreaUnit(calculation.areaUnit())
                + " x " + formatMoney(calculation.areaPrice()) + " = " + formatMoney(calculation.totalPrice()));
    }

    private AreaCalculation calculateAreaPrice(CustomItemOption item, BigDecimal areaPrice, boolean showErrors) {
        if (areaPrice == null) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Area price is not configured for this selection.");
            }
            return null;
        }
        BigDecimal width = parsePositiveDimension(widthField.getText().trim(), "Width", showErrors);
        BigDecimal length = parsePositiveDimension(lengthField.getText().trim(), "Length", showErrors);
        if (width == null || length == null) {
            return null;
        }
        if (item.maxWidth() != null && width.compareTo(item.maxWidth()) > 0) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Width exceeds the max of " + stripTrailingZeros(item.maxWidth()) + " " + displayDimensionUnit(item.dimensionUnit()) + ".");
            }
            return null;
        }
        if (item.maxLength() != null && length.compareTo(item.maxLength()) > 0) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Length exceeds the max of " + stripTrailingZeros(item.maxLength()) + " " + displayDimensionUnit(item.dimensionUnit()) + ".");
            }
            return null;
        }
        BigDecimal areaInSquareMeters = toMeters(width, item.dimensionUnit()).multiply(toMeters(length, item.dimensionUnit()));
        BigDecimal area = fromSquareMeters(areaInSquareMeters, item.areaPriceUnit());
        BigDecimal total = area.multiply(areaPrice).setScale(2, java.math.RoundingMode.HALF_UP);
        return new AreaCalculation(width, length, item.dimensionUnit(), area, item.areaPriceUnit(), areaPrice, total);
    }

    private BigDecimal parsePositiveDimension(String value, String fieldName, boolean showErrors) {
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (Exception ex) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, fieldName + " must be greater than zero.");
            }
            return null;
        }
    }

    private BigDecimal toMeters(BigDecimal value, String unit) {
        return switch (unit == null ? "IN" : unit) {
            case "FT" -> value.multiply(new BigDecimal("0.3048"));
            case "YD" -> value.multiply(new BigDecimal("0.9144"));
            case "M" -> value;
            case "CM" -> value.multiply(new BigDecimal("0.01"));
            default -> value.multiply(new BigDecimal("0.0254"));
        };
    }

    private BigDecimal fromSquareMeters(BigDecimal value, String areaUnit) {
        return switch (areaUnit == null ? "SQ_FT" : areaUnit) {
            case "SQ_IN" -> value.multiply(new BigDecimal("1550.0031000062"));
            case "SQ_YD" -> value.multiply(new BigDecimal("1.1959900463"));
            case "SQ_M" -> value;
            case "SQ_CM" -> value.multiply(new BigDecimal("10000"));
            default -> value.multiply(new BigDecimal("10.7639104167"));
        };
    }

    private void applyOrderFilter() {
        if (ordersSorter == null) {
            return;
        }
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        String search = orderSearchField == null ? "" : orderSearchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
        }
        Object status = statusFilterBox == null ? "All" : statusFilterBox.getSelectedItem();
        if (status != null && !"All".equals(status.toString())) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(status.toString()) + "$", 2));
        }
        ordersSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void applyMyOrderFilter() {
        if (myOrdersSorter == null) {
            return;
        }
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        String search = myOrderSearchField == null ? "" : myOrderSearchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
        }
        Object status = myStatusFilterBox == null ? "All" : myStatusFilterBox.getSelectedItem();
        if (status != null && !"All".equals(status.toString())) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(status.toString()) + "$", 2));
        }
        myOrdersSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void clearOrderEntry() {
        if (orderLineModel != null) {
            orderLineModel.setRowCount(0);
        }
        if (customerInfoPanel != null) {
            customerInfoPanel.clear();
        }
        selectedOrderLineModelRow = -1;
        if (addLineButton != null) {
            addLineButton.setText("Add to Order");
        }
        if (dueDateField != null) {
            dueDateField.clearDate();
        }
        designPlacementField.setText("");
        lineNotesArea.setText("");
        lineQuantityField.setText("1");
        lineQuantityField.setEnabled(true);
        if (printMaterialBox != null && printMaterialBox.getItemCount() > 0) {
            printMaterialBox.setSelectedIndex(0);
        }
        if (printAddonModel != null) {
            printAddonModel.setRowCount(0);
        }
        printChargeField.setText("0.00");
        printLineCountField.setText("1");
        printLineCountField.setEnabled(false);
        printDescriptionField.setText("");
        widthField.setText("");
        lengthField.setText("");
        upfrontPaymentField.setText("0.00");
        paymentReferenceField.setText("");
        selectedPaymentMethod = null;
        if (paymentMethodGroup != null) {
            paymentMethodGroup.clearSelection();
        }
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
        applySelectedOrderItemPrice();
        updateOrderTotal();
    }

    private void updateOrderTotal() {
        orderTotalLabel.setText("Total: " + formatMoney(calculateOrderTotal()));
        updatePaymentPreview();
    }

    private void updatePaymentPreview() {
        if (balanceDueLabel == null) {
            return;
        }
        BigDecimal total = calculateOrderTotal();
        BigDecimal paid = BigDecimal.ZERO;
        try {
            String paidText = upfrontPaymentField == null ? "" : upfrontPaymentField.getText().trim();
            if (!paidText.isEmpty()) {
                paid = parseMoneyValue(paidText);
            }
        } catch (Exception ignored) {
            balanceDueLabel.setText("Balance Due: --");
            return;
        }
        balanceDueLabel.setText("Balance Due: " + formatMoney(total.subtract(paid)));
    }

    private BigDecimal calculateOrderTotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (orderLineModel == null) {
            return total;
        }
        for (int i = 0; i < orderLineModel.getRowCount(); i++) {
            total = total.add(parseMoneyValue(orderLineModel.getValueAt(i, 5).toString()));
        }
        return total;
    }

    private void selectEmployeeByName(String name) {
        if (assignEmployeeBox == null) {
            return;
        }
        for (int i = 0; i < assignEmployeeBox.getItemCount(); i++) {
            EmployeeOption option = assignEmployeeBox.getItemAt(i);
            if ((name == null || name.isBlank()) && option.userId() == null) {
                assignEmployeeBox.setSelectedIndex(i);
                return;
            }
            if (name != null && name.equals(option.name())) {
                assignEmployeeBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void runTableLoadAsync(DefaultTableModel model, RowLoader loader, Runnable afterLoad) {
        if (model == null) {
            return;
        }
        model.setRowCount(0);
        model.addRow(loadingRow(model.getColumnCount()));
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                return loader.load();
            }

            @Override
            protected void done() {
                model.setRowCount(0);
                try {
                    for (Object[] row : get()) {
                        model.addRow(row);
                    }
                    if (afterLoad != null) {
                        afterLoad.run();
                    }
                } catch (Exception ex) {
                    showDatabaseSetupMessage(new SQLException(ex));
                }
            }
        }.execute();
    }

    private Object[] loadingRow(int columnCount) {
        Object[] row = new Object[columnCount];
        if (columnCount > 1) {
            row[1] = "Loading...";
        } else if (columnCount == 1) {
            row[0] = "Loading...";
        }
        return row;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            BigDecimal amount = parseMoneyValue(value);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, fieldName + " cannot be negative.");
                return null;
            }
            return amount;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

    private BigDecimal parseMoneyValue(String value) {
        return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    }

    private BigDecimal parseNullableMoneyValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return parseMoneyValue(value.toString());
    }

    private String blankToNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String formatPayment(String method, String status) {
        if (method == null || method.isBlank()) {
            return status == null ? "" : status;
        }
        String normalizedMethod = method.substring(0, 1).toUpperCase() + method.substring(1).toLowerCase();
        if (status == null || status.isBlank()) {
            return normalizedMethod;
        }
        return normalizedMethod + " / " + status;
    }

    private String stripTrailingZeros(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String displayDimensionUnit(String unit) {
        return switch (unit == null ? "IN" : unit) {
            case "FT" -> "ft";
            case "YD" -> "yd";
            case "M" -> "m";
            case "CM" -> "cm";
            default -> "in";
        };
    }

    private String displayAreaUnit(String areaUnit) {
        return switch (areaUnit == null ? "SQ_FT" : areaUnit) {
            case "SQ_IN" -> "sq in";
            case "SQ_YD" -> "sq yd";
            case "SQ_M" -> "sq m";
            case "SQ_CM" -> "sq cm";
            default -> "sq ft";
        };
    }

    private String valueAt(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void showDatabaseSetupMessage(SQLException ex) {
        JOptionPane.showMessageDialog(
                this,
                "Custom orders are not ready yet. Run database/custom_orders_setup.sql, then reopen this screen.\n\n" + ex.getMessage(),
                "Database Setup Needed",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private record AreaCalculation(BigDecimal width, BigDecimal length, String dimensionUnit, BigDecimal area, String areaUnit, BigDecimal areaPrice, BigDecimal totalPrice) {
    }

    private record PrintAddonLine(
            Long printMaterialId,
            String materialName,
            Long printSizePresetId,
            String printSizeName,
            String pricingMode,
            String printDescription,
            int printLineCount,
            BigDecimal printCharge
    ) {
    }

    private record OrderEntryData(
            List<CustomItemOption> items,
            List<PrintMaterialOption> printMaterials,
            List<String> designPlacements
    ) {
    }

    @FunctionalInterface
    private interface RowLoader {
        List<Object[]> load() throws Exception;
    }
}
