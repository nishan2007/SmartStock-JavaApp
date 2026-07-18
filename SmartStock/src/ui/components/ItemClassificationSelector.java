package ui.components;

import services.LanApiClient;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ItemClassificationSelector extends JPanel {
    private final SearchableComboBox itemTypeBox = new SearchableComboBox();
    private final SearchableComboBox brandBox = new SearchableComboBox();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public ItemClassificationSelector(DepartmentSelector departmentSelector) {
        setLayout(new BorderLayout());
        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 5));
        fields.add(itemTypeBox);
        fields.add(brandBox);
        add(fields, BorderLayout.CENTER);
        add(loadingState, BorderLayout.SOUTH);
        loadBrands();
        departmentSelector.addSelectionListener(e -> loadItemTypes(departmentSelector.getSelectedDepartmentId()));
        loadItemTypes(departmentSelector.getSelectedDepartmentId());
    }

    public JComboBox<String> itemTypeComponent() {
        return itemTypeBox;
    }

    public JComboBox<String> brandComponent() {
        return brandBox;
    }

    public String itemTypeName() {
        return selectedText(itemTypeBox);
    }

    public String brandName() {
        return selectedText(brandBox);
    }

    public void setValues(Integer categoryId, String itemType, String brand) {
        loadItemTypes(categoryId);
        itemTypeBox.setSelectedItem(value(itemType));
        brandBox.setSelectedItem(value(brand));
    }

    public void clearSelection() {
        itemTypeBox.setSelectedItem("");
        brandBox.setSelectedItem("");
    }

    public void setSelectorEnabled(boolean enabled) {
        itemTypeBox.setEnabled(enabled);
        brandBox.setEnabled(enabled);
    }

    private void loadItemTypes(Integer categoryId) {
        if (categoryId == null) {
            itemTypeBox.setOptions(List.of());
            return;
        }
        load(itemTypeBox, categoryId, true);
    }

    private void loadBrands() {
        load(brandBox, null, false);
    }

    private void load(SearchableComboBox box, Integer categoryId, boolean itemTypes) {
        String key="reference:inventory-lookups:"+(categoryId==null?"all":"category:"+categoryId);
        CachedUiLoader.loadAfterDisplay(this,key,LanApiClient.InventoryLookups.class,SessionDataCache.REFERENCE_TTL,
                loadingState,()->LanApiClient.loadInventoryLookups(categoryId),lookups->{
            String desired=selectedText(box);
            box.setOptions(itemTypes ? lookups.itemTypes() : lookups.brands());
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

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
