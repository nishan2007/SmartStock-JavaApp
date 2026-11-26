package ui.screens.customorders;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class OrdersDueDateTest {
  private Component find(Container parent,String name){
    for(Component child:parent.getComponents()){
      if(name.equals(child.getName()))return child;
      if(child instanceof Container nested){var found=find(nested,name);if(found!=null)return found;}
    }return null;
  }
  @Test void missingDateCanBeSavedAndFailedSavesCanBeRetried()throws Exception{
    SwingUtilities.invokeAndWait(()->{
      var legacy=new CustomOrderDetailsPanel(enabled->{});var view=new OrdersDetailWorkspace(legacy);
      var data=CustomOrderDetailsPanelTest.fixture("100",false);var summary=data.getAsJsonObject("summary");
      summary.addProperty("custom_order_id",42);summary.addProperty("status","IN_PROGRESS");summary.remove("due_date");
      AtomicReference<Consumer<Boolean>> completion=new AtomicReference<>();
      view.setDueDateUpdater((id,date,done)->{assertEquals(42,id);assertEquals(LocalDate.of(2026,11,3),date);completion.set(done);});
      legacy.showDetails(legacy.loading(),data);
      var date=(DatePickerField)find(view,"missingOrderDueDate");var save=(JButton)find(view,"saveOrderDueDate");
      save.doClick();assertNull(completion.get());
      date.setText("2026-02-30");save.doClick();assertNull(completion.get());
      date.setSelectedDate(LocalDate.of(2026,11,3));save.doClick();assertFalse(save.isEnabled());assertFalse(date.isEnabled());
      completion.get().accept(false);assertTrue(save.isEnabled());assertTrue(date.isEnabled());
      save.doClick();completion.get().accept(true);assertNull(find(view,"saveOrderDueDate"));
      summary.addProperty("due_date","2026-11-03");legacy.showDetails(legacy.loading(),data);assertNull(find(view,"missingOrderDueDate"));
      summary.remove("due_date");view.setDueDateUpdater(null);legacy.showDetails(legacy.loading(),data);assertNull(find(view,"missingOrderDueDate"));
    });
  }
}
