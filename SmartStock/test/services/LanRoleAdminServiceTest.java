package services;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LanRoleAdminServiceTest {
  @FunctionalInterface
  interface Operation {
    void run(Connection c) throws Exception;
  }

  @Test
  void everyRoleOperationRejectsUnauthorizedActorsBeforeAnyWrite() throws Exception {
    var body = new JsonObject();
    body.addProperty("roleId", 5);
    body.addProperty("name", "Custom role");
    body.addProperty("sourceRoleId", 1);
    body.addProperty("description", "Description");
    List<Operation> operations =
        List.of(
            c -> LanRoleAdminService.state(c, 7),
            c -> LanRoleAdminService.selected(c, body, 7),
            c -> LanRoleAdminService.save(c, body, 7, 1, UUID.randomUUID()),
            c -> LanRoleAdminService.add(c, body, 7, 1, UUID.randomUUID()));
    for (var operation : operations) {
      var writes = new ArrayList<String>();
      var failure =
          assertThrows(
              LanRoleAdminService.RuleViolation.class,
              () -> operation.run(connection(false, writes)));
      assertEquals(403, failure.status);
      assertEquals("PERMISSION_DENIED", failure.code);
      assertTrue(writes.isEmpty());
    }
  }

  @Test
  void oversizedDescriptionFailsBeforePolicyReplacement() throws Exception {
    var body = new JsonObject();
    body.addProperty("roleId", 5);
    body.addProperty("description", "x".repeat(2001));
    var writes = new ArrayList<String>();
    var failure =
        assertThrows(
            LanRoleAdminService.RuleViolation.class,
            () ->
                LanRoleAdminService.save(connection(true, writes), body, 7, 1, UUID.randomUUID()));
    assertEquals(400, failure.status);
    assertEquals("VALIDATION_ERROR", failure.code);
    assertTrue(writes.isEmpty());
  }

  private static Connection connection(boolean authorized, List<String> writes) {
    return (Connection)
        Proxy.newProxyInstance(
            LanRoleAdminServiceTest.class.getClassLoader(),
            new Class<?>[] {Connection.class},
            (proxy, method, args) -> {
              if (method.getName().equals("prepareStatement"))
                return statement((String) args[0], authorized, writes);
              if (method.getName().equals("close")) return null;
              throw new AssertionError("Unexpected connection operation: " + method.getName());
            });
  }

  private static PreparedStatement statement(String sql, boolean authorized, List<String> writes) {
    return (PreparedStatement)
        Proxy.newProxyInstance(
            LanRoleAdminServiceTest.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class},
            (proxy, method, args) -> {
              if (method.getName().startsWith("set") || method.getName().equals("close"))
                return null;
              if (method.getName().equals("executeUpdate")) {
                writes.add(sql);
                return 1;
              }
              if (method.getName().equals("executeQuery")) {
                boolean exists =
                    sql.contains("JOIN role_permissions")
                        ? authorized
                        : sql.startsWith("SELECT 1 FROM roles");
                if (!sql.contains("JOIN role_permissions")
                    && !sql.startsWith("SELECT 1 FROM roles"))
                  throw new AssertionError("Unexpected read: " + sql);
                int[] cursor = {0};
                return Proxy.newProxyInstance(
                    LanRoleAdminServiceTest.class.getClassLoader(),
                    new Class<?>[] {ResultSet.class},
                    (result, m, a) ->
                        switch (m.getName()) {
                          case "next" -> cursor[0]++ == 0 && exists;
                          case "close" -> null;
                          default -> throw new AssertionError(m.getName());
                        });
              }
              throw new AssertionError("Unexpected statement operation: " + method.getName());
            });
  }
}
