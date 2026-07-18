package ui.components;

import services.LanApiClient;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class DepartmentSelector extends JPanel {
    private final SearchableComboBox departmentBox = new SearchableComboBox();
    private final Map<String, Integer> idsByName = new LinkedHashMap<>();
    private final Map<Integer, String> namesById = new LinkedHashMap<>();
    private final List<ActionListener> selectionListeners = new ArrayList<>();
    private boolean loading;
    private Integer lastNotifiedDepartmentId;

    public DepartmentSelector() {
        setLayout(new BorderLayout(6, 0));
        add(departmentBox, BorderLayout.CENTER);
        departmentBox.addActionListener(this::handleSelectionChange);
        loadDepartments();
    }

    public void loadDepartments() {
        String selectedText = getSelectedDepartmentName();
        loading = true;
        idsByName.clear();
        namesById.clear();
        List<String> options = new ArrayList<>();

        try {
            LanApiClient.InventoryLookups lookups = LanApiClient.loadInventoryLookups(null);
            for (LanApiClient.NamedId department : lookups.departments()) {
                int id = department.id();
                String name = department.name();
                options.add(name);
                idsByName.put(normalize(name), id);
                namesById.put(id, name);
            }
            departmentBox.setOptions(options);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load departments: " + ex.getMessage(), "LAN Service", JOptionPane.ERROR_MESSAGE);
        } finally {
            loading = false;
        }

        if (!selectedText.isBlank()) {
            setSelectedDepartmentByName(selectedText);
        }
    }

    public Integer getSelectedDepartmentId() {
        String text = getSelectedDepartmentName();
        if (text.isBlank()) return null;
        Integer id = idsByName.get(normalize(text));
        if (id != null) {
            departmentBox.setSelectedItem(namesById.get(id));
            return id;
        }

        JOptionPane.showMessageDialog(this, "Select an existing department from the list.");
        return null;
    }

    public String getSelectedDepartmentName() {
        Object selected = departmentBox.isEditable() ? departmentBox.getEditor().getItem() : departmentBox.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }

    public void setSelectedDepartment(Integer departmentId, String departmentName) {
        if (departmentId == null && (departmentName == null || departmentName.isBlank())) {
            clearSelection();
            return;
        }

        String matchedName = departmentId == null ? null : namesById.get(departmentId);
        if (matchedName == null && departmentName != null) {
            Integer matchedId = idsByName.get(normalize(departmentName));
            if (matchedId != null) matchedName = namesById.get(matchedId);
        }
        departmentBox.setSelectedItem(matchedName == null ? (departmentName == null ? "" : departmentName) : matchedName);
    }

    public void setSelectedDepartmentByName(String departmentName) {
        setSelectedDepartment(null, departmentName);
    }

    public void clearSelection() {
        departmentBox.setSelectedItem("");
    }

    public void setSelectorEnabled(boolean enabled) {
        departmentBox.setEnabled(enabled);
    }

    public void addSelectionListener(ActionListener listener) {
        selectionListeners.add(listener);
    }

    private void handleSelectionChange(java.awt.event.ActionEvent event) {
        if (loading) return;
        String text = getSelectedDepartmentName();
        Integer resolvedId = text.isBlank() ? null : idsByName.get(normalize(text));
        if (!text.isBlank() && resolvedId == null) return;
        if (Objects.equals(lastNotifiedDepartmentId, resolvedId)) return;
        lastNotifiedDepartmentId = resolvedId;
        for (ActionListener listener : List.copyOf(selectionListeners)) {
            listener.actionPerformed(event);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
