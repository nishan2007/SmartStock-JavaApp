package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrossStoreReferenceSyncIntegrationTest {
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
                location.addProperty("updated_at",java.time.Instant.now().plusSeconds(60).toString());
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
