package ui.screens.customorders;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderDetailsPanelTest {
    @Test void newProductionStepsCanBeSelectedAndSentWithoutLosingTheirStatus()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            for(String status:java.util.List.of("STARTED","WAITING_FOR_APPROVAL")){
                var data=fixture("0",false);
                data.getAsJsonObject("summary").addProperty("custom_order_id",40);
                data.getAsJsonObject("summary").addProperty("status","NEW");
                data.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("delivery_status","PENDING");
                var panel=new CustomOrderDetailsPanel(enabled->{});
                var sent=new java.util.concurrent.atomic.AtomicReference<String>();
                panel.setProductionUpdater((orderId,lineId,value,completion)->{
                    assertEquals(40,orderId);assertEquals(1,lineId);sent.set(value);completion.accept(false);
                });
                panel.showDetails(panel.loading(),data);
                var box=selector(panel);assertNotNull(box);
                box.setSelectedItem(status);assertEquals(status,sent.get());
                assertEquals("NOT_STARTED",box.getSelectedItem());
            }
        });
    }
    private static JComboBox<?> selector(java.awt.Container parent){
        for(var child:parent.getComponents()){
            if(child instanceof JComboBox<?> box)return box;
            if(child instanceof java.awt.Container container){var found=selector(container);if(found!=null)return found;}
        }
        return null;
    }
    @Test void inlineProductionUpdatesOnlyItsLineAndRestoresFailedSelection()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var data=fixture("0",false);
            data.getAsJsonObject("summary").addProperty("custom_order_id",40);
            data.getAsJsonObject("summary").addProperty("status","IN_PROGRESS");
            var line=data.getAsJsonArray("lines").get(0).getAsJsonObject();
            line.addProperty("production_status","FINISHED");line.addProperty("delivery_status","PENDING");
            var panel=new CustomOrderDetailsPanel(enabled->{});
            panel.setProductionUpdater((orderId,lineId,status,completion)->{
                assertEquals(40,orderId);assertEquals(1,lineId);assertEquals("READY",status);completion.accept(false);
            });
            panel.showDetails(panel.loading(),data);
            var box=selector(panel);assertNotNull(box);assertEquals("FINISHED",box.getSelectedItem());
            box.setSelectedItem("READY");assertEquals("FINISHED",box.getSelectedItem());assertTrue(box.isEnabled());
            line.addProperty("delivery_status","DELIVERED");panel.showDetails(panel.loading(),data);
            assertNull(selector(panel));
        });
    }
    static JsonObject fixture(String balance,boolean billed){
        JsonObject data=JsonParser.parseString("{\"summary\":{\"order_number\":\"CO-1791233345409-5036\",\"customer_name\":\"Melly\",\"customer_phone\":\"651 0368\",\"status\":\"DELIVERED\",\"total_amount\":13920,\"amount_paid\":13920},\"lines\":[{\"custom_order_line_id\":1,\"item_name\":\"Banner\",\"item_size\":\"4ft x 3ft\",\"line_total\":13920,\"production_status\":\"NOT_STARTED\",\"delivery_status\":\"DELIVERED\",\"order_instructions\":\"Image sent on WhatsApp\"}],\"addons\":[],\"payments\":[],\"activity\":[],\"deliveries\":[],\"production\":[],\"returns\":[],\"authorization\":[]}").getAsJsonObject();
        data.getAsJsonObject("summary").addProperty("balance_due",balance);data.getAsJsonObject("summary").addProperty("invoice_billed",billed);return data;
    }
    @Test void paidAndInvoiceLinkedOrdersCannotTakePayment()throws Exception{SwingUtilities.invokeAndWait(()->{
        var panel=new CustomOrderDetailsPanel(enabled->{});
        panel.showDetails(panel.loading(),fixture("0",false));assertFalse(panel.canPay());
        panel.showDetails(panel.loading(),fixture("100",false));assertTrue(panel.canPay());
        panel.showDetails(panel.loading(),fixture("100",true));assertFalse(panel.canPay());
        assertEquals(3,panel.tabs.getTabCount());
    });}
    @Test void oldResponsesAndErrorsCannotOverwriteTheSelectedOrder()throws Exception{SwingUtilities.invokeAndWait(()->{
        var panel=new CustomOrderDetailsPanel(enabled->{});long old=panel.loading(),current=panel.loading();
        panel.showDetails(current,fixture("100",false));panel.showDetails(old,fixture("0",false));panel.failed(old,"Old error");assertTrue(panel.canPay());
        panel.empty();panel.showDetails(current,fixture("100",false));assertFalse(panel.canPay());
    });}
    @Test void longNamesReturnsAndHistoryRemainSeparateAndScrollable()throws Exception{SwingUtilities.invokeAndWait(()->{
        var data=fixture("100",false);var line=data.getAsJsonArray("lines").get(0).getAsJsonObject();
        line.addProperty("item_name","A very long custom banner name with many words and customer requirements");line.addProperty("variant_name","Large / full colour");line.addProperty("return_status","PARTIAL");line.addProperty("returned_amount",20);
        for(int i=0;i<30;i++)data.getAsJsonArray("activity").add(JsonParser.parseString("{\"created_at\":\"2026-10-05T20:49:57Z\",\"action_type\":\"STATUS_CHANGE\",\"new_value\":\"DELIVERED\",\"user_name\":\"Amit\"}"));
        var panel=new CustomOrderDetailsPanel(enabled->{});panel.setSize(600,720);panel.showDetails(panel.loading(),data);
        assertTrue(panel.canPay());assertEquals("NOT_STARTED",line.get("production_status").getAsString());assertEquals("DELIVERED",line.get("delivery_status").getAsString());
        assertTrue(panel.tabs.getComponentAt(2) instanceof JScrollPane);
    });}
    @Test void datesAndDeliveryDescriptionsAreReadable(){
        assertFalse(CustomOrderDetailsPanel.date("2026-10-05T20:49:57.883987Z").contains("883987"));
        var event=JsonParser.parseString("{\"action_type\":\"deliveries\",\"user_name\":\"Amit\",\"item_name\":\"Banner\",\"device_name\":\"POS-05\"}").getAsJsonObject();
        assertEquals("Amit delivered Banner from POS-05",CustomOrderDetailsPanel.eventTitle(event));
    }
}
