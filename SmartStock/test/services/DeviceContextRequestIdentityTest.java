package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DeviceContextRequestIdentityTest {
    @Test void orderCapabilityUsesTheAuthenticatedRegisterAndStillRejectsDisabledOrders() throws Exception {
        String register = "00000000-0000-0000-0000-000000000123";
        List<String> checkedDevices = new ArrayList<>();
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p,m,a) -> switch(m.getName()) {
                    case "next" -> true;
                    case "getBoolean" -> false;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                (p,m,a) -> switch(m.getName()) {
                    case "setString" -> { checkedDevices.add((String)a[1]); yield null; }
                    case "executeQuery" -> rows;
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        Connection connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p,m,a) -> {
                    if (m.getName().equals("prepareStatement")) return statement;
                    throw new UnsupportedOperationException(m.getName());
                });
        ServerRequestIdentity.bind(1,1,"Store","Cashier",register,"Register");
        try {
            SQLException failure = assertThrows(SQLException.class,
                    () -> DeviceContextService.requireOrdersAllowed(connection));
            assertTrue(failure.getMessage().contains("Enable Allow Orders"));
            assertEquals(List.of(register,register), checkedDevices);
        } finally { ServerRequestIdentity.clear(); }
    }
}
