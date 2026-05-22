package ui.screens;

import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import Receipt.ReceiptItem;
import Receipt.CustomOrderSlipBuilder;
import Receipt.CustomOrderSlipData;
import Receipt.CustomOrderSlipRenderer;
import managers.CompanyCustomizationManager;
import managers.NavigationManager;
import managers.PermissionManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
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
    private final JTextField companyNameField = new JTextField();
    private final JTextField headerLineField = new JTextField();
    private final JTextField footerLineField = new JTextField();
    private final JTextField logoPathField = new JTextField();
    private final JTextField configPathField = new JTextField();
    private final JCheckBox showLogoBox = new JCheckBox("Show logo on receipt");
    private final JCheckBox showSaleIdBox = new JCheckBox("Show sale ID");
    private final JCheckBox showDeviceBox = new JCheckBox("Show device ID");
    private final JCheckBox showCustomerBox = new JCheckBox("Show customer/account");
    private final JCheckBox showSkuBox = new JCheckBox("Show SKU");
    private final JCheckBox showItemDiscountBox = new JCheckBox("Show item discounts");
    private final JCheckBox showPaymentStatusBox = new JCheckBox("Show payment status");
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
    private final JCheckBox slipShowLineItemsBox = new JCheckBox("Line items");
    private final JCheckBox slipShowPricingBox = new JCheckBox("Pricing");
    private final JCheckBox slipShowPaymentSummaryBox = new JCheckBox("Payment summary");
    private final JCheckBox slipShowPaymentReferenceBox = new JCheckBox("Payment reference");
    private final JCheckBox slipShowTakenByBox = new JCheckBox("Taken/delivered by");
    private final JCheckBox slipShowSignaturesBox = new JCheckBox("Signature lines");
    private final JLabel logoPreviewLabel = new JLabel("No Logo", SwingConstants.CENTER);
    private final ReceiptPreview.ReceiptPaperPanel sampleReceiptPaperPanel = new ReceiptPreview.ReceiptPaperPanel();
    private final CustomOrderSlipPreviewPanel sampleSlipPanel = new CustomOrderSlipPreviewPanel();
    private boolean loadingSettings = false;

    public CompanyCustomization() {
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

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Receipt Preferences", buildReceiptPreferencesScreen());
        tabs.addTab("Custom Order Slips", buildCustomOrderSlipPreferencesScreen());
        rootPanel.add(tabs, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");
        JButton saveButton = new JButton("Save");
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);
        buttonPanel.add(saveButton);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(rootPanel);

        refreshButton.addActionListener(e -> loadSettings());
        closeButton.addActionListener(e -> NavigationManager.showMainMenu(this));
        saveButton.addActionListener(e -> saveSettings());
        wireLivePreview();

        loadSettings();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildPreferencesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.add(buildCompanyIdentityPanel());
        panel.add(Box.createVerticalStrut(16));
        panel.add(buildReceiptFormattingPanel());
        panel.add(Box.createVerticalStrut(16));
        panel.add(buildCustomOrdersPanel());
        return panel;
    }

    private JPanel buildReceiptPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildPreferencesPanel(), BorderLayout.CENTER);
        contentPanel.add(buildSamplePreviewPanel(), BorderLayout.EAST);
        return contentPanel;
    }

    private JPanel buildCustomOrderSlipPreferencesScreen() {
        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildCustomOrderSlipPanel(), BorderLayout.CENTER);
        contentPanel.add(buildSlipPreviewPanel(), BorderLayout.EAST);
        return contentPanel;
    }

    private JPanel buildCompanyIdentityPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionLabel = new JLabel("Company Identity");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(panel, sectionLabel, 0);

        logoPathField.setEditable(false);
        addRow(panel, 1, "Company Name", companyNameField);
        addRow(panel, 2, "Company Logo", buildLogoFilePanel());

        return panel;
    }

    private JPanel buildCustomOrdersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionLabel = new JLabel("Custom Orders");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(panel, sectionLabel, 0);

        JPanel percentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        percentPanel.setOpaque(false);
        customOrderMinimumDepositPercentField.setPreferredSize(new Dimension(90, 30));
        percentPanel.add(customOrderMinimumDepositPercentField);
        percentPanel.add(new JLabel("%"));
        addRow(panel, 1, "Minimum Deposit", percentPanel);
        addRow(panel, 2, "Refund Approval Over", customOrderRefundApprovalLimitField);

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

        return panel;
    }

    private JPanel buildReceiptFormattingPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionLabel = new JLabel("Receipt Formatting");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(panel, sectionLabel, 0);

        configPathField.setEditable(false);
        configPathField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        configPathField.setText(CompanyCustomizationManager.getConfigPath().toString());

        addRow(panel, 1, "Header Line", headerLineField);
        addRow(panel, 2, "Footer Line", footerLineField);
        addRow(panel, 3, "Config File", configPathField);

        JPanel optionsPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        optionsPanel.setOpaque(false);
        optionsPanel.add(showLogoBox);
        optionsPanel.add(showSaleIdBox);
        optionsPanel.add(showDeviceBox);
        optionsPanel.add(showCustomerBox);
        optionsPanel.add(showSkuBox);
        optionsPanel.add(showItemDiscountBox);
        optionsPanel.add(showPaymentStatusBox);
        addWide(panel, optionsPanel, 4);

        return panel;
    }

    private JPanel buildCustomOrderSlipPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.add(buildSlipBehaviorPanel());
        panel.add(Box.createVerticalStrut(16));
        panel.add(buildSlipHeaderPanel());
        panel.add(Box.createVerticalStrut(16));
        panel.add(buildSlipFieldsPanel());
        return panel;
    }

    private JPanel buildSlipBehaviorPanel() {
        JPanel panel = createSectionPanel("Slip Behavior");
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        optionsPanel.setOpaque(false);
        optionsPanel.add(slipEnabledBox);
        optionsPanel.add(slipAutoPrintBox);
        addWide(panel, optionsPanel, 1);
        return panel;
    }

    private JPanel buildSlipHeaderPanel() {
        JPanel panel = createSectionPanel("Slip Header");
        addRow(panel, 1, "Title", slipTitleField);
        addRow(panel, 2, "Contact Line", slipContactLineField);
        addRow(panel, 3, "Email Line", slipEmailLineField);
        addRow(panel, 4, "Footer Note", slipFooterNoteField);
        addRow(panel, 5, "Blank Detail Lines", slipBlankDetailLinesField);
        return panel;
    }

    private JPanel buildSlipFieldsPanel() {
        JPanel panel = createSectionPanel("Fields to Print");
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        fieldsPanel.setOpaque(false);
        fieldsPanel.add(slipShowLogoBox);
        fieldsPanel.add(slipShowOrderNumberBox);
        fieldsPanel.add(slipShowDueDateBox);
        fieldsPanel.add(slipShowCustomerPhoneBox);
        fieldsPanel.add(slipShowLineItemsBox);
        fieldsPanel.add(slipShowPricingBox);
        fieldsPanel.add(slipShowPaymentSummaryBox);
        fieldsPanel.add(slipShowPaymentReferenceBox);
        fieldsPanel.add(slipShowTakenByBox);
        fieldsPanel.add(slipShowSignaturesBox);
        addWide(panel, fieldsPanel, 1);
        return panel;
    }

    private JPanel createSectionPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel sectionLabel = new JLabel(title);
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(panel, sectionLabel, 0);
        return panel;
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

        JScrollPane sampleScrollPane = new JScrollPane(sampleSlipPanel);
        sampleScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(sampleScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void addRow(JPanel panel, int row, String label, JComponent field) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        fieldLabel.setForeground(new Color(55, 65, 81));

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(0, 0, 12, 14);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(fieldLabel, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.insets = new Insets(0, 0, 12, 0);
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, fieldConstraints);
    }

    private void addWide(JPanel panel, JComponent component, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(row == 0 ? 0 : 8, 0, 14, 0);
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(component, constraints);
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
            CompanyCustomizationManager.clearPreviewOverrideSettings();
            CompanyCustomizationManager.saveReceiptSettings(getSettingsFromFields());
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
                showPaymentStatusBox.isSelected()
        );
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
        logoPathField.getDocument().addDocumentListener(previewDocumentListener);

        showLogoBox.addActionListener(e -> refreshSamplePreview());
        showSaleIdBox.addActionListener(e -> refreshSamplePreview());
        showDeviceBox.addActionListener(e -> refreshSamplePreview());
        showCustomerBox.addActionListener(e -> refreshSamplePreview());
        showSkuBox.addActionListener(e -> refreshSamplePreview());
        showItemDiscountBox.addActionListener(e -> refreshSamplePreview());
        showPaymentStatusBox.addActionListener(e -> refreshSamplePreview());

        slipEnabledBox.addActionListener(e -> refreshSlipPreview());
        slipAutoPrintBox.addActionListener(e -> refreshSlipPreview());
        slipShowLogoBox.addActionListener(e -> refreshSlipPreview());
        slipShowOrderNumberBox.addActionListener(e -> refreshSlipPreview());
        slipShowDueDateBox.addActionListener(e -> refreshSlipPreview());
        slipShowCustomerPhoneBox.addActionListener(e -> refreshSlipPreview());
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
        sampleSlipPanel.setSlip(
                CustomOrderSlipBuilder.sample(),
                getSettingsFromFields(),
                getSlipSettingsForPreview()
        );
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
                "R-S001-POS-DEMO-000123",
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
                new BigDecimal("21.37"),
                new BigDecimal("21.37"),
                BigDecimal.ZERO,
                new BigDecimal("25.00"),
                new BigDecimal("3.63"),
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
        if (!getSlipSettingsForPreview().showLogo() || settings.logoPath().isBlank()) {
            return;
        }
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return CompanyCustomizationManager.loadCompanyLogo(settings);
            }

            @Override
            protected void done() {
                try {
                    sampleSlipPanel.setLogo(get());
                } catch (Exception ex) {
                    sampleSlipPanel.setLogo(null);
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
