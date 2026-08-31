package ui.screens.companyprefs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SaleReceiptPanel extends JPanel {
    public SaleReceiptPanel(JTextArea headerLineField,
                            JTextArea footerLineField,
                            JTextField receiptStartCounterField,
                            JTextField configPathField,
                            JTextField saleDiscountLimitPercentField,
                            JTextField saleReturnApprovalLimitField,
                            JCheckBox roundSalesToNearestTwentyBox,
                            JCheckBox alwaysPrintSaleReceiptBox,
                            JCheckBox showLogoBox,
                            JCheckBox showSaleIdBox,
                            JCheckBox showDeviceBox,
                            JCheckBox showCustomerBox,
                            JCheckBox showSkuBox,
                            JCheckBox showItemDiscountBox,
                            JCheckBox showPaymentStatusBox,
                            JCheckBox vatEnabledBox,
                            JCheckBox vatUseDepartmentRatesBox,
                            JTextField vatFixedRatePercentField) {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel sectionLabel = new JLabel("Receipt Formatting");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(this, sectionLabel, 0);

        addMultilineRow(this, 1, "Header Lines", headerLineField);
        addMultilineRow(this, 2, "Footer Lines", footerLineField);
        addRow(this, 3, "Receipt Counter Start", receiptStartCounterField);
        addRow(this, 4, "Discount Limit (%)", saleDiscountLimitPercentField);
        addRow(this, 5, "Return Approval Over", saleReturnApprovalLimitField);
        addRow(this, 6, "Fixed VAT (%)", vatFixedRatePercentField);
        addRow(this, 7, "Config File", configPathField);

        JPanel optionsPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        optionsPanel.setOpaque(false);
        optionsPanel.add(alwaysPrintSaleReceiptBox);
        optionsPanel.add(roundSalesToNearestTwentyBox);
        optionsPanel.add(showLogoBox);
        optionsPanel.add(showSaleIdBox);
        optionsPanel.add(showDeviceBox);
        optionsPanel.add(showCustomerBox);
        optionsPanel.add(showSkuBox);
        optionsPanel.add(showItemDiscountBox);
        optionsPanel.add(showPaymentStatusBox);
        optionsPanel.add(vatEnabledBox);
        optionsPanel.add(vatUseDepartmentRatesBox);
        addWide(this, optionsPanel, 8);
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field) {
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

    private static void addMultilineRow(JPanel panel, int row, String label, JTextArea field) {
        JScrollPane scrollPane = new JScrollPane(field);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setPreferredSize(new Dimension(320, 72));
        addRow(panel, row, label, scrollPane);
    }

    private static void addWide(JPanel panel, JComponent component, int row) {
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
}
