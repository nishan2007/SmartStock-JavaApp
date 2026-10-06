package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.ItemSearchOption;
import javax.swing.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderItemSearchTest {
    private static long generation(CustomOrderItemSearch search) throws Exception {
        var f = CustomOrderItemSearch.class.getDeclaredField("generation");
        f.setAccessible(true);
        return f.getLong(search);
    }

    @Test void canChooseASpecificVariantWithoutRunningTheFirstMatchLookup() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = new JTextField();
            AtomicReference<ItemSearchOption> chosen = new AtomicReference<>();
            AtomicInteger lookups = new AtomicInteger();
            var search = new CustomOrderItemSearch(field, chosen::set, lookups::incrementAndGet);
            var first = new ItemSearchOption(10L, 11L, "Shirt / Red / Small");
            var second = new ItemSearchOption(10L, 12L, "Shirt / Blue / Large");
            search.model.addElement(first);
            search.model.addElement(second);
            search.list.setSelectedIndex(1);
            search.selectHighlighted();
            assertEquals(second, chosen.get());
            assertEquals(second.label(), field.getText());
            field.postActionEvent();
            assertEquals(0, lookups.get());
            assertFalse(search.popup.isVisible());
            assertTrue(search.model.isEmpty());
            search.dismiss();
        });
    }

    @Test void editsClearSuggestionsAndLateResultsCannotReplaceANewerQuery() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                JTextField field = new JTextField();
                var search = new CustomOrderItemSearch(field, option -> {}, () -> {});
                field.setText("shirt");
                long old = generation(search);
                search.acceptResults(old, "shirt", List.of(new ItemSearchOption(1L,null,"Shirt")));
                assertEquals(1,search.model.size());
                field.setText("cap");
                assertTrue(search.model.isEmpty());
                search.acceptResults(old,"shirt",List.of(new ItemSearchOption(1L,null,"Shirt")));
                assertTrue(search.model.isEmpty());
                // Returning to the same text must not revive a dismissed request.
                field.setText("shirt");
                search.acceptResults(old,"shirt",List.of(new ItemSearchOption(1L,null,"Old Shirt")));
                assertTrue(search.model.isEmpty());
                long latest = generation(search);
                search.dismiss();
                search.acceptResults(latest,"shirt",List.of(new ItemSearchOption(1L,null,"Shirt")));
                assertTrue(search.model.isEmpty());
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    @Test void barcodeEnterKeepsExplicitLookupWhenNoSuggestionIsOpen() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField field = new JTextField();
            AtomicInteger lookups = new AtomicInteger();
            var search = new CustomOrderItemSearch(field, option -> fail("Unexpected suggestion selection"), lookups::incrementAndGet);
            field.setText("1234567890");
            field.postActionEvent();
            assertEquals(1,lookups.get());
            assertEquals("1234567890",field.getText());
            search.dismiss();
        });
    }
}
