package ui.components;

import data.DB;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VendorSelector extends JPanel {
    private final JComboBox<VendorOption> vendorBox = new JComboBox<>();
    private boolean loading;

    public VendorSelector() {
        setLayout(new BorderLayout(6, 0));
        vendorBox.setEditable(true);
        add(vendorBox, BorderLayout.CENTER);
        loadVendors();
    }

    public void loadVendors() {
        Object selected = vendorBox.getSelectedItem();
        String selectedText = selected == null ? "" : selected.toString();
        loading = true;
        vendorBox.removeAllItems();
        vendorBox.addItem(new VendorOption(null, ""));

        String sql = "SELECT vendor_id, name FROM vendors WHERE COALESCE(is_active, TRUE) = TRUE ORDER BY name";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                vendorBox.addItem(new VendorOption(rs.getInt("vendor_id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load vendors: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            loading = false;
        }

        if (!selectedText.isBlank()) {
            setSelectedVendorByName(selectedText);
        }
    }

    public Integer getSelectedVendorId() {
        Object selected = vendorBox.getSelectedItem();
        if (selected instanceof VendorOption option) {
            return option.id();
        }

        String text = getSelectedVendorName();
        if (text.isBlank()) {
            return null;
        }

        for (int i = 0; i < vendorBox.getItemCount(); i++) {
            VendorOption option = vendorBox.getItemAt(i);
            if (option.name().equalsIgnoreCase(text)) {
                vendorBox.setSelectedItem(option);
                return option.id();
            }
        }

        JOptionPane.showMessageDialog(this, "Select an existing vendor from the list.");
        return null;
    }

    public String getSelectedVendorName() {
        Object selected = vendorBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    public void setSelectedVendor(Integer vendorId, String vendorName) {
        if (vendorId == null && (vendorName == null || vendorName.isBlank())) {
            clearSelection();
            return;
        }

        for (int i = 0; i < vendorBox.getItemCount(); i++) {
            VendorOption option = vendorBox.getItemAt(i);
            if ((vendorId != null && vendorId.equals(option.id()))
                    || (vendorName != null && option.name().equalsIgnoreCase(vendorName))) {
                vendorBox.setSelectedItem(option);
                return;
            }
        }

        vendorBox.setSelectedItem(new VendorOption(vendorId, vendorName == null ? "" : vendorName));
    }

    public void setSelectedVendorByName(String vendorName) {
        setSelectedVendor(null, vendorName);
    }

    public void clearSelection() {
        if (vendorBox.getItemCount() > 0) {
            vendorBox.setSelectedIndex(0);
        } else if (!loading) {
            vendorBox.setSelectedItem("");
        }
    }

    public void setSelectorEnabled(boolean enabled) {
        vendorBox.setEnabled(enabled);
    }

    private record VendorOption(Integer id, String name) {
        private VendorOption {
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
