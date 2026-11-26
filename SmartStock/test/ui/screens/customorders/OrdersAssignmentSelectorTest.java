package ui.screens.customorders;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import services.CustomOrderDataService.EmployeeOption;

class OrdersAssignmentSelectorTest {
  private JComboBox<?> selector(Container parent) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JComboBox<?> combo && "orderAssignedEmployee".equals(combo.getName())) return combo;
      if (child instanceof Container nested) {var result=selector(nested);if(result!=null)return result;}
    }
    return null;
  }
  private JsonObject order() {
    var data=CustomOrderDetailsPanelTest.fixture("100",false);
    var summary=data.getAsJsonObject("summary");
    summary.addProperty("custom_order_id",42);summary.addProperty("status","IN_PROGRESS");
    summary.addProperty("assigned_to_user_id",7);summary.addProperty("assigned_to_name","Alex");
    return data;
  }
  @Test void usesEmployeeIdsAndRestoresSelectionOnFailureWithoutDuplicateWrites() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var legacy=new CustomOrderDetailsPanel(enabled->{});var view=new OrdersDetailWorkspace(legacy);
      view.allowAssignment(true);
      AtomicReference<Consumer<Boolean>> saved=new AtomicReference<>();
      AtomicReference<Integer> chosenId=new AtomicReference<>();
      view.setAssignmentHandler(new OrdersDetailWorkspace.AssignmentHandler(){
        public void load(Consumer<List<EmployeeOption>> done){done.accept(List.of(new EmployeeOption(8,"Alex"),new EmployeeOption(7,"Alex")));}
        public void save(long id,Integer employeeId,String status,Consumer<Boolean> done){
          assertEquals(42,id);assertEquals("IN_PROGRESS",status);assertNull(saved.get());chosenId.set(employeeId);saved.set(done);
        }
      });
      legacy.showDetails(legacy.loading(),order());
      JComboBox<?> box=selector(view);assertNotNull(box);
      assertEquals(7,((EmployeeOption)box.getSelectedItem()).userId());
      box.setSelectedIndex(1);assertEquals(8,chosenId.get());assertFalse(box.isEnabled());
      saved.get().accept(false);assertTrue(box.isEnabled());assertEquals(7,((EmployeeOption)box.getSelectedItem()).userId());
      saved.set(null);box.setSelectedIndex(0);assertNull(chosenId.get());saved.get().accept(true);
      assertNull(((EmployeeOption)box.getSelectedItem()).userId());
    });
  }
  @Test void onlyAuthorizedCurrentOrdersLoadEmployeesAndStaleLoadsAreIgnored() throws Exception {
    SwingUtilities.invokeAndWait(()->{
      var legacy=new CustomOrderDetailsPanel(enabled->{});var view=new OrdersDetailWorkspace(legacy);
      AtomicReference<Consumer<List<EmployeeOption>>> loading=new AtomicReference<>();
      view.setAssignmentHandler(new OrdersDetailWorkspace.AssignmentHandler(){
        public void load(Consumer<List<EmployeeOption>> done){loading.set(done);}
        public void save(long id,Integer employeeId,String status,Consumer<Boolean> done){fail("No write expected");}
      });
      legacy.showDetails(legacy.loading(),order());assertNull(selector(view));assertNull(loading.get());
      view.allowAssignment(true);legacy.showDetails(legacy.loading(),order());assertFalse(selector(view).isEnabled());
      var old=loading.get();var cancelled=order();cancelled.getAsJsonObject("summary").addProperty("status","CANCELLED");
      legacy.showDetails(legacy.loading(),cancelled);old.accept(List.of(new EmployeeOption(9,"New employee")));
      assertNull(selector(view));assertSame(cancelled,view.selected());
    });
  }
}
