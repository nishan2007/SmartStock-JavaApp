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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmployeeManagement extends JFrame {

    private JTable employeeTable;
    private DefaultTableModel employeeModel;
    private TableRowSorter<DefaultTableModel> employeeSorter;
    private JTextField employeeSearchField;
    private JTable storeTable;
    private DefaultTableModel storeModel;
    private TableRowSorter<DefaultTableModel> storeSorter;
    private JTextField storeSearchField;

    private JTextField usernameField;
    private JTextField passwordField;
    private JTextField firstNameField;
    private JTextField middleNameField;
    private JTextField lastNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField badgeIdField;
    private JComboBox<CompensationOption> compensationTypeBox;
    private JTextField salaryAmountField;
    private JComboBox<RoleOption> roleBox;
    private JCheckBox activeCheckBox;

    private JButton addButton;
    private JButton updateButton;
    private JButton clearButton;
    private JButton refreshButton;
    private JButton deleteButton;

    private Integer selectedUserId = null;
    private String originalFirstName = "";
    private String originalMiddleName = "";
    private String originalLastName = "";
    private String originalFullName = "";
    private String originalEmail = "";
    private boolean originalIsActive = true;
    private boolean updatingGeneratedUsername;
    private String lastGeneratedUsername = "";

    private static final String SUPABASE_URL = getConfig("SUPABASE_URL", "https://wbffhygkttoaaodjcvuh.supabase.co");
    private static final String SUPABASE_PUBLISHABLE_KEY = getConfig("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_A_Z2rTrylkxY9JIRCM1pRQ_Rf56Lqja");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public EmployeeManagement() {
        setTitle("Employee Management");
        setSize(1100, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setJMenuBar(AppMenuBar.create(this, "EmployeeManagement"));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        employeeModel = new DefaultTableModel(
                new Object[]{"User ID", "Username", "Full Name", "First Name", "Middle Name", "Last Name", "Email", "Phone", "Badge ID", "Pay Type", "Salary", "Role", "Active"}, 0
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
        for (int hiddenColumn = 3; hiddenColumn <= 5; hiddenColumn++) {
            TableColumn column = employeeTable.getColumnModel().getColumn(hiddenColumn);
            column.setMinWidth(0);
            column.setPreferredWidth(0);
            column.setMaxWidth(0);
        }
        employeeTable.getColumnModel().getColumn(6).setPreferredWidth(180);
        employeeTable.getColumnModel().getColumn(7).setPreferredWidth(120);
        employeeTable.getColumnModel().getColumn(8).setPreferredWidth(100);
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
        firstNameField = new JTextField();
        middleNameField = new JTextField();
        lastNameField = new JTextField();
        emailField = new JTextField();
        phoneField = new JTextField();
        badgeIdField = new JTextField();
        compensationTypeBox = new JComboBox<>(new CompensationOption[]{
                new CompensationOption("HOURLY", "Hourly"),
                new CompensationOption("SALARY", "Salary"),
                new CompensationOption("DAILY", "Daily")
        });
        compensationTypeBox.setEditable(false);
        salaryAmountField = new JTextField();
        roleBox = new JComboBox<>();
        activeCheckBox = new JCheckBox("Active", true);
        activeCheckBox.setEnabled(true);
        storeSearchField = new JTextField();
        storeModel = new DefaultTableModel(new Object[]{"Assigned", "Store ID", "Store Name", "Address"}, 0) {
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
        storeSorter = new TableRowSorter<>(storeModel);
        storeTable = new JTable(storeModel);
        storeTable.setRowSorter(storeSorter);
        storeTable.setRowHeight(24);
        storeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(new JLabel("Username (auto):"), gbc);

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
        formPanel.add(new JLabel("First Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(firstNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Middle Name (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(middleNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Last Name:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(lastNameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Email:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(emailField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Phone Number:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(phoneField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Badge ID (optional):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(badgeIdField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Pay Type:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(compensationTypeBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Salary:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(salaryAmountField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 10;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Role:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(roleBox, gbc);

        gbc.gridx = 0;
        gbc.gridy = 11;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Status:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        formPanel.add(activeCheckBox, gbc);

        JPanel storePanel = new JPanel(new BorderLayout(6, 6));
        JPanel storeSearchPanel = new JPanel(new BorderLayout(6, 0));
        storeSearchPanel.add(new JLabel("Assigned Stores:"), BorderLayout.WEST);
        storeSearchPanel.add(storeSearchField, BorderLayout.CENTER);
        storePanel.add(storeSearchPanel, BorderLayout.NORTH);

        JScrollPane storeScrollPane = new JScrollPane(storeTable);
        storeScrollPane.setPreferredSize(new Dimension(0, 135));
        storePanel.add(storeScrollPane, BorderLayout.CENTER);

        TableColumn assignedStoreColumn = storeTable.getColumnModel().getColumn(0);
        assignedStoreColumn.setPreferredWidth(80);
        assignedStoreColumn.setMaxWidth(100);
        TableColumn storeIdColumn = storeTable.getColumnModel().getColumn(1);
        storeIdColumn.setPreferredWidth(70);
        storeIdColumn.setMaxWidth(90);

        gbc.gridx = 0;
        gbc.gridy = 12;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(storePanel, gbc);

        gbc.gridwidth = 1;
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

        Dimension compactButtonSize = new Dimension(145, 32);
        addButton.setPreferredSize(compactButtonSize);
        updateButton.setPreferredSize(compactButtonSize);
        deleteButton.setPreferredSize(compactButtonSize);

        Dimension smallButtonSize = new Dimension(125, 32);
        clearButton.setPreferredSize(smallButtonSize);
        refreshButton.setPreferredSize(smallButtonSize);

        topButtonPanel.add(addButton);
        topButtonPanel.add(updateButton);
        topButtonPanel.add(deleteButton);

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
                loadStoresForUser(selectedUserId);
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
        storeSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyStoreFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyStoreFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyStoreFilter();
            }
        });
        DocumentListener generatedUsernameListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateGeneratedUsername();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateGeneratedUsername();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateGeneratedUsername();
            }
        };
        firstNameField.getDocument().addDocumentListener(generatedUsernameListener);
        lastNameField.getDocument().addDocumentListener(generatedUsernameListener);

        employeeTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedEmployee();
            }
        });

        deleteButton.setEnabled(false);
        loadRoles();
        loadStoresForUser(null);
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
                       u.first_name,
                       u.middle_name,
                       u.last_name,
                       u.email,
                       u.phone,
                       u.badge_id,
                       COALESCE(u.compensation_type, 'HOURLY') AS compensation_type,
                       COALESCE(u.salary, 0) AS salary,
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
                        rs.getString("first_name"),
                        rs.getString("middle_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("badge_id"),
                        rs.getString("compensation_type"),
                        rs.getBigDecimal("salary"),
                        rs.getString("role"),
                        rs.getBoolean("is_active")
                });
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load employees: " + ex.getMessage());
        }
    }

    private void loadStoresForUser(Integer userId) {
        storeModel.setRowCount(0);

        String sql = """
                SELECT l.location_id,
                       l.name,
                       l.address,
                       CASE WHEN ul.user_id IS NULL THEN FALSE ELSE TRUE END AS assigned
                FROM locations l
                LEFT JOIN user_locations ul
                    ON l.location_id = ul.location_id AND ul.user_id = ?
                ORDER BY l.name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId == null ? -1 : userId);

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
            JOptionPane.showMessageDialog(this, "Failed to load stores: " + ex.getMessage());
        }

        applyStoreFilter();
    }

    private void loadSelectedEmployee() {
        int selectedRow = employeeTable.getSelectedRow();
        if (selectedRow == -1) {
            return;
        }
        selectedRow = employeeTable.convertRowIndexToModel(selectedRow);

        selectedUserId = Integer.parseInt(employeeModel.getValueAt(selectedRow, 0).toString());
        usernameField.setText(employeeModel.getValueAt(selectedRow, 1) == null ? "" : employeeModel.getValueAt(selectedRow, 1).toString());
        String selectedFullName = employeeModel.getValueAt(selectedRow, 2) == null ? "" : employeeModel.getValueAt(selectedRow, 2).toString();
        String selectedFirstName = employeeModel.getValueAt(selectedRow, 3) == null ? "" : employeeModel.getValueAt(selectedRow, 3).toString();
        String selectedMiddleName = employeeModel.getValueAt(selectedRow, 4) == null ? "" : employeeModel.getValueAt(selectedRow, 4).toString();
        String selectedLastName = employeeModel.getValueAt(selectedRow, 5) == null ? "" : employeeModel.getValueAt(selectedRow, 5).toString();
        updatingGeneratedUsername = true;
        firstNameField.setText(selectedFirstName);
        middleNameField.setText(selectedMiddleName);
        lastNameField.setText(selectedLastName);
        updatingGeneratedUsername = false;
        emailField.setText(employeeModel.getValueAt(selectedRow, 6) == null ? "" : employeeModel.getValueAt(selectedRow, 6).toString());
        phoneField.setText(employeeModel.getValueAt(selectedRow, 7) == null ? "" : employeeModel.getValueAt(selectedRow, 7).toString());
        badgeIdField.setText(employeeModel.getValueAt(selectedRow, 8) == null ? "" : employeeModel.getValueAt(selectedRow, 8).toString());
        selectCompensationType(employeeModel.getValueAt(selectedRow, 9) == null ? "HOURLY" : employeeModel.getValueAt(selectedRow, 9).toString());
        salaryAmountField.setText(employeeModel.getValueAt(selectedRow, 10) == null ? "" : employeeModel.getValueAt(selectedRow, 10).toString());
        selectRole(String.valueOf(employeeModel.getValueAt(selectedRow, 11)));

        Object activeValue = employeeModel.getValueAt(selectedRow, 12);
        activeCheckBox.setSelected(activeValue instanceof Boolean ? (Boolean) activeValue : true);
        originalFirstName = firstNameField.getText().trim();
        originalMiddleName = middleNameField.getText().trim();
        originalLastName = lastNameField.getText().trim();
        originalFullName = selectedFullName;
        originalEmail = emailField.getText().trim();
        originalIsActive = activeCheckBox.isSelected();
        lastGeneratedUsername = generateUsername(originalFirstName, originalLastName);

        passwordField.setText("");
        loadStoresForUser(selectedUserId);
        deleteButton.setEnabled(true);
    }

    private void addEmployee() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String middleName = middleNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String fullName = composeFullName(firstName, middleName, lastName);
        String email = emailField.getText().trim();
        String phoneNumber = phoneField.getText().trim();
        String badgeId = badgeIdField.getText().trim();
        String compensationType = getSelectedCompensationType();
        BigDecimal salary = parseMoneyAmount(salaryAmountField, "Salary");
        if (salary == null) {
            return;
        }
        String role = getSelectedRole();
        boolean isActive = activeCheckBox.isSelected();

        if (password.isEmpty()
                || firstName.isEmpty()
                || lastName.isEmpty()
                || email.isEmpty()
                || phoneNumber.isEmpty()
                || salaryAmountField.getText().trim().isEmpty()
                || role.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All employee fields are required.");
            return;
        }
        if (username.isEmpty()) {
            username = generateUsername(firstName, lastName);
            usernameField.setText(username);
        }

        List<Integer> selectedLocationIds = getSelectedLocationIds();
        if (selectedLocationIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one assigned store.");
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String authUserId = createSupabaseAuthUser(email, password, fullName, isActive);

                String sql = """
                        INSERT INTO users (username, password_hash, first_name, middle_name, last_name, full_name, email, phone, badge_id, compensation_type, salary, role_id, auth_user_id, is_active)
                        VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT role_id FROM roles WHERE UPPER(role_name) = UPPER(?)), ?::uuid, ?)
                        RETURNING user_id
                        """;

                int newUserId;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, firstName);
                    ps.setString(3, middleName.isEmpty() ? null : middleName);
                    ps.setString(4, lastName);
                    ps.setString(5, fullName);
                    ps.setString(6, email);
                    ps.setString(7, phoneNumber);
                    ps.setString(8, badgeId.isEmpty() ? null : badgeId);
                    ps.setObject(9, compensationType, java.sql.Types.OTHER);
                    ps.setBigDecimal(10, salary);
                    ps.setString(11, role);
                    ps.setString(12, normalizeUuid(authUserId));
                    ps.setBoolean(13, isActive);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Employee was not created.");
                        }
                        newUserId = rs.getInt("user_id");
                    }
                }

                saveStoreAssignments(conn, newUserId, selectedLocationIds);
                conn.commit();
                JOptionPane.showMessageDialog(this, "Employee added successfully.");
                loadEmployees();
                selectEmployeeInTable(newUserId);

            } catch (Exception ex) {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Failed to add employee: " + getFriendlyEmployeeError(ex));
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to add employee: " + getFriendlyEmployeeError(ex));
        }
    }

    private void updateEmployee() {
        if (selectedUserId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String middleName = middleNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String fullName = composeFullName(firstName, middleName, lastName);
        String email = emailField.getText().trim();
        String phoneNumber = phoneField.getText().trim();
        String badgeId = badgeIdField.getText().trim();
        String compensationType = getSelectedCompensationType();
        BigDecimal salary = parseMoneyAmount(salaryAmountField, "Salary");
        if (salary == null) {
            return;
        }
        String role = getSelectedRole();
        boolean isActive = activeCheckBox.isSelected();

        if (firstName.isEmpty()
                || lastName.isEmpty()
                || email.isEmpty()
                || phoneNumber.isEmpty()
                || salaryAmountField.getText().trim().isEmpty()
                || role.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All employee fields except password are required.");
            return;
        }
        if (username.isEmpty()) {
            username = generateUsername(firstName, lastName);
            usernameField.setText(username);
        }

        List<Integer> selectedLocationIds = getSelectedLocationIds();
        if (selectedLocationIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one assigned store.");
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
                        try {
                            updateSupabaseAuthUser(authUserId, email, password, fullName, isActive);
                        } catch (IllegalStateException ex) {
                            if (!isSupabaseAuthUserNotFound(ex)) {
                                throw ex;
                            }
                            if (password.isEmpty()) {
                                throw new IllegalStateException("This employee's linked Supabase auth account was not found. Enter a new password so the auth account can be recreated.");
                            }
                            authUserId = createSupabaseAuthUser(email, password, fullName, isActive);
                        }
                    }
                }

                String sql = """
                        UPDATE users
                        SET username = ?,
                            password_hash = NULL,
                            first_name = ?,
                            middle_name = ?,
                            last_name = ?,
                            full_name = ?,
                            email = ?,
                            phone = ?,
                            badge_id = ?,
                            compensation_type = ?,
                            salary = ?,
                            role_id = (SELECT role_id FROM roles WHERE UPPER(role_name) = UPPER(?)),
                            auth_user_id = ?::uuid,
                            is_active = ?
                        WHERE user_id = ?
                        """;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, firstName);
                    ps.setString(3, middleName.isEmpty() ? null : middleName);
                    ps.setString(4, lastName);
                    ps.setString(5, fullName);
                    ps.setString(6, email);
                    ps.setString(7, phoneNumber);
                    ps.setString(8, badgeId.isEmpty() ? null : badgeId);
                    ps.setObject(9, compensationType, java.sql.Types.OTHER);
                    ps.setBigDecimal(10, salary);
                    ps.setString(11, role);
                    ps.setString(12, normalizeUuid(authUserId));
                    ps.setBoolean(13, isActive);
                    ps.setInt(14, selectedUserId);
                    ps.executeUpdate();
                }

                saveStoreAssignments(conn, selectedUserId, selectedLocationIds);
                conn.commit();
                JOptionPane.showMessageDialog(this, "Employee updated successfully.");
                clearFields();
                loadEmployees();

            } catch (Exception ex) {
                conn.rollback();
                JOptionPane.showMessageDialog(this, "Failed to update employee: " + getFriendlyEmployeeError(ex));
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to update employee: " + getFriendlyEmployeeError(ex));
        }
    }

    private void clearFields() {
        selectedUserId = null;
        usernameField.setText("");
        passwordField.setText("");
        updatingGeneratedUsername = true;
        firstNameField.setText("");
        middleNameField.setText("");
        lastNameField.setText("");
        updatingGeneratedUsername = false;
        emailField.setText("");
        phoneField.setText("");
        badgeIdField.setText("");
        selectCompensationType("HOURLY");
        salaryAmountField.setText("");
        roleBox.setSelectedIndex(0);
        activeCheckBox.setSelected(true);
        activeCheckBox.setEnabled(true);
        originalFirstName = "";
        originalMiddleName = "";
        originalLastName = "";
        originalFullName = "";
        originalEmail = "";
        originalIsActive = true;
        lastGeneratedUsername = "";
        employeeTable.clearSelection();
        storeSearchField.setText("");
        loadStoresForUser(null);
        deleteButton.setEnabled(false);
        usernameField.requestFocusInWindow();
    }

    private void updateGeneratedUsername() {
        if (updatingGeneratedUsername) {
            return;
        }

        String currentUsername = usernameField.getText().trim();
        if (!currentUsername.isEmpty() && !currentUsername.equalsIgnoreCase(lastGeneratedUsername)) {
            return;
        }

        String generatedUsername = generateUsername(firstNameField.getText().trim(), lastNameField.getText().trim());
        updatingGeneratedUsername = true;
        usernameField.setText(generatedUsername);
        updatingGeneratedUsername = false;
        lastGeneratedUsername = generatedUsername;
    }

    private static String generateUsername(String firstName, String lastName) {
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            return "";
        }
        String firstInitial = firstName.trim().substring(0, 1).toUpperCase();
        return firstInitial + "-" + toDisplayNamePart(lastName);
    }

    private static String composeFullName(String firstName, String middleName, String lastName) {
        StringBuilder fullName = new StringBuilder();
        appendNamePart(fullName, firstName);
        appendNamePart(fullName, middleName);
        appendNamePart(fullName, lastName);
        return fullName.toString();
    }

    private static void appendNamePart(StringBuilder builder, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" ");
        }
        builder.append(value.trim());
    }

    private static String toDisplayNamePart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return trimmed.toUpperCase();
        }
        return trimmed.substring(0, 1).toUpperCase() + trimmed.substring(1);
    }

    private static boolean sameText(String left, String right) {
        String normalizedLeft = left == null ? "" : left.trim();
        String normalizedRight = right == null ? "" : right.trim();
        return normalizedLeft.equals(normalizedRight);
    }

    private void selectEmployeeInTable(int userId) {
        for (int modelRow = 0; modelRow < employeeModel.getRowCount(); modelRow++) {
            Object value = employeeModel.getValueAt(modelRow, 0);
            if (value != null && Integer.parseInt(value.toString()) == userId) {
                int viewRow = employeeTable.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    employeeTable.setRowSelectionInterval(viewRow, viewRow);
                    employeeTable.scrollRectToVisible(employeeTable.getCellRect(viewRow, 0, true));
                    loadSelectedEmployee();
                }
                return;
            }
        }
    }

    private List<Integer> getSelectedLocationIds() {
        List<Integer> locationIds = new ArrayList<>();
        for (int row = 0; row < storeModel.getRowCount(); row++) {
            Object assignedValue = storeModel.getValueAt(row, 0);
            boolean assigned = assignedValue instanceof Boolean && (Boolean) assignedValue;
            if (assigned) {
                locationIds.add(Integer.parseInt(storeModel.getValueAt(row, 1).toString()));
            }
        }
        return locationIds;
    }

    private void saveStoreAssignments(Connection conn, int userId, List<Integer> locationIds) throws SQLException {
        String deleteSql = "DELETE FROM user_locations WHERE user_id = ?";
        String insertSql = "INSERT INTO user_locations (user_id, location_id) VALUES (?, ?)";

        try (PreparedStatement deletePs = conn.prepareStatement(deleteSql);
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

            deletePs.setInt(1, userId);
            deletePs.executeUpdate();

            for (Integer locationId : locationIds) {
                insertPs.setInt(1, userId);
                insertPs.setInt(2, locationId);
                insertPs.addBatch();
            }

            insertPs.executeBatch();
        }
    }

    private static String getFriendlyEmployeeError(Exception ex) {
        if (isDuplicateBadgeError(ex)) {
            return "That badge ID is already assigned to another employee.";
        }
        return ex.getMessage();
    }

    private static boolean isDuplicateBadgeError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (current instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && message != null
                    && message.toLowerCase().contains("badge")) {
                return true;
            }
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("duplicate")
                        && normalized.contains("badge")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
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

    private static boolean isSupabaseAuthUserNotFound(Exception ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase();
        return normalized.contains("user not found")
                || normalized.contains("user from sub claim in jwt does not exist");
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

                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user_locations WHERE user_id = ?")) {
                    ps.setInt(1, selectedUserId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
                    ps.setInt(1, selectedUserId);
                    int deletedRows = ps.executeUpdate();
                    if (deletedRows == 0) {
                        throw new IllegalStateException("Employee record was not found.");
                    }
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

    private void applyStoreFilter() {
        if (storeSorter == null) {
            return;
        }

        String text = storeSearchField == null ? "" : storeSearchField.getText().trim();
        if (text.isEmpty()) {
            storeSorter.setRowFilter(null);
        } else {
            storeSorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text)));
        }
    }
}
