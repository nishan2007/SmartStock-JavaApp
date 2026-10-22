package Receipt;

import com.google.gson.Gson;
import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class LoyaltyReceiptTest {
    @Test void loyaltyAppearsOnSaleAndCustomOrderReceipts()throws Exception {
        var settings=new Gson().fromJson("{\"companyName\":\"DECKERS\",\"footerLine\":\"Thank you\"}",CompanyCustomizationManager.ReceiptSettings.class);
        String summary="Points used: 100 (G$100.00 off)\nPoints pending: 29\nAvailable: 400 points = G$400.00";
        var receipt=new ReceiptData(1,"0001-0001-000123",null,"Store","Cashier","Customer","C1","CARD","PAID","",new BigDecimal("3000"),BigDecimal.ZERO,new BigDecimal("100"),BigDecimal.ZERO,BigDecimal.ZERO,"",new BigDecimal("2900"),new BigDecimal("2900"),BigDecimal.ZERO,null,null,List.of(),"").withLoyaltySummary(summary);
        String text=ReceiptFormatter.formatText(receipt,settings);assertTrue(text.contains("Points used: 100"));assertTrue(text.contains("Points pending: 29"));assertTrue(text.contains("G$400.00"));
        var slip=new CustomOrderSlipData("CO-LOYALTY-PREVIEW","Alex Customer","555-0199","CA-000100",java.time.LocalDate.of(2026,10,25),java.sql.Timestamp.valueOf("2026-10-21 10:00:00"),"Cashier","Main Store","POS-01","CARD","Test","PARTIAL",new BigDecimal("2900"),new BigDecimal("1000"),new BigDecimal("1900"),"Loyalty points applied before the first payment.",List.of(new CustomOrderSlipData.Line("Custom print","","Customer artwork","",new BigDecimal("2900")))).withLoyaltySummary(summary);
        var slipSettings=new CompanyCustomizationManager.CustomOrderSlipSettings(true,false,"CUSTOMER ORDER","","","Thank you",0,false,true,true,true,true,true,true,true,true,true,true,false,false,false);
        String custom=CustomOrderSlipFormatter.format40Column(slip,settings,slipSettings);assertTrue(custom.contains("Points used: 100"));assertTrue(custom.contains("G$400.00"));
        Path out=Path.of("target/loyalty-previews");Files.createDirectories(out);Files.writeString(out.resolve("sale-receipt.txt"),text);
        var image=new BufferedImage(850,1100,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();CustomOrderSlipRenderer.paintSlip(g,0,0,850,1100,slip,settings,slipSettings,null);g.dispose();ImageIO.write(image,"png",out.resolve("custom-order-slip.png").toFile());
    }
}
