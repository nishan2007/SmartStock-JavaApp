package ui.screens;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class ExpenseCategorySelectorTest {
    @Test void offersStandardCategoriesAndAllowsAnUnlistedCategory()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JComboBox<String> selector=BalanceSheet.expenseCategorySelector();
            assertTrue(selector.isEditable());
            selector.setSelectedItem("Inventory / Supplies");assertEquals("Inventory / Supplies",selector.getSelectedItem());
            selector.getEditor().setItem("Trade show fees");assertEquals("Trade show fees",selector.getEditor().getItem());
            selector.setSelectedIndex(-1);assertEquals("",selector.getEditor().getItem());
        });
    }
}
