package services;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Connection;
import static org.junit.jupiter.api.Assertions.*;
class QuotationDiscountPrecisionTest {
    @Test void savedLineAmountsKeepTheEnteredDiscountedPriceAndCents() throws Exception {
        Class<?> settings=Class.forName("services.ServerQuotationInvoiceService$SalesVatSettings");
        var constructor=settings.getDeclaredConstructor(boolean.class,boolean.class,BigDecimal.class);constructor.setAccessible(true);
        var method=ServerQuotationInvoiceService.class.getDeclaredMethod("lineAmounts",Connection.class,settings,ServerQuotationInvoiceService.QuotationLineInput.class);method.setAccessible(true);
        var line=new ServerQuotationInvoiceService.QuotationLineInput(null,"Item","",1,new BigDecimal("1000"),new BigDecimal("1000"),new BigDecimal("24.9750"),"PICKUP","",null,null,null,null,null);
        Object amounts=method.invoke(null,null,constructor.newInstance(false,false,BigDecimal.ZERO),line);
        var accessor=amounts.getClass().getDeclaredMethod("preVatTotal");accessor.setAccessible(true);
        assertEquals(new BigDecimal("750.25"),accessor.invoke(amounts));
        var discount=amounts.getClass().getDeclaredMethod("discount");discount.setAccessible(true);
        assertEquals(new BigDecimal("249.75"),discount.invoke(amounts));
    }
}
