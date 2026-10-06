package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs only against an explicitly selected disposable loopback cluster. */
class ManualTimeClockDatabaseTest {
    private Connection open() throws Exception {
        String url=System.getProperty("smartstock.manual.test.jdbc","");
        assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:55441/postgres")) throw new IllegalArgumentException("Use the isolated manual-hours test cluster.");
        Connection c=DriverManager.getConnection(url,"manual_test","");
        c.setAutoCommit(false);
        try(var s=c.createStatement()) {
            s.execute("CREATE TEMP TABLE users(user_id int PRIMARY KEY,full_name text,username text,is_active boolean,compensation_type text,salary numeric)");
            s.execute("CREATE TEMP TABLE locations(location_id int PRIMARY KEY,name text,timezone text)");
            s.execute("CREATE TEMP TABLE user_locations(user_id int,location_id int)");
            s.execute("CREATE TEMP TABLE employee_registrations(employee_id int,status text)");
            s.execute("CREATE TEMP TABLE employee_payroll_settings(user_id int,effective_from date,updated_at timestamptz,compensation_type text,pay_rate numeric,period_type text DEFAULT 'SEMI_MONTHLY')");
            s.execute("CREATE TEMP TABLE employee_time_clock(clock_id bigserial PRIMARY KEY,user_id int,user_name text,location_id int,location_name text,work_date date,clock_in timestamptz,lunch_start timestamptz,lunch_end timestamptz,break_start timestamptz,break_end timestamptz,clock_out timestamptz,total_hours_worked numeric,total_earned numeric)");
            s.execute("CREATE TEMP TABLE lan_api_idempotency(device_id uuid,idempotency_key text,operation_key text,request_hash text,response_status int,response_body text,completed_at timestamptz,PRIMARY KEY(device_id,idempotency_key))");
            s.execute("INSERT INTO locations VALUES(1,'Store','America/Guyana'),(2,'Other Store','America/Guyana')");
            s.execute("INSERT INTO users VALUES(1,'Hourly','hourly',true,'HOURLY',100),(2,'Daily','daily',true,'DAILY',500),(3,'Salary','salary',true,'SALARY',10000),(4,'Inactive','inactive',false,'HOURLY',100),(5,'Other store','other',true,'HOURLY',100),(6,'Pending','pending',true,'HOURLY',100)");
            s.execute("INSERT INTO user_locations VALUES(5,2)");
            s.execute("INSERT INTO employee_registrations VALUES(6,'PENDING')");
            s.execute("INSERT INTO employee_payroll_settings(user_id,effective_from,updated_at,compensation_type,pay_rate) VALUES(1,'2026-01-01',now(),'HOURLY',80),(1,'2026-10-01',now(),'HOURLY',120)");
        }
        return c;
    }
    private TimeClockAutoCloseService.Correction entry(String start,String end) {
        return new TimeClockAutoCloseService.Correction(LocalDateTime.parse(start),null,null,null,null,LocalDateTime.parse(end),"Work away from store");
    }
    @Test void historicalPayEligibilityOverlapAndSeparateSessions() throws Exception {
        try(Connection c=open()) {
            assertEquals(3,ManualTimeClockService.employees(c,1).size());
            long id=ManualTimeClockService.create(c,1,1,entry("2026-09-30T08:00","2026-09-30T12:00"));
            try(var p=c.prepareStatement("SELECT total_hours_worked,total_earned,work_date,location_id FROM employee_time_clock WHERE clock_id=?")) {
                p.setLong(1,id);
                try(var r=p.executeQuery()) { assertTrue(r.next()); assertEquals(4,r.getBigDecimal(1).intValue()); assertEquals(320,r.getBigDecimal(2).intValue()); assertEquals("2026-09-30",r.getString(3)); assertEquals(1,r.getInt(4)); }
            }
            assertThrows(SQLException.class,()->ManualTimeClockService.create(c,1,1,entry("2026-09-30T11:00","2026-09-30T13:00")));
            assertDoesNotThrow(()->ManualTimeClockService.create(c,1,1,entry("2026-09-30T12:00","2026-09-30T14:00")));
            for(int user:new int[]{4,5,6,999}) assertThrows(SQLException.class,()->ManualTimeClockService.create(c,user,1,entry("2026-09-30T08:00","2026-09-30T12:00")));
            ManualTimeClockService.create(c,2,1,entry("2026-09-30T08:00","2026-09-30T12:00"));
            ManualTimeClockService.create(c,2,1,entry("2026-09-30T13:00","2026-09-30T17:00"));
            ManualTimeClockService.create(c,3,1,entry("2026-09-30T22:00","2026-10-01T06:00"));
            try(var s=c.createStatement();var r=s.executeQuery("SELECT SUM(total_earned),COUNT(total_earned) FROM employee_time_clock WHERE user_id=2")) { r.next(); assertEquals(500,r.getBigDecimal(1).intValue()); assertEquals(1,r.getInt(2)); }
            try(var s=c.createStatement();var r=s.executeQuery("SELECT total_earned,work_date,total_hours_worked FROM employee_time_clock WHERE user_id=3")) { r.next(); assertNull(r.getBigDecimal(1)); assertEquals("2026-09-30",r.getString(2)); assertEquals(8,r.getBigDecimal(3).intValue()); }
            c.rollback();
        }
    }
    @Test void openSessionBlocksOverlapAndIdempotentReplayReturnsSameRecord() throws Exception {
        try(Connection c=open()) {
            try(var s=c.createStatement()) { s.execute("INSERT INTO employee_time_clock(user_id,clock_in) VALUES(1,'2026-09-30 08:00:00-04')"); }
            assertThrows(SQLException.class,()->ManualTimeClockService.create(c,1,1,entry("2026-09-30T09:00","2026-09-30T10:00")));
            UUID device=UUID.randomUUID(); String key=UUID.randomUUID().toString(),hash=LanSecurity.sha256("entry");
            assertNull(LanApiServer.loadIdempotentResult(c,device,key,"time-clock.manual.create.v1",hash));
            long id=ManualTimeClockService.create(c,2,1,entry("2026-09-30T08:00","2026-09-30T12:00"));
            LanApiServer.completeIdempotency(c,device,key,Map.of("clockId",id));
            assertEquals(id,((Number)LanApiServer.loadIdempotentResult(c,device,key,"time-clock.manual.create.v1",hash).get("clockId")).longValue());
            assertThrows(Exception.class,()->LanApiServer.loadIdempotentResult(c,device,key,"time-clock.manual.create.v1",LanSecurity.sha256("changed")));
            c.rollback();
        }
    }
}
