package Receipt;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import managers.CompanyCustomizationManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AccountChargeSignatureReceiptTest {
    private ReceiptData receipt(String representative){
        return new ReceiptData(1,"0001-0001-000123",null,"Store","Cashier","Organization","ORG1",
            "ACCOUNT","UNPAID","",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"",
            BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,null,null,List.of(),representative,new BigDecimal("1250.00"));
    }
    @Test void authorizedAccountReceiptIncludesBlankSigningSectionAtBottomIncludingReprint()throws Exception{
        var settings=new Gson().fromJson("{\"companyName\":\"DECKERS\",\"footerLine\":\"Thank you\"}",CompanyCustomizationManager.ReceiptSettings.class);
        String text=ReceiptFormatter.formatText(receipt("Jane Collector"),settings);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/account-charge-receipt-preview.txt"),text);
        assertFalse(text.contains("Collected by:"));
        assertTrue(text.contains("Account balance"));assertTrue(text.contains("1,250")||text.contains("1250"));
        assertTrue(text.contains("Signature"));assertFalse(text.contains("Date: _"));
        assertTrue(text.indexOf("Signature")>text.indexOf("Thank you"));
        assertTrue(text.contains("\n\n\n_"),"Leave space for a handwritten signature");
        String reprint=new String(ReceiptFormatter.formatEscPos(receipt("Jane Collector"),settings,true),StandardCharsets.ISO_8859_1);
        assertTrue(reprint.contains("Signature"));
        String unsigned=ReceiptFormatter.formatText(receipt(""),settings);
        assertFalse(unsigned.contains("Signature"));assertTrue(unsigned.contains("Account balance"));
    }
}
