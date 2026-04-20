package ui.components;

import data.DB;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DepartmentSelector extends JPanel {
    private final JComboBox<DepartmentOption> departmentBox = new JComboBox<>();
    private boolean loading;

    public DepartmentSelector() {
        setLayout(new BorderLayout(6, 0));
        departmentBox.setEditable(true);
        add(departmentBox, BorderLayout.CENTER);
        loadDepartments();
    }

    public void loadDepartments() {
        Object selected = departmentBox.getSelectedItem();
        String selectedText = selected == null ? "" : selected.toString();
        loading = true;
        departmentBox.removeAllItems();
        departmentBox.addItem(new DepartmentOption(null, ""));

        String sql = "SELECT category_id, name FROM categories ORDER BY name";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                departmentBox.addItem(new DepartmentOption(rs.getInt("category_id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load departments: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            loading = false;
        }

        if (!selectedText.isBlank()) {
            setSelectedDepartmentByName(selectedText);
        }
    }

    public Integer getSelectedDepartmentId() {
        Object selected = departmentBox.getSelectedItem();
        if (selected instanceof DepartmentOption option) {
            return option.id();
        }

        String text = getSelectedDepartmentName();
        if (text.isBlank()) {
            return null;
        }

        for (int i = 0; i < departmentBox.getItemCount(); i++) {
            DepartmentOption option = departmentBox.getItemAt(i);
            if (option.name().equalsIgnoreCase(text)) {
                departmentBox.setSelectedItem(option);
                return option.id();
            }
        }

        JOptionPane.showMessageDialog(this, "Select an existing department from the list.");
        return null;
    }

    public String getSelectedDepartmentName() {
        Object selected = departmentBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    public void setSelectedDepartment(Integer departmentId, String departmentName) {
        if (departmentId == null && (departmentName == null || departmentName.isBlank())) {
            clearSelection();
            return;
        }

        for (int i = 0; i < departmentBox.getItemCount(); i++) {
            DepartmentOption option = departmentBox.getItemAt(i);
            if ((departmentId != null && departmentId.equals(option.id()))
                    || (departmentName != null && option.name().equalsIgnoreCase(departmentName))) {
                departmentBox.setSelectedItem(option);
                return;
            }
        }

        departmentBox.setSelectedItem(new DepartmentOption(departmentId, departmentName == null ? "" : departmentName));
    }

    public void setSelectedDepartmentByName(String departmentName) {
        setSelectedDepartment(null, departmentName);
    }

    public void clearSelection() {
        if (departmentBox.getItemCount() > 0) {
            departmentBox.setSelectedIndex(0);
        } else if (!loading) {
            departmentBox.setSelectedItem("");
        }
    }

    public void setSelectorEnabled(boolean enabled) {
        departmentBox.setEnabled(enabled);
    }

    private record DepartmentOption(Integer id, String name) {
        private DepartmentOption {
            if (name == null) {
                name = "";
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
