package ui.components;

import services.LanApiClient;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ItemDetailsSelector extends JPanel {
    private final SearchableComboBox itemTypeBox = new SearchableComboBox();
    private final SearchableComboBox brandBox = new SearchableComboBox();
    private final SearchableComboBox shelfBox = new SearchableComboBox();
    private final SearchableComboBox storageShelfBox = new SearchableComboBox();
    private final int locationId;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public ItemDetailsSelector(DepartmentSelector departmentSelector, int locationId) {
        this.locationId = locationId;
        setLayout(new BorderLayout());
        JPanel fields = new JPanel(new GridLayout(4, 1, 0, 5));
        fields.add(itemTypeBox);
        fields.add(brandBox);
        fields.add(shelfBox);
        fields.add(storageShelfBox);
        add(fields, BorderLayout.CENTER);
        add(loadingState, BorderLayout.SOUTH);
        loadCommonLookups();
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
        if (categoryId == null) {
            itemTypeBox.setOptions(List.of());
            return;
        }
        load(itemTypeBox, categoryId, "ITEM_TYPES");
    }

    private void loadCommonLookups() {
        CachedUiLoader.loadAfterDisplay(brandBox,"reference:inventory-lookups:all",LanApiClient.InventoryLookups.class,
                SessionDataCache.REFERENCE_TTL,loadingState,()->LanApiClient.loadInventoryLookups(null),lookups->{
                    String brand=selectedText(brandBox),shelf=selectedText(shelfBox),storage=selectedText(storageShelfBox);
                    brandBox.setOptions(lookups.brands());
                    shelfBox.setOptions(lookups.shelves());
                    storageShelfBox.setOptions(lookups.shelves());
                    selectExistingOrClear(brandBox,brand);selectExistingOrClear(shelfBox,shelf);selectExistingOrClear(storageShelfBox,storage);
                });
    }

    private void load(SearchableComboBox box, Integer categoryId, String kind) {
        String key="reference:inventory-lookups:"+(categoryId==null?"all":"category:"+categoryId);
        CachedUiLoader.loadAfterDisplay(box,key,LanApiClient.InventoryLookups.class,SessionDataCache.REFERENCE_TTL,
                loadingState,()->LanApiClient.loadInventoryLookups(categoryId),lookups->{
            String desired=selectedText(box);
            List<String> options = switch (kind) {
                case "ITEM_TYPES" -> lookups.itemTypes();
                case "BRANDS" -> lookups.brands();
                default -> lookups.shelves();
            };
            box.setOptions(options);
            selectExistingOrClear(box,desired);
        });
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
