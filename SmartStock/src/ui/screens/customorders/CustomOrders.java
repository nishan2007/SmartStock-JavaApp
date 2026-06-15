package ui.screens.customorders;

import Receipt.CustomOrderSlipPrinter;
import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import managers.PermissionManager;
import managers.SessionManager;
import managers.CompanyCustomizationManager;
import models.CashDrawerContext;
import services.CashDrawerService;
import services.CustomOrderAuditService;
import services.CustomOrderDataService;
import services.DeviceContextService;
import services.ManagerApprovalService;
import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.CustomerOption;
import services.CustomOrderDataService.LookupResult;
import services.CustomOrderDataService.OrderLineRequest;
import services.CustomOrderDataService.OrderSaveRequest;
import services.CustomOrderDataService.PrintAddonRequest;
import services.CustomOrderDataService.PrintMaterialOption;
import services.CustomOrderDataService.PrintSizePresetOption;
import services.CustomOrderDataService.VariantOption;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;
import ui.screens.CustomOrderSlipPreview;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    private JCheckBox dueDateEnabledBox;
    private DatePickerField dueDateField;
    private JTextArea guidedOrderNotesArea;
    private JTextField itemLookupField;
    private JComboBox<CustomItemOption> orderItemBox;
    private JComboBox<VariantOption> variantBox;
    private JTextField linePriceField;
    private JLabel priceRateUnitLabel;
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
    private JTextField lineDiscountPercentField;
    private JTextField lineDiscountReasonField;
    private JTextField priceOverrideReasonField;
    private JTextField depositOverrideReasonField;
    private JLabel orderTotalLabel;
    private JLabel lineCountLabel;
    private JLabel minimumDepositLabel;
    private JLabel reviewLineCountLabel;
    private JLabel reviewOrderTotalLabel;
    private JLabel reviewMinimumDepositLabel;
    private DefaultListModel<String> reviewLineModel;
    private JLabel customerSummaryLabel;
    private JLabel paymentMinimumDepositLabel;
    private JLabel depositOverrideNoticeLabel;
    private ButtonGroup paymentMethodGroup;
    private JToggleButton cashPaymentButton;
    private JToggleButton cardPaymentButton;
    private JToggleButton chequePaymentButton;
    private JToggleButton mmgPaymentButton;
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
    private JTextArea selectedOrderDetailsArea;
    private Long selectedOrderId;
    private CustomOrdersNewOrderTabPanel newOrderPanel;
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
            tabs.addTab("Lookup", buildOrderLookupPanel());
        }
        if (orderManagementMode && (canViewAssignedOrders || canManageOrders)) {
            tabs.addTab("My Orders", buildMyOrdersPanel());
        }
        if (orderManagementMode && canManageOrders) {
            tabs.addTab("All Orders", buildManageOrdersPanel());
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
            @Override public void orderLookup() { lookupOrderItem(); }
            @Override public void orderItemChanged() { loadVariantsForSelectedItem(); applySelectedOrderItemPrice(); }
            @Override public void variantChanged() { applySelectedOrderItemPrice(); }
            @Override public void printMaterialChanged() { loadPrintSizePresets(); applySelectedPrintPresetPrice(); }
            @Override public void printPresetChanged() { applySelectedPrintPresetPrice(); }
            @Override public Runnable printLineCountChanged() { return () -> SwingUtilities.invokeLater(CustomOrders.this::applySelectedPrintPresetPrice); }
            @Override public void addPrintAddon() { CustomOrders.this.addPrintAddon(); }
            @Override public void removePrintAddon() { removeSelectedPrintAddon(); }
            @Override public Runnable areaChanged() { return CustomOrders.this::updateAreaCalculationPreview; }
            @Override public void addPlacement() { addDesignPlacementNote(); }
            @Override public void addOrderLine() { CustomOrders.this.addOrderLine(); }
            @Override public void removeOrderLine() { removeSelectedOrderLine(); }
            @Override public void editLineDiscount() { showLineDiscountDialog(); }
            @Override public void cartSelectionChanged() { loadSelectedCartLineIntoEditor(); }
            @Override public void selectPaymentMethod(String method) { CustomOrders.this.selectPaymentMethod(method); }
            @Override public Runnable upfrontChanged() { return CustomOrders.this::updatePaymentPreview; }
            @Override public boolean canLeaveStep(int step) { return validateGuidedStep(step); }
            @Override public void enterStep(int step) { enterGuidedStep(step); }
            @Override public void saveOrder() { saveCustomOrder(); }
            @Override public void clearOrder() { clearOrderEntry(); }
        });
        newOrderPanel = panel;
        customerInfoPanel = panel.customerInfoPanel;
        dueDateEnabledBox = panel.dueDateEnabledBox;
        dueDateField = panel.dueDateField;
        guidedOrderNotesArea = panel.orderNotesArea;
        itemLookupField = panel.itemLookupField;
        orderItemBox = panel.orderItemBox;
        variantBox = panel.variantBox;
        linePriceField = panel.linePriceField;
        priceRateUnitLabel = panel.priceRateUnitLabel;
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
        lineDiscountPercentField = panel.lineDiscountPercentField;
        lineDiscountReasonField = panel.lineDiscountReasonField;
        priceOverrideReasonField = panel.priceOverrideReasonField;
        depositOverrideReasonField = panel.depositOverrideReasonField;
        lineDiscountPercentField.setEnabled(true);
        lineDiscountPercentField.setToolTipText("Enter a percentage discount for this order line. Approval is required if you do not have discount permission.");
        lineDiscountReasonField.setEnabled(true);
        lineDiscountReasonField.setToolTipText("Required when a line discount is used.");
        orderTotalLabel = panel.orderTotalLabel;
        lineCountLabel = panel.lineCountLabel;
        minimumDepositLabel = panel.minimumDepositLabel;
        reviewLineCountLabel = panel.reviewLineCountLabel;
        reviewOrderTotalLabel = panel.reviewOrderTotalLabel;
        reviewMinimumDepositLabel = panel.reviewMinimumDepositLabel;
        reviewLineModel = panel.reviewLineModel;
        customerSummaryLabel = panel.customerSummaryLabel;
        paymentMinimumDepositLabel = panel.paymentMinimumDepositLabel;
        depositOverrideNoticeLabel = panel.depositOverrideNoticeLabel;
        paymentMethodGroup = panel.paymentMethodGroup;
        cashPaymentButton = panel.cashPaymentButton;
        cardPaymentButton = panel.cardPaymentButton;
        chequePaymentButton = panel.chequePaymentButton;
        mmgPaymentButton = panel.mmgPaymentButton;
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
            @Override public void refreshOrders() { CustomOrders.this.loadOrders(); }
            @Override public void previewSelectedSlip() { CustomOrders.this.previewSelectedManagedOrderSlip(); }
            @Override public void printSelectedSlip() { CustomOrders.this.printSelectedManagedOrderSlip(); }
        });
        ordersModel = panel.ordersModel;
        ordersTable = panel.ordersTable;
        ordersSorter = panel.ordersSorter;
        orderSearchField = panel.orderSearchField;
        statusFilterBox = panel.statusFilterBox;
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
        } else if ("MMG".equals(method) && mmgPaymentButton != null) {
            mmgPaymentButton.setSelected(true);
        } else if ("ACCOUNT".equals(method) && accountPaymentButton != null) {
            accountPaymentButton.setSelected(true);
        }
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
    }

    private boolean validateGuidedStep(int step) {
        if (step == 0 && (orderLineModel == null || orderLineModel.getRowCount() == 0)) {
            JOptionPane.showMessageDialog(this, "Add at least one custom order line before review.");
            return false;
        }
        if (step == 1) {
            try {
                if (dueDateEnabledBox != null && dueDateEnabledBox.isSelected() && dueDateField != null) {
                    dueDateField.getSelectedDate();
                }
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Due date must use YYYY-MM-DD.");
                return false;
            }
        }
        if (step == 2) {
            String customerName = customerInfoPanel == null ? "" : customerInfoPanel.getCustomerName();
            String customerPhone = customerInfoPanel == null ? "" : customerInfoPanel.getCustomerPhone();
            if (customerName.isBlank()) {
                JOptionPane.showMessageDialog(this, "Customer name is required before payment.");
                return false;
            }
            if (customerPhone.isBlank()) {
                JOptionPane.showMessageDialog(this, "Customer phone number is required before payment.");
                return false;
            }
        }
        return true;
    }

    private void enterGuidedStep(int step) {
        if (step == 1) {
            refreshReviewStep();
        } else if (step == 3) {
            refreshPaymentStep();
        }
    }

    private void refreshReviewStep() {
        if (reviewLineModel == null) {
            return;
        }
        BigDecimal total = calculateOrderTotal();
        BigDecimal minimumDeposit = calculateMinimumDepositRequired();
        reviewLineCountLabel.setText("Lines: " + orderLineModel.getRowCount());
        reviewOrderTotalLabel.setText("Order Total: " + formatMoney(total));
        reviewMinimumDepositLabel.setText("Minimum Deposit Required: " + formatMoney(minimumDeposit));
        reviewLineModel.clear();
        for (int i = 0; i < orderLineModel.getRowCount(); i++) {
            String item = valueAt(orderLineModel, i, 2);
            String variant = valueAt(orderLineModel, i, 3);
            String totalText = valueAt(orderLineModel, i, 5);
            String discount = valueAt(orderLineModel, i, 24);
            String addons = valueAt(orderLineModel, i, 21);
            String notes = valueAt(orderLineModel, i, 7);
            String details = valueAt(orderLineModel, i, 6);
            StringBuilder line = new StringBuilder(item);
            if (!variant.isBlank()) {
                line.append(" / ").append(variant);
            }
            line.append(" - ").append(totalText);
            if (!discount.isBlank() && parseMoneyValue(discount).compareTo(BigDecimal.ZERO) > 0) {
                line.append(" (discount ").append(discount).append("%)");
            }
            if (!addons.isBlank()) {
                line.append(" | print: ").append(addons);
            }
            if (!notes.isBlank()) {
                line.append(" | notes: ").append(notes.replace("\n", " / "));
            }
            if (!details.isBlank()) {
                line.append(" | details: ").append(details.replace("\n", " / "));
            }
            reviewLineModel.addElement(line.toString());
        }
    }

    private void refreshPaymentStep() {
        if (customerSummaryLabel != null && customerInfoPanel != null) {
            customerSummaryLabel.setText("Customer: " + customerInfoPanel.getCustomerName() + " / " + customerInfoPanel.getCustomerPhone());
        }
        updatePaymentPreview();
    }

    private void updatePaymentButtonStyles() {
        stylePaymentButton(cashPaymentButton, "CASH".equals(selectedPaymentMethod));
        stylePaymentButton(cardPaymentButton, "CARD".equals(selectedPaymentMethod));
        stylePaymentButton(chequePaymentButton, "CHEQUE".equals(selectedPaymentMethod));
        stylePaymentButton(mmgPaymentButton, "MMG".equals(selectedPaymentMethod));
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
        boolean needsReference = requiresPaymentReference(selectedPaymentMethod);
        paymentReferenceField.setEnabled(needsReference);
        if (!needsReference) {
            paymentReferenceField.setText("");
        }
        paymentReferenceField.setToolTipText(needsReference ? "Enter card transaction ID, cheque number, or MMG reference." : "Reference is only used for card, cheque, or MMG payments.");
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
            @Override public void previewSelectedMyOrderSlip() { CustomOrders.this.previewSelectedMyOrderSlip(); }
            @Override public void printSelectedMyOrderSlip() { CustomOrders.this.printSelectedMyOrderSlip(); }
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
        } else if ("All Orders".equals(title) && !manageOrdersLoaded) {
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
        orderItemBox.addItem(new CustomItemOption(null, "Loading...", null, "INVENTORY", "VARIABLE", null, false, null, null, null, null, null));
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
        variantBox.addItem(new VariantOption(null, "No Variant", null, null));
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

    private void lookupOrderItem() {
        String search = itemLookupField == null ? "" : itemLookupField.getText().trim();
        if (search.isEmpty()) {
            return;
        }
        try {
            LookupResult result = CustomOrderDataService.lookupCustomItem(search);
            if (result == null || result.customItemId() == null) {
                JOptionPane.showMessageDialog(this, "No custom item or variant matched that SKU/barcode/name.");
                return;
            }
            selectOrderItemById(result.customItemId());
            if (result.customVariantId() != null) {
                selectVariantById(result.customVariantId());
            }
            applySelectedOrderItemPrice();
            itemLookupField.selectAll();
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
                WHERE (? IS NULL OR location_id = ?)
                ORDER BY created_at DESC
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bindCurrentLocation(ps, 1);
            bindCurrentLocation(ps, 2);
            try (ResultSet rs = ps.executeQuery()) {
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
                            WHERE (? IS NULL OR location_id = ?)
                            ORDER BY created_at DESC
                            """;
                    try (Connection conn = DB.getConnection();
                         PreparedStatement ps = conn.prepareStatement(sql)) {
                        bindCurrentLocation(ps, 1);
                        bindCurrentLocation(ps, 2);
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
                                        rs.getString("assigned_to_name"),
                                        rs.getString("taken_by_name"),
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
                  AND (? IS NULL OR location_id = ?)
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
            bindCurrentLocation(ps, 2);
            bindCurrentLocation(ps, 3);
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
                              AND (? IS NULL OR location_id = ?)
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
                        bindCurrentLocation(ps, 2);
                        bindCurrentLocation(ps, 3);
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
        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "Select a store before saving a custom order.");
            return;
        }
        LocalDate dueDate;
        try {
            dueDate = dueDateEnabledBox != null && dueDateEnabledBox.isSelected() && dueDateField != null ? dueDateField.getSelectedDate() : null;
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Due date must use YYYY-MM-DD.");
            return;
        }
        String orderNotes = guidedOrderNotesArea == null ? "" : guidedOrderNotesArea.getText().trim();

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
        if ("ACCOUNT".equals(selectedPaymentMethod) && upfrontPaid.compareTo(BigDecimal.ZERO) > 0) {
            JOptionPane.showMessageDialog(this, "Account charges use the unpaid balance. Leave upfront payment at 0.00, or choose Cash/Card/Cheque/MMG for an upfront payment.");
            return;
        }
        String paymentReference = paymentReferenceField.getText().trim();
        if (upfrontPaid.compareTo(BigDecimal.ZERO) > 0
                && ("CARD".equals(selectedPaymentMethod) || "CHEQUE".equals(selectedPaymentMethod) || "MMG".equals(selectedPaymentMethod))
                && paymentReference.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a payment reference for card, cheque, or MMG payments.");
            return;
        }
        BigDecimal balanceDue = total.subtract(upfrontPaid);
        DepositOverride depositOverride = resolveDepositOverride(total, upfrontPaid);
        if (depositOverride == null) {
            return;
        }
        String paymentStatus = upfrontPaid.compareTo(BigDecimal.ZERO) == 0
                ? "UNPAID"
                : balanceDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "PARTIAL";
        try (Connection conn = DB.getConnection()) {
            DeviceContextService.requireOrdersAllowed(conn);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Device Access Required", JOptionPane.WARNING_MESSAGE);
            return;
        }
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
                    SessionManager.getCurrentLocationId(),
                    SessionManager.getCurrentLocationName(),
                    DeviceContextService.currentDeviceId(),
                    DeviceContextService.currentDeviceName(),
                    depositOverride.requiredDeposit(),
                    depositOverride.overrideReason(),
                    depositOverride.overrideByUserId(),
                    depositOverride.overrideByName(),
                    orderNotes,
                    buildOrderLineRequests()
            ));
                String printMessage = printCustomOrderSlipIfEnabled(orderNumber);
                JOptionPane.showMessageDialog(this, "Custom order " + orderNumber + " saved." + printMessage);
                clearOrderEntry();
                loadOrders();
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private String printCustomOrderSlipIfEnabled(String orderNumber) {
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = CompanyCustomizationManager.loadCustomOrderSlipSettings();
        if (!slipSettings.enabled() || !slipSettings.autoPrint()) {
            return "";
        }
        try {
            CustomOrderSlipPrinter.print(orderNumber);
            return "\n\nOrder slip sent to printer.";
        } catch (Exception ex) {
            return "\n\nOrder slip could not be printed: " + ex.getMessage();
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
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 23)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 24)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 25)),
                    discountUserIdForLine(i),
                    discountUserNameForLine(i),
                    blankToNull(orderLineModel.getValueAt(i, 26)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 27)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 28)),
                    parseNullableMoneyValue(orderLineModel.getValueAt(i, 29)),
                    blankToNull(orderLineModel.getValueAt(i, 30)),
                    priceOverrideUserIdForLine(i),
                    priceOverrideUserNameForLine(i),
                    buildPrintAddonRequests(printAddonsForModelRow(i))
            ));
        }
        return lines;
    }

    private DepositOverride resolveDepositOverride(BigDecimal total, BigDecimal upfrontPaid) {
        BigDecimal requiredDeposit = calculateMinimumDepositRequired();
        if (total.compareTo(BigDecimal.ZERO) <= 0 || requiredDeposit.compareTo(BigDecimal.ZERO) <= 0
                || upfrontPaid.compareTo(requiredDeposit) >= 0) {
            return new DepositOverride(requiredDeposit, null, null, null);
        }
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_DEPOSIT_OVERRIDE")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            JOptionPane.showMessageDialog(this,
                    "This order requires at least " + formatMoney(requiredDeposit) + " upfront.",
                    "Deposit Required",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        String reason = depositOverrideReasonField == null ? "" : depositOverrideReasonField.getText().trim();
        if (reason.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Required deposit is " + formatMoney(requiredDeposit) + ". Enter a deposit override reason on the Payment step.");
            return null;
        }
        return new DepositOverride(requiredDeposit, reason, SessionManager.getCurrentUserId(), SessionManager.getCurrentUserDisplayName());
    }

    private BigDecimal calculateMinimumDepositRequired() {
        BigDecimal percent = CompanyCustomizationManager.loadCustomOrderSettings().minimumDepositPercent();
        if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return calculateOrderTotal().multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
    }

    private Integer discountUserIdForLine(int row) {
        BigDecimal discount = parseNullableMoneyValue(orderLineModel.getValueAt(row, 25));
        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return SessionManager.getCurrentUserId();
    }

    private String discountUserNameForLine(int row) {
        BigDecimal discount = parseNullableMoneyValue(orderLineModel.getValueAt(row, 25));
        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return SessionManager.getCurrentUserDisplayName();
    }

    private Integer priceOverrideUserIdForLine(int row) {
        String reason = valueAt(orderLineModel, row, 30);
        return reason.isBlank() ? null : SessionManager.getCurrentUserId();
    }

    private String priceOverrideUserNameForLine(int row) {
        String reason = valueAt(orderLineModel, row, 30);
        return reason.isBlank() ? null : SessionManager.getCurrentUserDisplayName();
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
            @Override public List<CustomOrdersLookupTabPanel.LineReturnOption> loadReturnableLines(Long orderId) { return CustomOrders.this.loadReturnableLines(orderId); }
            @Override public List<CustomOrdersLookupTabPanel.LineDeliveryOption> loadDeliverableLines(Long orderId) { return CustomOrders.this.loadDeliverableLines(orderId); }
            @Override public BigDecimal parseNullableMoneyValue(Object value) { return CustomOrders.this.parseNullableMoneyValue(value); }
            @Override public boolean applyLookupPayment(Long orderId, String amountText, String method, String reference, Component parent) { return CustomOrders.this.applyLookupPayment(orderId, amountText, method, reference, parent); }
            @Override public boolean applyLookupRefund(Long orderId, String amountText, String method, String reference, String reason, Component parent) { return CustomOrders.this.applyLookupRefund(orderId, amountText, method, reference, reason, parent); }
            @Override public boolean applyLookupLineRefund(Long orderId, List<CustomOrdersLookupTabPanel.LineReturnRequest> lines, String method, String reference, String reason, Component parent) { return CustomOrders.this.applyLookupLineRefund(orderId, lines, method, reference, reason, parent); }
            @Override public boolean markLookupOrderDelivered(Long orderId, Component parent) { return CustomOrders.this.markLookupOrderDelivered(orderId, parent); }
            @Override public boolean markLookupLinesDelivered(Long orderId, List<Long> lineIds, String notes, Component parent) { return CustomOrders.this.markLookupLinesDelivered(orderId, lineIds, notes, parent); }
            @Override public List<CustomOrdersLookupTabPanel.ProductionLineOption> loadProductionLines(Long orderId) { return CustomOrders.this.loadProductionLines(orderId); }
            @Override public boolean updateProductionLines(Long orderId, List<Long> lineIds, String productionStatus, String notes, Component parent) { return CustomOrders.this.updateProductionLines(orderId, lineIds, productionStatus, notes, parent); }
            @Override public boolean canRefundPayments() { return PermissionManager.hasPermission("CUSTOM_ORDER_REFUNDS") || PermissionManager.hasPermission("CUSTOM_ORDER_LINE_RETURNS") || PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES"); }
            @Override public boolean canDeliverOrderLines() { return PermissionManager.hasPermission("CUSTOM_ORDER_LINE_DELIVERY") || PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES"); }
            @Override public boolean canUpdateProduction() { return PermissionManager.hasPermission("CUSTOM_ORDER_PRODUCTION_STEPS") || PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES"); }
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

    private List<CustomOrdersLookupTabPanel.LineReturnOption> loadReturnableLines(Long orderId) {
        List<CustomOrdersLookupTabPanel.LineReturnOption> rows = new ArrayList<>();
        String sql = """
                SELECT custom_order_line_id,
                       item_name,
                       variant_name,
                       COALESCE(line_total, unit_price, 0) AS line_total,
                       COALESCE(returned_amount, 0) AS returned_amount,
                       GREATEST(COALESCE(line_total, unit_price, 0) - COALESCE(returned_amount, 0), 0) AS remaining_amount
                FROM custom_order_lines
                WHERE custom_order_id = ?
                  AND GREATEST(COALESCE(line_total, unit_price, 0) - COALESCE(returned_amount, 0), 0) > 0
                ORDER BY sort_order, custom_order_line_id
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CustomOrdersLookupTabPanel.LineReturnOption(
                            rs.getLong("custom_order_line_id"),
                            rs.getString("item_name"),
                            rs.getString("variant_name"),
                            defaultZero(rs.getBigDecimal("line_total")),
                            defaultZero(rs.getBigDecimal("returned_amount")),
                            defaultZero(rs.getBigDecimal("remaining_amount"))
                    ));
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        return rows;
    }

    private List<CustomOrdersLookupTabPanel.LineDeliveryOption> loadDeliverableLines(Long orderId) {
        List<CustomOrdersLookupTabPanel.LineDeliveryOption> rows = new ArrayList<>();
        String sql = """
                SELECT custom_order_line_id,
                       item_name,
                       variant_name,
                       COALESCE(delivery_status, 'PENDING') AS delivery_status,
                       COALESCE(return_status, 'NONE') AS return_status
                FROM custom_order_lines
                WHERE custom_order_id = ?
                  AND COALESCE(delivery_status, 'PENDING') <> 'DELIVERED'
                  AND COALESCE(return_status, 'NONE') <> 'FULL'
                ORDER BY sort_order, custom_order_line_id
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CustomOrdersLookupTabPanel.LineDeliveryOption(
                            rs.getLong("custom_order_line_id"),
                            rs.getString("item_name"),
                            rs.getString("variant_name"),
                            rs.getString("delivery_status"),
                            rs.getString("return_status")
                    ));
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        return rows;
    }

    private List<CustomOrdersLookupTabPanel.ProductionLineOption> loadProductionLines(Long orderId) {
        List<CustomOrdersLookupTabPanel.ProductionLineOption> rows = new ArrayList<>();
        String sql = """
                SELECT custom_order_line_id,
                       item_name,
                       variant_name,
                       COALESCE(production_status, 'NOT_STARTED') AS production_status,
                       COALESCE(delivery_status, 'PENDING') AS delivery_status
                FROM custom_order_lines
                WHERE custom_order_id = ?
                  AND COALESCE(return_status, 'NONE') <> 'FULL'
                ORDER BY sort_order, custom_order_line_id
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new CustomOrdersLookupTabPanel.ProductionLineOption(
                            rs.getLong("custom_order_line_id"),
                            rs.getString("item_name"),
                            rs.getString("variant_name"),
                            displayProductionStatus(rs.getString("production_status")),
                            rs.getString("delivery_status")
                    ));
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        return rows;
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
        if (requiresPaymentReference(method) && reference.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Enter a payment reference for card, cheque, or MMG payments.");
            return false;
        }
        String lockSql = """
                SELECT custom_order_id, order_number, customer_id,
                       COALESCE(balance_due, total_amount) AS balance_due
                FROM custom_orders
                WHERE custom_order_id = ?
                FOR UPDATE
                """;
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
                    taken_by_user_id, taken_by_name, payment_action, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, 'PAYMENT', ?, ?, ?, ?, ?)
                """;
        String accountUpdateSql = """
                UPDATE customer_accounts
                SET current_balance = GREATEST(COALESCE(current_balance, 0) - ?, 0)
                WHERE customer_id = ?
                """;
        String accountTransactionSql = """
                INSERT INTO customer_account_transactions (
                    customer_id, custom_order_id, amount, transaction_type, note, user_name, device_id, device_name,
                    payment_method, payment_reference, cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, 'PAYMENT', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement paymentPs = conn.prepareStatement(paymentSql);
                 PreparedStatement accountUpdatePs = conn.prepareStatement(accountUpdateSql);
                 PreparedStatement accountTransactionPs = conn.prepareStatement(accountTransactionSql)) {
                lockPs.setLong(1, orderId);
                BigDecimal balanceDue;
                int customerId;
                String orderNumber;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(parent, "Order was not found.");
                        conn.rollback();
                        return false;
                    }
                    balanceDue = rs.getBigDecimal("balance_due");
                    customerId = rs.getInt("customer_id");
                    orderNumber = rs.getString("order_number");
                }
                if (amount.compareTo(balanceDue) > 0) {
                    JOptionPane.showMessageDialog(parent, "Payment cannot be more than the balance due.");
                    conn.rollback();
                    return false;
                }
                CashDrawerContext cashDrawer = new CashDrawerContext(null, null);
                if ("CASH".equalsIgnoreCase(method)) {
                    try {
                        cashDrawer = CashDrawerService.requireActiveCashSession(conn);
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(parent, "This device is not assigned to an active cash drawer for the selected store.");
                        conn.rollback();
                        return false;
                    }
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
                paymentPs.setString(7, blankToNull(DeviceContextService.currentDeviceId()));
                paymentPs.setString(8, blankToNull(DeviceContextService.currentDeviceName()));
                setNullableLong(paymentPs, 9, cashDrawer.cashDrawerId());
                paymentPs.setString(10, blankToNull(cashDrawer.drawerName()));
                setNullableLong(paymentPs, 11, cashDrawer.sessionId());
                paymentPs.executeUpdate();
                accountUpdatePs.setBigDecimal(1, amount);
                accountUpdatePs.setInt(2, customerId);
                accountUpdatePs.executeUpdate();
                accountTransactionPs.setInt(1, customerId);
                accountTransactionPs.setLong(2, orderId);
                accountTransactionPs.setBigDecimal(3, amount.negate());
                accountTransactionPs.setString(4, "Custom order payment applied. order_number=" + orderNumber + ", method=" + method);
                accountTransactionPs.setString(5, SessionManager.getCurrentUserDisplayName());
                accountTransactionPs.setString(6, blankToNull(DeviceContextService.currentDeviceId()));
                accountTransactionPs.setString(7, blankToNull(DeviceContextService.currentDeviceName()));
                accountTransactionPs.setString(8, method);
                accountTransactionPs.setString(9, reference.isBlank() ? null : reference);
                setNullableLong(accountTransactionPs, 10, cashDrawer.cashDrawerId());
                accountTransactionPs.setString(11, blankToNull(cashDrawer.drawerName()));
                setNullableLong(accountTransactionPs, 12, cashDrawer.sessionId());
                accountTransactionPs.executeUpdate();
                CustomOrderAuditService.recordAudit(conn, orderId, "PAYMENT", "amount_paid", null, amount, "Payment applied by order lookup");
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

    private boolean applyLookupRefund(Long orderId, String amountText, String methodLabel, String reference, String reason, Component parent) {
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_REFUNDS") && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            JOptionPane.showMessageDialog(parent, "You do not have permission to refund custom order payments.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        BigDecimal amount = parseMoney(amountText, "Refund amount");
        if (amount == null) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(parent, "Refund amount must be greater than zero.");
            return false;
        }
        String method = methodLabel.toUpperCase().replace(" ", "_");
        if (requiresPaymentReference(method) && reference.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Enter a refund reference for card, cheque, or MMG refunds.");
            return false;
        }
        if (reason == null || reason.isBlank()) {
            JOptionPane.showMessageDialog(parent, "A refund reason is required.");
            return false;
        }
        if (!approveLargeRefund(amount, parent)) {
            return false;
        }
        boolean paymentMistakeRefund = isPaymentMistakeRefund(reason);

        String lockSql = """
                SELECT custom_order_id, order_number, customer_id,
                       COALESCE(total_amount, 0) AS total_amount,
                       COALESCE(amount_paid, 0) AS amount_paid,
                       COALESCE(balance_due, 0) AS balance_due
                FROM custom_orders
                WHERE custom_order_id = ?
                FOR UPDATE
                """;
        String paymentMistakeUpdateSql = """
                UPDATE custom_orders
                SET amount_paid = GREATEST(COALESCE(amount_paid, 0) - ?, 0),
                    balance_due = GREATEST(COALESCE(balance_due, 0) + ?, 0),
                    payment_method = ?,
                    payment_reference = ?,
                    payment_status = CASE
                        WHEN GREATEST(COALESCE(amount_paid, 0) - ?, 0) <= 0 THEN 'UNPAID'
                        WHEN GREATEST(COALESCE(balance_due, 0) + ?, 0) <= 0 THEN 'PAID'
                        ELSE 'PARTIAL'
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        String orderAdjustmentUpdateSql = """
                UPDATE custom_orders
                SET total_amount = ?,
                    amount_paid = ?,
                    balance_due = ?,
                    payment_method = ?,
                    payment_reference = ?,
                    payment_status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        String refundSql = """
                INSERT INTO custom_order_payments (
                    custom_order_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name, payment_action, void_reason, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, 'REFUND', ?, ?, ?, ?, ?, ?)
                """;
        String accountUpdateSql = """
                UPDATE customer_accounts
                SET current_balance = COALESCE(current_balance, 0) + ?
                WHERE customer_id = ?
                """;
        String accountTransactionSql = """
                INSERT INTO customer_account_transactions (
                    customer_id, custom_order_id, amount, transaction_type, note, user_name, device_id, device_name,
                    payment_method, payment_reference, cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, 'CUSTOM_ORDER_REFUND', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement paymentMistakeUpdatePs = conn.prepareStatement(paymentMistakeUpdateSql);
                 PreparedStatement orderAdjustmentUpdatePs = conn.prepareStatement(orderAdjustmentUpdateSql);
                 PreparedStatement refundPs = conn.prepareStatement(refundSql);
                 PreparedStatement accountUpdatePs = conn.prepareStatement(accountUpdateSql);
                 PreparedStatement accountTransactionPs = conn.prepareStatement(accountTransactionSql)) {
                lockPs.setLong(1, orderId);
                BigDecimal totalAmount;
                BigDecimal amountPaid;
                BigDecimal balanceDue;
                int customerId;
                String orderNumber;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(parent, "Order was not found.");
                        conn.rollback();
                        return false;
                    }
                    totalAmount = defaultZero(rs.getBigDecimal("total_amount"));
                    amountPaid = defaultZero(rs.getBigDecimal("amount_paid"));
                    balanceDue = defaultZero(rs.getBigDecimal("balance_due"));
                    customerId = rs.getInt("customer_id");
                    orderNumber = rs.getString("order_number");
                }
                if (paymentMistakeRefund && amount.compareTo(amountPaid) > 0) {
                    JOptionPane.showMessageDialog(parent, "Payment mistake refund cannot be more than the amount paid on this order.");
                    conn.rollback();
                    return false;
                }
                if (!paymentMistakeRefund && amount.compareTo(totalAmount) > 0) {
                    JOptionPane.showMessageDialog(parent, "Order adjustment refund cannot be more than the order total.");
                    conn.rollback();
                    return false;
                }

                BigDecimal balanceReduction = BigDecimal.ZERO;
                BigDecimal actualRefundAmount = amount;
                BigDecimal accountLedgerAmount = amount;
                if (paymentMistakeRefund) {
                    paymentMistakeUpdatePs.setBigDecimal(1, amount);
                    paymentMistakeUpdatePs.setBigDecimal(2, amount);
                    paymentMistakeUpdatePs.setString(3, method);
                    paymentMistakeUpdatePs.setString(4, reference.isBlank() ? null : reference);
                    paymentMistakeUpdatePs.setBigDecimal(5, amount);
                    paymentMistakeUpdatePs.setBigDecimal(6, amount);
                    paymentMistakeUpdatePs.setLong(7, orderId);
                    paymentMistakeUpdatePs.executeUpdate();
                } else {
                    balanceReduction = amount.min(balanceDue);
                    actualRefundAmount = amount.subtract(balanceReduction);
                    accountLedgerAmount = balanceReduction.negate();
                    BigDecimal newTotal = totalAmount.subtract(amount).max(BigDecimal.ZERO);
                    BigDecimal newAmountPaid = amountPaid.subtract(actualRefundAmount).max(BigDecimal.ZERO);
                    BigDecimal newBalanceDue = balanceDue.subtract(balanceReduction).max(BigDecimal.ZERO);
                    String newPaymentStatus = paymentStatusFor(newAmountPaid, newBalanceDue);
                    orderAdjustmentUpdatePs.setBigDecimal(1, newTotal);
                    orderAdjustmentUpdatePs.setBigDecimal(2, newAmountPaid);
                    orderAdjustmentUpdatePs.setBigDecimal(3, newBalanceDue);
                    orderAdjustmentUpdatePs.setString(4, method);
                    orderAdjustmentUpdatePs.setString(5, reference.isBlank() ? null : reference);
                    orderAdjustmentUpdatePs.setString(6, newPaymentStatus);
                    orderAdjustmentUpdatePs.setLong(7, orderId);
                    orderAdjustmentUpdatePs.executeUpdate();
                }

                CashDrawerContext cashDrawer = new CashDrawerContext(null, null);
                if (actualRefundAmount.compareTo(BigDecimal.ZERO) > 0 && "CASH".equalsIgnoreCase(method)) {
                    try {
                        cashDrawer = CashDrawerService.requireActiveCashSession(conn);
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(parent, "This device is not assigned to an active cash drawer for the selected store.");
                        conn.rollback();
                        return false;
                    }
                }

                if (actualRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    refundPs.setLong(1, orderId);
                    refundPs.setBigDecimal(2, actualRefundAmount);
                    refundPs.setString(3, method);
                    refundPs.setString(4, reference.isBlank() ? null : reference);
                    setNullableInteger(refundPs, 5, SessionManager.getCurrentUserId());
                    refundPs.setString(6, SessionManager.getCurrentUserDisplayName());
                    refundPs.setString(7, reason);
                    refundPs.setString(8, blankToNull(DeviceContextService.currentDeviceId()));
                    refundPs.setString(9, blankToNull(DeviceContextService.currentDeviceName()));
                    setNullableLong(refundPs, 10, cashDrawer.cashDrawerId());
                    refundPs.setString(11, blankToNull(cashDrawer.drawerName()));
                    setNullableLong(refundPs, 12, cashDrawer.sessionId());
                    refundPs.executeUpdate();
                }

                if (paymentMistakeRefund) {
                    accountUpdatePs.setBigDecimal(1, amount);
                    accountUpdatePs.setInt(2, customerId);
                    accountUpdatePs.executeUpdate();
                } else if (balanceReduction.compareTo(BigDecimal.ZERO) > 0) {
                    try (PreparedStatement accountCreditPs = conn.prepareStatement("""
                            UPDATE customer_accounts
                            SET current_balance = GREATEST(COALESCE(current_balance, 0) - ?, 0)
                            WHERE customer_id = ?
                            """)) {
                        accountCreditPs.setBigDecimal(1, balanceReduction);
                        accountCreditPs.setInt(2, customerId);
                        accountCreditPs.executeUpdate();
                    }
                }

                String note = "Custom order refund. order_number=" + orderNumber
                        + ", method=" + method
                        + ", reason=" + reason
                        + ", order_adjustment=" + amount
                        + ", balance_reduction=" + balanceReduction
                        + ", cash_refund=" + actualRefundAmount
                        + (paymentMistakeRefund ? ", balance_reopened=true" : ", order_total_adjusted=true");
                accountTransactionPs.setInt(1, customerId);
                accountTransactionPs.setLong(2, orderId);
                accountTransactionPs.setBigDecimal(3, accountLedgerAmount);
                accountTransactionPs.setString(4, note);
                accountTransactionPs.setString(5, SessionManager.getCurrentUserDisplayName());
                accountTransactionPs.setString(6, blankToNull(DeviceContextService.currentDeviceId()));
                accountTransactionPs.setString(7, blankToNull(DeviceContextService.currentDeviceName()));
                accountTransactionPs.setString(8, method);
                accountTransactionPs.setString(9, reference.isBlank() ? null : reference);
                setNullableLong(accountTransactionPs, 10, cashDrawer.cashDrawerId());
                accountTransactionPs.setString(11, blankToNull(cashDrawer.drawerName()));
                setNullableLong(accountTransactionPs, 12, cashDrawer.sessionId());
                accountTransactionPs.executeUpdate();

                CustomOrderAuditService.recordAudit(conn, orderId, "REFUND", "amount_paid", amountPaid, amountPaid.subtract(actualRefundAmount), reason);
                if (!paymentMistakeRefund) {
                    CustomOrderAuditService.recordAudit(conn, orderId, "REFUND", "total_amount", totalAmount, totalAmount.subtract(amount).max(BigDecimal.ZERO), reason);
                    if (balanceReduction.compareTo(BigDecimal.ZERO) > 0) {
                        CustomOrderAuditService.recordAudit(conn, orderId, "REFUND", "balance_due", balanceDue, balanceDue.subtract(balanceReduction).max(BigDecimal.ZERO), reason);
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Refund recorded.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean applyLookupLineRefund(Long orderId, List<CustomOrdersLookupTabPanel.LineReturnRequest> requests, String methodLabel, String reference, String reason, Component parent) {
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_LINE_RETURNS")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_REFUNDS")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            JOptionPane.showMessageDialog(parent, "You do not have permission to process custom order line returns.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (requests == null || requests.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Select at least one order line to refund.");
            return false;
        }
        String method = methodLabel.toUpperCase().replace(" ", "_");
        if (requiresPaymentReference(method) && reference.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Enter a refund reference for card, cheque, or MMG refunds.");
            return false;
        }
        if (reason == null || reason.isBlank()) {
            JOptionPane.showMessageDialog(parent, "A refund reason is required.");
            return false;
        }

        BigDecimal amount = BigDecimal.ZERO;
        for (CustomOrdersLookupTabPanel.LineReturnRequest request : requests) {
            if (request.refundAmount() == null || request.refundAmount().compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(parent, "Refund amount must be greater than zero for every selected line.");
                return false;
            }
            amount = amount.add(request.refundAmount());
        }

        boolean paymentMistakeRefund = isPaymentMistakeRefund(reason);
        if (!approveLargeRefund(amount, parent)) {
            return false;
        }
        String lockSql = """
                SELECT custom_order_id, order_number, customer_id,
                       location_id,
                       COALESCE(total_amount, 0) AS total_amount,
                       COALESCE(amount_paid, 0) AS amount_paid,
                       COALESCE(balance_due, 0) AS balance_due
                FROM custom_orders
                WHERE custom_order_id = ?
                FOR UPDATE
                """;
        String lineSql = """
                SELECT l.custom_order_line_id, l.custom_item_id, l.custom_variant_id,
                       l.item_name, l.variant_name,
                       COALESCE(l.line_total, l.unit_price, 0) AS line_total,
                       COALESCE(l.returned_amount, 0) AS returned_amount,
                       GREATEST(COALESCE(l.line_total, l.unit_price, 0) - COALESCE(l.returned_amount, 0), 0) AS remaining_amount
                FROM custom_order_lines l
                WHERE l.custom_order_id = ?
                  AND l.custom_order_line_id = ?
                FOR UPDATE
                """;
        String paymentMistakeUpdateSql = """
                UPDATE custom_orders
                SET amount_paid = GREATEST(COALESCE(amount_paid, 0) - ?, 0),
                    balance_due = GREATEST(COALESCE(balance_due, 0) + ?, 0),
                    payment_method = ?,
                    payment_reference = ?,
                    payment_status = CASE
                        WHEN GREATEST(COALESCE(amount_paid, 0) - ?, 0) <= 0 THEN 'UNPAID'
                        WHEN GREATEST(COALESCE(balance_due, 0) + ?, 0) <= 0 THEN 'PAID'
                        ELSE 'PARTIAL'
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        String orderAdjustmentUpdateSql = """
                UPDATE custom_orders
                SET total_amount = ?,
                    amount_paid = ?,
                    balance_due = ?,
                    payment_method = ?,
                    payment_reference = ?,
                    payment_status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        String refundSql = """
                INSERT INTO custom_order_payments (
                    custom_order_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name, payment_action, void_reason, device_id, device_name,
                    cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, ?, ?, ?, 'REFUND', ?, ?, ?, ?, ?, ?)
                """;
        String lineReturnSql = """
                INSERT INTO custom_order_line_returns (
                    custom_order_id, custom_order_line_id, custom_item_id, custom_variant_id,
                    item_name, variant_name, return_type, restock_action,
                    refund_amount, balance_reduction, payout_amount, reason,
                    created_by_user_id, created_by_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String lineUpdateSql = """
                UPDATE custom_order_lines
                SET returned_amount = LEAST(COALESCE(line_total, unit_price, 0), COALESCE(returned_amount, 0) + ?),
                    return_status = CASE
                        WHEN LEAST(COALESCE(line_total, unit_price, 0), COALESCE(returned_amount, 0) + ?) >= COALESCE(line_total, unit_price, 0) THEN 'FULL'
                        WHEN LEAST(COALESCE(line_total, unit_price, 0), COALESCE(returned_amount, 0) + ?) > 0 THEN 'PARTIAL'
                        ELSE 'NONE'
                    END
                WHERE custom_order_line_id = ?
                """;
        String accountTransactionSql = """
                INSERT INTO customer_account_transactions (
                    customer_id, custom_order_id, amount, transaction_type, note, user_name, device_id, device_name,
                    payment_method, payment_reference, cash_drawer_id, cash_drawer_name, cash_drawer_session_id
                )
                VALUES (?, ?, ?, 'CUSTOM_ORDER_REFUND', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            ensureCustomOrderMovementAuditColumns(conn);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement linePs = conn.prepareStatement(lineSql);
                 PreparedStatement paymentMistakeUpdatePs = conn.prepareStatement(paymentMistakeUpdateSql);
                 PreparedStatement orderAdjustmentUpdatePs = conn.prepareStatement(orderAdjustmentUpdateSql);
                 PreparedStatement refundPs = conn.prepareStatement(refundSql);
                 PreparedStatement lineReturnPs = conn.prepareStatement(lineReturnSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement lineUpdatePs = conn.prepareStatement(lineUpdateSql);
                 PreparedStatement accountTransactionPs = conn.prepareStatement(accountTransactionSql)) {
                lockPs.setLong(1, orderId);
                BigDecimal totalAmount;
                BigDecimal amountPaid;
                BigDecimal balanceDue;
                int customerId;
                Integer orderLocationId;
                String orderNumber;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(parent, "Order was not found.");
                        conn.rollback();
                        return false;
                    }
                    totalAmount = defaultZero(rs.getBigDecimal("total_amount"));
                    amountPaid = defaultZero(rs.getBigDecimal("amount_paid"));
                    balanceDue = defaultZero(rs.getBigDecimal("balance_due"));
                    customerId = rs.getInt("customer_id");
                    orderLocationId = rs.getObject("location_id", Integer.class);
                    orderNumber = rs.getString("order_number");
                }

                List<LineReturnRow> returnRows = new ArrayList<>();
                for (CustomOrdersLookupTabPanel.LineReturnRequest request : requests) {
                    linePs.setLong(1, orderId);
                    linePs.setLong(2, request.lineId());
                    try (ResultSet rs = linePs.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(parent, "One of the selected order lines was not found.");
                            conn.rollback();
                            return false;
                        }
                        BigDecimal remaining = defaultZero(rs.getBigDecimal("remaining_amount"));
                        if (request.refundAmount().compareTo(remaining) > 0) {
                            JOptionPane.showMessageDialog(parent, "Refund amount cannot be more than the remaining refundable amount for " + rs.getString("item_name") + ".");
                            conn.rollback();
                            return false;
                        }
                        returnRows.add(new LineReturnRow(
                                rs.getLong("custom_order_line_id"),
                                rs.getLong("custom_item_id"),
                                nullableLong(rs, "custom_variant_id"),
                                rs.getString("item_name"),
                                rs.getString("variant_name"),
                                defaultZero(rs.getBigDecimal("line_total")),
                                defaultZero(rs.getBigDecimal("returned_amount")),
                                request.refundAmount(),
                                request.partial(),
                                normalizeRestockAction(request.restockAction())
                        ));
                    }
                }

                if (paymentMistakeRefund && amount.compareTo(amountPaid) > 0) {
                    JOptionPane.showMessageDialog(parent, "Payment mistake refund cannot be more than the amount paid on this order.");
                    conn.rollback();
                    return false;
                }
                if (!paymentMistakeRefund && amount.compareTo(totalAmount) > 0) {
                    JOptionPane.showMessageDialog(parent, "Line return cannot be more than the order total.");
                    conn.rollback();
                    return false;
                }

                BigDecimal balanceReduction = BigDecimal.ZERO;
                BigDecimal actualRefundAmount = amount;
                if (paymentMistakeRefund) {
                    paymentMistakeUpdatePs.setBigDecimal(1, amount);
                    paymentMistakeUpdatePs.setBigDecimal(2, amount);
                    paymentMistakeUpdatePs.setString(3, method);
                    paymentMistakeUpdatePs.setString(4, reference.isBlank() ? null : reference);
                    paymentMistakeUpdatePs.setBigDecimal(5, amount);
                    paymentMistakeUpdatePs.setBigDecimal(6, amount);
                    paymentMistakeUpdatePs.setLong(7, orderId);
                    paymentMistakeUpdatePs.executeUpdate();
                } else {
                    balanceReduction = amount.min(balanceDue);
                    actualRefundAmount = amount.subtract(balanceReduction);
                    BigDecimal newTotal = totalAmount.subtract(amount).max(BigDecimal.ZERO);
                    BigDecimal newAmountPaid = amountPaid.subtract(actualRefundAmount).max(BigDecimal.ZERO);
                    BigDecimal newBalanceDue = balanceDue.subtract(balanceReduction).max(BigDecimal.ZERO);
                    String newPaymentStatus = paymentStatusFor(newAmountPaid, newBalanceDue);
                    orderAdjustmentUpdatePs.setBigDecimal(1, newTotal);
                    orderAdjustmentUpdatePs.setBigDecimal(2, newAmountPaid);
                    orderAdjustmentUpdatePs.setBigDecimal(3, newBalanceDue);
                    orderAdjustmentUpdatePs.setString(4, method);
                    orderAdjustmentUpdatePs.setString(5, reference.isBlank() ? null : reference);
                    orderAdjustmentUpdatePs.setString(6, newPaymentStatus);
                    orderAdjustmentUpdatePs.setLong(7, orderId);
                    orderAdjustmentUpdatePs.executeUpdate();
                }

                CashDrawerContext cashDrawer = new CashDrawerContext(null, null);
                if (actualRefundAmount.compareTo(BigDecimal.ZERO) > 0 && "CASH".equalsIgnoreCase(method)) {
                    try {
                        cashDrawer = CashDrawerService.requireActiveCashSession(conn);
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(parent, "This device is not assigned to an active cash drawer for the selected store.");
                        conn.rollback();
                        return false;
                    }
                }

                if (actualRefundAmount.compareTo(BigDecimal.ZERO) > 0) {
                    refundPs.setLong(1, orderId);
                    refundPs.setBigDecimal(2, actualRefundAmount);
                    refundPs.setString(3, method);
                    refundPs.setString(4, reference.isBlank() ? null : reference);
                    setNullableInteger(refundPs, 5, SessionManager.getCurrentUserId());
                    refundPs.setString(6, SessionManager.getCurrentUserDisplayName());
                    refundPs.setString(7, reason);
                    refundPs.setString(8, blankToNull(DeviceContextService.currentDeviceId()));
                    refundPs.setString(9, blankToNull(DeviceContextService.currentDeviceName()));
                    setNullableLong(refundPs, 10, cashDrawer.cashDrawerId());
                    refundPs.setString(11, blankToNull(cashDrawer.drawerName()));
                    setNullableLong(refundPs, 12, cashDrawer.sessionId());
                    refundPs.executeUpdate();
                }

                if (paymentMistakeRefund) {
                    try (PreparedStatement accountDebitPs = conn.prepareStatement("""
                            UPDATE customer_accounts
                            SET current_balance = COALESCE(current_balance, 0) + ?
                            WHERE customer_id = ?
                            """)) {
                        accountDebitPs.setBigDecimal(1, amount);
                        accountDebitPs.setInt(2, customerId);
                        accountDebitPs.executeUpdate();
                    }
                } else if (balanceReduction.compareTo(BigDecimal.ZERO) > 0) {
                    try (PreparedStatement accountCreditPs = conn.prepareStatement("""
                            UPDATE customer_accounts
                            SET current_balance = GREATEST(COALESCE(current_balance, 0) - ?, 0)
                            WHERE customer_id = ?
                            """)) {
                        accountCreditPs.setBigDecimal(1, balanceReduction);
                        accountCreditPs.setInt(2, customerId);
                        accountCreditPs.executeUpdate();
                    }
                }

                BigDecimal balanceLeft = balanceReduction;
                for (LineReturnRow row : returnRows) {
                    BigDecimal lineBalanceReduction = paymentMistakeRefund ? BigDecimal.ZERO : row.refundAmount().min(balanceLeft);
                    BigDecimal linePayout = row.refundAmount().subtract(lineBalanceReduction);
                    balanceLeft = balanceLeft.subtract(lineBalanceReduction).max(BigDecimal.ZERO);
                    lineReturnPs.setLong(1, orderId);
                    lineReturnPs.setLong(2, row.lineId());
                    lineReturnPs.setLong(3, row.itemId());
                    setNullableLong(lineReturnPs, 4, row.variantId());
                    lineReturnPs.setString(5, row.itemName());
                    lineReturnPs.setString(6, row.variantName());
                    lineReturnPs.setString(7, row.partial() || row.returnedAmount().add(row.refundAmount()).compareTo(row.lineTotal()) < 0 ? "PARTIAL" : "FULL");
                    lineReturnPs.setString(8, row.restockAction());
                    lineReturnPs.setBigDecimal(9, row.refundAmount());
                    lineReturnPs.setBigDecimal(10, lineBalanceReduction);
                    lineReturnPs.setBigDecimal(11, linePayout);
                    lineReturnPs.setString(12, reason);
                    setNullableInteger(lineReturnPs, 13, SessionManager.getCurrentUserId());
                    lineReturnPs.setString(14, SessionManager.getCurrentUserDisplayName());
                    lineReturnPs.setString(15, blankToNull(DeviceContextService.currentDeviceId()));
                    lineReturnPs.setString(16, blankToNull(DeviceContextService.currentDeviceName()));
                    lineReturnPs.executeUpdate();
                    long lineReturnId;
                    try (ResultSet returnKeys = lineReturnPs.getGeneratedKeys()) {
                        if (!returnKeys.next()) {
                            throw new SQLException("Failed to create custom order return movement reference.");
                        }
                        lineReturnId = returnKeys.getLong(1);
                    }

                    if (!paymentMistakeRefund) {
                        lineUpdatePs.setBigDecimal(1, row.refundAmount());
                        lineUpdatePs.setBigDecimal(2, row.refundAmount());
                        lineUpdatePs.setBigDecimal(3, row.refundAmount());
                        lineUpdatePs.setLong(4, row.lineId());
                        lineUpdatePs.executeUpdate();
                        restockReturnedLine(conn, row, orderId, orderLocationId, lineReturnId, orderNumber, reason);
                    }
                }

                String note = "Custom order line return. order_number=" + orderNumber
                        + ", method=" + method
                        + ", reason=" + reason
                        + ", line_adjustment=" + amount
                        + ", balance_reduction=" + balanceReduction
                        + ", cash_refund=" + actualRefundAmount
                        + (paymentMistakeRefund ? ", balance_reopened=true" : ", order_total_adjusted=true");
                accountTransactionPs.setInt(1, customerId);
                accountTransactionPs.setLong(2, orderId);
                accountTransactionPs.setBigDecimal(3, paymentMistakeRefund ? amount : amount.negate());
                accountTransactionPs.setString(4, note);
                accountTransactionPs.setString(5, SessionManager.getCurrentUserDisplayName());
                accountTransactionPs.setString(6, blankToNull(DeviceContextService.currentDeviceId()));
                accountTransactionPs.setString(7, blankToNull(DeviceContextService.currentDeviceName()));
                accountTransactionPs.setString(8, method);
                accountTransactionPs.setString(9, reference.isBlank() ? null : reference);
                setNullableLong(accountTransactionPs, 10, cashDrawer.cashDrawerId());
                accountTransactionPs.setString(11, blankToNull(cashDrawer.drawerName()));
                setNullableLong(accountTransactionPs, 12, cashDrawer.sessionId());
                accountTransactionPs.executeUpdate();

                CustomOrderAuditService.recordAudit(conn, orderId, "LINE_RETURN", "line_return_amount", null, amount, reason);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Line return recorded.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean markLookupOrderDelivered(Long orderId, Component parent) {
        String lockSql = "SELECT status FROM custom_orders WHERE custom_order_id = ? FOR UPDATE";
        String sql = """
                UPDATE custom_orders
                SET status = 'DELIVERED',
                    delivered_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                  AND COALESCE(balance_due, 0) <= 0
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                lockPs.setLong(1, orderId);
                String oldStatus = null;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (rs.next()) {
                        oldStatus = rs.getString("status");
                    }
                }
                ps.setLong(1, orderId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    JOptionPane.showMessageDialog(parent, "This order still has a balance due. Complete payment before marking it delivered.");
                    conn.rollback();
                    return false;
                }
                if (!"DELIVERED".equals(oldStatus)) {
                    CustomOrderAuditService.recordStatus(conn, orderId, oldStatus, "DELIVERED", "Delivered from order lookup");
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Order marked delivered.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean updateProductionLines(Long orderId, List<Long> lineIds, String productionStatus, String notes, Component parent) {
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_PRODUCTION_STEPS")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            JOptionPane.showMessageDialog(parent, "You do not have permission to update custom order production steps.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (lineIds == null || lineIds.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Select at least one order line to update.");
            return false;
        }
        String normalizedStatus = normalizeProductionStatus(productionStatus);
        String lineSql = """
                SELECT custom_order_line_id, custom_item_id, custom_variant_id, item_name, variant_name,
                       COALESCE(production_status, 'NOT_STARTED') AS production_status
                FROM custom_order_lines
                WHERE custom_order_id = ?
                  AND custom_order_line_id = ?
                FOR UPDATE
                """;
        String updateSql = """
                UPDATE custom_order_lines
                SET production_status = ?,
                    production_updated_at = CURRENT_TIMESTAMP,
                    production_updated_by_user_id = ?,
                    production_updated_by_name = ?
                WHERE custom_order_line_id = ?
                """;
        String historySql = """
                INSERT INTO custom_order_line_production_history (
                    custom_order_id, custom_order_line_id, custom_item_id, custom_variant_id,
                    item_name, variant_name, old_status, new_status, notes,
                    updated_by_user_id, updated_by_name, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement linePs = conn.prepareStatement(lineSql);
                 PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement historyPs = conn.prepareStatement(historySql)) {
                for (Long lineId : lineIds) {
                    linePs.setLong(1, orderId);
                    linePs.setLong(2, lineId);
                    try (ResultSet rs = linePs.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(parent, "One of the selected order lines was not found.");
                            conn.rollback();
                            return false;
                        }
                        String oldStatus = rs.getString("production_status");
                        updatePs.setString(1, normalizedStatus);
                        setNullableInteger(updatePs, 2, SessionManager.getCurrentUserId());
                        updatePs.setString(3, SessionManager.getCurrentUserDisplayName());
                        updatePs.setLong(4, lineId);
                        updatePs.executeUpdate();

                        historyPs.setLong(1, orderId);
                        historyPs.setLong(2, lineId);
                        setNullableLong(historyPs, 3, nullableLong(rs, "custom_item_id"));
                        setNullableLong(historyPs, 4, nullableLong(rs, "custom_variant_id"));
                        historyPs.setString(5, rs.getString("item_name"));
                        historyPs.setString(6, rs.getString("variant_name"));
                        historyPs.setString(7, oldStatus);
                        historyPs.setString(8, normalizedStatus);
                        historyPs.setString(9, blankToNull(notes));
                        setNullableInteger(historyPs, 10, SessionManager.getCurrentUserId());
                        historyPs.setString(11, SessionManager.getCurrentUserDisplayName());
                        historyPs.setString(12, blankToNull(DeviceContextService.currentDeviceId()));
                        historyPs.setString(13, blankToNull(DeviceContextService.currentDeviceName()));
                        historyPs.executeUpdate();
                    }
                }
                CustomOrderAuditService.recordAudit(conn, orderId, "PRODUCTION_STEP", "production_status", null, normalizedStatus, notes == null || notes.isBlank() ? "Production updated" : notes);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Production checklist updated.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean markLookupLinesDelivered(Long orderId, List<Long> lineIds, String notes, Component parent) {
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_LINE_DELIVERY")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            JOptionPane.showMessageDialog(parent, "You do not have permission to deliver individual order lines.", "Access Denied", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        if (lineIds == null || lineIds.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Select at least one order line to deliver.");
            return false;
        }
        String orderLockSql = """
                SELECT status, COALESCE(balance_due, 0) AS balance_due
                FROM custom_orders
                WHERE custom_order_id = ?
                FOR UPDATE
                """;
        String lineSql = """
                SELECT custom_order_line_id, custom_item_id, custom_variant_id, item_name, variant_name,
                       COALESCE(delivery_status, 'PENDING') AS delivery_status,
                       COALESCE(return_status, 'NONE') AS return_status
                FROM custom_order_lines
                WHERE custom_order_id = ?
                  AND custom_order_line_id = ?
                FOR UPDATE
                """;
        String lineUpdateSql = """
                UPDATE custom_order_lines
                SET delivery_status = 'DELIVERED',
                    delivered_at = CURRENT_TIMESTAMP,
                    delivered_by_user_id = ?,
                    delivered_by_name = ?
                WHERE custom_order_line_id = ?
                """;
        String deliverySql = """
                INSERT INTO custom_order_line_deliveries (
                    custom_order_id, custom_order_line_id, custom_item_id, custom_variant_id,
                    item_name, variant_name, delivered_by_user_id, delivered_by_name,
                    delivery_notes, device_id, device_name
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String remainingLinesSql = """
                SELECT COUNT(*) AS remaining_lines
                FROM custom_order_lines
                WHERE custom_order_id = ?
                  AND COALESCE(delivery_status, 'PENDING') <> 'DELIVERED'
                  AND COALESCE(return_status, 'NONE') <> 'FULL'
                """;
        String orderDeliveredSql = """
                UPDATE custom_orders
                SET status = 'DELIVERED',
                    delivered_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                  AND COALESCE(balance_due, 0) <= 0
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement orderLockPs = conn.prepareStatement(orderLockSql);
                 PreparedStatement linePs = conn.prepareStatement(lineSql);
                 PreparedStatement lineUpdatePs = conn.prepareStatement(lineUpdateSql);
                 PreparedStatement deliveryPs = conn.prepareStatement(deliverySql);
                 PreparedStatement remainingLinesPs = conn.prepareStatement(remainingLinesSql);
                 PreparedStatement orderDeliveredPs = conn.prepareStatement(orderDeliveredSql)) {
                orderLockPs.setLong(1, orderId);
                String oldStatus;
                BigDecimal balanceDue;
                try (ResultSet rs = orderLockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(parent, "Order was not found.");
                        conn.rollback();
                        return false;
                    }
                    oldStatus = rs.getString("status");
                    balanceDue = defaultZero(rs.getBigDecimal("balance_due"));
                }

                for (Long lineId : lineIds) {
                    linePs.setLong(1, orderId);
                    linePs.setLong(2, lineId);
                    try (ResultSet rs = linePs.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(parent, "One of the selected order lines was not found.");
                            conn.rollback();
                            return false;
                        }
                        if ("DELIVERED".equals(rs.getString("delivery_status"))) {
                            continue;
                        }
                        if ("FULL".equals(rs.getString("return_status"))) {
                            continue;
                        }

                        setNullableInteger(lineUpdatePs, 1, SessionManager.getCurrentUserId());
                        lineUpdatePs.setString(2, SessionManager.getCurrentUserDisplayName());
                        lineUpdatePs.setLong(3, lineId);
                        lineUpdatePs.executeUpdate();

                        deliveryPs.setLong(1, orderId);
                        deliveryPs.setLong(2, lineId);
                        setNullableLong(deliveryPs, 3, nullableLong(rs, "custom_item_id"));
                        setNullableLong(deliveryPs, 4, nullableLong(rs, "custom_variant_id"));
                        deliveryPs.setString(5, rs.getString("item_name"));
                        deliveryPs.setString(6, rs.getString("variant_name"));
                        setNullableInteger(deliveryPs, 7, SessionManager.getCurrentUserId());
                        deliveryPs.setString(8, SessionManager.getCurrentUserDisplayName());
                        deliveryPs.setString(9, blankToNull(notes));
                        deliveryPs.setString(10, blankToNull(DeviceContextService.currentDeviceId()));
                        deliveryPs.setString(11, blankToNull(DeviceContextService.currentDeviceName()));
                        deliveryPs.executeUpdate();
                    }
                }

                remainingLinesPs.setLong(1, orderId);
                int remainingLines = 0;
                try (ResultSet rs = remainingLinesPs.executeQuery()) {
                    if (rs.next()) {
                        remainingLines = rs.getInt("remaining_lines");
                    }
                }
                if (remainingLines == 0) {
                    if (balanceDue.compareTo(BigDecimal.ZERO) > 0) {
                        JOptionPane.showMessageDialog(parent, "All lines were delivered, but the order still has a balance due. Complete payment before the order becomes delivered.");
                    } else {
                        orderDeliveredPs.setLong(1, orderId);
                        if (orderDeliveredPs.executeUpdate() > 0 && !"DELIVERED".equals(oldStatus)) {
                            CustomOrderAuditService.recordStatus(conn, orderId, oldStatus, "DELIVERED", "All order lines delivered");
                        }
                    }
                }
                CustomOrderAuditService.recordAudit(conn, orderId, "LINE_DELIVERY", "delivery_status", null, "DELIVERED", notes == null || notes.isBlank() ? "Order lines delivered" : notes);
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Selected order lines marked delivered.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean isPaymentMistakeRefund(String reason) {
        if (reason == null) {
            return false;
        }
        String normalized = reason.trim().toLowerCase();
        return normalized.equals("payment mistake") || normalized.startsWith("payment mistake:");
    }

    private String normalizeProductionStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase().replace(" ", "_");
        return switch (normalized) {
            case "DESIGN_APPROVED", "PRINTED", "FINISHED", "QUALITY_CHECKED", "READY" -> normalized;
            default -> "DESIGN_APPROVED";
        };
    }

    private String displayProductionStatus(String status) {
        String normalized = status == null ? "NOT_STARTED" : status.trim().toUpperCase().replace(" ", "_");
        return switch (normalized) {
            case "DESIGN_APPROVED" -> "Design Approved";
            case "PRINTED" -> "Printed";
            case "FINISHED" -> "Finished";
            case "QUALITY_CHECKED" -> "Quality Checked";
            case "READY" -> "Ready";
            default -> "Not Started";
        };
    }

    private String paymentStatusFor(BigDecimal amountPaid, BigDecimal balanceDue) {
        BigDecimal paid = defaultZero(amountPaid);
        BigDecimal balance = defaultZero(balanceDue);
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            return "PAID";
        }
        return paid.compareTo(BigDecimal.ZERO) <= 0 ? "UNPAID" : "PARTIAL";
    }

    private boolean approveLargeRefund(BigDecimal amount, Component parent) {
        BigDecimal limit = CompanyCustomizationManager.loadCustomOrderSettings().refundApprovalLimit();
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0 || amount.compareTo(limit) <= 0) {
            return true;
        }
        if (PermissionManager.hasPermission("CUSTOM_ORDER_REFUND_APPROVAL")
                || PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            return true;
        }
        JOptionPane.showMessageDialog(parent,
                "Refunds over " + formatMoney(limit) + " require Custom Order Refund Approval permission.",
                "Refund Approval Required",
                JOptionPane.WARNING_MESSAGE);
        return false;
    }

    private boolean requiresPaymentReference(String method) {
        return "CARD".equals(method) || "CHEQUE".equals(method) || "MMG".equals(method);
    }

    private BigDecimal parseLineDiscountPercent() {
        if (lineDiscountPercentField == null) {
            return BigDecimal.ZERO;
        }
        String value = lineDiscountPercentField.getText() == null ? "" : lineDiscountPercentField.getText().trim();
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal percent = new BigDecimal(value.replace("%", "").trim());
            if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                JOptionPane.showMessageDialog(this, "Line discount percent must be between 0 and 100.");
                return null;
            }
            return percent;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Line discount percent must be a valid percentage.");
            return null;
        }
    }

    private ManagerApprovalService.ApprovalResult requireCustomOrderApproval(String permissionKey, String title, String prompt) {
        return ManagerApprovalService.requestApproval(this, permissionKey, title, prompt);
    }

    private void showLineDiscountDialog() {
        int viewRow = orderLineTable == null ? -1 : orderLineTable.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a cart line to discount.");
            return;
        }
        int modelRow = orderLineTable.convertRowIndexToModel(viewRow);
        JTextField percentField = new JTextField(valueAt(orderLineModel, modelRow, 24).isBlank() ? "0" : valueAt(orderLineModel, modelRow, 24), 8);
        JTextField reasonField = new JTextField(valueAt(orderLineModel, modelRow, 26), 24);
        JLabel previewLabel = new JLabel(" ");
        BigDecimal originalTotal = parseMoneyValue(valueAt(orderLineModel, modelRow, 23));
        Runnable updatePreview = () -> {
            try {
                BigDecimal percent = new BigDecimal(percentField.getText().replace("%", "").trim());
                if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                    previewLabel.setText("Percent must be between 0 and 100.");
                    return;
                }
                BigDecimal discountAmount = originalTotal.multiply(percent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                previewLabel.setText("Discount: " + formatMoney(discountAmount) + " | New Total: " + formatMoney(originalTotal.subtract(discountAmount).max(BigDecimal.ZERO)));
            } catch (Exception ex) {
                previewLabel.setText("Enter a valid percentage.");
            }
        };
        percentField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
        });
        updatePreview.run();

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Discount %:"), gbc);
        gbc.gridx = 1;
        panel.add(percentField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Reason:"), gbc);
        gbc.gridx = 1;
        panel.add(reasonField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(previewLabel, gbc);

        int result = JOptionPane.showConfirmDialog(this, panel, "Line Discount", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        BigDecimal percent;
        try {
            percent = new BigDecimal(percentField.getText().replace("%", "").trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Line discount percent must be a valid percentage.");
            return;
        }
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            JOptionPane.showMessageDialog(this, "Line discount percent must be between 0 and 100.");
            return;
        }
        String reason = reasonField.getText().trim();
        if (percent.compareTo(BigDecimal.ZERO) > 0
                && !PermissionManager.hasPermission("CUSTOM_ORDER_LINE_DISCOUNT")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            ManagerApprovalService.ApprovalResult approval = requireCustomOrderApproval(
                    "CUSTOM_ORDER_LINE_DISCOUNT",
                    "Custom Order Line Discount Override",
                    "Reason for custom order line discount override:"
            );
            if (approval == null) {
                return;
            }
            if (reason.isBlank() && approval.reason() != null && !approval.reason().isBlank()) {
                reason = approval.reason().trim();
            }
        }
        if (percent.compareTo(BigDecimal.ZERO) > 0 && reason.isBlank()) {
            JOptionPane.showMessageDialog(this, "Discount reason is required when a line discount is used.");
            return;
        }
        BigDecimal discountAmount = originalTotal.multiply(percent).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal newTotal = originalTotal.subtract(discountAmount).max(BigDecimal.ZERO);
        orderLineModel.setValueAt(formatMoney(newTotal), modelRow, 5);
        orderLineModel.setValueAt(percent, modelRow, 24);
        orderLineModel.setValueAt(discountAmount, modelRow, 25);
        orderLineModel.setValueAt(percent.compareTo(BigDecimal.ZERO) == 0 ? "" : reason, modelRow, 26);
        updateOrderTotal();
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
            BigDecimal areaRate = parseMoney(linePriceField.getText().trim(), "Area price/rate");
            if (areaRate == null) {
                return;
            }
            areaCalculation = calculateAreaPrice(item, areaRate, true);
            if (areaCalculation == null) {
                return;
            }
            basePrice = areaCalculation.totalPrice();
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
        BigDecimal originalLineTotal = basePrice.add(printCharge);
        BigDecimal discountPercent = parseLineDiscountPercent();
        if (discountPercent == null) {
            return;
        }
        String discountReason = lineDiscountReasonField == null ? "" : lineDiscountReasonField.getText().trim();
        if (discountPercent.compareTo(BigDecimal.ZERO) > 0
                && !PermissionManager.hasPermission("CUSTOM_ORDER_LINE_DISCOUNT")
                && !PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            ManagerApprovalService.ApprovalResult approval = requireCustomOrderApproval(
                    "CUSTOM_ORDER_LINE_DISCOUNT",
                    "Custom Order Line Discount Override",
                    "Reason for custom order line discount override:"
            );
            if (approval == null) {
                return;
            }
            if (discountReason.isBlank() && approval.reason() != null && !approval.reason().isBlank()) {
                discountReason = approval.reason().trim();
            }
        }
        if (discountPercent.compareTo(BigDecimal.ZERO) > 0 && discountReason.isBlank()) {
            JOptionPane.showMessageDialog(this, "Discount reason is required when a line discount is used.");
            return;
        }
        PriceOverrideAudit priceOverride = resolvePriceOverrideAudit(item, configuredPrice, basePrice);
        if (priceOverride == null) {
            return;
        }
        BigDecimal discountAmount = originalLineTotal.multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal lineTotal = originalLineTotal.subtract(discountAmount).max(BigDecimal.ZERO);
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
            Object[] updatedRow = buildOrderLineRow(item, variant, item.pricingType(), lineTotal, details, notes, areaCalculation, printAddons, basePrice, originalLineTotal, discountPercent, discountAmount, discountReason, priceOverride);
            for (int column = 0; column < updatedRow.length; column++) {
                orderLineModel.setValueAt(updatedRow[column], selectedOrderLineModelRow, column);
            }
            orderLineTable.clearSelection();
        } else {
            for (int i = 0; i < quantity; i++) {
                String lineDetails = quantity == 1 ? details : details + "\nCopy " + (i + 1) + " of " + quantity;
                orderLineModel.addRow(buildOrderLineRow(item, variant, item.pricingType(), lineTotal, lineDetails, notes, areaCalculation, printAddons, basePrice, originalLineTotal, discountPercent, discountAmount, discountReason, priceOverride));
            }
        }
        clearLineEditor();
        updateAreaCalculationPreview();
        updateOrderTotal();
    }

    private PriceOverrideAudit resolvePriceOverrideAudit(CustomItemOption item, BigDecimal configuredPrice, BigDecimal basePrice) {
        if (item == null || basePrice == null || "AREA".equals(item.pricingType()) || "FIXED".equals(item.pricingType())) {
            return new PriceOverrideAudit(null, null, null);
        }
        boolean manualVariablePrice = "VARIABLE".equals(item.pricingType());
        boolean changedFromConfigured = configuredPrice != null && basePrice.compareTo(configuredPrice) != 0;
        if (!manualVariablePrice && !changedFromConfigured) {
            return new PriceOverrideAudit(null, null, null);
        }
        String reason = priceOverrideReasonField == null ? "" : priceOverrideReasonField.getText().trim();
        if (reason.isBlank()) {
            JOptionPane.showMessageDialog(this, "Price reason is required for variable or manually changed pricing.");
            return null;
        }
        if (!PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES")) {
            ManagerApprovalService.ApprovalResult approval = requireCustomOrderApproval(
                    "CUSTOM_ORDER_OVERRIDES",
                    "Custom Order Price Override",
                    "Reason for custom order price override:"
            );
            if (approval == null) {
                return null;
            }
            if (approval.reason() != null && !approval.reason().isBlank()) {
                reason = approval.reason().trim();
                if (priceOverrideReasonField != null) {
                    priceOverrideReasonField.setText(reason);
                }
            }
        }
        return new PriceOverrideAudit(configuredPrice, basePrice, reason);
    }

    private Object[] buildOrderLineRow(CustomItemOption item, VariantOption variant, String pricingType, BigDecimal price, String details, String notes, AreaCalculation areaCalculation, List<PrintAddonLine> printAddons, BigDecimal basePrice, BigDecimal originalLineTotal, BigDecimal discountPercent, BigDecimal discountAmount, String discountReason, PriceOverrideAudit priceOverride) {
        PrintAddonLine firstAddon = printAddons == null || printAddons.isEmpty() ? null : printAddons.get(0);
        BigDecimal totalPrintCharge = sumPrintAddons(printAddons == null ? List.of() : printAddons);
        List<PrintAddonLine> addonCopy = printAddons == null ? new ArrayList<>() : new ArrayList<>(printAddons);
        BigDecimal minimumDepositPercent = CompanyCustomizationManager.loadCustomOrderSettings().minimumDepositPercent();
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
                addonCopy,
                originalLineTotal,
                discountPercent,
                discountAmount,
                discountReason,
                minimumDepositPercent,
                priceOverride == null ? null : priceOverride.originalBasePrice(),
                priceOverride == null ? null : priceOverride.overridePrice(),
                priceOverride == null ? "" : priceOverride.reason()
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
        if (lineDiscountPercentField != null) {
            lineDiscountPercentField.setText("0");
        }
        if (lineDiscountReasonField != null) {
            lineDiscountReasonField.setText("");
        }
        if (priceOverrideReasonField != null) {
            priceOverrideReasonField.setText("");
        }
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
        if (lineDiscountPercentField != null) {
            lineDiscountPercentField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 24).isBlank() ? "0" : valueAt(orderLineModel, selectedOrderLineModelRow, 24));
        }
        if (lineDiscountReasonField != null) {
            lineDiscountReasonField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 26));
        }
        if (priceOverrideReasonField != null) {
            priceOverrideReasonField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 30));
        }
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

    private void previewSelectedManagedOrderSlip() {
        String orderNumber = selectedOrderNumber(ordersTable, ordersModel);
        if (orderNumber == null) {
            JOptionPane.showMessageDialog(this, "Select a custom order to preview its slip.");
            return;
        }
        showSlipPreview(orderNumber);
    }

    private void printSelectedManagedOrderSlip() {
        String orderNumber = selectedOrderNumber(ordersTable, ordersModel);
        if (orderNumber == null) {
            JOptionPane.showMessageDialog(this, "Select a custom order to print its slip.");
            return;
        }
        printSlip(orderNumber);
    }

    private void previewSelectedMyOrderSlip() {
        String orderNumber = selectedOrderNumber(myOrdersTable, myOrdersModel);
        if (orderNumber == null) {
            JOptionPane.showMessageDialog(this, "Select a custom order to preview its slip.");
            return;
        }
        showSlipPreview(orderNumber);
    }

    private void printSelectedMyOrderSlip() {
        String orderNumber = selectedOrderNumber(myOrdersTable, myOrdersModel);
        if (orderNumber == null) {
            JOptionPane.showMessageDialog(this, "Select a custom order to print its slip.");
            return;
        }
        printSlip(orderNumber);
    }

    private String selectedOrderNumber(JTable table, DefaultTableModel model) {
        if (table == null || model == null || table.getSelectedRow() < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        Object value = model.getValueAt(modelRow, 1);
        String orderNumber = value == null ? "" : value.toString().trim();
        return orderNumber.isBlank() ? null : orderNumber;
    }

    private void showSlipPreview(String orderNumber) {
        try {
            new CustomOrderSlipPreview(orderNumber).setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to preview custom order slip.\n\n" + ex.getMessage(), "Slip Preview", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void printSlip(String orderNumber) {
        try {
            CustomOrderSlipPrinter.print(orderNumber);
            JOptionPane.showMessageDialog(this, "Custom order slip sent to printer.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to print custom order slip.\n\n" + ex.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadOrderDetails(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT co.order_number, co.customer_name, co.customer_phone, co.status, co.due_date,
                       co.order_notes, co.total_amount, co.amount_paid, co.balance_due,
                       co.payment_method, co.payment_reference, co.payment_status, co.assigned_to_name,
                       col.custom_order_line_id, col.item_name, col.variant_name, col.unit_price, col.line_total,
                       col.original_line_total, col.line_discount_percent, col.line_discount_amount, col.line_discount_by_name,
                       col.line_discount_reason, col.original_base_price, col.price_override_price,
                       col.price_override_reason, col.price_override_by_name,
                       COALESCE(col.production_status, 'NOT_STARTED') AS production_status,
                       col.production_updated_at, col.production_updated_by_name,
                       col.returned_amount, col.return_status, col.delivery_status, col.delivered_at, col.delivered_by_name,
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
                                .append(" - ").append(formatMoney(rs.getBigDecimal("line_total")));
                        BigDecimal discountAmount = defaultZero(rs.getBigDecimal("line_discount_amount"));
                        if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                            details.append(" [Discount ")
                                    .append(stripTrailingZeros(rs.getBigDecimal("line_discount_percent")))
                                    .append("%: -")
                                    .append(formatMoney(discountAmount))
                                    .append(" from ")
                                    .append(formatMoney(rs.getBigDecimal("original_line_total")));
                            String discountBy = rs.getString("line_discount_by_name");
                            if (discountBy != null && !discountBy.isBlank()) {
                                details.append(" by ").append(discountBy);
                            }
                            String discountReason = rs.getString("line_discount_reason");
                            if (discountReason != null && !discountReason.isBlank()) {
                                details.append(" reason: ").append(discountReason);
                            }
                            details.append("]");
                        }
                        String priceOverrideReason = rs.getString("price_override_reason");
                        if (priceOverrideReason != null && !priceOverrideReason.isBlank()) {
                            details.append(" [Price Reason: ").append(priceOverrideReason);
                            BigDecimal originalBase = rs.getBigDecimal("original_base_price");
                            BigDecimal overridePrice = rs.getBigDecimal("price_override_price");
                            if (originalBase != null || overridePrice != null) {
                                details.append(" (")
                                        .append(originalBase == null ? "manual" : formatMoney(originalBase))
                                        .append(" -> ")
                                        .append(overridePrice == null ? formatMoney(rs.getBigDecimal("unit_price")) : formatMoney(overridePrice))
                                        .append(")");
                            }
                            String overrideBy = rs.getString("price_override_by_name");
                            if (overrideBy != null && !overrideBy.isBlank()) {
                                details.append(" by ").append(overrideBy);
                            }
                            details.append("]");
                        }
                        BigDecimal returnedAmount = defaultZero(rs.getBigDecimal("returned_amount"));
                        String returnStatus = rs.getString("return_status");
                        if (returnedAmount.compareTo(BigDecimal.ZERO) > 0) {
                            details.append(" [")
                                    .append("FULL".equals(returnStatus) ? "Returned" : "Partial Return")
                                    .append(": ")
                                    .append(formatMoney(returnedAmount))
                                    .append("]");
                        }
                        if ("DELIVERED".equals(rs.getString("delivery_status"))) {
                            details.append(" [Delivered");
                            String deliveredBy = rs.getString("delivered_by_name");
                            if (deliveredBy != null && !deliveredBy.isBlank()) {
                                details.append(" by ").append(deliveredBy);
                            }
                            if (rs.getTimestamp("delivered_at") != null) {
                                details.append(" at ").append(rs.getTimestamp("delivered_at"));
                            }
                            details.append("]");
                        }
                        String productionStatus = displayProductionStatus(rs.getString("production_status"));
                        if (!"Not Started".equals(productionStatus)) {
                            details.append(" [Production: ").append(productionStatus);
                            String productionBy = rs.getString("production_updated_by_name");
                            if (productionBy != null && !productionBy.isBlank()) {
                                details.append(" by ").append(productionBy);
                            }
                            if (rs.getTimestamp("production_updated_at") != null) {
                                details.append(" at ").append(rs.getTimestamp("production_updated_at"));
                            }
                            details.append("]");
                        }
                        details.append("\n");
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
            appendLineReturnHistory(orderId, detailsArea);
            appendLineDeliveryHistory(orderId, detailsArea);
            appendProductionHistory(orderId, detailsArea);
            appendAuditHistory(orderId, detailsArea);
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
                SELECT payment_amount, payment_method, payment_reference, taken_by_name,
                       COALESCE(payment_action, 'PAYMENT') AS payment_action,
                       void_reason,
                       created_at
                FROM custom_order_payments
                WHERE custom_order_id = ?
                ORDER BY created_at
                """;
        StringBuilder payments = new StringBuilder(detailsArea.getText());
        StringBuilder paymentRows = new StringBuilder();
        StringBuilder refundRows = new StringBuilder();
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String action = rs.getString("payment_action");
                    BigDecimal amount = defaultZero(rs.getBigDecimal("payment_amount"));
                    StringBuilder row = new StringBuilder();
                    if ("REFUND".equals(action) || "REVERSAL".equals(action)) {
                        row.append(formatMoney(amount.negate()));
                    } else {
                        row.append(formatMoney(amount));
                    }
                    row.append(" - ")
                            .append(formatPayment(rs.getString("payment_method"), null));
                    String reference = rs.getString("payment_reference");
                    if (reference != null && !reference.isBlank()) {
                        row.append(" Ref: ").append(reference);
                    }
                    String takenBy = rs.getString("taken_by_name");
                    if (takenBy != null && !takenBy.isBlank()) {
                        row.append(" By: ").append(takenBy);
                    }
                    String reason = rs.getString("void_reason");
                    if (reason != null && !reason.isBlank()) {
                        row.append(" Reason: ").append(reason);
                    }
                    row.append(" At: ").append(rs.getTimestamp("created_at")).append("\n");
                    if ("REFUND".equals(action) || "REVERSAL".equals(action)) {
                        refundRows.append(row);
                    } else {
                        paymentRows.append(row);
                    }
                }
            }
            if (paymentRows.length() > 0) {
                payments.append("\nPayments\n").append(paymentRows);
            }
            if (refundRows.length() > 0) {
                payments.append("\nRefund Payments\n").append(refundRows);
            }
            detailsArea.setText(payments.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState())) {
                showDatabaseSetupMessage(ex);
            }
        }
    }

    private void appendLineReturnHistory(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT item_name, variant_name, return_type, restock_action,
                       refund_amount, balance_reduction, payout_amount, reason, created_by_name, created_at
                FROM custom_order_line_returns
                WHERE custom_order_id = ?
                ORDER BY created_at, custom_order_line_return_id
                """;
        StringBuilder returns = new StringBuilder(detailsArea.getText());
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                while (rs.next()) {
                    if (!headerWritten) {
                        returns.append("\nLine Returns\n");
                        headerWritten = true;
                    }
                    returns.append(rs.getString("item_name"));
                    String variantName = rs.getString("variant_name");
                    if (variantName != null && !variantName.isBlank()) {
                        returns.append(" / ").append(variantName);
                    }
                    returns.append(" - ")
                            .append(rs.getString("return_type"))
                            .append(" - Adjustment: ").append(formatMoney(rs.getBigDecimal("refund_amount")))
                            .append(" Balance Reduced: ").append(formatMoney(rs.getBigDecimal("balance_reduction")))
                            .append(" Payout: ").append(formatMoney(rs.getBigDecimal("payout_amount")))
                            .append(" Restock: ").append(rs.getString("restock_action"))
                            .append(" Reason: ").append(rs.getString("reason"));
                    String createdBy = rs.getString("created_by_name");
                    if (createdBy != null && !createdBy.isBlank()) {
                        returns.append(" By: ").append(createdBy);
                    }
                    returns.append(" At: ").append(rs.getTimestamp("created_at")).append("\n");
                }
            }
            detailsArea.setText(returns.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState()) && !"42703".equals(ex.getSQLState())) {
                showDatabaseSetupMessage(ex);
            }
        }
    }

    private void appendLineDeliveryHistory(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT item_name, variant_name, delivered_by_name, delivery_notes, device_name, delivered_at
                FROM custom_order_line_deliveries
                WHERE custom_order_id = ?
                ORDER BY delivered_at, custom_order_line_delivery_id
                """;
        StringBuilder deliveries = new StringBuilder(detailsArea.getText());
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                while (rs.next()) {
                    if (!headerWritten) {
                        deliveries.append("\nLine Deliveries\n");
                        headerWritten = true;
                    }
                    deliveries.append(rs.getString("item_name"));
                    String variantName = rs.getString("variant_name");
                    if (variantName != null && !variantName.isBlank()) {
                        deliveries.append(" / ").append(variantName);
                    }
                    String deliveredBy = rs.getString("delivered_by_name");
                    if (deliveredBy != null && !deliveredBy.isBlank()) {
                        deliveries.append(" By: ").append(deliveredBy);
                    }
                    String deviceName = rs.getString("device_name");
                    if (deviceName != null && !deviceName.isBlank()) {
                        deliveries.append(" Device: ").append(deviceName);
                    }
                    String notes = rs.getString("delivery_notes");
                    if (notes != null && !notes.isBlank()) {
                        deliveries.append(" Notes: ").append(notes);
                    }
                    deliveries.append(" At: ").append(rs.getTimestamp("delivered_at")).append("\n");
                }
            }
            detailsArea.setText(deliveries.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState()) && !"42703".equals(ex.getSQLState())) {
                showDatabaseSetupMessage(ex);
            }
        }
    }

    private void appendProductionHistory(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT item_name, variant_name, old_status, new_status, notes, updated_by_name, device_name, created_at
                FROM custom_order_line_production_history
                WHERE custom_order_id = ?
                ORDER BY created_at, custom_order_line_production_history_id
                """;
        StringBuilder history = new StringBuilder(detailsArea.getText());
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                while (rs.next()) {
                    if (!headerWritten) {
                        history.append("\nProduction History\n");
                        headerWritten = true;
                    }
                    history.append(rs.getString("item_name"));
                    String variantName = rs.getString("variant_name");
                    if (variantName != null && !variantName.isBlank()) {
                        history.append(" / ").append(variantName);
                    }
                    history.append(" - ")
                            .append(displayProductionStatus(rs.getString("old_status")))
                            .append(" -> ")
                            .append(displayProductionStatus(rs.getString("new_status")));
                    String updatedBy = rs.getString("updated_by_name");
                    if (updatedBy != null && !updatedBy.isBlank()) {
                        history.append(" By: ").append(updatedBy);
                    }
                    String deviceName = rs.getString("device_name");
                    if (deviceName != null && !deviceName.isBlank()) {
                        history.append(" Device: ").append(deviceName);
                    }
                    String notes = rs.getString("notes");
                    if (notes != null && !notes.isBlank()) {
                        history.append(" Notes: ").append(notes);
                    }
                    history.append(" At: ").append(rs.getTimestamp("created_at")).append("\n");
                }
            }
            detailsArea.setText(history.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState()) && !"42703".equals(ex.getSQLState())) {
                showDatabaseSetupMessage(ex);
            }
        }
    }

    private void appendAuditHistory(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT action_type, field_name, old_value, new_value, reason, user_name, device_name, created_at
                FROM custom_order_audit_log
                WHERE custom_order_id = ?
                ORDER BY created_at DESC, custom_order_audit_id DESC
                LIMIT 50
                """;
        StringBuilder audit = new StringBuilder(detailsArea.getText());
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                while (rs.next()) {
                    if (!headerWritten) {
                        audit.append("\nAudit History\n");
                        headerWritten = true;
                    }
                    audit.append(rs.getTimestamp("created_at"))
                            .append(" - ")
                            .append(rs.getString("action_type"));
                    String fieldName = rs.getString("field_name");
                    if (fieldName != null && !fieldName.isBlank()) {
                        audit.append(" ").append(fieldName);
                    }
                    String oldValue = rs.getString("old_value");
                    String newValue = rs.getString("new_value");
                    if ((oldValue != null && !oldValue.isBlank()) || (newValue != null && !newValue.isBlank())) {
                        audit.append(": ")
                                .append(oldValue == null ? "" : oldValue)
                                .append(" -> ")
                                .append(newValue == null ? "" : newValue);
                    }
                    String userName = rs.getString("user_name");
                    if (userName != null && !userName.isBlank()) {
                        audit.append(" By: ").append(userName);
                    }
                    String deviceName = rs.getString("device_name");
                    if (deviceName != null && !deviceName.isBlank()) {
                        audit.append(" Device: ").append(deviceName);
                    }
                    String reason = rs.getString("reason");
                    if (reason != null && !reason.isBlank()) {
                        audit.append(" Reason: ").append(reason);
                    }
                    audit.append("\n");
                }
            }
            detailsArea.setText(audit.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState()) && !"42703".equals(ex.getSQLState())) {
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
            updatePriceRateUnitLabel(null);
            setAreaLineVisible(false);
            return;
        }
        boolean fixed = "FIXED".equals(item.pricingType());
        boolean area = "AREA".equals(item.pricingType());
        VariantOption variant = variantBox == null ? null : (VariantOption) variantBox.getSelectedItem();
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        linePriceField.setEditable(!fixed);
        widthField.setEnabled(area);
        lengthField.setEnabled(area);
        setAreaLineVisible(area);
        if (fixed && configuredPrice != null) {
            linePriceField.setText(formatMoney(configuredPrice));
        } else if (area && configuredPrice != null && linePriceField.getText().trim().isBlank()) {
            linePriceField.setText(formatMoney(configuredPrice));
        } else if (!area) {
            linePriceField.setText("");
        }
        updatePriceRateUnitLabel(item);
        updateAreaCalculationPreview();
    }

    private void updatePriceRateUnitLabel(CustomItemOption item) {
        if (priceRateUnitLabel == null) {
            return;
        }
        if (item != null && "AREA".equals(item.pricingType())) {
            priceRateUnitLabel.setText("/ " + displayAreaUnit(item.areaPriceUnit()));
        } else {
            priceRateUnitLabel.setText(" ");
        }
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
            updatePriceRateUnitLabel(item);
            return;
        }
        updatePriceRateUnitLabel(item);
        VariantOption variant = variantBox == null ? null : (VariantOption) variantBox.getSelectedItem();
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        if (!linePriceField.getText().trim().isBlank()) {
            try {
                configuredPrice = parseMoneyValue(linePriceField.getText().trim());
            } catch (Exception ignored) {
                areaCalculationLabel.setText("Enter a valid area price/rate.");
                return;
            }
        }
        AreaCalculation calculation = calculateAreaPrice(item, configuredPrice, false);
        if (calculation == null) {
            areaCalculationLabel.setText(" ");
            return;
        }
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
        if (dueDateEnabledBox != null) {
            dueDateEnabledBox.setSelected(false);
            dueDateField.setEnabled(false);
        }
        if (guidedOrderNotesArea != null) {
            guidedOrderNotesArea.setText("");
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
        if (depositOverrideReasonField != null) {
            depositOverrideReasonField.setText("");
            depositOverrideReasonField.setEnabled(false);
        }
        selectedPaymentMethod = null;
        if (paymentMethodGroup != null) {
            paymentMethodGroup.clearSelection();
        }
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
        applySelectedOrderItemPrice();
        updateOrderTotal();
        if (newOrderPanel != null) {
            newOrderPanel.showLinesStep();
        }
    }

    private void updateOrderTotal() {
        BigDecimal total = calculateOrderTotal();
        BigDecimal minimumDeposit = calculateMinimumDepositRequired();
        if (lineCountLabel != null) {
            lineCountLabel.setText("<html><b>Lines</b><br>" + (orderLineModel == null ? 0 : orderLineModel.getRowCount()) + "</html>");
        }
        if (orderTotalLabel != null) {
            orderTotalLabel.setText("<html><b>Order Total</b><br>" + formatMoney(total) + "</html>");
        }
        if (minimumDepositLabel != null) {
            minimumDepositLabel.setText("<html><b>Minimum Deposit</b><br>" + formatMoney(minimumDeposit) + "</html>");
        }
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
        BigDecimal minimumDeposit = calculateMinimumDepositRequired();
        BigDecimal balance = total.subtract(paid);
        balanceDueLabel.setText("Balance Due: " + formatMoney(balance));
        if (paymentMinimumDepositLabel != null) {
            paymentMinimumDepositLabel.setText("Minimum Deposit Required: " + formatMoney(minimumDeposit));
        }
        if (depositOverrideNoticeLabel != null) {
            boolean belowMinimum = total.compareTo(BigDecimal.ZERO) > 0
                    && minimumDeposit.compareTo(BigDecimal.ZERO) > 0
                    && paid.compareTo(minimumDeposit) < 0;
            depositOverrideNoticeLabel.setText(belowMinimum
                    ? "Payment is below minimum deposit. Override permission and reason are required."
                    : " ");
            if (depositOverrideReasonField != null) {
                depositOverrideReasonField.setEnabled(belowMinimum);
                if (!belowMinimum) {
                    depositOverrideReasonField.setText("");
                }
            }
        }
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

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String normalizeRestockAction(String value) {
        return switch (value == null ? "" : value) {
            case "RESTOCK", "DAMAGED", "CUSTOMER_KEPT", "WASTE" -> value;
            default -> "NO_RESTOCK";
        };
    }

    private void restockReturnedLine(
            Connection conn,
            LineReturnRow row,
            long orderId,
            Integer locationId,
            long lineReturnId,
            String orderNumber,
            String reason
    ) throws SQLException {
        if (!"RESTOCK".equals(row.restockAction())) {
            return;
        }
        ensureCustomOrderMovementAuditColumns(conn);
        BigDecimal restockQty = BigDecimal.ONE;
        String itemSql = """
                UPDATE custom_order_items
                SET quantity_on_hand = COALESCE(quantity_on_hand, 0) + ?,
                    sold_quantity = GREATEST(COALESCE(sold_quantity, 0) - ?, 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                  AND COALESCE(product_type, 'INVENTORY') = 'INVENTORY'
                """;
        String variantSql = """
                UPDATE custom_order_item_variants v
                SET quantity_on_hand = COALESCE(v.quantity_on_hand, 0) + ?,
                    sold_quantity = GREATEST(COALESCE(v.sold_quantity, 0) - ?, 0),
                    updated_at = CURRENT_TIMESTAMP
                FROM custom_order_items i
                WHERE v.custom_variant_id = ?
                  AND i.custom_item_id = v.custom_item_id
                  AND COALESCE(i.product_type, 'INVENTORY') = 'INVENTORY'
                """;
        String parentSql = """
                UPDATE custom_order_items i
                SET quantity_on_hand = COALESCE((SELECT SUM(quantity_on_hand) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id AND is_active = TRUE), 0),
                    sold_quantity = COALESCE((SELECT SUM(sold_quantity) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id), 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                  AND has_variants = TRUE
                """;
        String itemMovementSql = """
                INSERT INTO custom_order_item_movements (
                    custom_item_id, location_id, change_qty, reason, note, user_name,
                    user_id, device_id, device_name, custom_order_id, custom_order_line_id,
                    custom_order_line_return_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String variantMovementSql = """
                INSERT INTO custom_order_item_movements (
                    custom_item_id, custom_variant_id, variant_name, location_id,
                    change_qty, reason, note, user_name, user_id, device_id, device_name,
                    custom_order_id, custom_order_line_id, custom_order_line_return_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String movementNote = "custom_order_id=" + orderId
                + "; custom_order_line_id=" + row.lineId()
                + "; custom_order_line_return_id=" + lineReturnId
                + "; order_number=" + orderNumber
                + "; reason=" + reason;
        if (row.variantId() != null) {
            try (PreparedStatement variantPs = conn.prepareStatement(variantSql);
                 PreparedStatement parentPs = conn.prepareStatement(parentSql);
                 PreparedStatement movementPs = conn.prepareStatement(variantMovementSql)) {
                variantPs.setBigDecimal(1, restockQty);
                variantPs.setBigDecimal(2, restockQty);
                variantPs.setLong(3, row.variantId());
                if (variantPs.executeUpdate() == 0) {
                    return;
                }
                parentPs.setLong(1, row.itemId());
                parentPs.executeUpdate();
                movementPs.setLong(1, row.itemId());
                movementPs.setLong(2, row.variantId());
                movementPs.setString(3, row.variantName());
                setNullableInteger(movementPs, 4, locationId);
                movementPs.setBigDecimal(5, restockQty);
                movementPs.setString(6, "RETURN_RESTOCK");
                movementPs.setString(7, movementNote);
                movementPs.setString(8, SessionManager.getCurrentUserDisplayName());
                setNullableInteger(movementPs, 9, SessionManager.getCurrentUserId());
                movementPs.setString(10, blankToNull(DeviceContextService.currentDeviceId()));
                movementPs.setString(11, blankToNull(DeviceContextService.currentDeviceName()));
                movementPs.setLong(12, orderId);
                movementPs.setLong(13, row.lineId());
                movementPs.setLong(14, lineReturnId);
                movementPs.executeUpdate();
            }
        } else {
            try (PreparedStatement itemPs = conn.prepareStatement(itemSql);
                 PreparedStatement movementPs = conn.prepareStatement(itemMovementSql)) {
                itemPs.setBigDecimal(1, restockQty);
                itemPs.setBigDecimal(2, restockQty);
                itemPs.setLong(3, row.itemId());
                if (itemPs.executeUpdate() == 0) {
                    return;
                }
                movementPs.setLong(1, row.itemId());
                setNullableInteger(movementPs, 2, locationId);
                movementPs.setBigDecimal(3, restockQty);
                movementPs.setString(4, "RETURN_RESTOCK");
                movementPs.setString(5, movementNote);
                movementPs.setString(6, SessionManager.getCurrentUserDisplayName());
                setNullableInteger(movementPs, 7, SessionManager.getCurrentUserId());
                movementPs.setString(8, blankToNull(DeviceContextService.currentDeviceId()));
                movementPs.setString(9, blankToNull(DeviceContextService.currentDeviceName()));
                movementPs.setLong(10, orderId);
                movementPs.setLong(11, row.lineId());
                movementPs.setLong(12, lineReturnId);
                movementPs.executeUpdate();
            }
        }
    }

    private void ensureCustomOrderMovementAuditColumns(Connection conn) throws SQLException {
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS location_id INTEGER REFERENCES locations(location_id)");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS user_id INTEGER REFERENCES users(user_id)");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS device_id TEXT");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS device_name TEXT");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS receive_id TEXT");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS receive_device_id TEXT");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS receive_sequence INTEGER");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS custom_order_id BIGINT REFERENCES custom_orders(custom_order_id)");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS custom_order_line_id BIGINT REFERENCES custom_order_lines(custom_order_line_id)");
            stmt.executeUpdate("ALTER TABLE custom_order_item_movements ADD COLUMN IF NOT EXISTS custom_order_line_return_id BIGINT REFERENCES custom_order_line_returns(custom_order_line_return_id)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_location_idx ON custom_order_item_movements(location_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_user_idx ON custom_order_item_movements(user_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_device_idx ON custom_order_item_movements(device_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_receive_idx ON custom_order_item_movements(receive_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_order_idx ON custom_order_item_movements(custom_order_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_line_idx ON custom_order_item_movements(custom_order_line_id, created_at DESC)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS custom_order_item_movements_line_return_idx ON custom_order_item_movements(custom_order_line_return_id, created_at DESC)");
        }
    }

    private void bindCurrentLocation(PreparedStatement ps, int index) throws SQLException {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, locationId);
        }
    }

    private void releaseCustomOrderReservations(Connection conn, long orderId, String reason) throws SQLException {
        String selectSql = """
                SELECT custom_order_inventory_reservation_id, custom_item_id, custom_variant_id,
                       COALESCE(reserved_qty, 0) - COALESCE(released_qty, 0) AS open_qty
                FROM custom_order_inventory_reservations
                WHERE custom_order_id = ?
                  AND status = 'RESERVED'
                  AND COALESCE(reserved_qty, 0) > COALESCE(released_qty, 0)
                FOR UPDATE
                """;
        String releaseSql = """
                UPDATE custom_order_inventory_reservations
                SET released_qty = COALESCE(released_qty, 0) + ?,
                    status = 'RELEASED',
                    released_at = CURRENT_TIMESTAMP,
                    release_reason = ?
                WHERE custom_order_inventory_reservation_id = ?
                """;
        String itemSql = """
                UPDATE custom_order_items
                SET quantity_on_hand = quantity_on_hand + ?,
                    sold_quantity = GREATEST(COALESCE(sold_quantity, 0) - ?, 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                """;
        String variantSql = """
                UPDATE custom_order_item_variants
                SET quantity_on_hand = quantity_on_hand + ?,
                    sold_quantity = GREATEST(COALESCE(sold_quantity, 0) - ?, 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_variant_id = ?
                """;
        String parentSql = """
                UPDATE custom_order_items i
                SET quantity_on_hand = COALESCE((SELECT SUM(quantity_on_hand) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id AND is_active = TRUE), 0),
                    sold_quantity = COALESCE((SELECT SUM(sold_quantity) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id), 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                  AND has_variants = TRUE
                """;
        try (PreparedStatement selectPs = conn.prepareStatement(selectSql);
             PreparedStatement releasePs = conn.prepareStatement(releaseSql);
             PreparedStatement itemPs = conn.prepareStatement(itemSql);
             PreparedStatement variantPs = conn.prepareStatement(variantSql);
             PreparedStatement parentPs = conn.prepareStatement(parentSql)) {
            selectPs.setLong(1, orderId);
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next()) {
                    long reservationId = rs.getLong("custom_order_inventory_reservation_id");
                    long itemId = rs.getLong("custom_item_id");
                    long variantId = rs.getLong("custom_variant_id");
                    boolean hasVariant = !rs.wasNull();
                    BigDecimal openQty = defaultZero(rs.getBigDecimal("open_qty"));
                    if (openQty.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    if (hasVariant) {
                        variantPs.setBigDecimal(1, openQty);
                        variantPs.setBigDecimal(2, openQty);
                        variantPs.setLong(3, variantId);
                        variantPs.executeUpdate();
                        parentPs.setLong(1, itemId);
                        parentPs.executeUpdate();
                    } else {
                        itemPs.setBigDecimal(1, openQty);
                        itemPs.setBigDecimal(2, openQty);
                        itemPs.setLong(3, itemId);
                        itemPs.executeUpdate();
                    }
                    releasePs.setBigDecimal(1, openQty);
                    releasePs.setString(2, reason);
                    releasePs.setLong(3, reservationId);
                    releasePs.executeUpdate();
                }
            }
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
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

    private record DepositOverride(
            BigDecimal requiredDeposit,
            String overrideReason,
            Integer overrideByUserId,
            String overrideByName
    ) {
    }

    private record PriceOverrideAudit(
            BigDecimal originalBasePrice,
            BigDecimal overridePrice,
            String reason
    ) {
    }

    private record LineReturnRow(
            long lineId,
            long itemId,
            Long variantId,
            String itemName,
            String variantName,
            BigDecimal lineTotal,
            BigDecimal returnedAmount,
            BigDecimal refundAmount,
            boolean partial,
            String restockAction
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
