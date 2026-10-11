package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderAllOrdersTest {
    @Test void allOrdersIncludesHistoryAndUsesCreationDateWithinStore() throws Exception {
        AtomicReference<String> sql = new AtomicReference<>();
        Map<Integer,Object> parameters = new HashMap<>();
        ResultSet rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p,m,a) -> m.getName().equals("next") ? false : null);
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p,m,a) -> {
                    if(m.getName().startsWith("set")) parameters.put((Integer)a[0],a[1]);
                    return m.getName().equals("executeQuery") ? rows : null;
                });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (p,m,a) -> {
                    if(m.getName().equals("prepareStatement")){sql.set((String)a[0]);return statement;}
                    return null;
                });
        assertTrue(LanCustomOrderWorkflowService.allOrders(connection,7," Alice ").isEmpty());
        assertTrue(sql.get().contains("WHERE location_id=?"));
        assertTrue(sql.get().contains("created_at DESC,custom_order_id DESC"));
        assertFalse(sql.get().contains("due_date NULLS LAST"));
        assertFalse(sql.get().contains("status NOT IN"));
        assertEquals(7, parameters.get(1));
        assertEquals("Alice", parameters.get(4));
        assertEquals("%Alice%", parameters.get(5));
        assertEquals(Integer.MAX_VALUE, parameters.get(9));
        LanCustomOrderWorkflowService.lookupOrders(connection,7,"",100);
        assertTrue(sql.get().contains("AND (status<>'DELIVERED' OR COALESCE(balance_due,0)>0 OR pricing_state='PENDING')"));
        assertTrue(sql.get().contains("AND status<>'CANCELLED'"));
        assertEquals(7,parameters.get(1));
        LanCustomOrderWorkflowService.lookupOrders(connection,7,"Alice",100);
        assertFalse(sql.get().contains("status<>'DELIVERED'"));
        LanCustomOrderWorkflowService.currentAssignedOrders(connection,7,12);
        assertTrue(sql.get().contains("status<>'DELIVERED' OR COALESCE(balance_due,0)>0"));
        assertEquals(12,parameters.get(3));
    }
}
