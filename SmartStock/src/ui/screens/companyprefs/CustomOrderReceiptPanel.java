package ui.screens.companyprefs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CustomOrderReceiptPanel extends JPanel {
    public CustomOrderReceiptPanel(
            JCheckBox slipEnabledBox,
            JCheckBox slipAutoPrintBox,
            JTextField slipTitleField,
            JTextField slipContactLineField,
            JTextField slipEmailLineField,
            JTextField slipFooterNoteField,
            JTextField slipBlankDetailLinesField,
            JCheckBox slipShowLogoBox,
            JCheckBox slipShowOrderNumberBox,
            JCheckBox slipShowDueDateBox,
            JCheckBox slipShowCustomerPhoneBox,
            JCheckBox slipShowCustomerAccountBox,
            JCheckBox slipShowStoreBox,
            JCheckBox slipShowDeviceBox,
            JCheckBox slipShowCashierBox,
            JCheckBox slipShowLineItemsBox,
            JCheckBox slipShowPricingBox,
            JCheckBox slipShowPaymentSummaryBox,
            JCheckBox slipShowPaymentReferenceBox,
            JCheckBox slipShowTakenByBox,
            JCheckBox slipShowSignaturesBox
    ) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        add(buildSlipBehaviorPanel(slipEnabledBox, slipAutoPrintBox));
        add(Box.createVerticalStrut(16));
        add(buildSlipHeaderPanel(slipTitleField, slipContactLineField, slipEmailLineField, slipFooterNoteField, slipBlankDetailLinesField));
        add(Box.createVerticalStrut(16));
        add(buildSlipFieldsPanel(
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
        ));
    }

    private JPanel buildSlipBehaviorPanel(JCheckBox slipEnabledBox, JCheckBox slipAutoPrintBox) {
        JPanel panel = createSectionPanel("Slip Behavior");
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        optionsPanel.setOpaque(false);
        optionsPanel.add(slipEnabledBox);
        optionsPanel.add(slipAutoPrintBox);
        addWide(panel, optionsPanel, 1);
        return panel;
    }

    private JPanel buildSlipHeaderPanel(JTextField slipTitleField,
                                        JTextField slipContactLineField,
                                        JTextField slipEmailLineField,
                                        JTextField slipFooterNoteField,
                                        JTextField slipBlankDetailLinesField) {
        JPanel panel = createSectionPanel("Slip Header");
        addRow(panel, 1, "Title", slipTitleField);
        addRow(panel, 2, "Contact Line", slipContactLineField);
        addRow(panel, 3, "Email Line", slipEmailLineField);
        addRow(panel, 4, "Footer Note", slipFooterNoteField);
        addRow(panel, 5, "Blank Detail Lines", slipBlankDetailLinesField);
        return panel;
    }

    private JPanel buildSlipFieldsPanel(
            JCheckBox slipShowLogoBox,
            JCheckBox slipShowOrderNumberBox,
            JCheckBox slipShowDueDateBox,
            JCheckBox slipShowCustomerPhoneBox,
            JCheckBox slipShowCustomerAccountBox,
            JCheckBox slipShowStoreBox,
            JCheckBox slipShowDeviceBox,
            JCheckBox slipShowCashierBox,
            JCheckBox slipShowLineItemsBox,
            JCheckBox slipShowPricingBox,
            JCheckBox slipShowPaymentSummaryBox,
            JCheckBox slipShowPaymentReferenceBox,
            JCheckBox slipShowTakenByBox,
            JCheckBox slipShowSignaturesBox
    ) {
        JPanel panel = createSectionPanel("Fields to Print");
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2, 10, 8));
        fieldsPanel.setOpaque(false);
        fieldsPanel.add(slipShowLogoBox);
        fieldsPanel.add(slipShowOrderNumberBox);
        fieldsPanel.add(slipShowDueDateBox);
        fieldsPanel.add(slipShowCustomerPhoneBox);
        fieldsPanel.add(slipShowCustomerAccountBox);
        fieldsPanel.add(slipShowStoreBox);
        fieldsPanel.add(slipShowDeviceBox);
        fieldsPanel.add(slipShowCashierBox);
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
