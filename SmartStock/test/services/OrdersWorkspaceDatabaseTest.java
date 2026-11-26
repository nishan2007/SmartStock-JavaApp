package services;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicit disposable database only; never opens the configured store database. */
@EnabledIfSystemProperty(
    named = "smartstock.test.ordersWorkspaceDatabaseUrl",
    matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/orders_workspace_test")
class OrdersWorkspaceDatabaseTest {
  @Test
  void listAndDetailsExposeActualLineStatesPricesAndAssignedIdentityWithinTheStore()
      throws Exception {
    try (var c =
        DriverManager.getConnection(
            System.getProperty("smartstock.test.ordersWorkspaceDatabaseUrl"), "postgres", "")) {
      try (var statement = c.createStatement()) {
        statement.execute(
            "DROP SCHEMA IF EXISTS storefront CASCADE; DROP SCHEMA public CASCADE; CREATE SCHEMA"
                + " public");
      }
      SchemaContractService.installLocalBaseline(c);
      c.setAutoCommit(false);
      try {
        try (var s = c.createStatement()) {
          s.execute(
              "INSERT INTO locations(location_id,name) VALUES(989,'Orders workspace test"
                  + " store'),(988,'Other workspace store')");
          s.execute(
              "INSERT INTO users(user_id,username,full_name,role_id,is_active)"
                  + " VALUES(989,'workspace-test','Workspace Tester',1,true)");
          s.execute(
              "INSERT INTO customer_accounts(customer_id,name,phone,account_number)"
                  + " VALUES(989,'Workspace Customer','600-0989','CA-WORKSPACE-989')");
          s.execute(
              "INSERT INTO"
                  + " custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,total_amount,amount_paid,balance_due,location_id,order_type,assigned_to_user_id,assigned_to_name)"
                  + " VALUES(989,'CO-WORKSPACE-989',989,'Workspace"
                  + " Customer','600-0989',5000,2000,3000,989,'CUSTOM',989,'Workspace"
                  + " Tester'),(988,'CO-OTHER-988',989,'Other"
                  + " Customer','600-0988',5000,0,5000,988,'CUSTOM',989,'Workspace Tester')");
          s.execute(
              "INSERT INTO"
                  + " custom_order_lines(custom_order_line_id,custom_order_id,item_name,pricing_type,unit_price,line_total,customization_details,production_status,delivery_status,return_status)"
                  + " VALUES(9891,989,'Navy polo','FIXED',2500,2400,'Chest"
                  + " logo','STARTED','PENDING','NONE'),(9892,989,'Navy"
                  + " polo','FIXED',2500,2600,'Back"
                  + " logo','DESIGN_APPROVED','CALLED_NO_ANSWER','NONE'),(9893,989,'Returned"
                  + " polo','FIXED',100,100,'','READY','DELIVERED','FULL')");
        }
        var list = LanCustomOrderWorkflowService.allOrders(c, 989, "");
        assertEquals(1, list.size());
        var order = list.get(0);
        assertEquals(989, order.assignedUserId());
        assertEquals(
            Set.of("STARTED", "DESIGN_APPROVED"), Set.of(order.productionStates().split(",")));
        assertEquals(
            Set.of("PENDING", "CALLED_NO_ANSWER"), Set.of(order.deliveryStates().split(",")));
        assertEquals(1, LanCustomOrderWorkflowService.allOrders(c, 989, "600-0989").size());
        assertTrue(LanCustomOrderWorkflowService.allOrders(c, 989, "CO-OTHER").isEmpty());
        var details = CustomOrderDetailsService.read(c, 989, 989);
        var lines = (java.util.List<?>) details.get("lines");
        assertEquals(3, lines.size());
        var line = (java.util.Map<?, ?>) lines.get(0);
        assertEquals(new java.math.BigDecimal("2500.00"), line.get("unit_price"));
        assertEquals(new java.math.BigDecimal("2400.00"), line.get("line_total"));
        assertThrows(
            java.sql.SQLException.class, () -> CustomOrderDetailsService.read(c, 989, 988));
      } finally {
        c.rollback();
      }
    }
  }
}
