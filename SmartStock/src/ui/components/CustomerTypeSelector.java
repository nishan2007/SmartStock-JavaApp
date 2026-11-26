package ui.components;

import services.LanApiClient;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class CustomerTypeSelector extends JPanel {
    private final JComboBox<CustomerTypeOption> customerTypeBox = new JComboBox<>();
    private boolean loading;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public CustomerTypeSelector() {
        this(true);
    }

    public CustomerTypeSelector(boolean loadOptions) {
        setLayout(new BorderLayout(6, 0));
        customerTypeBox.setEditable(false);
        customerTypeBox.setMaximumRowCount(12);
        customerTypeBox.getAccessibleContext().setAccessibleName("Customer type");
        customerTypeBox.setToolTipText("Choose an existing customer type. Manage options using Customer Types.");
        add(customerTypeBox, BorderLayout.CENTER);
        add(loadingState, BorderLayout.SOUTH);
        if (loadOptions) loadCustomerTypes();
        else {
            customerTypeBox.addItem(new CustomerTypeOption(null, ""));
            loadingState.setVisible(false);
        }
    }

    public void loadCustomerTypes() {
        Object selected = customerTypeBox.getSelectedItem();
        String selectedText = selected == null ? "" : selected.toString();
        CachedUiLoader.loadAfterDisplay(this, "reference:customer-types", CustomerTypesSnapshot.class,
                SessionDataCache.REFERENCE_TTL, loadingState,
                () -> new CustomerTypesSnapshot(LanApiClient.loadCustomerTypes("", true)),
                snapshot -> applyCustomerTypes(snapshot.types(), selectedText));
    }

    private void applyCustomerTypes(List<LanApiClient.CustomerTypeRecord> types, String selectedText) {
        String desiredText=getSelectedCustomerTypeName();
        if(desiredText.isBlank())desiredText=selectedText;
        loading = true;
        customerTypeBox.removeAllItems();
        customerTypeBox.addItem(new CustomerTypeOption(null, ""));
        for (LanApiClient.CustomerTypeRecord type : types) {
            customerTypeBox.addItem(new CustomerTypeOption(type.customerTypeId(), type.name()));
        }
        loading = false;

        if (!desiredText.isBlank()) {
            setSelectedCustomerTypeByName(desiredText);
        }
    }

    private record CustomerTypesSnapshot(List<LanApiClient.CustomerTypeRecord> types) { }

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

    /** Account classification follows the selected type; there is no second user choice. */
    public boolean isBusinessAccount() {
        return utils.CustomerAccountTypes.isBusiness(getSelectedCustomerTypeName());
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

        if(customerTypeId!=null){
            CustomerTypeOption existing=new CustomerTypeOption(customerTypeId,customerTypeName);
            customerTypeBox.addItem(existing);customerTypeBox.setSelectedItem(existing);
        }else clearSelection();
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
