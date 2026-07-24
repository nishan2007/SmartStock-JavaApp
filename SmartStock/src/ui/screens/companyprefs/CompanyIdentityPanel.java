package ui.screens.companyprefs;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CompanyIdentityPanel extends JPanel {
    public CompanyIdentityPanel(JTextField companyNameField,
                                JTextField mottoLine1Field,
                                JTextField mottoLine2Field,
                                JComponent logoPanel) {
        setLayout(new GridBagLayout());
        putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        setBackground(DeckersPalette.surface());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.ORANGE)),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, DeckersPalette.ORANGE),
                        new EmptyBorder(22, 24, 24, 24)
                )
        ));

        JLabel sectionLabel = new JLabel("Company Identity");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        sectionLabel.setForeground(DeckersPalette.text());
        sectionLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        addWide(this, sectionLabel, 0);

        JLabel description = new JLabel(
                "<html>Set the business name, receipt motto, and logo used throughout SmartStock.</html>"
        );
        description.setFont(new Font("SansSerif", Font.PLAIN, 14));
        description.setForeground(DeckersPalette.muted());
        description.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        addWide(this, description, 1);

        DeckersSwing.styleField(companyNameField);
        DeckersSwing.styleField(mottoLine1Field);
        DeckersSwing.styleField(mottoLine2Field);
        companyNameField.setPreferredSize(new Dimension(320, 36));
        mottoLine1Field.setPreferredSize(new Dimension(320, 36));
        mottoLine2Field.setPreferredSize(new Dimension(320, 36));

        addRow(this, 2, "Company name", companyNameField);
        addRow(this, 3, "Primary motto", mottoLine1Field);
        addRow(this, 4, "Secondary motto", mottoLine2Field);
        addRow(this, 5, "Company logo", logoPanel);
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        fieldLabel.setForeground(DeckersPalette.text());
        fieldLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.insets = new Insets(4, 0, 16, 22);
        labelConstraints.anchor = GridBagConstraints.WEST;
        panel.add(fieldLabel, labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.insets = new Insets(0, 0, 16, 0);
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(field, fieldConstraints);
    }

    private static void addWide(JPanel panel, JComponent component, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(row == 0 ? 0 : 4, 0, row == 0 ? 6 : 20, 0);
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(component, constraints);
    }
}
