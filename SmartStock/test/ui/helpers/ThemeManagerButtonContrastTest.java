package ui.helpers;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeManagerButtonContrastTest {
    @Test
    void repairsWhiteTextOnWhiteButton() {
        JButton button = new JButton("Save");
        button.setBackground(Color.WHITE);
        button.setForeground(Color.WHITE);

        ThemeManager.ensureReadableButtonColors(button);

        assertEquals(Color.BLACK, button.getForeground());
        assertTrue(ThemeManager.contrastRatio(button.getForeground(), button.getBackground()) >= 4.5);
    }

    @Test
    void usesDarkTextOnBrightAccentButtons() {
        JButton button = new JButton("Discount");
        button.setBackground(new Color(245, 158, 11));
        button.setForeground(Color.WHITE);

        ThemeManager.ensureReadableButtonColors(button);

        assertEquals(Color.BLACK, button.getForeground());
        assertTrue(ThemeManager.contrastRatio(button.getForeground(), button.getBackground()) >= 4.5);
    }

    @Test
    void preservesReadableBrandButtonText() {
        JButton button = new JButton("Delete");
        button.setBackground(new Color(185, 28, 28));
        button.setForeground(Color.WHITE);

        ThemeManager.ensureReadableButtonColors(button);

        assertEquals(Color.WHITE, button.getForeground());
    }

    @Test
    void choosesDarkTextForNativeMacButtonFill() {
        Color nativeLightBezel = new Color(238, 238, 238);

        assertEquals(Color.BLACK, ThemeManager.readableTextColor(nativeLightBezel));
        assertTrue(ThemeManager.contrastRatio(Color.BLACK, nativeLightBezel) >= 4.5);
    }
}
