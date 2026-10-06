package ui.helpers;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class DiscountPriceFieldsTest {
    @Test void editsWorkInBothDirectionsAndRetainTheLastInputMode() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JTextField original=new JTextField("1000"),percent=new JTextField("0"),discounted=new JTextField();
            var binding=new DiscountPriceFields(()->new BigDecimal(original.getText()),percent,discounted);
            DiscountPriceFields.listen(original,binding::refresh);
            discounted.setText("750");
            assertEquals(0,new BigDecimal(percent.getText()).compareTo(new BigDecimal("25")));
            original.setText("1500");
            assertEquals(0,new BigDecimal(percent.getText()).compareTo(new BigDecimal("50")));
            percent.setText("20");
            assertEquals(0,new BigDecimal(discounted.getText()).compareTo(new BigDecimal("1200")));
            original.setText("1000");
            assertEquals(0,new BigDecimal(discounted.getText()).compareTo(new BigDecimal("800")));
            discounted.setText("1100");
            assertThrows(IllegalArgumentException.class,binding::validatedPercent);
        });
    }
    @Test void keepsCentsWhenTheDiscountedPriceIsEntered() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            JTextField percent=new JTextField("0"),price=new JTextField();
            var binding=new DiscountPriceFields(()->new BigDecimal("1000"),percent,price);
            price.setText("750.25");
            assertEquals(0,new BigDecimal("24.9750").compareTo(binding.validatedPercent()));
            assertEquals(new BigDecimal("750.25"),DiscountPriceFields.discountedPrice(new BigDecimal("1000"),binding.validatedPercent()));
            price.setText("750.251");
            assertThrows(IllegalArgumentException.class,binding::validatedPercent);
        });
    }
    @Test void validatesZeroFullAndRecurringDiscounts() {
        assertEquals(BigDecimal.ZERO,DiscountPriceFields.percentage(BigDecimal.ZERO,BigDecimal.ZERO));
        assertEquals(new BigDecimal("100.0000"),DiscountPriceFields.percentage(new BigDecimal("100"),BigDecimal.ZERO));
        assertEquals(new BigDecimal("33.3333"),DiscountPriceFields.percentage(new BigDecimal("300"),new BigDecimal("200")));
        assertThrows(IllegalArgumentException.class,()->DiscountPriceFields.percentage(new BigDecimal("100"),new BigDecimal("-1")));
        assertThrows(IllegalArgumentException.class,()->DiscountPriceFields.discountedPrice(new BigDecimal("100"),new BigDecimal("101")));
    }
}
