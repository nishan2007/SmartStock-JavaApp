package ui.components;

import data.DB;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerTypeSelector extends JPanel {
    private final JComboBox<CustomerTypeOption> customerTypeBox = new JComboBox<>();
    private boolean loading;

    public CustomerTypeSelector() {
        setLayout(new BorderLayout(6, 0));
        customerTypeBox.setEditable(true);
        add(customerTypeBox, BorderLayout.CENTER);
        loadCustomerTypes();
    }

    public void loadCustomerTypes() {
        Object selected = customerTypeBox.getSelectedItem();
        String selectedText = selected == null ? "" : selected.toString();
        loading = true;
        customerTypeBox.removeAllItems();
        customerTypeBox.addItem(new CustomerTypeOption(null, ""));

        String sql = "SELECT customer_type_id, name FROM customer_types WHERE COALESCE(is_active, TRUE) = TRUE ORDER BY name";
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                customerTypeBox.addItem(new CustomerTypeOption(rs.getInt("customer_type_id"), rs.getString("name")));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customer types: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        } finally {
            loading = false;
        }

        if (!selectedText.isBlank()) {
            setSelectedCustomerTypeByName(selectedText);
        }
    }

    public Integer getSelectedCustomerTypeId() {
        Object selected = customerTypeBox.getSelectedItem();
        if (selected instanceof CustomerTypeOption option) {
            return option.id();
        }

        String text = getSelectedCustomerTypeName();
        if (text.isBlank()) {
            return null;
        }

        for (int i = 0; i < customerTypeBox.getItemCount(); i++) {
            CustomerTypeOption option = customerTypeBox.getItemAt(i);
            if (option.name().equalsIgnoreCase(text)) {
                customerTypeBox.setSelectedItem(option);
                return option.id();
            }
        }

        JOptionPane.showMessageDialog(this, "Select an existing customer type from the list.");
        return null;
    }

    public String getSelectedCustomerTypeName() {
        Object selected = customerTypeBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    public void setSelectedCustomerType(Integer customerTypeId, String customerTypeName) {
        if (customerTypeId == null && (customerTypeName == null || customerTypeName.isBlank())) {
            clearSelection();
            return;
        }

        for (int i = 0; i < customerTypeBox.getItemCount(); i++) {
            CustomerTypeOption option = customerTypeBox.getItemAt(i);
            if ((customerTypeId != null && customerTypeId.equals(option.id()))
                    || (customerTypeName != null && option.name().equalsIgnoreCase(customerTypeName))) {
                customerTypeBox.setSelectedItem(option);
                return;
            }
        }

        customerTypeBox.setSelectedItem(new CustomerTypeOption(customerTypeId, customerTypeName == null ? "" : customerTypeName));
    }

    public void setSelectedCustomerTypeByName(String customerTypeName) {
        setSelectedCustomerType(null, customerTypeName);
    }

    public void clearSelection() {
        if (customerTypeBox.getItemCount() > 0) {
            customerTypeBox.setSelectedIndex(0);
        } else if (!loading) {
            customerTypeBox.setSelectedItem("");
        }
    }

    public void setSelectorEnabled(boolean enabled) {
        customerTypeBox.setEnabled(enabled);
    }

    private record CustomerTypeOption(Integer id, String name) {
        private CustomerTypeOption {
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
