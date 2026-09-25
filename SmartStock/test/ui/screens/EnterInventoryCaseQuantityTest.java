package ui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnterInventoryCaseQuantityTest {
    @Test
    void quantityPerCaseIsMultipliedByNumberOfCases() {
        assertEquals(24L, EnterInventory.calculateTotalToAdd(2, 12));
    }

    @Test
    void oneCaseLeavesQuantityUnchanged() {
        assertEquals(7L, EnterInventory.calculateTotalToAdd(7, 1));
    }

    @Test
    void availableAttributesShareOneDisplayColumn() {
        assertEquals("315 ml / Red / Cherry",
                EnterInventory.availableAttributes("315 ml", "Red", "Cherry"));
        assertEquals("Cherry", EnterInventory.availableAttributes("", null, "Cherry"));
    }
}
