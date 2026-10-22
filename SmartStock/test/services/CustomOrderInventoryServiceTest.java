package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderInventoryServiceTest {
    private static ServerCustomOrderDataService.OrderLineRequest line(String base,String total) {
        JsonObject j=new JsonObject();j.addProperty("inventoryProductId",7);j.addProperty("pricingType","FIXED");
        j.addProperty("itemName","Untrusted name");j.addProperty("baseItemPrice",base);j.addProperty("originalLineTotal",total);
        j.addProperty("printCharge",40);
        var addons=new com.google.gson.JsonArray();var addon=new JsonObject();addon.addProperty("printCharge",40);addons.add(addon);j.add("printAddons",addons);
        return LanJson.create().fromJson(j,ServerCustomOrderDataService.OrderLineRequest.class);
    }
    private static Object fallback(Class<?> type){if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null;}
    private static Connection database(int updates,boolean restocked,List<String> sql) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(!m.getName().equals("prepareStatement"))return fallback(m.getReturnType());
            String query=(String)a[0];sql.add(query);
            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},(s,op,args)->{
                if(op.getName().equals("executeUpdate"))return updates;
                if(op.getName().equals("executeQuery"))return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class[]{ResultSet.class},(r,read,values)->switch(read.getName()){
                    case "next"->true;
                    case "getBigDecimal"->new BigDecimal("100");
                    case "getString"->"Pen";
                    case "getObject"->7;
                    case "getBoolean"->restocked;
                    default->fallback(read.getReturnType());
                });
                return fallback(op.getReturnType());
            });
        });
    }
    @Test void usesServerIdentityAndRejectsUnapprovedPriceChangesAndWrongTotals() throws Exception {
        var c=database(1,false,new ArrayList<>());
        var trusted=CustomOrderInventoryService.validate(c,line("100","140"),false);
        assertEquals("Pen",trusted.itemName());assertEquals(new BigDecimal("100"),trusted.originalBasePrice());
        assertThrows(SQLException.class,()->CustomOrderInventoryService.validate(c,line("1","41"),false));
        assertThrows(SQLException.class,()->CustomOrderInventoryService.validate(c,line("100","101"),false));
    }
    @Test void rejectsOutOfStockWithoutRecordingMovement() {
        List<String> sql=new ArrayList<>();
        assertThrows(SQLException.class,()->CustomOrderInventoryService.consume(database(0,false,sql),1,2,7,1,1,"Staff","device","Register"));
        assertEquals(1,sql.size());assertTrue(sql.get(0).contains("quantity_on_hand>=1"));
    }
    @Test void earlierPartialRefundCannotRestockSameItemTwice() throws Exception {
        List<String> sql=new ArrayList<>();
        assertTrue(CustomOrderInventoryService.restock(database(1,true,sql),1,2,1,1,"Staff","device","Register","Return"));
        assertEquals(1,sql.size());
    }
    @Test void regularInventoryIdSurvivesClientServerAndHoldSerialization() {
        var client=LanJson.create().fromJson(LanJson.create().toJson(line("100","140")),CustomOrderDataService.OrderLineRequest.class);
        assertEquals(7,client.inventoryProductId());
        assertEquals(7,LanJson.create().fromJson(LanJson.create().toJson(client),ServerCustomOrderDataService.OrderLineRequest.class).inventoryProductId());
    }
}
