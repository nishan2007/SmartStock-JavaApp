package ui.components;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadingStatePanelTest {
    @Test
    void rendersLoadingRefreshingFailureRetryAndReadyStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LoadingStatePanel panel = new LoadingStatePanel();
            panel.loading(false, Instant.now());
            assertTrue(label(panel).getText().startsWith("Loading"));
            assertTrue(progress(panel).isVisible());

            panel.loading(true, Instant.now());
            assertTrue(label(panel).getText().startsWith("Refreshing"));

            AtomicBoolean retried = new AtomicBoolean();
            panel.failed("offline", true, () -> retried.set(true));
            assertTrue(label(panel).getText().contains("existing data"));
            JButton retry = button(panel);
            assertTrue(retry.isVisible());
            retry.doClick();
            assertTrue(retried.get());

            panel.ready(Instant.now());
            assertFalse(progress(panel).isVisible());
            assertFalse(retry.isVisible());
            assertTrue(label(panel).getText().startsWith("Updated"));
        });
    }

    private static JLabel label(Container root) { return find(root,JLabel.class); }
    private static JButton button(Container root) { return find(root,JButton.class); }
    private static JProgressBar progress(Container root) { return find(root,JProgressBar.class); }
    private static <T extends Component>T find(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container nested){T found=findOrNull(nested,type);if(found!=null)return found;}}throw new AssertionError("Missing "+type.getSimpleName());}
    private static <T extends Component>T findOrNull(Container root,Class<T> type){for(Component c:root.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container nested){T found=findOrNull(nested,type);if(found!=null)return found;}}return null;}
}
