package ui.screens;

import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import Receipt.ReceiptItem;
import Receipt.CustomOrderSlipBuilder;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipFormatter;
import Receipt.CustomOrderSlipRenderer;
import managers.CompanyCustomizationManager;
import managers.NavigationManager;
import managers.PermissionManager;
import services.OfflineWriteGuard;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;
import ui.screens.companyprefs.CompanyIdentityPanel;
import ui.screens.companyprefs.CustomOrderDepositPanel;
import ui.screens.companyprefs.CustomOrderReceiptPanel;
import ui.screens.companyprefs.SaleReceiptPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

public class CompanyCustomization extends JFrame {
    public static final String NAV_COMPANY_IDENTITY = "Company Identity";
    public static final String NAV_LOCATIONS = "Locations";
    public static final String NAV_CASH_DRAWER_MANAGER = "Cash Drawer Manager";
    private static final String NAV_SALE = "Sale";
    private static final String NAV_SALE_RECEIPT_FORMATTING = "Sale Receipt & Formatting";
    private static final String NAV_CUSTOM_ORDERS = "Custom Orders";
    private static final String NAV_CUSTOM_ORDER_DEPOSIT_REFUND = "Order Deposit & Refund Approval";
    private static final String NAV_CUSTOM_ORDER_SLIP_FORMATTING = "Receipt/Slip Formatting";

    private final JTextField companyNameField = new JTextField();
    private final JTextField headerLineField = new JTextField();
    private final JTextField footerLineField = new JTextField();
    private final JTextField receiptStartCounterField = new JTextField("1");
    private final JTextField logoPathField = new JTextField();
    private final JTextField configPathField = new JTextField();
    private final JCheckBox showLogoBox = new JCheckBox("Show logo on receipt");
    private final JCheckBox showSaleIdBox = new JCheckBox("Show sale ID");
    private final JCheckBox showDeviceBox = new JCheckBox("Show device ID");
    private final JCheckBox showCustomerBox = new JCheckBox("Show customer/account");
    private final JCheckBox showSkuBox = new JCheckBox("Show SKU");
    private final JCheckBox showItemDiscountBox = new JCheckBox("Show item discounts");
    private final JCheckBox showPaymentStatusBox = new JCheckBox("Show payment status");
    private final JCheckBox vatEnabledBox = new JCheckBox("Enable VAT");
    private final JCheckBox vatUseDepartmentRatesBox = new JCheckBox("Use department VAT rates");
    private final JTextField vatFixedRatePercentField = new JTextField("0", 8);
    private final JTextField saleDiscountLimitPercentField = new JTextField("5", 8);
    private final JTextField saleReturnApprovalLimitField = new JTextField("0.00", 8);
    private final JTextField customOrderMinimumDepositPercentField = new JTextField("0", 8);
    private final JTextField customOrderRefundApprovalLimitField = new JTextField("0.00", 8);
    private final JCheckBox slipEnabledBox = new JCheckBox("Enable custom order slips");
    private final JCheckBox slipAutoPrintBox = new JCheckBox("Print automatically after saving a custom order");
    private final JTextField slipTitleField = new JTextField("CUSTOMER'S ORDER SLIP");
    private final JTextField slipContactLineField = new JTextField();
    private final JTextField slipEmailLineField = new JTextField();
    private final JTextField slipBlankDetailLinesField = new JTextField("8", 4);
    private final JTextField slipFooterNoteField = new JTextField();
    private final JCheckBox slipShowLogoBox = new JCheckBox("Logo");
    private final JCheckBox slipShowOrderNumberBox = new JCheckBox("Order number");
    private final JCheckBox slipShowDueDateBox = new JCheckBox("Due date");
    private final JCheckBox slipShowCustomerPhoneBox = new JCheckBox("Customer phone");
    private final JCheckBox slipShowCustomerAccountBox = new JCheckBox("Customer account");
    private final JCheckBox slipShowStoreBox = new JCheckBox("Store");
    private final JCheckBox slipShowDeviceBox = new JCheckBox("Device");
    private final JCheckBox slipShowCashierBox = new JCheckBox("Cashier");
    private final JCheckBox slipShowLineItemsBox = new JCheckBox("Line items");
    private final JCheckBox slipShowPricingBox = new JCheckBox("Pricing");
    private final JCheckBox slipShowPaymentSummaryBox = new JCheckBox("Payment summary");
    private final JCheckBox slipShowPaymentReferenceBox = new JCheckBox("Payment reference");
    private final JCheckBox slipShowTakenByBox = new JCheckBox("Taken/delivered by");
    private final JCheckBox slipShowSignaturesBox = new JCheckBox("Signature lines");
    private final JLabel logoPreviewLabel = new JLabel("No Logo", SwingConstants.CENTER);
    private final ReceiptPreview.ReceiptPaperPanel sampleReceiptPaperPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final CustomOrderSlipPreviewPanel sampleSlipPanel = new CustomOrderSlipPreviewPanel();
    private final ReceiptPreview.ReceiptPaperPanel sampleSlip40ColumnPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final JPanel rightContentPanel = new JPanel(new CardLayout());
    private JTree navigationTree;
    private boolean loadingSettings = false;
    private JButton saveButton;

    public CompanyCustomization() {
        this(NAV_COMPANY_IDENTITY);
    }

    public CompanyCustomization(String initialSection) {
        setTitle("Company Preferences");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);
        setJMenuBar(AppMenuBar.create(this, "CompanyCustomization"));

        JPanel rootPanel = new JPanel(new BorderLayout(18, 18));
        rootPanel.setBorder(new EmptyBorder(24, 24, 24, 24));
        rootPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("Company Preferences");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(new Color(32, 41, 57));
        rootPanel.add(titleLabel, BorderLayout.NORTH);

        addPermittedCards();

        JSplitPane mainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildNavigationPanel(),
                rightContentPanel
        );
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder());
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setResizeWeight(0);
        mainSplitPane.setDividerLocation(290);
        rootPanel.add(mainSplitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");
        saveButton = new JButton("Save");
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        buttonPanel.add(saveButton);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(rootPanel);

        refreshButton.addActionListener(e -> loadSettings());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveButton.addActionListener(e -> saveSettings());
        wireLivePreview();
        configureActionButtons();

        loadSettings();
        routeNavigationKey(firstPermittedSection(initialSection));
        WindowHelper.configurePosWindow(this);
    }

    private JComponent buildNavigationPanel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Preferences");
        addNodeIfPermitted(root, NAV_COMPANY_IDENTITY);
        addNodeIfPermitted(root, NAV_LOCATIONS);
        addNodeIfPermitted(root, NAV_CASH_DRAWER_MANAGER);

        DefaultMutableTreeNode saleNode = new DefaultMutableTreeNode(NAV_SALE);
        addNodeIfPermitted(saleNode, NAV_SALE_RECEIPT_FORMATTING);
        if (saleNode.getChildCount() > 0 || canAccessPreferenceSection(NAV_SALE)) {
            root.add(saleNode);
        }

        DefaultMutableTreeNode customOrdersNode = new DefaultMutableTreeNode(NAV_CUSTOM_ORDERS);
        addNodeIfPermitted(customOrdersNode, NAV_CUSTOM_ORDER_DEPOSIT_REFUND);
        addNodeIfPermitted(customOrdersNode, NAV_CUSTOM_ORDER_SLIP_FORMATTING);
        if (customOrdersNode.getChildCount() > 0 || canAccessPreferenceSection(NAV_CUSTOM_ORDERS)) {
            root.add(customOrdersNode);
        }

        JTree tree = new JTree(new DefaultTreeModel(root));
        navigationTree = tree;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(24);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 14));

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBorderSelectionColor(new Color(220, 224, 230));
        renderer.setBackgroundSelectionColor(new Color(232, 240, 254));
        renderer.setTextSelectionColor(new Color(32, 41, 57));
        renderer.setTextNonSelectionColor(new Color(32, 41, 57));
        renderer.setBackgroundNonSelectionColor(Color.WHITE);
        tree.setCellRenderer(renderer);

        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }

        tree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (selectedNode == null) {
                return;
            }
            String key = String.valueOf(selectedNode.getUserObject());
            routeNavigationKey(key);
        });

        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(Color.WHITE);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(10, 10, 10, 10)
        ));
        JLabel label = new JLabel("Preferences");
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        container.add(label, BorderLayout.NORTH);
        container.add(new JScrollPane(tree), BorderLayout.CENTER);

        TreePath defaultPath = findTreePath(root, firstPermittedSection(NAV_COMPANY_IDENTITY));
        if (defaultPath != null) {
            tree.setSelectionPath(defaultPath);
        }

        return container;
    }

    private void routeNavigationKey(String key) {
        String permittedKey = firstPermittedSection(key);
        CardLayout cardLayout = (CardLayout) rightContentPanel.getLayout();
        cardLayout.show(rightContentPanel, permittedKey);
        selectNavigationPath(permittedKey);
    }

    private void addPermittedCards() {
        if (canAccessPreferenceSection(NAV_COMPANY_IDENTITY)) {
            rightContentPanel.add(buildCompanyIdentityScreen(), NAV_COMPANY_IDENTITY);
        }
        if (canAccessPreferenceSection(NAV_LOCATIONS)) {
            rightContentPanel.add(buildLocationsEmbeddedScreen(), NAV_LOCATIONS);
        }
        if (canAccessPreferenceSection(NAV_CASH_DRAWER_MANAGER)) {
            rightContentPanel.add(buildCashDrawerEmbeddedScreen(), NAV_CASH_DRAWER_MANAGER);
        }
        if (canAccessPreferenceSection(NAV_SALE)) {
            rightContentPanel.add(buildSaleReceiptPreferencesScreen(), NAV_SALE);
        }
        if (canAccessPreferenceSection(NAV_SALE_RECEIPT_FORMATTING)) {
            rightContentPanel.add(buildSaleReceiptPreferencesScreen(), NAV_SALE_RECEIPT_FORMATTING);
        }
        if (canAccessPreferenceSection(NAV_CUSTOM_ORDER_DEPOSIT_REFUND)) {
            rightContentPanel.add(buildCustomOrderDepositRefundScreen(), NAV_CUSTOM_ORDER_DEPOSIT_REFUND);
        }
        if (canAccessPreferenceSection(NAV_CUSTOM_ORDERS)) {
            rightContentPanel.add(buildCustomOrderSlipPreferencesScreen(), NAV_CUSTOM_ORDERS);
        }
        if (canAccessPreferenceSection(NAV_CUSTOM_ORDER_SLIP_FORMATTING)) {
            rightContentPanel.add(buildCustomOrderSlipPreferencesScreen(), NAV_CUSTOM_ORDER_SLIP_FORMATTING);
        }
    }

    private void configureActionButtons() {
        if (saveButton == null) {
            return;
        }
        boolean canSavePreferences = canEditCompanyPreferences();
        saveButton.setEnabled(canSavePreferences);
        saveButton.setToolTipText(canSavePreferences
                ? "Save company preference updates"
                : "Requires Company Preferences access.");
    }

    private void addNodeIfPermitted(DefaultMutableTreeNode parent, String key) {
        if (canAccessPreferenceSection(key)) {
            parent.add(new DefaultMutableTreeNode(key));
        }
    }

    private boolean canAccessPreferenceSection(String key) {
        return switch (key) {
            case NAV_LOCATIONS -> PermissionManager.hasPermission("LOCATION_MANAGEMENT") || canEditCompanyPreferences();
            case NAV_CASH_DRAWER_MANAGER -> PermissionManager.hasPermission("CASH_DRAWER_MANAGEMENT") || canEditCompanyPreferences();
            case NAV_COMPANY_IDENTITY, NAV_SALE, NAV_SALE_RECEIPT_FORMATTING, NAV_CUSTOM_ORDERS,
                 NAV_CUSTOM_ORDER_DEPOSIT_REFUND, NAV_CUSTOM_ORDER_SLIP_FORMATTING -> canEditCompanyPreferences();
            default -> false;
        };
    }

    private boolean canEditCompanyPreferences() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION");
    }

    private String firstPermittedSection(String preferredKey) {
        if (canAccessPreferenceSection(preferredKey)) {
            return preferredKey;
        }
        for (String key : new String[]{
                NAV_COMPANY_IDENTITY,
                NAV_LOCATIONS,
                NAV_CASH_DRAWER_MANAGER,
                NAV_SALE,
                NAV_SALE_RECEIPT_FORMATTING,
                NAV_CUSTOM_ORDER_DEPOSIT_REFUND,
                NAV_CUSTOM_ORDERS,
                NAV_CUSTOM_ORDER_SLIP_FORMATTING
        }) {
            if (canAccessPreferenceSection(key)) {
                return key;
            }
        }
        return NAV_COMPANY_IDENTITY;
    }

    private TreePath findTreePath(DefaultMutableTreeNode root, String key) {
        java.util.Enumeration<?> nodes = root.breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (key.equals(String.valueOf(node.getUserObject()))) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    private void selectNavigationPath(String key) {
        if (navigationTree == null) {
            return;
        }
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) navigationTree.getModel().getRoot();
        TreePath path = findTreePath(root, key);
        if (path != null && !path.equals(navigationTree.getSelectionPath())) {
            navigationTree.setSelectionPath(path);
        }
    }

    private JPanel buildCompanyIdentityScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        logoPathField.setEditable(false);
        contentPanel.add(new CompanyIdentityPanel(companyNameField, buildLogoFilePanel()), BorderLayout.NORTH);
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        contentPanel.add(filler, BorderLayout.CENTER);
        return contentPanel;
    }

    private JPanel buildSaleReceiptPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildReceiptFormattingPanel(), BorderLayout.CENTER);
        contentPanel.add(buildSamplePreviewPanel(), BorderLayout.EAST);
        return contentPanel;
    }

    private JPanel buildCustomOrderDepositRefundScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildCustomOrdersPanel(), BorderLayout.NORTH);
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        contentPanel.add(filler, BorderLayout.CENTER);
        return contentPanel;
    }

    private JPanel buildCustomOrderSlipPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildCustomOrderSlipPanel(), BorderLayout.CENTER);
        contentPanel.add(buildSlipPreviewPanel(), BorderLayout.EAST);
        return contentPanel;
    }

    private JPanel buildLocationsEmbeddedScreen() {
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(new LocationManagementPanel(), BorderLayout.CENTER);
        return container;
    }

    private JPanel buildCashDrawerEmbeddedScreen() {
        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.add(new CashDrawerManagementPanel(), BorderLayout.CENTER);
        return container;
    }

    private JPanel buildCustomOrdersPanel() {
        boolean canEditDeposit = PermissionManager.hasPermission("CUSTOM_ORDER_DEPOSIT_SETTINGS")
                || PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES");
        boolean canEditRefundLimit = PermissionManager.hasPermission("CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS")
                || PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES");
        customOrderMinimumDepositPercentField.setEnabled(canEditDeposit);
        customOrderRefundApprovalLimitField.setEnabled(canEditRefundLimit);
        customOrderMinimumDepositPercentField.setToolTipText(canEditDeposit
                ? "Default percentage required upfront for custom orders."
                : "Requires Custom Order Deposit Settings permission.");
        customOrderRefundApprovalLimitField.setToolTipText(canEditRefundLimit
                ? "Refunds above this amount require approval permission. Use 0.00 to disable."
                : "Requires Custom Order Refund Approval Settings permission.");

        return new CustomOrderDepositPanel(customOrderMinimumDepositPercentField, customOrderRefundApprovalLimitField);
    }

    private JPanel buildReceiptFormattingPanel() {
        configPathField.setEditable(false);
        configPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        configPathField.setText(CompanyCustomizationManager.getConfigPath().toString());
        boolean canEditDiscountLimit = PermissionManager.hasPermission("SALE_DISCOUNT_LIMIT_SETTINGS")
                || canEditCompanyPreferences();
        boolean canEditReturnApprovalLimit = PermissionManager.hasPermission("SALE_RETURN_APPROVAL_SETTINGS")
                || canEditCompanyPreferences();
        saleDiscountLimitPercentField.setEnabled(canEditDiscountLimit);
        saleReturnApprovalLimitField.setEnabled(canEditReturnApprovalLimit);
        saleDiscountLimitPercentField.setToolTipText(canEditDiscountLimit
                ? "Default discount limit without manager override."
                : "Requires Sale Discount Limit Settings permission.");
        saleReturnApprovalLimitField.setToolTipText(canEditReturnApprovalLimit
                ? "Returns above this amount require override permission. Use 0.00 to disable."
                : "Requires Sale Return Approval Settings permission.");

        return new SaleReceiptPanel(
                headerLineField,
                footerLineField,
                receiptStartCounterField,
                configPathField,
                saleDiscountLimitPercentField,
                saleReturnApprovalLimitField,
                showLogoBox,
                showSaleIdBox,
                showDeviceBox,
                showCustomerBox,
                showSkuBox,
                showItemDiscountBox,
                showPaymentStatusBox,
                vatEnabledBox,
                vatUseDepartmentRatesBox,
                vatFixedRatePercentField
        );
    }

    private JPanel buildCustomOrderSlipPanel() {
        return new CustomOrderReceiptPanel(
                slipEnabledBox,
                slipAutoPrintBox,
                slipTitleField,
                slipContactLineField,
                slipEmailLineField,
                slipFooterNoteField,
                slipBlankDetailLinesField,
                slipShowLogoBox,
                slipShowOrderNumberBox,
                slipShowDueDateBox,
                slipShowCustomerPhoneBox,
                slipShowCustomerAccountBox,
                slipShowStoreBox,
                slipShowDeviceBox,
                slipShowCashierBox,
                slipShowLineItemsBox,
                slipShowPricingBox,
                slipShowPaymentSummaryBox,
                slipShowPaymentReferenceBox,
                slipShowTakenByBox,
                slipShowSignaturesBox
        );
    }

    private JPanel buildLogoFilePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);

        logoPathField.setMinimumSize(new Dimension(260, 28));
        panel.add(logoPathField, BorderLayout.CENTER);

        JPanel logoToolsPanel = new JPanel(new BorderLayout(8, 0));
        logoToolsPanel.setOpaque(false);
        logoPreviewLabel.setOpaque(true);
        logoPreviewLabel.setBackground(new Color(248, 250, 252));
        logoPreviewLabel.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        logoPreviewLabel.setPreferredSize(new Dimension(150, 70));
        logoToolsPanel.add(logoPreviewLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        buttonPanel.setOpaque(false);
        JButton uploadButton = new JButton("Upload Logo");
        JButton clearButton = new JButton("Clear Logo");
        buttonPanel.add(uploadButton);
        buttonPanel.add(clearButton);
        logoToolsPanel.add(buttonPanel, BorderLayout.EAST);
        panel.add(logoToolsPanel, BorderLayout.EAST);

        uploadButton.addActionListener(e -> uploadLogo());
        clearButton.addActionListener(e -> clearLogo());
        return panel;
    }

    private JPanel buildSamplePreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(430, 0));
        panel.setMinimumSize(new Dimension(380, 0));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sectionLabel = new JLabel("Sample Receipt Preview");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(sectionLabel, BorderLayout.NORTH);

        JScrollPane sampleScrollPane = new JScrollPane(sampleReceiptPaperPanel);
        sampleScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(sampleScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSlipPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(430, 0));
        panel.setMinimumSize(new Dimension(380, 0));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sectionLabel = new JLabel("Sample Order Slip Preview");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(sectionLabel, BorderLayout.NORTH);

        JTabbedPane previewTabs = new JTabbedPane();
        JScrollPane letterScrollPane = new JScrollPane(sampleSlipPanel);
        letterScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        previewTabs.addTab("Letter", letterScrollPane);
        previewTabs.addTab("40 Column", buildSlip40ColumnPreviewPanel());
        panel.add(previewTabs, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildSlip40ColumnPreviewPanel() {
        JScrollPane scrollPane = new JScrollPane(sampleSlip40ColumnPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private void loadSettings() {
        loadingSettings = true;
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        companyNameField.setText(settings.companyName());
        headerLineField.setText(settings.headerLine());
        footerLineField.setText(settings.footerLine());
        logoPathField.setText(settings.logoPath());
        showLogoBox.setSelected(settings.showLogo());
        showSaleIdBox.setSelected(settings.showSaleId());
        showDeviceBox.setSelected(settings.showDevice());
        showCustomerBox.setSelected(settings.showCustomer());
        showSkuBox.setSelected(settings.showSku());
        showItemDiscountBox.setSelected(settings.showItemDiscount());
        showPaymentStatusBox.setSelected(settings.showPaymentStatus());
        vatEnabledBox.setSelected(settings.vatEnabled());
        vatUseDepartmentRatesBox.setSelected(settings.vatUseDepartmentRates());
        vatFixedRatePercentField.setText(settings.vatFixedRatePercent().stripTrailingZeros().toPlainString());
        receiptStartCounterField.setText(String.valueOf(settings.nextReceiptCounter()));
        CompanyCustomizationManager.SaleSafetySettings saleSafetySettings = CompanyCustomizationManager.loadSaleSafetySettings();
        saleDiscountLimitPercentField.setText(saleSafetySettings.discountLimitPercent().stripTrailingZeros().toPlainString());
        saleReturnApprovalLimitField.setText(saleSafetySettings.returnApprovalLimit().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        CompanyCustomizationManager.CustomOrderSettings customOrderSettings = CompanyCustomizationManager.loadCustomOrderSettings();
        customOrderMinimumDepositPercentField.setText(customOrderSettings.minimumDepositPercent().stripTrailingZeros().toPlainString());
        customOrderRefundApprovalLimitField.setText(customOrderSettings.refundApprovalLimit().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = CompanyCustomizationManager.loadCustomOrderSlipSettings();
        loadSlipFields(slipSettings);
        updateLogoPreview(settings.logoPath());
        loadingSettings = false;
        refreshSamplePreview();
        refreshSlipPreview();
    }

    private void saveSettings() {
        try {
            OfflineWriteGuard.requireCloudForGlobalWrite("Company preference");
            CompanyCustomizationManager.clearPreviewOverrideSettings();
            CompanyCustomizationManager.saveReceiptSettings(getSettingsFromFields());
            CompanyCustomizationManager.SaleSafetySettings existingSaleSafetySettings = CompanyCustomizationManager.loadSaleSafetySettings();
            if (saleDiscountLimitPercentField.isEnabled() || saleReturnApprovalLimitField.isEnabled()) {
                CompanyCustomizationManager.saveSaleSafetySettings(getSaleSafetySettingsFromFields(existingSaleSafetySettings));
            }
            CompanyCustomizationManager.CustomOrderSettings existingCustomOrderSettings = CompanyCustomizationManager.loadCustomOrderSettings();
            if (customOrderMinimumDepositPercentField.isEnabled() || customOrderRefundApprovalLimitField.isEnabled()) {
                CompanyCustomizationManager.saveCustomOrderSettings(getCustomOrderSettingsFromFields(existingCustomOrderSettings));
            }
            CompanyCustomizationManager.saveCustomOrderSlipSettings(getSlipSettingsFromFields());
            JOptionPane.showMessageDialog(this, "Company preferences saved.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save company preferences.\n\n" + ex.getMessage(), "Company Preferences", JOptionPane.ERROR_MESSAGE);
        }
    }

    private CompanyCustomizationManager.CustomOrderSettings getCustomOrderSettingsFromFields(CompanyCustomizationManager.CustomOrderSettings existingSettings) {
        String percentValue = customOrderMinimumDepositPercentField.getText() == null ? "" : customOrderMinimumDepositPercentField.getText().trim();
        BigDecimal percent = percentValue.isBlank() ? BigDecimal.ZERO : new BigDecimal(percentValue.replace("%", "").trim());
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Custom order minimum deposit percent must be between 0 and 100.");
        }
        String limitValue = customOrderRefundApprovalLimitField.getText() == null ? "" : customOrderRefundApprovalLimitField.getText().trim();
        BigDecimal refundApprovalLimit = limitValue.isBlank() ? BigDecimal.ZERO : new BigDecimal(limitValue.replace("$", "").replace(",", "").trim());
        if (refundApprovalLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Custom order refund approval limit cannot be negative.");
        }
        BigDecimal savedPercent = customOrderMinimumDepositPercentField.isEnabled() ? percent : existingSettings.minimumDepositPercent();
        BigDecimal savedRefundLimit = customOrderRefundApprovalLimitField.isEnabled() ? refundApprovalLimit : existingSettings.refundApprovalLimit();
        return new CompanyCustomizationManager.CustomOrderSettings(savedPercent, savedRefundLimit);
    }

    private CompanyCustomizationManager.SaleSafetySettings getSaleSafetySettingsFromFields(CompanyCustomizationManager.SaleSafetySettings existingSettings) {
        String discountValue = saleDiscountLimitPercentField.getText() == null ? "" : saleDiscountLimitPercentField.getText().trim();
        BigDecimal discountLimit = discountValue.isBlank() ? BigDecimal.valueOf(5) : new BigDecimal(discountValue.replace("%", "").trim());
        if (discountLimit.compareTo(BigDecimal.ZERO) < 0 || discountLimit.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Sale discount limit percent must be between 0 and 100.");
        }
        String returnLimitValue = saleReturnApprovalLimitField.getText() == null ? "" : saleReturnApprovalLimitField.getText().trim();
        BigDecimal returnApprovalLimit = returnLimitValue.isBlank() ? BigDecimal.ZERO : new BigDecimal(returnLimitValue.replace("$", "").replace(",", "").trim());
        if (returnApprovalLimit.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Sale return approval limit cannot be negative.");
        }
        BigDecimal savedDiscountLimit = saleDiscountLimitPercentField.isEnabled() ? discountLimit : existingSettings.discountLimitPercent();
        BigDecimal savedReturnLimit = saleReturnApprovalLimitField.isEnabled() ? returnApprovalLimit : existingSettings.returnApprovalLimit();
        return new CompanyCustomizationManager.SaleSafetySettings(savedDiscountLimit, savedReturnLimit);
    }

    private CompanyCustomizationManager.ReceiptSettings getSettingsFromFields() {
        return new CompanyCustomizationManager.ReceiptSettings(
                companyNameField.getText(),
                headerLineField.getText(),
                footerLineField.getText(),
                logoPathField.getText(),
                showLogoBox.isSelected(),
                showSaleIdBox.isSelected(),
                showDeviceBox.isSelected(),
                showCustomerBox.isSelected(),
                showSkuBox.isSelected(),
                showItemDiscountBox.isSelected(),
                showPaymentStatusBox.isSelected(),
                vatEnabledBox.isSelected(),
                vatUseDepartmentRatesBox.isSelected(),
                parsePercentField(vatFixedRatePercentField.getText(), "Fixed VAT percent"),
                parsePositiveCounter(receiptStartCounterField.getText())
        );
    }

    private BigDecimal parsePercentField(String value, String label) {
        String text = value == null ? "" : value.trim();
        BigDecimal percent = text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text.replace("%", "").trim());
        if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(label + " must be between 0 and 100.");
        }
        return percent;
    }

    private int parsePositiveCounter(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "1" : value.trim());
            if (parsed < 1) {
                throw new IllegalArgumentException("Receipt counter start must be 1 or more.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Receipt counter start must be a whole number.");
        }
    }

    private void loadSlipFields(CompanyCustomizationManager.CustomOrderSlipSettings settings) {
        slipEnabledBox.setSelected(settings.enabled());
        slipAutoPrintBox.setSelected(settings.autoPrint());
        slipTitleField.setText(settings.title());
        slipContactLineField.setText(settings.contactLine());
        slipEmailLineField.setText(settings.emailLine());
        slipFooterNoteField.setText(settings.footerNote());
        slipBlankDetailLinesField.setText(String.valueOf(settings.blankDetailLines()));
        slipShowLogoBox.setSelected(settings.showLogo());
        slipShowOrderNumberBox.setSelected(settings.showOrderNumber());
        slipShowDueDateBox.setSelected(settings.showDueDate());
        slipShowCustomerPhoneBox.setSelected(settings.showCustomerPhone());
        slipShowCustomerAccountBox.setSelected(settings.showCustomerAccount());
        slipShowStoreBox.setSelected(settings.showStore());
        slipShowDeviceBox.setSelected(settings.showDevice());
        slipShowCashierBox.setSelected(settings.showCashier());
        slipShowLineItemsBox.setSelected(settings.showLineItems());
        slipShowPricingBox.setSelected(settings.showPricing());
        slipShowPaymentSummaryBox.setSelected(settings.showPaymentSummary());
        slipShowPaymentReferenceBox.setSelected(settings.showPaymentReference());
        slipShowTakenByBox.setSelected(settings.showTakenBy());
        slipShowSignaturesBox.setSelected(settings.showSignatures());
    }

    private CompanyCustomizationManager.CustomOrderSlipSettings getSlipSettingsFromFields() {
        int blankLines;
        try {
            blankLines = Integer.parseInt(slipBlankDetailLinesField.getText() == null || slipBlankDetailLinesField.getText().isBlank()
                    ? "8"
                    : slipBlankDetailLinesField.getText().trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Blank detail lines must be a whole number from 0 to 20.");
        }
        if (blankLines < 0 || blankLines > 20) {
            throw new IllegalArgumentException("Blank detail lines must be from 0 to 20.");
        }
        return new CompanyCustomizationManager.CustomOrderSlipSettings(
                slipEnabledBox.isSelected(),
                slipAutoPrintBox.isSelected(),
                slipTitleField.getText(),
                slipContactLineField.getText(),
                slipEmailLineField.getText(),
                slipFooterNoteField.getText(),
                blankLines,
                slipShowLogoBox.isSelected(),
                slipShowOrderNumberBox.isSelected(),
                slipShowDueDateBox.isSelected(),
                slipShowCustomerPhoneBox.isSelected(),
                slipShowCustomerAccountBox.isSelected(),
                slipShowStoreBox.isSelected(),
                slipShowDeviceBox.isSelected(),
                slipShowCashierBox.isSelected(),
                slipShowLineItemsBox.isSelected(),
                slipShowPricingBox.isSelected(),
                slipShowPaymentSummaryBox.isSelected(),
                slipShowPaymentReferenceBox.isSelected(),
                slipShowTakenByBox.isSelected(),
                slipShowSignaturesBox.isSelected()
        );
    }

    private void uploadLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Company Logo");
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg", "gif", "bmp"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        try {
            String uploadedPath = CompanyCustomizationManager.uploadCompanyLogo(selectedFile.toPath());
            logoPathField.setText(uploadedPath);
            showLogoBox.setSelected(true);
            updateLogoPreview(uploadedPath);
            refreshSamplePreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload logo.\n\n" + ex.getMessage(), "Logo Upload", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearLogo() {
        logoPathField.setText("");
        showLogoBox.setSelected(false);
        updateLogoPreview("");
        refreshSamplePreview();
    }

    private void updateLogoPreview(String logoPath) {
        logoPreviewLabel.setIcon(null);
        logoPreviewLabel.setText("No Logo");
        if (logoPath == null || logoPath.isBlank()) {
            return;
        }

        ImageIcon icon;
        try {
            if (logoPath.startsWith("http://") || logoPath.startsWith("https://")) {
                URL url = URI.create(logoPath).toURL();
                icon = new ImageIcon(url);
            } else {
                icon = new ImageIcon(Path.of(logoPath).toString());
            }
        } catch (Exception ex) {
            return;
        }
        if (icon.getIconWidth() <= 0) {
            return;
        }

        Image scaled = icon.getImage().getScaledInstance(210, -1, Image.SCALE_SMOOTH);
        logoPreviewLabel.setText("");
        logoPreviewLabel.setIcon(new ImageIcon(scaled));
    }

    private void wireLivePreview() {
        DocumentListener previewDocumentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshSlipPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshSlipPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshSlipPreview();
            }
        };

        companyNameField.getDocument().addDocumentListener(previewDocumentListener);
        headerLineField.getDocument().addDocumentListener(previewDocumentListener);
        footerLineField.getDocument().addDocumentListener(previewDocumentListener);
        receiptStartCounterField.getDocument().addDocumentListener(previewDocumentListener);
        vatFixedRatePercentField.getDocument().addDocumentListener(previewDocumentListener);
        logoPathField.getDocument().addDocumentListener(previewDocumentListener);

        showLogoBox.addActionListener(e -> refreshSamplePreview());
        showSaleIdBox.addActionListener(e -> refreshSamplePreview());
        showDeviceBox.addActionListener(e -> refreshSamplePreview());
        showCustomerBox.addActionListener(e -> refreshSamplePreview());
        showSkuBox.addActionListener(e -> refreshSamplePreview());
        showItemDiscountBox.addActionListener(e -> refreshSamplePreview());
        showPaymentStatusBox.addActionListener(e -> refreshSamplePreview());
        vatEnabledBox.addActionListener(e -> refreshSamplePreview());
        vatUseDepartmentRatesBox.addActionListener(e -> refreshSamplePreview());

        slipEnabledBox.addActionListener(e -> refreshSlipPreview());
        slipAutoPrintBox.addActionListener(e -> refreshSlipPreview());
        slipShowLogoBox.addActionListener(e -> refreshSlipPreview());
        slipShowOrderNumberBox.addActionListener(e -> refreshSlipPreview());
        slipShowDueDateBox.addActionListener(e -> refreshSlipPreview());
        slipShowCustomerPhoneBox.addActionListener(e -> refreshSlipPreview());
        slipShowCustomerAccountBox.addActionListener(e -> refreshSlipPreview());
        slipShowStoreBox.addActionListener(e -> refreshSlipPreview());
        slipShowDeviceBox.addActionListener(e -> refreshSlipPreview());
        slipShowCashierBox.addActionListener(e -> refreshSlipPreview());
        slipShowLineItemsBox.addActionListener(e -> refreshSlipPreview());
        slipShowPricingBox.addActionListener(e -> refreshSlipPreview());
        slipShowPaymentSummaryBox.addActionListener(e -> refreshSlipPreview());
        slipShowPaymentReferenceBox.addActionListener(e -> refreshSlipPreview());
        slipShowTakenByBox.addActionListener(e -> refreshSlipPreview());
        slipShowSignaturesBox.addActionListener(e -> refreshSlipPreview());
        slipTitleField.getDocument().addDocumentListener(previewDocumentListener);
        slipContactLineField.getDocument().addDocumentListener(previewDocumentListener);
        slipEmailLineField.getDocument().addDocumentListener(previewDocumentListener);
        slipFooterNoteField.getDocument().addDocumentListener(previewDocumentListener);
        slipBlankDetailLinesField.getDocument().addDocumentListener(previewDocumentListener);
    }

    private void refreshSamplePreview() {
        if (loadingSettings) {
            return;
        }

        CompanyCustomizationManager.ReceiptSettings previewSettings = getSettingsFromFields();
        CompanyCustomizationManager.setPreviewOverrideSettings(previewSettings);
        try {
            sampleReceiptPaperPanel.setReceiptText(ReceiptFormatter.formatText(createSampleReceipt()), false);
            updateSampleLogoPreview(previewSettings);
        } finally {
            CompanyCustomizationManager.clearPreviewOverrideSettings();
        }
    }

    private void refreshSlipPreview() {
        if (loadingSettings) {
            return;
        }
        CustomOrderSlipData sampleSlip = CustomOrderSlipBuilder.sample();
        CompanyCustomizationManager.ReceiptSettings receiptSettings = getSettingsFromFields();
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = getSlipSettingsForPreview();
        sampleSlipPanel.setSlip(
                sampleSlip,
                receiptSettings,
                slipSettings
        );
        sampleSlip40ColumnPanel.setReceiptText(CustomOrderSlipFormatter.format40Column(sampleSlip, receiptSettings, slipSettings), false);
        updateSlipLogoPreview();
    }

    private CompanyCustomizationManager.CustomOrderSlipSettings getSlipSettingsForPreview() {
        try {
            return getSlipSettingsFromFields();
        } catch (IllegalArgumentException ex) {
            return new CompanyCustomizationManager.CustomOrderSlipSettings(
                    slipEnabledBox.isSelected(),
                    slipAutoPrintBox.isSelected(),
                    slipTitleField.getText(),
                    slipContactLineField.getText(),
                    slipEmailLineField.getText(),
                    slipFooterNoteField.getText(),
                    8,
                    slipShowLogoBox.isSelected(),
                    slipShowOrderNumberBox.isSelected(),
                    slipShowDueDateBox.isSelected(),
                    slipShowCustomerPhoneBox.isSelected(),
                    slipShowCustomerAccountBox.isSelected(),
                    slipShowStoreBox.isSelected(),
                    slipShowDeviceBox.isSelected(),
                    slipShowCashierBox.isSelected(),
                    slipShowLineItemsBox.isSelected(),
                    slipShowPricingBox.isSelected(),
                    slipShowPaymentSummaryBox.isSelected(),
                    slipShowPaymentReferenceBox.isSelected(),
                    slipShowTakenByBox.isSelected(),
                    slipShowSignaturesBox.isSelected()
            );
        }
    }

    private ReceiptData createSampleReceipt() {
        return new ReceiptData(
                12345,
                "0001-0001-000123",
                Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 14, 35)),
                "Main Store",
                "Sample Cashier",
                "Alex Customer",
                "C-000100",
                "CASH",
                "PAID",
                "POS-DEMO",
                new BigDecimal("22.50"),
                new BigDecimal("5.00"),
                new BigDecimal("1.13"),
                new BigDecimal("3.20"),
                new BigDecimal("15.00"),
                "FIXED",
                new BigDecimal("21.37"),
                new BigDecimal("24.57"),
                BigDecimal.ZERO,
                new BigDecimal("25.00"),
                new BigDecimal("0.43"),
                List.of(
                        new ReceiptItem("Salted Chips", "CHIP-001", 2, new BigDecimal("2.50"), new BigDecimal("2.38"), new BigDecimal("5.00"), new BigDecimal("4.76")),
                        new ReceiptItem("Sparkling Water", "DRINK-010", 3, new BigDecimal("1.50"), new BigDecimal("1.43"), new BigDecimal("0.00"), new BigDecimal("4.29")),
                        new ReceiptItem("Notebook", "NOTE-200", 1, new BigDecimal("12.32"), new BigDecimal("12.32"), new BigDecimal("0.00"), new BigDecimal("12.32"))
                )
        );
    }

    private void updateSampleLogoPreview(CompanyCustomizationManager.ReceiptSettings settings) {
        sampleReceiptPaperPanel.setLogo(null, false);
        if (settings == null || !settings.showLogo() || settings.logoPath().isBlank()) {
            return;
        }

        sampleReceiptPaperPanel.setLogoLoading(true);
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return CompanyCustomizationManager.loadReceiptLogo(settings);
            }

            @Override
            protected void done() {
                try {
                    sampleReceiptPaperPanel.setLogo(get(), false);
                } catch (Exception ex) {
                    sampleReceiptPaperPanel.setLogo(null, false);
                }
            }
        }.execute();
    }

    private void updateSlipLogoPreview() {
        CompanyCustomizationManager.ReceiptSettings settings = getSettingsFromFields();
        sampleSlipPanel.setLogo(null);
        sampleSlip40ColumnPanel.setLogo(null, false);
        if (!getSlipSettingsForPreview().showLogo() || settings.logoPath().isBlank()) {
            return;
        }
        sampleSlip40ColumnPanel.setLogoLoading(true);
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return CompanyCustomizationManager.loadCompanyLogo(settings);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage logo = get();
                    sampleSlipPanel.setLogo(logo);
                    sampleSlip40ColumnPanel.setLogo(logo, false);
                } catch (Exception ex) {
                    sampleSlipPanel.setLogo(null);
                    sampleSlip40ColumnPanel.setLogo(null, false);
                }
            }
        }.execute();
    }

    private static class CustomOrderSlipPreviewPanel extends JPanel {
        private CustomOrderSlipData slipData = CustomOrderSlipBuilder.sample();
        private CompanyCustomizationManager.ReceiptSettings receiptSettings = CompanyCustomizationManager.loadReceiptSettings();
        private CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = CompanyCustomizationManager.loadCustomOrderSlipSettings();
        private BufferedImage logo;

        CustomOrderSlipPreviewPanel() {
            setPreferredSize(new Dimension(760, 470));
            setBackground(new Color(248, 250, 252));
        }

        void setSlip(CustomOrderSlipData slipData,
                     CompanyCustomizationManager.ReceiptSettings receiptSettings,
                     CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) {
            this.slipData = slipData;
            this.receiptSettings = receiptSettings;
            this.slipSettings = slipSettings;
            repaint();
        }

        void setLogo(BufferedImage logo) {
            this.logo = logo;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                CustomOrderSlipRenderer.paintSlip(g, 20, 20, getWidth() - 40, 390, slipData, receiptSettings, slipSettings, logo);
            } finally {
                g.dispose();
            }
        }
    }
}
