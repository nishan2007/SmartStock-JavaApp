package ui.components;

import services.LanApiClient;

import javax.swing.*;
import java.awt.*;

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

        try {
            for (LanApiClient.CustomerTypeRecord type : LanApiClient.loadCustomerTypes("", true)) {
                customerTypeBox.addItem(new CustomerTypeOption(type.customerTypeId(), type.name()));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customer types: " + ex.getMessage(),
                    "SmartStock Server Error", JOptionPane.ERROR_MESSAGE);
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
