package ui.screens;

import managers.PermissionManager;
import ui.helpers.WindowHelper;
import data.DB;
import ui.components.AppMenuBar;


import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Roles_Permission extends JFrame {

    private JList<RoleItem> roleList;
    private DefaultListModel<RoleItem> roleListModel;

    private Map<String, JCheckBox> permissionCheckboxes = new LinkedHashMap<>();

    private JButton saveButton;
    private JButton addRoleButton;

    public Roles_Permission() {
        setTitle("Role & Permission Management");
        setSize(700, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "Roles_Permissions"));
        setLayout(new BorderLayout());

        // LEFT: ROLE LIST
        roleListModel = new DefaultListModel<>();
        roleList = new JList<>(roleListModel);
        JScrollPane roleScroll = new JScrollPane(roleList);
        roleScroll.setPreferredSize(new Dimension(200, 0));

        // RIGHT: PERMISSIONS
        JPanel permissionPanel = new JPanel();
        permissionPanel.setLayout(new BoxLayout(permissionPanel, BoxLayout.Y_AXIS));

        addPermission("MAKE_SALE", "Make Sale");
        addPermission("NEW_ITEM", "Add Item");
        addPermission("RECEIVING_INVENTORY", "Receiving Inventory");
        addPermission("VIEW_RECEIVING_HISTORY", "View Receiving History");
        addPermission("STORE_TRANSFER", "Store Transfer");
        addPermission("EDIT_ITEM", "Edit Item");
        addPermission("EMPLOYEE_MANAGEMENT", "Employee Management");
        addPermission("ROLE_MANAGEMENT", "Roles & Permission");
        addPermission("CHANGE_STORE", "Change Store");
        addPermission("VIEW_REPORTS", "View Reports");
        addPermission("VIEW_SALES", "View Sales");
        addPermission("VIEW_INVENTORY", "View Inventory");
        addPermission("VIEW_ITEM_DETAILS", "View Item Details");
        addPermission("CUSTOMER_ACCOUNTS", "Customer Accounts");
        addPermission("TIME_CLOCK", "Time Clock");
        addPermission("TIME_CLOCK_MANAGEMENT", "Time Clock Management");
        addPermission("PAYROLL_DASHBOARD", "Payroll Dashboard");
        addPermission("SET_CREDIT_LIMIT", "Set Credit Limit");
        addPermission("EDIT_ACCOUNT_NUMBER", "Edit Account Number");
        addPermission("LOCAL_DEVICE_SETTINGS", "Local Device Settings");
        ensurePermissionDefinitionsExist();

        JScrollPane permScroll = new JScrollPane(permissionPanel);

        for (JCheckBox cb : permissionCheckboxes.values()) {
            permissionPanel.add(cb);
        }

        // TOP BUTTONS
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addRoleButton = new JButton("Add Role");
        topPanel.add(addRoleButton);

        // BOTTOM BUTTON
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        saveButton = new JButton("Save");
        bottomPanel.add(saveButton);

        add(topPanel, BorderLayout.NORTH);
        add(roleScroll, BorderLayout.WEST);
        add(permScroll, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        loadRoles();

        roleList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadPermissionsForSelectedRole();
            }
        });

        saveButton.addActionListener(e -> savePermissions());

        addRoleButton.addActionListener(e -> addNewRole());

        WindowHelper.showPosWindow(this);
    }

    private void addPermission(String key, String label) {
        JCheckBox cb = new JCheckBox(label);
        permissionCheckboxes.put(key, cb);
    }

    private void ensurePermissionDefinitionsExist() {
        try (Connection conn = DB.getConnection()) {
            Set<String> permissionColumns = getPermissionTableColumns(conn);
            if (!permissionColumns.contains("permission_key")) {
                throw new SQLException("The permissions table does not have a permission_key column.");
            }

            List<String> optionalColumns = new ArrayList<>();
            if (permissionColumns.contains("permission_name")) {
                optionalColumns.add("permission_name");
            }
            if (permissionColumns.contains("permission_label")) {
                optionalColumns.add("permission_label");
            }
            if (permissionColumns.contains("name")) {
                optionalColumns.add("name");
            }
            if (permissionColumns.contains("label")) {
                optionalColumns.add("label");
            }
            if (permissionColumns.contains("description")) {
                optionalColumns.add("description");
            }

            String columnsSql = "permission_key";
            String selectSql = "?";
            for (String column : optionalColumns) {
                columnsSql += ", " + column;
                selectSql += ", ?";
            }

            String insertSql = "INSERT INTO permissions (" + columnsSql + ") " +
                    "SELECT " + selectSql + " " +
                    "WHERE NOT EXISTS (SELECT 1 FROM permissions WHERE UPPER(permission_key) = UPPER(?))";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (Map.Entry<String, JCheckBox> entry : permissionCheckboxes.entrySet()) {
                    String key = entry.getKey();
                    String label = entry.getValue().getText();

                    int parameterIndex = 1;
                    ps.setString(parameterIndex++, key);
                    for (int i = 0; i < optionalColumns.size(); i++) {
                        ps.setString(parameterIndex++, label);
                    }
                    ps.setString(parameterIndex, key);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to verify permission definitions.\n\n" + ex.getMessage(),
                    "Permission Setup Error",
                    JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }
    }

    private Set<String> getPermissionTableColumns(Connection conn) throws SQLException {
        Set<String> columns = new HashSet<>();
        String sql = """
                SELECT LOWER(column_name) AS column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'permissions'
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                columns.add(rs.getString("column_name"));
            }
        }

        return columns;
    }

    private void loadRoles() {
        roleListModel.clear();

        String sql = "SELECT role_id, role_name FROM roles ORDER BY role_name";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                roleListModel.addElement(new RoleItem(
                        rs.getInt("role_id"),
                        rs.getString("role_name")
                ));
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void loadPermissionsForSelectedRole() {
        RoleItem selected = roleList.getSelectedValue();
        if (selected == null) return;

        // reset all
        for (JCheckBox cb : permissionCheckboxes.values()) {
            cb.setSelected(false);
        }

        String sql = """
                SELECT p.permission_key
                FROM role_permissions rp
                JOIN permissions p ON rp.permission_id = p.permission_id
                WHERE rp.role_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, selected.id);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("permission_key").toUpperCase();
                    if (permissionCheckboxes.containsKey(key)) {
                        permissionCheckboxes.get(key).setSelected(true);
                    }
                }
            }

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void savePermissions() {
        RoleItem selected = roleList.getSelectedValue();
        if (selected == null) return;

        try (Connection conn = DB.getConnection()) {

            conn.setAutoCommit(false);
            ensureSelectedPermissionsExist(conn);

            // DELETE OLD
            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM role_permissions WHERE role_id = ?")) {
                delete.setInt(1, selected.id);
                delete.executeUpdate();
            }

            // INSERT NEW
            String insertSql = """
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT ?, permission_id FROM permissions WHERE UPPER(permission_key) = UPPER(?)
                    """;

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {

                for (Map.Entry<String, JCheckBox> entry : permissionCheckboxes.entrySet()) {
                    if (entry.getValue().isSelected()) {
                        insert.setInt(1, selected.id);
                        insert.setString(2, entry.getKey());
                        insert.addBatch();
                    }
                }

                insert.executeBatch();
            }

            conn.commit();

            if (selected.name != null && selected.name.equalsIgnoreCase(PermissionManager.getCurrentRole())) {
                PermissionManager.refreshOpenWindows();
            }

            JOptionPane.showMessageDialog(this, "Permissions updated.");

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private void ensureSelectedPermissionsExist(Connection conn) throws SQLException {
        String sql = "SELECT permission_id FROM permissions WHERE UPPER(permission_key) = UPPER(?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, JCheckBox> entry : permissionCheckboxes.entrySet()) {
                if (!entry.getValue().isSelected()) {
                    continue;
                }

                ps.setString(1, entry.getKey());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Missing permission definition: " + entry.getKey());
                    }
                }
            }
        }
    }

    private void addNewRole() {
        String name = JOptionPane.showInputDialog(this, "Enter new role name:");
        if (name == null || name.isBlank()) return;

        String sql = "INSERT INTO roles (role_name, description) VALUES (?, ?)";

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, name.toUpperCase());
            ps.setString(2, "Custom role");
            ps.executeUpdate();

            loadRoles();

        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    private static class RoleItem {
        int id;
        String name;

        RoleItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return formatRoleName(name);
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
}
