package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerBusinessTypeTest {
    @Test void newClientWritesOnlyTypeAndIgnoresLegacyBusinessProperty() {
        var request=LanJson.create().fromJson("{name:'Example',customerTypeId:9,business:true,active:true}",LanApiClient.CustomerAccountSaveRequest.class);
        var json=LanJson.create().toJsonTree(request).getAsJsonObject();
        assertEquals(9,json.get("customerTypeId").getAsInt());
        assertFalse(json.has("business"));
        assertTrue(utils.CustomerAccountTypes.isBusiness(" Businesses "));
        assertFalse(utils.CustomerAccountTypes.isBusiness("Individual"));
    }
    @Test void savePersistsOnlyCustomerTypeOnCreateAndUpdate() throws Exception {
        for(boolean update:new boolean[]{false,true}) {
            FakeDb db=new FakeDb("  bUsInEsS  ");
            var body=LanJson.create().fromJson("{name:'Example Company',phone:'',active:true,customerTypeId:9,business:false}",com.google.gson.JsonObject.class);
            if(update){body.addProperty("customerId",42);body.addProperty("accountNumber","CA42");}
            LanCustomerAccountService.save(db.connection(),body,UUID.randomUUID(),1);
            assertEquals(9,db.savedType);
            assertTrue(db.sql.stream().noneMatch(sql -> sql.contains("is_business")));
        }
    }
    @Test void legacyFlagDoesNotOverrideIndividualType() throws Exception {
        FakeDb db=new FakeDb("Individual");
        var body=LanJson.create().fromJson("{customerId:42,accountNumber:'CA42',name:'Example',phone:'',active:true,customerTypeId:9,business:true}",com.google.gson.JsonObject.class);
        LanCustomerAccountService.save(db.connection(),body,UUID.randomUUID(),1);
        assertEquals(9,db.savedType);
    }
    @Test void unknownTypeIsRejected() {
        assertThrows(LanCustomerAccountService.RuleViolation.class,()->LanCustomerAccountService.save(new FakeDb(null).connection(),LanJson.create().fromJson("{name:'Example',customerTypeId:999}",com.google.gson.JsonObject.class),UUID.randomUUID(),1));
    }
    @Test void savesAndNormalizesThreePhoneNumbers() throws Exception {
        FakeDb db=new FakeDb("Individual");
        var body=LanJson.create().fromJson("{customerId:42,accountNumber:'CA42',name:'Example',phone:'6600101',additionalPhone1:'660-0102',additionalPhone2:'+5926600103',phoneLabel:'Personal',additionalPhone1Label:'Business',additionalPhone2Label:'Work',active:true}",com.google.gson.JsonObject.class);
        LanCustomerAccountService.save(db.connection(),body,UUID.randomUUID(),1);
        assertEquals("Personal",db.mainLabel);assertEquals("Business",db.firstLabel);assertEquals("Work",db.secondLabel);
        assertEquals("+5926600102",db.firstPhone);assertEquals("+5926600103",db.secondPhone);
        body.remove("additionalPhone1");body.remove("additionalPhone2");body.remove("phoneLabel");body.remove("additionalPhone1Label");body.remove("additionalPhone2Label");
        LanCustomerAccountService.save(db.connection(),body,UUID.randomUUID(),1);
        assertEquals("+5926600102",db.firstPhone);assertEquals("Business",db.firstLabel);
        body.addProperty("additionalPhone1", "");body.addProperty("additionalPhone2", "");
        LanCustomerAccountService.save(db.connection(),body,UUID.randomUUID(),1);
        assertNull(db.firstPhone);assertNull(db.secondPhone);
    }
    @Test void rejectsDuplicateAndInvalidAdditionalPhones() {
        for(String phone:new String[]{"+5926600101","invalid"}) {
            var body=LanJson.create().fromJson("{name:'Example',phone:'6600101',active:true}",com.google.gson.JsonObject.class);
            body.addProperty("additionalPhone1",phone);
            var error=assertThrows(LanCustomerAccountService.RuleViolation.class,()->LanCustomerAccountService.save(new FakeDb("Individual").connection(),body,UUID.randomUUID(),1));
            assertEquals(400,error.status());
        }
    }
    private static final class FakeDb {
        String firstPhone,secondPhone,mainLabel,firstLabel,secondLabel; final String typeName; Integer savedType; final List<String> sql=new ArrayList<>();
        FakeDb(String typeName){this.typeName=typeName;}
        Connection connection(){return proxy(Connection.class,(p,m,a)->m.getName().equals("prepareStatement")?statement((String)a[0]):zero(m.getReturnType()));}
        PreparedStatement statement(String sql) {
            this.sql.add(sql);Map<Integer,Object> args=new HashMap<>();
            return proxy(PreparedStatement.class,(p,m,a)->{
                if(m.getName().startsWith("set")){args.put((Integer)a[0],a[1]);return null;}
                if(m.getName().equals("executeUpdate")) {
                    if(sql.startsWith("UPDATE customer_accounts SET account_number")){savedType=(Integer)args.get(3);firstPhone=(String)args.get(25);secondPhone=(String)args.get(26);mainLabel=(String)args.get(27);firstLabel=(String)args.get(28);secondLabel=(String)args.get(29);}
                    return 1;
                }
                if(!m.getName().equals("executeQuery"))return zero(m.getReturnType());
                if(sql.contains("JOIN role_permissions"))return rows(List.of(1));
                if(sql.startsWith("SELECT 1 FROM customer_types"))return rows(typeName==null?null:List.of(1));
                if(sql.contains("SELECT EXISTS(SELECT 1 FROM customer_accounts"))return rows(List.of(false));
                if(sql.startsWith("SELECT additional_phone_1"))return rows(Arrays.asList(firstPhone,secondPhone,mainLabel,firstLabel,secondLabel));
                if(sql.startsWith("SELECT account_number,credit_limit"))return rows(Arrays.asList("CA42",BigDecimal.ZERO,"",false,BigDecimal.ZERO,false,BigDecimal.ZERO,false,BigDecimal.ZERO,false));
                if(sql.contains("INSERT INTO customer_accounts")){savedType=(Integer)args.get(2);return rows(List.of(42,"CA42"));}
                throw new AssertionError("Unexpected query: "+sql);
            });
        }
        ResultSet rows(List<Object> row) {
            boolean[] first={true};
            return proxy(ResultSet.class,(p,m,a)->{
                if(m.getName().equals("next")){boolean result=first[0]&&row!=null;first[0]=false;return result;}
                if(m.getName().startsWith("get"))return row.get((Integer)a[0]-1);
                return zero(m.getReturnType());
            });
        }
    }
    private static <T>T proxy(Class<T> type,InvocationHandler h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h));}
    private static Object zero(Class<?> t){if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
}
