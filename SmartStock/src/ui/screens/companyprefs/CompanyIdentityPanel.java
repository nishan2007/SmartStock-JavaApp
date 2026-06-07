package ui.screens.companyprefs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CompanyIdentityPanel extends JPanel {
    public CompanyIdentityPanel(JTextField companyNameField,
                                JTextField addressLine1Field,
                                JTextField addressLine2Field,
                                JTextField addressLine3Field,
                                JTextField phoneLine1Field,
                                JTextField phoneLine2Field,
                                JTextField emailLine1Field,
                                JTextField emailLine2Field,
                                JComponent logoPanel) {
        setLayout(new GridBagLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel sectionLabel = new JLabel("Company Identity");
        sectionLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        addWide(this, sectionLabel, 0);

        addRow(this, 1, "Company Name", companyNameField);
        addRow(this, 2, "Address Line 1", addressLine1Field);
        addRow(this, 3, "Address Line 2", addressLine2Field);
        addRow(this, 4, "Address Line 3", addressLine3Field);
        addRow(this, 5, "Phone Line 1", phoneLine1Field);
        addRow(this, 6, "Phone Line 2", phoneLine2Field);
        addRow(this, 7, "Email Line 1", emailLine1Field);
        addRow(this, 8, "Email Line 2", emailLine2Field);
        addRow(this, 9, "Company Logo", logoPanel);
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
