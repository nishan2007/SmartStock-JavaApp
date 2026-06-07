package ui.screens;

import managers.PermissionManager;
import ui.helpers.WindowHelper;
import data.DB;
import services.OfflineWriteGuard;
import services.ReferenceDataSyncService;
import ui.components.AppMenuBar;


import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class Roles_Permission extends JFrame {
    private static final class PermissionDefinition {
        final String key;
        final String label;
        final String group;
        final String description;

        PermissionDefinition(String key, String label, String group, String description) {
            this.key = key;
            this.label = label;
            this.group = group;
            this.description = description;
        }
    }

    private JList<RoleItem> roleList;
    private DefaultListModel<RoleItem> roleListModel;

    private Map<String, JCheckBox> permissionCheckboxes = new LinkedHashMap<>();
    private Map<String, JCheckBox> mobilePermissionCheckboxes = new LinkedHashMap<>();
    private JPanel permissionPanel;
    private JPanel mobilePermissionPanel;
    private boolean mobilePermissionsAvailable = false;

    private JButton saveButton;
    private JButton addRoleButton;
    private static final Map<String, String> DEFAULT_PERMISSIONS = createDefaultPermissions();
    private static final Map<String, String> DEFAULT_PERMISSION_DESCRIPTIONS = createDefaultPermissionDescriptions();
    private static final Map<String, String> DEFAULT_PERMISSION_GROUPS = createDefaultPermissionGroups();

    public Roles_Permission() {
        setTitle("Role & Permission Management");
        setSize(900, 650);
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
        permissionPanel = new JPanel();
        permissionPanel.setLayout(new BoxLayout(permissionPanel, BoxLayout.Y_AXIS));
        permissionPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mobilePermissionPanel = new JPanel();
        mobilePermissionPanel.setLayout(new BoxLayout(mobilePermissionPanel, BoxLayout.Y_AXIS));
        mobilePermissionPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        ensurePermissionDefinitionsExist();
        loadPermissionDefinitions();
        loadMobilePermissionDefinitions();

        JScrollPane desktopPermScroll = new JScrollPane(
                wrapPermissionColumn("Desktop Permissions", permissionPanel),
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        desktopPermScroll.getVerticalScrollBar().setUnitIncrement(16);
        JScrollPane mobilePermScroll = new JScrollPane(
                wrapPermissionColumn("App Permissions", mobilePermissionPanel),
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        mobilePermScroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel permissionsContainer = new JPanel(new GridLayout(1, 2, 10, 0));
        permissionsContainer.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        permissionsContainer.add(desktopPermScroll);
        permissionsContainer.add(mobilePermScroll);

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
        add(permissionsContainer, BorderLayout.CENTER);
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

    private JCheckBox buildPermissionCheckBox(String key, String label, String description) {
        JCheckBox cb = new JCheckBox(formatPermissionText(label, description));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setOpaque(false);

        String tooltip = key;
        if (description != null && !description.isBlank()) {
            tooltip += " - " + description;
        }
        cb.setToolTipText(tooltip);
        return cb;
    }

    private JPanel wrapPermissionColumn(String title, JPanel contentPanel) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        wrapper.add(titleLabel, BorderLayout.NORTH);
        wrapper.add(contentPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel createSectionPanel(String sectionTitle) {
        JPanel sectionPanel = new JPanel();
        sectionPanel.setLayout(new BoxLayout(sectionPanel, BoxLayout.Y_AXIS));
        sectionPanel.setOpaque(false);
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionPanel.setBorder(BorderFactory.createTitledBorder(sectionTitle));
        return sectionPanel;
    }

    private void ensurePermissionDefinitionsExist() {
        try (Connection conn = DB.getConnection()) {
            Set<String> permissionColumns = getTableColumns(conn, "permissions");
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
            if (permissionColumns.contains("permission_group")) {
                optionalColumns.add("permission_group");
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
                for (Map.Entry<String, String> entry : DEFAULT_PERMISSIONS.entrySet()) {
                    String key = entry.getKey();
                    String label = entry.getValue();
                    String description = defaultPermissionDescription(key, label);
                    String group = defaultPermissionGroup(key);

                    int parameterIndex = 1;
                    ps.setString(parameterIndex++, key);
                    for (String column : optionalColumns) {
                        if ("description".equals(column)) {
                            ps.setString(parameterIndex++, description);
                        } else if ("permission_group".equals(column)) {
                            ps.setString(parameterIndex++, group);
                        } else {
                            ps.setString(parameterIndex++, label);
                        }
                    }
                    ps.setString(parameterIndex, key);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            if (permissionColumns.contains("description")) {
                String updateSql = """
                        UPDATE permissions
                        SET description = ?
                        WHERE UPPER(permission_key) = UPPER(?)
                          AND (description IS NULL OR TRIM(description) = '')
                        """;
                try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                    for (Map.Entry<String, String> entry : DEFAULT_PERMISSIONS.entrySet()) {
                        String key = entry.getKey();
                        String description = defaultPermissionDescription(key, entry.getValue());
                        update.setString(1, description);
                        update.setString(2, key);
                        update.addBatch();
                    }
                    update.executeBatch();
                }
            }

            if (permissionColumns.contains("permission_group")) {
                String groupSql = """
                        UPDATE permissions
                        SET permission_group = ?
                        WHERE UPPER(permission_key) = UPPER(?)
                          AND (permission_group IS NULL OR TRIM(permission_group) = '')
                        """;
                try (PreparedStatement update = conn.prepareStatement(groupSql)) {
                    for (Map.Entry<String, String> entry : DEFAULT_PERMISSIONS.entrySet()) {
                        String key = entry.getKey();
                        String group = defaultPermissionGroup(key);
                        update.setString(1, group);
                        update.setString(2, key);
                        update.addBatch();
                    }
                    update.executeBatch();
                }
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

    private void loadPermissionDefinitions() {
        permissionCheckboxes.clear();
        permissionPanel.removeAll();
        List<PermissionDefinition> definitions = new ArrayList<>();

        try (Connection conn = DB.getConnection()) {
            Set<String> permissionColumns = getTableColumns(conn, "permissions");
            if (!permissionColumns.contains("permission_key")) {
                throw new SQLException("The permissions table does not have a permission_key column.");
            }

            String displayExpression = buildPermissionDisplayExpression(permissionColumns);
            String groupSelect = permissionColumns.contains("permission_group")
                    ? "permission_group"
                    : "NULL AS permission_group";
            String descriptionSelect = permissionColumns.contains("description")
                    ? "description"
                    : "NULL AS description";
            String orderSql = permissionColumns.contains("sort_order")
                    ? "permission_group NULLS LAST, sort_order NULLS LAST, display_name, permission_key"
                    : "display_name, permission_key";

            String sql = "SELECT permission_key, " + displayExpression + " AS display_name, " +
                    groupSelect + ", " + descriptionSelect + " " +
                    "FROM permissions " +
                    "WHERE permission_key IS NOT NULL AND TRIM(permission_key) <> '' " +
                    "ORDER BY " + orderSql;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("permission_key");
                    String label = rs.getString("display_name");
                    String group = rs.getString("permission_group");
                    String description = rs.getString("description");
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    String normalizedKey = key.trim().toUpperCase();
                    String resolvedLabel = label == null || label.isBlank() ? formatRoleName(key) : label;
                    String resolvedGroup = (group == null || group.isBlank())
                            ? defaultPermissionGroup(normalizedKey)
                            : formatRoleName(group);
                    String resolvedDescription = (description == null || description.isBlank())
                            ? defaultPermissionDescription(normalizedKey, resolvedLabel)
                            : description;
                    definitions.add(new PermissionDefinition(
                            normalizedKey,
                            resolvedLabel,
                            resolvedGroup,
                            resolvedDescription
                    ));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load permissions.\n\n" + ex.getMessage(),
                    "Permission Load Error",
                    JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();

            for (Map.Entry<String, String> entry : DEFAULT_PERMISSIONS.entrySet()) {
                String key = entry.getKey();
                String label = entry.getValue();
                definitions.add(new PermissionDefinition(
                        key,
                        label,
                        defaultPermissionGroup(key),
                        defaultPermissionDescription(key, label)
                ));
            }
        }

        renderPermissionSections(permissionPanel, permissionCheckboxes, definitions);
        permissionPanel.revalidate();
        permissionPanel.repaint();
    }

    private void loadMobilePermissionDefinitions() {
        mobilePermissionCheckboxes.clear();
        mobilePermissionPanel.removeAll();
        mobilePermissionsAvailable = false;
        List<PermissionDefinition> definitions = new ArrayList<>();

        try (Connection conn = DB.getConnection()) {
            if (!tableExists(conn, "mobile_permissions") || !tableExists(conn, "role_mobile_permissions")) {
                JLabel missingLabel = new JLabel("<html>Run the mobile permissions SQL first to manage app permissions here.</html>");
                missingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                mobilePermissionPanel.add(missingLabel);
                return;
            }

            Set<String> mobilePermissionColumns = getTableColumns(conn, "mobile_permissions");
            if (!mobilePermissionColumns.contains("permission_key")) {
                throw new SQLException("The mobile_permissions table does not have a permission_key column.");
            }

            String displayExpression = buildPermissionDisplayExpression(mobilePermissionColumns);
            String groupSelect = mobilePermissionColumns.contains("permission_group")
                    ? "permission_group"
                    : "NULL AS permission_group";
            String descriptionSelect = mobilePermissionColumns.contains("description")
                    ? "description"
                    : "NULL AS description";
            String orderSql = mobilePermissionColumns.contains("sort_order")
                    ? "permission_group NULLS LAST, sort_order NULLS LAST, display_name, permission_key"
                    : "display_name, permission_key";
            String sql = "SELECT permission_key, " + displayExpression + " AS display_name, " +
                    groupSelect + ", " + descriptionSelect + " " +
                    "FROM mobile_permissions " +
                    "WHERE permission_key IS NOT NULL AND TRIM(permission_key) <> '' " +
                    "ORDER BY " + orderSql;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("permission_key");
                    if (key == null || key.isBlank()) {
                        continue;
                    }
                    String resolvedGroup = rs.getString("permission_group");
                    String resolvedLabel = rs.getString("display_name");
                    if (resolvedLabel == null || resolvedLabel.isBlank()) {
                        resolvedLabel = formatRoleName(key);
                    }
                    if (resolvedGroup == null || resolvedGroup.isBlank()) {
                        resolvedGroup = "General";
                    }
                    String description = rs.getString("description");
                    if (description == null || description.isBlank()) {
                        description = "Allows " + formatRoleName(key).toLowerCase() + " in the mobile app.";
                    }
                    definitions.add(new PermissionDefinition(
                            key.trim(),
                            resolvedLabel,
                            formatRoleName(resolvedGroup),
                            description
                    ));
                }
            }
            renderPermissionSections(mobilePermissionPanel, mobilePermissionCheckboxes, definitions);
            mobilePermissionsAvailable = true;
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "Failed to load app permissions.\n\n" + ex.getMessage(),
                    "App Permission Load Error",
                    JOptionPane.ERROR_MESSAGE
            );
            ex.printStackTrace();
        }

        mobilePermissionPanel.revalidate();
        mobilePermissionPanel.repaint();
    }

    private void renderPermissionSections(
            JPanel targetPanel,
            Map<String, JCheckBox> checkBoxMap,
            List<PermissionDefinition> definitions
    ) {
        targetPanel.removeAll();
        Map<String, List<PermissionDefinition>> bySection = new TreeMap<>();
        for (PermissionDefinition definition : definitions) {
            String section = definition.group == null || definition.group.isBlank()
                    ? "General"
                    : definition.group;
            bySection.computeIfAbsent(section, ignored -> new ArrayList<>()).add(definition);
        }

        for (Map.Entry<String, List<PermissionDefinition>> sectionEntry : bySection.entrySet()) {
            JPanel sectionPanel = createSectionPanel(sectionEntry.getKey());
            List<PermissionDefinition> sectionPermissions = sectionEntry.getValue();
            sectionPermissions.sort(Comparator.comparing(permission -> permission.label.toLowerCase()));
            for (PermissionDefinition definition : sectionPermissions) {
                if (checkBoxMap.containsKey(definition.key)) {
                    continue;
                }
                JCheckBox checkBox = buildPermissionCheckBox(
                        definition.key,
                        definition.label,
                        definition.description
                );
                checkBoxMap.put(definition.key, checkBox);
                sectionPanel.add(checkBox);
            }
            targetPanel.add(sectionPanel);
        }
    }

    private String buildPermissionDisplayExpression(Set<String> permissionColumns) {
        List<String> labelColumns = new ArrayList<>();
        if (permissionColumns.contains("permission_name")) {
            labelColumns.add("permission_name");
        }
        if (permissionColumns.contains("permission_label")) {
            labelColumns.add("permission_label");
        }
        if (permissionColumns.contains("name")) {
            labelColumns.add("name");
        }
        if (permissionColumns.contains("label")) {
            labelColumns.add("label");
        }

        if (labelColumns.isEmpty()) {
            return "permission_key";
        }

        StringBuilder expression = new StringBuilder("COALESCE(");
        for (String column : labelColumns) {
            expression.append("NULLIF(TRIM(").append(column).append("), ''), ");
        }
        expression.append("permission_key)");
        return expression.toString();
    }

    private Set<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        String sql = """
                SELECT LOWER(column_name) AS column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }

        return columns;
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        String sql = """
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
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
        for (JCheckBox cb : mobilePermissionCheckboxes.values()) {
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

        if (mobilePermissionsAvailable) {
            loadMobilePermissionsForRole(selected.id);
        }
    }

    private void loadMobilePermissionsForRole(int roleId) {
        String sql = """
                SELECT permission_key
                FROM role_mobile_permissions
                WHERE role_id = ?
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, roleId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("permission_key");
                    if (mobilePermissionCheckboxes.containsKey(key)) {
                        mobilePermissionCheckboxes.get(key).setSelected(true);
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
            OfflineWriteGuard.requireCloudForGlobalWrite("Role and permission");

            conn.setAutoCommit(false);
            ensureSelectedPermissionsExist(conn);
            if (mobilePermissionsAvailable) {
                ensureSelectedMobilePermissionsExist(conn);
            }

            Set<Integer> existingPermissionIds = loadRolePermissionIds(conn, selected.id);
            Map<String, Integer> selectedPermissionIds = loadSelectedPermissionIds(conn);
            Set<Integer> removedPermissionIds = new HashSet<>(existingPermissionIds);
            removedPermissionIds.removeAll(selectedPermissionIds.values());

            try (PreparedStatement delete = conn.prepareStatement(
                    "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?")) {
                for (Integer permissionId : removedPermissionIds) {
                    ReferenceDataSyncService.recordTombstone(conn, "role_permissions", Map.of(
                            "role_id", selected.id,
                            "permission_id", permissionId
                    ));
                    delete.setInt(1, selected.id);
                    delete.setInt(2, permissionId);
                    delete.addBatch();
                }
                delete.executeBatch();
            }

            String insertSql = """
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT ?, permission_id FROM permissions WHERE UPPER(permission_key) = UPPER(?)
                    ON CONFLICT (role_id, permission_id) DO NOTHING
                    """;

            try (PreparedStatement insert = conn.prepareStatement(insertSql)) {

                for (String permissionKey : selectedPermissionIds.keySet()) {
                    insert.setInt(1, selected.id);
                    insert.setString(2, permissionKey);
                    insert.addBatch();
                }

                insert.executeBatch();
            }

            if (mobilePermissionsAvailable) {
                Set<String> existingMobileKeys = loadRoleMobilePermissionKeys(conn, selected.id);
                Set<String> selectedMobileKeys = selectedMobilePermissionKeys();
                Set<String> removedMobileKeys = new HashSet<>(existingMobileKeys);
                removedMobileKeys.removeAll(selectedMobileKeys);

                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM role_mobile_permissions WHERE role_id = ? AND permission_key = ?")) {
                    for (String permissionKey : removedMobileKeys) {
                        ReferenceDataSyncService.recordTombstone(conn, "role_mobile_permissions", Map.of(
                                "role_id", selected.id,
                                "permission_key", permissionKey
                        ));
                        delete.setInt(1, selected.id);
                        delete.setString(2, permissionKey);
                        delete.addBatch();
                    }
                    delete.executeBatch();
                }

                String mobileInsertSql = """
                        INSERT INTO role_mobile_permissions (role_id, permission_key)
                        SELECT ?, permission_key FROM mobile_permissions WHERE permission_key = ?
                        ON CONFLICT (role_id, permission_key) DO NOTHING
                        """;

                try (PreparedStatement insert = conn.prepareStatement(mobileInsertSql)) {
                    for (String permissionKey : selectedMobileKeys) {
                        insert.setInt(1, selected.id);
                        insert.setString(2, permissionKey);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
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

    private Set<Integer> loadRolePermissionIds(Connection conn, int roleId) throws SQLException {
        Set<Integer> permissionIds = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT permission_id FROM role_permissions WHERE role_id = ?")) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    permissionIds.add(rs.getInt("permission_id"));
                }
            }
        }
        return permissionIds;
    }

    private Map<String, Integer> loadSelectedPermissionIds(Connection conn) throws SQLException {
        Map<String, Integer> permissionIds = new LinkedHashMap<>();
        String sql = "SELECT permission_id FROM permissions WHERE UPPER(permission_key) = UPPER(?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, JCheckBox> entry : permissionCheckboxes.entrySet()) {
                if (!entry.getValue().isSelected()) {
                    continue;
                }
                ps.setString(1, entry.getKey());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        permissionIds.put(entry.getKey(), rs.getInt("permission_id"));
                    }
                }
            }
        }
        return permissionIds;
    }

    private Set<String> loadRoleMobilePermissionKeys(Connection conn, int roleId) throws SQLException {
        Set<String> permissionKeys = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT permission_key FROM role_mobile_permissions WHERE role_id = ?")) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    permissionKeys.add(rs.getString("permission_key"));
                }
            }
        }
        return permissionKeys;
    }

    private Set<String> selectedMobilePermissionKeys() {
        Set<String> permissionKeys = new HashSet<>();
        for (Map.Entry<String, JCheckBox> entry : mobilePermissionCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                permissionKeys.add(entry.getKey());
            }
        }
        return permissionKeys;
    }

    private void ensureSelectedMobilePermissionsExist(Connection conn) throws SQLException {
        String sql = "SELECT permission_key FROM mobile_permissions WHERE permission_key = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, JCheckBox> entry : mobilePermissionCheckboxes.entrySet()) {
                if (!entry.getValue().isSelected()) {
                    continue;
                }

                ps.setString(1, entry.getKey());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Missing app permission definition: " + entry.getKey());
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

    private static String formatPermissionText(String label, String description) {
        if (description == null || description.isBlank()) {
            return label;
        }
        return "<html><b>" + escapeHtml(label) + "</b><br><span style='color:#666666;'>" +
                escapeHtml(description) + "</span></html>";
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String defaultPermissionDescription(String permissionKey, String fallbackLabel) {
        if (permissionKey == null) {
            return fallbackLabel;
        }
        String description = DEFAULT_PERMISSION_DESCRIPTIONS.get(permissionKey.toUpperCase());
        return (description == null || description.isBlank()) ? fallbackLabel : description;
    }

    private static String defaultPermissionGroup(String permissionKey) {
        if (permissionKey == null) {
            return "General";
        }
        String group = DEFAULT_PERMISSION_GROUPS.get(permissionKey.toUpperCase());
        return (group == null || group.isBlank()) ? "General" : group;
    }

    private static Map<String, String> createDefaultPermissions() {
        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("MAKE_SALE", "Make Sale");
        permissions.put("APPLY_SALE_DISCOUNT", "Apply Sale Discount");
        permissions.put("SALE_DISCOUNT_OVERRIDE", "Sale Discount Override");
        permissions.put("RETURN_OVERRIDE", "Return Override");
        permissions.put("SALE_DISCOUNT_LIMIT_SETTINGS", "Sale Discount Limit Settings");
        permissions.put("SALE_RETURN_APPROVAL_SETTINGS", "Sale Return Approval Settings");
        permissions.put("CHANGE_SALE_ITEM_PRICE", "Change Sale Item Price");
        permissions.put("PROCESS_RETURNS", "Process Returns");
        permissions.put("END_OF_DAY", "End of Day");
        permissions.put("BALANCE_DRAWER", "Balance Draw");
        permissions.put("BALANCE_SHEET", "Balance Sheet");
        permissions.put("VIEW_SALES", "View Sales");
        permissions.put("NEW_ITEM", "Add Item");
        permissions.put("EDIT_ITEM", "Edit Item");
        permissions.put("RECEIVING_INVENTORY", "Receiving Inventory");
        permissions.put("RECEIVING_STOCK_OVERRIDE", "Receiving Stock Override");
        permissions.put("VIEW_RECEIVING_HISTORY", "View Receiving History");
        permissions.put("STORE_TRANSFER", "Store Transfer");
        permissions.put("VIEW_INVENTORY", "View Inventory");
        permissions.put("VIEW_ITEM_DETAILS", "View Item Details");
        permissions.put("VIEW_COST_PRICE", "View Cost Price");
        permissions.put("VIEW_VENDOR", "View Vendor");
        permissions.put("VIEW_CREATED_BY", "View Created By");
        permissions.put("MANUAL_ADJUSTMENT", "Manual Adjustment");
        permissions.put("DEPARTMENT_MANAGEMENT", "Department Management");
        permissions.put("VENDOR_MANAGEMENT", "Vendor Management");
        permissions.put("CUSTOMER_ACCOUNTS", "Customer Accounts");
        permissions.put("CREATE_CUSTOM_ORDER", "Create Custom Order");
        permissions.put("MANAGE_CUSTOM_ORDERS", "Manage Custom Orders");
        permissions.put("VIEW_ASSIGNED_CUSTOM_ORDERS", "View Assigned Custom Orders");
        permissions.put("SALES_QUOTES_ORDERS", "Sales Quotes / Orders");
        permissions.put("CREATE_SALES_QUOTE", "Create Sales Quote");
        permissions.put("MANAGE_SALES_ORDERS", "Manage Sales Orders");
        permissions.put("POST_SALES_ORDER_DELIVERY", "Post Sales Order Delivery");
        permissions.put("ORDERS_MANAGER_DASHBOARD", "Orders Manager Dashboard");
        permissions.put("ORDERS_END_OF_DAY", "Orders End Of Day");
        permissions.put("CUSTOM_ORDER_REFUNDS", "Custom Order Refunds");
        permissions.put("CUSTOM_ORDER_LINE_RETURNS", "Custom Order Line Returns");
        permissions.put("CUSTOM_ORDER_LINE_DELIVERY", "Custom Order Line Delivery");
        permissions.put("CUSTOM_ORDER_LINE_DISCOUNT", "Custom Order Line Discount");
        permissions.put("CUSTOM_ORDER_DEPOSIT_OVERRIDE", "Custom Order Deposit Override");
        permissions.put("CUSTOM_ORDER_DEPOSIT_SETTINGS", "Custom Order Deposit Settings");
        permissions.put("CUSTOM_ORDER_REFUND_APPROVAL", "Custom Order Refund Approval");
        permissions.put("CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS", "Custom Order Refund Approval Settings");
        permissions.put("CUSTOM_ORDER_PRODUCTION_STEPS", "Custom Order Production Steps");
        permissions.put("CUSTOM_ORDER_CANCEL", "Cancel Custom Orders");
        permissions.put("SET_CREDIT_LIMIT", "Set Credit Limit");
        permissions.put("EDIT_ACCOUNT_NUMBER", "Edit Account Number");
        permissions.put("EMPLOYEE_MANAGEMENT", "Employee Management");
        permissions.put("TIME_CLOCK", "Time Clock");
        permissions.put("TIME_CLOCK_MANAGEMENT", "Time Clock Management");
        permissions.put("TIME_CLOCK_OVERRIDE", "Time Clock Override");
        permissions.put("PAYROLL_DASHBOARD", "Payroll Dashboard");
        permissions.put("ROLE_MANAGEMENT", "Roles & Permission");
        permissions.put("LOCATION_MANAGEMENT", "Location Management");
        permissions.put("CASH_DRAWER_MANAGEMENT", "Cash Drawer Management");
        permissions.put("COMPANY_PREFERENCES", "Company Preferences");
        permissions.put("CHANGE_STORE", "Change Store");
        permissions.put("VIEW_REPORTS", "View Reports");
        permissions.put("LOCAL_DEVICE_SETTINGS", "Workstation Settings");
        permissions.put("HARDWARE_SETUP", "Hardware Settings");
        return permissions;
    }

    private static Map<String, String> createDefaultPermissionDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("MAKE_SALE", "Allows creating and completing normal sales transactions.");
        descriptions.put("APPLY_SALE_DISCOUNT", "Allows applying line and sale-level discounts without manager override.");
        descriptions.put("SALE_DISCOUNT_OVERRIDE", "Allows approving discount overrides above configured limits.");
        descriptions.put("RETURN_OVERRIDE", "Allows approving return amount overrides above configured limits.");
        descriptions.put("SALE_DISCOUNT_LIMIT_SETTINGS", "Allows changing sale discount approval thresholds in company settings.");
        descriptions.put("SALE_RETURN_APPROVAL_SETTINGS", "Allows changing return approval thresholds in company settings.");
        descriptions.put("CHANGE_SALE_ITEM_PRICE", "Allows editing item unit prices during a sale without override.");
        descriptions.put("PROCESS_RETURNS", "Allows creating and completing return transactions.");
        descriptions.put("END_OF_DAY", "Allows running end-of-day reconciliation and closeout tasks.");
        descriptions.put("BALANCE_DRAWER", "Allows balancing drawer sessions and submitting counted cash totals.");
        descriptions.put("BALANCE_SHEET", "Allows viewing balance sheet totals and logging business expenses.");
        descriptions.put("VIEW_SALES", "Allows viewing past sales and related transaction history.");
        descriptions.put("NEW_ITEM", "Allows creating new inventory items.");
        descriptions.put("EDIT_ITEM", "Allows editing existing inventory item details.");
        descriptions.put("RECEIVING_INVENTORY", "Allows receiving stock into inventory quantities.");
        descriptions.put("RECEIVING_STOCK_OVERRIDE", "Allows correcting counted shelf/storage stock during receiving with an audit trail.");
        descriptions.put("VIEW_RECEIVING_HISTORY", "Allows viewing historical receiving records.");
        descriptions.put("STORE_TRANSFER", "Allows sending and receiving inventory store transfers.");
        descriptions.put("VIEW_INVENTORY", "Allows viewing the inventory list and stock levels.");
        descriptions.put("VIEW_ITEM_DETAILS", "Allows opening full item detail records.");
        descriptions.put("VIEW_COST_PRICE", "Allows viewing internal item cost prices.");
        descriptions.put("VIEW_VENDOR", "Allows viewing vendor assignments on items.");
        descriptions.put("VIEW_CREATED_BY", "Allows viewing item creation and ownership metadata.");
        descriptions.put("MANUAL_ADJUSTMENT", "Allows manual quantity adjustments outside normal receiving/transfer flows.");
        descriptions.put("DEPARTMENT_MANAGEMENT", "Allows creating and managing item departments.");
        descriptions.put("VENDOR_MANAGEMENT", "Allows creating and managing vendors.");
        descriptions.put("CUSTOMER_ACCOUNTS", "Allows using customer account balances, credits, and history.");
        descriptions.put("CREATE_CUSTOM_ORDER", "Allows creating new custom orders.");
        descriptions.put("MANAGE_CUSTOM_ORDERS", "Allows full management access across all custom orders.");
        descriptions.put("VIEW_ASSIGNED_CUSTOM_ORDERS", "Allows viewing custom orders assigned to the logged-in user.");
        descriptions.put("SALES_QUOTES_ORDERS", "Allows opening the sales quotes and sales orders workflow.");
        descriptions.put("CREATE_SALES_QUOTE", "Allows creating and issuing customer sales quotes.");
        descriptions.put("MANAGE_SALES_ORDERS", "Allows accepting quotes, taking order payments, and managing sales orders.");
        descriptions.put("POST_SALES_ORDER_DELIVERY", "Allows posting partial or full sales order deliveries.");
        descriptions.put("ORDERS_MANAGER_DASHBOARD", "Allows access to manager-level custom order dashboard tools.");
        descriptions.put("ORDERS_END_OF_DAY", "Allows access to custom-order end-of-day totals and reconciliation.");
        descriptions.put("CUSTOM_ORDER_REFUNDS", "Allows issuing refunds on custom orders.");
        descriptions.put("CUSTOM_ORDER_LINE_RETURNS", "Allows returning individual custom-order lines.");
        descriptions.put("CUSTOM_ORDER_LINE_DELIVERY", "Allows marking custom-order lines as delivered.");
        descriptions.put("CUSTOM_ORDER_LINE_DISCOUNT", "Allows discounting custom-order lines without override.");
        descriptions.put("CUSTOM_ORDER_DEPOSIT_OVERRIDE", "Allows overriding required custom-order deposit amounts.");
        descriptions.put("CUSTOM_ORDER_DEPOSIT_SETTINGS", "Allows editing custom-order minimum deposit settings.");
        descriptions.put("CUSTOM_ORDER_REFUND_APPROVAL", "Allows approving high-value custom-order refunds.");
        descriptions.put("CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS", "Allows editing custom-order refund approval limits.");
        descriptions.put("CUSTOM_ORDER_PRODUCTION_STEPS", "Allows updating production workflow states for custom-order lines.");
        descriptions.put("CUSTOM_ORDER_CANCEL", "Allows canceling custom orders.");
        descriptions.put("SET_CREDIT_LIMIT", "Allows setting customer credit limits.");
        descriptions.put("EDIT_ACCOUNT_NUMBER", "Allows changing customer account numbers.");
        descriptions.put("EMPLOYEE_MANAGEMENT", "Allows creating and managing employee records.");
        descriptions.put("TIME_CLOCK", "Allows clock-in/clock-out actions.");
        descriptions.put("TIME_CLOCK_MANAGEMENT", "Allows viewing and correcting staff time clock records.");
        descriptions.put("TIME_CLOCK_OVERRIDE", "Allows approving additional employee time clock sessions after a completed session on the same day.");
        descriptions.put("PAYROLL_DASHBOARD", "Allows viewing payroll and labor summary dashboards.");
        descriptions.put("ROLE_MANAGEMENT", "Allows editing role definitions and assigning permissions.");
        descriptions.put("LOCATION_MANAGEMENT", "Allows creating and editing store locations.");
        descriptions.put("CASH_DRAWER_MANAGEMENT", "Allows configuring cash drawer workflows and sessions.");
        descriptions.put("COMPANY_PREFERENCES", "Allows editing company-wide operational preferences.");
        descriptions.put("CHANGE_STORE", "Allows switching the active store context.");
        descriptions.put("VIEW_REPORTS", "Allows opening reporting screens and exports.");
        descriptions.put("LOCAL_DEVICE_SETTINGS", "Allows changing workstation-specific app/receipt settings.");
        descriptions.put("HARDWARE_SETUP", "Allows configuring scanner, printer, and hardware integration settings.");
        return descriptions;
    }

    private static Map<String, String> createDefaultPermissionGroups() {
        Map<String, String> groups = new LinkedHashMap<>();

        groups.put("MAKE_SALE", "Sales");
        groups.put("APPLY_SALE_DISCOUNT", "Sales");
        groups.put("SALE_DISCOUNT_OVERRIDE", "Sales");
        groups.put("RETURN_OVERRIDE", "Sales");
        groups.put("SALE_DISCOUNT_LIMIT_SETTINGS", "Sales");
        groups.put("SALE_RETURN_APPROVAL_SETTINGS", "Sales");
        groups.put("CHANGE_SALE_ITEM_PRICE", "Sales");
        groups.put("PROCESS_RETURNS", "Sales");
        groups.put("END_OF_DAY", "Sales");
        groups.put("BALANCE_DRAWER", "Sales");
        groups.put("BALANCE_SHEET", "Sales");
        groups.put("VIEW_SALES", "Sales");
        groups.put("CUSTOMER_ACCOUNTS", "Sales");
        groups.put("SET_CREDIT_LIMIT", "Sales");
        groups.put("EDIT_ACCOUNT_NUMBER", "Sales");
        groups.put("CREATE_CUSTOM_ORDER", "Orders");
        groups.put("MANAGE_CUSTOM_ORDERS", "Orders");
        groups.put("VIEW_ASSIGNED_CUSTOM_ORDERS", "Orders");
        groups.put("SALES_QUOTES_ORDERS", "Orders");
        groups.put("CREATE_SALES_QUOTE", "Orders");
        groups.put("MANAGE_SALES_ORDERS", "Orders");
        groups.put("POST_SALES_ORDER_DELIVERY", "Orders");
        groups.put("ORDERS_MANAGER_DASHBOARD", "Orders");
        groups.put("ORDERS_END_OF_DAY", "Orders");
        groups.put("CUSTOM_ORDER_REFUNDS", "Orders");
        groups.put("CUSTOM_ORDER_LINE_RETURNS", "Orders");
        groups.put("CUSTOM_ORDER_LINE_DELIVERY", "Orders");
        groups.put("CUSTOM_ORDER_LINE_DISCOUNT", "Orders");
        groups.put("CUSTOM_ORDER_DEPOSIT_OVERRIDE", "Orders");
        groups.put("CUSTOM_ORDER_DEPOSIT_SETTINGS", "Orders");
        groups.put("CUSTOM_ORDER_REFUND_APPROVAL", "Orders");
        groups.put("CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS", "Orders");
        groups.put("CUSTOM_ORDER_PRODUCTION_STEPS", "Orders");
        groups.put("CUSTOM_ORDER_CANCEL", "Orders");

        groups.put("NEW_ITEM", "Inventory");
        groups.put("EDIT_ITEM", "Inventory");
        groups.put("RECEIVING_INVENTORY", "Inventory");
        groups.put("RECEIVING_STOCK_OVERRIDE", "Inventory");
        groups.put("VIEW_RECEIVING_HISTORY", "Inventory");
        groups.put("STORE_TRANSFER", "Inventory");
        groups.put("VIEW_INVENTORY", "Inventory");
        groups.put("VIEW_ITEM_DETAILS", "Inventory");
        groups.put("VIEW_COST_PRICE", "Inventory");
        groups.put("VIEW_VENDOR", "Inventory");
        groups.put("VIEW_CREATED_BY", "Inventory");
        groups.put("MANUAL_ADJUSTMENT", "Inventory");
        groups.put("DEPARTMENT_MANAGEMENT", "Inventory");
        groups.put("VENDOR_MANAGEMENT", "Inventory");

        groups.put("EMPLOYEE_MANAGEMENT", "People");
        groups.put("TIME_CLOCK", "People");
        groups.put("TIME_CLOCK_MANAGEMENT", "People");
        groups.put("TIME_CLOCK_OVERRIDE", "People");
        groups.put("PAYROLL_DASHBOARD", "People");

        groups.put("ROLE_MANAGEMENT", "Administration");
        groups.put("LOCATION_MANAGEMENT", "Administration");
        groups.put("CASH_DRAWER_MANAGEMENT", "Administration");
        groups.put("COMPANY_PREFERENCES", "Administration");
        groups.put("CHANGE_STORE", "Administration");
        groups.put("VIEW_REPORTS", "Administration");
        groups.put("LOCAL_DEVICE_SETTINGS", "Administration");
        groups.put("HARDWARE_SETUP", "Administration");
        return groups;
    }
}
