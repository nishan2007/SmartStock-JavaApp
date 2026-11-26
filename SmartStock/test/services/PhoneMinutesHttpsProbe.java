package services;

import com.google.gson.*;
import com.sun.net.httpserver.HttpsServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/** Child-JVM acceptance against an explicitly isolated disposable PostgreSQL cluster. */
public final class PhoneMinutesHttpsProbe {
 private static final UUID DEVICE=UUID.fromString("a50a03f2-277f-4a51-8221-21f675f4ab00"),BROWSER=UUID.fromString("4b832893-95ad-4269-9d42-b9a520679ac1"),AUTH=UUID.fromString("52d69fa0-c458-4d66-bc8c-723d129aa5d1");
 private static final String DEVICE_TOKEN="isolated-device-token",SESSION="isolated-employee-session",WEB_BROWSER="isolated-browser-token",WEB_SESSION="isolated-web-session",CSRF="isolated-csrf";
 private static HttpClient client;private static Connection db;private static int lanPort,webPort;
 public static void main(String[] args) {
  try{run(args[0]);System.out.println("PASS real HTTPS LAN and browser handlers: authenticated create, persistence, history, retries, reverse, cash, CSRF and role fencing.");System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
 }
 private static void run(String url)throws Exception {
  if(!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres.*")||!Path.of(System.getProperty("user.home")).getFileName().toString().equals("http-profile"))throw new IllegalArgumentException("Disposable fixture required");
  db=DriverManager.getConnection(url,"clock_test","");
  try(var s=db.createStatement()) {
   s.execute("INSERT INTO user_locations(user_id,location_id) VALUES(901,901) ON CONFLICT DO NOTHING");
   s.execute("INSERT INTO locations(location_id,name) VALUES(902,'Stale remote fixture') ON CONFLICT DO NOTHING");
   s.execute("UPDATE devices SET is_approved=true,is_blocked=false,last_store_id=901,api_credential_hash='"+LanSecurity.sha256(DEVICE_TOKEN)+"',api_credential_expires_at=now()+interval '1 hour' WHERE device_id='"+DEVICE+"'");
   s.execute("INSERT INTO lan_api_sessions(session_hash,device_id,user_id,location_id,expires_at,absolute_expires_at,auth_source) VALUES('"+LanSecurity.sha256(SESSION)+"','"+DEVICE+"',901,901,now()+interval '1 hour',now()+interval '2 hours','EMPLOYEE_PIN')");
   s.execute("UPDATE users SET auth_user_id='"+AUTH+"' WHERE user_id=901");
   s.execute("INSERT INTO mobile_managed_devices(device_id,device_name) VALUES('"+BROWSER+"','Isolated phone')");
   s.execute("INSERT INTO mobile_device_apps(device_id,app_key) VALUES('"+BROWSER+"','PHONE_MINUTES')");
   s.execute("INSERT INTO mobile_device_credentials(credential_hash,device_id) VALUES('"+LanSecurity.sha256(WEB_BROWSER)+"','"+BROWSER+"')");
   s.execute("INSERT INTO employee_web_devices(device_id,browser_hash,device_name) VALUES('"+BROWSER+"','"+LanSecurity.sha256(WEB_BROWSER)+"','Isolated browser')");
   s.execute("INSERT INTO employee_web_sessions(session_hash,csrf_hash,browser_device_id,user_id,auth_user_id,expires_at,absolute_expires_at) VALUES('"+LanSecurity.sha256(WEB_SESSION)+"','"+LanSecurity.sha256(CSRF)+"','"+BROWSER+"',901,'"+AUTH+"',now()+interval '1 hour',now()+interval '2 hours')");
   s.execute("UPDATE employee_web_runtime SET enabled=true WHERE runtime_id=1");
   s.execute("INSERT INTO cash_drawers(cash_drawer_id,location_id,drawer_name) VALUES(901,901,'HTTPS Drawer')");
   s.execute("INSERT INTO cash_drawer_sessions(cash_drawer_session_id,cash_drawer_id,location_id,device_id,drawer_name,current_cashier_user_id) VALUES(901,901,901,'"+DEVICE+"','HTTPS Drawer',901)");
  }
  var tls=LanTlsIdentity.loadOrCreate();SSLContext trust=SSLContext.getInstance("TLS");
  trust.init(null,new TrustManager[]{new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Client certificates not expected");}public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{try{chain[0].checkValidity();String pin=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded()));if(!tls.fingerprint().equals(pin))throw new CertificateException("Unexpected fixture certificate");}catch(GeneralSecurityException ex){throw new CertificateException(ex);}}}},null);
  client=HttpClient.newBuilder().sslContext(trust).connectTimeout(java.time.Duration.ofSeconds(5)).build();ServerRoleGuard.update("PRIMARY");
  HttpsServer server=LanHttpsTransport.create(new InetSocketAddress("127.0.0.1",0),tls.sslContext());
  var constructor=LanApiServer.class.getDeclaredConstructor(HttpsServer.class,LanTlsIdentity.class,LanDiscoveryService.class);constructor.setAccessible(true);var api=constructor.newInstance(server,tls,null);
  var routes=LanApiServer.class.getDeclaredMethod("installRoutes");routes.setAccessible(true);routes.invoke(api);server.start();lanPort=server.getAddress().getPort();
  try {
   String key=UUID.randomUUID().toString();JsonObject body=json("{customerId:901,phone:'+5926000123',amount:5140,method:'CREDIT'}");
   check(post("/v1/phone-minutes/create",body,key,false,false).statusCode()==401,"Unpaired request must fail");
   var created=post("/v1/phone-minutes/create",body,key,false,true);check(created.statusCode()==200,created.body());long id=data(created).get("minutesId").getAsLong();
   var repeated=post("/v1/phone-minutes/create",body,key,false,true);check(data(repeated).get("minutesId").getAsLong()==id,"LAN duplicate recorded again");
   check(count("SELECT COUNT(*) FROM phone_minutes_sales WHERE minutes_id="+id)==1,"LAN persistence missing");check(balance()==5160,"LAN debt incorrect");
   check(post("/v1/phone-minutes/history",json("{search:'+5926000123'}"),null,false,true).body().contains("+5926000123"),"LAN history missing destination");
   var reverse=json("{minutesId:"+id+",reason:'Isolated correction'}");check(post("/v1/phone-minutes/reverse",reverse,UUID.randomUUID().toString(),false,true).statusCode()==200,"LAN reverse failed");check(balance()==20,"LAN reverse balance incorrect");
   ServerRoleGuard.update("STANDBY");check(post("/v1/phone-minutes/create",body,UUID.randomUUID().toString(),false,true).statusCode()==503,"Standby allowed create");check(post("/v1/phone-minutes/reverse",reverse,UUID.randomUUID().toString(),false,true).statusCode()==503,"Standby allowed reversal");ServerRoleGuard.update("PRIMARY");
   try(var socket=new java.net.ServerSocket(0)){webPort=socket.getLocalPort();}System.setProperty("smartstock.employee.web.port",String.valueOf(webPort));String origin="https://127.0.0.1:"+webPort;
   try(var web=EmployeeWebServer.start(origin)) {
    var launcher=client.send(HttpRequest.newBuilder(URI.create(origin+"/employees/mobile-management/")).GET().build(),HttpResponse.BodyHandlers.ofString());
    check(launcher.statusCode()==200&&launcher.body().contains("Your workspace")&&launcher.body().contains("Welcome back"),"Deckers workspace or login missing");
    for(String resource:List.of("orders.html","orders.js","orders.css","orders.webmanifest","orders-icon-v2-192.png","inventory.html","customers.html","minutes.html","balance-sheet.html","overseas.html")) {
     var asset=client.send(HttpRequest.newBuilder(URI.create(origin+"/employees/"+resource)).GET().build(),HttpResponse.BodyHandlers.ofString());check(asset.statusCode()==200,"Unified app asset missing: "+resource);
     if(resource.equals("overseas.html"))check(asset.body().contains("data-workspace=\"overseas\""),"Unified overseas workspace missing");
    }
    check(post("/employees/mobile-management/state",json("{}"),null,true,true).body().contains("PHONE_MINUTES"),"Shared device app list missing");
    check(!post("/employees/mobile-management/state",json("{}"),null,true,true).body().contains("RECEIVING"),"Workspace showed an app without a device grant");
    check(post("/employees/mobile-management/open",json("{app:'RECEIVING'}"),null,true,true).statusCode()==403,"Workspace opened an app disabled for this device");
    check(post("/employees/mobile-management/network-challenge",json("{app:'RECEIVING'}"),null,true,true).statusCode()==403,"Disabled app received a LAN challenge");
    repairWorkspaceDevice();
    canonicalWorkspaceOrigin();
    workspaceNetworkProbe(tls);
    unifiedScreens(tls,api);
    try(var statement=db.createStatement()) {
     statement.execute("INSERT INTO mobile_device_pairings(code_hash,device_name,app_keys,issued_by,expires_at) VALUES('"+LanSecurity.sha256("https-pair-fixture")+"','HTTPS paired phone',ARRAY['PHONE_MINUTES'],901,now()+interval '10 minutes')");
    }
    var paired=post("/employees/mobile-management/pair",json("{token:'https-pair-fixture'}"),null,true,false);
    check(paired.statusCode()==200,paired.body());String sharedCookie=paired.headers().allValues("Set-Cookie").stream().filter(value->value.startsWith("ss_mobile_device=")).findFirst().orElseThrow();
    check(sharedCookie.contains("HttpOnly")&&sharedCookie.contains("Secure"),"Shared cookie flags missing");
    check(post("/employees/mobile-management/pair",json("{token:'https-pair-fixture'}"),null,true,false).statusCode()==401,"Shared QR accepted twice");
    var pairedState=HttpRequest.newBuilder(URI.create(origin+"/employees/mobile-management/state")).header("Origin",origin).header("Content-Type","application/json").header("Cookie",sharedCookie.split(";",2)[0]).POST(HttpRequest.BodyPublishers.ofString("{}")).build();
    check(client.send(pairedState,HttpResponse.BodyHandlers.ofString()).statusCode()==401,"Device approval bypassed employee sign-in");
    try(var statement=db.createStatement()){statement.execute("UPDATE employee_web_runtime SET gateway_running=true,gateway_heartbeat_at=now(),public_origin='"+origin+"' WHERE runtime_id=1");}
    long phonesBefore=count("SELECT count(*) FROM mobile_managed_devices");
    var connection=post("/employees/mobile-management/link-request",json("{app:'PHONE_MINUTES'}"),null,true,false);
    check(connection.statusCode()==200,connection.body());String requestId=data(connection).get("requestId").getAsString();
    String claimCookie=connection.headers().allValues("Set-Cookie").stream().filter(value->value.startsWith("ss_mobile_link=")).findFirst().orElseThrow().split(";",2)[0];
    check(!data(connection).has("claim"),"Claim secret exposed in JavaScript response");
    check(!data(linkStatus(origin,requestId,claimCookie)).get("approved").getAsBoolean(),"Home-screen app approved without Safari confirmation");
    check(post("/employees/mobile-management/approve-link",json("{requestId:'"+requestId+"'}"),null,true,false).statusCode()==401,"Unpaired browser approved an installed app");
    var connected=post("/employees/mobile-management/approve-link",json("{requestId:'"+requestId+"'}"),null,true,true);check(connected.statusCode()==200,connected.body());
    check(linkStatus(origin,requestId,"").statusCode()==401,"Connection claimed without the requesting app cookie");
    var claimed=linkStatus(origin,requestId,claimCookie);check(claimed.statusCode()==200&&data(claimed).get("approved").getAsBoolean(),claimed.body());
    check(claimed.headers().allValues("Set-Cookie").stream().anyMatch(value->value.startsWith("ss_mobile_device=")&&value.contains("HttpOnly")&&value.contains("Secure")),"Installed app approval cookie missing");
    check(count("SELECT count(*) FROM mobile_managed_devices")==phonesBefore,"Linking a home-screen app created another phone approval");
    check(linkStatus(origin,requestId,claimCookie).statusCode()==401,"App connection claimed twice");
    var managed=post("/v1/security/mobile-devices/state",json("{}"),null,false,true);
    check(managed.statusCode()==200&&managed.body().contains("PHONE_MINUTES"),"Mobile Management state endpoint failed");
    var enableReceiving=json("{deviceId:'"+BROWSER+"',name:'Isolated phone',revision:1,apps:['PHONE_MINUTES','RECEIVING']}");
    var enabled=post("/v1/security/mobile-devices/update",enableReceiving,UUID.randomUUID().toString(),false,true);
    check(enabled.statusCode()==200,enabled.body());
    check(post("/employees/mobile-management/state",json("{}"),null,true,true).body().contains("RECEIVING"),"Manager app change did not reach the workspace");
    var network=post("/employees/mobile-management/network-challenge",json("{app:'RECEIVING'}"),null,true,true);
    check(network.statusCode()==200,network.body());String networkToken=data(network).get("challenge").getAsString();
    verifyWorkspaceChallenge(tls,networkToken,origin);
    var launch=json("{app:'RECEIVING',challenge:'"+networkToken+"'}");
    check(post("/employees/mobile-management/open",launch,null,true,true).statusCode()==200,"Verified LAN app launch failed");
    check(post("/employees/mobile-management/open",launch,null,true,true).statusCode()==403,"Verified LAN app launch proof was replayed");
    var disableReceiving=json("{deviceId:'"+BROWSER+"',name:'Isolated phone',revision:2,apps:['PHONE_MINUTES']}");
    check(post("/v1/security/mobile-devices/update",disableReceiving,UUID.randomUUID().toString(),false,true).statusCode()==200,"Manager could not disable Receiving");
    check(!post("/employees/mobile-management/state",json("{}"),null,true,true).body().contains("RECEIVING"),"Disabled app remained in the workspace");
    try(var statement=db.createStatement()){statement.execute("INSERT INTO roles(role_id,role_name) VALUES(1901,'Workspace denied fixture')");statement.execute("UPDATE users SET role_id=1901 WHERE user_id=901");}
    check(post("/employees/mobile-management/state",json("{}"),null,true,true).statusCode()==403,"Employee role revocation left workspace access");
    check(post("/v1/security/mobile-devices/repair",json("{deviceId:'"+BROWSER+"'}"),UUID.randomUUID().toString(),false,true).statusCode()==403,"Non-administrator issued a repair QR");
    check(post("/v1/security/mobile-devices/restore",json("{deviceId:'"+BROWSER+"',revision:1}"),UUID.randomUUID().toString(),false,true).statusCode()==403,"Non-administrator restored a device");
    try(var statement=db.createStatement()){statement.execute("UPDATE users SET role_id=1 WHERE user_id=901");}
    check(count("SELECT count(*) FROM mobile_managed_devices")==phonesBefore,"Manager app changes created a second device approval");
    try(var statement=db.createStatement()){statement.execute("DELETE FROM mobile_device_apps WHERE device_id='"+BROWSER+"'");}
    check(post("/employees/api/v1/minutes/bootstrap",json("{}"),null,true,true).statusCode()==403,"Disabled app allowed an existing session");
    try(var statement=db.createStatement()){statement.execute("INSERT INTO mobile_device_apps(device_id,app_key) VALUES('"+BROWSER+"','PHONE_MINUTES')");}
    var boot=post("/employees/api/v1/minutes/bootstrap",json("{}"),null,true,true);check(boot.statusCode()==200,boot.body());check(boot.body().contains("HTTPS Drawer"),"Authorized drawer absent");
    check(post("/employees/api/v1/minutes/bootstrap",json("{}"),null,true,false).statusCode()==401,"Unapproved browser allowed");
    var wrongCsrf=HttpRequest.newBuilder(URI.create(origin+"/employees/api/v1/minutes/create")).header("Origin",origin).header("Content-Type","application/json").header("Cookie",cookies()).header("X-CSRF-Token","wrong").POST(HttpRequest.BodyPublishers.ofString("{}")).build();check(client.send(wrongCsrf,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"CSRF check missing");
    var webBody=json("{customerId:901,amount:1000,method:'CREDIT',idempotencyKey:'"+UUID.randomUUID()+"'}");
    var first=post("/employees/api/v1/minutes/create",webBody,null,true,true);check(first.statusCode()==200,first.body());long webId=data(first).get("minutesId").getAsLong();check(balance()==1020,"Browser credit not persisted");
    check(data(post("/employees/api/v1/minutes/create",webBody,null,true,true)).get("minutesId").getAsLong()==webId,"Browser retry duplicated");
    check(post("/employees/api/v1/minutes/reverse",json("{minutesId:"+webId+",reason:'Fixture reversal',idempotencyKey:'"+UUID.randomUUID()+"'}"),null,true,true).statusCode()==200,"Browser reverse failed");check(balance()==20,"Browser reverse balance incorrect");
    var cash=json("{phone:'6000666',amount:30,method:'CASH',drawerSessionId:901,idempotencyKey:'"+UUID.randomUUID()+"'}");var cashResponse=post("/employees/api/v1/minutes/create",cash,null,true,true);check(cashResponse.statusCode()==200,cashResponse.body());long cashId=data(cashResponse).get("minutesId").getAsLong();check(CashDrawerService.calculateNetCashActivity(db,901).intValueExact()==30,"Browser cash not counted once");
    var cashReverse=json("{minutesId:"+cashId+",reason:'Fixture refund',drawerSessionId:901,idempotencyKey:'"+UUID.randomUUID()+"'}");var cashRefund=post("/employees/api/v1/minutes/reverse",cashReverse,null,true,true);check(cashRefund.statusCode()==200,cashRefund.body());check(CashDrawerService.calculateNetCashActivity(db,901).signum()==0,"Browser refund incorrect");
    var history=post("/employees/api/v1/minutes/history",json("{customerId:901}"),null,true,true);check(history.statusCode()==200&&history.body().contains("MIN-"+webId),"Browser history missing");
    var revoke=json("{deviceId:'"+BROWSER+"',name:'Isolated phone',revision:3,apps:['PHONE_MINUTES'],revoke:true}");
    check(post("/v1/security/mobile-devices/update",revoke,UUID.randomUUID().toString(),false,true).statusCode()==200,"Manager phone revocation failed");
    check(post("/employees/mobile-management/state",json("{}"),null,true,true).statusCode()==401,"Revoked phone retained workspace access");
    check(post("/employees/api/v1/minutes/bootstrap",json("{}"),null,true,true).statusCode()==401,"Revoked phone retained native app access");
   }
  } finally {api.close();db.close();}
 }
 private static void unifiedScreens(LanTlsIdentity tls,LanApiServer owner)throws Exception {
  List<String> previous=MobileDeviceService.grants(db,BROWSER);
  // The managed device ID differs from the browser-session row ID.
  UUID managed=MobileDeviceService.identity(db,WEB_BROWSER);previous=MobileDeviceService.grants(db,managed);
  var mobileConstructor=MobileItemWebServer.class.getDeclaredConstructor(HttpsServer.class,HttpsServer.class,java.util.concurrent.ExecutorService.class,LanApiServer.class,String.class);mobileConstructor.setAccessible(true);
  var mobile=mobileConstructor.newInstance(null,null,null,owner,"127.0.0.1");
  var mobileActive=MobileItemWebServer.class.getDeclaredField("active");mobileActive.setAccessible(true);Object oldMobile=mobileActive.get(null);mobileActive.set(null,mobile);
  var schedulerConstructor=SchedulerWebServer.class.getDeclaredConstructor(HttpsServer.class,java.util.concurrent.ExecutorService.class,String.class);schedulerConstructor.setAccessible(true);
  var scheduler=schedulerConstructor.newInstance(null,null,"https://127.0.0.1:"+webPort);
  var schedulerActive=SchedulerWebServer.class.getDeclaredField("active");schedulerActive.setAccessible(true);Object oldScheduler=schedulerActive.get(null);schedulerActive.set(null,scheduler);
  try {
   try(var statement=db.createStatement()) {statement.execute("UPDATE mobile_item_web_runtime SET enabled=true");statement.execute("UPDATE scheduler_web_runtime SET enabled=true");}
   try(var statement=db.createStatement();var rows=statement.executeQuery("SELECT generation FROM scheduler_web_runtime WHERE runtime_id=1")){check(rows.next(),"Scheduler runtime fixture missing");var generation=SchedulerWebServer.class.getDeclaredField("generation");generation.setAccessible(true);generation.set(scheduler,rows.getObject(1));}
   MobileDeviceService.replaceGrants(db,managed,MobileDeviceService.APPS.stream().map(MobileDeviceService.App::key).toList());
   for(var app:MobileDeviceService.APPS) {
    JsonObject packet=json("{app:'"+app.key()+"'}");
    if(app.gateway().equals("mobile")) {
     check(post("/employees/mobile-management/screen",packet,null,true,true).statusCode()==400,"LAN screen opened without proof");
     var challenge=post("/employees/mobile-management/network-challenge",packet,null,true,true);check(challenge.statusCode()==200,challenge.body());String token=data(challenge).get("challenge").getAsString();verifyWorkspaceChallenge(tls,token,"https://127.0.0.1:"+webPort);packet.addProperty("challenge",token);
    }
    var opened=post("/employees/mobile-management/screen",packet,null,true,true);check(opened.statusCode()==200,app.key()+": "+opened.body());check(data(opened).has("body")&&data(opened).has("module")&&!data(opened).has("url"),"Screen was a navigation handoff");
   }
   check(post("/employees/mobile-management/screen-api",json("{app:'PHONE_MINUTES',route:'minutes/bootstrap',payload:{}}"),null,true,true).statusCode()==200,"Shared employee session did not reach Phone Minutes");
   check(post("/employees/mobile-management/screen-api",json("{app:'INVENTORY',route:'customers/save',payload:{}}"),null,true,true).statusCode()==403,"Screen crossed an app permission boundary");
   var forged=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+webPort+"/employees/mobile-management/screen-api")).header("Origin","https://127.0.0.1:"+webPort).header("Cookie",cookies()).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"app\":\"PHONE_MINUTES\",\"route\":\"minutes/bootstrap\",\"payload\":{}}"));
   check(client.send(forged.build(),HttpResponse.BodyHandlers.ofString()).statusCode()==403,"Shared screen API accepted missing CSRF");
   JsonObject stock=json("{app:'RECEIVING',route:'mobile/bootstrap',payload:{}}");
   check(post("/employees/mobile-management/screen-api",stock,null,true,true).statusCode()==400,"Stock request did not require fresh LAN proof");
   var challenge=post("/employees/mobile-management/network-challenge",stock,null,true,true);String token=data(challenge).get("challenge").getAsString();verifyWorkspaceChallenge(tls,token,"https://127.0.0.1:"+webPort);stock.addProperty("challenge",token);
   var stockReply=post("/employees/mobile-management/screen-api",stock,null,true,true);check(stockReply.statusCode()==200,stockReply.body());check(data(stockReply).get("locationId").getAsInt()==901,"Stock bridge used a different store");
   check(post("/employees/mobile-management/screen-api",stock,null,true,true).statusCode()==403,"Stock operation replayed a LAN proof");
   var schedule=post("/employees/mobile-management/screen-api",json("{app:'SCHEDULER',route:'scheduler/bootstrap',payload:{}}"),null,true,true);check(schedule.statusCode()==200,schedule.body());
   check(count("SELECT COUNT(*) FROM mobile_item_web_sessions")==0,"Workspace required a second mobile login");
   MobileDeviceService.replaceGrants(db,managed,List.of("PHONE_MINUTES"));
   check(post("/employees/mobile-management/screen-api",json("{app:'SCHEDULER',route:'scheduler/bootstrap',payload:{}}"),null,true,true).statusCode()==403,"Disabled device screen retained API access");
  }finally {MobileDeviceService.replaceGrants(db,managed,previous);mobileActive.set(null,oldMobile);schedulerActive.set(null,oldScheduler);}
 }
 private static void repairWorkspaceDevice()throws Exception {
  UUID device=MobileDeviceService.identity(db,WEB_BROWSER);List<String> grants=MobileDeviceService.grants(db,device);long countBefore=count("SELECT count(*) FROM mobile_managed_devices");
  String key=UUID.randomUUID().toString();JsonObject body=json("{deviceId:'"+device+"'}");
  var issued=post("/v1/security/mobile-devices/repair",body,key,false,true);check(issued.statusCode()==200,issued.body());
  var repeated=post("/v1/security/mobile-devices/repair",body,key,false,true);check(repeated.statusCode()==200&&data(issued).get("url").equals(data(repeated).get("url")),"Repair QR request was not idempotent");
  String url=data(issued).get("url").getAsString();check(url.startsWith(DeckersWorkspace.HOME+"#repair="),"Repair QR used another entry");String token=URI.create(url).getFragment().substring(7);
  var repaired=post("/employees/mobile-management/repair",json("{token:'"+token+"'}"),null,true,false);check(repaired.statusCode()==200,repaired.body());
  String cookie=repaired.headers().allValues("Set-Cookie").stream().filter(value->value.startsWith("ss_mobile_device=")).findFirst().orElseThrow();check(cookie.contains("Secure")&&cookie.contains("HttpOnly"),"Repaired browser cookie flags missing");
  String credential=cookie.split(";",2)[0].substring("ss_mobile_device=".length());check(MobileDeviceService.identity(db,credential).equals(device),"Repair created a different approval");
  check(count("SELECT count(*) FROM mobile_managed_devices")==countBefore,"Repair created a duplicate device");check(MobileDeviceService.grants(db,device).equals(grants),"Repair changed enabled apps");
  check(repaired.headers().allValues("Set-Cookie").stream().anyMatch(value->value.startsWith("ss_employee=;")&&value.contains("Max-Age=0")),"Repair retained an employee login");
  check(post("/employees/mobile-management/repair",json("{token:'"+token+"'}"),null,true,false).statusCode()==401,"Repair QR was replayed");
  String origin="https://127.0.0.1:"+webPort;
  var inherited=HttpRequest.newBuilder(URI.create(origin+"/employees/api/v1/auth/session")).header("Origin",origin).header("Cookie","ss_mobile_device="+credential+"; ss_employee="+WEB_SESSION).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
  check(client.send(inherited,HttpResponse.BodyHandlers.ofString()).statusCode()==401,"Repaired browser inherited the previous employee session");
  var expired=post("/v1/security/mobile-devices/repair",body,UUID.randomUUID().toString(),false,true);String expiredToken=URI.create(data(expired).get("url").getAsString()).getFragment().substring(7);
  try(var q=db.prepareStatement("UPDATE mobile_device_handoffs SET expires_at=now()-interval '1 second' WHERE token_hash=?")){q.setString(1,LanSecurity.sha256(expiredToken));q.executeUpdate();}
  check(post("/employees/mobile-management/repair",json("{token:'"+expiredToken+"'}"),null,true,false).statusCode()==401,"Expired repair QR was accepted");
  UUID revoked=UUID.randomUUID();try(var q=db.prepareStatement("INSERT INTO mobile_managed_devices(device_id,device_name) VALUES(?,'Revoked repair fixture')")){q.setObject(1,revoked);q.executeUpdate();}
  var revokedQr=post("/v1/security/mobile-devices/repair",json("{deviceId:'"+revoked+"'}"),UUID.randomUUID().toString(),false,true);check(revokedQr.statusCode()==200,revokedQr.body());String revokedToken=URI.create(data(revokedQr).get("url").getAsString()).getFragment().substring(7);
  try(var q=db.prepareStatement("UPDATE mobile_managed_devices SET revoked_at=now() WHERE device_id=?")){q.setObject(1,revoked);q.executeUpdate();}
  check(post("/employees/mobile-management/repair",json("{token:'"+revokedToken+"'}"),null,true,false).statusCode()==403,"Repair restored a revoked device");
  check(post("/v1/security/mobile-devices/repair",json("{deviceId:'"+revoked+"'}"),UUID.randomUUID().toString(),false,true).statusCode()==403,"A repair QR was issued for a revoked device");
  MobileDeviceService.replaceGrants(db,revoked,List.of("INVENTORY"));MobileDeviceService.addCredential(db,revoked,"isolated-restore-credential");
  var staleRestore=post("/v1/security/mobile-devices/restore",json("{deviceId:'"+revoked+"',revision:0}"),UUID.randomUUID().toString(),false,true);check(staleRestore.statusCode()==409,"Stale device revision restored access");
  String restoreKey=UUID.randomUUID().toString();JsonObject restore=json("{deviceId:'"+revoked+"',revision:1,apps:['ORDERS']}");
  var restored=post("/v1/security/mobile-devices/restore",restore,restoreKey,false,true);check(restored.statusCode()==200,restored.body());
  check(MobileDeviceService.identity(db,"isolated-restore-credential").equals(revoked),"Restoration did not restore the existing browser");
  check(MobileDeviceService.grants(db,revoked).equals(List.of("INVENTORY")),"Restoration changed enabled apps");
  check(post("/v1/security/mobile-devices/restore",restore,restoreKey,false,true).statusCode()==200,"Restore retry was not idempotent");
  check(count("SELECT revision FROM mobile_managed_devices WHERE device_id='"+revoked+"'")==2,"Restore retry changed the revision again");
  check(post("/v1/security/mobile-devices/restore",restore,UUID.randomUUID().toString(),false,true).statusCode()==409,"An already-approved device was restored again");
  check(count("SELECT count(*) FROM security_audit_events WHERE event_type='MOBILE_DEVICE_RESTORED' AND actor_user_id=901 AND details LIKE '%"+revoked+"%'")==1,"Restore audit was missing or duplicated");


 }
 private static void canonicalWorkspaceOrigin()throws Exception {
  String base="https://127.0.0.1:"+webPort,canonical="https://deckersapps.deckers.gy";
  for(String route:List.of("/employees/mobile-management/state","/employees/api/v1/minutes/bootstrap")) {
   var request=HttpRequest.newBuilder(URI.create(base+route)).header("Origin",canonical).header("Cookie",cookies()).header("X-CSRF-Token",CSRF).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
   var response=client.send(request,HttpResponse.BodyHandlers.ofString());check(response.statusCode()==200,"Dedicated Deckers origin rejected: "+response.body());
   if(route.endsWith("/state"))check(data(response).get("screens").getAsBoolean(),"Dedicated Deckers entry cannot mount screens");
   var evil=HttpRequest.newBuilder(URI.create(base+route)).header("Origin",canonical+".evil.example").header("Cookie",cookies()).header("X-CSRF-Token",CSRF).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
   check(client.send(evil,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"Lookalike Deckers origin accepted");
  }
 }
 private static HttpResponse<String> post(String path,JsonObject body,String key,boolean web,boolean auth)throws Exception {
  String origin="https://127.0.0.1:"+(web?webPort:lanPort);var b=HttpRequest.newBuilder(URI.create(origin+path)).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
  if(web){b.header("Origin",origin);if(auth)b.header("Cookie",cookies()).header("X-CSRF-Token",CSRF);}else if(auth)b.header("X-SmartStock-Device",DEVICE_TOKEN).header("Authorization","Bearer "+SESSION);
  if(key!=null)b.header("Idempotency-Key",key);return client.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 private static void workspaceNetworkProbe(LanTlsIdentity tls)throws Exception {
  HttpsServer probe=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),20);
  probe.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(tls.sslContext()));
  String caller="https://timeclock.deckers.gy";
  probe.createContext("/mobile-management/",exchange->{try{MobileDeviceService.handle(exchange,"/mobile-management/","https://127.0.0.1:"+probe.getAddress().getPort());}catch(Exception e){throw new RuntimeException(e);}finally{exchange.close();}});
  probe.start();
  String local="https://127.0.0.1:"+probe.getAddress().getPort();
  try {
   String token=DeckersWorkspace.challenge(BROWSER,901,"RECEIVING",caller);
   URI endpoint=URI.create(local+"/mobile-management/network-probe");
   var preflight=HttpRequest.newBuilder(endpoint).header("Origin",caller).header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","content-type").header("Access-Control-Request-Private-Network","true").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
   var ready=client.send(preflight,HttpResponse.BodyHandlers.ofString());check(ready.statusCode()==204,"LAN preflight failed: "+ready.statusCode()+" "+ready.body());check(ready.headers().firstValue("Access-Control-Allow-Origin").orElse("").equals(caller),"LAN CORS origin missing");
   var forwarded=HttpRequest.newBuilder(endpoint).header("Origin",caller).header("Content-Type","application/json").header("X-Forwarded-For","127.0.0.1").POST(HttpRequest.BodyPublishers.ofString("{\"challenge\":\""+token+"\"}")).build();
   check(client.send(forwarded,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"Forwarded request verified a LAN challenge");
   check(!DeckersWorkspace.verify(token,"https://evil.example"),"Wrong origin verified a LAN challenge");
   var request=HttpRequest.newBuilder(endpoint).header("Origin",caller).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"challenge\":\""+token+"\"}")).build();
   check(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()==200,"Direct LAN probe failed");
   DeckersWorkspace.consume(token,BROWSER,901,"RECEIVING",caller);
   check(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()==403,"LAN proof replay accepted");
  }finally{probe.stop(0);}
 }
 private static void verifyWorkspaceChallenge(LanTlsIdentity tls,String token,String caller)throws Exception {
  HttpsServer probe=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),20);
  probe.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(tls.sslContext()));
  probe.createContext("/mobile-management/",exchange->{try{MobileDeviceService.handle(exchange,"/mobile-management/","https://127.0.0.1:"+probe.getAddress().getPort());}catch(Exception e){throw new RuntimeException(e);}finally{exchange.close();}});
  probe.start();
  try {
   var request=HttpRequest.newBuilder(URI.create("https://127.0.0.1:"+probe.getAddress().getPort()+"/mobile-management/network-probe")).header("Origin",caller).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"challenge\":\""+token+"\"}")).build();
   check(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()==200,"Workspace challenge did not verify at the LAN listener");
  }finally{probe.stop(0);}
 }
 private static String cookies(){return "ss_mobile_device="+WEB_BROWSER+"; ss_employee="+WEB_SESSION;}
 private static HttpResponse<String> linkStatus(String origin,String request,String cookie)throws Exception {
  return client.send(HttpRequest.newBuilder(URI.create(origin+"/employees/mobile-management/link-status")).header("Origin",origin).header("Content-Type","application/json").header("Cookie",cookie).POST(HttpRequest.BodyPublishers.ofString("{\"requestId\":\""+request+"\"}")).build(),HttpResponse.BodyHandlers.ofString());
 }
 private static JsonObject data(HttpResponse<String> response){return json(response.body()).getAsJsonObject("data");}
 private static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
 private static void check(boolean good,String message){if(!good)throw new AssertionError(message);}
 private static long count(String sql)throws SQLException{try(var s=db.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
 private static int balance()throws SQLException{return CustomerAccountLedgerService.calculateCustomerBalance(db,901).intValueExact();}
}
