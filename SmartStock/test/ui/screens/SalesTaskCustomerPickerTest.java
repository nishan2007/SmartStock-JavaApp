package ui.screens;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SalesTaskCustomerPickerTest {
    @Test void preservesTheLinkedAccountUntilExplicitlyCleared()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var customer=new SalesTaskCustomerPicker.Customer(42,"Customer Name","7058194","CA-42","customer@example.invalid");
            var chosen=new AtomicReference<SalesTaskCustomerPicker.Customer>();
            var picker=new SalesTaskCustomerPicker(customer,chosen::set);
            assertEquals(customer,picker.selected());assertEquals(customer,chosen.get());
            JTextField label=(JTextField)((BorderLayout)picker.getLayout()).getLayoutComponent(BorderLayout.CENTER);
            assertFalse(label.isEditable());assertTrue(label.getText().contains("CA-42"));
            JPanel buttons=(JPanel)((BorderLayout)picker.getLayout()).getLayoutComponent(BorderLayout.EAST);
            for(Component component:buttons.getComponents())if(component instanceof JButton button&&button.getText().equals("Clear"))button.doClick();
            assertNull(picker.selected());assertNull(chosen.get());assertEquals("Walk-in customer",label.getText());
        });
    }
}
