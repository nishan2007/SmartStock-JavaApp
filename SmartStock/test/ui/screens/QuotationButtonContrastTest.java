package ui.helpers;

import org.junit.jupiter.api.Test;
import ui.screens.Quotations;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicButtonUI;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationButtonContrastTest {
    @Test
    void primaryQuotationButtonRemainsPaintedAndReadableAfterThemePass() {
        JButton button = new JButton("New Quotation");
        Quotations.stylePrimaryButton(button);

        ThemeManager.applyToComponent(button);

        assertInstanceOf(BasicButtonUI.class, button.getUI());
        assertTrue(button.isOpaque());
        assertTrue(button.isContentAreaFilled());
        assertTrue(ThemeManager.contrastRatio(button.getForeground(), button.getBackground()) >= 4.5);
    }
}
