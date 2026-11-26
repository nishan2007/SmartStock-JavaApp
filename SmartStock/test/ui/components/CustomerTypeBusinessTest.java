package ui.components;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class CustomerTypeBusinessTest {
    @Test void accountClassificationTracksTheSingleTypeSelection() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            CustomerTypeSelector selector=new CustomerTypeSelector(false);
            assertFalse(selector.isBusinessAccount());
            selector.setSelectedCustomerType(9,"Business");
            assertTrue(selector.isBusinessAccount());
            selector.setSelectedCustomerType(2,"Individual");
            assertFalse(selector.isBusinessAccount());
            selector.setSelectedCustomerType(10,"  Businesses  ");
            assertTrue(selector.isBusinessAccount());
            selector.setSelectedCustomerType(9,"Business");
            assertTrue(selector.isBusinessAccount());
            selector.clearSelection();
            assertFalse(selector.isBusinessAccount());
        });
    }
}
