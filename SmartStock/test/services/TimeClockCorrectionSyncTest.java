package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Uses its own disposable PostgreSQL cluster, never an installed store. */
class TimeClockCorrectionSyncTest {
    @TempDir Path temp;

    @Test void deliversHistoricalCorrectionsToOwnerWithoutAllowingReplicaOverwrites() throws Exception {
        Path bin = Path.of(System.getenv().getOrDefault("SMARTSTOCK_TEST_POSTGRES_BIN",
                "C:/Program Files/PostgreSQL/17/bin"));
        String suffix = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : "";
        assumeTrue(Files.isRegularFile(bin.resolve("initdb" + suffix)));
        Path cluster = temp.resolve("cluster");
        Process init = new ProcessBuilder(bin.resolve("initdb" + suffix).toString(), "-D", cluster.toString(),
                "-A", "trust", "-U", "clock_test", "--no-locale", "--encoding=UTF8")
                .redirectErrorStream(true).redirectOutput(temp.resolve("init.log").toFile()).start();
        assertTrue(init.waitFor(30,TimeUnit.SECONDS));
        assertEquals(0,init.exitValue(),Files.readString(temp.resolve("init.log")));
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Process server = new ProcessBuilder(bin.resolve("postgres" + suffix).toString(), "-D", cluster.toString(),
                "-h", "127.0.0.1", "-p", String.valueOf(port), "-F")
                .redirectErrorStream(true).redirectOutput(temp.resolve("postgres.log").toFile()).start();
        try {
            String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=1";
            Connection connected = null;
            for (int i=0;i<100 && connected==null;i++) {
                try { connected=DriverManager.getConnection(url,"clock_test",""); }
                catch(SQLException e) { Thread.sleep(100); }
            }
            assertNotNull(connected);
            try (Connection c = connected) {
                SchemaContractService.installLocalBaseline(c);
                try (Statement s=c.createStatement()) {
                    s.execute("INSERT INTO locations(location_id,name,timezone) VALUES(1,'Source','America/Guyana'),(2,'Owner','America/Guyana')");
                    s.execute("INSERT INTO roles(role_id,role_name) VALUES(987,'Correction Test Manager')");
                    s.execute("INSERT INTO permissions(permission_key,permission_name) VALUES('TIME_CLOCK_MANAGEMENT','Time Clock Management') ON CONFLICT DO NOTHING");
                    s.execute("INSERT INTO role_permissions(role_id,permission_id) SELECT 987,permission_id FROM permissions WHERE permission_key='TIME_CLOCK_MANAGEMENT'");
                    s.execute("INSERT INTO users(user_id,username,full_name,role_id,salary) VALUES(901,'test_employee','Employee',NULL,100),(902,'test_manager','Manager',987,100)");
                    s.execute("INSERT INTO employee_time_clock(clock_id,user_id,location_id,work_date,clock_in,clock_out,total_hours_worked) VALUES(501,901,2,'2026-10-03','2026-10-03 08:00-04','2026-10-03 16:00-04',8)");
                }
                TimeClockAutoCloseService.ensureSchema(c);
                c.setAutoCommit(false);
                var sourceStart = c.setSavepoint();
                var correction = new TimeClockAutoCloseService.Correction(LocalDateTime.parse("2026-10-03T07:30"),
                        null,null,null,null,LocalDateTime.parse("2026-10-03T16:16"),"Historical correction");
                TimeClockAutoCloseService.correctSession(c,501,correction,902,"Manager");
                assertEquals(1,TimeClockCorrectionSyncService.announce(c,1));
                assertEquals(0,TimeClockCorrectionSyncService.announce(c,1),"Do not enqueue the same correction twice");
                JsonObject payload;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT payload FROM sync_outbox WHERE payload->>'operation'='CORRECT_OWNER'")) {
                    assertTrue(r.next());payload=JsonParser.parseString(r.getString(1)).getAsJsonObject();
                }
                c.rollback(sourceStart); // Owner starts with the original punches and no source audit.
                var before = c.setSavepoint();
                CrossStoreReferenceSyncService.applyPayload(c,payload,3,1);
                assertClock(c,"08:00:00",0);
                JsonObject forged=payload.deepCopy();
                forged.getAsJsonObject("row_data").addProperty("actor_user_id",901);
                assertThrows(SQLException.class,()->CrossStoreReferenceSyncService.applyPayload(c,forged,2,1));
                c.rollback(before);
                try(var s=c.createStatement()) { s.execute("UPDATE employee_time_clock SET clock_out='2026-10-03 17:00-04'"); }
                assertThrows(SQLException.class,()->CrossStoreReferenceSyncService.applyPayload(c,payload,2,1));
                c.rollback(before);
                CrossStoreReferenceSyncService.applyPayload(c,payload,2,1);
                assertClock(c,"07:30:00",1);
                CrossStoreReferenceSyncService.applyPayload(c,payload,2,1);
                assertClock(c,"07:30:00",1);
                // Ordinary foreign snapshots still cannot overwrite an owner's shift.
                JsonObject stale;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(tc)::text FROM employee_time_clock tc")) {
                    r.next();stale=JsonParser.parseString(r.getString(1)).getAsJsonObject();
                }
                stale.addProperty("clock_in","2026-10-03T09:00:00-04:00");
                stale.addProperty("updated_at","2099-01-01T00:00:00Z");
                JsonObject snapshot=new JsonObject();snapshot.addProperty("table_name","employee_time_clock");
                snapshot.addProperty("operation","UPSERT");snapshot.add("row_data",stale);
                CrossStoreReferenceSyncService.applyPayload(c,snapshot,2,1);
                assertClock(c,"07:30:00",1);
                c.rollback();
                verifyWebCorrections(c);
            }
        } finally {
            Process stop=new ProcessBuilder(bin.resolve("pg_ctl"+suffix).toString(),"-D",cluster.toString(),"-m","immediate","stop")
                    .redirectErrorStream(true).redirectOutput(temp.resolve("stop.log").toFile()).start();
            stop.waitFor(15,TimeUnit.SECONDS);
            server.destroy();
            if (!server.waitFor(10,TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    private static void assertClock(Connection c,String time,int audits) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT (clock_in AT TIME ZONE 'America/Guyana')::time::text,(SELECT count(*) FROM employee_time_clock_adjustments) FROM employee_time_clock WHERE clock_id=501")) {
            assertTrue(r.next());assertEquals(time,r.getString(1));assertEquals(audits,r.getInt(2));
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyWebCorrections(Connection c)throws Exception {
        Set<String> manager=Set.of("ACCESS_EMPLOYEE_WEB","VIEW_ALL_EMPLOYEE_WEB","TIME_CLOCK_MANAGEMENT");
        var history=EmployeeWebHistoryService.history(c,902,901,manager,java.time.LocalDate.of(2026,10,3),java.time.LocalDate.of(2026,10,3),0);
        var session=((List<Map<String,Object>>)history.get("sessions")).get(0);
        JsonObject b=new JsonObject();b.addProperty("clockId",501);b.addProperty("revision",session.get("revision").toString());
        JsonObject values=new JsonObject();values.addProperty("clockIn","2026-10-03T08:30:00");values.addProperty("clockOut","2026-10-03T17:30:00");
        values.addProperty("lunchStart","2026-10-03T12:00:00");values.addProperty("lunchEnd","2026-10-03T12:30:00");
        values.addProperty("breakStart","2026-10-03T14:00:00");values.addProperty("breakEnd","2026-10-03T14:15:00");
        values.addProperty("reason","   ");b.add("correction",values);
        assertThrows(SecurityException.class,()->EmployeeWebCorrectionService.correct(c,902,"Manager",Set.of("ACCESS_EMPLOYEE_WEB","VIEW_ALL_EMPLOYEE_WEB"),b));
        assertThrows(SecurityException.class,()->EmployeeWebCorrectionService.correct(c,902,"Manager",Set.of("ACCESS_EMPLOYEE_WEB","TIME_CLOCK_MANAGEMENT"),b));
        EmployeeWebCorrectionService.correct(c,902,"Manager",manager,b);
        try(var s=c.createStatement();var r=s.executeQuery("SELECT (clock_in AT TIME ZONE 'America/Guyana')::time::text,(clock_out AT TIME ZONE 'America/Guyana')::time::text,total_hours_worked,total_earned FROM employee_time_clock WHERE clock_id=501")) {
            assertTrue(r.next());assertEquals("08:30:00",r.getString(1));assertEquals("17:30:00",r.getString(2));
            assertEquals(8.25,r.getDouble(3),.001);assertEquals(825,r.getDouble(4),.001);
        }
        try(var s=c.createStatement();var r=s.executeQuery("SELECT reason,actor_user_id,actor_name,after_hours FROM employee_time_clock_adjustments WHERE clock_id=501")) {
            assertTrue(r.next());assertEquals("N/A",r.getString(1));assertEquals(902,r.getInt(2));assertEquals("Manager",r.getString(3));assertEquals(8.25,r.getDouble(4),.001);assertFalse(r.next());
        }
        assertEquals(409,assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebCorrectionService.correct(c,902,"Manager",manager,b)).status);
        history=EmployeeWebHistoryService.history(c,902,901,manager,java.time.LocalDate.of(2026,10,3),java.time.LocalDate.of(2026,10,3),0);
        session=((List<Map<String,Object>>)history.get("sessions")).get(0);
        assertEquals(8.25,((Number)session.get("hours")).doubleValue(),.001);
        assertEquals(825,((Number)session.get("calculatedPay")).doubleValue(),.001);
        b.addProperty("revision",session.get("revision").toString());values.addProperty("clockOut","2026-10-03T07:00:00");
        assertEquals(400,assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebCorrectionService.correct(c,902,"Manager",manager,b)).status);
        c.rollback();
    }
}
