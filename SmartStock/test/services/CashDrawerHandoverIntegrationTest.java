package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CashDrawerHandoverIntegrationTest {
    @Test void outgoingCountPausesThenIncomingAcceptanceResumesSameSession() throws Exception {
        String url=System.getProperty("smartstock.variants.test.jdbc","");assumeTrue(!url.isBlank());
        try(Connection c=DriverManager.getConnection(url,System.getProperty("smartstock.variants.test.user","variant_test"),"")) {
            SchemaContractService.requireLocalReady(c);c.setAutoCommit(false);
            try {
                int location=(int)id(c,"INSERT INTO locations(name,receipt_store_code) VALUES ('Handover test','HT01') RETURNING location_id");
                int a=(int)id(c,"INSERT INTO users(username,full_name,role_id) SELECT 'handover-out','Outgoing',role_id FROM roles WHERE UPPER(role_name)='ADMIN' RETURNING user_id");
                int b=(int)id(c,"INSERT INTO users(username,full_name,role_id) SELECT 'handover-in','Incoming',role_id FROM roles WHERE UPPER(role_name)='ADMIN' RETURNING user_id");
                UUID device=UUID.randomUUID();
                try(var p=c.prepareStatement("INSERT INTO devices(device_id,installation_id) VALUES (?,?)")){p.setObject(1,device);p.setString(2,UUID.randomUUID().toString());p.executeUpdate();}
                long drawer=id(c,"INSERT INTO cash_drawers(location_id,drawer_name) VALUES ("+location+",'Handover drawer') RETURNING cash_drawer_id");
                try(var p=c.prepareStatement("INSERT INTO cash_drawer_device_assignments(cash_drawer_id,location_id,device_id) VALUES (?,?,?)")){p.setLong(1,drawer);p.setInt(2,location);p.setObject(3,device);p.executeUpdate();}
                long session;
                try(var p=c.prepareStatement("INSERT INTO cash_drawer_sessions(cash_drawer_id,location_id,device_id,drawer_name,opening_cash,current_cashier_user_id,current_cashier_name,main_cashier_user_id,main_cashier_name,status) VALUES (?,?,?,'Handover drawer',100,?,'Outgoing',?,'Outgoing','OPEN') RETURNING cash_drawer_session_id")) {
                    p.setLong(1,drawer);p.setInt(2,location);p.setObject(3,device);p.setInt(4,a);p.setInt(5,a);try(var r=p.executeQuery()){r.next();session=r.getLong(1);}
                }
                JsonObject request=CashDrawerHandoverServiceTest.count();request.addProperty("sessionId",session);request.addProperty("acceptTakeover",false);
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location));
                LanCashDrawerService.handover(c,request,a,"Outgoing",device,"Test",location);
                assertNotNull(CashDrawerHandoverService.pending(c,session));
                assertEquals(a,CashDrawerService.getActiveSessionForDevice(c,location,device.toString()).currentCashierUserId());
                assertFalse(CashDrawerService.resolveDrawerForDevice(c,location,device.toString()).hasActiveSession());
                var draft=LanCashDrawerService.registerState(c,device,b,location);
                assertNotNull(draft.get("pendingHandover"));
                var saved=CashDrawerCountHistoryService.latest(c,session,location,device,b);
                assertEquals(1,((CashDrawerCountHistoryService.Draft)saved.get("draft")).denominationCounts().get("100"));
                assertThrows(SQLException.class,()->CashDrawerService.closeSession(c,session,java.math.BigDecimal.valueOf(100),null,a,"Outgoing"));
                request.addProperty("acceptTakeover",true);
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,a,"Outgoing",device,"Test",location));
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",UUID.randomUUID(),"Test",location));
                request.addProperty("countedCash",200);
                request.getAsJsonObject("denominationCounts").addProperty("100",2);
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location));
                request.addProperty("countedCash",100);
                request.getAsJsonObject("denominationCounts").addProperty("100",1);
                LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location);
                assertNull(CashDrawerHandoverService.pending(c,session));
                assertTrue(CashDrawerService.resolveDrawerForDevice(c,location,device.toString()).hasActiveSession());
                var resumed=CashDrawerService.getActiveSessionForDevice(c,location,device.toString());
                assertEquals(session,resumed.sessionId());assertEquals(a,resumed.mainCashierUserId());assertEquals(b,resumed.currentCashierUserId());
                assertEquals(1,CashDrawerService.listHandovers(c,session).size());
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location));
            } finally {c.rollback();}
        }
    }
    private static long id(Connection c,String sql)throws SQLException{try(var p=c.prepareStatement(sql);var r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}}
}
