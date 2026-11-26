package ui.screens.customorders;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class OrdersDeliverySelectorTest {
  private JComboBox<?> selector(Container parent) {
    for(Component child:parent.getComponents()){
      if(child instanceof JComboBox<?> box && "orderDeliveryAction".equals(box.getName()))return box;
      if(child instanceof Container nested){var result=selector(nested);if(result!=null)return result;}
    }
    return null;
  }
  @Test @SuppressWarnings("unchecked")
  void eachActionUsesAllRemainingLinesAndKeepsValidationInTheExistingHandler() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      AtomicReference<String> status=new AtomicReference<>();
      AtomicReference<Consumer<Boolean>> completion=new AtomicReference<>();
      var handler=(CustomOrdersLookupTabPanel.Handler)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CustomOrdersLookupTabPanel.Handler.class},(proxy,method,args)->{
        if(method.getReturnType()==boolean.class)return true;
        if(method.getName().equals("loadDeliverableLines")){
          assertEquals(42L,args[0]);
          ((Consumer<List<CustomOrdersLookupTabPanel.LineDeliveryOption>>)args[1]).accept(List.of(
            new CustomOrdersLookupTabPanel.LineDeliveryOption(1L,"Polo","S","PENDING","NONE"),
            new CustomOrdersLookupTabPanel.LineDeliveryOption(2L,"Polo","M","CALLED","NONE"),
            new CustomOrdersLookupTabPanel.LineDeliveryOption(3L,"Polo","L","DELIVERED","NONE"),
            new CustomOrdersLookupTabPanel.LineDeliveryOption(4L,"Polo","XL","PENDING","FULL")));
        }
        if(method.getName().equals("updateLookupLineDelivery")){
          assertEquals(42L,args[0]);assertEquals(List.of(1L,2L),args[1]);
          status.set((String)args[2]);completion.set((Consumer<Boolean>)args[5]);
        }
        return null;
      });
      var panel=new CustomOrdersLookupTabPanel(handler);
      var data=CustomOrderDetailsPanelTest.fixture("100",false);
      data.getAsJsonObject("summary").addProperty("custom_order_id",42);
      data.getAsJsonObject("summary").addProperty("status","IN_PROGRESS");
      panel.orderDetails.showDetails(panel.orderDetails.loading(),data);
      var box=selector(panel);
      for(int i=1;i<=3;i++){
        box.setSelectedIndex(i);
        assertEquals(List.of("DELIVERED","CALLED","CALLED_NO_ANSWER").get(i-1),status.get());
        assertFalse(box.isEnabled());completion.get().accept(false);
        assertTrue(box.isEnabled());assertEquals(0,box.getSelectedIndex());
      }
    });
  }
  @Test void noPermissionOrTerminalOrderCannotChangeDelivery() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var legacy=new CustomOrderDetailsPanel(enabled->{});var view=new OrdersDetailWorkspace(legacy);
      var data=CustomOrderDetailsPanelTest.fixture("100",false);
      data.getAsJsonObject("summary").addProperty("status","IN_PROGRESS");
      legacy.showDetails(legacy.loading(),data);assertFalse(selector(view).isEnabled());
      view.setDeliveryUpdater((id,status,done)->fail("No write expected"));
      for(String terminal:List.of("CANCELLED","DELIVERED")){
        data.getAsJsonObject("summary").addProperty("status",terminal);
        legacy.showDetails(legacy.loading(),data);assertFalse(selector(view).isEnabled());
      }
    });
  }
}
