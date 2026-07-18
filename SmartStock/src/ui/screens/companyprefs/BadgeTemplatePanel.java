package ui.screens.companyprefs;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class BadgeTemplatePanel extends JPanel {
    public BadgeTemplatePanel(
            JTextField companyNameField,
            JComponent logoSelector,
            JTextField quoteField,
            JTextField signatoryNameField,
            JTextField signatoryTitleField,
            JTextField backInstructionsField,
            JCheckBox magStripeEnabledBox,
            JTextField magStripeTrack1Field,
            JTextField magStripeTrack2Field,
            JTextField magStripeTrack3Field,
            JTextField magStripeCommandField,
            JCheckBox nfcEnabledBox,
            JTextField nfcPayloadField,
            JTextField nfcWriterCommandField,
            JTextField nfcVerifyCommandField
    ) {
        setLayout(new BorderLayout(0, 14));
        setOpaque(false);

        JLabel titleLabel = new JLabel("Employee Badge Template");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(titleLabel, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(4, 0, 0, 0));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 0, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        row = addRow(form, gbc, row, "Badge company name:", companyNameField);
        row = addRow(form, gbc, row, "Badge logo:", logoSelector);
        row = addRow(form, gbc, row, "Front quote:", quoteField);
        row = addRow(form, gbc, row, "Back instructions:", backInstructionsField);
        row = addRow(form, gbc, row, "Signature name:", signatoryNameField);
        row = addRow(form, gbc, row, "Signature title:", signatoryTitleField);

        JLabel stripeLabel = new JLabel("Magnetic Stripe");
        stripeLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 0, 4, 10);
        form.add(stripeLabel, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(6, 0, 6, 10);

        gbc.gridx = 1;
        gbc.gridy = row++;
        gbc.weightx = 1;
        form.add(magStripeEnabledBox, gbc);

        row = addRow(form, gbc, row, "Track 1 template:", magStripeTrack1Field);
        row = addRow(form, gbc, row, "Track 2 template:", magStripeTrack2Field);
        row = addRow(form, gbc, row, "Track 3 template:", magStripeTrack3Field);
        row = addRow(form, gbc, row, "Writer command:", magStripeCommandField);

        JLabel nfcLabel = new JLabel("RFID / NFC");
        nfcLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(16, 0, 4, 10);
        form.add(nfcLabel, gbc);
        gbc.gridwidth = 1;
        gbc.insets = new Insets(6, 0, 6, 10);
        gbc.gridx = 1;
        gbc.gridy = row++;
        form.add(nfcEnabledBox, gbc);
        row = addRow(form, gbc, row, "Payload template:", nfcPayloadField);
        row = addRow(form, gbc, row, "Writer command:", nfcWriterCommandField);
        row = addRow(form, gbc, row, "Verification command:", nfcVerifyCommandField);

        JTextArea help = new JTextArea("Placeholders: {badge_id}, {employee_id}, {full_name}, {first_name}, {last_name}, {role}, {company}, {issued_date}, {track1}, {track2}, {track3}.");
        help.setEditable(false);
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setOpaque(false);
        help.setForeground(new Color(75, 85, 99));
        help.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 1;
        gbc.gridy = row;
        gbc.weightx = 1;
        form.add(help, gbc);

        add(form, BorderLayout.CENTER);
    }

    private static int addRow(JPanel form, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        form.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(field, gbc);
        return row + 1;
    }
}
