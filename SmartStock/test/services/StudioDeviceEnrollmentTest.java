package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StudioDeviceEnrollmentTest {
    private Connection open()throws Exception {
        String url=System.getProperty("smartstock.studio.test.jdbc","");assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:55446/postgres"))throw new IllegalArgumentException("Use the isolated Studio test cluster.");
        Connection c=DriverManager.getConnection(url,"studio_test","");c.setAutoCommit(false);
        try(var s=c.createStatement()) {
            s.execute("CREATE TABLE locations(location_id int PRIMARY KEY);INSERT INTO locations VALUES(1),(2)");
            s.execute("""
                    CREATE TABLE devices(device_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),installation_id text UNIQUE,device_fingerprint text,
                      device_name text,hostname text,os_name text,os_version text,os_arch text,java_version text,app_version text,local_username text,
                      mac_addresses text,pairing_public_key text,api_credential_hash text,api_previous_credential_hash text,last_store_id int,last_login_user_id int,
                      is_approved boolean DEFAULT false,is_blocked boolean DEFAULT false,allow_sales boolean DEFAULT true,allow_orders boolean DEFAULT true,
                      first_seen timestamptz DEFAULT now(),last_seen timestamptz,access_mode text DEFAULT 'CLIENT')
                    """);
            s.execute("CREATE TABLE lan_api_sessions(session_id uuid DEFAULT gen_random_uuid(),device_id uuid,user_id int,location_id int,revoked_at timestamptz,session_hash text,expires_at timestamptz,absolute_expires_at timestamptz,auth_source text)");
        }
        String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261008160000_studio_device_access.sql");
        SqlScriptRunner.runSql(c,migration);SqlScriptRunner.runSql(c,migration);
        return c;
    }
    private JsonObject body(String installation) {
        JsonObject b=new JsonObject();b.addProperty("installationId",installation);b.addProperty("publicKey","studio-key");
        b.addProperty("deviceFingerprint","same-hardware");b.addProperty("hostname","STORE-PC");b.addProperty("pairingChallenge","challenge");return b;
    }
    private UUID register(Connection c)throws Exception {
        try(var s=c.createStatement();var r=s.executeQuery("INSERT INTO devices(installation_id,device_fingerprint,hostname,last_store_id,is_approved,pairing_public_key,api_credential_hash) VALUES('register','same-hardware','STORE-PC',1,true,'register-key','register-hash') RETURNING device_id")){r.next();return (UUID)r.getObject(1);}
    }
    private long count(Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    @Test void existingComputerNeedsOnlyStudioCheckboxAndRetainsRegisterCredential()throws Exception {
        try(Connection c=open()) {
            UUID id=register(c);var b=body("studio");var enrolled=StudioDeviceEnrollmentService.enroll(c,b,1,"challenge");
            assertEquals(id,enrolled.deviceId());assertFalse(enrolled.allowed());assertEquals(1,count(c,"SELECT count(*) FROM devices"));
            assertThrows(StudioDeviceEnrollmentService.Denied.class,()->StudioDeviceEnrollmentService.claim(c,b));
            try(var s=c.createStatement()){s.execute("UPDATE devices SET allow_studio=true");}
            var credential=StudioDeviceEnrollmentService.claim(c,b);
            assertEquals(id,credential.deviceId());String hash=LanSecurity.sha256(credential.token());
            assertEquals(id,StudioDeviceEnrollmentService.authenticate(c,hash,"/v1/studio/remove-background").deviceId());
            assertThrows(StudioDeviceEnrollmentService.Denied.class,()->StudioDeviceEnrollmentService.authenticate(c,hash,"/v1/sales/checkout"));
            assertEquals(1,count(c,"SELECT count(*) FROM devices WHERE pairing_public_key='register-key' AND api_credential_hash='register-hash'"));
            try(var s=c.createStatement()){s.execute("UPDATE devices SET allow_studio=false");}
            assertThrows(StudioDeviceEnrollmentService.Denied.class,()->StudioDeviceEnrollmentService.authenticate(c,hash,"/v1/studio/branding"));c.rollback();
        }
    }
    @Test void oldStudioDuplicateIsRetainedForHistoryAndHiddenUnderExistingDevice()throws Exception {
        try(Connection c=open()) {
            UUID parent=register(c);
            try(var s=c.createStatement()){s.execute("INSERT INTO devices(installation_id,device_fingerprint,hostname,last_store_id,is_approved) VALUES('studio','same-hardware','STORE-PC',1,true)");}
            assertEquals(parent,StudioDeviceEnrollmentService.enroll(c,body("studio"),1,"challenge").deviceId());
            assertEquals(1,count(c,"SELECT count(*) FROM devices d WHERE NOT EXISTS(SELECT 1 FROM studio_device_clients sc WHERE sc.legacy_device_id=d.device_id)"));
            assertEquals(1,count(c,"SELECT count(*) FROM devices WHERE installation_id='studio' AND is_blocked=true"));
            StudioDeviceEnrollmentService.enroll(c,body("studio"),1,"challenge");assertEquals(2,count(c,"SELECT count(*) FROM devices"));c.rollback();
        }
    }
    @Test void newComputerCreatesOneRowAndCanLaterBecomeARegisterWithoutDuplicatingIt()throws Exception {
        try(Connection c=open()) {
            var enrolled=StudioDeviceEnrollmentService.enroll(c,body("studio"),1,"challenge");
            assertEquals(1,count(c,"SELECT count(*) FROM devices"));
            assertFalse(enrolled.allowed());StudioDeviceEnrollmentService.adoptStudioOnlyDevice(c,body("register"),1);
            assertEquals(1,count(c,"SELECT count(*) FROM devices WHERE installation_id='register'"));assertEquals(1,count(c,"SELECT count(*) FROM studio_device_clients"));c.rollback();
        }
    }
    @Test void studioLoginDoesNotRevokeRegisterSession()throws Exception {
        try(Connection c=open()) {
            UUID id=register(c);Class<?> principal=Class.forName("services.LanApiServer$DevicePrincipal"),user=Class.forName("services.LanApiServer$AuthenticatedUser");
            var deviceCtor=principal.getDeclaredConstructor(UUID.class,String.class,Integer.class,boolean.class,boolean.class);deviceCtor.setAccessible(true);
            var userCtor=user.getDeclaredConstructor(int.class,String.class,String.class,String.class,String.class,int.class,String.class,String.class);userCtor.setAccessible(true);
            Object employee=userCtor.newInstance(1,"staff","Staff","","USER",1,"Store","America/Guyana");
            var issue=LanApiServer.class.getDeclaredMethod("issueSession",Connection.class,principal,user,String.class);issue.setAccessible(true);
            issue.invoke(null,c,deviceCtor.newInstance(id,"register",1,false,false),employee,"EMPLOYEE_PIN");
            Object studio=deviceCtor.newInstance(id,"studio",1,false,true);
            issue.invoke(null,c,studio,employee,"EMPLOYEE_PIN");issue.invoke(null,c,studio,employee,"EMPLOYEE_PIN");
            assertEquals(1,count(c,"SELECT count(*) FROM lan_api_sessions WHERE client_application='smartstock' AND revoked_at IS NULL"));
            assertEquals(1,count(c,"SELECT count(*) FROM lan_api_sessions WHERE client_application='smartstudio' AND revoked_at IS NULL"));c.rollback();
        }
    }
    @Test void studioCredentialsCannotInvokeRegisterOrAdministrationRoutes() {
        assertTrue(StudioDeviceEnrollmentService.allowedPath("/v1/studio/remove-background"));
        assertFalse(StudioDeviceEnrollmentService.allowedPath("/v1/security/devices/update"));
        assertFalse(StudioDeviceEnrollmentService.allowedPath("/v1/cloud/storage/upload"));
    }
}
