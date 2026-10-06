package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class CrossStoreEmployeeRoleGuardTest {
    @Test
    void missingRoleStopsEmployeeUpdateBeforeAnyWrite() {
        ResultSet result = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                    if (method.getName().equals("next")) return false;
                    if (method.getName().equals("close")) return null;
                    throw new AssertionError("Unexpected result operation: " + method.getName());
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "setString", "close" -> null;
                        case "executeQuery" -> result;
                        default -> throw new AssertionError("Unexpected statement operation: " + method.getName());
                    };
                });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    assertEquals("prepareStatement", method.getName());
                    assertTrue(((String) args[0]).startsWith("SELECT"), "Must not write an unresolved employee role");
                    return statement;
                });
        JsonObject row = new JsonObject();
        row.addProperty("user_id", 42);
        row.addProperty("role_name", "CUSTOM_CASHIER");
        row.addProperty("updated_at", "2026-09-30T00:00:00Z");
        JsonObject payload = new JsonObject();
        payload.addProperty("table_name", "users");
        payload.addProperty("operation", "UPSERT");
        payload.add("row_data", row);
        SQLException error = assertThrows(SQLException.class,
                () -> CrossStoreReferenceSyncService.applyPayload(connection, payload));
        assertTrue(error.getMessage().contains("CUSTOM_CASHIER"));
    }
}
