package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CashDrawerReprintIntegrationTest {
    @Test void limitsReprintsToAssignedDrawerAndStoreDate()throws Exception {
        String url=System.getProperty("smartstock.variants.test.jdbc","");assumeTrue(!url.isBlank());
        try(Connection c=DriverManager.getConnection(url,System.getProperty("smartstock.variants.test.user","variant_test"),"")) {
            try(var ps=c.prepareStatement("SELECT to_regclass('public.products')");var rs=ps.executeQuery()){rs.next();if(rs.getString(1)==null)SchemaContractService.installLocalBaseline(c);}
            SchemaContractService.requireLocalReady(c);
            c.setAutoCommit(false);
            try {
                long location=id(c,"INSERT INTO locations(name,receipt_store_code,timezone) VALUES ('Reprint store','RP01','America/Guyana') RETURNING location_id");
                int user=(int)id(c,"INSERT INTO users(username,full_name,role_id) SELECT 'reprint-admin','Reprint Admin',role_id FROM roles WHERE UPPER(role_name)='ADMIN' RETURNING user_id");
                int denied=(int)id(c,"INSERT INTO users(username,full_name) VALUES ('reprint-denied','Reprint Denied') RETURNING user_id");
                UUID device=UUID.randomUUID();try(var ps=c.prepareStatement("INSERT INTO devices(device_id,installation_id) VALUES (?,?)")){ps.setObject(1,device);ps.setString(2,UUID.randomUUID().toString());ps.executeUpdate();}
                long drawer=id(c,"INSERT INTO cash_drawers(location_id,drawer_name) VALUES ("+location+",'Draw 1') RETURNING cash_drawer_id");
                long other=id(c,"INSERT INTO cash_drawers(location_id,drawer_name) VALUES ("+location+",'Draw 2') RETURNING cash_drawer_id");
                try(var ps=c.prepareStatement("INSERT INTO cash_drawer_device_assignments(cash_drawer_id,location_id,device_id) VALUES (?,?,?)")){ps.setLong(1,drawer);ps.setLong(2,location);ps.setObject(3,device);ps.executeUpdate();}
                long chosen=session(c,drawer,location,device,"2026-09-11 03:59:00+00");
                session(c,drawer,location,device,"2026-09-11 04:00:00+00");
                long forbidden=session(c,other,location,device,"2026-09-11 03:59:00+00");
                JsonObject request=new JsonObject();request.addProperty("date","2026-09-10");
                var list=LanCashDrawerService.reprint(c,request,device,user,(int)location);
                assertEquals(1,((List<?>)list.get("sessions")).size());
                request.addProperty("sessionId",forbidden);
                assertThrows(LanCashDrawerService.RuleViolation.class,()->LanCashDrawerService.reprint(c,request,device,user,(int)location));
                request.addProperty("sessionId",chosen);
                assertEquals(false,LanCashDrawerService.reprint(c,request,device,user,(int)location).get("savedBreakdown"));
                assertThrows(LanCashDrawerService.RuleViolation.class,()->LanCashDrawerService.reprint(c,request,device,denied,(int)location));
                assertThrows(LanCashDrawerService.RuleViolation.class,()->LanCashDrawerService.reprint(c,request,UUID.randomUUID(),user,(int)location));
                try(var ps=c.prepareStatement("INSERT INTO cash_drawer_count_events(cash_drawer_session_id,location_id,revision_no,event_type,denomination_counts,float_counts,counted_cash,float_total,cash_in_hand,expected_cash,complete) VALUES (?,?,1,'CLOSE','{\"100\":3}','{\"100\":1}',300,100,200,250,true)")){ps.setLong(1,chosen);ps.setLong(2,location);ps.executeUpdate();}
                var receipt=LanCashDrawerService.reprint(c,request,device,user,(int)location);
                assertEquals(true,receipt.get("savedBreakdown"));
                assertEquals(0,new java.math.BigDecimal("100").compareTo((java.math.BigDecimal)receipt.get("floatCash")));
                assertEquals(300,((JsonObject)receipt.get("session")).get("countedCash").getAsInt());
                assertEquals(50,((JsonObject)receipt.get("session")).get("variance").getAsInt());
                var decoded=LanJson.create().fromJson(LanJson.create().toJson(receipt),LanApiClient.CashDrawerReprint.class);
                assertEquals(chosen,decoded.session().sessionId());assertNotNull(decoded.session().closedAt());
                assertEquals(1,id(c,"SELECT count(*) FROM cash_drawer_count_events WHERE cash_drawer_session_id="+chosen));
            }finally{c.rollback();}
        }
    }
    private static long session(Connection c,long drawer,long location,UUID device,String closed)throws SQLException {
        try(var ps=c.prepareStatement("INSERT INTO cash_drawer_sessions(cash_drawer_id,location_id,device_id,drawer_name,status,closed_at,opening_cash,counted_cash,expected_cash,cash_to_remove,variance) VALUES (?,?,?,'Test draw','CLOSED',?::timestamptz,100,400,250,300,150) RETURNING cash_drawer_session_id")){ps.setLong(1,drawer);ps.setLong(2,location);ps.setObject(3,device);ps.setString(4,closed);try(var rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}
    }
    private static long id(Connection c,String sql)throws SQLException{try(var ps=c.prepareStatement(sql);var rs=ps.executeQuery()){assertTrue(rs.next());return rs.getLong(1);}}
}
