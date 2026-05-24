package ui.screens.companyprefs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SaleReceiptPanel extends JPanel {
    public SaleReceiptPanel(JTextField headerLineField,
                            JTextField footerLineField,
                            JTextField receiptStartCounterField,
                            JTextField configPathField,
                            JTextField saleDiscountLimitPercentField,
                            JTextField saleReturnApprovalLimitField,
                            JCheckBox showLogoBox,
                            JCheckBox showSaleIdBox,
                            JCheckBox showDeviceBox,
                            JCheckBox showCustomerBox,
                            JCheckBox showSkuBox,
                            JCheckBox showItemDiscountBox,
                            JCheckBox showPaymentStatusBox) {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel sectionLabel = new JLabel("Receipt Formatting");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(this, sectionLabel, 0);

        addRow(this, 1, "Header Line", headerLineField);
        addRow(this, 2, "Footer Line", footerLineField);
        addRow(this, 3, "Receipt Counter Start", receiptStartCounterField);
        addRow(this, 4, "Discount Limit (%)", saleDiscountLimitPercentField);
        addRow(this, 5, "Return Approval Over", saleReturnApprovalLimitField);
        addRow(this, 6, "Config File", configPathField);

        JPanel optionsPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        optionsPanel.setOpaque(false);
        optionsPanel.add(showLogoBox);
        optionsPanel.add(showSaleIdBox);
        optionsPanel.add(showDeviceBox);
        optionsPanel.add(showCustomerBox);
        optionsPanel.add(showSkuBox);
        optionsPanel.add(showItemDiscountBox);
        optionsPanel.add(showPaymentStatusBox);
        addWide(this, optionsPanel, 7);
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
