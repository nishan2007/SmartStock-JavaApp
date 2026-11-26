package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MobileDeviceServiceTest {
    @Test void diagnosticReportsStayOnThePairedDeviceAndDoNotChangeApprovalPolicy()throws Exception {
        try(Store store=new Store()) {
            UUID device=MobileDeviceService.redeemPairing(store.c,issue(store,request("First","EMPLOYEE_WEB")),"first-browser");
            UUID other=MobileDeviceService.redeemPairing(store.c,issue(store,request("Second","EMPLOYEE_WEB")),"second-browser");
            JsonObject report=new JsonObject();report.addProperty("model","SM-S918B");report.addProperty("platform","Android");report.addProperty("rememberLogin",true);
            MobileDeviceInfo.save(store.c,MobileDeviceService.identity(store.c,"first-browser"),report,"test browser");
            try(var p=store.c.prepareStatement("SELECT device_id,device_info::text,device_info_updated_at,remember_login,revision FROM mobile_managed_devices ORDER BY device_name" );var r=p.executeQuery()) {
                assertTrue(r.next());assertEquals(device,r.getObject(1));assertTrue(r.getString(2).contains("SM-S918B"));assertFalse(r.getString(2).contains("rememberLogin"));assertNotNull(r.getTimestamp(3));assertFalse(r.getBoolean(4));assertEquals(1,r.getLong(5));
                assertTrue(r.next());assertEquals(other,r.getObject(1));assertEquals("{}",r.getString(2));assertNull(r.getTimestamp(3));
            }
            report.addProperty("model","Rate limited");MobileDeviceInfo.save(store.c,device,report,"test browser");
            try(var p=store.c.prepareStatement("SELECT device_info->>'model' FROM mobile_managed_devices WHERE device_id=?")){p.setObject(1,device);try(var r=p.executeQuery()){assertTrue(r.next());assertEquals("SM-S918B",r.getString(1));}}
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.identity(store.c,"unpaired"));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.state(store.c,2));
        }
    }
    @Test void onlyManagersControlPersistenceAndPolicyChangesEndOnlyThatDevicesSessions()throws Exception {
        try(Store store=new Store()) {
            UUID device=MobileDeviceService.redeemPairing(store.c,issue(store,request("Personal phone","EMPLOYEE_WEB")),"personal-browser");
            UUID other=MobileDeviceService.redeemPairing(store.c,issue(store,request("Shared phone","EMPLOYEE_WEB")),"other-browser");
            assertFalse(MobileDeviceService.rememberLogin(store.c,"personal-browser"));
            try(var st=store.c.createStatement()) {
                st.execute("CREATE TABLE employee_web_devices(device_id uuid PRIMARY KEY,browser_hash text)");
                st.execute("CREATE TABLE employee_web_sessions(session_id int PRIMARY KEY,browser_device_id uuid,revoked_at timestamptz)");
                st.execute("INSERT INTO employee_web_devices VALUES('"+device+"','"+LanSecurity.sha256("personal-browser")+"'),('"+other+"','"+LanSecurity.sha256("other-browser")+"')");
                st.execute("INSERT INTO employee_web_sessions VALUES(1,'"+device+"',NULL),(2,'"+other+"',NULL)");
            }
            JsonObject update=request("Personal phone","EMPLOYEE_WEB");update.addProperty("deviceId",device.toString());update.addProperty("revision",1);update.addProperty("rememberLogin",true);
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.update(store.c,2,update));
            assertFalse(MobileDeviceService.rememberLogin(store.c,"personal-browser"));
            MobileDeviceService.update(store.c,1,update);
            assertTrue(MobileDeviceService.rememberLogin(store.c,"personal-browser"));
            assertFalse(MobileDeviceService.rememberLogin(store.c,"other-browser"));
            try(var st=store.c.createStatement();var r=st.executeQuery("SELECT session_id,revoked_at IS NOT NULL FROM employee_web_sessions ORDER BY session_id")) {
                assertTrue(r.next());assertTrue(r.getBoolean(2));assertTrue(r.next());assertFalse(r.getBoolean(2));
            }
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.update(store.c,1,update));
            update.addProperty("revision",2);update.remove("rememberLogin");
            MobileDeviceService.update(store.c,1,update); // Old clients must preserve policy.
            assertTrue(MobileDeviceService.rememberLogin(store.c,"personal-browser"));
            update.addProperty("revision",3);update.addProperty("rememberLogin",false);
            MobileDeviceService.update(store.c,1,update);
            assertFalse(MobileDeviceService.rememberLogin(store.c,"personal-browser"));
        }
    }
    private static final Gson JSON=LanJson.create();
    private JsonObject request(String name,String... apps) {
        JsonObject b=new JsonObject();b.addProperty("name",name);b.add("apps",JSON.toJsonTree(apps));return b;
    }
    private static final class Store implements AutoCloseable {
        final String url,schema="mobile_test_"+UUID.randomUUID().toString().replace("-","");
        final Connection c;
        Store() throws Exception {
            url=System.getProperty("smartstock.mobile.test.jdbc","");assumeTrue(!url.isBlank(),"An isolated PostgreSQL test cluster is required");
            if(!url.equals("jdbc:postgresql://127.0.0.1:55473/mobile_management_test"))throw new IllegalArgumentException("Use only the isolated mobile management test cluster.");
            c=DriverManager.getConnection(url,"mobile_test","");
            try(var s=c.createStatement()) {
                s.execute("CREATE SCHEMA "+schema);s.execute("SET search_path TO "+schema);
                s.execute("CREATE TABLE users(user_id int PRIMARY KEY,role_id int,is_active boolean DEFAULT true);INSERT INTO users VALUES(1,1,true),(2,2,true)");
                s.execute("CREATE TABLE permissions(permission_id int PRIMARY KEY,permission_key text);INSERT INTO permissions VALUES(1,'DEVICE_MANAGEMENT')");
                s.execute("CREATE TABLE role_permissions(role_id int,permission_id int);INSERT INTO role_permissions VALUES(1,1)");
                s.execute("CREATE TABLE security_audit_events(event_type text,actor_user_id int,details text)");
                s.execute("CREATE TABLE employee_web_runtime(runtime_id int,public_origin text,enabled boolean,gateway_running boolean,gateway_heartbeat_at timestamptz);INSERT INTO employee_web_runtime VALUES(1,'https://timeclock.deckers.gy',true,true,now())");
                s.execute("CREATE TABLE mobile_item_web_runtime(runtime_id int,enabled boolean);INSERT INTO mobile_item_web_runtime VALUES(1,true)");
            }
            String sql=SqlScriptRunner.readResource("database/migrations/v1_after/20261119120000_shared_mobile_management.sql").replace("public.",schema+".");
            SqlScriptRunner.runSql(c,sql);
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261120200000_mobile_device_remember_login.sql").replace("public.",schema+"."));SqlScriptRunner.runSql(c,sql);
            String links=SqlScriptRunner.readResource("database/migrations/v1_after/20261119130000_mobile_home_screen_links.sql").replace("public.",schema+".");
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261120210000_mobile_device_info.sql").replace("public.",schema+"."));
            SqlScriptRunner.runSql(c,links);SqlScriptRunner.runSql(c,links);c.setAutoCommit(false);
        }
        Connection another() throws Exception {Connection other=DriverManager.getConnection(url,"mobile_test","");try(var s=other.createStatement()){s.execute("SET search_path TO "+schema);}other.setAutoCommit(false);return other;}
        public void close() throws Exception {try {c.rollback();c.setAutoCommit(true);try(var s=c.createStatement()){s.execute("DROP SCHEMA "+schema+" CASCADE");}}finally{c.close();}}
    }
    private String issue(Store store,JsonObject b) throws Exception {
        String url=MobileDeviceService.issue(store.c,1,b).get("url").toString();return url.substring(url.indexOf("#pair=")+6);
    }
    @Test void repairUsesTheExistingApprovalAndCannotBeIssuedByNonAdministrators()throws Exception {
        try(Store store=new Store()) {
            UUID id=MobileDeviceService.redeemPairing(store.c,issue(store,request("Repair phone","INVENTORY")),"old-browser");
            JsonObject b=new JsonObject();b.addProperty("deviceId",id.toString());
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.issueRepair(store.c,2,b));
            var repair=MobileDeviceService.issueRepair(store.c,1,b);String token=java.net.URI.create(repair.get("url").toString()).getFragment().substring(7);
            assertEquals(id,MobileDeviceService.redeemRepair(store.c,token,"old-browser","repaired-browser"));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.identity(store.c,"old-browser"));
            assertEquals(id,MobileDeviceService.identity(store.c,"repaired-browser"));assertEquals(List.of("INVENTORY"),MobileDeviceService.grants(store.c,id));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.redeemRepair(store.c,token,null,"replay"));
        }
    }
    @Test void registryAndRouteBoundariesFailClosed() throws Exception {
        assertEquals(MobileDeviceService.APPS.size(),MobileDeviceService.APPS.stream().map(MobileDeviceService.App::key).distinct().count());
        assertEquals(List.of("ORDERS"),MobileDeviceService.routeApps("employee","orders/assign"));
        assertEquals(List.of("RECEIVING"),MobileDeviceService.routeApps("mobile","/receiving/post"));
        assertEquals(List.of("PHONE_MINUTES"),MobileDeviceService.routeApps("employee","minutes/create"));
        assertTrue(MobileDeviceService.routeApps("employee","new-unregistered-api").isEmpty());
        assertEquals(List.of("MOBILE_ITEMS"),MobileDeviceService.routeApps("mobile","/products/save",new JsonObject()));
        JsonObject update=new JsonObject();update.addProperty("productId",4);
        assertEquals(List.of("EDIT_ITEMS"),MobileDeviceService.routeApps("mobile","/products/save",update));
        assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.app("UNKNOWN"));
        assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.keys(request("Phone","UNKNOWN")));
        assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.name(request(" ")));
    }
    @Test void installedWorkspaceUsesTheExistingDeviceWithoutGrantingAnyApps() throws Exception {
        try(Store store=new Store()) {
            UUID id=MobileDeviceService.redeemPairing(store.c,issue(store,request("Workspace phone","INVENTORY")),"paired-browser");
            var link=MobileDeviceService.requestLink(store.c,"WORKSPACE","https://timeclock.deckers.gy/employees/mobile-management/");
            assertEquals("Deckers workspace",MobileDeviceService.linkInfo(store.c,"paired-browser",link.requestId()).get("appName"));
            MobileDeviceService.approveLink(store.c,"paired-browser",link.requestId());
            assertTrue(MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),"https://timeclock.deckers.gy/employees/mobile-management/","installed-workspace"));
            assertEquals(id,MobileDeviceService.identity(store.c,"installed-workspace"));
            assertEquals(List.of("INVENTORY"),MobileDeviceService.grants(store.c,id));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.requireAny(store.c,"installed-workspace",List.of("RECEIVING")));
            assertTrue(DeckersWorkspace.apps(MobileDeviceService.grants(store.c,id),Set.of("NEW_ITEM")).isEmpty());
            var update=request("Workspace phone","RECEIVING");update.addProperty("deviceId",id.toString());update.addProperty("revision",1);
            MobileDeviceService.update(store.c,1,update);
            assertEquals(List.of("RECEIVING"),DeckersWorkspace.apps(MobileDeviceService.grants(store.c,id),Set.of("RECEIVING_INVENTORY")).stream().map(a->a.get("key")).toList());
            update.addProperty("revision",2);update.addProperty("revoke",true);MobileDeviceService.update(store.c,1,update);
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.identity(store.c,"installed-workspace"));
        }
    }
    @Test void oneApprovalSupportsMultipleAppsAndEmployeeRolesStillApply() throws Exception {
        try(Store store=new Store()) {
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.issue(store.c,2,request("Denied","ORDERS")));
            String code=issue(store,request("Receiving phone","ORDERS","INVENTORY"));
            UUID id=MobileDeviceService.redeemPairing(store.c,code,"phone-secret");
            assertEquals(id,MobileDeviceService.requireAny(store.c,"phone-secret",List.of("ORDERS")));
            assertEquals(id,MobileDeviceService.requireAny(store.c,"phone-secret",List.of("INVENTORY")));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.requireAny(store.c,"phone-secret",List.of("RECEIVING")));
            assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("orders/assign",Set.of()));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.redeemPairing(store.c,code,"second-secret"));
            store.c.commit();
            try(Connection next=store.another()){assertEquals(id,MobileDeviceService.requireAny(next,"phone-secret",List.of("ORDERS")));next.rollback();}
        }
    }
    @Test void grantsCanChangeWithoutPairingAndRevocationBlocksAllCredentials() throws Exception {
        try(Store store=new Store()) {
            UUID id=MobileDeviceService.redeemPairing(store.c,issue(store,request("Phone","ORDERS")),"original");
            MobileDeviceService.addCredential(store.c,id,"second-origin");
            JsonObject b=request("Renamed","INVENTORY");b.addProperty("deviceId",id.toString());b.addProperty("revision",1);
            MobileDeviceService.update(store.c,1,b);
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.requireAny(store.c,"original",List.of("ORDERS")));
            assertEquals(id,MobileDeviceService.requireAny(store.c,"original",List.of("INVENTORY")));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.update(store.c,1,b));
            b.addProperty("revision",2);b.addProperty("revoke",true);MobileDeviceService.update(store.c,1,b);
            for(String credential:List.of("original","second-origin"))assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.requireAny(store.c,credential,List.of("INVENTORY")));
        }
    }
    @Test void emptyInitialGrantsAndExpiredCodesNeverGrantAccess() throws Exception {
        try(Store store=new Store()) {
            UUID id=MobileDeviceService.redeemPairing(store.c,issue(store,request("No apps")),"empty");
            assertTrue(MobileDeviceService.grants(store.c,id).isEmpty());
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.requireAny(store.c,"empty",List.of("ORDERS")));
            String code=issue(store,request("Expired","ORDERS"));
            try(var s=store.c.createStatement()){s.executeUpdate("UPDATE mobile_device_pairings SET expires_at=now()-INTERVAL '1 second'");}
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.redeemPairing(store.c,code,"expired"));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.identity(store.c,"old-app-approval"));
        }
    }
    @Test void crossOriginHandoffsAreSingleUseDestinationBoundAndRecheckGrants() throws Exception {
        try(Store store=new Store()) {
            UUID id=MobileDeviceService.redeemPairing(store.c,issue(store,request("Phone","INVENTORY")),"launcher");
            String link=MobileDeviceService.handoff(store.c,id,"INVENTORY");String token=link.substring(link.indexOf("#handoff=")+9);
            Savepoint before=store.c.setSavepoint();
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.redeemHandoff(store.c,token,"https://evil.example/employees/mobile-management/","bad"));store.c.rollback(before);
            String base=MobileDeviceService.callback(InventoryWebService.PUBLIC_URL);
            assertEquals(InventoryWebService.PUBLIC_URL,MobileDeviceService.redeemHandoff(store.c,token,base,"inventory-origin"));
            assertEquals(id,MobileDeviceService.requireAny(store.c,"inventory-origin",List.of("INVENTORY")));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.redeemHandoff(store.c,token,base,"replay"));
            String next=MobileDeviceService.handoff(store.c,id,"INVENTORY");String second=next.substring(next.indexOf("#handoff=")+9);
            MobileDeviceService.replaceGrants(store.c,id,List.of());
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.redeemHandoff(store.c,second,base,"disabled"));
        }
    }
    @Test void onlyOneConcurrentBrowserCanRedeemTheSameApproval() throws Exception {
        try(Store store=new Store()) {
            String code=issue(store,request("Race","ORDERS"));store.c.commit();
            ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
            try {
                List<Future<Boolean>> results=new ArrayList<>();
                for(int i=0;i<2;i++){String credential="race-"+i;results.add(executor.submit(()->{try(Connection c=store.another()){start.await();try{MobileDeviceService.redeemPairing(c,code,credential);c.commit();return true;}catch(MobileDeviceService.Denied e){c.rollback();return false;}}}));}
                start.countDown();int accepted=0;for(var result:results)if(result.get(10,TimeUnit.SECONDS))accepted++;
                assertEquals(1,accepted);
            }finally{executor.shutdownNow();}
        }
    }
    @Test void isolatedHomeScreenAppAttachesToExistingDeviceWithoutAnotherPairing() throws Exception {
        try(Store store=new Store()) {
            UUID device=MobileDeviceService.redeemPairing(store.c,issue(store,request("iPhone","INVENTORY")),"safari");
            String destination="https://inventory.deckers.gy/employees/mobile-management/";
            var link=MobileDeviceService.requestLink(store.c,"INVENTORY",destination);
            assertFalse(MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),destination,"installed"));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.approveLink(store.c,null,link.requestId()));
            assertEquals("Inventory",MobileDeviceService.linkInfo(store.c,"safari",link.requestId()).get("appName"));
            MobileDeviceService.approveLink(store.c,"safari",link.requestId());
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.claimLink(store.c,link.requestId(),"wrong-claim",destination,"stolen"));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),"https://evil.example/","stolen"));
            assertTrue(MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),destination,"installed"));
            assertEquals(device,MobileDeviceService.requireAny(store.c,"installed",List.of("INVENTORY")));
            assertEquals(device,MobileDeviceService.identity(store.c,"safari"));
            try(var statement=store.c.createStatement();var row=statement.executeQuery("SELECT count(*) FROM mobile_managed_devices")){row.next();assertEquals(1,row.getInt(1));}
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),destination,"replay"));
        }
    }
    @Test void homeScreenConnectionsRespectExpiryDisabledAppsAndRevocation() throws Exception {
        try(Store store=new Store()) {
            UUID device=MobileDeviceService.redeemPairing(store.c,issue(store,request("iPhone","ORDERS")),"safari");
            String destination="https://ordersmanagement.deckers.gy/employees/mobile-management/";
            var denied=MobileDeviceService.requestLink(store.c,"INVENTORY",destination);
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.approveLink(store.c,"safari",denied.requestId()));
            var link=MobileDeviceService.requestLink(store.c,"ORDERS",destination);
            MobileDeviceService.approveLink(store.c,"safari",link.requestId());
            MobileDeviceService.replaceGrants(store.c,device,List.of());
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),destination,"disabled"));
            MobileDeviceService.replaceGrants(store.c,device,List.of("ORDERS"));
            try(var statement=store.c.createStatement()){statement.executeUpdate("UPDATE mobile_managed_devices SET revoked_at=now()");}
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),destination,"revoked"));
            try(var statement=store.c.createStatement()){statement.executeUpdate("UPDATE mobile_device_links SET expires_at=now()-INTERVAL '1 second'");}
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.linkInfo(store.c,"safari",link.requestId()));
            assertThrows(MobileDeviceService.Denied.class,()->MobileDeviceService.claimLink(store.c,link.requestId(),link.claim(),destination,"expired"));
        }
    }
}
