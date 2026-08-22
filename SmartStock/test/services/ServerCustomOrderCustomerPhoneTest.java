package services;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerCustomOrderCustomerPhoneTest {
    @Test
    void selectedCustomerAlwaysReceivesThePhoneSubmittedWithTheOrder() throws Exception {
        AtomicReference<String> sql = new AtomicReference<>();
        Map<Integer, Object> parameters = new HashMap<>();
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setString", "setInt" -> { parameters.put((Integer) args[0], args[1]); yield null; }
                    case "executeUpdate" -> 1;
                    case "close" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName())) {
                        sql.set((String) args[0]);
                        return statement;
                    }
                    return defaultValue(method.getReturnType());
                });
        var selected = new ServerCustomOrderDataService.CustomerOption(
                42, "Customer", "old-number", "CA-42", "customer@example.com");

        int customerId = ServerCustomOrderDataService.resolveOrderCustomerId(
                connection, selected, "Customer", "new-number");

        assertEquals(42, customerId);
        assertTrue(sql.get().contains("UPDATE customer_accounts SET phone = ?"));
        assertEquals("new-number", parameters.get(1));
        assertEquals(42, parameters.get(2));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
