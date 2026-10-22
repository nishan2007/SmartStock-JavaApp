package Receipt;

import managers.CompanyCustomizationManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderSlipQuantityTest {
    private CustomOrderSlipData data(List<CustomOrderSlipData.Line> lines) {
        var sample=CustomOrderSlipBuilder.sample();
        return new CustomOrderSlipData(sample.orderNumber(),sample.customerName(),sample.customerPhone(),sample.customerAccountNumber(),sample.dueDate(),sample.createdAt(),sample.takenByName(),sample.locationName(),sample.deviceName(),sample.paymentMethod(),sample.paymentReference(),sample.paymentStatus(),new BigDecimal("500"),BigDecimal.ZERO,new BigDecimal("500"),"",lines);
    }
    private CustomOrderSlipData.Line line(String variant,String details,String instructions,String price) {
        return new CustomOrderSlipData.Line("Custom shirt",variant,details,instructions,new BigDecimal(price));
    }
    @Test void fiveIdenticalUnitsPrintOnceWithQuantityAndSummedPrice() {
        var units=new ArrayList<CustomOrderSlipData.Line>(Collections.nCopies(5,line("Large","Front logo","Centre it","100")));
        units.set(4,line("Large","Front logo","Centre it","100.00"));
        var data=data(units);
        assertEquals(5,data.lines().size());assertEquals(1,data.printLines().size());
        assertEquals(5,data.printLines().get(0).quantity());assertEquals(0,new BigDecimal("500").compareTo(data.printLines().get(0).total()));
        var receipt=new CompanyCustomizationManager.ReceiptSettings("Deckers","","","","","","","","","","","Thank you","",false,true,true,true,true,true,true,false,false,BigDecimal.ZERO,1,BigDecimal.ZERO,false,CompanyCustomizationManager.AccountPaymentReceiptSettings.defaults());
        var settings=new CompanyCustomizationManager.CustomOrderSlipSettings(true,false,"ORDER SLIP","","","",0,false,true,true,true,true,true,true,true,true,true,true,false,false,false);
        String text=CustomOrderSlipFormatter.format40Column(data,receipt,settings);
        assertTrue(text.contains("Custom shirt / Large x 5 $500"),text);
        assertEquals(1,text.split("Front logo",-1).length-1);
        assertFalse(text.contains("2. Custom shirt"));
        var image=new java.awt.image.BufferedImage(700,400,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try {assertDoesNotThrow(()->CustomOrderSlipRenderer.paintSlip(graphics,0,0,700,390,data,receipt,settings,null));}finally{graphics.dispose();}
    }
    @Test void differencesRemainSeparateAndGroupsKeepFirstAppearanceOrder() {
        var first=line("Large","Front logo","Centre it","100");
        var data=data(List.of(first,line("Small","Front logo","Centre it","100"),line("Large","Back logo","Centre it","100"),line("Large","Front logo","Left chest","100"),line("Large","Front logo","Centre it","90"),first));
        var groups=data.printLines();assertEquals(5,groups.size());assertEquals(2,groups.get(0).quantity());
        assertEquals("Small",groups.get(1).line().variantName());assertEquals(1,groups.get(4).quantity());
        assertEquals("",groups.get(1).quantitySuffix());assertEquals(6,data.lines().size());
    }
}
