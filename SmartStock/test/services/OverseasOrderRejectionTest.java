package services;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverseasOrderRejectionTest {
    @Test void reportsOverseasDrawerRequirement() {
        String message = "This draw is not enabled for overseas-order cash. Use an enabled drawer.";
        assertEquals(message, OverseasOrderService.rejectionMessage(new SQLException(message)));
    }
    @Test void reportsPaymentReferenceRequirement() {
        String message = "A payment reference is required for this method.";
        assertEquals(message, OverseasOrderService.rejectionMessage(new SQLException(message)));
    }
    @Test void hidesDatabaseDetailsAndUnknownFailures() {
        for (SQLException error : new SQLException[]{new SQLException("secret database detail", "23514"), new SQLException()}) {
            assertEquals("Order could not be saved. Check item quantities, customer details and the register cash drawer.",
                    OverseasOrderService.rejectionMessage(error));
        }
    }
}
