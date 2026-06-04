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
import services.BadgePrintService;
import services.OfflineWriteGuard;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;
import utils.ImageCacheManager;
import ui.screens.companyprefs.CompanyIdentityPanel;
import ui.screens.companyprefs.CustomOrderDepositPanel;
import ui.screens.companyprefs.CustomOrderReceiptPanel;
import ui.screens.companyprefs.SaleReceiptPanel;

import javax.imageio.ImageIO;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final String NAV_SALE = "Sale";
    private static final String NAV_SALE_RECEIPT_FORMATTING = "Sale Receipt & Formatting";
    private static final String NAV_CUSTOM_ORDERS = "Custom Orders";
    private static final String NAV_CUSTOM_ORDER_DEPOSIT_REFUND = "Order Deposit & Refund Approval";
    private static final String NAV_CUSTOM_ORDER_SLIP_FORMATTING = "Receipt/Slip Formatting";
    private static final int BADGE_CARD_WIDTH = 638;
    private static final int BADGE_CARD_HEIGHT = 1013;

    private static String[] badgeTemplateSlotLabels() {
        String[] labels = new String[CompanyCustomizationManager.badgeTemplateCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = CompanyCustomizationManager.badgeTemplateDisplayName(i);
        }
        return labels;
    }

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
    private String badgeLayoutData = "";
    private final String[] badgeTemplateLayouts = new String[CompanyCustomizationManager.badgeTemplateCount()];
    private int badgeTemplateSlotIndex = 0;
    private boolean updatingBadgeTemplateSlot = false;
    private final JComboBox<String> badgeTemplateSlotBox = new JComboBox<>(badgeTemplateSlotLabels());
    private String badgeSelectedTemplateElementId = "";
    private final Set<String> badgeSelectedTemplateElementIds = new LinkedHashSet<>();
    private final Map<String, JCheckBox> badgeElementVisibilityBoxes = new LinkedHashMap<>();
    private boolean updatingBadgeFontControls = false;
    private final JComboBox<String> badgeFontFamilyBox = new JComboBox<>(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
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
        addNodeIfPermitted(root, NAV_EMPLOYEE_BADGES);
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
        if (canAccessPreferenceSection(NAV_EMPLOYEE_BADGES)) {
            rightContentPanel.add(buildEmployeeBadgesScreen(), NAV_EMPLOYEE_BADGES);
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
            case NAV_COMPANY_IDENTITY, NAV_EMPLOYEE_BADGES, NAV_SALE, NAV_SALE_RECEIPT_FORMATTING, NAV_CUSTOM_ORDERS,
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
                NAV_EMPLOYEE_BADGES,
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

    private JPanel buildEmployeeBadgesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        badgeLogoPathField.setEditable(false);
        contentPanel.add(buildBadgeEditorLaunchPanel(), BorderLayout.NORTH);
        contentPanel.add(buildBadgeMagStripePanel(), BorderLayout.CENTER);
        return contentPanel;
    }

    private JPanel buildBadgeEditorLaunchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 12));
        panel.setOpaque(false);
        JButton openEditorButton = new JButton("Open Badge Template Editor");
        openEditorButton.addActionListener(e -> openBadgeTemplateEditorDialog());
        panel.add(openEditorButton);
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
        dialog.pack();
        dialog.setSize(new Dimension(760, 760));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private JPanel buildBadgePreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setPreferredSize(new Dimension(520, 680));
        panel.setMinimumSize(new Dimension(500, 640));
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
        headerPanel.add(buildBadgeGroupAlignmentToolbar(), BorderLayout.SOUTH);
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
        return toolbar;
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
        JButton selectUploadedButton = new JButton("Select Uploaded");
        JButton clearButton = new JButton("Clear Logo");
        buttonPanel.add(uploadButton);
        buttonPanel.add(selectUploadedButton);
        buttonPanel.add(clearButton);
        logoToolsPanel.add(buttonPanel, BorderLayout.EAST);
        panel.add(logoToolsPanel, BorderLayout.EAST);

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
        CompanyCustomizationManager.BadgeTemplateSettings badgeTemplateSettings = CompanyCustomizationManager.loadBadgeTemplateSettings();
        loadBadgeTemplateFields(badgeTemplateSettings);
        updateLogoPreview(settings.logoPath());
        loadingSettings = false;
        refreshSamplePreview();
        refreshSlipPreview();
        refreshBadgePreview();
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
            CompanyCustomizationManager.saveBadgeTemplateSettings(getBadgeTemplateSettingsForSave());
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
                refreshSlipPreview();
                refreshBadgePreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshSlipPreview();
                refreshBadgePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSamplePreview();
                refreshSlipPreview();
                refreshBadgePreview();
            }
        };

        companyNameField.getDocument().addDocumentListener(previewDocumentListener);
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

            if ("back".equals(side)) {
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
