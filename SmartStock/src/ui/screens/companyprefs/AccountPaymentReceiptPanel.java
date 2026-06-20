package ui.screens.companyprefs;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Font;

public class AccountPaymentReceiptPanel extends JPanel {
    public AccountPaymentReceiptPanel(
            JTextField titleField,
            JCheckBox showUserBox,
            JCheckBox showCustomerBox,
            JCheckBox showAccountNumberBox,
            JCheckBox showMethodBox,
            JCheckBox showReferenceBox,
            JCheckBox showDeviceBox,
            JCheckBox showDrawerBox,
            JCheckBox showAllocationsBox,
            JCheckBox showBalanceBox,
            JCheckBox showBarcodeBox
    ) {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel sectionLabel = new JLabel("Account Payment Receipt");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(this, sectionLabel, 0);
        addRow(this, 1, "Receipt Title", titleField);

        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        fieldsPanel.setOpaque(false);
        fieldsPanel.add(showUserBox);
        fieldsPanel.add(showCustomerBox);
        fieldsPanel.add(showAccountNumberBox);
        fieldsPanel.add(showMethodBox);
        fieldsPanel.add(showReferenceBox);
        fieldsPanel.add(showDeviceBox);
        fieldsPanel.add(showDrawerBox);
        fieldsPanel.add(showAllocationsBox);
        fieldsPanel.add(showBalanceBox);
        fieldsPanel.add(showBarcodeBox);
        addWide(this, fieldsPanel, 2);
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
