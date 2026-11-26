package ui.screens.customorders;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class OrdersWorkspaceTest {
  @Test void deliveredAccountOrdersLeaveCurrentQueueButRemainInHistory()throws Exception{
    ListView v=list(3);
    SwingUtilities.invokeAndWait(()->{
      for(int row=0;row<3;row++)v.model.setValueAt("ACCOUNT / UNPAID",row,v.model.findColumn("Payment"));
      v.model.setValueAt("DELIVERED",0,v.model.findColumn("Status"));
      v.model.setValueAt("DELIVERED",1,v.model.findColumn("Status"));
      v.model.setValueAt("Price pending",1,v.model.findColumn("Total"));
      v.panel.refreshModel();
      assertFalse(v.panel.accepts(0));
      assertTrue(v.panel.accepts(1));
      assertTrue(v.panel.accepts(2));
      assertEquals(2,v.table.getRowCount());
      combo(v.panel,"Current orders").setSelectedItem("DELIVERED");
      assertEquals(2,v.table.getRowCount());
      combo(v.panel,"Current orders").setSelectedItem("All statuses");
      assertEquals(3,v.table.getRowCount());
    });
  }

  @Test void queueStartsWideEnoughAndScalesWithTheWindow(){
    assertEquals(660,CustomOrdersLookupTabPanel.queueWidth(1280));
    assertEquals(761,CustomOrdersLookupTabPanel.queueWidth(1902));
    assertTrue(CustomOrdersLookupTabPanel.queueWidth(1902)>400);
  }

  @Test void queueDividerIsRepositionedAfterTheWindowReachesItsFinalWidth()throws Exception{
    AtomicReference<CustomOrdersLookupTabPanel> view=new AtomicReference<>();
    SwingUtilities.invokeAndWait(()->{
      var handler=(CustomOrdersLookupTabPanel.Handler)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CustomOrdersLookupTabPanel.Handler.class},(proxy,method,args)->method.getReturnType()==boolean.class?false:null);
      var panel=new CustomOrdersLookupTabPanel(handler);view.set(panel);panel.setSize(400,800);panel.doLayout();panel.setSize(1902,900);panel.doLayout();
    });flush();SwingUtilities.invokeAndWait(()->{JSplitPane split=(JSplitPane)view.get().getComponent(0);assertEquals(761,split.getDividerLocation());assertTrue(split.getDividerSize()>1);});
  }

  @Test void currentOrdersHideCancelledAndDeliveredSettledOrdersAndHistoryRemainsAvailable()throws Exception{
    ListView v=list(6);
    SwingUtilities.invokeAndWait(()->{
      v.model.setValueAt("DELIVERED",0,v.model.findColumn("Status"));
      v.model.setValueAt("0",0,v.model.findColumn("Balance"));
      v.model.setValueAt("DELIVERED",1,v.model.findColumn("Status"));
      v.model.setValueAt("READY",2,v.model.findColumn("Status"));
      v.model.setValueAt("0",2,v.model.findColumn("Balance"));
      v.model.setValueAt("DELIVERED",3,v.model.findColumn("Status"));
      v.model.setValueAt("Price pending",3,v.model.findColumn("Total"));
      v.model.setValueAt("Price pending",3,v.model.findColumn("Balance"));
      v.model.setValueAt("CANCELLED",4,v.model.findColumn("Status"));
      v.panel.refreshModel();
      assertEquals(4,v.table.getRowCount());
      assertFalse(v.panel.accepts(0));assertTrue(v.panel.accepts(1));assertTrue(v.panel.accepts(2));assertTrue(v.panel.accepts(3));
      assertFalse(v.panel.accepts(4));
      assertEquals("All orders (4)",toggle(v.panel,"All orders").getText());
      combo(v.panel,"Current orders").setSelectedItem("All statuses");
      assertEquals(6,v.table.getRowCount());
      combo(v.panel,"Current orders").setSelectedItem("CANCELLED");
      assertEquals(1,v.table.getRowCount());
      combo(v.panel,"Current orders").setSelectedItem("DELIVERED");
      assertEquals(3,v.table.getRowCount());
      combo(v.panel,"Current orders").setSelectedIndex(0);assertEquals(4,v.table.getRowCount());
    });
  }
  @Test
  void pricingAndBillingPresentationUseTheAuthoritativeFinancialFields() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var legacy = new CustomOrderDetailsPanel(enabled -> {});
          var workspace = new OrdersDetailWorkspace(legacy);
          JsonObject data = CustomOrderDetailsPanelTest.fixture("100", true);
          JsonObject line = data.getAsJsonArray("lines").get(0).getAsJsonObject();
          line.addProperty("unit_price", 500);
          line.addProperty("line_total", 400);
          data.getAsJsonObject("summary").addProperty("loyalty_discount_amount", 100);
          legacy.showDetails(legacy.loading(), data);
          assertEquals("G$500", workspace.items.getValueAt(0, 4));
          assertEquals("G$400", workspace.items.getValueAt(0, 5));
          assertFalse(legacy.canPay());
          assertTrue(hasLabel(workspace, "Billed on invoice"));
          assertTrue(hasLabel(workspace, "Loyalty discount"));
          assertTrue(hasLabel(workspace, "G$14,020"));
        });
  }

  private boolean hasLabel(Container parent, String text) {
    for (Component c : parent.getComponents()) {
      if (c instanceof JLabel label && text.equals(label.getText())) return true;
      if (c instanceof Container child && hasLabel(child, text)) return true;
    }
    return false;
  }

  @Test
  void workspaceActionsUseTheSelectedOrderAndTheExistingFinancialHandler() throws Exception {
    java.util.List<String> actions = new java.util.ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> {
          var handler =
              (CustomOrdersLookupTabPanel.Handler)
                  java.lang.reflect.Proxy.newProxyInstance(
                      getClass().getClassLoader(),
                      new Class<?>[] {CustomOrdersLookupTabPanel.Handler.class},
                      (proxy, method, args) -> {
                        switch (method.getName()) {
                          case "canCreateOrders",
                              "canAssignOrders",
                              "canUpdateProduction",
                              "canDeliverOrderLines",
                              "canRefundPayments",
                              "canReversePayments",
                              "canCancelOrders",
                              "canExchangeItems",
                              "canFinalizePrice":
                            return true;
                          case "selectedLookupOrderId":
                            {
                              JTable table = (JTable) args[0];
                              DefaultTableModel model = (DefaultTableModel) args[1];
                              return table.getSelectedRow() < 0
                                  ? null
                                  : ((Number)
                                          model.getValueAt(
                                              table.convertRowIndexToModel(table.getSelectedRow()),
                                              0))
                                      .longValue();
                            }
                          case "parseNullableMoneyValue":
                            return new java.math.BigDecimal(args[0].toString());
                          case "loadStructuredOrderDetails":
                            {
                              CustomOrderDetailsPanel details = (CustomOrderDetailsPanel) args[1];
                              JsonObject data = CustomOrderDetailsPanelTest.fixture("20000", false);
                              data.getAsJsonObject("summary").addProperty("status", "IN_PROGRESS");
                              details.showDetails(details.loading(), data);
                              return null;
                            }
                          case "applyLookupPayment":
                            assertEquals(42L, args[0]);
                            assertEquals("20,000", args[1]);
                            assertEquals("Card", args[2]);
                            assertEquals("REF-42", args[3]);
                            actions.add("payment");
                            ((java.util.function.Consumer<Boolean>) args[5]).accept(false);
                            return null;
                          case "newOrder":
                            actions.add("new");
                            return null;
                          case "exchangeItem":
                            assertEquals(42L,args[0]);
                            actions.add("exchange");
                            ((java.util.function.Consumer<Boolean>)args[1]).accept(false);
                            return null;
                          case "previewOrderSlip":
                            assertEquals("CO-42", args[0]);
                            actions.add("preview");
                            return null;
                          case "openMedia":
                            assertEquals(42L, args[0]);
                            actions.add("files");
                            return null;
                          case "changeAssignment":
                            assertEquals(42L, args[0]);
                            actions.add("assignment");
                            return null;
                          default:
                            return null;
                        }
                      });
          var panel = new CustomOrdersLookupTabPanel(handler);
          panel.model.addRow(
              new Object[] {
                42L,
                "CO-42",
                "IN_PROGRESS",
                "Customer",
                "600-0042",
                LocalDate.of(2026, 10, 27),
                "30000",
                "10000",
                "20000",
                "CASH",
                "",
                new java.util.Date(),
                "Tester",
                "Cashier",
                "READY",
                "PENDING",
                7
              });
          panel.orderList.refreshModel();
          panel.table.setRowSelectionInterval(0, 0);
          panel.orderList.newOrder.doClick();
          button(panel.orderDetails,"Exchange item").doClick();
          button(panel, "Preview Slip").doClick();
          button(panel, "Files & Design Approval").doClick();
          button(panel, "Change").doClick();
          panel.methodBox.setSelectedItem("Card");
          assertTrue(panel.referenceField.isEnabled());
          panel.referenceField.setText("REF-42");
          button(panel, "Apply Payment").doClick();
          panel.methodBox.setSelectedItem("Cash");
          assertFalse(panel.referenceField.isEnabled());
          assertEquals("", panel.referenceField.getText());
          assertEquals(
              java.util.List.of("new", "exchange", "preview", "files", "assignment", "payment"), actions);
          panel.table.clearSelection();
          assertFalse(button(panel, "Apply Payment").isEnabled());
          assertNull(panel.workspace.selected());
        });
    flush();
  }

  private Integer previousUser;
  private String previousName;

  @org.junit.jupiter.api.BeforeEach
  void rememberSession() {
    previousUser = managers.SessionManager.getCurrentUserId();
    previousName = managers.SessionManager.getCurrentUserDisplayName();
  }

  @org.junit.jupiter.api.AfterEach
  void restoreSession() throws Exception {
    flush();
    managers.SessionManager.setCurrentUserId(previousUser);
    managers.SessionManager.setCurrentUserDisplayName(previousName);
  }

  private record ListView(DefaultTableModel model, JTable table, OrdersListWorkspace panel) {}

  private ListView list(int count) throws Exception {
    AtomicReference<ListView> result = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          managers.SessionManager.setCurrentUserId(70);
          managers.SessionManager.setCurrentUserDisplayName("Same name");
          DefaultTableModel model =
              new DefaultTableModel(CustomOrderListColumns.workspaceHeadings(), 0);
          JTable table = new JTable(model);
          CustomOrderListColumns.installSorting(
              table, new javax.swing.table.TableRowSorter<>(model));
          OrdersListWorkspace panel = new OrdersListWorkspace(table, model, new JTextField());
          for (int i = 0; i < count; i++)
            model.addRow(
                new Object[] {
                  (long) i + 1,
                  "CO-" + (i + 1),
                  "IN_PROGRESS",
                  "Customer " + i,
                  "600-0100",
                  LocalDate.of(2026, 10, 27),
                  "30000",
                  "10000",
                  "20000",
                  "CASH / PARTIAL",
                  "",
                  new java.util.Date(),
                  i == 2 ? "" : "Same name",
                  "Cashier",
                  i == 1 ? "STARTED,DESIGN_APPROVED" : "READY",
                  i == 1 ? "CALLED_NO_ANSWER" : "PENDING",
                  i == 2 ? null : i == 0 ? 70 : 80
                });
          result.set(new ListView(model, table, panel));
        });
    flush();
    return result.get();
  }

  private void flush() throws Exception {
    SwingUtilities.invokeAndWait(() -> {});
    SwingUtilities.invokeAndWait(() -> {});
  }

  @Test
  void filteringUsesAssignedUserIdentityAndAnyMatchingLineStage() throws Exception {
    ListView v = list(4);
    SwingUtilities.invokeAndWait(
        () -> {
          toggle(v.panel, "My orders").doClick();
          assertEquals(1, v.table.getRowCount());
          assertEquals(1L, v.model.getValueAt(v.table.convertRowIndexToModel(0), 0));
          toggle(v.panel, "All orders").doClick();
          combo(v.panel, "All production").setSelectedItem("DESIGN_APPROVED");
          assertEquals(1, v.table.getRowCount());
          assertEquals(2L, v.model.getValueAt(v.table.convertRowIndexToModel(0), 0));
          combo(v.panel, "All delivery").setSelectedItem("CALLED_NO_ANSWER");
          assertEquals(1, v.table.getRowCount());
          combo(v.panel, "All production").setSelectedIndex(0);
          combo(v.panel, "All delivery").setSelectedIndex(0);
          v.model.setValueAt("WAITING_FOR_APPROVAL", 0, v.model.findColumn("Production"));
          combo(v.panel, "Current orders").setSelectedItem("WAITING_FOR_APPROVAL");
          assertEquals(1, v.table.getRowCount());
          combo(v.panel, "Current orders").setSelectedIndex(0);
          toggle(v.panel, "Unassigned").doClick();
          assertEquals(1, v.table.getRowCount());
          assertEquals(3L, v.model.getValueAt(v.table.convertRowIndexToModel(0), 0));
        });
  }

  @Test
  void paginationSortsTheWholeResultAndKeepsSelectionWithinTheVisiblePage() throws Exception {
    ListView v = list(63);
    SwingUtilities.invokeAndWait(
        () -> {
          assertEquals(50, v.table.getRowCount());
          button(v.panel, "›").doClick();
          assertEquals(13, v.table.getRowCount());
          assertTrue(v.table.getSelectedRow() >= 0);
          assertEquals(51L, v.model.getValueAt(v.table.convertRowIndexToModel(0), 0));
          button(v.panel, "‹").doClick();
          v.table
              .getRowSorter()
              .setSortKeys(java.util.List.of(new RowSorter.SortKey(1, SortOrder.DESCENDING)));
        });
    flush();
    SwingUtilities.invokeAndWait(
        () -> {
          assertEquals(63L, v.model.getValueAt(v.table.convertRowIndexToModel(0), 0));
          button(v.panel, "›").doClick();
          assertEquals(13, v.table.getRowCount());
          assertEquals(13L, v.model.getValueAt(v.table.convertRowIndexToModel(0), 0));
        });
  }

  @Test
  void zeroResultsClearSelectionAndPaymentFiltersRespectPendingAndInvoiceOrders() throws Exception {
    ListView v = list(4);
    SwingUtilities.invokeAndWait(
        () -> {
          v.model.setValueAt("Price pending", 0, v.model.findColumn("Total"));
          v.model.setValueAt("INVOICE / BILLED ON INVOICE", 1, v.model.findColumn("Payment"));
          combo(v.panel, "All payment").setSelectedItem("Part paid");
          assertEquals(2, v.table.getRowCount());
          combo(v.panel, "All payment").setSelectedItem("Price pending");
          assertEquals(1, v.table.getRowCount());
          combo(v.panel, "All payment").setSelectedItem("Billed on invoice");
          assertEquals(1, v.table.getRowCount());
          combo(v.panel, "Current orders").setSelectedItem("CANCELLED");
          assertEquals(0, v.table.getRowCount());
          assertEquals(-1, v.table.getSelectedRow());
        });
  }

  @Test
  void staleDetailLoadsCannotReplaceTheWorkspaceOrReenablePayment() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          var legacy = new CustomOrderDetailsPanel(enabled -> {});
          var view = new OrdersDetailWorkspace(legacy);
          long old = legacy.loading(), current = legacy.loading();
          JsonObject data = CustomOrderDetailsPanelTest.fixture("100", false);
          legacy.showDetails(current, data);
          assertSame(data, view.selected());
          assertEquals(4, view.tabs.getTabCount());
          legacy.showDetails(old, CustomOrderDetailsPanelTest.fixture("0", false));
          legacy.failed(old, "stale");
          assertSame(data, view.selected());
          assertTrue(legacy.canPay());
          legacy.empty();
          assertNull(view.selected());
          assertEquals(0, view.items.getRowCount());
          assertFalse(legacy.canPay());
        });
  }

  @Test
  void proofSelectionNeverTreatsAnOldApprovedRevisionAsTheLatestProof() {
    JsonObject media =
        JsonParser.parseString(
                "{\"proofs\":[{\"lineId\":1,\"revision\":1,\"status\":\"APPROVED\"},{\"lineId\":1,\"revision\":2,\"status\":\"CHANGES_REQUESTED\"},{\"lineId\":2,\"revision\":1,\"status\":\"APPROVED\"}]}")
            .getAsJsonObject();
    var proofs = OrdersDetailWorkspace.latestProofs(media);
    assertEquals(2, proofs.size());
    assertEquals("CHANGES_REQUESTED", proofs.get(0).get("status").getAsString());
  }

  @SuppressWarnings("unchecked")
  private JComboBox<Object> combo(Container p, String first) {
    for (Component c : p.getComponents()) {
      if (c instanceof JComboBox<?> b && b.getItemCount() > 0 && first.equals(b.getItemAt(0)))
        return (JComboBox<Object>) b;
      if (c instanceof Container child) {
        JComboBox<Object> found = combo(child, first);
        if (found != null) return found;
      }
    }
    return null;
  }

  private JToggleButton toggle(Container p, String text) {
    for (Component c : p.getComponents()) {
      if (c instanceof JToggleButton b && b.getText().startsWith(text)) return b;
      if (c instanceof Container child) {
        var found = toggle(child, text);
        if (found != null) return found;
      }
    }
    return null;
  }

  private JButton button(Container p, String text) {
    for (Component c : p.getComponents()) {
      if (c instanceof JButton b && b.getText().equals(text)) return b;
      if (c instanceof Container child) {
        var found = button(child, text);
        if (found != null) return found;
      }
    }
    return null;
  }
}
