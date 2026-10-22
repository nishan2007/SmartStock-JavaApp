package services;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in disposable cluster only. Every fixture is a temporary table, never a live store. */
class EmployeeWebDatabaseTest {
    private static final Set<String> SELF=Set.of("ACCESS_EMPLOYEE_WEB"),ALL=Set.of("ACCESS_EMPLOYEE_WEB","VIEW_ALL_EMPLOYEE_WEB");
    private Connection open()throws Exception {
        String url=System.getProperty("smartstock.employee.test.jdbc","");assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:55443/postgres"))throw new IllegalArgumentException("Use the disposable employee web cluster.");
        Connection c=DriverManager.getConnection(url,"employee_web_test","");
        try(Statement s=c.createStatement()) {
            s.execute("CREATE TEMP TABLE users(user_id int PRIMARY KEY,username text,full_name text,is_active boolean,role_id int,auth_user_id uuid,compensation_type text,salary numeric)");
            s.execute("CREATE TEMP TABLE roles(role_id int PRIMARY KEY,role_name text)");
            s.execute("CREATE TEMP TABLE permissions(permission_id int,permission_key text)");
            s.execute("CREATE TEMP TABLE role_permissions(role_id int,permission_id int)");
            s.execute("CREATE TEMP TABLE role_mobile_permissions(role_id int,permission_key text)");
            s.execute("CREATE TEMP TABLE security_audit_events(event_type text,actor_user_id int,details text)");
            s.execute("CREATE TEMP TABLE locations(location_id int PRIMARY KEY,name text,timezone text)");
            s.execute("CREATE TEMP TABLE user_locations(user_id int,location_id int)");
            s.execute("CREATE TEMP TABLE employee_daily_pay_days(user_id int,work_date date,half_day boolean)");
            s.execute("CREATE TEMP TABLE employee_payroll_settings(setting_id uuid,user_id int,period_type text,work_hour_limit numeric,effective_from date,updated_at timestamptz,compensation_type text,pay_rate numeric,created_by_user_id int,created_by_name text)");
            s.execute("CREATE TEMP TABLE employee_time_clock(clock_id serial PRIMARY KEY,user_id int,user_name text,work_date date,location_id int,location_name text,clock_in timestamptz,lunch_start timestamptz,lunch_end timestamptz,break_start timestamptz,break_end timestamptz,clock_out timestamptz,total_hours_worked numeric,total_earned numeric,auto_clock_out boolean,auto_clock_out_review_status text,auto_break_end boolean,auto_break_end_review_status text,updated_at timestamptz DEFAULT CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO roles VALUES(1,'Employee')");
            s.execute("INSERT INTO users VALUES(1,'alice','Alice',true,1,'00000000-0000-0000-0000-000000000001','HOURLY',100),(2,'bob','Bob',false,1,'00000000-0000-0000-0000-000000000002','DAILY',500),(3,'salary','Salary',true,1,'00000000-0000-0000-0000-000000000003','SALARY',10000)");
            s.execute("INSERT INTO locations VALUES(1,'Main','America/Guyana'),(2,'Other','UTC')");
            s.execute("INSERT INTO user_locations VALUES(1,1),(2,1),(3,1)");
            s.execute("INSERT INTO role_mobile_permissions VALUES(1,'ACCESS_EMPLOYEE_WEB')");
            s.execute("INSERT INTO employee_payroll_settings VALUES(gen_random_uuid(),1,'SEMI_MONTHLY',8,'2026-10-01',now(),'HOURLY',100,NULL,NULL)");
        }
        String migration=Files.readString(Path.of("database/migrations/v1_after/20261020120000_employee_web.sql"))
                .replace("CREATE TABLE IF NOT EXISTS public.","CREATE TEMP TABLE IF NOT EXISTS ").replace("public.","");
        SqlScriptRunner.runSql(c,migration);c.setAutoCommit(false);return c;
    }
    private int shift(Connection c,int user,String start,String end,int location)throws Exception {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_time_clock(user_id,work_date,clock_in,clock_out,location_id) VALUES(?,?::date,?::timestamptz,?::timestamptz,?) RETURNING clock_id")) {
            p.setInt(1,user);p.setString(2,start.substring(0,10));p.setString(3,start);p.setString(4,end);p.setInt(5,location);
            try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> data,String key){return (List<Map<String,Object>>)data.get(key);}
    private Map<String,Object> history(Connection c,int user,String start,String end)throws Exception {
        return EmployeeWebHistoryService.history(c,1,user,ALL,LocalDate.parse(start),LocalDate.parse(end),0);
    }
    @Test void completeContextOvertimeOvernightBreaksAndCorrections()throws Exception {
        try(Connection c=open()) {
            shift(c,1,"2026-10-01 08:00-04","2026-10-01 16:00-04",2);
            int overnight=shift(c,1,"2026-10-02 22:00-04","2026-10-03 06:00-04",1);
            try(Statement s=c.createStatement()){s.execute("UPDATE employee_time_clock SET lunch_start='2026-10-03 00:00-04',lunch_end='2026-10-03 00:30-04',break_start='2026-10-03 02:00-04',break_end='2026-10-03 02:15-04' WHERE clock_id="+overnight);}
            var session=rows(history(c,1,"2026-10-02","2026-10-02"),"sessions").get(0);
            assertEquals(overnight,session.get("clockId"));assertEquals(7.25,((Number)session.get("hours")).doubleValue(),.001);
            // SmartStock normalizes currency to whole dollars, including each payroll segment.
            assertEquals(1088,((Number)session.get("calculatedPay")).doubleValue(),.001);
            assertTrue(session.get("clockOut").toString().startsWith("2026-10-03T06:00"));
            try(Statement s=c.createStatement()){s.execute("UPDATE employee_time_clock SET clock_out='2026-10-03 07:00-04' WHERE clock_id="+overnight);}
            session=rows(history(c,1,"2026-10-02","2026-10-02"),"sessions").get(0);
            assertEquals(1238,((Number)session.get("calculatedPay")).doubleValue(),.001);
            assertThrows(SecurityException.class,()->EmployeeWebHistoryService.history(c,1,2,SELF,null,null,0));
        }
    }
    @Test void dailyHalfDayMultipleSessionsSalaryInactiveAndPagination()throws Exception {
        try(Connection c=open()) {
            shift(c,2,"2026-10-02 08:00-04","2026-10-02 12:00-04",1);
            shift(c,2,"2026-10-02 13:00-04","2026-10-02 17:00-04",1);
            try(Statement s=c.createStatement()){s.execute("INSERT INTO employee_daily_pay_days VALUES(2,'2026-10-02',true)");}
            var sessions=rows(history(c,2,"2026-10-02","2026-10-02"),"sessions");
            assertEquals(250,sessions.stream().mapToDouble(r->((Number)r.get("calculatedPay")).doubleValue()).sum(),.001);
            shift(c,3,"2026-10-02 08:00-04","2026-10-02 16:00-04",1);
            assertNull(rows(history(c,3,"2026-10-02","2026-10-02"),"sessions").get(0).get("calculatedPay"));
            assertEquals(0,rows(EmployeeWebHistoryService.employees(c,1,ALL,"Bob",false,0),"employees").size());
            assertEquals(1,rows(EmployeeWebHistoryService.employees(c,1,ALL,"bob",true,0),"employees").size());
            for(int i=0;i<51;i++)shift(c,1,"2026-10-04 08:00Z","2026-10-04 09:00Z",2);
            var page=history(c,1,"2026-10-04","2026-10-04");assertEquals(50,rows(page,"sessions").size());assertEquals(true,page.get("hasMore"));
            var last=EmployeeWebHistoryService.history(c,1,1,SELF,LocalDate.of(2026,10,4),LocalDate.of(2026,10,4),50);
            assertEquals(1,rows(last,"sessions").size());assertEquals(false,last.get("hasMore"));
            assertEquals("UTC",rows(last,"sessions").get(0).get("timezone"));
            // The newest session is overtime even when earlier regular hours are on another page.
            assertEquals(150,((Number)rows(page,"sessions").get(0).get("calculatedPay")).doubleValue(),.001);
            assertEquals(100,((Number)rows(last,"sessions").get(0).get("calculatedPay")).doubleValue(),.001);
        }
    }
    @Test void openAndMissingSessionsRemainVisible()throws Exception {
        try(Connection c=open()) {
            String start=LocalDate.now(ZoneId.of("America/Guyana"))+" 08:00-04";
            shift(c,1,start,null,1);
            try(Statement s=c.createStatement()){s.execute("INSERT INTO employee_time_clock(user_id,work_date) VALUES(1,CURRENT_DATE)");}
            var sessions=rows(EmployeeWebHistoryService.history(c,1,1,SELF,null,null,0),"sessions");
            assertTrue(sessions.stream().anyMatch(r->Boolean.TRUE.equals(r.get("provisional"))&&r.get("clockIn")!=null));
            assertTrue(sessions.stream().anyMatch(r->r.get("clockIn")==null&&r.get("hours")==null));
        }
    }

    private EmployeeWebServer app()throws Exception {
        var constructor=EmployeeWebServer.class.getDeclaredConstructor(HttpsServer.class,java.util.concurrent.ExecutorService.class,String.class,UUID.class);
        constructor.setAccessible(true);return constructor.newInstance(null,null,"https://employees.example.test",UUID.randomUUID());
    }
    private Object call(Object app,String name,Object...args)throws Exception {
        Method m=Arrays.stream(app.getClass().getDeclaredMethods()).filter(v->v.getName().equals(name)).findFirst().orElseThrow();m.setAccessible(true);
        try{return m.invoke(app,args);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    @Test void grantsExpireRedeemOnceAndRevocationBlocksBrowser()throws Exception {
        try(Connection c=open()) {
            try(Statement s=c.createStatement()){s.execute("INSERT INTO employee_web_grants(grant_hash,device_name,expires_at) VALUES('"+LanSecurity.sha256("expired")+"','Expired',now()-interval '1 second'),('"+LanSecurity.sha256("valid")+"','Phone',now()+interval '10 minutes')");}
            EmployeeWebServer app=app();Exchange x=new Exchange();JsonObject b=new JsonObject();b.addProperty("token","expired");
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"activate",c,x,b));
            b.addProperty("token","valid");call(app,"activate",c,x,b);
            x.request.set("Cookie",x.response.get("Set-Cookie").stream().filter(v->v.startsWith("ss_employee_browser=")).findFirst().orElseThrow().split(";",2)[0]);
            UUID device=(UUID)call(app,"requireBrowser",c,x);
            try(Statement s=c.createStatement()) {
                s.execute("INSERT INTO employee_web_sessions(session_hash,csrf_hash,browser_device_id,user_id,auth_user_id,expires_at,absolute_expires_at) VALUES('revocation-session','csrf','"+device+"',1,'00000000-0000-0000-0000-000000000001',now()+interval '15 minutes',now()+interval '8 hours')");
            }
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"activate",c,new Exchange(),b));
            JsonObject revoke=new JsonObject();revoke.addProperty("action","REVOKE");revoke.addProperty("deviceId",device.toString());
            EmployeeWebAdminService.execute(c,1,revoke);
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"requireBrowser",c,x));
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT revoked_at IS NOT NULL FROM employee_web_sessions WHERE session_hash='revocation-session'")) {r.next();assertTrue(r.getBoolean(1));}
        }
    }
    @Test void sessionsLogoutExpiryAndAccountPermissionChanges()throws Exception {
        try(Connection c=open()) {
            UUID browser=UUID.randomUUID();
            try(Statement s=c.createStatement()) {
                s.execute("INSERT INTO employee_web_devices(device_id,browser_hash,device_name) VALUES('"+browser+"','browser','Phone')");
                s.execute("INSERT INTO employee_web_sessions(session_hash,csrf_hash,browser_device_id,user_id,auth_user_id,expires_at,absolute_expires_at) VALUES('"+LanSecurity.sha256("session")+"','"+LanSecurity.sha256("csrf")+"','"+browser+"',1,'00000000-0000-0000-0000-000000000001',now()+interval '15 minutes',now()+interval '8 hours')");
            }
            EmployeeWebServer app=app();Exchange x=new Exchange();x.request.set("Cookie","ss_employee=session");x.request.set("X-CSRF-Token","csrf");
            Object session=call(app,"requireSession",c,x,browser,true);
            try(Statement s=c.createStatement()){s.execute("UPDATE users SET is_active=false WHERE user_id=1");}
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"requireSession",c,x,browser,true));
            try(Statement s=c.createStatement()){s.execute("UPDATE users SET is_active=true WHERE user_id=1");s.execute("DELETE FROM role_mobile_permissions");}
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"requireSession",c,x,browser,true));
            try(Statement s=c.createStatement()){s.execute("INSERT INTO role_mobile_permissions VALUES(1,'ACCESS_EMPLOYEE_WEB')");s.execute("UPDATE employee_web_sessions SET absolute_expires_at=now()-interval '1 second'");}
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"requireSession",c,x,browser,true));
            try(Statement s=c.createStatement()){s.execute("UPDATE employee_web_sessions SET absolute_expires_at=now()+interval '8 hours'");}
            try(Statement s=c.createStatement()){s.execute("UPDATE employee_web_sessions SET expires_at=now()-interval '1 second'");}
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"requireSession",c,x,browser,true));
            try(Statement s=c.createStatement()){s.execute("UPDATE employee_web_sessions SET expires_at=now()+interval '15 minutes'");}
            call(app,"logout",c,x,session);
            assertThrows(EmployeeWebServer.WebError.class,()->call(app,"requireSession",c,x,browser,true));
        }
    }

    private static final class Exchange extends HttpExchange {
        final Headers request=new Headers(),response=new Headers();
        public Headers getRequestHeaders(){return request;}public Headers getResponseHeaders(){return response;}
        public URI getRequestURI(){return URI.create("https://employees.example.test/employees/");}public String getRequestMethod(){return "POST";}
        public HttpContext getHttpContext(){return null;}public void close(){}public InputStream getRequestBody(){return InputStream.nullInputStream();}
        public OutputStream getResponseBody(){return OutputStream.nullOutputStream();}public void sendResponseHeaders(int s,long n){}
        public InetSocketAddress getRemoteAddress(){return new InetSocketAddress("127.0.0.1",1234);}public int getResponseCode(){return 200;}
        public InetSocketAddress getLocalAddress(){return new InetSocketAddress("127.0.0.1",EmployeeWebServer.DEFAULT_PORT);}public String getProtocol(){return "HTTP/1.1";}
        public Object getAttribute(String n){return null;}public void setAttribute(String n,Object v){}public void setStreams(InputStream i,OutputStream o){}
        public HttpPrincipal getPrincipal(){return null;}
    }
}
