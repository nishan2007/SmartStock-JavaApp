package ui.screens;

import managers.SupabaseSessionManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;
import data.DB;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmployeeManagement extends JFrame {

    private JTable employeeTable;
    private DefaultTableModel employeeModel;
    private TableRowSorter<DefaultTableModel> employeeSorter;
    private JTextField employeeSearchField;

    private JTextField usernameField;
    private JTextField passwordField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField badgeIdField;
    private JComboBox<CompensationOption> compensationTypeBox;
    private JTextField hourlyWageField;
    private JTextField salaryAmountField;
    private JTextField dailySalaryField;
    private JComboBox<PayPeriodOption> payPeriodTypeBox;
    private JComboBox<RoleOption> roleBox;
    private JCheckBox activeCheckBox;

    private JButton addButton;
    private JButton updateButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton deleteButton;
    private JButton assignStoresButton;

    private Integer selectedUserId = null;
    private String originalFullName = "";
    private String originalEmail = "";
    private boolean originalIsActive = true;

    private static final String SUPABASE_URL = getConfig("SUPABASE_URL", "https://wbffhygkttoaaodjcvuh.supabase.co");
    private static final String SUPABASE_PUBLISHABLE_KEY = getConfig("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_A_Z2rTrylkxY9JIRCM1pRQ_Rf56Lqja");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public EmployeeManagement() {
        setTitle("Employee Management");
        setSize(900, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setJMenuBar(AppMenuBar.create(this, "EmployeeManagement"));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        employeeModel = new DefaultTableModel(
                new Object[]{"User ID", "Username", "Full Name", "Email", "Phone", "Badge ID", "Pay Type", "Pay Period", "Hourly Wage", "Salary", "Day Salary", "Role", "Active"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        employeeSorter = new TableRowSorter<>(employeeModel);

        employeeTable = new JTable(employeeModel);
        employeeTable.setRowSorter(employeeSorter);
        employeeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));

        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        employeeSearchField = new JTextField();
        searchPanel.add(new JLabel("Search Employees:"), BorderLayout.WEST);
        searchPanel.add(employeeSearchField, BorderLayout.CENTER);
        JScrollPane tableScrollPane = new JScrollPane(employeeTable);
        tableScrollPane.setPreferredSize(new Dimension(390, 0));
        leftPanel.add(searchPanel, BorderLayout.NORTH);
        leftPanel.add(tableScrollPane, BorderLayout.CENTER);
        employeeTable.setRowHeight(28);
        employeeTable.getColumnModel().getColumn(0).setPreferredWidth(70);
        employeeTable.getColumnModel().getColumn(0).setMinWidth(60);
        employeeTable.getColumnModel().getColumn(0).setMaxWidth(90);

        employeeTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        employeeTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        employeeTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        employeeTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        employeeTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        employeeTable.getColumnModel().getColumn(6).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(7).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(8).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(9).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(10).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(11).setPreferredWidth(90);
        employeeTable.getColumnModel().getColumn(12).setPreferredWidth(70);
        employeeTable.getColumnModel().getColumn(12).setMinWidth(60);
        employeeTable.getColumnModel().getColumn(12).setMaxWidth(80);

        employeeTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JPanel formPanel = new JPanel(new GridBagLayout());

        usernameField = new JTextField();
        passwordField = new JTextField();
        fullNameField = new JTextField();
        emailField = new JTextField();
        phoneField = new JTextField();
        badgeIdField = new JTextField();
        compensationTypeBox = new JComboBox<>(new CompensationOption[]{
                new CompensationOption("HOURLY", "Hourly"),
                new CompensationOption("SALARY", "Fixed Salary"),
                new CompensationOption("DAILY", "Day Salary")
        });
        compensationTypeBox.setEditable(false);
        hourlyWageField = new JTextField();
        salaryAmountField = new JTextField();
        dailySalaryField = new JTextField();
        payPeriodTypeBox = new JComboBox<>(new PayPeriodOption[]{
                new PayPeriodOption("SEMI_MONTHLY", "Semi-Monthly"),
                new PayPeriodOption("WEEKLY", "Weekly")
        });
        payPeriodTypeBox.setEditable(false);
        roleBox = new JComboBox<>();
        activeCheckBox = new JCheckBox("Active", true);
        activeCheckBox.setEnabled(true);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JLabel("Username:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Password:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Full Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(fullNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Phone Number:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(phoneField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Badge ID:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(badgeIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Pay Type:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(compensationTypeBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Pay Period:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(payPeriodTypeBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Hourly Wage:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(hourlyWageField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Salary / Pay Period:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(salaryAmountField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Day Salary:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(dailySalaryField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Role:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(roleBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 12;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(activeCheckBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 13;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(Box.createVerticalGlue(), gbc);

        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JPanel bottomButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        addButton = new JButton("Add Employee");
        updateButton = new JButton("Update Employee");
        clearButton = new JButton("Clear");
        refreshButton = new JButton("Refresh");
        deleteButton = new JButton("Delete Employee");
        assignStoresButton = new JButton("Assign Stores");

        Dimension compactButtonSize = new Dimension(145, 32);
        addButton.setPreferredSize(compactButtonSize);
        updateButton.setPreferredSize(compactButtonSize);
        deleteButton.setPreferredSize(compactButtonSize);

        Dimension smallButtonSize = new Dimension(125, 32);
        assignStoresButton.setPreferredSize(new Dimension(135, 32));
        clearButton.setPreferredSize(smallButtonSize);
        refreshButton.setPreferredSize(smallButtonSize);

        topButtonPanel.add(addButton);
        topButtonPanel.add(updateButton);
        topButtonPanel.add(deleteButton);

        bottomButtonPanel.add(assignStoresButton);
        bottomButtonPanel.add(clearButton);
        bottomButtonPanel.add(refreshButton);

        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setPreferredSize(new Dimension(650, 0));
        rightPanel.add(topButtonPanel, BorderLayout.NORTH);
        rightPanel.add(formPanel, BorderLayout.CENTER);
        rightPanel.add(bottomButtonPanel, BorderLayout.SOUTH);

        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        add(mainPanel);

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addEmployee();
            }
        });

        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateEmployee();
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteEmployee();
            }
        });

        assignStoresButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openAssignStoresDialog();
            }
        });

        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearFields();
            }
        });

        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadRoles();
                loadEmployees();
            }
        });
        employeeSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyEmployeeFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyEmployeeFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyEmployeeFilter();
            }
        });

        employeeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedEmployee();
            }
        });

        assignStoresButton.setEnabled(false);
        deleteButton.setEnabled(false);
        loadRoles();
        loadEmployees();
        WindowHelper.showPosWindow(this);
    }

    private void loadRoles() {
        roleBox.removeAllItems();

        String sql = "SELECT role_name FROM roles ORDER BY role_name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String roleName = rs.getString("role_name");
                if (roleName != null && !roleName.isBlank()) {
                    roleBox.addItem(new RoleOption(roleName));
                }
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load roles: " + ex.getMessage());
        }

        if (roleBox.getItemCount() == 0) {
            roleBox.addItem(new RoleOption("USER"));
        }
    }

    private void loadEmployees() {
        employeeModel.setRowCount(0);

        String sql = """
                SELECT u.user_id,
                       u.username,
                       u.full_name,
                       u.email,
                       u.phone,
                       u.badge_id,
                       COALESCE(u.compensation_type, 'HOURLY') AS compensation_type,
                       COALESCE(u.pay_period_type, 'SEMI_MONTHLY') AS pay_period_type,
                       COALESCE(u.hourly_wage, 0) AS hourly_wage,
                       COALESCE(u.salary_amount, 0) AS salary_amount,
                       COALESCE(u.daily_salary, 0) AS daily_salary,
                       COALESCE(r.role_name, 'USER') AS role,
                       COALESCE(u.is_active, TRUE) AS is_active
                FROM users u
                LEFT JOIN roles r ON u.role_id = r.role_id
                ORDER BY u.user_id
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                employeeModel.addRow(new Object[]{
                        rs.getInt("user_id"),
                        rs.getString("username"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("badge_id"),
                        rs.getString("compensation_type"),
                        rs.getString("pay_period_type"),
                        rs.getBigDecimal("hourly_wage"),
                        rs.getBigDecimal("salary_amount"),
                        rs.getBigDecimal("daily_salary"),
                        rs.getString("role"),
                        rs.getBoolean("is_active")
                });
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load employees: " + ex.getMessage());
        }
    }

    private void loadSelectedEmployee() {
        int selectedRow = employeeTable.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }
        selectedRow = employeeTable.convertRowIndexToModel(selectedRow);

        selectedUserId = Integer.parseInt(employeeModel.getValueAt(selectedRow, 0).toString());
        usernameField.setText(employeeModel.getValueAt(selectedRow, 1).toString());
        fullNameField.setText(employeeModel.getValueAt(selectedRow, 2) == null ? "" : employeeModel.getValueAt(selectedRow, 2).toString());
        emailField.setText(employeeModel.getValueAt(selectedRow, 3) == null ? "" : employeeModel.getValueAt(selectedRow, 3).toString());
        phoneField.setText(employeeModel.getValueAt(selectedRow, 4) == null ? "" : employeeModel.getValueAt(selectedRow, 4).toString());
        badgeIdField.setText(employeeModel.getValueAt(selectedRow, 5) == null ? "" : employeeModel.getValueAt(selectedRow, 5).toString());
        selectCompensationType(employeeModel.getValueAt(selectedRow, 6) == null ? "HOURLY" : employeeModel.getValueAt(selectedRow, 6).toString());
        selectPayPeriodType(employeeModel.getValueAt(selectedRow, 7) == null ? "SEMI_MONTHLY" : employeeModel.getValueAt(selectedRow, 7).toString());
        hourlyWageField.setText(employeeModel.getValueAt(selectedRow, 8) == null ? "" : employeeModel.getValueAt(selectedRow, 8).toString());
        salaryAmountField.setText(employeeModel.getValueAt(selectedRow, 9) == null ? "" : employeeModel.getValueAt(selectedRow, 9).toString());
        dailySalaryField.setText(employeeModel.getValueAt(selectedRow, 10) == null ? "" : employeeModel.getValueAt(selectedRow, 10).toString());
        selectRole(String.valueOf(employeeModel.getValueAt(selectedRow, 11)));

        Object activeValue = employeeModel.getValueAt(selectedRow, 12);
        activeCheckBox.setSelected(activeValue instanceof Boolean ? (Boolean) activeValue : true);
        originalFullName = fullNameField.getText().trim();
        originalEmail = emailField.getText().trim();
        originalIsActive = activeCheckBox.isSelected();

        passwordField.setText("");
        assignStoresButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    private void addEmployee() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String phoneNumber = phoneField.getText().trim();
        String badgeId = badgeIdField.getText().trim();
        String compensationType = getSelectedCompensationType();
        String payPeriodType = getSelectedPayPeriodType();
        BigDecimal hourlyWage = parseMoneyAmount(hourlyWageField, "Hourly wage");
        if (hourlyWage == null) {
            return;
        }
        BigDecimal salaryAmount = parseMoneyAmount(salaryAmountField, "Salary amount");
        if (salaryAmount == null) {
            return;
        }
        BigDecimal dailySalary = parseMoneyAmount(dailySalaryField, "Day salary");
        if (dailySalary == null) {
            return;
        }
        String role = getSelectedRole();
        boolean isActive = activeCheckBox.isSelected();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username, email, and password are required.");
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String authUserId = createSupabaseAuthUser(email, password, fullName, isActive);

                String sql = """
                        INSERT INTO users (username, password_hash, full_name, email, phone, badge_id, compensation_type, pay_period_type, hourly_wage, salary_amount, daily_salary, role_id, auth_user_id, is_active)
                        VALUES (?, NULL, ?, ?, ?, ?, ?::compensation_type_enum, ?::pay_period_type_enum, ?, ?, ?, (SELECT role_id FROM roles WHERE UPPER(role_name) = UPPER(?)), ?::uuid, ?)
                        """;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, fullName.isEmpty() ? null : fullName);
                    ps.setString(3, email.isEmpty() ? null : email);
                    ps.setString(4, phoneNumber.isEmpty() ? null : phoneNumber);
                    ps.setString(5, badgeId.isEmpty() ? null : badgeId);
                    ps.setString(6, compensationType);
                    ps.setString(7, payPeriodType);
                    ps.setBigDecimal(8, hourlyWage);
                    ps.setBigDecimal(9, salaryAmount);
                    ps.setBigDecimal(10, dailySalary);
                    ps.setString(11, role);
                    ps.setString(12, normalizeUuid(authUserId));
                    ps.setBoolean(13, isActive);
                    ps.executeUpdate();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Employee added successfully.");
                clearFields();
                loadEmployees();

            } catch (Exception ex) {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Failed to add employee: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to add employee: " + ex.getMessage());
        }
    }

    private void updateEmployee() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String email = emailField.getText().trim();
        String phoneNumber = phoneField.getText().trim();
        String badgeId = badgeIdField.getText().trim();
        String compensationType = getSelectedCompensationType();
        String payPeriodType = getSelectedPayPeriodType();
        BigDecimal hourlyWage = parseMoneyAmount(hourlyWageField, "Hourly wage");
        if (hourlyWage == null) {
            return;
        }
        BigDecimal salaryAmount = parseMoneyAmount(salaryAmountField, "Salary amount");
        if (salaryAmount == null) {
            return;
        }
        BigDecimal dailySalary = parseMoneyAmount(dailySalaryField, "Day salary");
        if (dailySalary == null) {
            return;
        }
        String role = getSelectedRole();
        boolean isActive = activeCheckBox.isSelected();

        if (username.isEmpty() || email.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username and email are required.");
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String authUserId = getAuthUserId(conn, selectedUserId);
                boolean shouldSyncAuth = !password.isBlank()
                        || !sameText(fullName, originalFullName)
                        || !sameText(email, originalEmail)
                        || isActive != originalIsActive;

                if (shouldSyncAuth) {
                    if (authUserId == null || authUserId.isBlank()) {
                        if (password.isEmpty()) {
                            throw new IllegalStateException("This employee does not have a linked Supabase auth user yet. Enter a password so one can be created.");
                        }
                        authUserId = createSupabaseAuthUser(email, password, fullName, isActive);
                    } else {
                        updateSupabaseAuthUser(authUserId, email, password, fullName, isActive);
                    }
                }

                String sql = """
                        UPDATE users
                        SET username = ?,
                            password_hash = NULL,
                            full_name = ?,
                            email = ?,
                            phone = ?,
                            badge_id = ?,
                            compensation_type = ?::compensation_type_enum,
                            pay_period_type = ?::pay_period_type_enum,
                            hourly_wage = ?,
                            salary_amount = ?,
                            daily_salary = ?,
                            role_id = (SELECT role_id FROM roles WHERE UPPER(role_name) = UPPER(?)),
                            auth_user_id = ?::uuid,
                            is_active = ?
                        WHERE user_id = ?
                        """;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, fullName.isEmpty() ? null : fullName);
                    ps.setString(3, email.isEmpty() ? null : email);
                    ps.setString(4, phoneNumber.isEmpty() ? null : phoneNumber);
                    ps.setString(5, badgeId.isEmpty() ? null : badgeId);
                    ps.setString(6, compensationType);
                    ps.setString(7, payPeriodType);
                    ps.setBigDecimal(8, hourlyWage);
                    ps.setBigDecimal(9, salaryAmount);
                    ps.setBigDecimal(10, dailySalary);
                    ps.setString(11, role);
                    ps.setString(12, normalizeUuid(authUserId));
                    ps.setBoolean(13, isActive);
                    ps.setInt(14, selectedUserId);
                    ps.executeUpdate();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Employee updated successfully.");
                clearFields();
                loadEmployees();

            } catch (Exception ex) {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Failed to update employee: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to update employee: " + ex.getMessage());
        }
    }

    private void clearFields() {
        selectedUserId = null;
        usernameField.setText("");
        passwordField.setText("");
        fullNameField.setText("");
        emailField.setText("");
        phoneField.setText("");
        badgeIdField.setText("");
        selectCompensationType("HOURLY");
        selectPayPeriodType("SEMI_MONTHLY");
        hourlyWageField.setText("");
        salaryAmountField.setText("");
        dailySalaryField.setText("");
        roleBox.setSelectedIndex(0);
        activeCheckBox.setSelected(true);
        activeCheckBox.setEnabled(true);
        originalFullName = "";
        originalEmail = "";
        originalIsActive = true;
        employeeTable.clearSelection();
        assignStoresButton.setEnabled(false);
        deleteButton.setEnabled(false);
        usernameField.requestFocusInWindow();
    }

    private static boolean sameText(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

    private String getSelectedRole() {
        Object selectedRole = roleBox.getSelectedItem();
        if (selectedRole instanceof RoleOption roleOption) {
            return roleOption.roleName;
        }
        return selectedRole == null ? "" : selectedRole.toString();
    }

    private String getSelectedCompensationType() {
        Object selected = compensationTypeBox.getSelectedItem();
        if (selected instanceof CompensationOption option) {
            return option.key;
        }
        return "HOURLY";
    }

    private String getSelectedPayPeriodType() {
        Object selected = payPeriodTypeBox.getSelectedItem();
        if (selected instanceof PayPeriodOption option) {
            return option.key;
        }
        return "SEMI_MONTHLY";
    }

    private void selectCompensationType(String compensationType) {
        String key = compensationType == null || compensationType.isBlank()
                ? "HOURLY"
                : compensationType.trim().toUpperCase();

        for (int i = 0; i < compensationTypeBox.getItemCount(); i++) {
            CompensationOption option = compensationTypeBox.getItemAt(i);
            if (option.key.equalsIgnoreCase(key)) {
                compensationTypeBox.setSelectedIndex(i);
                return;
            }
        }
        compensationTypeBox.setSelectedIndex(0);
    }

    private void selectPayPeriodType(String payPeriodType) {
        String key = payPeriodType == null || payPeriodType.isBlank()
                ? "SEMI_MONTHLY"
                : payPeriodType.trim().toUpperCase();

        for (int i = 0; i < payPeriodTypeBox.getItemCount(); i++) {
            PayPeriodOption option = payPeriodTypeBox.getItemAt(i);
            if (option.key.equalsIgnoreCase(key)) {
                payPeriodTypeBox.setSelectedIndex(i);
                return;
            }
        }
        payPeriodTypeBox.setSelectedIndex(0);
    }

    private BigDecimal parseMoneyAmount(JTextField field, String label) {
        String value = field.getText().trim();
        if (value.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal amount = new BigDecimal(value.replace("$", "").replace(",", ""));
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, label + " cannot be negative.");
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid " + label.toLowerCase() + ".");
            return null;
        }
    }

    private void selectRole(String roleName) {
        if (roleName == null) {
            roleBox.setSelectedIndex(roleBox.getItemCount() > 0 ? 0 : -1);
            return;
        }

        for (int i = 0; i < roleBox.getItemCount(); i++) {
            RoleOption option = roleBox.getItemAt(i);
            if (option.roleName.equalsIgnoreCase(roleName)) {
                roleBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String formatRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }

        String[] words = roleName.trim().replace("_", " ").split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                formatted.append(word.substring(1).toLowerCase());
            }
        }
        return formatted.toString();
    }

    private static class RoleOption {
        private final String roleName;

        private RoleOption(String roleName) {
            this.roleName = roleName;
        }

        @Override
        public String toString() {
            return formatRoleName(roleName);
        }
    }

    private static class CompensationOption {
        private final String key;
        private final String label;

        private CompensationOption(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class PayPeriodOption {
        private final String key;
        private final String label;

        private PayPeriodOption(String key, String label) {
            this.key = key;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void deleteSupabaseAuthUser(String authUserId) throws IOException, InterruptedException {
        ensureSupabaseConfig();

        String body = "{" +
                "\"user_id\":" + jsonValue(authUserId) +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/functions/v1/delete-employee-auth-user"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", buildUserAuthHeader())
                .method("DELETE", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    extractErrorMessage(response.body(),
                            "Supabase auth user delete failed with status " + response.statusCode())
            );
        }
    }

    private void openAssignStoresDialog() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        JDialog dialog = new JDialog(this, "Assign Stores", true);
        dialog.setSize(700, 400);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        JTextField searchField = new JTextField();
        topPanel.add(new JLabel("Search Store:"), BorderLayout.WEST);
        topPanel.add(searchField, BorderLayout.CENTER);

        DefaultTableModel storeModel = new DefaultTableModel(
                new Object[]{"Assigned", "Location ID", "Store Name", "Address"}, 0
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 0) {
                    return Boolean.class;
                }
                return String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };

        JTable storeTable = new JTable(storeModel);
        storeTable.setRowHeight(24);
        JScrollPane scrollPane = new JScrollPane(storeTable);

        TableColumn assignedColumn = storeTable.getColumnModel().getColumn(0);
        assignedColumn.setPreferredWidth(80);
        assignedColumn.setMaxWidth(100);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save Assignments");
        JButton closeButton = new JButton("Close");
        bottomPanel.add(saveButton);
        bottomPanel.add(closeButton);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(bottomPanel, BorderLayout.SOUTH);

        Runnable loadStores = () -> {
            storeModel.setRowCount(0);
            String searchText = searchField.getText().trim();

            String sql = """
                    SELECT l.location_id, l.name, l.address,
                           CASE WHEN ul.user_id IS NULL THEN FALSE ELSE TRUE END AS assigned
                    FROM locations l
                    LEFT JOIN user_locations ul
                        ON l.location_id = ul.location_id AND ul.user_id = ?
                    WHERE l.name LIKE ?
                       OR COALESCE(l.address, '') LIKE ?
                       OR CAST(l.location_id AS TEXT) LIKE ?
                    ORDER BY l.location_id
                    """;

            try (Connection conn = DB.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                String searchLike = "%" + searchText + "%";
                ps.setInt(1, selectedUserId);
                ps.setString(2, searchLike);
                ps.setString(3, searchLike);
                ps.setString(4, searchLike);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        storeModel.addRow(new Object[]{
                                rs.getBoolean("assigned"),
                                String.valueOf(rs.getInt("location_id")),
                                rs.getString("name"),
                                rs.getString("address") == null ? "" : rs.getString("address")
                        });
                    }
                }

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to load stores: " + ex.getMessage());
            }
        };

        searchField.addActionListener(e -> loadStores.run());

        saveButton.addActionListener(e -> {
            String deleteSql = "DELETE FROM user_locations WHERE user_id = ?";
            String insertSql = "INSERT INTO user_locations (user_id, location_id) VALUES (?, ?)";

            try (Connection conn = DB.getConnection()) {
                conn.setAutoCommit(false);

                try (PreparedStatement deletePs = conn.prepareStatement(deleteSql);
                     PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

                    deletePs.setInt(1, selectedUserId);
                    deletePs.executeUpdate();

                    for (int i = 0; i < storeModel.getRowCount(); i++) {
                        Object assignedValue = storeModel.getValueAt(i, 0);
                        boolean assigned = assignedValue instanceof Boolean && (Boolean) assignedValue;

                        if (assigned) {
                            int locationId = Integer.parseInt(storeModel.getValueAt(i, 1).toString());
                            insertPs.setInt(1, selectedUserId);
                            insertPs.setInt(2, locationId);
                            insertPs.addBatch();
                        }
                    }

                    insertPs.executeBatch();
                    conn.commit();
                    JOptionPane.showMessageDialog(dialog, "Store assignments saved.");
                    dialog.dispose();

                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }

            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Failed to save assignments: " + ex.getMessage());
            }
        });

        closeButton.addActionListener(e -> dialog.dispose());

        loadStores.run();
        dialog.setVisible(true);
    }

    private String getAuthUserId(Connection conn, int userId) throws SQLException {
        String sql = "SELECT auth_user_id FROM users WHERE user_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("auth_user_id");
                }
            }
        }

        return null;
    }

    private String createSupabaseAuthUser(String email, String password, String fullName, boolean isActive)
            throws IOException, InterruptedException {
        ensureSupabaseConfig();

        String body = "{"
                + "\"email\":" + jsonValue(email) + ","
                + "\"password\":" + jsonValue(password) + ","
                + "\"full_name\":" + jsonValue(fullName == null || fullName.isBlank() ? email : fullName) + ","
                + "\"is_active\":" + isActive
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/functions/v1/create-employee-auth-user"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", buildUserAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    extractErrorMessage(response.body(),
                            "Supabase auth user creation failed with status " + response.statusCode())
            );
        }

        String authUserId = extractJsonString(response.body(), "auth_user_id");
        if (authUserId == null || authUserId.isBlank()) {
            authUserId = extractJsonString(response.body(), "user_id");
        }
        if (authUserId == null || authUserId.isBlank()) {
            authUserId = extractJsonString(response.body(), "id");
        }

        if (authUserId == null || authUserId.isBlank()) {
            throw new IllegalStateException("Supabase auth user was created but no auth user id was returned.");
        }

        return authUserId;
    }

    private void updateSupabaseAuthUser(String authUserId, String email, String password, String fullName, boolean isActive)
            throws IOException, InterruptedException {
        ensureSupabaseConfig();

        StringBuilder body = new StringBuilder();
        body.append("{")
                .append("\"auth_user_id\":").append(jsonValue(authUserId)).append(",")
                .append("\"email\":").append(jsonValue(email)).append(",")
                .append("\"full_name\":").append(jsonValue(fullName == null || fullName.isBlank() ? email : fullName)).append(",")
                .append("\"is_active\":").append(isActive);

        if (password != null && !password.isBlank()) {
            body.append(",\"password\":").append(jsonValue(password));
        }

        body.append("}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/functions/v1/update-employee-auth-user"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", buildUserAuthHeader())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    extractErrorMessage(response.body(),
                            "Supabase auth user update failed with status " + response.statusCode())
            );
        }
    }

    private static void ensureSupabaseConfig() {
        if (SUPABASE_URL == null || SUPABASE_URL.isBlank()) {
            throw new IllegalStateException("Missing SUPABASE_URL configuration.");
        }
        if (SUPABASE_PUBLISHABLE_KEY == null || SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            throw new IllegalStateException("Missing SUPABASE_PUBLISHABLE_KEY configuration.");
        }
    }

    private static String buildUserAuthHeader() throws IOException, InterruptedException {
        String accessToken = SupabaseSessionManager.getValidAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Missing logged-in Supabase access token.");
        }
        return "Bearer " + accessToken;
    }



    private static String getConfig(String key) {
        return getConfig(key, null);
    }

    private static String getConfig(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return value;
    }

    private static String normalizeUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value.trim()).toString();
    }

    private static String jsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonString(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractErrorMessage(String responseBody, String fallback) {
        String message = extractJsonString(responseBody, "error");
        if (message == null || message.isBlank()) {
            message = extractJsonString(responseBody, "message");
        }
        if (message == null || message.isBlank()) {
            message = extractJsonString(responseBody, "msg");
        }
        return (message == null || message.isBlank()) ? fallback : message;
    }

    private void deleteEmployee() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete this employee? This will also remove their Supabase auth account.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String authUserId = getAuthUserId(conn, selectedUserId);

                if (authUserId != null && !authUserId.isBlank()) {
                    deleteSupabaseAuthUser(authUserId);
                }

                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                    ps.setInt(1, selectedUserId);
                    ps.executeUpdate();
                }

                conn.commit();
                JOptionPane.showMessageDialog(this, "Employee deleted successfully.");
                clearFields();
                loadEmployees();

            } catch (Exception ex) {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Failed to delete employee: " + ex.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete employee: " + ex.getMessage());
        }
    }

    private void applyEmployeeFilter() {
        if (employeeSorter == null) {
            return;
        }

        String text = employeeSearchField == null ? "" : employeeSearchField.getText().trim();
        if (text.isEmpty()) {
            employeeSorter.setRowFilter(null);
        } else {
            employeeSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }
}
