package ui.components;

import services.LanApiClient;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VendorSelector extends JPanel {
    private final SearchableComboBox vendorBox = new SearchableComboBox();
    private final Map<String, Integer> idsByName = new LinkedHashMap<>();
    private final Map<Integer, String> namesById = new LinkedHashMap<>();
    private boolean loading;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public VendorSelector() {
        setLayout(new BorderLayout(6, 0));
        add(vendorBox, BorderLayout.CENTER);
        add(loadingState, BorderLayout.SOUTH);
        loadVendors();
    }

    public void loadVendors() {
        String selectedText = getSelectedVendorName();
        CachedUiLoader.loadAfterDisplay(this, "reference:inventory-lookups:all",
                LanApiClient.InventoryLookups.class, SessionDataCache.REFERENCE_TTL, loadingState,
                () -> LanApiClient.loadInventoryLookups(null),
                lookups -> applyVendors(lookups, selectedText));
    }

    private void applyVendors(LanApiClient.InventoryLookups lookups, String selectedText) {
        String desiredText = getSelectedVendorName();
        if (desiredText.isBlank()) desiredText = selectedText;
        loading = true;
        idsByName.clear();
        namesById.clear();
        List<String> options = new ArrayList<>();

        for (LanApiClient.NamedId vendor : lookups.vendors()) {
                int id = vendor.id();
                String name = vendor.name();
                options.add(name);
                idsByName.put(normalize(name), id);
                namesById.put(id, name);
        }
        vendorBox.setOptions(options);
        loading = false;

        if (!desiredText.isBlank()) {
            setSelectedVendorByName(desiredText);
        }
    }

    public Integer getSelectedVendorId() {
        String text = getSelectedVendorName();
        if (text.isBlank()) return null;
        Integer id = idsByName.get(normalize(text));
        if (id != null) {
            vendorBox.setSelectedItem(namesById.get(id));
            return id;
        }

        JOptionPane.showMessageDialog(this, "Select an existing vendor from the list.");
        return null;
    }

    public String getSelectedVendorName() {
        Object selected = vendorBox.isEditable() ? vendorBox.getEditor().getItem() : vendorBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    public void setSelectedVendor(Integer vendorId, String vendorName) {
        if (vendorId == null && (vendorName == null || vendorName.isBlank())) {
            clearSelection();
            return;
        }

        String matchedName = vendorId == null ? null : namesById.get(vendorId);
        if (matchedName == null && vendorName != null) {
            Integer matchedId = idsByName.get(normalize(vendorName));
            if (matchedId != null) matchedName = namesById.get(matchedId);
        }
        vendorBox.setSelectedItem(matchedName == null ? (vendorName == null ? "" : vendorName) : matchedName);
    }

    public void setSelectedVendorByName(String vendorName) {
        setSelectedVendor(null, vendorName);
    }

    public void clearSelection() {
        vendorBox.setSelectedItem("");
    }

    public void setSelectorEnabled(boolean enabled) {
        vendorBox.setEnabled(enabled);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
