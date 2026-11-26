package ui.screens.customorders;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class OrdersProductionStepsTest {
  private JButton step(Container parent, String status) {
    for (Component child : parent.getComponents()) {
      if (child instanceof JButton button && status.equals(button.getActionCommand())) return button;
      if (child instanceof Container container) {
        JButton result = step(container, status);
        if (result != null) return result;
      }
    }
    return null;
  }

  @Test void stageClickUpdatesEveryOrderLineAndWaitsForServerCompletion() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      var legacy = new CustomOrderDetailsPanel(enabled -> {});
      var view = new OrdersDetailWorkspace(legacy);
      JsonObject data = CustomOrderDetailsPanelTest.fixture("100", false);
      data.getAsJsonObject("summary").addProperty("custom_order_id", 42);
      data.getAsJsonObject("summary").addProperty("status", "IN_PROGRESS");
      data.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("custom_order_line_id", 101);
      JsonObject second = data.getAsJsonArray("lines").get(0).getAsJsonObject().deepCopy();
      second.addProperty("custom_order_line_id", 102);
      data.getAsJsonArray("lines").add(second);
      AtomicReference<Consumer<Boolean>> completion = new AtomicReference<>();
      view.setProductionUpdater((orderId, ids, status, done) -> {
        assertEquals(42, orderId);
        assertEquals(List.of(101L, 102L), ids);
        assertEquals("READY", status);
        completion.set(done);
      });
      legacy.showDetails(legacy.loading(), data);
      step(view, "READY").doClick();
      assertNotNull(completion.get());
      assertFalse(step(view, "STARTED").isEnabled());
      completion.get().accept(false);
      assertTrue(step(view, "STARTED").isEnabled());
      // Failed writes leave the authoritative detail data unchanged.
      assertSame(data, view.selected());
      for (String status : List.of("CANCELLED", "DELIVERED")) {
        data.getAsJsonObject("summary").addProperty("status", status);
        legacy.showDetails(legacy.loading(), data);
        assertFalse(step(view, "READY").isEnabled());
      }
      view.setProductionUpdater(null);
      assertFalse(step(view, "READY").isEnabled());
    });
  }

  @Test void allStagesDispatchTheirExactCodesAndReadOnlyStepsCannotWrite() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      AtomicReference<String> chosen = new AtomicReference<>();
      var steps = new OrdersProductionSteps("READY", chosen::set, true);
      for (String status : List.of("NOT_STARTED", "STARTED", "WAITING_FOR_APPROVAL", "DESIGN_APPROVED", "PRINTED", "FINISHED", "QUALITY_CHECKED", "READY")) {
        step(steps, status).doClick();
        assertEquals(status, chosen.get());
      }
      chosen.set(null);
      step(new OrdersProductionSteps("READY", chosen::set, false), "PRINTED").doClick();
      assertNull(chosen.get());
    });
  }
}
