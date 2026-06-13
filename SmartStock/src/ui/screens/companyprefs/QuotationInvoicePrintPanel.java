package ui.screens.companyprefs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class QuotationInvoicePrintPanel extends JPanel {
    public QuotationInvoicePrintPanel(JTextField quotationTitleField,
                                     JTextField quotationValidityNoteField,
                                     JTextField invoiceTitleField,
                                     JTextField deliveryTitleField,
                                     JTextField footerNoteField,
                                     JCheckBox showSignaturesBox) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        add(buildTitlesPanel(quotationTitleField, quotationValidityNoteField, invoiceTitleField, deliveryTitleField, footerNoteField));
        add(Box.createVerticalStrut(16));
        add(buildOptionsPanel(showSignaturesBox));
    }

    private JPanel buildTitlesPanel(JTextField quotationTitleField,
                                    JTextField quotationValidityNoteField,
                                    JTextField invoiceTitleField,
                                    JTextField deliveryTitleField,
                                    JTextField footerNoteField) {
        JPanel panel = createSectionPanel("Quotation / Invoice Printouts");
        addRow(panel, 1, "Quotation Title", quotationTitleField);
        addRow(panel, 2, "Quotation Validity Note", quotationValidityNoteField);
        addRow(panel, 3, "Invoice Title", invoiceTitleField);
        addRow(panel, 4, "Delivery Bill Title", deliveryTitleField);
        addRow(panel, 5, "Footer Note", footerNoteField);
        return panel;
    }

    private JPanel buildOptionsPanel(JCheckBox showSignaturesBox) {
        JPanel panel = createSectionPanel("Print Options");
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 8, 8));
        optionsPanel.setOpaque(false);
        optionsPanel.add(showSignaturesBox);
        addWide(panel, optionsPanel, 1);
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
