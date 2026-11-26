package services;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.lang.reflect.*;
import java.sql.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opted-in disposable database; never reads store connection settings. */
@EnabledIfSystemProperty(named="smartstock.test.orderDatabaseUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/customer_order_test")
class ExistingCustomerOrderDatabaseTest {
    private static final Gson JSON=LanJson.create();
    private static final String DEVICE="00000000-0000-0000-0000-000000000991";

    @Test void createsOrdersForAnExistingAccount()throws Exception {
        try(var c=DriverManager.getConnection(System.getProperty("smartstock.test.orderDatabaseUrl"),"postgres","")) {
            try(var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS storefront CASCADE; DROP SCHEMA public CASCADE; CREATE SCHEMA public");}
            SchemaContractService.installLocalBaseline(c);
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.execute("INSERT INTO locations(location_id,name) VALUES(991,'Order test store')");
                s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(991,'order-test','Order Tester',1,true)");
                s.execute("INSERT INTO devices(device_id,installation_id,device_name,last_store_id,allow_orders,is_blocked) VALUES('"+DEVICE+"','order-test','Order test register',991,true,false)");
                s.execute("INSERT INTO company_customization(location_id) VALUES(991)");
                s.execute("INSERT INTO customer_accounts(customer_id,name,phone,account_number) VALUES(991,'Existing Customer','7058194','CA-991')");
                ServerRequestIdentity.bind(991,991,"Order test store","Order Tester",DEVICE,"Order test register");
                var submitted=JSON.fromJson("""
                    {selectedCustomer:{customerId:991},customerName:'Existing Customer',customerPhone:'7058194',
                     amountPaid:0,applyCustomerDiscount:true,
                     lines:[{itemName:'Test order',customizationDetails:'Test customization',pricingType:'FIXED',unitPrice:1000,baseItemPrice:1000,
                       originalLineTotal:1000,minimumDepositPercent:0,printCharge:0,printLineCount:0,printAddons:[]}]}
                    """,ServerCustomOrderDataService.OrderSaveRequest.class);
                // Typing an owned number without selecting its account is rejected.
                var unselected=JSON.toJsonTree(submitted).getAsJsonObject();unselected.remove("selectedCustomer");
                var point=c.setSavepoint();
                var duplicate=JSON.fromJson(unselected,ServerCustomOrderDataService.OrderSaveRequest.class);
                assertEquals("23505",assertThrows(SQLException.class,()->ServerCustomOrderDataService.saveCustomOrder(c,trusted(c,duplicate))).getSQLState());
                c.rollback(point);
                // Selecting the account after that rejection must create the order against that account.
                String number=ServerCustomOrderDataService.saveCustomOrder(c,trusted(c,submitted));
                try(var p=c.prepareStatement("SELECT customer_id,customer_phone FROM custom_orders WHERE order_number=?")) {
                    p.setString(1,number);try(var r=p.executeQuery()){assertTrue(r.next());assertEquals(991,r.getInt(1));assertEquals("+5927058194",r.getString(2));}
                }
                try(var r=s.executeQuery("SELECT count(*) FROM customer_accounts")){r.next();assertEquals(1,r.getInt(1));}

                unselected.addProperty("customerName","Walk-in customer");
                unselected.addProperty("customerPhone","");
                unselected.addProperty("amountPaid",400);
                unselected.addProperty("paymentMethod","CARD");
                unselected.addProperty("paymentReference","test-deposit");
                var oneTime=JSON.fromJson(unselected,ServerCustomOrderDataService.OrderSaveRequest.class);
                String oneTimeNumber=ServerCustomOrderDataService.saveCustomOrder(c,trusted(c,oneTime,true),true);
                try(var p=c.prepareStatement("""
                        SELECT customer_id, customer_name, customer_phone, balance_due,
                            (SELECT count(*) FROM custom_order_payments p WHERE p.custom_order_id=o.custom_order_id),
                            (SELECT count(*) FROM customer_account_transactions t WHERE t.custom_order_id=o.custom_order_id)
                        FROM custom_orders o WHERE order_number=?
                        """)) {
                    p.setString(1,oneTimeNumber);
                    try(var r=p.executeQuery()) {
                        assertTrue(r.next());
                        assertNull(r.getObject(1));
                        assertEquals("Walk-in customer",r.getString(2));
                        assertEquals("",r.getString(3));
                        assertEquals(600,r.getInt(4));
                        assertEquals(1,r.getInt(5));
                        assertEquals(0,r.getInt(6));
                    }
                }
                try(var r=s.executeQuery("SELECT count(*) FROM customer_accounts")){r.next();assertEquals(1,r.getInt(1));}
            }finally{ServerRequestIdentity.clear();c.rollback();}
        }
    }

    private static ServerCustomOrderDataService.OrderSaveRequest trusted(Connection c,ServerCustomOrderDataService.OrderSaveRequest request)throws Exception {
        return trusted(c,request,false);
    }

    private static ServerCustomOrderDataService.OrderSaveRequest trusted(Connection c,ServerCustomOrderDataService.OrderSaveRequest request,boolean oneTimeCustomer)throws Exception {
        Constructor<?> ctor=LanApiServer.class.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Object server=ctor.newInstance(new Object[ctor.getParameterCount()]);
        Method method=Arrays.stream(LanApiServer.class.getDeclaredMethods()).filter(m->m.getName().equals("trustedCustomOrderRequest")).findFirst().orElseThrow();method.setAccessible(true);
        Class<?>[] types=method.getParameterTypes();
        Object device=JSON.fromJson("{deviceId:'"+DEVICE+"'}",types[1]);
        Object session=JSON.fromJson("{userId:991,locationId:991}",types[2]);
        Object user=JSON.fromJson("{userId:991,username:'order-test',fullName:'Order Tester',role:'ADMIN',locationId:991,locationName:'Order test store'}",types[3]);
        try{return (ServerCustomOrderDataService.OrderSaveRequest)method.invoke(server,c,device,session,user,request,oneTimeCustomer);}
        catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}
    }
}
