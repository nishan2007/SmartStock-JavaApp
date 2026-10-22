package ui.screens.customorders;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPhoneLookupTest {
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    @Test void phoneTypingShowsExplicitSelectionAndRetainsCustomerDiscounts()throws Exception {
        var reference=new java.util.concurrent.atomic.AtomicReference<CustomerInfoPanel>();
        var lookup=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()->{var panel=new CustomerInfoPanel(phone->{lookup.countDown();return JsonParser.parseString("""
          {"reserved":true,"customers":[{"customerId":42,"name":"Existing customer","phone":"+5927058194","accountNumber":"CA-42","email":"customer@example.com","active":true,"customOrderDiscountEnabled":true,"customOrderDiscountPercent":10,"customOrderDepositExempt":true}]}
          """).getAsJsonObject();});reference.set(panel);try{((JTextField)field(panel,"phoneField")).setText("7058194");}catch(Exception e){throw new RuntimeException(e);}});
        assertTrue(lookup.await(5,TimeUnit.SECONDS));
        var selected=new CountDownLatch(1);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(()->{Timer timer=new Timer(30,null);timer.addActionListener(event->{try{var panel=reference.get();JPanel matches=(JPanel)field(panel,"phoneMatches");if(matches.getComponentCount()==0)return;timer.stop();assertNull(panel.getSelectedCustomer());((JButton)matches.getComponent(0)).doClick();assertEquals(42,panel.getSelectedCustomer().customerId());assertEquals("Existing customer",panel.getCustomerName());assertTrue(panel.applyCustomerDiscount());assertTrue(panel.getSelectedCustomer().customOrderDepositExempt());selected.countDown();}catch(Throwable e){failure.set(e);timer.stop();selected.countDown();}});timer.start();});
        assertTrue(selected.await(5,TimeUnit.SECONDS));if(failure.get()!=null)throw new AssertionError(failure.get());
    }
}
