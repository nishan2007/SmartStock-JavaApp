package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class OrdersDashboardQueuesTest {
    @Test void currentQueueIncludesPaidUndeliveredOrdersAndDeliveryHistoryIsOnDemand()throws Exception {
        var sql=new AtomicReference<String>();var location=new AtomicInteger();
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},
                (p,m,a)->m.getName().equals("next")?false:null);
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},
                (p,m,a)->{if(m.getName().equals("setInt"))location.set((Integer)a[1]);return m.getName().equals("executeQuery")?rows:null;});
        Connection c=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},
                (p,m,a)->{if(m.getName().equals("prepareStatement")){sql.set((String)a[0]);return statement;}return null;});
        var actions=LanOrdersDashboardService.class.getDeclaredMethod("actions",Connection.class,int.class);
        actions.setAccessible(true);actions.invoke(null,c,7);
        assertTrue(sql.get().contains("status NOT IN ('DELIVERED','CANCELLED')"));
        assertFalse(sql.get().contains("balance_due,0)>0"));
        assertFalse(sql.get().contains("LIMIT 100"));
        assertEquals(7,location.get());
        LanOrdersDashboardService.delivered(c,7,false);
        assertTrue(sql.get().contains("status='DELIVERED'"));
        assertTrue(sql.get().contains("delivered_at>=CURRENT_TIMESTAMP-INTERVAL '7 days'"));
        assertEquals(7,location.get());
        LanOrdersDashboardService.delivered(c,7,true);
        assertFalse(sql.get().contains("INTERVAL"));
        assertTrue(sql.get().contains("WHERE location_id=?"));
        assertTrue(sql.get().contains("ORDER BY delivered_at DESC"));
    }
}
