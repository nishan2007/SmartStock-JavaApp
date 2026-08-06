package managers;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoLogoutManagerTest {
    @Test
    void staySignedInOverridesAutomaticLogout() {
        AutoLogoutManager.Policy policy = new AutoLogoutManager.Policy(true, true, 15);

        assertFalse(policy.effective());
        assertEquals(Duration.ofMinutes(15), policy.timeout());
    }

    @Test
    void enabledPolicyUsesConfiguredWholeMinutes() {
        AutoLogoutManager.Policy policy = new AutoLogoutManager.Policy(false, true, 37);

        assertTrue(policy.effective());
        assertEquals(Duration.ofMinutes(37), policy.timeout());
    }

    @Test
    void newlyEnabledOrShortenedPolicyStartsFreshDeadline() {
        AutoLogoutManager.Policy disabled = new AutoLogoutManager.Policy(false, false, 15);
        AutoLogoutManager.Policy enabled = new AutoLogoutManager.Policy(false, true, 15);
        AutoLogoutManager.Policy shortened = new AutoLogoutManager.Policy(false, true, 5);
        AutoLogoutManager.Policy extended = new AutoLogoutManager.Policy(false, true, 30);

        assertTrue(AutoLogoutManager.shouldResetDeadline(disabled, enabled, false));
        assertTrue(AutoLogoutManager.shouldResetDeadline(enabled, shortened, false));
        assertFalse(AutoLogoutManager.shouldResetDeadline(enabled, extended, false));
        assertTrue(AutoLogoutManager.shouldResetDeadline(enabled, extended, true));
    }

    @Test
    void warningOccupiesFinalMinuteExceptForOneMinuteTimeout() {
        assertEquals(60_000L, AutoLogoutManager.warningLeadMillis(15 * 60_000L));
        assertEquals(30_000L, AutoLogoutManager.warningLeadMillis(60_000L));
    }

    @Test
    void posKeyboardAndPointerInputCountAsActivity() {
        JPanel source = new JPanel();

        assertTrue(AutoLogoutManager.countsAsActivity(new KeyEvent(source, KeyEvent.KEY_PRESSED,
                1L, 0, KeyEvent.VK_ENTER, '\n')));
        assertTrue(AutoLogoutManager.countsAsActivity(new MouseEvent(source, MouseEvent.MOUSE_PRESSED,
                1L, 0, 2, 3, 1, false)));
        assertTrue(AutoLogoutManager.countsAsActivity(new MouseEvent(source, MouseEvent.MOUSE_MOVED,
                1L, 0, 2, 3, 0, false)));
        assertTrue(AutoLogoutManager.countsAsActivity(new MouseEvent(source, MouseEvent.MOUSE_DRAGGED,
                1L, 0, 2, 3, 0, false)));
        assertTrue(AutoLogoutManager.countsAsActivity(new MouseWheelEvent(source,
                MouseEvent.MOUSE_WHEEL, 1L, 0, 2, 3, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, 1)));
    }

    @Test
    void keyReleaseAndPassiveMouseEventsDoNotExtendTheDeadline() {
        JPanel source = new JPanel();

        assertFalse(AutoLogoutManager.countsAsActivity(new KeyEvent(source, KeyEvent.KEY_RELEASED,
                1L, 0, KeyEvent.VK_ENTER, '\n')));
        assertFalse(AutoLogoutManager.countsAsActivity(new MouseEvent(source, MouseEvent.MOUSE_RELEASED,
                1L, 0, 2, 3, 1, false)));
        assertFalse(AutoLogoutManager.countsAsActivity(new MouseEvent(source, MouseEvent.MOUSE_CLICKED,
                1L, 0, 2, 3, 1, false)));
    }
}
