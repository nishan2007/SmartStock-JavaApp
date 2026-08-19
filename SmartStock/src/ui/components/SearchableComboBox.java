package ui.components;

import javax.swing.*;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import javax.accessibility.Accessible;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public class SearchableComboBox extends JComboBox<String> {
    private final List<String> allOptions = new ArrayList<>();
    private boolean updating;
    private int navigationIndex = -1;
    private JTextComponent installedEditor;
    private final KeyAdapter searchKeyListener = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                navigateOptions(1);
                event.consume();
            } else if (event.getKeyCode() == KeyEvent.VK_UP) {
                navigateOptions(-1);
                event.consume();
            } else if (event.getKeyCode() == KeyEvent.VK_ENTER && navigationIndex > 0 && navigationIndex < getItemCount()) {
                setSelectedIndex(navigationIndex);
                setPopupVisible(false);
                navigationIndex = -1;
                event.consume();
            } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE && isPopupVisible()) {
                setPopupVisible(false);
                navigationIndex = -1;
                event.consume();
            }
        }

        @Override
        public void keyReleased(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_UP
                    || event.getKeyCode() == KeyEvent.VK_DOWN
                    || event.getKeyCode() == KeyEvent.VK_ENTER
                    || event.getKeyCode() == KeyEvent.VK_ESCAPE
                    || event.getKeyCode() == KeyEvent.VK_TAB) {
                return;
            }
            JTextComponent editor = currentEditor();
            if (editor != null) SwingUtilities.invokeLater(() -> filterOptions(editor.getText()));
        }
    };

    public SearchableComboBox() {
        setEditable(true);
        addItem("");
        installSearchListener();
    }

    @Override
    public void setUI(ComboBoxUI ui) {
        super.setUI(ui);
        if (searchKeyListener != null) installSearchListener();
    }

    @Override
    public void setEditor(ComboBoxEditor editor) {
        super.setEditor(editor);
        if (searchKeyListener != null) installSearchListener();
    }

    public void setOptions(List<String> options) {
        String currentText = editorText();
        allOptions.clear();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String option : options) {
            if (option != null && !option.isBlank()) unique.add(option);
        }
        allOptions.addAll(unique);
        replaceVisibleOptions(allOptions, currentText);
    }

    private void filterOptions(String typedText) {
        if (updating) return;
        navigationIndex = -1;
        String query = normalizeForSearch(typedText);
        List<String> startsWith = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String option : allOptions) {
            String normalized = normalizeForSearch(option);
            if (query.isEmpty() || normalized.startsWith(query)) {
                startsWith.add(option);
            } else if (normalized.contains(query)) {
                contains.add(option);
            }
        }
        startsWith.addAll(contains);
        replaceVisibleOptions(startsWith, typedText);
        JTextComponent editor = currentEditor();
        if (editor == null) return;
        if (editor.hasFocus() && !typedText.isBlank() && !startsWith.isEmpty()) {
            setPopupVisible(true);
        } else if (startsWith.isEmpty()) {
            setPopupVisible(false);
        }
    }

    private void replaceVisibleOptions(List<String> options, String editorText) {
        updating = true;
        try {
            DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
            model.addElement("");
            for (String option : options) model.addElement(option);
            setModel(model);
            String preservedText = editorText == null ? "" : editorText;
            getEditor().setItem(preservedText);
            collapseEditorSelection(preservedText);
        } finally {
            updating = false;
        }
    }

    private void collapseEditorSelection(String expectedText) {
        JTextComponent editor = currentEditor();
        if (editor == null) return;
        moveCaretToEnd(editor, expectedText);
        SwingUtilities.invokeLater(() -> {
            JTextComponent current = currentEditor();
            if (current != null) moveCaretToEnd(current, expectedText);
        });
    }

    private static void moveCaretToEnd(JTextComponent editor, String expectedText) {
        if (!editor.getText().equals(expectedText)) return;
        int end = editor.getDocument().getLength();
        editor.setCaretPosition(end);
        editor.moveCaretPosition(end);
    }

    private void navigateOptions(int direction) {
        if (getItemCount() <= 1) return;
        if (!isPopupVisible() && isShowing()) setPopupVisible(true);

        if (navigationIndex <= 0 || navigationIndex >= getItemCount()) {
            navigationIndex = direction > 0 ? 1 : getItemCount() - 1;
        } else {
            navigationIndex += direction;
            if (navigationIndex < 1) navigationIndex = getItemCount() - 1;
            if (navigationIndex >= getItemCount()) navigationIndex = 1;
        }

        JList<?> popupList = popupList();
        if (popupList != null) {
            popupList.setSelectedIndex(navigationIndex);
            popupList.ensureIndexIsVisible(navigationIndex);
        }
    }

    private JList<?> popupList() {
        ComboBoxUI comboBoxUI = getUI();
        if (comboBoxUI == null) return null;
        Accessible child = comboBoxUI.getAccessibleChild(this, 0);
        return child instanceof ComboPopup popup ? popup.getList() : null;
    }

    private String editorText() {
        Object value = getEditor().getItem();
        return value == null ? "" : value.toString();
    }

    private void installSearchListener() {
        JTextComponent editor = currentEditor();
        if (editor == null) return;
        if (installedEditor != null && installedEditor != editor) {
            installedEditor.removeKeyListener(searchKeyListener);
        }
        editor.removeKeyListener(searchKeyListener);
        editor.addKeyListener(searchKeyListener);
        installedEditor = editor;
    }

    private JTextComponent currentEditor() {
        if (getEditor() == null || !(getEditor().getEditorComponent() instanceof JTextComponent editor)) return null;
        return editor;
    }

    private static String normalizeForSearch(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
