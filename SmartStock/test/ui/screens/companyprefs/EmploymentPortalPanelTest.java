package ui.screens.companyprefs;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class EmploymentPortalPanelTest {
    @Test void preferencesCanConstructPortalBeforeAttachingItToAWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            EmploymentPortalPanel panel = assertDoesNotThrow(EmploymentPortalPanel::new);
            JPanel preferencesCard = new JPanel();
            preferencesCard.add(panel);
            assertSame(preferencesCard, panel.getParent());
            assertNull(SwingUtilities.getWindowAncestor(panel));
        });
    }
}
