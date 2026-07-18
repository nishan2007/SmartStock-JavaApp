package ui.helpers;

import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** Debounces text-driven server searches on Swing's event thread. */
public final class UiDebouncer {
    private UiDebouncer() { }

    public static void bind(JTextComponent field, int delayMillis, Runnable action) {
        Timer timer = new Timer(delayMillis, event -> action.run());
        timer.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { timer.restart(); }
            @Override public void removeUpdate(DocumentEvent event) { timer.restart(); }
            @Override public void changedUpdate(DocumentEvent event) { timer.restart(); }
        });
    }
}
