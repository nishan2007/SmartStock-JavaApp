package ui.helpers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TouchSearchKeyboardTest {
    @Test void distinguishesTouchFromMouseAndPen() {
        assertTrue(TouchSearchKeyboard.isTouch(0xff515780L));
        assertTrue(TouchSearchKeyboard.isTouch(0xff5157ffL));
        assertFalse(TouchSearchKeyboard.isTouch(0));
        assertFalse(TouchSearchKeyboard.isTouch(0xff515701L));
        assertFalse(TouchSearchKeyboard.isTouch(0x80));
    }
}
