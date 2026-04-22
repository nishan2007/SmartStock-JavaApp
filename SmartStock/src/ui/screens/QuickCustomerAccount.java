package ui.screens;

import data.DB;
import managers.PermissionManager;
import ui.components.CustomerTypeSelector;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class QuickCustomerAccount extends JFrame {
    private final Runnable afterSave;
    private final boolean canSetCreditLimit;

    private JTextField accountNumberField;
    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private CustomerTypeSelector customerTypeSelector;
    private JTextField creditLimitField;
    private JTextArea accountNotesArea;
    private JCheckBox businessAccountCheckBox;

    public QuickCustomerAccount(Runnable afterSave) {
        this.afterSave = afterSave;
        this.canSetCreditLimit = PermissionManager.hasPermission("SET_CREDIT_LIMIT");

        setTitle("New Customer Account");
        setSize(460, canSetCreditLimit ? 500 : 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(12, 12));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        add(mainPanel, BorderLayout.CENTER);

        mainPanel.add(buildHeaderPanel(), BorderLayout.NORTH);
        mainPanel.add(buildFormPanel(), BorderLayout.CENTER);
        mainPanel.add(buildButtonPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Create Customer Account");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        JLabel subtitleLabel = new JLabel(canSetCreditLimit
                ? "Create an account and set credit terms."
                : "Create an account with no credit limit.");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(4));
        panel.add(subtitleLabel);
        return panel;
    }

    private JPanel buildFormPanel() {
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        accountNumberField = new JTextField();
        accountNumberField.setEditable(false);
        nameField = new JTextField();
        phoneField = new JTextField();
        emailField = new JTextField();
        customerTypeSelector = new CustomerTypeSelector();
        businessAccountCheckBox = new JCheckBox("Business Account");
        creditLimitField = new JTextField("0.00");
        accountNotesArea = new JTextArea(4, 20);
        accountNotesArea.setLineWrap(true);
        accountNotesArea.setWrapStyleWord(true);

        accountNumberField.setText("Auto-generated on save");
        addField(formPanel, gbc, 0, "Account #:", accountNumberField);
        addField(formPanel, gbc, 1, "Name:", nameField);
        addField(formPanel, gbc, 2, "Customer Type:", customerTypeSelector);
        addField(formPanel, gbc, 3, "Phone:", phoneField);
        addField(formPanel, gbc, 4, "Email:", emailField);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Account Type:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        formPanel.add(businessAccountCheckBox, gbc);

        if (canSetCreditLimit) {
            addField(formPanel, gbc, 6, "Credit Limit:", creditLimitField);
        }
        JScrollPane notesScrollPane = new JScrollPane(accountNotesArea);
        notesScrollPane.setPreferredSize(new Dimension(0, 82));
        addField(formPanel, gbc, canSetCreditLimit ? 7 : 6, "Notes:", notesScrollPane);

        return formPanel;
    }

    private JPanel buildButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton cancelButton = new JButton("Cancel");
        JButton saveButton = new JButton("Create Account");

        cancelButton.addActionListener(e -> dispose());
        saveButton.addActionListener(e -> saveCustomerAccount());

        panel.add(cancelButton);
        panel.add(saveButton);
        return panel;
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void saveCustomerAccount() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String accountNotes = accountNotesArea.getText().trim();
        Integer customerTypeId = customerTypeSelector.getSelectedCustomerTypeId();
        if (customerTypeId == null && !customerTypeSelector.getSelectedCustomerTypeName().isBlank()) {
            return;
        }
        boolean businessAccount = businessAccountCheckBox.isSelected();
        BigDecimal creditLimit = BigDecimal.ZERO;

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        String accountNumber = generateNextAccountNumber(businessAccount);
        if (accountNumber == null) {
            return;
        }
        accountNumberField.setText(accountNumber);

        if (canSetCreditLimit) {
            creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
            if (creditLimit == null) {
                return;
            }
            if (creditLimit.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Credit limit cannot be negative.");
                return;
            }
        }

        String sql = """
                INSERT INTO customer_accounts (account_number, name, customer_type_id, phone, email, credit_limit, current_balance, is_business, is_active, account_notes)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?, TRUE, ?)
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, accountNumber);
            ps.setString(2, name);
            setNullableInteger(ps, 3, customerTypeId);
            ps.setString(4, phone.isEmpty() ? null : phone);
            ps.setString(5, email.isEmpty() ? null : email);
            ps.setBigDecimal(6, creditLimit);
            ps.setBoolean(7, businessAccount);
            ps.setString(8, accountNotes.isEmpty() ? null : accountNotes);
            ps.executeUpdate();

            JOptionPane.showMessageDialog(this, "Customer account created.");
            if (afterSave != null) {
                afterSave.run();
            }
            dispose();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to create customer account: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String generateNextAccountNumber(boolean businessAccount) {
        String prefix = businessAccount ? "BA" : "CA";
        String sql = """
                SELECT COALESCE(MAX(CAST(SUBSTRING(account_number FROM 4) AS INTEGER)), 0) + 1 AS next_number
                FROM customer_accounts
                WHERE account_number ~ ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "^" + prefix + "-[0-9]+$");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return String.format("%s-%06d", prefix, rs.getInt("next_number"));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to generate account number: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }

        return null;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            return new BigDecimal(value.replace("$", "").replace(",", "").trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
