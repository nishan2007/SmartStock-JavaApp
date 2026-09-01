package ui.screens;

import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import Receipt.ReceiptItem;
import Receipt.AccountPaymentReceiptData;
import Receipt.AccountPaymentReceiptFormatter;
import Receipt.CustomOrderSlipBuilder;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipFormatter;
import Receipt.CustomOrderSlipRenderer;
import Receipt.QuotationInvoiceDocumentBuilder;
import managers.CompanyCustomizationManager;
import managers.NavigationManager;
import managers.PermissionManager;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.BadgePrintService;
import services.CustomerCardService;
import services.PriceTagPrintService;
import services.CompanyBackupService;
import services.CompanyBackupScheduler;
import services.TimeClockAutoCloseService;
import services.TimeClockAutoCloseService.AutoCloseSettings;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.components.PreferenceTreeCellRenderer;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.ThemeManager;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;
import utils.ImageCacheManager;
import ui.screens.companyprefs.CompanyIdentityPanel;
import ui.screens.companyprefs.AccountPaymentReceiptPanel;
import ui.screens.companyprefs.CustomOrderDepositPanel;
import ui.screens.companyprefs.CustomOrderReceiptPanel;
import ui.screens.companyprefs.SaleReceiptPanel;
import ui.screens.companyprefs.QuotationInvoicePrintPanel;
import ui.screens.companyprefs.ImageStoragePanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompanyCustomization extends JFrame {
    public static final String NAV_COMPANY_IDENTITY = "Company Identity";
    public static final String NAV_EMPLOYEE_BADGES = "Employee Badges";
    public static final String NAV_LOCATIONS = "Locations";
    public static final String NAV_CASH_DRAWER_MANAGER = "Cash Drawer Manager";
    private static final String NAV_BACKUPS = "Backups";
    private static final String NAV_TIME_CLOCK_SAFETY = "Time Clock Safety";
    private static final String NAV_SALE = "Sale";
    private static final String NAV_SALE_RECEIPT_FORMATTING = "Sale Receipt & Formatting";
    private static final String NAV_ACCOUNT_PAYMENT_RECEIPTS = "Account Payment Receipts";
    private static final String NAV_CUSTOM_ORDERS = "Custom Orders";
    private static final String NAV_CUSTOM_ORDER_DEPOSIT_REFUND = "Order Deposit & Refund Approval";
    private static final String NAV_CUSTOM_ORDER_SLIP_FORMATTING = "Receipt/Slip Formatting";
    private static final String NAV_QUOTATION_ORDER_PRINTING = "Quotation/Invoice Printouts";
    private static final String NAV_PRICE_TAG_TEMPLATE = "Price Tag Template";
    private static final String NAV_IMAGE_STORAGE = "Image Storage";
    private static final int BADGE_CARD_WIDTH = 638;
    private static final int BADGE_CARD_HEIGHT = 1013;

    private static String[] badgeTemplateSlotLabels() {
        String[] labels = new String[CompanyCustomizationManager.badgeTemplateCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = CompanyCustomizationManager.badgeTemplateDisplayName(i);
        }
        return labels;
    }

    private static JTextArea receiptTextArea() {
        JTextArea area = new JTextArea(3, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("SansSerif", Font.PLAIN, 14));
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return area;
    }

    private final JTextField companyNameField = new JTextField();
    private final JTextField companyAddressLine1Field = new JTextField();
    private final JTextField companyAddressLine2Field = new JTextField();
    private final JTextField companyAddressLine3Field = new JTextField();
    private final JTextField companyPhoneLine1Field = new JTextField();
    private final JTextField companyPhoneLine2Field = new JTextField();
    private final JTextField companyEmailLine1Field = new JTextField();
    private final JTextField companyEmailLine2Field = new JTextField();
    private final JTextField companyMottoLine1Field = new JTextField();
    private final JTextField companyMottoLine2Field = new JTextField();
    private final JTextArea headerLineField = receiptTextArea();
    private final JTextArea footerLineField = receiptTextArea();
    private final JTextField receiptStartCounterField = new JTextField("1");
    private final JTextField logoPathField = new JTextField();
    private final JTextField configPathField = new JTextField();
    private final JCheckBox backupSchedulerEnabledBox = new JCheckBox("Run automatic company backups");
    private final JTextField backupDirectoryField = new JTextField();
    private final JSpinner backupIntervalMinutesSpinner = new JSpinner(new SpinnerNumberModel(1440, 15, 525600, 15));
    private final JSpinner backupRetentionCountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 1000, 1));
    private final JLabel backupStatusLabel = new JLabel("Not run yet");
    private final JCheckBox timeClockAutoCloseEnabledBox = new JCheckBox("Automatically close stale time-clock sessions");
    private final JSpinner scheduledDetectionDelaySpinner = new JSpinner(new SpinnerNumberModel(4, 0, 24, 1));
    private final JSpinner unscheduledDetectionHoursSpinner = new JSpinner(new SpinnerNumberModel(12, 1, 48, 1));
    private final JSpinner maximumAutomaticWorkHoursSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 24, 1));
    private final JCheckBox showLogoBox = new JCheckBox("Show logo on receipt");
    private final JCheckBox showSaleIdBox = new JCheckBox("Show sale ID");
    private final JCheckBox showDeviceBox = new JCheckBox("Show device ID");
    private final JCheckBox showCustomerBox = new JCheckBox("Show customer/account");
    private final JCheckBox showSkuBox = new JCheckBox("Show SKU");
    private final JCheckBox showItemDiscountBox = new JCheckBox("Show item discounts");
    private final JCheckBox showPaymentStatusBox = new JCheckBox("Show payment status");
    private final JCheckBox alwaysPrintSaleReceiptBox = new JCheckBox("Always print receipt");
    private final JTextField accountPaymentReceiptTitleField = new JTextField("CUSTOMER ACCOUNT PAYMENT");
    private final JCheckBox accountPaymentReceiptShowUserBox = new JCheckBox("User");
    private final JCheckBox accountPaymentReceiptShowCustomerBox = new JCheckBox("Customer");
    private final JCheckBox accountPaymentReceiptShowAccountNumberBox = new JCheckBox("Account number");
    private final JCheckBox accountPaymentReceiptShowMethodBox = new JCheckBox("Payment method");
    private final JCheckBox accountPaymentReceiptShowReferenceBox = new JCheckBox("Payment reference");
    private final JCheckBox accountPaymentReceiptShowDeviceBox = new JCheckBox("Device");
    private final JCheckBox accountPaymentReceiptShowDrawerBox = new JCheckBox("Cash drawer");
    private final JCheckBox accountPaymentReceiptShowAllocationsBox = new JCheckBox("Applied charges");
    private final JCheckBox accountPaymentReceiptShowBalanceBox = new JCheckBox("Balance due");
    private final JCheckBox accountPaymentReceiptShowBarcodeBox = new JCheckBox("Barcode");
    private final JCheckBox vatEnabledBox = new JCheckBox("Enable VAT");
    private final JCheckBox vatUseDepartmentRatesBox = new JCheckBox("Use department VAT rates");
    private final JTextField vatFixedRatePercentField = new JTextField("0", 8);
    private final JTextField saleDiscountLimitPercentField = new JTextField("5", 8);
    private final JTextField saleReturnApprovalLimitField = new JTextField("0", 8);
    private final JCheckBox requireCostPriceOnNewItemBox = new JCheckBox("Require cost price when adding a new inventory item", true);
    private final JCheckBox roundSalesToNearestTwentyBox = new JCheckBox("Round final sale total to nearest $20", true);
    private final JTextField customOrderMinimumDepositPercentField = new JTextField("0", 8);
    private final JTextField customOrderRefundApprovalLimitField = new JTextField("0", 8);
    private final JCheckBox roundCustomOrdersToNearestTwentyBox = new JCheckBox("Round custom-order line prices to nearest $20", true);
    private final JCheckBox slipEnabledBox = new JCheckBox("Enable custom order slips");
    private final JCheckBox slipAutoPrintBox = new JCheckBox("Always print order slip");
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
    private final JTextField quotationPrintTitleField = new JTextField("QUOTE / NOT FINAL SALE");
    private final JTextField quotationValidityNoteField = new JTextField("This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.");
    private final JTextField invoicePrintTitleField = new JTextField("INVOICE");
    private final JTextField salesDeliveryPrintTitleField = new JTextField("DELIVERY BILL");
    private final JTextField quotationInvoiceFooterNoteField = new JTextField();
    private final JCheckBox quotationInvoiceShowSignaturesBox = new JCheckBox("Received/approved signature lines");
    private final JTextField badgeCompanyNameField = new JTextField();
    private final JTextField badgeLogoPathField = new JTextField();
    private final JTextField badgeQuoteField = new JTextField();
    private final JTextField badgeSignatoryNameField = new JTextField();
    private final JTextField badgeSignatoryTitleField = new JTextField();
    private final JTextField badgeBackInstructionsField = new JTextField();
    private final JCheckBox badgeShowQuoteBox = new JCheckBox("Quote");
    private final JCheckBox badgeShowEmployeeIdBox = new JCheckBox("Employee ID");
    private final JCheckBox badgeShowIssueDateBox = new JCheckBox("Issue date");
    private final JCheckBox badgeShowBarcodeBox = new JCheckBox("Back barcode");
    private final JCheckBox badgeShowBadgeTextBox = new JCheckBox("Badge ID text");
    private final JCheckBox badgeMagStripeEnabledBox = new JCheckBox("Enable magnetic stripe writer");
    private final JTextField badgeMagStripeTrack1Field = new JTextField();
    private final JTextField badgeMagStripeTrack2Field = new JTextField();
    private final JTextField badgeMagStripeTrack3Field = new JTextField();
    private final JTextField badgeMagStripeCommandField = new JTextField();
    private final JCheckBox badgeNfcEnabledBox = new JCheckBox("Enable RFID/NFC writer");
    private final JCheckBox requireBadgePinLoginBox = new JCheckBox(
            "Require employee PIN after badge scan, swipe, or tap", true);
    private final JTextField badgeNfcPayloadField = new JTextField("{badge_id}");
    private final JTextField badgeNfcWriterCommandField = new JTextField();
    private final JTextField badgeNfcVerifyCommandField = new JTextField();
    private String badgeLayoutData = "";
    private final String[] badgeTemplateLayouts = new String[CompanyCustomizationManager.badgeTemplateCount()];
    private int badgeTemplateSlotIndex = 0;
    private boolean updatingBadgeTemplateSlot = false;
    private final JComboBox<String> badgeTemplateSlotBox = new JComboBox<>(badgeTemplateSlotLabels());
    private String badgeSelectedTemplateElementId = "";
    private final Set<String> badgeSelectedTemplateElementIds = new LinkedHashSet<>();
    private final Map<String, JCheckBox> badgeElementVisibilityBoxes = new LinkedHashMap<>();
    private boolean updatingBadgeFontControls = false;
    private final JComboBox<String> badgeFontFamilyBox = new JComboBox<>(new String[]{"SansSerif"});
    private final JComboBox<String> badgeFontStyleBox = new JComboBox<>(new String[]{"Regular", "Bold", "Italic", "Bold Italic"});
    private final JComboBox<String> badgeFontWeightBox = new JComboBox<>(new String[]{"Regular", "Medium", "Semi Bold", "Bold", "Extra Bold", "Black"});
    private final JSpinner badgeFontMaxSizeSpinner = new JSpinner(new SpinnerNumberModel(44, 8, 96, 1));
    private final JComboBox<String> badgeTextAlignmentBox = new JComboBox<>(new String[]{"Left", "Center", "Right"});
    private final JComboBox<String> badgeElementRotationBox = new JComboBox<>(new String[]{"0", "90", "180", "270"});
    private final JSpinner badgeElementXSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 638, 1));
    private final JSpinner badgeElementYSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 1013, 1));
    private final JSpinner badgeElementWidthSpinner = new JSpinner(new SpinnerNumberModel(100, 24, 638, 1));
    private final JSpinner badgeElementHeightSpinner = new JSpinner(new SpinnerNumberModel(40, 18, 1013, 1));
    private final JTextField badgeCustomTextField = new JTextField();
    private final JTextField badgeSignatureImageField = new JTextField();
    private final JTextField badgeElementImageField = new JTextField();
    private final JSlider badgeImageOpacitySlider = new JSlider(5, 100, 100);
    private final JLabel badgeImageOpacityValueLabel = new JLabel("100%");
    private final JButton badgeElementColorButton = new JButton("Choose");
    private final JLabel badgeElementColorSwatch = new JLabel();
    private final JCheckBox badgeTextAllCapsBox = new JCheckBox("All caps");
    private final JCheckBox badgeTextOutlineBox = new JCheckBox("Text outline");
    private final JButton badgeTextOutlineColorButton = new JButton("Choose");
    private final JLabel badgeTextOutlineColorSwatch = new JLabel();
    private final JCheckBox badgeTextBoxOutlineBox = new JCheckBox("Box outline");
    private final JButton badgeTextBoxOutlineColorButton = new JButton("Choose");
    private final JLabel badgeTextBoxOutlineColorSwatch = new JLabel();
    private final JButton badgeAlignGroupCenterHButton = new JButton("Center H");
    private final JButton badgeAlignGroupCenterVButton = new JButton("Center V");
    private final JButton badgeAlignGroupCenterBothButton = new JButton("Center Both");
    private final JComboBox<String> badgeNameLayoutBox = new JComboBox<>(new String[]{"One line", "Two lines"});
    private final JLabel badgeSelectedElementLabel = new JLabel("Select an element");
    private BadgeTemplateEditorPanel sampleBadgeFrontPanel;
    private BadgeTemplateEditorPanel sampleBadgeBackPanel;
    private final JLabel logoPreviewLabel = new JLabel("No Logo", SwingConstants.CENTER);
    private final ReceiptPreview.ReceiptPaperPanel sampleReceiptPaperPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final ReceiptPreview.ReceiptPaperPanel sampleAccountPaymentReceipt40Panel = new ReceiptPreview.ReceiptPaperPanel();
    private final ReceiptPreview.ReceiptPaperPanel sampleAccountPaymentReceiptLetterPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final CustomOrderSlipPreviewPanel sampleSlipPanel = new CustomOrderSlipPreviewPanel();
    private final ReceiptPreview.ReceiptPaperPanel sampleSlip40ColumnPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final JEditorPane sampleQuotationPane = createSalesDocumentPreviewPane();
    private final JEditorPane sampleInvoicePane = createSalesDocumentPreviewPane();
    private final JEditorPane sampleSalesDeliveryPane = createSalesDocumentPreviewPane();
    private final JPanel rightContentPanel = new JPanel(new CardLayout());
    private final Map<String, JComponent> preferenceCardPlaceholders = new LinkedHashMap<>();
    private final Set<String> builtPreferenceCards = new HashSet<>();
    private final Set<String> pendingPreferenceCards = new HashSet<>();
    private String selectedPreferenceCardKey;
    private boolean badgeFontsLoading;
    private final JCheckBox priceTagShowCompanyBox = new JCheckBox("Show company name");
    private final JCheckBox priceTagShowNameBox = new JCheckBox("Show item name");
    private final JCheckBox priceTagShowPriceBox = new JCheckBox("Show price");
    private final JCheckBox priceTagShowSkuBox = new JCheckBox("Show SKU");
    private final JCheckBox priceTagShowBarcodeBox = new JCheckBox("Show barcode");
    private final JCheckBox priceTagShowSizeBox = new JCheckBox("Show size");
    private final JCheckBox priceTagShowDescriptionBox = new JCheckBox("Show description");
    private final JComboBox<String> priceTagTemplateSlotBox = new JComboBox<>(new String[]{"Template 1", "Template 2", "Template 3", "Template 4", "Template 5"});
    private final JTextField priceTagTemplateNameField = new JTextField(16);
    private final JSpinner priceTagWidthSpinner = new JSpinner(new SpinnerNumberModel(2.25d, .75d, 6d, .25d));
    private final JSpinner priceTagHeightSpinner = new JSpinner(new SpinnerNumberModel(1.25d, .5d, 4d, .25d));
    private final JLabel priceTagPreviewLabel = new JLabel();
    private List<CompanyCustomizationManager.PriceTagTemplateSettings> priceTagTemplates = new ArrayList<>();
    private int activePriceTagTemplateSlot = 0;
    private JTree navigationTree;
    private boolean loadingSettings = false;
    private JButton saveButton;
    private final LoadingStatePanel loadingStatePanel = new LoadingStatePanel();
    private BigDecimal loadedChangeBasketTargetAmount = BigDecimal.valueOf(60000);
    private CompanyCustomizationManager.SaleSafetySettings loadedSaleSafetySettings;
    private CompanyCustomizationManager.CustomOrderSettings loadedCustomOrderSettings;
    private CompanyBackupScheduler.BackupScheduleSettings loadedBackupSettings;
    private final String initialPreferenceSection;

    public CompanyCustomization() {
        this(NAV_COMPANY_IDENTITY);
    }

    public CompanyCustomization(String initialSection) {
        initialPreferenceSection = firstPermittedSection(initialSection);
        setTitle("Company Preferences");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 680));
        setLocationRelativeTo(null);
        setJMenuBar(AppMenuBar.create(this, "CompanyCustomization"));

        JPanel rootPanel = new JPanel(new BorderLayout(20, 18));
        rootPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        rootPanel.setBorder(new EmptyBorder(22, 24, 20, 24));
        rootPanel.setBackground(DeckersPalette.background());

        JLabel titleLabel = new JLabel("Company Preferences");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        titleLabel.setForeground(DeckersPalette.text());
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        rootPanel.add(titleLabel, BorderLayout.NORTH);

        addPermittedCardPlaceholders();

        JSplitPane mainSplitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                buildNavigationPanel(),
                rightContentPanel
        );
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder());
        mainSplitPane.setContinuousLayout(true);
        mainSplitPane.setResizeWeight(0);
        mainSplitPane.setDividerLocation(270);
        mainSplitPane.setDividerSize(8);
        rootPanel.add(mainSplitPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton exportBackupButton = new JButton("Export Backup");
        JButton restoreBackupButton = new JButton("Restore Backup");
        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");
        saveButton = new JButton("Save");
        DeckersSwing.styleUtilityButton(exportBackupButton, DeckersPalette.PURPLE);
        DeckersSwing.styleUtilityButton(restoreBackupButton, DeckersPalette.YELLOW);
        DeckersSwing.styleUtilityButton(refreshButton, DeckersPalette.PURPLE);
        DeckersSwing.styleUtilityButton(closeButton, DeckersPalette.CORAL);
        DeckersSwing.styleUtilityButton(saveButton, DeckersPalette.LIME);
        if (isPhysicalServerMode()) {
            buttonPanel.add(exportBackupButton);
            buttonPanel.add(restoreBackupButton);
        }
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        buttonPanel.add(saveButton);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(4, 0, 0, 0));
        footer.add(loadingStatePanel, BorderLayout.CENTER);
        footer.add(buttonPanel, BorderLayout.SOUTH);
        rootPanel.add(footer, BorderLayout.SOUTH);

        add(rootPanel);

        exportBackupButton.addActionListener(e -> exportCompanyBackup());
        restoreBackupButton.addActionListener(e -> restoreCompanyBackup());
        refreshButton.addActionListener(e -> loadSettings());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveButton.addActionListener(e -> saveSettings());
        wireLivePreview();
        saveButton.setEnabled(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                loadSettings();
                routeNavigationKey(initialPreferenceSection);
            }
        });
        ThemeManager.applyToWindow(this);
        WindowHelper.configurePosWindow(this);
    }

    private JComponent buildNavigationPanel() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Preferences");
        addNodeIfPermitted(root, NAV_COMPANY_IDENTITY);
        addNodeIfPermitted(root, NAV_EMPLOYEE_BADGES);
        addNodeIfPermitted(root, NAV_PRICE_TAG_TEMPLATE);
        addNodeIfPermitted(root, NAV_LOCATIONS);
        addNodeIfPermitted(root, NAV_CASH_DRAWER_MANAGER);
        addNodeIfPermitted(root, NAV_BACKUPS);
        addNodeIfPermitted(root, NAV_IMAGE_STORAGE);
        addNodeIfPermitted(root, NAV_TIME_CLOCK_SAFETY);

        DefaultMutableTreeNode saleNode = new DefaultMutableTreeNode(NAV_SALE);
        addNodeIfPermitted(saleNode, NAV_SALE_RECEIPT_FORMATTING);
        addNodeIfPermitted(saleNode, NAV_ACCOUNT_PAYMENT_RECEIPTS);
        if (saleNode.getChildCount() > 0 || canAccessPreferenceSection(NAV_SALE)) {
            root.add(saleNode);
        }

        DefaultMutableTreeNode customOrdersNode = new DefaultMutableTreeNode(NAV_CUSTOM_ORDERS);
        addNodeIfPermitted(customOrdersNode, NAV_CUSTOM_ORDER_DEPOSIT_REFUND);
        addNodeIfPermitted(customOrdersNode, NAV_CUSTOM_ORDER_SLIP_FORMATTING);
        addNodeIfPermitted(customOrdersNode, NAV_QUOTATION_ORDER_PRINTING);
        if (customOrdersNode.getChildCount() > 0 || canAccessPreferenceSection(NAV_CUSTOM_ORDERS)) {
            root.add(customOrdersNode);
        }

        JTree tree = new JTree(new DefaultTreeModel(root));
        navigationTree = tree;
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(30);
        tree.setFont(new Font("SansSerif", Font.PLAIN, 14));
        tree.setBorder(new EmptyBorder(4, 4, 4, 4));
        tree.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        tree.setBackground(DeckersPalette.surface());
        tree.setForeground(DeckersPalette.text());
        tree.setOpaque(true);

        tree.setCellRenderer(new PreferenceTreeCellRenderer());

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
        container.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        container.setBackground(DeckersPalette.surface());
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.border()),
                new EmptyBorder(12, 12, 12, 12)
        ));
        JLabel label = new JLabel("Preferences");
        label.setFont(new Font("SansSerif", Font.BOLD, 16));
        label.setForeground(DeckersPalette.text());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        label.setBorder(new EmptyBorder(0, 2, 10, 0));
        container.add(label, BorderLayout.NORTH);
        JScrollPane navigationScroll = new JScrollPane(tree);
        navigationScroll.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        navigationScroll.setBackground(DeckersPalette.surface());
        navigationScroll.setBorder(BorderFactory.createEmptyBorder());
        navigationScroll.getViewport().putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        navigationScroll.getViewport().setBackground(DeckersPalette.surface());
        container.add(navigationScroll, BorderLayout.CENTER);

        TreePath defaultPath = findTreePath(root, firstPermittedSection(NAV_COMPANY_IDENTITY));
        if (defaultPath != null) {
            tree.setSelectionPath(defaultPath);
        }

        return container;
    }

    private void routeNavigationKey(String key) {
        String permittedKey = firstPermittedSection(key);
        String cardKey = canonicalCardKey(permittedKey);
        selectedPreferenceCardKey = cardKey;
        CardLayout cardLayout = (CardLayout) rightContentPanel.getLayout();
        cardLayout.show(rightContentPanel, cardKey);
        selectNavigationPath(permittedKey);
        if (pendingPreferenceCards.add(cardKey)) {
            SwingUtilities.invokeLater(() -> {
                if (!isDisplayable()) {
                    pendingPreferenceCards.remove(cardKey);
                    return;
                }
                ensurePreferenceCardBuilt(cardKey);
            });
        }
    }

    private void addPermittedCardPlaceholders() {
        for (String key : allPreferenceSections()) {
            if (!canAccessPreferenceSection(key)) continue;
            String cardKey = canonicalCardKey(key);
            if (preferenceCardPlaceholders.containsKey(cardKey)) continue;
            JPanel placeholder = new JPanel(new GridBagLayout());
            placeholder.setBackground(Color.WHITE);
            JLabel loadingLabel = new JLabel("Preparing " + cardKey + "...");
            loadingLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
            loadingLabel.setForeground(new Color(91, 101, 117));
            placeholder.add(loadingLabel);
            preferenceCardPlaceholders.put(cardKey, placeholder);
            rightContentPanel.add(placeholder, cardKey);
        }
    }

    private void ensurePreferenceCardBuilt(String cardKey) {
        if (builtPreferenceCards.contains(cardKey)) {
            pendingPreferenceCards.remove(cardKey);
            return;
        }
        long started = System.nanoTime();
        JComponent cardContent = switch (cardKey) {
            case NAV_COMPANY_IDENTITY -> buildCompanyIdentityScreen();
            case NAV_EMPLOYEE_BADGES -> buildEmployeeBadgesScreen();
            case NAV_PRICE_TAG_TEMPLATE -> buildPriceTagTemplateScreen();
            case NAV_LOCATIONS -> buildLocationsEmbeddedScreen();
            case NAV_CASH_DRAWER_MANAGER -> buildCashDrawerEmbeddedScreen();
            case NAV_BACKUPS -> buildBackupSchedulerScreen();
            case NAV_IMAGE_STORAGE -> new ImageStoragePanel();
            case NAV_TIME_CLOCK_SAFETY -> buildTimeClockSafetyScreen();
            case NAV_SALE_RECEIPT_FORMATTING -> buildSaleReceiptPreferencesScreen();
            case NAV_ACCOUNT_PAYMENT_RECEIPTS -> buildAccountPaymentReceiptPreferencesScreen();
            case NAV_CUSTOM_ORDER_DEPOSIT_REFUND -> buildCustomOrderDepositRefundScreen();
            case NAV_CUSTOM_ORDER_SLIP_FORMATTING -> buildCustomOrderSlipPreferencesScreen();
            case NAV_QUOTATION_ORDER_PRINTING -> buildQuotationInvoicePrintPreferencesScreen();
            default -> throw new IllegalArgumentException("Unknown preference section: " + cardKey);
        };
        JComponent card = decoratePreferenceCard(cardKey, cardContent);
        JComponent placeholder = preferenceCardPlaceholders.get(cardKey);
        if (placeholder != null) rightContentPanel.remove(placeholder);
        rightContentPanel.add(card, cardKey);
        builtPreferenceCards.add(cardKey);
        pendingPreferenceCards.remove(cardKey);
        if (cardKey.equals(selectedPreferenceCardKey)) {
            ((CardLayout) rightContentPanel.getLayout()).show(rightContentPanel, cardKey);
        }
        rightContentPanel.revalidate();
        rightContentPanel.repaint();
        // Cards are created after the window's initial theme pass. Theme the
        // newly inserted controls immediately so Aqua cannot leave stale light
        // fields or panels inside a dark SmartStock window.
        ThemeManager.applyToWindow(this);
        ui.helpers.PerformanceDiagnostics.record("screen-card", cardKey, started, true, -1);
        if (NAV_EMPLOYEE_BADGES.equals(cardKey)) loadBadgeFontFamilies();
    }

    private JComponent decoratePreferenceCard(String cardKey, JComponent content) {
        stylePreferenceControls(content);

        JPanel page = new JPanel(new BorderLayout(0, 14));
        page.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        page.setBackground(DeckersPalette.background());

        JPanel heading = new JPanel();
        heading.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        heading.setBackground(DeckersPalette.background());
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(cardKey);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        title.setForeground(DeckersPalette.text());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel description = new JLabel("<html>" + preferenceSectionDescription(cardKey) + "</html>");
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(DeckersPalette.muted());
        description.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        description.setBorder(new EmptyBorder(4, 0, 0, 0));

        heading.add(title);
        heading.add(description);
        page.add(heading, BorderLayout.NORTH);

        JPanel surface = new JPanel(new BorderLayout());
        surface.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        surface.setBackground(DeckersPalette.surface());
        surface.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.ORANGE)),
                new EmptyBorder(16, 16, 16, 16)
        ));
        surface.add(content, BorderLayout.CENTER);
        page.add(surface, BorderLayout.CENTER);
        return page;
    }

    private String preferenceSectionDescription(String cardKey) {
        return switch (cardKey) {
            case NAV_COMPANY_IDENTITY -> "Business identity and branding used throughout SmartStock.";
            case NAV_EMPLOYEE_BADGES -> "Badge security, printing, encoding, and template controls.";
            case NAV_PRICE_TAG_TEMPLATE -> "Reusable price-tag layouts, dimensions, and printed fields.";
            case NAV_LOCATIONS -> "Stores and business locations available to this company.";
            case NAV_CASH_DRAWER_MANAGER -> "Cash drawers, workstation assignments, and operating controls.";
            case NAV_BACKUPS -> "Automatic encrypted company backups and retention settings.";
            case NAV_IMAGE_STORAGE -> "Company image storage, upload status, and maintenance.";
            case NAV_TIME_CLOCK_SAFETY -> "Automatic safeguards for stale and unusually long shifts.";
            case NAV_SALE_RECEIPT_FORMATTING -> "Sales rules and the information printed on receipts.";
            case NAV_ACCOUNT_PAYMENT_RECEIPTS -> "Fields and layout used for account-payment receipts.";
            case NAV_CUSTOM_ORDER_DEPOSIT_REFUND -> "Deposit requirements and manager refund approval limits.";
            case NAV_CUSTOM_ORDER_SLIP_FORMATTING -> "Custom-order receipt behavior, fields, and appearance.";
            case NAV_QUOTATION_ORDER_PRINTING -> "Titles, notes, signatures, and previews for sales documents.";
            default -> "Company-level settings shared by authorized SmartStock workstations.";
        };
    }

    private void stylePreferenceControls(Component component) {
        if (component instanceof JTextField textField) {
            DeckersSwing.styleField(textField);
            Dimension preferred = textField.getPreferredSize();
            textField.setPreferredSize(new Dimension(Math.max(160, preferred.width), Math.max(34, preferred.height)));
        } else if (component instanceof JTable table) {
            DeckersSwing.styleTable(table, DeckersPalette.ORANGE);
        } else if (component instanceof JButton button
                && !Boolean.TRUE.equals(button.getClientProperty("SmartStock.customPaintedButton"))) {
            DeckersSwing.styleUtilityButton(button, preferenceButtonAccent(button.getText()));
        } else if (component instanceof JComboBox<?> comboBox) {
            Dimension preferred = comboBox.getPreferredSize();
            comboBox.setPreferredSize(new Dimension(preferred.width, Math.max(34, preferred.height)));
        } else if (component instanceof JSpinner spinner) {
            Dimension preferred = spinner.getPreferredSize();
            spinner.setPreferredSize(new Dimension(preferred.width, Math.max(34, preferred.height)));
        } else if (component instanceof JScrollPane scrollPane) {
            scrollPane.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        }

        if (component instanceof JScrollPane scrollPane) {
            Component view = scrollPane.getViewport().getView();
            if (view != null) {
                stylePreferenceControls(view);
            }
        } else if (component instanceof Container container
                && !(component instanceof JComboBox<?>)
                && !(component instanceof JSpinner)) {
            for (Component child : container.getComponents()) {
                stylePreferenceControls(child);
            }
        }
    }

    private Color preferenceButtonAccent(String text) {
        String action = text == null ? "" : text.toLowerCase();
        if (action.contains("save") || action.contains("apply") || action.contains("enable")
                || action.contains("add") || action.contains("upload") || action.contains("create")) {
            return DeckersPalette.LIME;
        }
        if (action.contains("delete") || action.contains("remove") || action.contains("clear")
                || action.contains("stop") || action.contains("disable") || action.contains("cancel")) {
            return DeckersPalette.CORAL;
        }
        if (action.contains("refresh") || action.contains("preview") || action.contains("view")
                || action.contains("select") || action.contains("browse") || action.contains("test")) {
            return DeckersPalette.PURPLE;
        }
        return DeckersPalette.ORANGE;
    }

    private void loadBadgeFontFamilies() {
        if (badgeFontsLoading) return;
        badgeFontsLoading = true;
        String selected = String.valueOf(badgeFontFamilyBox.getSelectedItem());
        UiTaskRunner.submit(this, "company-preferences.badge-fonts",
                () -> GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(),
                families -> {
                    badgeFontsLoading = false;
                    badgeFontFamilyBox.setModel(new DefaultComboBoxModel<>(families));
                    badgeFontFamilyBox.setSelectedItem(selected);
                }, ignored -> badgeFontsLoading = false);
    }

    private String canonicalCardKey(String key) {
        if (NAV_SALE.equals(key)) return NAV_SALE_RECEIPT_FORMATTING;
        if (NAV_CUSTOM_ORDERS.equals(key)) return NAV_CUSTOM_ORDER_SLIP_FORMATTING;
        return key;
    }

    private String[] allPreferenceSections() {
        return new String[]{
                NAV_COMPANY_IDENTITY, NAV_EMPLOYEE_BADGES, NAV_PRICE_TAG_TEMPLATE,
                NAV_LOCATIONS, NAV_CASH_DRAWER_MANAGER, NAV_BACKUPS, NAV_TIME_CLOCK_SAFETY,
                NAV_IMAGE_STORAGE,
                NAV_SALE, NAV_SALE_RECEIPT_FORMATTING, NAV_ACCOUNT_PAYMENT_RECEIPTS,
                NAV_CUSTOM_ORDERS, NAV_CUSTOM_ORDER_DEPOSIT_REFUND,
                NAV_CUSTOM_ORDER_SLIP_FORMATTING, NAV_QUOTATION_ORDER_PRINTING
        };
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
            case NAV_BACKUPS -> isPhysicalServerMode() && canEditCompanyPreferences();
            case NAV_IMAGE_STORAGE -> canEditCompanyPreferences();
            case NAV_COMPANY_IDENTITY, NAV_EMPLOYEE_BADGES, NAV_PRICE_TAG_TEMPLATE, NAV_TIME_CLOCK_SAFETY, NAV_SALE, NAV_SALE_RECEIPT_FORMATTING, NAV_ACCOUNT_PAYMENT_RECEIPTS, NAV_CUSTOM_ORDERS,
                 NAV_CUSTOM_ORDER_DEPOSIT_REFUND, NAV_CUSTOM_ORDER_SLIP_FORMATTING, NAV_QUOTATION_ORDER_PRINTING -> canEditCompanyPreferences();
            default -> false;
        };
    }

    private boolean isPhysicalServerMode() {
        return DatabaseConfig.load().mode() == DatabaseMode.SERVER;
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
                NAV_EMPLOYEE_BADGES,
                NAV_LOCATIONS,
                NAV_CASH_DRAWER_MANAGER,
                NAV_BACKUPS,
                NAV_IMAGE_STORAGE,
                NAV_SALE,
                NAV_SALE_RECEIPT_FORMATTING,
                NAV_ACCOUNT_PAYMENT_RECEIPTS,
                NAV_CUSTOM_ORDER_DEPOSIT_REFUND,
                NAV_CUSTOM_ORDERS,
                NAV_CUSTOM_ORDER_SLIP_FORMATTING,
                NAV_QUOTATION_ORDER_PRINTING
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
        contentPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        contentPanel.setBackground(DeckersPalette.background());
        logoPathField.setEditable(false);
        CompanyIdentityPanel identityPanel = new CompanyIdentityPanel(
                companyNameField,
                companyMottoLine1Field,
                companyMottoLine2Field,
                buildLogoFilePanel()
        );
        JPanel cardColumn = new JPanel();
        cardColumn.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        cardColumn.setBackground(DeckersPalette.background());
        cardColumn.setLayout(new BoxLayout(cardColumn, BoxLayout.Y_AXIS));
        identityPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        identityPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, identityPanel.getPreferredSize().height));
        cardColumn.add(identityPanel);
        cardColumn.add(Box.createVerticalGlue());
        JScrollPane scrollPane = new JScrollPane(cardColumn);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        return contentPanel;
    }

    private JPanel buildSaleReceiptPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        JPanel inventoryRules = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        inventoryRules.setOpaque(false);
        requireCostPriceOnNewItemBox.setToolTipText("When disabled, a blank cost price is saved as $0.00.");
        inventoryRules.add(requireCostPriceOnNewItemBox);
        contentPanel.add(inventoryRules, BorderLayout.NORTH);
        contentPanel.add(buildReceiptFormattingPanel(), BorderLayout.CENTER);
        contentPanel.add(buildSamplePreviewPanel(), BorderLayout.EAST);
        return contentPanel;
    }

    private JPanel buildAccountPaymentReceiptPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildAccountPaymentReceiptPanel(), BorderLayout.CENTER);
        contentPanel.add(buildAccountPaymentReceiptPreviewPanel(), BorderLayout.EAST);
        return contentPanel;
    }

    private JPanel buildEmployeeBadgesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        badgeLogoPathField.setEditable(false);
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildBadgeSecurityPanel());
        top.add(buildBadgeEditorLaunchPanel());
        top.add(buildCustomerCardEditorLaunchPanel());
        contentPanel.add(top, BorderLayout.NORTH);
        contentPanel.add(buildBadgeMagStripePanel(), BorderLayout.CENTER);
        return contentPanel;
    }

    private JPanel buildCustomerCardEditorLaunchPanel() {
        JPanel panel=new JPanel(new BorderLayout(12,6));panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(229,231,235)),new EmptyBorder(14,14,14,14)));
        JLabel text=new JLabel("<html><b>Customer Card Templates</b><br>Edit the five landscape CR80 customer-card slots used by Customer Accounts.</html>");
        JButton button=new JButton("Open Customer Card Template Editor");button.addActionListener(e->openCustomerCardTemplateEditor());
        panel.add(text,BorderLayout.CENTER);panel.add(button,BorderLayout.EAST);return panel;
    }

    private void openCustomerCardTemplateEditor() {
        List<CustomerCardService.Template> templates=new ArrayList<>(CustomerCardService.load());
        JDialog dialog=new JDialog(this,"Customer Card Template Editor",true);dialog.setSize(980,720);dialog.setLocationRelativeTo(this);
        JComboBox<String> slot=new JComboBox<>();for(int i=0;i<templates.size();i++)slot.addItem((i+1)+" — "+templates.get(i).name());
        JTextField name=new JTextField();JCheckBox configured=new JCheckBox("Template configured and available for printing");
        JButton background=new JButton("Background Color"),header=new JButton("Header Color"),image=new JButton("Background Image"),clearImage=new JButton("Clear Image");final Color[] chosen={Color.WHITE},headerChosen={new Color(255,112,0)};final String[] backgroundImage={""};
        CustomerCardLayoutCanvas preview=new CustomerCardLayoutCanvas();
        Runnable load=()->{CustomerCardService.Template t=templates.get(slot.getSelectedIndex());name.setText(t.name());configured.setSelected(t.configured());var c=t.background();chosen[0]=new Color(c.red(),c.green(),c.blue());var h=t.header()==null?new CustomerCardService.ColorData(255,112,0):t.header();headerChosen[0]=new Color(h.red(),h.green(),h.blue());backgroundImage[0]=t.backgroundImage()==null?"":t.backgroundImage();background.setBackground(chosen[0]);header.setBackground(headerChosen[0]);refreshCustomerCardEditorPreview(preview,t);};
        Runnable apply=()->{int i=slot.getSelectedIndex();Color c=chosen[0],h=headerChosen[0];CustomerCardService.Template t=new CustomerCardService.Template(name.getText().trim(),configured.isSelected(),new CustomerCardService.ColorData(c.getRed(),c.getGreen(),c.getBlue()),new CustomerCardService.ColorData(h.getRed(),h.getGreen(),h.getBlue()),backgroundImage[0],preview.layoutData());templates.set(i,t);slot.insertItemAt((i+1)+" — "+t.name(),i);slot.removeItemAt(i+1);slot.setSelectedIndex(i);refreshCustomerCardEditorPreview(preview,t);};
        JPanel toolbar=new JPanel(new GridLayout(3,3,8,8));toolbar.add(new JLabel("Template slot:"));toolbar.add(slot);toolbar.add(configured);toolbar.add(new JLabel("Template name:"));toolbar.add(name);toolbar.add(background);toolbar.add(header);toolbar.add(image);toolbar.add(clearImage);
        JButton previewButton=new JButton("Preview Changes"),reset=new JButton("Reset Defaults"),save=new JButton("Save All Templates"),close=new JButton("Close");
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(previewButton);actions.add(reset);actions.add(save);actions.add(close);
        background.addActionListener(e->{Color c=JColorChooser.showDialog(dialog,"Customer Card Background",chosen[0]);if(c!=null){chosen[0]=c;background.setBackground(c);}});
        header.addActionListener(e->{Color c=JColorChooser.showDialog(dialog,"Customer Card Header",headerChosen[0]);if(c!=null){headerChosen[0]=c;header.setBackground(c);}});
        image.addActionListener(e->{JFileChooser chooser=new JFileChooser();if(chooser.showOpenDialog(dialog)==JFileChooser.APPROVE_OPTION)try{byte[] bytes=Files.readAllBytes(chooser.getSelectedFile().toPath());if(bytes.length>5_000_000)throw new IllegalArgumentException("Background image must be 5 MB or smaller.");backgroundImage[0]=Base64.getEncoder().encodeToString(bytes);apply.run();}catch(Exception ex){JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Background Image",JOptionPane.ERROR_MESSAGE);}});
        clearImage.addActionListener(e->{backgroundImage[0]="";apply.run();});
        previewButton.addActionListener(e->apply.run());slot.addActionListener(e->load.run());
        reset.addActionListener(e->{templates.clear();templates.addAll(CustomerCardService.defaults());load.run();});
        save.addActionListener(e->{try{apply.run();CustomerCardService.save(templates);JOptionPane.showMessageDialog(dialog,"All five customer-card templates were saved.");}catch(Exception ex){JOptionPane.showMessageDialog(dialog,ex.getMessage(),"Save Failed",JOptionPane.ERROR_MESSAGE);}});
        close.addActionListener(e->dialog.dispose());
        JLabel help=new JLabel("Click an element and drag to move it. Drag the orange bottom-right handle to resize it.");
        JPanel previewArea=new JPanel(new BorderLayout(0,6));previewArea.add(help,BorderLayout.NORTH);previewArea.add(preview,BorderLayout.CENTER);
        JPanel body=new JPanel(new BorderLayout(12,12));body.setBorder(new EmptyBorder(14,14,14,14));body.add(toolbar,BorderLayout.NORTH);body.add(previewArea,BorderLayout.CENTER);body.add(actions,BorderLayout.SOUTH);dialog.setContentPane(body);load.run();dialog.setVisible(true);
    }

    private void refreshCustomerCardEditorPreview(CustomerCardLayoutCanvas preview,CustomerCardService.Template template) {
        preview.setTemplate(template);
    }

    private static final class CustomerCardLayoutCanvas extends JPanel {
        private static final int PREVIEW_WIDTH=810,PREVIEW_HEIGHT=510,HANDLE=12;
        private final CustomerCardService.CardData sample=new CustomerCardService.CardData(0,"Sample Customer","Customer Type","CUST-00001","592-555-0100","customer@example.com",2020,1);
        private CustomerCardService.Template template;private String selected;private Point press;private Rectangle start;private boolean resizing;
        CustomerCardLayoutCanvas(){setPreferredSize(new Dimension(PREVIEW_WIDTH,PREVIEW_HEIGHT));setMinimumSize(new Dimension(640,400));setBackground(new Color(226,232,240));
            MouseAdapter mouse=new MouseAdapter(){public void mousePressed(MouseEvent e){if(template==null||!template.configured())return;selected=hit(e.getPoint());if(selected==null){repaint();return;}press=toCard(e.getPoint());start=new Rectangle(CustomerCardService.layoutRects(template.layoutData()).get(selected));Rectangle shown=toPreview(start);resizing=shown!=null&&e.getX()>=shown.x+shown.width-HANDLE&&e.getY()>=shown.y+shown.height-HANDLE;repaint();}
                public void mouseDragged(MouseEvent e){if(selected==null||press==null||start==null)return;Point p=toCard(e.getPoint());Rectangle updated=new Rectangle(start);if(resizing){updated.width+=p.x-press.x;updated.height+=p.y-press.y;}else{updated.x+=p.x-press.x;updated.y+=p.y-press.y;}String layout=CustomerCardService.updateLayoutRect(template.layoutData(),selected,updated);template=new CustomerCardService.Template(template.name(),template.configured(),template.background(),template.header(),template.backgroundImage(),layout);repaint();}
                public void mouseReleased(MouseEvent e){press=null;start=null;resizing=false;}};addMouseListener(mouse);addMouseMotionListener(mouse);}
        void setTemplate(CustomerCardService.Template value){template=value;selected=null;repaint();}
        String layoutData(){return template==null?"":template.layoutData();}
        private String hit(Point p){for(var e:CustomerCardService.layoutRects(template.layoutData()).entrySet())if(toPreview(e.getValue()).contains(p))return e.getKey();return null;}
        private Point toCard(Point p){double sx=getWidth()/(double)CustomerCardService.WIDTH,sy=getHeight()/(double)CustomerCardService.HEIGHT;return new Point((int)Math.round(p.x/sx),(int)Math.round(p.y/sy));}
        private Rectangle toPreview(Rectangle r){double sx=getWidth()/(double)CustomerCardService.WIDTH,sy=getHeight()/(double)CustomerCardService.HEIGHT;return new Rectangle((int)Math.round(r.x*sx),(int)Math.round(r.y*sy),Math.max(1,(int)Math.round(r.width*sx)),Math.max(1,(int)Math.round(r.height*sy)));}
        protected void paintComponent(Graphics g){super.paintComponent(g);if(template==null)return;if(!template.configured()){g.setColor(Color.DARK_GRAY);g.drawString("This slot is intentionally blank and cannot be printed.",24,40);return;}BufferedImage image=CustomerCardService.render(sample,template);g.drawImage(image,0,0,getWidth(),getHeight(),null);Graphics2D g2=(Graphics2D)g.create();for(var e:CustomerCardService.layoutRects(template.layoutData()).entrySet()){Rectangle r=toPreview(e.getValue());boolean active=e.getKey().equals(selected);g2.setColor(active?new Color(255,112,0):new Color(0,85,145));g2.setStroke(new BasicStroke(active?3:1));g2.draw(r);if(active)g2.fillRect(r.x+r.width-HANDLE,r.y+r.height-HANDLE,HANDLE,HANDLE);}g2.dispose();}
    }

    private JPanel buildBadgeSecurityPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                new EmptyBorder(14, 14, 14, 14)));
        JLabel title = new JLabel("Badge Login Security");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(title, BorderLayout.NORTH);
        panel.add(requireBadgePinLoginBox, BorderLayout.CENTER);
        JLabel warning = new JLabel("<html>When turned off, possession of an active badge is enough to log in and approve or override actions allowed by that employee's permissions.</html>");
        warning.setForeground(new Color(153, 27, 27));
        panel.add(warning, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPriceTagTemplateScreen() {
        JPanel panel = new JPanel(new BorderLayout(18, 18)); panel.setOpaque(false);
        JPanel controls = new JPanel(); controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS)); controls.setBorder(new EmptyBorder(16, 16, 16, 16));
        JLabel heading = new JLabel("Price Tag Sticker Template"); heading.setFont(new Font("SansSerif", Font.BOLD, 20)); controls.add(heading); controls.add(Box.createVerticalStrut(12));
        controls.add(new JLabel("Each template has its own size and is available in Price Tag Printing.")); controls.add(Box.createVerticalStrut(14));
        JPanel slot = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); slot.add(new JLabel("Template:")); slot.add(priceTagTemplateSlotBox); slot.add(new JLabel("Name:")); slot.add(priceTagTemplateNameField); controls.add(slot); controls.add(Box.createVerticalStrut(8));
        controls.add(priceTagShowCompanyBox); controls.add(priceTagShowNameBox); controls.add(priceTagShowPriceBox); controls.add(priceTagShowSkuBox); controls.add(priceTagShowBarcodeBox); controls.add(priceTagShowSizeBox); controls.add(priceTagShowDescriptionBox); controls.add(Box.createVerticalStrut(10));
        JPanel sizes = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0)); sizes.add(new JLabel("Sticker size (inches):")); sizes.add(priceTagWidthSpinner); sizes.add(new JLabel("wide ×")); sizes.add(priceTagHeightSpinner); sizes.add(new JLabel("high")); controls.add(sizes);
        JButton preview = new JButton("Refresh preview"); JButton editLayout = new JButton("Open Template Editor"); JButton save = new JButton("Save template"); JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10)); buttons.add(preview); buttons.add(editLayout); buttons.add(save); controls.add(buttons);
        priceTagTemplateSlotBox.addActionListener(e -> { savePriceTagFieldsToSlot(); activePriceTagTemplateSlot = priceTagTemplateSlotBox.getSelectedIndex(); loadPriceTagTemplateFields(); });
        preview.addActionListener(e -> refreshPriceTagPreview()); editLayout.addActionListener(e -> openPriceTagTemplateEditor()); save.addActionListener(e -> { try { savePriceTagFieldsToSlot(); CompanyCustomizationManager.savePriceTagTemplateSettings(priceTagTemplates); refreshPriceTagPreview(); JOptionPane.showMessageDialog(this, "All five price tag templates saved."); } catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "Price Tags", JOptionPane.ERROR_MESSAGE); } });
        panel.add(controls, BorderLayout.WEST); priceTagPreviewLabel.setHorizontalAlignment(SwingConstants.CENTER); panel.add(new JScrollPane(priceTagPreviewLabel), BorderLayout.CENTER); loadPriceTagTemplateFields(); return panel;
    }

    private CompanyCustomizationManager.PriceTagTemplateSettings priceTagSettingsFromFields() { String layout = priceTagTemplates.size() == 5 ? priceTagTemplates.get(activePriceTagTemplateSlot).layoutData() : ""; return new CompanyCustomizationManager.PriceTagTemplateSettings(priceTagTemplateNameField.getText(), priceTagShowCompanyBox.isSelected(), priceTagShowNameBox.isSelected(), priceTagShowPriceBox.isSelected(), priceTagShowSkuBox.isSelected(), priceTagShowBarcodeBox.isSelected(), priceTagShowSizeBox.isSelected(), priceTagShowDescriptionBox.isSelected(), ((Number) priceTagWidthSpinner.getValue()).doubleValue(), ((Number) priceTagHeightSpinner.getValue()).doubleValue(), layout); }
    private void savePriceTagFieldsToSlot() { if (priceTagTemplates.size() == 5) priceTagTemplates.set(activePriceTagTemplateSlot, priceTagSettingsFromFields()); }
    private void loadPriceTagTemplateFields() { if (priceTagTemplates.size() != 5) return; activePriceTagTemplateSlot = priceTagTemplateSlotBox.getSelectedIndex(); CompanyCustomizationManager.PriceTagTemplateSettings s = priceTagTemplates.get(activePriceTagTemplateSlot); priceTagTemplateNameField.setText(s.name()); priceTagShowCompanyBox.setSelected(s.showCompany()); priceTagShowNameBox.setSelected(s.showName()); priceTagShowPriceBox.setSelected(s.showPrice()); priceTagShowSkuBox.setSelected(s.showSku()); priceTagShowBarcodeBox.setSelected(s.showBarcode()); priceTagShowSizeBox.setSelected(s.showSize()); priceTagShowDescriptionBox.setSelected(s.showDescription()); priceTagWidthSpinner.setValue(s.widthInches()); priceTagHeightSpinner.setValue(s.heightInches()); refreshPriceTagPreview(); }
    private void refreshPriceTagPreview() { priceTagPreviewLabel.setIcon(new ImageIcon(PriceTagPrintService.render(new PriceTagPrintService.PriceTagItem("Sample Inventory Item", "Large", "A sample product description", "SKU-10025", "SKU-10025", java.math.BigDecimal.valueOf(2500)), priceTagSettingsFromFields()))); }
    private void openPriceTagTemplateEditor() {
        savePriceTagFieldsToSlot(); int slot = priceTagTemplateSlotBox.getSelectedIndex();
        JDialog dialog = new JDialog(this, "Price Tag Template Editor — " + priceTagTemplates.get(slot).name(), Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(10, 10)); dialog.getRootPane().setBorder(new EmptyBorder(14,14,14,14));
        PriceTagLayoutCanvas canvas = new PriceTagLayoutCanvas(priceTagTemplates.get(slot)); dialog.add(canvas, BorderLayout.CENTER);
        JButton reset = new JButton("Reset layout"); JButton done = new JButton("Done"); JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT)); actions.add(reset); actions.add(done); dialog.add(actions, BorderLayout.SOUTH);
        reset.addActionListener(e -> canvas.reset()); done.addActionListener(e -> { try { priceTagTemplates.set(slot, canvas.settings()); CompanyCustomizationManager.savePriceTagTemplateSettings(priceTagTemplates); refreshPriceTagPreview(); dialog.dispose(); } catch (Exception ex) { JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Price Tags", JOptionPane.ERROR_MESSAGE); } });
        dialog.pack(); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }
    private static final class PriceTagLayoutCanvas extends JPanel {
        private final CompanyCustomizationManager.PriceTagTemplateSettings settings; private final LinkedHashMap<String,Rectangle> rects; private String selected="name"; private Point press; private boolean resize;
        PriceTagLayoutCanvas(CompanyCustomizationManager.PriceTagTemplateSettings s){settings=s;rects=PriceTagPrintService.layoutRects(s.layoutData());rects.entrySet().removeIf(e->!visible(e.getKey()));setPreferredSize(new Dimension(1040,560));setBackground(new Color(242,244,248));addMouseListener(new java.awt.event.MouseAdapter(){public void mousePressed(java.awt.event.MouseEvent e){press=e.getPoint();selected=hit(e.getPoint());Rectangle r=rects.get(selected);resize=e.getX()>r.x+r.width-16&&e.getY()>r.y+r.height-16;repaint();}});addMouseMotionListener(new java.awt.event.MouseMotionAdapter(){public void mouseDragged(java.awt.event.MouseEvent e){Rectangle r=rects.get(selected);int dx=e.getX()-press.x,dy=e.getY()-press.y;if(resize){r.width=Math.max(35,r.width+dx);r.height=Math.max(25,r.height+dy);}else{r.x=Math.max(0,Math.min(1000-r.width,r.x+dx));r.y=Math.max(0,Math.min(500-r.height,r.y+dy));}press=e.getPoint();repaint();}});}
        private boolean visible(String id){return switch(id){case "company"->settings.showCompany();case "name"->settings.showName();case "price"->settings.showPrice();case "sku"->settings.showSku();case "barcode"->settings.showBarcode();case "size"->settings.showSize();case "description"->settings.showDescription();default->true;};}
        private String hit(Point p){for(var e:rects.entrySet())if(visible(e.getKey())&&e.getValue().contains(p))return e.getKey();return selected;} void reset(){rects.clear();rects.putAll(PriceTagPrintService.defaultLayout());repaint();}
        CompanyCustomizationManager.PriceTagTemplateSettings settings(){return new CompanyCustomizationManager.PriceTagTemplateSettings(settings.name(),settings.showCompany(),settings.showName(),settings.showPrice(),settings.showSku(),settings.showBarcode(),settings.showSize(),settings.showDescription(),settings.widthInches(),settings.heightInches(),PriceTagPrintService.encodeLayout(rects));}
        protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D g2=(Graphics2D)g;g2.setColor(Color.WHITE);g2.fillRect(0,0,1000,500);g2.setColor(new Color(235,238,244));for(int x=0;x<1000;x+=50)g2.drawLine(x,0,x,500);for(int y=0;y<500;y+=50)g2.drawLine(0,y,1000,y);g2.setColor(new Color(30,41,59));g2.setStroke(new BasicStroke(4));g2.drawRect(1,1,998,498);g2.setFont(new Font("SansSerif",Font.BOLD,14));g2.drawString(String.format("Printable label boundary — %.2f × %.2f in",settings.widthInches(),settings.heightInches()),16,22);for(var e:rects.entrySet()){Rectangle r=e.getValue();boolean on=e.getKey().equals(selected);g2.setColor(on?new Color(255,112,0,55):new Color(0,85,145,35));g2.fill(r);g2.setColor(on?new Color(255,112,0):new Color(0,85,145));g2.setStroke(new BasicStroke(on?3:2));g2.draw(r);g2.setColor(Color.BLACK);String sample=switch(e.getKey()){case "company"->"[ Company Logo ]";case "name"->"Sample Inventory Item";case "size"->"Large";case "description"->"A sample product description";case "price"->"$2,500";case "sku"->"SKU: 10025";default->"||| || ||| || |||| ||| ||";};g2.setFont(new Font("SansSerif",e.getKey().equals("price")?Font.BOLD:Font.PLAIN,Math.max(12,Math.min(32,r.height/2))));g2.drawString(sample,r.x+8,r.y+Math.min(r.height-8,g2.getFontMetrics().getAscent()+8));if(on)g2.fillRect(r.x+r.width-12,r.y+r.height-12,12,12);}g2.setColor(Color.DARK_GRAY);g2.drawString("Click an element; drag it to move. Drag its bottom-right handle to resize.",15,525);}
    }

    private JPanel buildBackupSchedulerScreen() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel title = new JLabel("Company Backups");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(new Color(32, 41, 57));

        backupDirectoryField.setEditable(false);
        backupStatusLabel.setForeground(new Color(55, 65, 81));
        backupStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addBackupCheckRow(form, 0, "Scheduler", backupSchedulerEnabledBox);
        addBackupDirectoryRow(form, 1);
        addBackupSpinnerRow(form, 2, "Run Every", backupIntervalMinutesSpinner, "minutes");
        addBackupSpinnerRow(form, 3, "Keep Last", backupRetentionCountSpinner, "backup files");
        addBackupLabelRow(form, 4, "Status", backupStatusLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        JButton refreshButton = new JButton("Refresh");
        JButton runNowButton = new JButton("Run Backup Now");
        JButton saveButton = new JButton("Save Schedule");
        buttons.add(refreshButton);
        buttons.add(runNowButton);
        buttons.add(saveButton);

        panel.add(title, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshButton.addActionListener(e -> loadSettings());
        saveButton.addActionListener(e -> saveBackupSchedulerSettings());
        runNowButton.addActionListener(e -> runScheduledBackupNow(runNowButton));
        return panel;
    }

    private JPanel buildTimeClockSafetyScreen() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(22, 22, 22, 22)
        ));

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Time Clock Safety");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        JLabel explanation = new JLabel("<html>Close forgotten punches safely while keeping every automatic result available for manager review.</html>");
        explanation.setForeground(new Color(75, 85, 99));
        explanation.setBorder(new EmptyBorder(6, 0, 0, 0));
        heading.add(title);
        heading.add(explanation);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        addBackupCheckRow(form, 0, "Status", timeClockAutoCloseEnabledBox);
        addBackupSpinnerRow(form, 1, "After scheduled shift", scheduledDetectionDelaySpinner, "hours after shift end");
        addBackupSpinnerRow(form, 2, "Without a schedule", unscheduledDetectionHoursSpinner, "elapsed hours after clock-in");
        addBackupSpinnerRow(form, 3, "Automatic work cap", maximumAutomaticWorkHoursSpinner, "worked hours, excluding lunch");

        JTextArea note = new JTextArea("Manual clock-outs preserve actual time and overtime. Automatic clock-outs affect payroll immediately and remain pending until a manager confirms or corrects them.");
        note.setEditable(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setOpaque(false);
        note.setForeground(new Color(75, 85, 99));
        note.setBorder(new EmptyBorder(8, 0, 0, 0));

        panel.add(heading, BorderLayout.NORTH);
        panel.add(form, BorderLayout.CENTER);
        panel.add(note, BorderLayout.SOUTH);
        return panel;
    }

    private void addBackupCheckRow(JPanel panel, int row, String label, JCheckBox checkBox) {
        addBackupLabel(panel, row, label);
        GridBagConstraints valueConstraints = backupValueConstraints(row);
        panel.add(checkBox, valueConstraints);
    }

    private void addBackupDirectoryRow(JPanel panel, int row) {
        addBackupLabel(panel, row, "Folder");
        JPanel chooserPanel = new JPanel(new BorderLayout(8, 0));
        chooserPanel.setOpaque(false);
        JButton browseButton = new JButton("Choose");
        chooserPanel.add(backupDirectoryField, BorderLayout.CENTER);
        chooserPanel.add(browseButton, BorderLayout.EAST);
        browseButton.addActionListener(e -> chooseBackupDirectory());
        panel.add(chooserPanel, backupValueConstraints(row));
    }

    private void addBackupSpinnerRow(JPanel panel, int row, String label, JSpinner spinner, String suffix) {
        addBackupLabel(panel, row, label);
        JPanel valuePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        valuePanel.setOpaque(false);
        spinner.setPreferredSize(new Dimension(110, spinner.getPreferredSize().height));
        JLabel suffixLabel = new JLabel(suffix);
        suffixLabel.setForeground(new Color(55, 65, 81));
        valuePanel.add(spinner);
        valuePanel.add(suffixLabel);
        panel.add(valuePanel, backupValueConstraints(row));
    }

    private void addBackupLabelRow(JPanel panel, int row, String label, JLabel valueLabel) {
        addBackupLabel(panel, row, label);
        panel.add(valueLabel, backupValueConstraints(row));
    }

    private void addBackupLabel(JPanel panel, int row, String labelText) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setForeground(new Color(55, 65, 81));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.insets = new Insets(0, 0, 12, 16);
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(label, constraints);
    }

    private GridBagConstraints backupValueConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 1;
        constraints.gridy = row;
        constraints.insets = new Insets(0, 0, 12, 0);
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        return constraints;
    }

    private JPanel buildBadgeEditorLaunchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 12));
        panel.setOpaque(false);
        JButton openEditorButton = new JButton("Open Badge Template Editor");
        openEditorButton.addActionListener(e -> openBadgeTemplateEditorDialog());
        panel.add(openEditorButton);
        JButton walletEditorButton = new JButton("Open Apple Wallet Template Editor");
        walletEditorButton.addActionListener(e -> new ui.screens.companyprefs.WalletTemplateEditor(
                this, getBadgeTemplateSettingsFromFields(), getSettingsFromFields()).setVisible(true));
        panel.add(walletEditorButton);
        return panel;
    }

    private JPanel buildBadgeMagStripePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                new EmptyBorder(14, 14, 14, 14)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 8, 0);

        JLabel title = new JLabel("Magnetic Stripe");
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(title, gbc);
        gbc.gridy++;
        panel.add(badgeMagStripeEnabledBox, gbc);
        gbc.gridy++;
        addFullWidthPreferenceField(panel, gbc, "Track 1 template:", badgeMagStripeTrack1Field);
        addFullWidthPreferenceField(panel, gbc, "Track 2 template:", badgeMagStripeTrack2Field);
        addFullWidthPreferenceField(panel, gbc, "Track 3 template:", badgeMagStripeTrack3Field);
        addFullWidthPreferenceField(panel, gbc, "Writer command:", badgeMagStripeCommandField);

        JTextArea help = new JTextArea("Placeholders: {badge_id}, {employee_id}, {full_name}, {first_name}, {last_name}, {role}, {company}, {issued_date}, {track1}, {track2}, {track3}.");
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setOpaque(false);
        help.setForeground(new Color(75, 85, 99));
        help.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.insets = new Insets(4, 0, 0, 0);
        panel.add(help, gbc);
        gbc.gridy++;

        gbc.insets = new Insets(16, 0, 8, 0);
        JLabel nfcTitle = new JLabel("RFID / NFC");
        nfcTitle.setFont(new Font("SansSerif", Font.BOLD, 16));
        panel.add(nfcTitle, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(badgeNfcEnabledBox, gbc);
        gbc.gridy++;
        addFullWidthPreferenceField(panel, gbc, "Payload template:", badgeNfcPayloadField);
        addFullWidthPreferenceField(panel, gbc, "Writer command:", badgeNfcWriterCommandField);
        addFullWidthPreferenceField(panel, gbc, "Verification command (optional):", badgeNfcVerifyCommandField);

        JTextArea nfcHelp = new JTextArea(
                "RFID/NFC placeholders: {badge_id}, {employee_id}, {full_name}, {first_name}, "
                        + "{last_name}, {role}, {company}, and {payload}. The payload defaults to {badge_id}.");
        nfcHelp.setEditable(false);
        nfcHelp.setLineWrap(true);
        nfcHelp.setWrapStyleWord(true);
        nfcHelp.setOpaque(false);
        nfcHelp.setForeground(new Color(75, 85, 99));
        nfcHelp.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.insets = new Insets(4, 0, 0, 0);
        panel.add(nfcHelp, gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);
        return panel;
    }

    private void addFullWidthPreferenceField(JPanel panel, GridBagConstraints gbc, String labelText, JComponent field) {
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        gbc.insets = new Insets(8, 0, 2, 0);
        panel.add(label, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 4, 0);
        panel.add(field, gbc);
        gbc.gridy++;
    }

    private void openBadgeTemplateEditorDialog() {
        JDialog dialog = new JDialog(this, "Badge Template Editor", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(true);
        dialog.setLayout(new BorderLayout(12, 12));
        dialog.getRootPane().setBorder(new EmptyBorder(14, 14, 14, 14));
        dialog.add(buildBadgePreviewPanel(), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton resetLayoutButton = new JButton("Reset Layout");
        JButton closeButton = new JButton("Close");
        resetLayoutButton.addActionListener(e -> {
            badgeLayoutData = BadgePrintService.resetLayout();
            updateBadgeFontControls();
            refreshBadgeElementVisibilityControls();
            refreshBadgePreview();
        });
        closeButton.addActionListener(e -> dialog.dispose());
        buttons.add(resetLayoutButton);
        buttons.add(closeButton);
        dialog.add(buttons, BorderLayout.SOUTH);

        refreshBadgePreview();
        saveButton.setEnabled(canEditCompanyPreferences());
        dialog.pack();
        Rectangle available = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setMinimumSize(new Dimension(Math.min(900, available.width), Math.min(620, available.height)));
        dialog.setSize(new Dimension(
                Math.min(1180, Math.max(760, available.width - 80)),
                Math.min(900, Math.max(620, available.height - 80))));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel buildBadgePreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(1080, 760));
        panel.setMinimumSize(new Dimension(720, 520));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sectionLabel = new JLabel("Badge Template Editor");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        JPanel headerPanel = new JPanel(new BorderLayout(12, 8));
        headerPanel.setOpaque(false);
        headerPanel.add(sectionLabel, BorderLayout.NORTH);
        JScrollPane toolbarScrollPane = new JScrollPane(buildBadgeGroupAlignmentToolbar(),
                ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        toolbarScrollPane.setBorder(BorderFactory.createEmptyBorder());
        toolbarScrollPane.setOpaque(false);
        toolbarScrollPane.getViewport().setOpaque(false);
        headerPanel.add(toolbarScrollPane, BorderLayout.SOUTH);
        panel.add(headerPanel, BorderLayout.NORTH);

        JTabbedPane previewTabs = new JTabbedPane();
        sampleBadgeFrontPanel = new BadgeTemplateEditorPanel("front", 390);
        sampleBadgeBackPanel = new BadgeTemplateEditorPanel("back", 390);
        previewTabs.addTab("Front", new JScrollPane(sampleBadgeFrontPanel));
        previewTabs.addTab("Back", new JScrollPane(sampleBadgeBackPanel));
        JPanel editorBody = new JPanel(new BorderLayout(12, 0));
        editorBody.setOpaque(false);
        editorBody.add(buildBadgeElementVisibilityPanel(), BorderLayout.WEST);
        editorBody.add(previewTabs, BorderLayout.CENTER);
        JScrollPane settingsScrollPane = new JScrollPane(buildBadgeFontControlPanel());
        settingsScrollPane.setBorder(BorderFactory.createLineBorder(new Color(229, 231, 235)));
        settingsScrollPane.setPreferredSize(new Dimension(250, 0));
        settingsScrollPane.setBackground(Color.WHITE);
        settingsScrollPane.getViewport().setBackground(Color.WHITE);
        settingsScrollPane.getViewport().setOpaque(true);
        settingsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        settingsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        settingsScrollPane.getVerticalScrollBar().setUnitIncrement(14);
        editorBody.add(settingsScrollPane, BorderLayout.EAST);
        panel.add(editorBody, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBadgeGroupAlignmentToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.setOpaque(false);
        JLabel templateLabel = new JLabel("Template:");
        templateLabel.setForeground(new Color(17, 24, 39));
        templateLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        badgeTemplateSlotBox.setPrototypeDisplayValue("Template 4");
        for (java.awt.event.ActionListener listener : badgeTemplateSlotBox.getActionListeners()) {
            badgeTemplateSlotBox.removeActionListener(listener);
        }
        badgeTemplateSlotBox.addActionListener(e -> switchBadgeTemplateSlot(badgeTemplateSlotBox.getSelectedIndex()));
        toolbar.add(templateLabel);
        toolbar.add(badgeTemplateSlotBox);
        JLabel label = new JLabel("Align selected:");
        label.setForeground(new Color(17, 24, 39));
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton centerHButton = new JButton("Center H");
        JButton centerVButton = new JButton("Center V");
        JButton centerBothButton = new JButton("Center Both");
        centerHButton.addActionListener(e -> centerSelectedBadgeElements(true, false));
        centerVButton.addActionListener(e -> centerSelectedBadgeElements(false, true));
        centerBothButton.addActionListener(e -> centerSelectedBadgeElements(true, true));
        toolbar.add(label);
        toolbar.add(centerHButton);
        toolbar.add(centerVButton);
        toolbar.add(centerBothButton);
        JLabel layerLabel = new JLabel("  Layer:");
        layerLabel.setForeground(new Color(17, 24, 39));
        layerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        JButton sendBackButton = new JButton("Send Back");
        JButton backwardButton = new JButton("Backward");
        JButton forwardButton = new JButton("Forward");
        JButton bringFrontButton = new JButton("Bring Front");
        sendBackButton.addActionListener(e -> moveSelectedBadgeLayers("back"));
        backwardButton.addActionListener(e -> moveSelectedBadgeLayers("backward"));
        forwardButton.addActionListener(e -> moveSelectedBadgeLayers("forward"));
        bringFrontButton.addActionListener(e -> moveSelectedBadgeLayers("front"));
        toolbar.add(layerLabel);
        toolbar.add(sendBackButton);
        toolbar.add(backwardButton);
        toolbar.add(forwardButton);
        toolbar.add(bringFrontButton);
        JLabel magStripeLabel = new JLabel("  Mag strip:");
        magStripeLabel.setForeground(new Color(17, 24, 39));
        magStripeLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        toolbar.add(magStripeLabel);
        toolbar.add(buildBadgeMagStripeTogglePanel());
        return toolbar;
    }

    private JPanel buildBadgeMagStripeTogglePanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 0, 0));
        panel.setOpaque(false);
        JToggleButton onButton = new JToggleButton("On");
        JToggleButton offButton = new JToggleButton("Off");
        ButtonGroup group = new ButtonGroup();
        group.add(onButton);
        group.add(offButton);
        onButton.setSelected(badgeMagStripeEnabledBox.isSelected());
        offButton.setSelected(!badgeMagStripeEnabledBox.isSelected());

        ActionListener listener = e -> {
            boolean enabled = onButton.isSelected();
            if (badgeMagStripeEnabledBox.isSelected() != enabled) {
                badgeMagStripeEnabledBox.setSelected(enabled);
            }
            styleBadgeMagStripeToggle(onButton, onButton.isSelected());
            styleBadgeMagStripeToggle(offButton, offButton.isSelected());
            refreshBadgePreview();
        };
        onButton.addActionListener(listener);
        offButton.addActionListener(listener);
        styleBadgeMagStripeToggle(onButton, onButton.isSelected());
        styleBadgeMagStripeToggle(offButton, offButton.isSelected());
        panel.add(onButton);
        panel.add(offButton);
        return panel;
    }

    private static void styleBadgeMagStripeToggle(JToggleButton button, boolean selected) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selected ? new Color(37, 99, 235) : new Color(203, 213, 225)),
                new EmptyBorder(4, 10, 4, 10)
        ));
        button.setBackground(selected ? new Color(37, 99, 235) : Color.WHITE);
        button.setForeground(selected ? Color.WHITE : new Color(31, 41, 55));
    }

    private JPanel buildBadgeElementVisibilityPanel() {
        badgeElementVisibilityBoxes.clear();
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        panel.setPreferredSize(new Dimension(170, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel title = new JLabel("Elements");
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        title.setForeground(new Color(17, 24, 39));
        panel.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel(new GridBagLayout());
        list.setOpaque(true);
        list.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 0, 2, 0);
        int row = 0;
        row = addBadgeVisibilityGroup(list, gbc, row, "Front", "front");
        row = addBadgeVisibilityGroup(list, gbc, row, "Back", "back");
        gbc.gridy = row;
        gbc.weighty = 1;
        list.add(Box.createVerticalGlue(), gbc);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(true);
        scrollPane.setBackground(Color.WHITE);
        scrollPane.getViewport().setOpaque(true);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scrollPane, BorderLayout.CENTER);
        refreshBadgeElementVisibilityControls();
        return panel;
    }

    private int addBadgeVisibilityGroup(JPanel list, GridBagConstraints gbc, int row, String title, String side) {
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(new Color(75, 85, 99));
        gbc.gridy = row++;
        gbc.insets = new Insets(row == 1 ? 0 : 10, 0, 4, 0);
        list.add(label, gbc);
        gbc.insets = new Insets(1, 0, 1, 0);
        for (BadgePrintService.BadgeElement element : BadgePrintService.elementsForSide(side)) {
            JCheckBox box = new JCheckBox(element.label());
            box.setOpaque(false);
            box.setForeground(new Color(31, 41, 55));
            box.addActionListener(e -> {
                badgeLayoutData = BadgePrintService.updateElementVisible(badgeLayoutData, element.id(), box.isSelected());
                if (!box.isSelected()) {
                    badgeSelectedTemplateElementIds.remove(element.id());
                    if (element.id().equals(badgeSelectedTemplateElementId)) {
                        badgeSelectedTemplateElementId = badgeSelectedTemplateElementIds.stream().reduce((first, second) -> second).orElse("");
                    }
                    updateBadgeFontControls();
                }
                syncLegacyBadgeVisibilityFields();
                refreshBadgePreview();
            });
            badgeElementVisibilityBoxes.put(element.id(), box);
            gbc.gridy = row++;
            list.add(box, gbc);
        }
        return row;
    }

    private JPanel buildBadgeFontControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setPreferredSize(new Dimension(230, 1040));
        panel.setMinimumSize(new Dimension(230, 1040));
        panel.setOpaque(true);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(229, 231, 235)),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JLabel title = new JLabel("Element Settings");
        title.setForeground(new Color(17, 24, 39));
        title.setFont(new Font("SansSerif", Font.BOLD, 15));
        badgeSelectedElementLabel.setForeground(new Color(75, 85, 99));
        badgeSelectedElementLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        badgeFontFamilyBox.setMaximumRowCount(12);
        badgeFontFamilyBox.setPrototypeDisplayValue("SansSerif Wide");
        ((JSpinner.DefaultEditor) badgeFontMaxSizeSpinner.getEditor()).getTextField().setColumns(4);
        badgeSignatureImageField.setEditable(false);
        badgeElementImageField.setEditable(false);
        badgeImageOpacitySlider.setPaintTicks(false);
        badgeImageOpacitySlider.setPaintLabels(false);
        badgeElementColorSwatch.setOpaque(true);
        badgeElementColorSwatch.setPreferredSize(new Dimension(32, 24));
        badgeElementColorSwatch.setBorder(BorderFactory.createLineBorder(new Color(156, 163, 175)));
        badgeTextOutlineColorSwatch.setOpaque(true);
        badgeTextOutlineColorSwatch.setPreferredSize(new Dimension(32, 24));
        badgeTextOutlineColorSwatch.setBorder(BorderFactory.createLineBorder(new Color(156, 163, 175)));
        badgeTextBoxOutlineColorSwatch.setOpaque(true);
        badgeTextBoxOutlineColorSwatch.setPreferredSize(new Dimension(32, 24));
        badgeTextBoxOutlineColorSwatch.setBorder(BorderFactory.createLineBorder(new Color(156, 163, 175)));
        configureCompactSpinner(badgeElementXSpinner);
        configureCompactSpinner(badgeElementYSpinner);
        configureCompactSpinner(badgeElementWidthSpinner);
        configureCompactSpinner(badgeElementHeightSpinner);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 6, 0);
        panel.add(title, gbc);
        gbc.gridy++;
        panel.add(badgeSelectedElementLabel, gbc);
        gbc.gridy++;
        JPanel exactPanel = new JPanel(new GridLayout(2, 4, 4, 4));
        exactPanel.setOpaque(false);
        exactPanel.add(new JLabel("X"));
        exactPanel.add(new JLabel("Y"));
        exactPanel.add(new JLabel("W"));
        exactPanel.add(new JLabel("H"));
        exactPanel.add(badgeElementXSpinner);
        exactPanel.add(badgeElementYSpinner);
        exactPanel.add(badgeElementWidthSpinner);
        exactPanel.add(badgeElementHeightSpinner);
        gbc.insets = new Insets(10, 0, 4, 0);
        panel.add(new JLabel("Exact size"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(exactPanel, gbc);
        gbc.gridy++;
        JPanel alignGroupPanel = new JPanel(new GridLayout(1, 0, 4, 0));
        alignGroupPanel.setOpaque(false);
        alignGroupPanel.add(badgeAlignGroupCenterHButton);
        alignGroupPanel.add(badgeAlignGroupCenterVButton);
        alignGroupPanel.add(badgeAlignGroupCenterBothButton);
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Align selected"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(alignGroupPanel, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 4, 0);
        panel.add(new JLabel("Custom text"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(badgeCustomTextField, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Element image"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 4, 0);
        panel.add(badgeElementImageField, gbc);
        gbc.gridy++;
        JPanel imageButtons = new JPanel(new GridLayout(1, 0, 4, 0));
        imageButtons.setOpaque(false);
        JButton uploadImageButton = new JButton("Upload");
        JButton selectImageButton = new JButton("Select");
        JButton clearImageButton = new JButton("Clear");
        imageButtons.add(uploadImageButton);
        imageButtons.add(selectImageButton);
        imageButtons.add(clearImageButton);
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(imageButtons, gbc);
        gbc.gridy++;
        JPanel opacityPanel = new JPanel(new BorderLayout(6, 0));
        opacityPanel.setOpaque(false);
        opacityPanel.add(badgeImageOpacitySlider, BorderLayout.CENTER);
        opacityPanel.add(badgeImageOpacityValueLabel, BorderLayout.EAST);
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Image opacity"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(opacityPanel, gbc);
        gbc.gridy++;
        JPanel colorPanel = new JPanel(new BorderLayout(6, 0));
        colorPanel.setOpaque(false);
        colorPanel.add(badgeElementColorSwatch, BorderLayout.WEST);
        colorPanel.add(badgeElementColorButton, BorderLayout.CENTER);
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Color"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(colorPanel, gbc);
        gbc.gridy++;
        JPanel textOptionsPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        textOptionsPanel.setOpaque(false);
        textOptionsPanel.add(badgeTextAllCapsBox);
        textOptionsPanel.add(badgeTextOutlineBox);
        textOptionsPanel.add(badgeTextBoxOutlineBox);
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Text options"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(textOptionsPanel, gbc);
        gbc.gridy++;
        JPanel textOutlineColorPanel = new JPanel(new BorderLayout(6, 0));
        textOutlineColorPanel.setOpaque(false);
        textOutlineColorPanel.add(badgeTextOutlineColorSwatch, BorderLayout.WEST);
        textOutlineColorPanel.add(badgeTextOutlineColorButton, BorderLayout.CENTER);
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Text outline color"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(textOutlineColorPanel, gbc);
        gbc.gridy++;
        JPanel boxOutlineColorPanel = new JPanel(new BorderLayout(6, 0));
        boxOutlineColorPanel.setOpaque(false);
        boxOutlineColorPanel.add(badgeTextBoxOutlineColorSwatch, BorderLayout.WEST);
        boxOutlineColorPanel.add(badgeTextBoxOutlineColorButton, BorderLayout.CENTER);
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Box outline color"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(boxOutlineColorPanel, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Name layout"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(badgeNameLayoutBox, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(12, 0, 4, 0);
        panel.add(new JLabel("Family"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(badgeFontFamilyBox, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Style"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(badgeFontStyleBox, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Weight"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 8, 0);
        panel.add(badgeFontWeightBox, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Max size"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(badgeFontMaxSizeSpinner, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Alignment"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(badgeTextAlignmentBox, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 0, 4, 0);
        panel.add(new JLabel("Rotation"), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(badgeElementRotationBox, gbc);
        gbc.gridy++;
        JTextArea help = new JTextArea("Text auto-fits to the selected box. Rotation works for selected elements at 0, 90, 180, or 270 degrees.");
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setOpaque(false);
        help.setForeground(new Color(75, 85, 99));
        help.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(help, gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        panel.add(Box.createVerticalGlue(), gbc);

        badgeFontFamilyBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeFontStyleBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeFontWeightBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeFontMaxSizeSpinner.addChangeListener(e -> applySelectedBadgeTextStyle());
        badgeTextAlignmentBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeTextAllCapsBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeTextOutlineBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeTextBoxOutlineBox.addActionListener(e -> applySelectedBadgeTextStyle());
        badgeElementRotationBox.addActionListener(e -> applySelectedBadgeElementRotation());
        badgeElementXSpinner.addChangeListener(e -> applySelectedBadgeElementBounds());
        badgeElementYSpinner.addChangeListener(e -> applySelectedBadgeElementBounds());
        badgeElementWidthSpinner.addChangeListener(e -> applySelectedBadgeElementBounds());
        badgeElementHeightSpinner.addChangeListener(e -> applySelectedBadgeElementBounds());
        badgeAlignGroupCenterHButton.addActionListener(e -> centerSelectedBadgeElements(true, false));
        badgeAlignGroupCenterVButton.addActionListener(e -> centerSelectedBadgeElements(false, true));
        badgeAlignGroupCenterBothButton.addActionListener(e -> centerSelectedBadgeElements(true, true));
        badgeCustomTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applySelectedBadgeCustomText();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applySelectedBadgeCustomText();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applySelectedBadgeCustomText();
            }
        });
        badgeImageOpacitySlider.addChangeListener(e -> applySelectedBadgeImageOpacity());
        badgeElementColorButton.addActionListener(e -> chooseBadgeElementColor());
        badgeTextOutlineColorButton.addActionListener(e -> chooseBadgeTextOutlineColor());
        badgeTextBoxOutlineColorButton.addActionListener(e -> chooseBadgeTextBoxOutlineColor());
        badgeNameLayoutBox.addActionListener(e -> applySelectedBadgeNameLayout());
        uploadImageButton.addActionListener(e -> uploadBadgeElementImage());
        selectImageButton.addActionListener(e -> selectBadgeElementImage());
        clearImageButton.addActionListener(e -> clearBadgeElementImage());
        styleBadgeEditorControls(panel);
        updateBadgeFontControls();
        return panel;
    }

    private static void styleBadgeEditorControls(Component component) {
        Color labelColor = new Color(31, 41, 55);
        Color mutedColor = new Color(75, 85, 99);
        if (component instanceof JLabel label) {
            label.setForeground(label.getFont().isBold() ? new Color(17, 24, 39) : labelColor);
        } else if (component instanceof JCheckBox checkBox) {
            checkBox.setForeground(labelColor);
            checkBox.setOpaque(false);
        } else if (component instanceof JTextArea textArea) {
            textArea.setForeground(mutedColor);
            textArea.setBackground(Color.WHITE);
        } else if (component instanceof JPanel panel) {
            if (panel.isOpaque()) {
                panel.setBackground(Color.WHITE);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                styleBadgeEditorControls(child);
            }
        }
    }

    private void updateBadgeFontControls() {
        updatingBadgeFontControls = true;
        try {
            boolean textElement = BadgePrintService.isTextElement(badgeSelectedTemplateElementId);
            boolean selectedElement = BadgePrintService.elementForId(badgeSelectedTemplateElementId) != null;
            boolean editableTextElement = isBadgeSelectedTextEditable();
            boolean opacityElement = BadgePrintService.isImageElement(badgeSelectedTemplateElementId);
            boolean imageElement = opacityElement || "front.logo".equals(badgeSelectedTemplateElementId);
            boolean colorElement = BadgePrintService.isColorElement(badgeSelectedTemplateElementId);
            boolean nameElement = "front.name".equals(badgeSelectedTemplateElementId);
            badgeFontFamilyBox.setEnabled(textElement);
            badgeFontStyleBox.setEnabled(textElement);
            badgeFontWeightBox.setEnabled(textElement);
            badgeFontMaxSizeSpinner.setEnabled(textElement);
            badgeTextAlignmentBox.setEnabled(textElement);
            badgeTextAllCapsBox.setEnabled(textElement);
            badgeTextOutlineBox.setEnabled(textElement);
            badgeTextOutlineColorButton.setEnabled(textElement);
            badgeTextBoxOutlineBox.setEnabled(textElement);
            badgeTextBoxOutlineColorButton.setEnabled(textElement);
            badgeElementRotationBox.setEnabled(selectedElement);
            badgeCustomTextField.setEnabled(editableTextElement);
            badgeElementImageField.setEnabled(imageElement);
            badgeImageOpacitySlider.setEnabled(opacityElement);
            badgeElementColorButton.setEnabled(colorElement);
            badgeNameLayoutBox.setEnabled(nameElement);
            badgeElementXSpinner.setEnabled(selectedElement);
            badgeElementYSpinner.setEnabled(selectedElement);
            badgeElementWidthSpinner.setEnabled(selectedElement);
            badgeElementHeightSpinner.setEnabled(selectedElement);
            badgeAlignGroupCenterHButton.setEnabled(selectedElement);
            badgeAlignGroupCenterVButton.setEnabled(selectedElement);
            badgeAlignGroupCenterBothButton.setEnabled(selectedElement);
            if (!selectedElement) {
                badgeSelectedElementLabel.setText("Select an element");
                badgeCustomTextField.setText("");
                badgeSignatureImageField.setText("");
                badgeElementImageField.setText("");
                badgeImageOpacitySlider.setValue(100);
                badgeImageOpacityValueLabel.setText("100%");
                badgeElementColorSwatch.setBackground(new Color(31, 41, 55));
                badgeTextAllCapsBox.setSelected(false);
                badgeTextOutlineBox.setSelected(false);
                badgeTextOutlineColorSwatch.setBackground(Color.BLACK);
                badgeTextBoxOutlineBox.setSelected(false);
                badgeTextBoxOutlineColorSwatch.setBackground(new Color(17, 24, 39));
                badgeNameLayoutBox.setSelectedItem("One line");
                badgeElementXSpinner.setValue(0);
                badgeElementYSpinner.setValue(0);
                badgeElementWidthSpinner.setValue(100);
                badgeElementHeightSpinner.setValue(40);
                return;
            }
            CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
            BadgePrintService.BadgeElement element = BadgePrintService.elementForId(badgeSelectedTemplateElementId);
            int selectedCount = badgeSelectedTemplateElementIds.size();
            String label = element == null ? badgeSelectedTemplateElementId : element.label();
            badgeSelectedElementLabel.setText(selectedCount > 1 ? selectedCount + " selected - " + label : label);
            Rectangle rect = BadgePrintService.layoutRect(settings, badgeSelectedTemplateElementId);
            badgeElementXSpinner.setValue(rect.x);
            badgeElementYSpinner.setValue(rect.y);
            badgeElementWidthSpinner.setValue(rect.width);
            badgeElementHeightSpinner.setValue(rect.height);
            badgeElementRotationBox.setSelectedItem(String.valueOf(BadgePrintService.elementRotation(settings, badgeSelectedTemplateElementId)));
            badgeCustomTextField.setText(editableTextElement ? selectedBadgeTextValue(settings) : "");
            String imagePath = "front.logo".equals(badgeSelectedTemplateElementId)
                    ? badgeLogoPathField.getText()
                    : imageElement ? BadgePrintService.elementImagePath(settings, badgeSelectedTemplateElementId) : "";
            badgeSignatureImageField.setText("back.signature".equals(badgeSelectedTemplateElementId) ? imagePath : "");
            badgeElementImageField.setText(imagePath);
            int opacityPercent = Math.round(BadgePrintService.elementOpacity(settings, badgeSelectedTemplateElementId) * 100f);
            badgeImageOpacitySlider.setValue(opacityPercent);
            badgeImageOpacityValueLabel.setText(opacityPercent + "%");
            badgeElementColorSwatch.setBackground(BadgePrintService.elementColor(settings, badgeSelectedTemplateElementId));
            badgeNameLayoutBox.setSelectedItem("two".equals(BadgePrintService.nameLayout(settings)) ? "Two lines" : "One line");
            if (textElement) {
                BadgePrintService.BadgeTextStyle style = BadgePrintService.textStyle(settings, badgeSelectedTemplateElementId);
                badgeFontFamilyBox.setSelectedItem(style.family());
                badgeFontStyleBox.setSelectedItem(fontStyleLabel(style.style()));
                badgeFontWeightBox.setSelectedItem(fontWeightLabel(style.weight()));
                badgeFontMaxSizeSpinner.setValue(style.maxSize());
                badgeTextAlignmentBox.setSelectedItem(alignmentLabel(style.alignment()));
                badgeTextAllCapsBox.setSelected(style.allCaps());
                badgeTextOutlineBox.setSelected(style.textOutline());
                badgeTextOutlineColorSwatch.setBackground(colorFromHex(style.textOutlineColorHex(), Color.BLACK));
                badgeTextBoxOutlineBox.setSelected(style.boxOutline());
                badgeTextBoxOutlineColorSwatch.setBackground(colorFromHex(style.boxOutlineColorHex(), new Color(17, 24, 39)));
            } else {
                badgeTextAllCapsBox.setSelected(false);
                badgeTextOutlineBox.setSelected(false);
                badgeTextOutlineColorSwatch.setBackground(Color.BLACK);
                badgeTextBoxOutlineBox.setSelected(false);
                badgeTextBoxOutlineColorSwatch.setBackground(new Color(17, 24, 39));
            }
        } finally {
            updatingBadgeFontControls = false;
        }
    }

    private void refreshBadgeElementVisibilityControls() {
        if (badgeElementVisibilityBoxes.isEmpty()) {
            return;
        }
        CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
        for (Map.Entry<String, JCheckBox> entry : badgeElementVisibilityBoxes.entrySet()) {
            entry.getValue().setSelected(BadgePrintService.elementVisible(settings, entry.getKey()));
        }
    }

    private void syncLegacyBadgeVisibilityFields() {
        CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
        badgeShowQuoteBox.setSelected(BadgePrintService.elementVisible(settings, "front.quote"));
        badgeShowEmployeeIdBox.setSelected(BadgePrintService.elementVisible(settings, "back.employeeNumber"));
        badgeShowIssueDateBox.setSelected(BadgePrintService.elementVisible(settings, "back.issueDate"));
        badgeShowBarcodeBox.setSelected(BadgePrintService.elementVisible(settings, "back.barcode"));
        badgeShowBadgeTextBox.setSelected(BadgePrintService.elementVisible(settings, "back.badgeText"));
    }

    private void applySelectedBadgeTextStyle() {
        if (updatingBadgeFontControls || !BadgePrintService.isTextElement(badgeSelectedTemplateElementId)) {
            return;
        }
        String family = String.valueOf(badgeFontFamilyBox.getSelectedItem());
        int style = fontStyleValue(String.valueOf(badgeFontStyleBox.getSelectedItem()));
        String weight = fontWeightValue(String.valueOf(badgeFontWeightBox.getSelectedItem()));
        int maxSize = ((Number) badgeFontMaxSizeSpinner.getValue()).intValue();
        String alignment = alignmentValue(String.valueOf(badgeTextAlignmentBox.getSelectedItem()));
        badgeLayoutData = BadgePrintService.updateTextStyle(
                badgeLayoutData,
                badgeSelectedTemplateElementId,
                new BadgePrintService.BadgeTextStyle(
                        family,
                        style,
                        maxSize,
                        alignment,
                        weight,
                        badgeTextAllCapsBox.isSelected(),
                        badgeTextOutlineBox.isSelected(),
                        colorToHex(badgeTextOutlineColorSwatch.getBackground()),
                        badgeTextBoxOutlineBox.isSelected(),
                        colorToHex(badgeTextBoxOutlineColorSwatch.getBackground())
                )
        );
        refreshBadgePreview();
    }

    private boolean isBadgeSelectedTextEditable() {
        return BadgePrintService.isCustomTextElement(badgeSelectedTemplateElementId)
                || switch (badgeSelectedTemplateElementId) {
                    case "front.logo", "front.quote", "back.instructions", "back.signature" -> true;
                    default -> false;
                };
    }

    private String selectedBadgeTextValue(CompanyCustomizationManager.BadgeTemplateSettings settings) {
        return switch (badgeSelectedTemplateElementId) {
            case "front.logo" -> badgeCompanyNameField.getText();
            case "front.quote" -> badgeQuoteField.getText();
            case "back.instructions" -> badgeBackInstructionsField.getText();
            case "back.signature" -> {
                String name = badgeSignatoryNameField.getText() == null ? "" : badgeSignatoryNameField.getText().trim();
                String title = badgeSignatoryTitleField.getText() == null ? "" : badgeSignatoryTitleField.getText().trim();
                yield title.isBlank() ? name : name + " | " + title;
            }
            default -> BadgePrintService.customText(settings, badgeSelectedTemplateElementId);
        };
    }

    private void applySelectedBadgeTemplateText(String text) {
        String cleanText = text == null ? "" : text;
        switch (badgeSelectedTemplateElementId) {
            case "front.logo" -> badgeCompanyNameField.setText(cleanText);
            case "front.quote" -> badgeQuoteField.setText(cleanText);
            case "back.instructions" -> badgeBackInstructionsField.setText(cleanText);
            case "back.signature" -> {
                String[] parts = cleanText.split("\\|", 2);
                badgeSignatoryNameField.setText(parts.length > 0 ? parts[0].trim() : "");
                if (parts.length > 1) {
                    badgeSignatoryTitleField.setText(parts[1].trim());
                }
            }
            default -> {
            }
        }
    }

    private void applySelectedBadgeElementRotation() {
        if (updatingBadgeFontControls || BadgePrintService.elementForId(badgeSelectedTemplateElementId) == null) {
            return;
        }
        int rotation = Integer.parseInt(String.valueOf(badgeElementRotationBox.getSelectedItem()));
        badgeLayoutData = BadgePrintService.updateElementRotation(badgeLayoutData, badgeSelectedTemplateElementId, rotation);
        refreshBadgePreview();
    }

    private void applySelectedBadgeElementBounds() {
        if (updatingBadgeFontControls || BadgePrintService.elementForId(badgeSelectedTemplateElementId) == null) {
            return;
        }
        Rectangle updated = new Rectangle(
                ((Number) badgeElementXSpinner.getValue()).intValue(),
                ((Number) badgeElementYSpinner.getValue()).intValue(),
                ((Number) badgeElementWidthSpinner.getValue()).intValue(),
                ((Number) badgeElementHeightSpinner.getValue()).intValue()
        );
        badgeLayoutData = BadgePrintService.updateLayoutRect(badgeLayoutData, badgeSelectedTemplateElementId, updated);
        refreshBadgePreview();
    }

    private void centerSelectedBadgeElements(boolean horizontal, boolean vertical) {
        if (updatingBadgeFontControls || badgeSelectedTemplateElementIds.isEmpty()) {
            return;
        }
        CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
        LinkedHashMap<String, Rectangle> selectedRects = new LinkedHashMap<>();
        Rectangle groupBounds = null;
        for (String elementId : badgeSelectedTemplateElementIds) {
            if (BadgePrintService.elementForId(elementId) == null) {
                continue;
            }
            Rectangle rect = BadgePrintService.layoutRect(settings, elementId);
            selectedRects.put(elementId, rect);
            groupBounds = groupBounds == null ? new Rectangle(rect) : groupBounds.union(rect);
        }
        if (selectedRects.isEmpty() || groupBounds == null) {
            return;
        }

        int dx = horizontal ? ((BADGE_CARD_WIDTH - groupBounds.width) / 2) - groupBounds.x : 0;
        int dy = vertical ? ((BADGE_CARD_HEIGHT - groupBounds.height) / 2) - groupBounds.y : 0;
        if (dx == 0 && dy == 0) {
            return;
        }
        for (Map.Entry<String, Rectangle> entry : selectedRects.entrySet()) {
            Rectangle updated = new Rectangle(entry.getValue());
            updated.x += dx;
            updated.y += dy;
            badgeLayoutData = BadgePrintService.updateLayoutRect(badgeLayoutData, entry.getKey(), updated);
        }
        refreshBadgePreview();
        updateBadgeFontControls();
    }

    private void moveSelectedBadgeLayers(String direction) {
        if (badgeSelectedTemplateElementIds.isEmpty()) {
            return;
        }
        BadgePrintService.BadgeElement activeElement = BadgePrintService.elementForId(badgeSelectedTemplateElementId);
        if (activeElement == null) {
            return;
        }
        String side = activeElement.side();
        CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
        List<String> orderedIds = BadgePrintService.elementsForSideInLayerOrder(settings, side).stream()
                .map(BadgePrintService.BadgeElement::id)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Set<String> selectedIds = new LinkedHashSet<>();
        for (String elementId : badgeSelectedTemplateElementIds) {
            BadgePrintService.BadgeElement element = BadgePrintService.elementForId(elementId);
            if (element != null && side.equals(element.side())) {
                selectedIds.add(elementId);
            }
        }
        if (selectedIds.isEmpty()) {
            return;
        }

        switch (direction) {
            case "front" -> {
                orderedIds.removeIf(selectedIds::contains);
                orderedIds.addAll(selectedIds);
            }
            case "back" -> {
                orderedIds.removeIf(selectedIds::contains);
                orderedIds.addAll(0, new ArrayList<>(selectedIds));
            }
            case "forward" -> {
                for (int i = orderedIds.size() - 2; i >= 0; i--) {
                    if (selectedIds.contains(orderedIds.get(i)) && !selectedIds.contains(orderedIds.get(i + 1))) {
                        String id = orderedIds.remove(i);
                        orderedIds.add(i + 1, id);
                    }
                }
            }
            case "backward" -> {
                for (int i = 1; i < orderedIds.size(); i++) {
                    if (selectedIds.contains(orderedIds.get(i)) && !selectedIds.contains(orderedIds.get(i - 1))) {
                        String id = orderedIds.remove(i);
                        orderedIds.add(i - 1, id);
                    }
                }
            }
            default -> {
                return;
            }
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            badgeLayoutData = BadgePrintService.updateElementZOrder(badgeLayoutData, orderedIds.get(i), i * 10);
        }
        refreshBadgePreview();
        updateBadgeFontControls();
    }

    private void applySelectedBadgeCustomText() {
        if (updatingBadgeFontControls || !isBadgeSelectedTextEditable()) {
            return;
        }
        if (BadgePrintService.isCustomTextElement(badgeSelectedTemplateElementId)) {
            badgeLayoutData = BadgePrintService.updateCustomText(badgeLayoutData, badgeSelectedTemplateElementId, badgeCustomTextField.getText());
        } else {
            applySelectedBadgeTemplateText(badgeCustomTextField.getText());
        }
        refreshBadgePreview();
    }

    private void uploadBadgeElementImage() {
        if ("front.logo".equals(badgeSelectedTemplateElementId)) {
            uploadTransparentBadgeLogo();
            badgeElementImageField.setText(badgeLogoPathField.getText());
            return;
        }
        if (!BadgePrintService.isImageElement(badgeSelectedTemplateElementId)) {
            JOptionPane.showMessageDialog(this, "Select the Logo, a background, extra image, or signature image element first.", "Element Image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Badge Element Image");
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg", "gif", "bmp"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            String uploadedPath = CompanyCustomizationManager.uploadBadgeTemplateImage(chooser.getSelectedFile().toPath());
            setBadgeElementImage(uploadedPath);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload element image.\n\n" + ex.getMessage(), "Element Image", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selectBadgeElementImage() {
        if ("front.logo".equals(badgeSelectedTemplateElementId)) {
            selectUploadedBadgeLogo();
            badgeElementImageField.setText(badgeLogoPathField.getText());
            return;
        }
        if (!BadgePrintService.isImageElement(badgeSelectedTemplateElementId)) {
            JOptionPane.showMessageDialog(this, "Select the Logo, a background, extra image, or signature image element first.", "Element Image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            List<CompanyCustomizationManager.UploadedImageOption> logos = CompanyCustomizationManager.listUploadedCompanyLogos();
            if (logos.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No uploaded images were found in Storage or saved company settings.", "Element Image", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JList<CompanyCustomizationManager.UploadedImageOption> imageList = new JList<>(logos.toArray(new CompanyCustomizationManager.UploadedImageOption[0]));
            imageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            imageList.setVisibleRowCount(Math.min(logos.size(), 10));
            imageList.setSelectedIndex(0);
            int result = JOptionPane.showConfirmDialog(
                    this,
                    new JScrollPane(imageList),
                    "Select Element Image",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );
            if (result == JOptionPane.OK_OPTION && imageList.getSelectedValue() != null) {
                setBadgeElementImage(imageList.getSelectedValue().url());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to list uploaded images.\n\n" + ex.getMessage(), "Element Image", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearBadgeElementImage() {
        if ("front.logo".equals(badgeSelectedTemplateElementId)) {
            clearBadgeLogo();
            badgeElementImageField.setText("");
            return;
        }
        if (!BadgePrintService.isImageElement(badgeSelectedTemplateElementId)) {
            return;
        }
        setBadgeElementImage("");
    }

    private void setBadgeElementImage(String imagePath) {
        if ("front.logo".equals(badgeSelectedTemplateElementId)) {
            badgeLogoPathField.setText(imagePath == null ? "" : imagePath);
            badgeElementImageField.setText(badgeLogoPathField.getText());
            refreshBadgePreview();
            return;
        }
        badgeLayoutData = BadgePrintService.updateElementImagePath(badgeLayoutData, badgeSelectedTemplateElementId, imagePath);
        String cleanPath = imagePath == null ? "" : imagePath;
        badgeElementImageField.setText(cleanPath);
        if ("back.signature".equals(badgeSelectedTemplateElementId)) {
            badgeSignatureImageField.setText(cleanPath);
        }
        refreshBadgePreview();
    }

    private void applySelectedBadgeImageOpacity() {
        if (updatingBadgeFontControls || !BadgePrintService.isImageElement(badgeSelectedTemplateElementId)) {
            return;
        }
        int percent = badgeImageOpacitySlider.getValue();
        badgeImageOpacityValueLabel.setText(percent + "%");
        badgeLayoutData = BadgePrintService.updateElementOpacity(badgeLayoutData, badgeSelectedTemplateElementId, percent / 100f);
        refreshBadgePreview();
    }

    private void chooseBadgeElementColor() {
        if (!BadgePrintService.isColorElement(badgeSelectedTemplateElementId)) {
            JOptionPane.showMessageDialog(this, "Select a text element or the Photo element first.", "Element Color", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
        Color initialColor = BadgePrintService.elementColor(settings, badgeSelectedTemplateElementId);
        Color selectedColor = JColorChooser.showDialog(this, "Choose Element Color", initialColor);
        if (selectedColor == null) {
            return;
        }
        badgeLayoutData = BadgePrintService.updateElementColor(badgeLayoutData, badgeSelectedTemplateElementId, selectedColor);
        badgeElementColorSwatch.setBackground(selectedColor);
        refreshBadgePreview();
    }

    private void chooseBadgeTextOutlineColor() {
        chooseBadgeTextEffectColor(badgeTextOutlineColorSwatch, "Choose Text Outline Color");
    }

    private void chooseBadgeTextBoxOutlineColor() {
        chooseBadgeTextEffectColor(badgeTextBoxOutlineColorSwatch, "Choose Box Outline Color");
    }

    private void chooseBadgeTextEffectColor(JLabel swatch, String title) {
        if (!BadgePrintService.isTextElement(badgeSelectedTemplateElementId)) {
            JOptionPane.showMessageDialog(this, "Select a text element first.", title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Color selectedColor = JColorChooser.showDialog(this, title, swatch.getBackground());
        if (selectedColor == null) {
            return;
        }
        swatch.setBackground(selectedColor);
        applySelectedBadgeTextStyle();
    }

    private void applySelectedBadgeNameLayout() {
        if (updatingBadgeFontControls || !"front.name".equals(badgeSelectedTemplateElementId)) {
            return;
        }
        String selected = String.valueOf(badgeNameLayoutBox.getSelectedItem());
        badgeLayoutData = BadgePrintService.updateNameLayout(
                badgeLayoutData,
                "Two lines".equals(selected) ? "two" : "one"
        );
        refreshBadgePreview();
    }

    private static String fontStyleLabel(int style) {
        return switch (style) {
            case Font.BOLD -> "Bold";
            case Font.ITALIC -> "Italic";
            case Font.BOLD | Font.ITALIC -> "Bold Italic";
            default -> "Regular";
        };
    }

    private static void configureCompactSpinner(JSpinner spinner) {
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(4);
    }

    private static int fontStyleValue(String label) {
        return switch (label) {
            case "Bold" -> Font.BOLD;
            case "Italic" -> Font.ITALIC;
            case "Bold Italic" -> Font.BOLD | Font.ITALIC;
            default -> Font.PLAIN;
        };
    }

    private static String fontWeightLabel(String weight) {
        return switch (weight == null ? "regular" : weight.toLowerCase()) {
            case "medium" -> "Medium";
            case "semibold" -> "Semi Bold";
            case "bold" -> "Bold";
            case "extrabold" -> "Extra Bold";
            case "black" -> "Black";
            default -> "Regular";
        };
    }

    private static String fontWeightValue(String label) {
        return switch (label) {
            case "Medium" -> "medium";
            case "Semi Bold" -> "semibold";
            case "Bold" -> "bold";
            case "Extra Bold" -> "extrabold";
            case "Black" -> "black";
            default -> "regular";
        };
    }

    private static String alignmentLabel(String alignment) {
        return switch (alignment == null ? "center" : alignment.toLowerCase()) {
            case "left" -> "Left";
            case "right" -> "Right";
            default -> "Center";
        };
    }

    private static String alignmentValue(String label) {
        return switch (label) {
            case "Left" -> "left";
            case "Right" -> "right";
            default -> "center";
        };
    }

    private static Color colorFromHex(String value, Color fallback) {
        String clean = value == null ? "" : value.trim();
        if (!clean.matches("#[0-9a-fA-F]{6}")) {
            return fallback;
        }
        try {
            return new Color(Integer.parseInt(clean.substring(1), 16));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String colorToHex(Color color) {
        Color clean = color == null ? Color.BLACK : color;
        return String.format("#%02X%02X%02X", clean.getRed(), clean.getGreen(), clean.getBlue());
    }

    private JPanel buildBadgeLogoSelectorPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.add(badgeLogoPathField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 0, 4, 0));
        buttons.setOpaque(false);
        JButton useCompanyLogoButton = new JButton("Use Company Logo");
        JButton uploadButton = new JButton("Upload");
        JButton selectButton = new JButton("Select Uploaded");
        JButton clearButton = new JButton("Clear");
        buttons.add(useCompanyLogoButton);
        buttons.add(uploadButton);
        buttons.add(selectButton);
        buttons.add(clearButton);
        panel.add(buttons, BorderLayout.EAST);

        useCompanyLogoButton.addActionListener(e -> useCompanyLogoForBadge());
        uploadButton.addActionListener(e -> uploadBadgeLogo());
        selectButton.addActionListener(e -> selectUploadedBadgeLogo());
        clearButton.addActionListener(e -> clearBadgeLogo());
        return panel;
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
                ? "Refunds above this amount require approval permission. Use 0 to disable."
                : "Requires Custom Order Refund Approval Settings permission.");

        roundCustomOrdersToNearestTwentyBox.setEnabled(canEditDeposit || canEditRefundLimit);
        return new CustomOrderDepositPanel(customOrderMinimumDepositPercentField,
                customOrderRefundApprovalLimitField, roundCustomOrdersToNearestTwentyBox);
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
                ? "Returns above this amount require override permission. Use 0 to disable."
                : "Requires Sale Return Approval Settings permission.");

        return new SaleReceiptPanel(
                headerLineField,
                footerLineField,
                receiptStartCounterField,
                configPathField,
                saleDiscountLimitPercentField,
                saleReturnApprovalLimitField,
                roundSalesToNearestTwentyBox,
                alwaysPrintSaleReceiptBox,
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

    private JPanel buildAccountPaymentReceiptPanel() {
        return new AccountPaymentReceiptPanel(
                accountPaymentReceiptTitleField,
                accountPaymentReceiptShowUserBox,
                accountPaymentReceiptShowCustomerBox,
                accountPaymentReceiptShowAccountNumberBox,
                accountPaymentReceiptShowMethodBox,
                accountPaymentReceiptShowReferenceBox,
                accountPaymentReceiptShowDeviceBox,
                accountPaymentReceiptShowDrawerBox,
                accountPaymentReceiptShowAllocationsBox,
                accountPaymentReceiptShowBalanceBox,
                accountPaymentReceiptShowBarcodeBox
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

    private JPanel buildQuotationInvoicePrintPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        QuotationInvoicePrintPanel settingsPanel = new QuotationInvoicePrintPanel(
                quotationPrintTitleField,
                quotationValidityNoteField,
                invoicePrintTitleField,
                salesDeliveryPrintTitleField,
                quotationInvoiceFooterNoteField,
                quotationInvoiceShowSignaturesBox
        );
        JScrollPane settingsScrollPane = new JScrollPane(settingsPanel);
        settingsScrollPane.setBorder(BorderFactory.createEmptyBorder());
        settingsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        settingsScrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                settingsScrollPane,
                buildQuotationInvoicePreviewPanel()
        );
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setContinuousLayout(true);
        splitPane.setResizeWeight(0.48);
        splitPane.setDividerLocation(760);
        contentPanel.add(splitPane, BorderLayout.CENTER);
        return contentPanel;
    }

    private JPanel buildQuotationInvoicePreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(900, 0));
        panel.setMinimumSize(new Dimension(720, 0));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sectionLabel = new JLabel("Sample Sales Printouts");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(sectionLabel, BorderLayout.NORTH);

        JTabbedPane previewTabs = new JTabbedPane();
        previewTabs.addTab("Quotation", buildSalesDocumentScroll(sampleQuotationPane));
        previewTabs.addTab("Invoice", buildSalesDocumentScroll(sampleInvoicePane));
        previewTabs.addTab("Delivery", buildSalesDocumentScroll(sampleSalesDeliveryPane));
        panel.add(previewTabs, BorderLayout.CENTER);
        return panel;
    }

    private JEditorPane createSalesDocumentPreviewPane() {
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.setBackground(new Color(241, 245, 249));
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        return pane;
    }

    private JComponent buildSalesDocumentScroll(JEditorPane previewPanel) {
        JScrollPane scrollPane = new JScrollPane(previewPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JPanel buildLogoFilePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        DeckersSwing.styleField(logoPathField);
        logoPathField.setPreferredSize(new Dimension(320, 36));
        logoPathField.setToolTipText("The stored logo location. Use the buttons below to change it.");
        panel.add(logoPathField, BorderLayout.NORTH);

        JPanel logoToolsPanel = new JPanel(new BorderLayout(12, 0));
        logoToolsPanel.setOpaque(false);
        logoPreviewLabel.setOpaque(true);
        logoPreviewLabel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        logoPreviewLabel.setBackground(DeckersPalette.fieldBackground());
        logoPreviewLabel.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
        logoPreviewLabel.setPreferredSize(new Dimension(190, 92));
        logoToolsPanel.add(logoPreviewLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.setOpaque(false);
        JButton uploadButton = new JButton("Upload Logo");
        JButton selectUploadedButton = new JButton("Select Uploaded");
        JButton clearButton = new JButton("Clear Logo");
        DeckersSwing.styleUtilityButton(uploadButton, DeckersPalette.ORANGE);
        DeckersSwing.styleUtilityButton(selectUploadedButton, DeckersPalette.PURPLE);
        DeckersSwing.styleUtilityButton(clearButton, DeckersPalette.CORAL);
        buttonPanel.add(uploadButton);
        buttonPanel.add(selectUploadedButton);
        buttonPanel.add(clearButton);
        logoToolsPanel.add(buttonPanel, BorderLayout.CENTER);
        panel.add(logoToolsPanel, BorderLayout.CENTER);

        uploadButton.addActionListener(e -> uploadLogo());
        selectUploadedButton.addActionListener(e -> selectUploadedLogo());
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

    private JPanel buildAccountPaymentReceiptPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(430, 0));
        panel.setMinimumSize(new Dimension(380, 0));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(14, 14, 14, 14)
        ));

        JLabel sectionLabel = new JLabel("Sample Account Payment Receipt");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        panel.add(sectionLabel, BorderLayout.NORTH);

        JTabbedPane previewTabs = new JTabbedPane();
        JScrollPane receiptScrollPane = new JScrollPane(sampleAccountPaymentReceipt40Panel);
        receiptScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        receiptScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        JScrollPane letterScrollPane = new JScrollPane(sampleAccountPaymentReceiptLetterPanel);
        letterScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        letterScrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        previewTabs.addTab("40 Column", receiptScrollPane);
        previewTabs.addTab("Letter", letterScrollPane);
        panel.add(previewTabs, BorderLayout.CENTER);

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
        CachedUiLoader.load(this, "company-preferences.all-settings", PreferencesSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingStatePanel, () -> {
                    var all = UiTaskRunner.supplyAsync(CompanyCustomizationManager::loadAllSettings);
                    var clock = UiTaskRunner.supplyAsync(CompanyCustomization::loadTimeClockSettingsSafely);
                    var backup = UiTaskRunner.supplyAsync(CompanyBackupScheduler::loadSettings);
                    return new PreferencesSnapshot(all.join(), clock.join(),backup.join());
                }, this::applySettings);
    }

    private static AutoCloseSettings loadTimeClockSettingsSafely() {
        try {
            return TimeClockAutoCloseService.loadSettings();
        } catch (Exception ex) {
            return new AutoCloseSettings(true,
                    TimeClockAutoCloseService.DEFAULT_SCHEDULED_DELAY_HOURS,
                    TimeClockAutoCloseService.DEFAULT_UNSCHEDULED_DETECTION_HOURS,
                    TimeClockAutoCloseService.DEFAULT_MAX_WORK_HOURS, null, null);
        }
    }

    private void applySettings(PreferencesSnapshot snapshot) {
        loadingSettings = true;
        CompanyCustomizationManager.AllSettings all = snapshot.settings();
        CompanyCustomizationManager.ReceiptSettings settings = all.receipt();
        loadedChangeBasketTargetAmount = settings.changeBasketTargetAmount()==null?BigDecimal.valueOf(60000):settings.changeBasketTargetAmount();
        companyNameField.setText(settings.companyName());
        companyAddressLine1Field.setText(settings.addressLine1());
        companyAddressLine2Field.setText(settings.addressLine2());
        companyAddressLine3Field.setText(settings.addressLine3());
        companyPhoneLine1Field.setText(settings.phoneLine1());
        companyPhoneLine2Field.setText(settings.phoneLine2());
        companyEmailLine1Field.setText(settings.emailLine1());
        companyEmailLine2Field.setText(settings.emailLine2());
        companyMottoLine1Field.setText(settings.mottoLine1());
        companyMottoLine2Field.setText(settings.mottoLine2());
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
        alwaysPrintSaleReceiptBox.setSelected(settings.alwaysPrintSaleReceipt());
        vatEnabledBox.setSelected(settings.vatEnabled());
        vatUseDepartmentRatesBox.setSelected(settings.vatUseDepartmentRates());
        vatFixedRatePercentField.setText(settings.vatFixedRatePercent().stripTrailingZeros().toPlainString());
        receiptStartCounterField.setText(String.valueOf(settings.nextReceiptCounter()));
        loadAccountPaymentReceiptFields(settings.accountPaymentReceiptSettings());
        CompanyCustomizationManager.SaleSafetySettings saleSafetySettings = all.saleSafety();
        loadedSaleSafetySettings=saleSafetySettings;
        saleDiscountLimitPercentField.setText(saleSafetySettings.discountLimitPercent().stripTrailingZeros().toPlainString());
        saleReturnApprovalLimitField.setText(utils.CurrencyFormatter.normalize(saleSafetySettings.returnApprovalLimit()).toPlainString());
        requireCostPriceOnNewItemBox.setSelected(saleSafetySettings.requireCostPriceOnNewItem());
        roundSalesToNearestTwentyBox.setSelected(saleSafetySettings.roundToNearestTwenty());
        CompanyCustomizationManager.CustomOrderSettings customOrderSettings = all.customOrder();
        loadedCustomOrderSettings=customOrderSettings;
        customOrderMinimumDepositPercentField.setText(customOrderSettings.minimumDepositPercent().stripTrailingZeros().toPlainString());
        customOrderRefundApprovalLimitField.setText(utils.CurrencyFormatter.normalize(customOrderSettings.refundApprovalLimit()).toPlainString());
        roundCustomOrdersToNearestTwentyBox.setSelected(customOrderSettings.roundToNearestTwenty());
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = all.customOrderSlip();
        loadSlipFields(slipSettings);
        CompanyCustomizationManager.QuotationInvoicePrintSettings salesPrintSettings = all.quotationInvoice();
        loadQuotationInvoicePrintFields(salesPrintSettings);
        CompanyCustomizationManager.BadgeTemplateSettings badgeTemplateSettings = all.badgeTemplate();
        loadBadgeTemplateFields(badgeTemplateSettings);
        requireBadgePinLoginBox.setSelected(all.badgeSecurity() == null
                || all.badgeSecurity().requireBadgePinLogin());
        priceTagTemplates = new ArrayList<>(all.priceTags() == null ? List.of() : all.priceTags());
        loadPriceTagTemplateFields();
        AutoCloseSettings timeClockSettings = snapshot.timeClock();
        timeClockAutoCloseEnabledBox.setSelected(timeClockSettings.enabled());
        scheduledDetectionDelaySpinner.setValue(timeClockSettings.scheduledDelayHours());
        unscheduledDetectionHoursSpinner.setValue(timeClockSettings.unscheduledDetectionHours());
        maximumAutomaticWorkHoursSpinner.setValue(timeClockSettings.maxWorkHours());
        applyBackupSchedulerSettings(snapshot.backup());
        updateLogoPreview(settings.logoPath());
        loadingSettings = false;
        refreshSamplePreview();
        refreshAccountPaymentReceiptPreview();
        refreshSlipPreview();
        refreshQuotationInvoicePrintPreview();
        refreshBadgePreview();
        configureActionButtons();
    }

    private record PreferencesSnapshot(CompanyCustomizationManager.AllSettings settings,
                                       AutoCloseSettings timeClock,
                                       CompanyBackupScheduler.BackupScheduleSettings backup) { }

    private void saveSettings() {
        try {
            CompanyCustomizationManager.clearPreviewOverrideSettings();
            var receipt=getSettingsFromFields();
            var sale=(saleDiscountLimitPercentField.isEnabled()||saleReturnApprovalLimitField.isEnabled()||requireCostPriceOnNewItemBox.isEnabled()||roundSalesToNearestTwentyBox.isEnabled())?getSaleSafetySettingsFromFields(loadedSaleSafetySettings):null;
            var custom=(customOrderMinimumDepositPercentField.isEnabled()||customOrderRefundApprovalLimitField.isEnabled()||roundCustomOrdersToNearestTwentyBox.isEnabled())?getCustomOrderSettingsFromFields(loadedCustomOrderSettings):null;
            var slip=getSlipSettingsFromFields();var print=getQuotationInvoicePrintSettingsFromFields();var badge=getBadgeTemplateSettingsForSave();
            var badgeSecurity=new CompanyCustomizationManager.BadgeSecuritySettings(requireBadgePinLoginBox.isSelected());
            var clock=new AutoCloseSettings(
                    timeClockAutoCloseEnabledBox.isSelected(),
                    ((Number) scheduledDetectionDelaySpinner.getValue()).intValue(),
                    ((Number) unscheduledDetectionHoursSpinner.getValue()).intValue(),
                    ((Number) maximumAutomaticWorkHoursSpinner.getValue()).intValue(),
                    null,
                    null);
            loadingStatePanel.loading(true,Instant.now());
            UiTaskRunner.submit(this,"company-preferences.save",()->{CompanyCustomizationManager.saveReceiptSettings(receipt);if(sale!=null)CompanyCustomizationManager.saveSaleSafetySettings(sale);if(custom!=null)CompanyCustomizationManager.saveCustomOrderSettings(custom);CompanyCustomizationManager.saveCustomOrderSlipSettings(slip);CompanyCustomizationManager.saveQuotationInvoicePrintSettings(print);CompanyCustomizationManager.saveBadgeTemplateSettings(badge);CompanyCustomizationManager.saveBadgeSecuritySettings(badgeSecurity);TimeClockAutoCloseService.saveSettings(clock);return Boolean.TRUE;},ignored->{SessionDataCache.invalidate("company-preferences.");loadSettings();JOptionPane.showMessageDialog(this,"Company preferences saved.");},ex->loadingStatePanel.failed(ex.getMessage(),true,this::saveSettings));
        } catch (Exception ex) {
            loadingStatePanel.failed(ex.getMessage(),true,this::saveSettings);
        }
    }

    private void exportCompanyBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Company Backup");
        chooser.setFileFilter(new FileNameExtensionFilter("SmartStock Backup (*.ssbackup)", "ssbackup"));
        chooser.setSelectedFile(new File(defaultBackupFileName()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path backupPath = ensureBackupExtension(chooser.getSelectedFile().toPath());
        try {
            CompanyBackupService.BackupSummary summary = CompanyBackupService.exportBackup(backupPath);
            JOptionPane.showMessageDialog(
                    this,
                    "Company backup saved.\n\nFile: " + backupPath + "\nTables: " + summary.tableCount() + "\nRows: " + summary.rowCount()
                            + "\nFiles: " + summary.assetCount()
                            + (summary.skippedAssetCount() > 0 ? "\nFiles skipped: " + summary.skippedAssetCount() : ""),
                    "Company Backup",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to export company backup.\n\n" + ex.getMessage(), "Company Backup", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void restoreCompanyBackup() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Restore Company Backup");
        chooser.setFileFilter(new FileNameExtensionFilter("SmartStock Backup (*.ssbackup, *.sql)", "ssbackup", "sql"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path backupPath = chooser.getSelectedFile().toPath();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Restore this backup?\n\nThis replaces the current SmartStock company data in the configured database.",
                "Restore Company Backup",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CompanyBackupService.BackupSummary summary = CompanyBackupService.restoreBackup(backupPath);
            CompanyCustomizationManager.clearPreviewOverrideSettings();
            loadSettings();
            JOptionPane.showMessageDialog(
                    this,
                    "Company backup restored."
                            + (summary.assetCount() > 0 ? "\n\nFiles restored: " + summary.assetCount() : "")
                            + (summary.skippedAssetCount() > 0 ? "\nFiles skipped: " + summary.skippedAssetCount() : ""),
                    "Company Backup",
                    JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to restore company backup.\n\n" + ex.getMessage(), "Company Backup", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String defaultBackupFileName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "smartstock-company-backup-" + timestamp + ".ssbackup";
    }

    private static Path ensureBackupExtension(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".ssbackup")) {
            return path;
        }
        return path.resolveSibling(fileName + ".ssbackup");
    }

    private void loadBackupSchedulerSettings() {
        CompanyBackupScheduler.BackupScheduleSettings settings = CompanyBackupScheduler.loadSettings();
        applyBackupSchedulerSettings(settings);
    }

    private void applyBackupSchedulerSettings(CompanyBackupScheduler.BackupScheduleSettings settings) {
        loadedBackupSettings=settings;
        backupSchedulerEnabledBox.setSelected(settings.enabled());
        backupDirectoryField.setText(settings.directory().toString());
        backupIntervalMinutesSpinner.setValue((int) Math.max(15, Math.min(525600, settings.intervalMinutes())));
        backupRetentionCountSpinner.setValue(Math.max(1, settings.retentionCount()));
        backupStatusLabel.setText(settings.lastStatus() == null || settings.lastStatus().isBlank()
                ? "Not run yet"
                : settings.lastStatus());
    }

    private void saveBackupSchedulerSettings() {
        try {
            CompanyBackupScheduler.saveSettings(getBackupSchedulerSettingsFromFields());
            CompanyBackupScheduler.pruneOldBackups(Path.of(backupDirectoryField.getText().trim()), (Integer) backupRetentionCountSpinner.getValue());
            loadBackupSchedulerSettings();
            JOptionPane.showMessageDialog(this, "Backup schedule saved.", "Company Backups", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save backup schedule.\n\n" + ex.getMessage(), "Company Backups", JOptionPane.ERROR_MESSAGE);
        }
    }

    private CompanyBackupScheduler.BackupScheduleSettings getBackupSchedulerSettingsFromFields() {
        String directoryText = backupDirectoryField.getText() == null ? "" : backupDirectoryField.getText().trim();
        Path directory = directoryText.isBlank() ? CompanyBackupScheduler.DEFAULT_BACKUP_DIRECTORY : Path.of(directoryText);
        int intervalMinutes = (Integer) backupIntervalMinutesSpinner.getValue();
        int retentionCount = (Integer) backupRetentionCountSpinner.getValue();
        CompanyBackupScheduler.BackupScheduleSettings existing = loadedBackupSettings==null
                ?new CompanyBackupScheduler.BackupScheduleSettings(false,directory,intervalMinutes,retentionCount,0,null,null)
                :loadedBackupSettings;
        return new CompanyBackupScheduler.BackupScheduleSettings(
                backupSchedulerEnabledBox.isSelected(),
                directory,
                Math.max(15, intervalMinutes),
                Math.max(1, retentionCount),
                existing.lastSuccessEpochMillis(),
                existing.lastStatus(),
                existing.lastError()
        );
    }

    private void chooseBackupDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose Backup Folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = backupDirectoryField.getText() == null ? "" : backupDirectoryField.getText().trim();
        if (!current.isBlank()) {
            chooser.setSelectedFile(Path.of(current).toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            backupDirectoryField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void runScheduledBackupNow(JButton runNowButton) {
        CompanyBackupScheduler.BackupScheduleSettings settings = getBackupSchedulerSettingsFromFields();
        runNowButton.setEnabled(false);
        backupStatusLabel.setText("Backup running...");
        new SwingWorker<CompanyBackupScheduler.BackupRunResult, Void>() {
            @Override
            protected CompanyBackupScheduler.BackupRunResult doInBackground() throws Exception {
                CompanyBackupScheduler.saveSettings(settings);
                return CompanyBackupScheduler.runNow();
            }

            @Override
            protected void done() {
                runNowButton.setEnabled(true);
                loadBackupSchedulerSettings();
                try {
                    CompanyBackupScheduler.BackupRunResult result = get();
                    JOptionPane.showMessageDialog(
                            CompanyCustomization.this,
                            "Backup saved.\n\nFile: " + result.backupFile()
                                    + "\nRows: " + result.summary().rowCount()
                                    + "\nFiles: " + result.summary().assetCount()
                                    + (result.summary().skippedAssetCount() > 0 ? "\nFiles skipped: " + result.summary().skippedAssetCount() : ""),
                            "Company Backups",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(CompanyCustomization.this, "Backup failed.\n\n" + ex.getMessage(), "Company Backups", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
        boolean roundToTwenty = roundCustomOrdersToNearestTwentyBox.isEnabled()
                ? roundCustomOrdersToNearestTwentyBox.isSelected() : existingSettings.roundToNearestTwenty();
        return new CompanyCustomizationManager.CustomOrderSettings(savedPercent, savedRefundLimit, roundToTwenty);
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
        boolean requireCostPrice = requireCostPriceOnNewItemBox.isEnabled()
                ? requireCostPriceOnNewItemBox.isSelected() : existingSettings.requireCostPriceOnNewItem();
        boolean roundToTwenty = roundSalesToNearestTwentyBox.isEnabled()
                ? roundSalesToNearestTwentyBox.isSelected() : existingSettings.roundToNearestTwenty();
        return new CompanyCustomizationManager.SaleSafetySettings(savedDiscountLimit, savedReturnLimit,
                requireCostPrice, roundToTwenty);
    }

    private CompanyCustomizationManager.ReceiptSettings getSettingsFromFields() {
        return new CompanyCustomizationManager.ReceiptSettings(
                companyNameField.getText(),
                companyAddressLine1Field.getText(),
                companyAddressLine2Field.getText(),
                companyAddressLine3Field.getText(),
                companyPhoneLine1Field.getText(),
                companyPhoneLine2Field.getText(),
                companyEmailLine1Field.getText(),
                companyEmailLine2Field.getText(),
                companyMottoLine1Field.getText(),
                companyMottoLine2Field.getText(),
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
                parsePositiveCounter(receiptStartCounterField.getText()),
                loadedChangeBasketTargetAmount,
                alwaysPrintSaleReceiptBox.isSelected(),
                getAccountPaymentReceiptSettingsFromFields()
        );
    }

    private CompanyCustomizationManager.AccountPaymentReceiptSettings getAccountPaymentReceiptSettingsFromFields() {
        return new CompanyCustomizationManager.AccountPaymentReceiptSettings(
                accountPaymentReceiptTitleField.getText(),
                accountPaymentReceiptShowUserBox.isSelected(),
                accountPaymentReceiptShowCustomerBox.isSelected(),
                accountPaymentReceiptShowAccountNumberBox.isSelected(),
                accountPaymentReceiptShowMethodBox.isSelected(),
                accountPaymentReceiptShowReferenceBox.isSelected(),
                accountPaymentReceiptShowDeviceBox.isSelected(),
                accountPaymentReceiptShowDrawerBox.isSelected(),
                accountPaymentReceiptShowAllocationsBox.isSelected(),
                accountPaymentReceiptShowBalanceBox.isSelected(),
                accountPaymentReceiptShowBarcodeBox.isSelected()
        );
    }

    private void loadAccountPaymentReceiptFields(CompanyCustomizationManager.AccountPaymentReceiptSettings settings) {
        CompanyCustomizationManager.AccountPaymentReceiptSettings cleanSettings = settings == null
                ? CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults()
                : settings;
        accountPaymentReceiptTitleField.setText(cleanSettings.title());
        accountPaymentReceiptShowUserBox.setSelected(cleanSettings.showUser());
        accountPaymentReceiptShowCustomerBox.setSelected(cleanSettings.showCustomer());
        accountPaymentReceiptShowAccountNumberBox.setSelected(cleanSettings.showAccountNumber());
        accountPaymentReceiptShowMethodBox.setSelected(cleanSettings.showMethod());
        accountPaymentReceiptShowReferenceBox.setSelected(cleanSettings.showReference());
        accountPaymentReceiptShowDeviceBox.setSelected(cleanSettings.showDevice());
        accountPaymentReceiptShowDrawerBox.setSelected(cleanSettings.showDrawer());
        accountPaymentReceiptShowAllocationsBox.setSelected(cleanSettings.showAllocations());
        accountPaymentReceiptShowBalanceBox.setSelected(cleanSettings.showBalance());
        accountPaymentReceiptShowBarcodeBox.setSelected(cleanSettings.showBarcode());
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

    private void loadQuotationInvoicePrintFields(CompanyCustomizationManager.QuotationInvoicePrintSettings settings) {
        quotationPrintTitleField.setText(settings.quotationTitle());
        quotationValidityNoteField.setText(settings.quotationValidityNote());
        invoicePrintTitleField.setText(settings.invoiceTitle());
        salesDeliveryPrintTitleField.setText(settings.deliveryTitle());
        quotationInvoiceFooterNoteField.setText(settings.footerNote());
        quotationInvoiceShowSignaturesBox.setSelected(settings.showSignatures());
    }

    private CompanyCustomizationManager.QuotationInvoicePrintSettings getQuotationInvoicePrintSettingsFromFields() {
        return new CompanyCustomizationManager.QuotationInvoicePrintSettings(
                quotationPrintTitleField.getText(),
                quotationValidityNoteField.getText(),
                invoicePrintTitleField.getText(),
                salesDeliveryPrintTitleField.getText(),
                quotationInvoiceFooterNoteField.getText(),
                quotationInvoiceShowSignaturesBox.isSelected()
        );
    }

    private void loadBadgeTemplateFields(CompanyCustomizationManager.BadgeTemplateSettings settings) {
        badgeCompanyNameField.setText(settings.companyName());
        String badgeLogoPath = settings.logoPath();
        if (badgeLogoPath == null || badgeLogoPath.isBlank()) {
            badgeLogoPath = logoPathField.getText();
        }
        badgeLogoPathField.setText(badgeLogoPath);
        badgeQuoteField.setText(settings.quoteLine());
        badgeSignatoryNameField.setText(settings.signatoryName());
        badgeSignatoryTitleField.setText(settings.signatoryTitle());
        badgeBackInstructionsField.setText(settings.backInstructions());
        badgeShowQuoteBox.setSelected(settings.showQuote());
        badgeShowEmployeeIdBox.setSelected(settings.showEmployeeId());
        badgeShowIssueDateBox.setSelected(settings.showIssueDate());
        badgeShowBarcodeBox.setSelected(settings.showBarcode());
        badgeShowBadgeTextBox.setSelected(false);
        badgeMagStripeEnabledBox.setSelected(settings.magStripeEnabled());
        badgeMagStripeTrack1Field.setText(settings.magStripeTrack1());
        badgeMagStripeTrack2Field.setText(settings.magStripeTrack2());
        badgeMagStripeTrack3Field.setText(settings.magStripeTrack3());
        badgeMagStripeCommandField.setText(settings.magStripeCommand());
        badgeNfcEnabledBox.setSelected(settings.nfcEnabled());
        badgeNfcPayloadField.setText(settings.nfcPayloadTemplate());
        badgeNfcWriterCommandField.setText(settings.nfcWriterCommand());
        badgeNfcVerifyCommandField.setText(settings.nfcVerifyCommand());
        String[] loadedLayouts = CompanyCustomizationManager.unpackBadgeTemplateLayouts(settings.layoutData());
        for (int i = 0; i < badgeTemplateLayouts.length; i++) {
            badgeTemplateLayouts[i] = i < loadedLayouts.length ? loadedLayouts[i] : "";
        }
        badgeTemplateSlotIndex = CompanyCustomizationManager.activeBadgeTemplateIndex(settings.layoutData());
        badgeLayoutData = badgeTemplateLayouts[badgeTemplateSlotIndex] == null ? "" : badgeTemplateLayouts[badgeTemplateSlotIndex];
        updatingBadgeTemplateSlot = true;
        try {
            badgeTemplateSlotBox.setSelectedIndex(badgeTemplateSlotIndex);
        } finally {
            updatingBadgeTemplateSlot = false;
        }
        refreshBadgeElementVisibilityControls();
        refreshBadgePreview();
    }

    private CompanyCustomizationManager.BadgeTemplateSettings getBadgeTemplateSettingsFromFields() {
        String badgeLogoPath = badgeLogoPathField.getText();
        if (badgeLogoPath == null || badgeLogoPath.isBlank()) {
            badgeLogoPath = logoPathField.getText();
        }
        CompanyCustomizationManager.BadgeTemplateSettings previewSettings = new CompanyCustomizationManager.BadgeTemplateSettings(
                badgeCompanyNameField.getText(),
                badgeLogoPath,
                badgeQuoteField.getText(),
                badgeSignatoryNameField.getText(),
                badgeSignatoryTitleField.getText(),
                badgeBackInstructionsField.getText(),
                badgeShowQuoteBox.isSelected(),
                badgeShowEmployeeIdBox.isSelected(),
                badgeShowIssueDateBox.isSelected(),
                badgeShowBarcodeBox.isSelected(),
                badgeShowBadgeTextBox.isSelected(),
                badgeMagStripeEnabledBox.isSelected(),
                badgeMagStripeTrack1Field.getText(),
                badgeMagStripeTrack2Field.getText(),
                badgeMagStripeTrack3Field.getText(),
                badgeMagStripeCommandField.getText(),
                badgeNfcEnabledBox.isSelected(),
                badgeNfcPayloadField.getText(),
                badgeNfcWriterCommandField.getText(),
                badgeNfcVerifyCommandField.getText(),
                badgeLayoutData
        );
        return new CompanyCustomizationManager.BadgeTemplateSettings(
                badgeCompanyNameField.getText(),
                badgeLogoPath,
                badgeQuoteField.getText(),
                badgeSignatoryNameField.getText(),
                badgeSignatoryTitleField.getText(),
                badgeBackInstructionsField.getText(),
                BadgePrintService.elementVisible(previewSettings, "front.quote"),
                BadgePrintService.elementVisible(previewSettings, "back.employeeNumber"),
                BadgePrintService.elementVisible(previewSettings, "back.issueDate"),
                BadgePrintService.elementVisible(previewSettings, "back.barcode"),
                BadgePrintService.elementVisible(previewSettings, "back.badgeText"),
                badgeMagStripeEnabledBox.isSelected(),
                badgeMagStripeTrack1Field.getText(),
                badgeMagStripeTrack2Field.getText(),
                badgeMagStripeTrack3Field.getText(),
                badgeMagStripeCommandField.getText(),
                badgeNfcEnabledBox.isSelected(),
                badgeNfcPayloadField.getText(),
                badgeNfcWriterCommandField.getText(),
                badgeNfcVerifyCommandField.getText(),
                badgeLayoutData
        );
    }

    private CompanyCustomizationManager.BadgeTemplateSettings getBadgeTemplateSettingsForSave() {
        storeCurrentBadgeTemplateSlot();
        CompanyCustomizationManager.BadgeTemplateSettings current = getBadgeTemplateSettingsFromFields();
        return current.withLayoutData(CompanyCustomizationManager.packBadgeTemplateLayouts(badgeTemplateLayouts, badgeTemplateSlotIndex));
    }

    private void storeCurrentBadgeTemplateSlot() {
        if (badgeTemplateSlotIndex >= 0 && badgeTemplateSlotIndex < badgeTemplateLayouts.length) {
            badgeTemplateLayouts[badgeTemplateSlotIndex] = badgeLayoutData == null ? "" : badgeLayoutData;
        }
    }

    private void switchBadgeTemplateSlot(int selectedIndex) {
        if (updatingBadgeTemplateSlot || selectedIndex < 0 || selectedIndex >= badgeTemplateLayouts.length || selectedIndex == badgeTemplateSlotIndex) {
            return;
        }
        storeCurrentBadgeTemplateSlot();
        badgeTemplateSlotIndex = selectedIndex;
        badgeLayoutData = badgeTemplateLayouts[badgeTemplateSlotIndex] == null ? "" : badgeTemplateLayouts[badgeTemplateSlotIndex];
        badgeSelectedTemplateElementId = "";
        badgeSelectedTemplateElementIds.clear();
        updateBadgeFontControls();
        refreshBadgeElementVisibilityControls();
        refreshBadgePreview();
    }

    private void useCompanyLogoForBadge() {
        badgeLogoPathField.setText(logoPathField.getText());
        if (badgeCompanyNameField.getText() == null || badgeCompanyNameField.getText().isBlank()) {
            badgeCompanyNameField.setText(companyNameField.getText());
        }
        refreshBadgePreview();
    }

    private void uploadBadgeLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Badge Logo");
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg", "gif", "bmp"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File selectedFile = chooser.getSelectedFile();
        try {
            String uploadedPath = CompanyCustomizationManager.uploadCompanyLogo(selectedFile.toPath());
            badgeLogoPathField.setText(uploadedPath);
            refreshBadgePreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload badge logo.\n\n" + ex.getMessage(), "Badge Logo Upload", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void uploadTransparentBadgeLogo() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Badge Logo");
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files", "png", "jpg", "jpeg", "gif", "bmp", "webp"));
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path sourcePath = chooser.getSelectedFile().toPath();
        int removeBackground = JOptionPane.showConfirmDialog(
                this,
                "Remove white/light background and save as transparent PNG?",
                "Badge Logo",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (removeBackground == JOptionPane.CANCEL_OPTION || removeBackground == JOptionPane.CLOSED_OPTION) {
            return;
        }

        Path uploadPath = sourcePath;
        try {
            if (removeBackground == JOptionPane.YES_OPTION) {
                uploadPath = createTransparentLogoPng(sourcePath);
            }
            String uploadedPath = CompanyCustomizationManager.uploadBadgeTemplateImage(uploadPath);
            badgeLogoPathField.setText(uploadedPath);
            badgeElementImageField.setText(uploadedPath);
            refreshBadgePreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to upload badge logo.\n\n" + ex.getMessage(), "Badge Logo Upload", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Path createTransparentLogoPng(Path sourcePath) throws IOException {
        BufferedImage source = ImageIO.read(sourcePath.toFile());
        if (source == null) {
            throw new IOException("The selected logo file is not a supported image.");
        }
        BufferedImage transparent = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                int brightness = Math.max(red, Math.max(green, blue));
                int colorSpread = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
                if (brightness >= 248 && colorSpread <= 18) {
                    alpha = 0;
                } else if (brightness >= 236 && colorSpread <= 24) {
                    alpha = Math.min(alpha, Math.max(0, (248 - brightness) * 18));
                }
                transparent.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        Path output = Files.createTempFile("smartstock-badge-logo-transparent-", ".png");
        ImageIO.write(transparent, "png", output.toFile());
        return output;
    }

    private void selectUploadedBadgeLogo() {
        try {
            List<CompanyCustomizationManager.UploadedImageOption> logos = CompanyCustomizationManager.listUploadedCompanyLogos();
            if (logos.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "No uploaded company logos were found in Storage or saved company settings.",
                        "Select Badge Logo",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            JList<CompanyCustomizationManager.UploadedImageOption> logoList = new JList<>(logos.toArray(new CompanyCustomizationManager.UploadedImageOption[0]));
            logoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            logoList.setVisibleRowCount(Math.min(logos.size(), 10));
            logoList.setSelectedIndex(0);

            int result = JOptionPane.showConfirmDialog(
                    this,
                    new JScrollPane(logoList),
                    "Select Badge Logo",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );
            if (result != JOptionPane.OK_OPTION || logoList.getSelectedValue() == null) {
                return;
            }

            badgeLogoPathField.setText(logoList.getSelectedValue().url());
            refreshBadgePreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to list uploaded logos.\n\n" + ex.getMessage(),
                    "Select Badge Logo",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void clearBadgeLogo() {
        badgeLogoPathField.setText("");
        refreshBadgePreview();
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

    private void selectUploadedLogo() {
        try {
            List<CompanyCustomizationManager.UploadedImageOption> logos = CompanyCustomizationManager.listUploadedCompanyLogos();
            if (logos.isEmpty()) {
                JOptionPane.showMessageDialog(
                        this,
                        "No uploaded company logos were found in Storage or saved company settings.",
                        "Select Uploaded Logo",
                        JOptionPane.INFORMATION_MESSAGE
                );
                return;
            }

            JList<CompanyCustomizationManager.UploadedImageOption> logoList = new JList<>(logos.toArray(new CompanyCustomizationManager.UploadedImageOption[0]));
            logoList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            logoList.setVisibleRowCount(Math.min(logos.size(), 10));
            logoList.setSelectedIndex(0);

            int result = JOptionPane.showConfirmDialog(
                    this,
                    new JScrollPane(logoList),
                    "Select Uploaded Logo",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE
            );
            if (result != JOptionPane.OK_OPTION || logoList.getSelectedValue() == null) {
                return;
            }

            String selectedUrl = logoList.getSelectedValue().url();
            logoPathField.setText(selectedUrl);
            showLogoBox.setSelected(true);
            updateLogoPreview(selectedUrl);
            refreshSamplePreview();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to list uploaded logos.\n\n" + ex.getMessage(),
                    "Select Uploaded Logo",
                    JOptionPane.ERROR_MESSAGE
            );
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

        BufferedImage image = ImageCacheManager.loadImage(logoPath);
        if (image == null) {
            return;
        }
        Image scaled = image.getScaledInstance(210, -1, Image.SCALE_SMOOTH);
        logoPreviewLabel.setText("");
        logoPreviewLabel.setIcon(new ImageIcon(scaled));
    }

    private void wireLivePreview() {
        DocumentListener previewDocumentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshAccountPaymentReceiptPreview();
                refreshSlipPreview();
                refreshQuotationInvoicePrintPreview();
                refreshBadgePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshAccountPaymentReceiptPreview();
                refreshSlipPreview();
                refreshQuotationInvoicePrintPreview();
                refreshBadgePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshAccountPaymentReceiptPreview();
                refreshSlipPreview();
                refreshQuotationInvoicePrintPreview();
                refreshBadgePreview();
            }
        };

        companyNameField.getDocument().addDocumentListener(previewDocumentListener);
        companyAddressLine1Field.getDocument().addDocumentListener(previewDocumentListener);
        companyAddressLine2Field.getDocument().addDocumentListener(previewDocumentListener);
        companyAddressLine3Field.getDocument().addDocumentListener(previewDocumentListener);
        companyPhoneLine1Field.getDocument().addDocumentListener(previewDocumentListener);
        companyPhoneLine2Field.getDocument().addDocumentListener(previewDocumentListener);
        companyEmailLine1Field.getDocument().addDocumentListener(previewDocumentListener);
        companyEmailLine2Field.getDocument().addDocumentListener(previewDocumentListener);
        companyMottoLine1Field.getDocument().addDocumentListener(previewDocumentListener);
        companyMottoLine2Field.getDocument().addDocumentListener(previewDocumentListener);
        headerLineField.getDocument().addDocumentListener(previewDocumentListener);
        footerLineField.getDocument().addDocumentListener(previewDocumentListener);
        receiptStartCounterField.getDocument().addDocumentListener(previewDocumentListener);
        vatFixedRatePercentField.getDocument().addDocumentListener(previewDocumentListener);
        logoPathField.getDocument().addDocumentListener(previewDocumentListener);
        badgeCompanyNameField.getDocument().addDocumentListener(previewDocumentListener);
        badgeLogoPathField.getDocument().addDocumentListener(previewDocumentListener);
        badgeQuoteField.getDocument().addDocumentListener(previewDocumentListener);
        badgeSignatoryNameField.getDocument().addDocumentListener(previewDocumentListener);
        badgeSignatoryTitleField.getDocument().addDocumentListener(previewDocumentListener);
        badgeBackInstructionsField.getDocument().addDocumentListener(previewDocumentListener);
        badgeMagStripeTrack1Field.getDocument().addDocumentListener(previewDocumentListener);
        badgeMagStripeTrack2Field.getDocument().addDocumentListener(previewDocumentListener);
        badgeMagStripeTrack3Field.getDocument().addDocumentListener(previewDocumentListener);
        badgeMagStripeCommandField.getDocument().addDocumentListener(previewDocumentListener);

        showLogoBox.addActionListener(e -> refreshSamplePreview());
        showSaleIdBox.addActionListener(e -> refreshSamplePreview());
        showDeviceBox.addActionListener(e -> refreshSamplePreview());
        showCustomerBox.addActionListener(e -> refreshSamplePreview());
        showSkuBox.addActionListener(e -> refreshSamplePreview());
        showItemDiscountBox.addActionListener(e -> refreshSamplePreview());
        showPaymentStatusBox.addActionListener(e -> refreshSamplePreview());
        vatEnabledBox.addActionListener(e -> refreshSamplePreview());
        vatUseDepartmentRatesBox.addActionListener(e -> refreshSamplePreview());

        accountPaymentReceiptTitleField.getDocument().addDocumentListener(previewDocumentListener);
        accountPaymentReceiptShowUserBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowCustomerBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowAccountNumberBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowMethodBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowReferenceBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowDeviceBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowDrawerBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowAllocationsBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowBalanceBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());
        accountPaymentReceiptShowBarcodeBox.addActionListener(e -> refreshAccountPaymentReceiptPreview());

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

        quotationPrintTitleField.getDocument().addDocumentListener(previewDocumentListener);
        quotationValidityNoteField.getDocument().addDocumentListener(previewDocumentListener);
        invoicePrintTitleField.getDocument().addDocumentListener(previewDocumentListener);
        salesDeliveryPrintTitleField.getDocument().addDocumentListener(previewDocumentListener);
        quotationInvoiceFooterNoteField.getDocument().addDocumentListener(previewDocumentListener);
        quotationInvoiceShowSignaturesBox.addActionListener(e -> refreshQuotationInvoicePrintPreview());

        badgeShowQuoteBox.addActionListener(e -> refreshBadgePreview());
        badgeShowEmployeeIdBox.addActionListener(e -> refreshBadgePreview());
        badgeShowIssueDateBox.addActionListener(e -> refreshBadgePreview());
        badgeShowBarcodeBox.addActionListener(e -> refreshBadgePreview());
        badgeShowBadgeTextBox.addActionListener(e -> refreshBadgePreview());
        badgeMagStripeEnabledBox.addActionListener(e -> refreshBadgePreview());
    }

    private void refreshSamplePreview() {
        if (loadingSettings) {
            return;
        }

        CompanyCustomizationManager.ReceiptSettings previewSettings = getSettingsFromFields();
        CompanyCustomizationManager.setPreviewOverrideSettings(previewSettings);
        try {
            ReceiptData sampleReceipt = createSampleReceipt();
            sampleReceiptPaperPanel.setReceiptText(ReceiptFormatter.formatText(sampleReceipt), false, sampleReceipt.getReceiptNumber());
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

    private void refreshAccountPaymentReceiptPreview() {
        if (loadingSettings) {
            return;
        }
        CompanyCustomizationManager.ReceiptSettings receiptSettings = getSettingsFromFields();
        CompanyCustomizationManager.AccountPaymentReceiptSettings paymentSettings = getAccountPaymentReceiptSettingsFromFields();
        AccountPaymentReceiptData sampleReceipt = createSampleAccountPaymentReceipt();
        String barcodeText = paymentSettings.showBarcode() ? sampleReceipt.getPaymentId() : "";
        sampleAccountPaymentReceipt40Panel.setReceiptText(
                AccountPaymentReceiptFormatter.formatText(sampleReceipt, receiptSettings, paymentSettings),
                false,
                barcodeText
        );
        sampleAccountPaymentReceiptLetterPanel.setReceiptText(
                AccountPaymentReceiptFormatter.formatLetterText(sampleReceipt, receiptSettings, paymentSettings),
                true,
                barcodeText
        );
        updateAccountPaymentReceiptLogoPreview(receiptSettings);
    }

    private void refreshQuotationInvoicePrintPreview() {
        if (loadingSettings) {
            return;
        }
        CompanyCustomizationManager.ReceiptSettings receiptSettings = getSettingsFromFields();
        CompanyCustomizationManager.QuotationInvoicePrintSettings printSettings = getQuotationInvoicePrintSettingsFromFields();
        updateSalesDocumentPreview(sampleQuotationPane, QuotationInvoiceDocumentBuilder.buildSampleQuotation(receiptSettings, printSettings));
        updateSalesDocumentPreview(sampleInvoicePane, QuotationInvoiceDocumentBuilder.buildSampleInvoice(receiptSettings, printSettings));
        updateSalesDocumentPreview(sampleSalesDeliveryPane, QuotationInvoiceDocumentBuilder.buildSampleDelivery(receiptSettings, printSettings));
    }

    private void updateSalesDocumentPreview(JEditorPane pane, String html) {
        pane.setText(utils.HtmlImageSourceResolver.resolveForSwing(html));
        pane.setCaretPosition(0);
    }

    private void refreshBadgePreview() {
        if (loadingSettings) {
            return;
        }
        try {
            CompanyCustomizationManager.BadgeTemplateSettings settings = getBadgeTemplateSettingsFromFields();
            BadgePrintService.EmployeeBadgeData sampleEmployee = new BadgePrintService.EmployeeBadgeData(
                    42,
                    "D-Bhudoo",
                    "Diana Devi Bhudoo",
                    "Diana",
                    "Devi",
                    "Bhudoo",
                    "diana@example.com",
                    "",
                    "SSB1ABC123DEF456GH",
                    "",
                    0,
                    "Manager",
                    "Main Store"
            );
            if (sampleBadgeFrontPanel != null) {
                sampleBadgeFrontPanel.setPreview(BadgePrintService.renderFront(sampleEmployee, settings), settings);
            }
            if (sampleBadgeBackPanel != null) {
                sampleBadgeBackPanel.setPreview(BadgePrintService.renderBack(sampleEmployee, settings), settings);
            }
        } catch (Exception ex) {
            if (sampleBadgeFrontPanel != null) {
                sampleBadgeFrontPanel.setError();
            }
            if (sampleBadgeBackPanel != null) {
                sampleBadgeBackPanel.setError();
            }
        }
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

    private AccountPaymentReceiptData createSampleAccountPaymentReceipt() {
        return new AccountPaymentReceiptData(
                42,
                managers.SessionManager.getCurrentLocationId(),
                "PAY-000042",
                Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 15, 10)),
                "Main Store",
                "Sample Cashier",
                "Alex Customer",
                "C-000100",
                "alex.customer@example.com",
                "CASH",
                "Drawer closeout",
                "POS-DEMO",
                "Front Register",
                new BigDecimal("125.00"),
                new BigDecimal("75.00"),
                List.of(
                        new AccountPaymentReceiptData.AllocationLine(
                                "Sale 0001-0001-000101",
                                new BigDecimal("80.00"),
                                new BigDecimal("150.00"),
                                new BigDecimal("150.00"),
                                "PAID",
                                Timestamp.valueOf(LocalDateTime.of(2026, 4, 18, 11, 25))
                        ),
                        new AccountPaymentReceiptData.AllocationLine(
                                "Invoice INV-MAIN-POS1-000088",
                                new BigDecimal("45.00"),
                                new BigDecimal("120.00"),
                                new BigDecimal("45.00"),
                                "UNPAID",
                                Timestamp.valueOf(LocalDateTime.of(2026, 4, 20, 13, 40))
                        )
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

    private void updateAccountPaymentReceiptLogoPreview(CompanyCustomizationManager.ReceiptSettings settings) {
        sampleAccountPaymentReceipt40Panel.setLogo(null, false);
        sampleAccountPaymentReceiptLetterPanel.setLogo(null, false);
        if (settings == null || !settings.showLogo() || settings.logoPath().isBlank()) {
            return;
        }

        sampleAccountPaymentReceipt40Panel.setLogoLoading(true);
        sampleAccountPaymentReceiptLetterPanel.setLogoLoading(true);
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return CompanyCustomizationManager.loadReceiptLogo(settings);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage logo = get();
                    sampleAccountPaymentReceipt40Panel.setLogo(logo, false);
                    sampleAccountPaymentReceiptLetterPanel.setLogo(logo, false);
                } catch (Exception ex) {
                    sampleAccountPaymentReceipt40Panel.setLogo(null, false);
                    sampleAccountPaymentReceiptLetterPanel.setLogo(null, false);
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

    private class BadgeTemplateEditorPanel extends JPanel {
        private static final int CARD_WIDTH = 638;
        private static final int CARD_HEIGHT = 1013;
        private final String side;
        private final int maxCardWidth;
        private BufferedImage image;
        private CompanyCustomizationManager.BadgeTemplateSettings settings;
        private String selectedElementId;
        private Point dragStart;
        private Map<String, Rectangle> dragStartRects = new LinkedHashMap<>();
        private boolean resizing;

        BadgeTemplateEditorPanel(String side, int maxCardWidth) {
            this.side = side;
            this.maxCardWidth = maxCardWidth;
            int preferredHeight = (int) Math.round(maxCardWidth * (CARD_HEIGHT / (double) CARD_WIDTH)) + 24;
            setPreferredSize(new Dimension(maxCardWidth + 24, preferredHeight));
            setBackground(new Color(248, 250, 252));
            setToolTipText("Click an element, shift-click to select multiple, drag to move, or drag the active element handle to resize.");

            MouseAdapter mouseAdapter = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    String hitElementId = hitTest(e.getPoint());
                    if (e.isShiftDown()) {
                        updateShiftSelection(hitElementId);
                    } else {
                        updateNormalSelection(hitElementId);
                    }
                    selectedElementId = badgeSelectedTemplateElementId;
                    updateBadgeFontControls();
                    if (hitElementId == null || selectedElementId == null || selectedElementId.isBlank()) {
                        repaint();
                        return;
                    }
                    dragStart = toCardPoint(e.getPoint());
                    dragStartRects = selectedRects();
                    Rectangle activeRect = BadgePrintService.layoutRect(settings, selectedElementId);
                    resizing = badgeSelectedTemplateElementIds.size() == 1 && isOnResizeHandle(e.getPoint(), activeRect);
                    repaint();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (selectedElementId == null || dragStart == null || dragStartRects.isEmpty()) {
                        return;
                    }
                    Point current = toCardPoint(e.getPoint());
                    int dx = current.x - dragStart.x;
                    int dy = current.y - dragStart.y;
                    if (resizing) {
                        Rectangle dragStartRect = dragStartRects.get(selectedElementId);
                        if (dragStartRect == null) {
                            return;
                        }
                        Rectangle updated = new Rectangle(dragStartRect);
                        updated.width += dx;
                        updated.height += dy;
                        badgeLayoutData = BadgePrintService.updateLayoutRect(badgeLayoutData, selectedElementId, updated);
                    } else {
                        for (Map.Entry<String, Rectangle> entry : dragStartRects.entrySet()) {
                            Rectangle updated = new Rectangle(entry.getValue());
                            updated.x += dx;
                            updated.y += dy;
                            badgeLayoutData = BadgePrintService.updateLayoutRect(badgeLayoutData, entry.getKey(), updated);
                        }
                    }
                    refreshBadgePreview();
                    updateBadgeFontControls();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragStart = null;
                    dragStartRects = new LinkedHashMap<>();
                    resizing = false;
                }
            };
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }

        void setPreview(BufferedImage image, CompanyCustomizationManager.BadgeTemplateSettings settings) {
            this.image = image;
            this.settings = settings;
            repaint();
        }

        void setError() {
            image = null;
            repaint();
        }

        private void updateNormalSelection(String hitElementId) {
            if (hitElementId == null) {
                badgeSelectedTemplateElementIds.clear();
                badgeSelectedTemplateElementId = "";
                return;
            }
            if (badgeSelectedTemplateElementIds.contains(hitElementId)) {
                removeOtherSideSelections();
                badgeSelectedTemplateElementId = hitElementId;
                return;
            }
            badgeSelectedTemplateElementIds.clear();
            badgeSelectedTemplateElementIds.add(hitElementId);
            badgeSelectedTemplateElementId = hitElementId;
        }

        private void updateShiftSelection(String hitElementId) {
            if (hitElementId == null) {
                return;
            }
            removeOtherSideSelections();
            if (badgeSelectedTemplateElementIds.contains(hitElementId)) {
                badgeSelectedTemplateElementIds.remove(hitElementId);
                if (hitElementId.equals(badgeSelectedTemplateElementId)) {
                    badgeSelectedTemplateElementId = badgeSelectedTemplateElementIds.stream().reduce((first, second) -> second).orElse("");
                }
            } else {
                badgeSelectedTemplateElementIds.add(hitElementId);
                badgeSelectedTemplateElementId = hitElementId;
            }
        }

        private void removeOtherSideSelections() {
            List<String> toRemove = new ArrayList<>();
            for (String elementId : badgeSelectedTemplateElementIds) {
                BadgePrintService.BadgeElement element = BadgePrintService.elementForId(elementId);
                if (element == null || !side.equals(element.side())) {
                    toRemove.add(elementId);
                }
            }
            badgeSelectedTemplateElementIds.removeAll(toRemove);
        }

        private Map<String, Rectangle> selectedRects() {
            LinkedHashMap<String, Rectangle> rects = new LinkedHashMap<>();
            for (String elementId : badgeSelectedTemplateElementIds) {
                BadgePrintService.BadgeElement element = BadgePrintService.elementForId(elementId);
                if (element != null && side.equals(element.side())) {
                    rects.put(elementId, BadgePrintService.layoutRect(settings, elementId));
                }
            }
            return rects;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                Rectangle card = cardBounds();
                if (image == null) {
                    g.setColor(new Color(75, 85, 99));
                    g.drawString("Preview unavailable", 24, 40);
                    return;
                }
                g.drawImage(image, card.x, card.y, card.width, card.height, null);
                paintNonPrintingGuides(g, card);
                paintElementOutlines(g, card);
            } finally {
                g.dispose();
            }
        }

        private void paintNonPrintingGuides(Graphics2D g, Rectangle card) {
            double scale = card.width / (double) CARD_WIDTH;
            Stroke previousStroke = g.getStroke();
            Composite previousComposite = g.getComposite();
            Font previousFont = g.getFont();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.78f));
            g.setStroke(new BasicStroke(
                    1.6f,
                    BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER,
                    10f,
                    new float[]{7f, 5f},
                    0f
            ));

            int punchWidth = (int) Math.round(170 * scale);
            int punchHeight = (int) Math.round(36 * scale);
            int punchX = card.x + (card.width - punchWidth) / 2;
            int punchY = card.y + (int) Math.round(24 * scale);
            g.setColor(new Color(37, 99, 235));
            g.drawRoundRect(punchX, punchY, punchWidth, punchHeight, punchHeight, punchHeight);
            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            g.drawString("HOLE PUNCH - DO NOT PRINT", punchX, Math.max(card.y + 12, punchY - 5));

            if ("back".equals(side) && settings != null && settings.magStripeEnabled()) {
                int stripeX = card.x + (int) Math.round(420 * scale);
                int stripeWidth = (int) Math.round(118 * scale);
                int stripeY = card.y + (int) Math.round(42 * scale);
                int stripeHeight = card.height - (int) Math.round(84 * scale);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
                g.setColor(Color.BLACK);
                g.fillRect(stripeX, stripeY, stripeWidth, stripeHeight);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.82f));
                g.setColor(new Color(17, 24, 39));
                g.drawRect(stripeX, stripeY, stripeWidth, stripeHeight);
                Graphics2D labelGraphics = (Graphics2D) g.create();
                labelGraphics.rotate(Math.PI / 2, stripeX + stripeWidth / 2.0, stripeY + stripeHeight / 2.0);
                labelGraphics.drawString(
                        "MAGNETIC STRIPE - DO NOT PRINT",
                        stripeX - (int) Math.round(210 * scale),
                        stripeY + stripeHeight / 2
                );
                labelGraphics.dispose();
            }

            g.setFont(previousFont);
            g.setStroke(previousStroke);
            g.setComposite(previousComposite);
        }

        private void paintElementOutlines(Graphics2D g, Rectangle card) {
            double scale = card.width / (double) CARD_WIDTH;
            for (BadgePrintService.BadgeElement element : BadgePrintService.elementsForSideInLayerOrder(settings, side)) {
                if (!BadgePrintService.elementVisible(settings, element.id())) {
                    continue;
                }
                Rectangle source = BadgePrintService.layoutRect(settings, element.id());
                Rectangle rect = new Rectangle(
                        card.x + (int) Math.round(source.x * scale),
                        card.y + (int) Math.round(source.y * scale),
                        (int) Math.round(source.width * scale),
                        (int) Math.round(source.height * scale)
                );
                boolean selected = badgeSelectedTemplateElementIds.contains(element.id());
                boolean active = element.id().equals(selectedElementId);
                g.setColor(active ? new Color(37, 99, 235) : selected ? new Color(59, 130, 246) : new Color(255, 112, 0));
                g.setStroke(new BasicStroke(selected ? 2f : 1f));
                g.drawRect(rect.x, rect.y, rect.width, rect.height);
                g.setFont(new Font("SansSerif", Font.BOLD, 10));
                g.drawString(element.label(), rect.x + 3, Math.max(card.y + 12, rect.y - 3));
                if (active) {
                    g.fillRect(rect.x + rect.width - 6, rect.y + rect.height - 6, 8, 8);
                }
            }
        }

        private String hitTest(Point point) {
            Rectangle card = cardBounds();
            double scale = card.width / (double) CARD_WIDTH;
            List<BadgePrintService.BadgeElement> elements = BadgePrintService.elementsForSideInLayerOrder(settings, side);
            for (int i = elements.size() - 1; i >= 0; i--) {
                BadgePrintService.BadgeElement element = elements.get(i);
                if (!BadgePrintService.elementVisible(settings, element.id())) {
                    continue;
                }
                Rectangle source = BadgePrintService.layoutRect(settings, element.id());
                Rectangle rect = new Rectangle(
                        card.x + (int) Math.round(source.x * scale),
                        card.y + (int) Math.round(source.y * scale),
                        (int) Math.round(source.width * scale),
                        (int) Math.round(source.height * scale)
                );
                if (rect.contains(point)) {
                    return element.id();
                }
            }
            return null;
        }

        private boolean isOnResizeHandle(Point point, Rectangle cardRect) {
            Rectangle card = cardBounds();
            double scale = card.width / (double) CARD_WIDTH;
            Rectangle handle = new Rectangle(
                    card.x + (int) Math.round((cardRect.x + cardRect.width) * scale) - 10,
                    card.y + (int) Math.round((cardRect.y + cardRect.height) * scale) - 10,
                    18,
                    18
            );
            return handle.contains(point);
        }

        private Point toCardPoint(Point point) {
            Rectangle card = cardBounds();
            double scale = CARD_WIDTH / (double) card.width;
            return new Point(
                    (int) Math.round((point.x - card.x) * scale),
                    (int) Math.round((point.y - card.y) * scale)
            );
        }

        private Rectangle cardBounds() {
            int width = Math.min(getWidth() - 16, maxCardWidth);
            int height = (int) Math.round(width * (CARD_HEIGHT / (double) CARD_WIDTH));
            int x = Math.max(8, (getWidth() - width) / 2);
            int y = Math.max(8, (getHeight() - height) / 2);
            return new Rectangle(x, y, width, height);
        }
    }

    private static class CustomOrderSlipPreviewPanel extends JPanel {
        private CustomOrderSlipData slipData = CustomOrderSlipBuilder.sample();
        private CompanyCustomizationManager.ReceiptSettings receiptSettings;
        private CompanyCustomizationManager.CustomOrderSlipSettings slipSettings;
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
                if (receiptSettings == null || slipSettings == null) {
                    g.setColor(new Color(100,116,139));
                    g.drawString("Loading preview…",20,30);
                    return;
                }
                CustomOrderSlipRenderer.paintSlip(g, 20, 20, getWidth() - 40, 390, slipData, receiptSettings, slipSettings, logo);
            } finally {
                g.dispose();
            }
        }
    }
}
