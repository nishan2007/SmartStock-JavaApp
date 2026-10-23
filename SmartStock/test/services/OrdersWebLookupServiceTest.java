package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OrdersWebLookupServiceTest {
    @Test void invalidLabelsNeverReachTheDatabase() {
        for(String value:new String[]{null,""," ","x".repeat(161),"CO-1\nCO-2"})
            assertThrows(IllegalArgumentException.class,()->OrdersWebLookupService.lookup(null,7,value));
    }
    @Test void exactLookupIsStoreScopedAndIncludesHistoricalOrders()throws Exception {
        var sql=new AtomicReference<String>();Map<Integer,Object> bindings=new HashMap<>();
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)->m.getName().equals("next")?false:null);
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->{
            if(m.getName().equals("setInt")||m.getName().equals("setString"))bindings.put((Integer)a[0],a[1]);
            return m.getName().equals("executeQuery")?rows:null;
        });
        Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("prepareStatement")){sql.set((String)a[0]);return statement;}return null;
        });
        assertNull(OrdersWebLookupService.lookup(connection,7," CO-1042 "));
        assertEquals(7,bindings.get(1));assertEquals("CO-1042",bindings.get(2));
        assertTrue(sql.get().contains("location_id=?"));assertTrue(sql.get().contains("order_type='CUSTOM'"));assertTrue(sql.get().contains("order_number=?"));
        assertFalse(sql.get().contains("INTERVAL"));assertFalse(sql.get().contains("LIKE"));
    }
}
