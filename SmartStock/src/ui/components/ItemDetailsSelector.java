package ui.components;

import services.LanApiClient;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ItemDetailsSelector extends JPanel {
    private final SearchableComboBox itemTypeBox = new SearchableComboBox();
    private final SearchableComboBox brandBox = new SearchableComboBox();
    private final SearchableComboBox shelfBox = new SearchableComboBox();
    private final SearchableComboBox storageShelfBox = new SearchableComboBox();
    private final int locationId;

    public ItemDetailsSelector(DepartmentSelector departmentSelector, int locationId) {
        this.locationId = locationId;
        setLayout(new GridLayout(4, 1, 0, 5));
        add(itemTypeBox);
        add(brandBox);
        add(shelfBox);
        add(storageShelfBox);
        loadBrands();
        loadShelves();
        departmentSelector.addSelectionListener(e -> loadItemTypes(departmentSelector.getSelectedDepartmentId()));
        loadItemTypes(departmentSelector.getSelectedDepartmentId());
    }

    public JComboBox<String> itemTypeComponent() { return itemTypeBox; }
    public JComboBox<String> brandComponent() { return brandBox; }
    public JComboBox<String> shelfComponent() { return shelfBox; }
    public JComboBox<String> storageShelfComponent() { return storageShelfBox; }
    public String itemTypeName() { return selectedText(itemTypeBox); }
    public String brandName() { return selectedText(brandBox); }
    public String shelfName() { return selectedText(shelfBox); }
    public String storageShelfName() { return selectedText(storageShelfBox); }

    public void setValues(Integer categoryId, String itemType, String brand, String shelf, String storageShelf) {
        loadItemTypes(categoryId);
        itemTypeBox.setSelectedItem(value(itemType));
        brandBox.setSelectedItem(value(brand));
        shelfBox.setSelectedItem(value(shelf));
        storageShelfBox.setSelectedItem(value(storageShelf));
    }

    public void clearSelection() {
        itemTypeBox.setSelectedItem("");
        brandBox.setSelectedItem("");
        shelfBox.setSelectedItem("");
        storageShelfBox.setSelectedItem("");
    }

    public void setSelectorEnabled(boolean enabled) {
        itemTypeBox.setEnabled(enabled);
        brandBox.setEnabled(enabled);
        shelfBox.setEnabled(enabled);
        storageShelfBox.setEnabled(enabled);
    }

    private void loadItemTypes(Integer categoryId) {
        String previous = selectedText(itemTypeBox);
        if (categoryId == null) {
            itemTypeBox.setOptions(List.of());
            return;
        }
        load(itemTypeBox, categoryId, "ITEM_TYPES");
        selectExistingOrClear(itemTypeBox, previous);
    }

    private void loadBrands() {
        load(brandBox, null, "BRANDS");
    }

    private void loadShelves() {
        load(shelfBox, null, "SHELVES");
        load(storageShelfBox, null, "SHELVES");
    }

    private void load(SearchableComboBox box, Integer categoryId, String kind) {
        try {
            LanApiClient.InventoryLookups lookups = LanApiClient.loadInventoryLookups(categoryId);
            List<String> options = switch (kind) {
                case "ITEM_TYPES" -> lookups.itemTypes();
                case "BRANDS" -> lookups.brands();
                default -> lookups.shelves();
            };
            box.setOptions(options);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load item details: " + ex.getMessage(), "LAN Service", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void selectExistingOrClear(JComboBox<String> box, String value) {
        for (int i = 0; i < box.getItemCount(); i++) {
            if (box.getItemAt(i).equalsIgnoreCase(value)) {
                box.setSelectedIndex(i);
                return;
            }
        }
        box.setSelectedItem("");
    }

    private static String selectedText(JComboBox<String> box) {
        Object selected = box.isEditable() ? box.getEditor().getItem() : box.getSelectedItem();
        return selected == null ? "" : selected.toString().trim().replaceAll("\\s+", " ");
    }

    private static String value(String value) { return value == null ? "" : value; }
}
