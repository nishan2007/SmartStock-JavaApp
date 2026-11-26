package Receipt;

import com.google.gson.Gson;
import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SalesTaskReceiptTest {
    @Test void provisionalAdvanceReceiptDoesNotPresentZeroAsTheFinalCharge() {
        var settings=new Gson().fromJson("{companyName:'DECKERS',footerLine:'Thank you'}",CompanyCustomizationManager.ReceiptSettings.class);
        var allocation=new AccountPaymentReceiptData.AllocationLine("Sales Task ST-12",BigDecimal.TEN,null,BigDecimal.TEN,"PRICE_PENDING",null);
        var receipt=new AccountPaymentReceiptData(-1,1,"ST-12/P-1",new Timestamp(0),"Store","Cashier","Customer","ACCOUNT-1","","CASH","","Register","Drawer",BigDecimal.TEN,BigDecimal.ZERO,List.of(allocation));
        String text=AccountPaymentReceiptFormatter.formatText(receipt,settings,CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        assertTrue(text.contains("Not finalized"));assertTrue(text.contains("ST-12"));
    }
    @Test void finalSaleReceiptIncludesAdvancesAndPickupTender() {
        var settings=new Gson().fromJson("{companyName:'DECKERS',footerLine:'Thank you'}",CompanyCustomizationManager.ReceiptSettings.class);
        var receipt=new ReceiptData(1,"TEST-1",null,"Store","Cashier","Customer","","CARD","PAID","",new BigDecimal("75"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"",new BigDecimal("75"),new BigDecimal("75"),BigDecimal.ZERO,null,null,List.of())
            .withTaskPaymentSummary("Sales Task ST-12\nNet tender including advances:\nCASH: 20.00\nCARD: 55.00");
        String text=ReceiptFormatter.formatText(receipt,settings);
        assertTrue(text.contains("ST-12"));assertTrue(text.contains("CASH: 20.00"));assertTrue(text.contains("CARD: 55.00"));
    }
}
