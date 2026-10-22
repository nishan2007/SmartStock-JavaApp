package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderCallStatusTest {
    @Test void callsPersistAndAuditWithoutDeliveringOrChangingProduction() throws Exception {
        for(String status:List.of("CALLED","CALLED_NO_ANSWER")) {
            FakeDb db=new FakeDb();
            LanCustomOrderWorkflowService.recordLineCall(db.connection(),9,List.of(12L,12L),status,"Spoke to customer",3,7,"Clerk","device","Till");
            assertEquals(3,db.writes.size());
            assertTrue(db.writes.get(0).startsWith("UPDATE custom_order_lines SET delivery_status=?"));
            assertEquals(status,db.bindings.get(0).get(1));
            assertEquals(12L,db.bindings.get(0).get(3));
            assertEquals("CUSTOMER_CALL",db.bindings.get(1).get(2));
            assertEquals("PENDING",db.bindings.get(1).get(4));
            assertEquals(status,db.bindings.get(1).get(5));
            assertEquals("Clerk",db.bindings.get(1).get(8));
            assertTrue(db.bindings.get(1).get(6).toString().contains("Line #12"));
            assertTrue(db.writes.stream().noneMatch(sql->sql.contains("delivered_at")||sql.contains("production_status")||sql.contains("line_deliveries")));
        }
    }
    @Test void terminalAndForeignLinesAreRejected() {
        for(String state:List.of("DELIVERED","CANCELLED")) {
            FakeDb db=new FakeDb();db.orderStatus=state;
            assertThrows(SQLException.class,()->call(db));assertTrue(db.writes.isEmpty());
        }
        for(String state:List.of("DELIVERED","FULL","MISSING")) {
            FakeDb db=new FakeDb();
            if(state.equals("FULL"))db.returnStatus=state;else if(state.equals("MISSING"))db.lineExists=false;else db.deliveryStatus=state;
            assertThrows(SQLException.class,()->call(db));assertTrue(db.writes.isEmpty());
        }
        FakeDb db=new FakeDb();db.orderExists=false;
        assertThrows(SQLException.class,()->call(db));assertTrue(db.writes.isEmpty());
    }
    @Test void freshAndExistingStoresAcceptBothCallOutcomes() throws Exception {
        String migration="database/migrations/v1_after/20261019160000_custom_order_call_status.sql";
        assertTrue(SchemaContractService.localContractResources().contains(migration));
        for(String path:List.of("database/v1/local/001_schema.sql",migration)) {
            String sql=SqlScriptRunner.readResource(path);
            assertTrue(sql.contains("custom_order_lines_delivery_status_chk"));
            assertTrue(sql.contains("'CALLED'"));assertTrue(sql.contains("'CALLED_NO_ANSWER'"));
        }
    }
    @Test void onlyCallOutcomesAreAccepted() {
        for(String status:Arrays.asList(null,"","DELIVERED","PENDING","OTHER"))
            assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.validateCallStatus(status));
    }
    private void call(FakeDb db)throws SQLException {
        LanCustomOrderWorkflowService.recordLineCall(db.connection(),9,List.of(12L),"CALLED","",3,7,"Clerk","device","Till");
    }
    @SuppressWarnings("unchecked")
    private static <T>T proxy(Class<T> type,java.lang.reflect.InvocationHandler handler) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);
    }
    private static class FakeDb {
        String orderStatus="READY",deliveryStatus="PENDING",returnStatus="NONE";
        boolean orderExists=true,lineExists=true;
        List<String>writes=new ArrayList<>();List<Map<Integer,Object>>bindings=new ArrayList<>();
        Connection connection() {
            return proxy(Connection.class,(p,m,a)->switch(m.getName()) {
                case "prepareStatement" -> statement((String)a[0]);
                case "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            });
        }
        PreparedStatement statement(String sql) {
            Map<Integer,Object> values=new HashMap<>();
            return proxy(PreparedStatement.class,(p,m,a)->{
                if(m.getName().startsWith("set")){values.put((Integer)a[0],a[1]);return null;}
                return switch(m.getName()) {
                    case "executeQuery" -> rows(sql);
                    case "executeUpdate" -> {writes.add(sql);bindings.add(new HashMap<>(values));yield 1;}
                    case "close" -> null;
                    default -> throw new UnsupportedOperationException(m.getName());
                };
            });
        }
        ResultSet rows(String sql) {
            boolean order=sql.contains("FROM custom_orders");boolean[] first={true};
            return proxy(ResultSet.class,(p,m,a)->switch(m.getName()) {
                case "next" -> {boolean result=first[0]&&(order?orderExists:lineExists);first[0]=false;yield result;}
                case "getString" -> order?orderStatus:((Integer)a[0]==1?deliveryStatus:returnStatus);
                case "getBigDecimal" -> BigDecimal.ZERO;
                case "close" -> null;
                default -> throw new UnsupportedOperationException(m.getName());
            });
        }
    }
}
