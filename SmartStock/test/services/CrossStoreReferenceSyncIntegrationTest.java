package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrossStoreReferenceSyncIntegrationTest {
    @Test
    void retriesExistingPayrollAndTransferFailuresIdempotently() throws Exception {
        String jdbc=System.getProperty("smartstock.test.jdbc","");
        String user=System.getProperty("smartstock.test.dbUser","");
        String password=System.getProperty("smartstock.test.dbPassword","");
        assumeTrue(!jdbc.isBlank()&&!user.isBlank());
        try(var connection=DriverManager.getConnection(jdbc,user,password)){
            connection.setAutoCommit(false);
            try{
                JsonObject payment;
                try(var ps=connection.prepareStatement(
                        "SELECT to_jsonb(p)::text FROM payroll_payments p ORDER BY payroll_payment_id LIMIT 1");
                    var rs=ps.executeQuery()){
                    assumeTrue(rs.next());
                    payment=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                payment.addProperty("sync_uuid",java.util.UUID.randomUUID().toString());
                JsonObject payload=new JsonObject();
                payload.addProperty("table_name","payroll_payments");
                payload.addProperty("operation","UPSERT");
                payload.add("row_data",payment);
                int before;
                try(var ps=connection.prepareStatement("SELECT count(*) FROM payroll_payments");
                    var rs=ps.executeQuery()){rs.next();before=rs.getInt(1);}
                CrossStoreReferenceSyncService.applyPayload(connection,payload);
                try(var ps=connection.prepareStatement("SELECT count(*) FROM payroll_payments");
                    var rs=ps.executeQuery()){rs.next();assertEquals(before,rs.getInt(1));}

                CrossStoreTransferSyncService.applyInbox(connection,2);
                try(var ps=connection.prepareStatement("""
                        SELECT count(*) FROM sync_inbox
                        WHERE event_type='STORE_TRANSFER_RECEIVED' AND status='FAILED'
                        """);var rs=ps.executeQuery()){
                    rs.next();assertEquals(0,rs.getInt(1));
                }
            }finally{connection.rollback();connection.setAutoCommit(true);}
        }
    }

    @Test
    void replicatesEmployeePayrollSettingsAndAdjustmentAuditWithoutCredentials() throws Exception {
        String jdbc=System.getProperty("smartstock.test.jdbc","");
        String user=System.getProperty("smartstock.test.dbUser","");
        String password=System.getProperty("smartstock.test.dbPassword","");
        assumeTrue(!jdbc.isBlank()&&!user.isBlank());
        try(var connection=DriverManager.getConnection(jdbc,user,password)){
            connection.setAutoCommit(false);
            try{
                try(var ps=connection.prepareStatement(
                        "UPDATE users SET updated_at=CURRENT_TIMESTAMP + INTERVAL '3 minutes' WHERE user_id=(SELECT min(user_id) FROM users)")){
                    assertEquals(1,ps.executeUpdate());
                }
                CrossStoreReferenceSyncService.announceChanges(connection,2);
                JsonObject employeePayload;
                try(var ps=connection.prepareStatement("""
                        SELECT payload FROM sync_outbox
                        WHERE event_type='REFERENCE_ROW_CHANGED' AND payload->>'table_name'='users'
                        ORDER BY created_at DESC,event_id DESC LIMIT 1
                        """);var rs=ps.executeQuery()){
                    assertTrue(rs.next());
                    employeePayload=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                JsonObject employee=employeePayload.getAsJsonObject("row_data");
                assertTrue(!employee.has("password_hash"));
                assertTrue(!employee.has("employee_pin_hash"));
                assertTrue(!employee.has("badge_secret_hash"));
                JsonObject protectedCredentials=employeePayload.getAsJsonObject("protected_credentials");
                assertTrue(protectedCredentials.has("employee_pin_hash"));
                assertTrue(protectedCredentials.has("badge_secret_hash"));
                assertTrue(!protectedCredentials.has("password_hash"));
                int employeeId=employee.get("user_id").getAsInt();
                employee.addProperty("full_name","Rollback-only shared employee");
                employee.addProperty("updated_at",java.time.Instant.now().plusSeconds(600).toString());
                CrossStoreReferenceSyncService.applyPayload(connection,employeePayload);
                try(var ps=connection.prepareStatement("SELECT full_name FROM users WHERE user_id=?")){
                    ps.setInt(1,employeeId);try(var rs=ps.executeQuery()){
                        assertTrue(rs.next());assertEquals("Rollback-only shared employee",rs.getString(1));
                    }
                }

                JsonObject setting;
                try(var ps=connection.prepareStatement(
                        "SELECT to_jsonb(s)::text FROM employee_payroll_settings s ORDER BY effective_from LIMIT 1");
                    var rs=ps.executeQuery()){
                    assertTrue(rs.next());setting=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                setting.addProperty("updated_at",java.time.Instant.now().plusSeconds(600).toString());
                JsonObject settingPayload=new JsonObject();settingPayload.addProperty("table_name","employee_payroll_settings");
                settingPayload.addProperty("operation","UPSERT");settingPayload.add("row_data",setting);
                CrossStoreReferenceSyncService.applyPayload(connection,settingPayload);

                JsonObject adjustment=null;
                try(var ps=connection.prepareStatement("""
                        SELECT (to_jsonb(a)-'clock_id'||jsonb_build_object('clock_uuid',tc.clock_uuid))::text
                        FROM employee_time_clock_adjustments a JOIN employee_time_clock tc ON tc.clock_id=a.clock_id
                        ORDER BY a.created_at LIMIT 1
                        """);var rs=ps.executeQuery()){
                    if(rs.next())adjustment=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                if(adjustment!=null){
                    var adjustmentId=java.util.UUID.randomUUID();
                    adjustment.addProperty("adjustment_id",adjustmentId.toString());
                    JsonObject adjustmentPayload=new JsonObject();adjustmentPayload.addProperty("table_name","employee_time_clock_adjustments");
                    adjustmentPayload.addProperty("operation","UPSERT");adjustmentPayload.add("row_data",adjustment);
                    CrossStoreReferenceSyncService.applyPayload(connection,adjustmentPayload);
                    try(var ps=connection.prepareStatement("SELECT count(*) FROM employee_time_clock_adjustments WHERE adjustment_id=?")){
                        ps.setObject(1,adjustmentId);try(var rs=ps.executeQuery()){rs.next();assertEquals(1,rs.getInt(1));}
                    }
                }
            }finally{connection.rollback();connection.setAutoCommit(true);}
        }
    }

    @Test
    void emitsCompleteLocationAndScheduleRowsWithoutChangingTheDatabase() throws Exception {
        String jdbc=System.getProperty("smartstock.test.jdbc","");
        String user=System.getProperty("smartstock.test.dbUser","");
        String password=System.getProperty("smartstock.test.dbPassword","");
        assumeTrue(!jdbc.isBlank()&&!user.isBlank());
        try(var connection=DriverManager.getConnection(jdbc,user,password)){
            connection.setAutoCommit(false);
            try{
                services.SqlScriptRunner.runSql(connection,services.SqlScriptRunner.readResource(
                        "database/migrations/v1_after/20260811233200_add_cross_store_time_clock_identity.sql"));
                try(var ps=connection.prepareStatement(
                        "UPDATE locations SET updated_at=CURRENT_TIMESTAMP + INTERVAL '2 minutes' WHERE location_id=2")){
                    assertEquals(1,ps.executeUpdate());
                }
                int announced=CrossStoreReferenceSyncService.announceChanges(connection,2);
                assertTrue(announced>0);
                try(var ps=connection.prepareStatement("""
                        SELECT count(*) FILTER (WHERE payload->>'table_name'='locations'
                                                  AND payload->'row_data'->>'location_id'='2'),
                               count(*) FILTER (WHERE payload->>'table_name'='employee_schedule_assignments'
                                                  AND payload->'row_data'->>'location_id'='1')
                        FROM sync_outbox WHERE event_type='REFERENCE_ROW_CHANGED'
                        """);var rs=ps.executeQuery()){
                    assertTrue(rs.next());
                    assertTrue(rs.getInt(1)>0,"Store 2 details were not enveloped.");
                    assertTrue(rs.getInt(2)>0,"Store 1 schedule rows were not enveloped.");
                }
                JsonObject location;
                try(var ps=connection.prepareStatement("SELECT to_jsonb(l)::text FROM locations l WHERE location_id=2");
                    var rs=ps.executeQuery()){
                    assertTrue(rs.next());location=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                location.addProperty("name","Rollback-only replicated store");
                location.addProperty("updated_at",java.time.Instant.now().plusSeconds(300).toString());
                JsonObject payload=new JsonObject();payload.addProperty("table_name","locations");
                payload.addProperty("operation","UPSERT");payload.add("row_data",location);
                CrossStoreReferenceSyncService.applyPayload(connection,payload);
                try(var ps=connection.prepareStatement("SELECT name FROM locations WHERE location_id=2");
                    var rs=ps.executeQuery()){
                    assertTrue(rs.next());
                    assertTrue("Rollback-only replicated store".equals(rs.getString(1)));
                }
                JsonObject assignment;
                try(var ps=connection.prepareStatement("""
                        SELECT to_jsonb(a)::text FROM employee_schedule_assignments a
                        WHERE location_id=1 ORDER BY work_date,user_id LIMIT 1
                        """);var rs=ps.executeQuery()){
                    assertTrue(rs.next());assignment=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                assignment.addProperty("lunch_start_time","12:34:00");
                assignment.addProperty("updated_at",java.time.Instant.now().plusSeconds(60).toString());
                JsonObject schedulePayload=new JsonObject();
                schedulePayload.addProperty("table_name","employee_schedule_assignments");
                schedulePayload.addProperty("operation","UPSERT");schedulePayload.add("row_data",assignment);
                CrossStoreReferenceSyncService.applyPayload(connection,schedulePayload);
                try(var ps=connection.prepareStatement("""
                        SELECT lunch_start_time FROM employee_schedule_assignments
                        WHERE location_id=? AND user_id=? AND work_date=?
                        """)){
                    ps.setInt(1,assignment.get("location_id").getAsInt());
                    ps.setInt(2,assignment.get("user_id").getAsInt());
                    ps.setDate(3,java.sql.Date.valueOf(assignment.get("work_date").getAsString()));
                    try(var rs=ps.executeQuery()){assertTrue(rs.next());assertTrue("12:34:00".equals(rs.getTime(1).toString()));}
                }
                JsonObject clock=null;
                try(var ps=connection.prepareStatement("SELECT to_jsonb(tc)::text FROM employee_time_clock tc ORDER BY clock_id LIMIT 1");
                    var rs=ps.executeQuery()){
                    if(rs.next())clock=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                }
                if(clock!=null){
                    String clockUuid=clock.get("clock_uuid").getAsString();
                    clock.addProperty("location_name","Rollback-only cross-store clock");
                    clock.addProperty("updated_at",java.time.Instant.now().plusSeconds(120).toString());
                    JsonObject clockPayload=new JsonObject();clockPayload.addProperty("table_name","employee_time_clock");
                    clockPayload.addProperty("operation","UPSERT");clockPayload.add("row_data",clock);
                    CrossStoreReferenceSyncService.applyPayload(connection,clockPayload);
                    try(var ps=connection.prepareStatement("SELECT location_name FROM employee_time_clock WHERE clock_uuid=?")){
                        ps.setObject(1,java.util.UUID.fromString(clockUuid));try(var rs=ps.executeQuery()){
                            assertTrue(rs.next());assertTrue("Rollback-only cross-store clock".equals(rs.getString(1)));
                        }
                    }
                }
            }finally{connection.rollback();connection.setAutoCommit(true);}
        }
    }
}
