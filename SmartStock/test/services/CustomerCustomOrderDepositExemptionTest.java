package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerCustomOrderDepositExemptionTest {
    private static final Gson JSON = LanJson.create();

    @Test void accountSaveRequiresDepositPermissionAndPreservesOlderClients() throws Exception {
        FakeDb db = new FakeDb();
        JsonObject body = JSON.fromJson("{customerId:42,accountNumber:'CA42',name:'Customer',phone:'6000000',active:true}", JsonObject.class);
        body.addProperty("customOrderDepositExempt", true);
        var denied = assertThrows(LanCustomerAccountService.RuleViolation.class,
                () -> LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1));
        assertEquals(403, denied.status());
        assertNull(db.savedExempt);
        db.canExempt = true;
        LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1);
        assertEquals(true, db.savedExempt);
        db.exempt = true;
        db.canExempt = false;
        body.remove("customOrderDepositExempt");
        LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1);
        assertEquals(true, db.savedExempt); // An older client must not silently revoke it.
        body.addProperty("customOrderDepositExempt", false);
        assertThrows(LanCustomerAccountService.RuleViolation.class,
                () -> LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1));
        db.canExempt = true;
        LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1);
        assertEquals(false, db.savedExempt);
    }

    @Test void creationDefaultsToNormalDepositAndRejectsUnauthorizedExemption() throws Exception {
        FakeDb db = new FakeDb();
        JsonObject body = JSON.fromJson("{name:'Customer',phone:'6000000',active:true}", JsonObject.class);
        LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1);
        assertEquals(false, db.savedExempt);
        body.addProperty("customOrderDepositExempt", true);
        assertThrows(LanCustomerAccountService.RuleViolation.class,
                () -> LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1));
        db.canExempt = true;
        LanCustomerAccountService.save(db.connection(), body, UUID.randomUUID(), 1);
        assertEquals(true, db.savedExempt);
    }

    @Test void orderUsesStoredPolicyAndKeepsTheEntireUnpaidBalance() throws Exception {
        FakeDb db = new FakeDb();
        var request = JSON.fromJson("""
                {selectedCustomer:{customerId:42,customOrderDepositExempt:true},customerPhone:'6000000',
                 amountPaid:0,lines:[{customItemId:1,itemName:'Shirt',pricingType:'FIXED',unitPrice:1000}]}
                """, ServerCustomOrderDataService.OrderSaveRequest.class);
        // A forged client flag cannot waive the server's normal minimum.
        var failure = assertThrows(InvocationTargetException.class, () -> trusted(db, request));
        assertTrue(failure.getCause().getMessage().contains("deposit override reason"));
        db.exempt = true;
        var saved = trusted(db, request);
        assertEquals(0, saved.minimumDepositRequired().signum());
        assertEquals(0, saved.amountPaid().signum());
        assertEquals(0, new BigDecimal("1000").compareTo(saved.balanceDue()));
        assertEquals("UNPAID", saved.paymentStatus());
        // The stored policy also wins when an older client's flag is false.
        var olderRequest = JSON.fromJson(JSON.toJson(request).replace("\"customOrderDepositExempt\":true", "\"customOrderDepositExempt\":false"), ServerCustomOrderDataService.OrderSaveRequest.class);
        assertEquals(0, trusted(db, olderRequest).minimumDepositRequired().signum());
    }

    private static ServerCustomOrderDataService.OrderSaveRequest trusted(FakeDb db, ServerCustomOrderDataService.OrderSaveRequest request) throws Exception {
        Constructor<?> ctor = LanApiServer.class.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object server = ctor.newInstance(new Object[ctor.getParameterCount()]);
        Method method = Arrays.stream(LanApiServer.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("trustedCustomOrderRequest")).findFirst().orElseThrow();
        method.setAccessible(true);
        Class<?>[] types = method.getParameterTypes();
        Object device = JSON.fromJson("{deviceId:'00000000-0000-0000-0000-000000000001'}", types[1]);
        Object session = JSON.fromJson("{userId:1,locationId:1}", types[2]);
        Object user = JSON.fromJson("{userId:1,username:'test',fullName:'Test',locationId:1,locationName:'Test'}", types[3]);
        return (ServerCustomOrderDataService.OrderSaveRequest) method.invoke(server, db.connection(), device, session, user, request);
    }

    /** Narrow JDBC fixture: no application configuration or live database access. */
    private static class FakeDb {
        boolean exempt, canExempt;
        Boolean savedExempt;
        Connection connection() {
            return proxy(Connection.class, (p,m,a) -> m.getName().equals("prepareStatement") ? statement((String)a[0]) : zero(m.getReturnType()));
        }
        PreparedStatement statement(String sql) {
            Map<Integer,Object> args = new HashMap<>();
            return proxy(PreparedStatement.class, (p,m,a) -> {
                if (m.getName().startsWith("set")) { args.put((Integer)a[0], a[1]); return null; }
                if (m.getName().equals("executeUpdate")) {
                    if (sql.startsWith("UPDATE customer_accounts SET account_number")) savedExempt = (Boolean)args.get(25);
                    return 1;
                }
                if (!m.getName().equals("executeQuery")) return zero(m.getReturnType());
                List<Object> row;
                if (sql.contains("JOIN role_permissions")) {
                    boolean allowed = "CUSTOMER_ACCOUNTS".equals(args.get(2)) || (canExempt && "CUSTOM_ORDER_DEPOSIT_OVERRIDE".equals(args.get(2)));
                    return rows(allowed ? List.of(1) : null);
                } else if (sql.contains("SELECT EXISTS(SELECT 1 FROM customer_phone_ownership")) {
                    row = List.of(false);
                } else if (sql.startsWith("SELECT account_number,credit_limit")) {
                    row = Arrays.asList("CA42", BigDecimal.ZERO, "", false, BigDecimal.ZERO, false, BigDecimal.ZERO, false, BigDecimal.ZERO, exempt);
                } else if (sql.contains("INSERT INTO customer_accounts")) {
                    savedExempt = (Boolean)args.get(22); row = List.of(42,"CA42");
                } else if (sql.contains("SELECT customer_id,name")) {
                    row = List.of(42,"Customer","CA42","",false,false,BigDecimal.ZERO,exempt);
                } else if (sql.contains("round_custom_orders_to_nearest_twenty")) row = List.of(false);
                else if (sql.contains("custom_order_minimum_deposit_percent")) row = List.of(new BigDecimal("50"));
                else if (sql.contains("FROM devices")) row = List.of("Test register");
                else throw new AssertionError("Unexpected query: " + sql);
                return rows(row);
            });
        }
        ResultSet rows(List<Object> row) {
            boolean[] first = {true};
            return proxy(ResultSet.class, (p,m,a) -> {
                if (m.getName().equals("next")) { boolean next = first[0] && row != null; first[0] = false; return next; }
                if (m.getName().startsWith("get")) return row.get((Integer)a[0]-1);
                return zero(m.getReturnType());
            });
        }
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) { return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler)); }
    private static Object zero(Class<?> type) { if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null; }
}
