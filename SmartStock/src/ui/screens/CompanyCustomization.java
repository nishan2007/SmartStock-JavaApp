package ui.screens;

import Receipt.ReceiptData;
import Receipt.ReceiptFormatter;
import Receipt.ReceiptItem;
import managers.CompanyCustomizationManager;
import managers.NavigationManager;
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
    private final JLabel logoPreviewLabel = new JLabel("No Logo", SwingConstants.CENTER);
    private final ReceiptPreview.ReceiptPaperPanel sampleReceiptPaperPanel = new ReceiptPreview.ReceiptPaperPanel();
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

        JPanel contentPanel = new JPanel(new BorderLayout(18, 18));
        contentPanel.setOpaque(false);
        contentPanel.add(buildPreferencesPanel(), BorderLayout.CENTER);
        contentPanel.add(buildSamplePreviewPanel(), BorderLayout.EAST);
        rootPanel.add(contentPanel, BorderLayout.CENTER);

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
        return panel;
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
        updateLogoPreview(settings.logoPath());
        loadingSettings = false;
        refreshSamplePreview();
    }

    private void saveSettings() {
        try {
            CompanyCustomizationManager.clearPreviewOverrideSettings();
            CompanyCustomizationManager.saveReceiptSettings(getSettingsFromFields());
            JOptionPane.showMessageDialog(this, "Company preferences saved.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save company preferences.\n\n" + ex.getMessage(), "Company Preferences", JOptionPane.ERROR_MESSAGE);
        }
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
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshSamplePreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshSamplePreview();
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
}
