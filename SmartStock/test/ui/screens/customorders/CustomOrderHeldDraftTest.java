package ui.screens.customorders;

import com.google.gson.*;
import services.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderHeldDraftTest {
    @TempDir Path directory;
    @Test void preservesQuantityDesignCustomerAndAttachmentsWithoutTransferringApprovalTokens()throws Exception{
        var json=LanJson.create();var request=json.fromJson("{\"itemName\":\"Shirt\",\"itemSize\":\"Large\",\"itemColor\":\"Black\",\"pricingType\":\"AREA\",\"widthValue\":36,\"lengthValue\":24,\"unitPrice\":1500,\"customizationDetails\":\"Orientation: Landscape | Design: 36 x 24 IN\",\"orderInstructions\":\"Chest logo\",\"lineDiscountPercent\":10,\"lineDiscountApprovalToken\":\"temporary-approval\",\"priceOverrideApprovalToken\":\"temporary-price-approval\",\"printAddons\":[{\"materialName\":\"Embroidery\",\"printCharge\":100}]}" ,CustomOrderDataService.OrderLineRequest.class);
        var item=json.fromJson("{\"name\":\"Shirt\",\"size\":\"Large\",\"color\":\"Black\",\"pricingType\":\"AREA\"}",CustomOrderDataService.CustomItemOption.class);
        var customer=json.fromJson("{\"customerId\":9,\"name\":\"Test Customer\",\"phone\":\"6000000\",\"customOrderDiscountEnabled\":true,\"customOrderDiscountPercent\":5}",CustomOrderDataService.CustomerOption.class);
        var details=new CustomerInfoPanel.Draft(customer,"Test Customer","6000000","0009","test@example.com",true);
        String file=UUID.randomUUID().toString();var line=new CustomOrderHeldDraft.Line(request,item,null,List.of(file));
        var draft=new CustomOrderHeldDraft(details,false,true,"2026-10-15","Pack together","CARD","reference","1500","Deposit reason",Collections.nCopies(16,line),List.of());
        Path path=directory.resolve("design.pdf");Files.write(path,new byte[]{1,2,3,4});
        var prepared=CustomOrderHoldTransfer.prepare(UUID.randomUUID().toString(),"Shirts",draft,Map.of(file,path));
        String serialized=json.toJson(prepared.draft());assertFalse(serialized.contains("temporary-approval"));assertFalse(serialized.contains("temporary-price-approval"));
        var restored=json.fromJson(serialized,CustomOrderHeldDraft.class);
        assertEquals(prepared.draft(),restored);assertEquals(16,restored.lines().size());assertEquals(1,new HashSet<>(restored.lines()).size());assertEquals(1,restored.attachments().size());assertEquals(4,restored.attachments().get(0).size());
        assertEquals(details,restored.customer());assertEquals("1500",restored.deposit());assertEquals(request.customizationDetails(),restored.lines().get(0).request().customizationDetails());assertEquals(request.printAddons(),restored.lines().get(0).request().printAddons());assertNull(restored.lines().get(0).request().lineDiscountApprovalToken());
        assertEquals(CustomOrderHoldTransfer.hash(path),restored.attachments().get(0).sha256());Files.write(path,new byte[]{4,3,2,1});assertNotEquals(CustomOrderHoldTransfer.hash(path),restored.attachments().get(0).sha256());
    }
    @Test void customerDraftRestoresSelectedAccountAndManualDetails()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            var customer=LanJson.create().fromJson("{\"customerId\":9,\"name\":\"Test Customer\",\"phone\":\"6000000\",\"customOrderDiscountEnabled\":true,\"customOrderDiscountPercent\":5}",CustomOrderDataService.CustomerOption.class);
            var draft=new CustomerInfoPanel.Draft(customer,"Custom name","6000001","0009","test@example.com",true);var panel=new CustomerInfoPanel();panel.restoreDraft(draft);assertEquals(draft,panel.draft());panel.clear();assertNull(panel.draft().customer());
        });
    }
}
