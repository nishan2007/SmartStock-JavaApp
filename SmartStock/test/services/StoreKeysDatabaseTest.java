package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in isolated PostgreSQL tests; the fixed test URL cannot target a live store. */
class StoreKeysDatabaseTest {
    private static final String URL="jdbc:postgresql://127.0.0.1:55447/smartstock_store_keys_test";
    private static Connection open()throws SQLException{return DriverManager.getConnection(URL,"store_keys_test","");}
    @BeforeAll static void schema()throws Exception {
        String configured=System.getProperty("smartstock.keys.test.jdbc","");assumeTrue(!configured.isBlank());assertEquals(URL,configured);
        try(Connection c=open();Statement s=c.createStatement()) {
            s.execute("CREATE TABLE roles(role_id int PRIMARY KEY,role_name text); CREATE TABLE permissions(permission_id serial PRIMARY KEY,permission_key text UNIQUE,permission_name text,description text,permission_group text,permission_subgroup text); CREATE TABLE role_permissions(role_id int,permission_id int,updated_at timestamptz,PRIMARY KEY(role_id,permission_id)); CREATE TABLE locations(location_id int PRIMARY KEY,name text,timezone text); CREATE TABLE users(user_id int PRIMARY KEY,username text,full_name text,is_active boolean,role_id int); CREATE TABLE user_locations(user_id int,location_id int,PRIMARY KEY(user_id,location_id)); INSERT INTO roles VALUES(1,'ADMIN'),(2,'Employee'),(3,'No access');");
            s.execute("CREATE TABLE lan_api_idempotency(device_id uuid,idempotency_key text,operation_key text,request_hash text,response_status int,response_body text,completed_at timestamptz,PRIMARY KEY(device_id,idempotency_key))");
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261122120000_store_keys.sql"));
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261122120000_store_keys.sql"));
            s.execute("INSERT INTO role_permissions SELECT 2,permission_id,CURRENT_TIMESTAMP FROM permissions WHERE permission_key='STORE_KEYS_SELF'");
        }
    }
    @BeforeEach void fixtures()throws Exception {
        try(Connection c=open();Statement s=c.createStatement()) {
            s.execute("TRUNCATE store_key_events,store_key_custody,store_keys,lan_api_idempotency,user_locations,users,locations CASCADE");
            s.execute("INSERT INTO locations VALUES(1,'Main','America/Guyana'),(2,'Other','UTC'); INSERT INTO users VALUES(1,'manager','Manager',true,1),(2,'alice','Alice',true,2),(3,'bob','Bob',true,2),(4,'inactive','Inactive',false,2),(5,'other','Other store',true,2),(6,'denied','Denied',true,3); INSERT INTO user_locations VALUES(1,1),(2,1),(3,1),(4,1),(5,2),(6,1)");
        }
    }
    private static JsonObject body(Object...kv){JsonObject b=new JsonObject();for(int i=0;i<kv.length;i+=2){Object v=kv[i+1];if(v instanceof Number n)b.addProperty(kv[i].toString(),n);else if(v instanceof Boolean t)b.addProperty(kv[i].toString(),t);else b.addProperty(kv[i].toString(),v.toString());}return b;}
    private static JsonObject key(StoreKeys.Key k){JsonObject b=body("keyId",k.keyId(),"version",k.version());if(k.custodyId()!=null)b.addProperty("custodyId",k.custodyId().toString());return b;}
    private static StoreKeys.Key current(Connection c,int n)throws Exception{return ServerStoreKeyService.list(c,1,1).keys().get(n);}
    private static String token(){return "SSK1"+UUID.randomUUID().toString().replace("-","");}
    private static void create(Connection c,int count)throws Exception {
        ServerStoreKeyService.mutate(c,1,1,"count",body("count",count,"expectedCount",0));
        for(int i=0;i<count;i++){var k=current(c,i);var b=key(k);b.addProperty("cardToken",token());ServerStoreKeyService.mutate(c,1,1,"pair",b);}
    }
    private static void checkout(Connection c,int index,int actor,int holder)throws Exception {var b=key(current(c,index));b.addProperty("employeeId",holder);ServerStoreKeyService.mutate(c,actor,1,"checkout",b);}
    private static void rule(String code,org.junit.jupiter.api.function.Executable operation){assertEquals(code,assertThrows(ServerStoreKeyService.Rule.class,operation).code);}

    @Test void multipleKeysSelfReturnAndManagerRecoveryKeepBothIdentities()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,2);checkout(c,0,2,2);checkout(c,1,1,2);
            var own=ServerStoreKeyService.list(c,2,1);assertEquals(2,own.keys().stream().filter(k->Objects.equals(k.holderUserId(),2)).count());
            ServerStoreKeyService.mutate(c,2,1,"return",key(current(c,0)));
            var b=key(current(c,1));b.addProperty("reason","Collected at end of shift");b.addProperty("recovered",true);
            ServerStoreKeyService.mutate(c,1,1,"force-return",b);
            assertTrue(ServerStoreKeyService.list(c,1,1).keys().stream().allMatch(k->k.status().equals("Available")));
            var history=ServerStoreKeyService.history(c,1,1,body("search","Alice"));assertEquals(4,history.events().size());
            assertTrue(history.events().stream().anyMatch(e->e.eventType().equals("FORCED_RETURN")&&e.actorName().equals("Manager")&&e.holderName().equals("Alice")&&!e.reason().isBlank()));
            assertEquals(0,ServerStoreKeyService.history(c,3,1,new JsonObject()).events().size());c.rollback();
        }
    }
    @Test void permissionsLocationEligibilityAndForcedReturnAreServerEnforced()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,1);
            rule("PERMISSION_DENIED",()->ServerStoreKeyService.list(c,6,1));rule("STORE_ACCESS_DENIED",()->ServerStoreKeyService.list(c,2,2));
            rule("PERMISSION_DENIED",()->ServerStoreKeyService.mutate(c,2,1,"retire",key(current(c,0))));
            rule("PERMISSION_DENIED",()->checkout(c,0,2,3));rule("EMPLOYEE_UNAVAILABLE",()->checkout(c,0,1,4));rule("EMPLOYEE_UNAVAILABLE",()->checkout(c,0,1,5));
            checkout(c,0,2,2);rule("PERMISSION_DENIED",()->ServerStoreKeyService.mutate(c,3,1,"return",key(current(c,0))));
            var b=key(current(c,0));b.addProperty("recovered",true);rule("INVALID_REQUEST",()->ServerStoreKeyService.mutate(c,1,1,"force-return",b));
            b.addProperty("reason","Recovered");b.addProperty("recovered",false);rule("RECOVERY_REQUIRED",()->ServerStoreKeyService.mutate(c,1,1,"force-return",b));
            b.addProperty("recovered",true);rule("PERMISSION_DENIED",()->ServerStoreKeyService.mutate(c,2,1,"force-return",b));
            assertEquals("Checked out",current(c,0).status());c.rollback();
        }
    }
    @Test void pairingReplacesOldCardRejectsDuplicatesAndCannotChangeWhileOut()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,2);String a=token(),z=token();var b=key(current(c,0));b.addProperty("cardToken",a);ServerStoreKeyService.mutate(c,1,1,"pair",b);
            var duplicate=key(current(c,1));duplicate.addProperty("cardToken",a);rule("KEY_STATE_CHANGED",()->ServerStoreKeyService.mutate(c,1,1,"pair",duplicate));
            var invalid=key(current(c,1));invalid.addProperty("cardToken","SSB1EMPLOYEE");rule("INVALID_KEY_CARD",()->ServerStoreKeyService.mutate(c,1,1,"pair",invalid));
            b=key(current(c,0));b.addProperty("cardToken",z);ServerStoreKeyService.mutate(c,1,1,"pair",b);
            var old=key(current(c,0));old.addProperty("cardToken",a);rule("KEY_STATE_CHANGED",()->ServerStoreKeyService.mutate(c,2,1,"checkout",old));
            var valid=key(current(c,0));valid.addProperty("cardToken",z);ServerStoreKeyService.mutate(c,2,1,"checkout",valid);
            var held=key(current(c,0));held.addProperty("cardToken",token());rule("KEY_STATE_CHANGED",()->ServerStoreKeyService.mutate(c,1,1,"pair",held));
            assertEquals("NFC",ServerStoreKeyService.history(c,1,1,body("search","CHECKOUT")).events().get(0).method());c.rollback();
        }
    }
    @Test void retirementCountAndStaleConfirmationsRetainHistory()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,2);var stale=key(current(c,0));checkout(c,0,2,2);
            rule("KEY_STATE_CHANGED",()->ServerStoreKeyService.mutate(c,2,1,"checkout",stale));
            rule("KEY_STATE_CHANGED",()->ServerStoreKeyService.mutate(c,1,1,"retire",key(current(c,0))));
            ServerStoreKeyService.mutate(c,1,1,"retire",key(current(c,1)));
            assertEquals("Retired",current(c,1).status());rule("KEY_STATE_CHANGED",()->checkout(c,1,2,2));
            ServerStoreKeyService.mutate(c,1,1,"count",body("count",2,"expectedCount",1));assertEquals(3,current(c,2).keyNumber());
            assertEquals("Available",current(c,2).status());checkout(c,2,2,2); assertFalse(current(c,2).paired()); ServerStoreKeyService.mutate(c,2,1,"return",key(current(c,2))); assertEquals("Available",current(c,2).status());
            rule("RETIRE_KEYS_FIRST",()->ServerStoreKeyService.mutate(c,1,1,"count",body("count",0,"expectedCount",2)));c.rollback();
        }
    }
    @Test void historyCannotBeRewrittenAndDeactivationDoesNotReleaseCustody()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,1);checkout(c,0,2,2);
            try(var s=c.createStatement()){s.executeUpdate("UPDATE users SET is_active=false WHERE user_id=2");}
            assertEquals("Checked out",current(c,0).status());var b=key(current(c,0));b.addProperty("recovered",true);b.addProperty("reason","Employee left company");ServerStoreKeyService.mutate(c,1,1,"force-return",b);
            Savepoint save=c.setSavepoint();assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.executeUpdate("DELETE FROM store_key_events");}});c.rollback(save);
            assertEquals(4,ServerStoreKeyService.history(c,1,1,new JsonObject()).events().size());c.rollback();
        }
    }
    @Test void managerAssistedReturnAndRollbackPreserveCustody()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,1);checkout(c,0,1,2);Savepoint beforeReturn=c.setSavepoint();
            ServerStoreKeyService.mutate(c,1,1,"return",key(current(c,0)));assertEquals("Available",current(c,0).status());
            c.rollback(beforeReturn);assertEquals("Checked out",current(c,0).status());
            assertEquals(0,ServerStoreKeyService.history(c,1,1,body("search","RETURN")).events().size());
            ServerStoreKeyService.mutate(c,1,1,"return",key(current(c,0)));
            var event=ServerStoreKeyService.history(c,1,1,body("search","RETURN")).events().get(0);
            assertEquals("Manager",event.actorName());assertEquals("Alice",event.holderName());assertEquals("MANUAL",event.method());c.rollback();
        }
    }
    @Test void repeatedRequestReturnsOriginalResultAndDifferentActorHashIsRejected()throws Exception {
        try(Connection c=open()){c.setAutoCommit(false);create(c,1);UUID device=UUID.randomUUID();String request=UUID.randomUUID().toString(),hash="actor2-location1";
            assertNull(LanApiServer.loadIdempotentResult(c,device,request,"checkout",hash));checkout(c,0,2,2);LanApiServer.completeIdempotency(c,device,request,Map.of("saved",true));c.commit();
            assertEquals(true,LanApiServer.loadIdempotentResult(c,device,request,"checkout",hash).get("saved"));
            assertThrows(Exception.class,()->LanApiServer.loadIdempotentResult(c,device,request,"checkout","actor3-location1"));
            assertEquals(1,ServerStoreKeyService.history(c,1,1,body("search","CHECKOUT")).events().size());c.rollback();
        }
    }
    @Test void concurrentRegistersCannotDoubleCheckout()throws Exception {
        JsonObject request;
        try(Connection c=open()){c.setAutoCommit(false);create(c,1);request=key(current(c,0));c.commit();}
        ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Boolean>>results=new ArrayList<>();
            for(int actor:List.of(2,3))results.add(workers.submit(()->{start.await();try(Connection c=open()){c.setAutoCommit(false);try{ServerStoreKeyService.mutate(c,actor,1,"checkout",request.deepCopy());c.commit();return true;}catch(ServerStoreKeyService.Rule e){c.rollback();assertEquals("KEY_STATE_CHANGED",e.code);return false;}}}));
            start.countDown();int winners=0;for(var f:results)if(f.get(15,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            try(Connection c=open()){assertEquals(1,ServerStoreKeyService.history(c,1,1,body("search","CHECKOUT")).events().size());}
        }finally{workers.shutdownNow();}
    }
}
