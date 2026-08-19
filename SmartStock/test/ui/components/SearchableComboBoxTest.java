package ui.components;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchableComboBoxTest {
    @Test
    void filteringDoesNotSelectAndReplaceTheTypedQuery() throws Exception {
        AtomicReference<SearchableComboBox> comboRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            SearchableComboBox combo = new SearchableComboBox();
            combo.setOptions(List.of("Deckers", "Members Mark", "Pineapple"));
            JTextComponent editor = (JTextComponent) combo.getEditor().getEditorComponent();
            editor.setText("b");
            KeyEvent released = new KeyEvent(editor, KeyEvent.KEY_RELEASED,
                    System.currentTimeMillis(), 0, KeyEvent.VK_B, 'b');
            for (KeyListener listener : editor.getKeyListeners()) listener.keyReleased(released);
            comboRef.set(combo);
        });

        SwingUtilities.invokeAndWait(() -> {
            JTextComponent editor = (JTextComponent) comboRef.get().getEditor().getEditorComponent();
            assertEquals(1, editor.getSelectionStart());
            assertEquals(1, editor.getSelectionEnd());
            editor.replaceSelection("d");
            assertEquals("bd", editor.getText());
        });
    }
}
