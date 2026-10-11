package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomOrderDesignOrientationTest {
    @Test void restoresOrientationFromSavedDesignDetails() {
        assertEquals("Portrait", CustomOrders.designOrientation("Orientation: Portrait | Design: 36 x 72 IN"));
        assertEquals("Landscape", CustomOrders.designOrientation("Orientation: Landscape | Design: 36 x 72 IN"));
    }

    @Test void olderOrdersDoNotInventAnOrientation() {
        assertEquals("Select orientation", CustomOrders.designOrientation("Design: 36 x 72 IN"));
        assertEquals("Select orientation", CustomOrders.designOrientation(null));
    }
}
