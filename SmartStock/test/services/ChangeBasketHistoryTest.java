package services;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChangeBasketHistoryTest {
    @Test void restoresSavedCountsAcrossTheWire() throws Exception {
        var state=LanCashOperationsService.changeBasketState(connection(true,1),7,42);
        var decoded=new Gson().fromJson(new Gson().toJson(state),LanApiClient.ChangeBasketState.class);
        assertEquals("Test store",decoded.storeName());
        assertEquals(448,decoded.latestUpdate().denominationCounts().get(100));
        assertEquals(new BigDecimal("60000"),decoded.latestUpdate().countedAmount());
        assertEquals("Diana",decoded.latestUpdate().updatedByName());
    }

    @Test void storeWithoutUpdatesHasNoSavedCount() throws Exception {
        assertFalse(LanCashOperationsService.changeBasketState(connection(true,0),7,42).containsKey("latestUpdate"));
        var history=LanCashOperationsService.changeBasketHistory(connection(true,0),7,42,0);
        assertEquals(List.of(),history.get("updates"));
        assertEquals(false,history.get("hasMore"));
    }

    @Test void pagesHistoryAndRejectsNegativeOffset() throws Exception {
        var history=LanCashOperationsService.changeBasketHistory(connection(true,101),7,42,100);
        assertEquals(100,((List<?>)history.get("updates")).size());
        assertEquals(true,history.get("hasMore"));
        assertEquals(400,assertThrows(LanCashOperationsService.RuleViolation.class,
                ()->LanCashOperationsService.changeBasketHistory(connection(true,0),7,42,-1)).status());
    }

    @Test void deniesStateAndHistoryBeforeReadingCounts() {
        assertEquals(403,assertThrows(LanCashOperationsService.RuleViolation.class,
                ()->LanCashOperationsService.changeBasketState(connection(false,1),7,42)).status());
        assertEquals(403,assertThrows(LanCashOperationsService.RuleViolation.class,
                ()->LanCashOperationsService.changeBasketHistory(connection(false,1),7,42,0)).status());
    }

    private static Connection connection(boolean allowed,int count) {
        return proxy(Connection.class,(method,args)-> {
            if(!method.equals("prepareStatement"))return null;
            String sql=(String)args[0];Map<Integer,Object> parameters=new HashMap<>();
            return proxy(PreparedStatement.class,(operation,values)-> {
                if(operation.startsWith("set")){parameters.put((Integer)values[0],values[1]);return null;}
                if(!operation.equals("executeQuery"))return null;
                boolean permission=sql.contains("role_permissions"),updates=sql.contains("FROM change_basket_updates");
                if(!permission)assertTrue(allowed,"Counts must not be read without permission");
                if(updates){assertEquals(42,parameters.get(1));assertTrue(sql.contains("ORDER BY updated_at DESC,change_basket_update_id DESC"));}
                int size=permission?(allowed?1:0):updates?Math.min(count,(Integer)parameters.get(2)):1;
                int[] index={0};
                return proxy(ResultSet.class,(read,arguments)-> {
                    if(read.equals("next"))return ++index[0]<=size;
                    if(read.equals("getLong"))return (long)index[0];
                    if(read.equals("getObject"))return OffsetDateTime.parse("2026-10-16T11:08:05-04:00");
                    if(read.equals("getBigDecimal"))return new BigDecimal("60000");
                    if(read.equals("getString"))return updates?switch((Integer)arguments[0]) {
                        case 3->"Diana";case 4->"Register";case 8->"{\"100\":448,\"500\":30,\"20\":10}";default->null;
                    }:"Test store";
                    return null;
                });
            });
        });
    }
    interface Call {Object run(String method,Object[] args)throws Throwable;}
    private static <T>T proxy(Class<T> type,Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(p,m,a)->call.run(m.getName(),a)));
    }
}
