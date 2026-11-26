package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CashDrawerHandoverIntegrationTest {
    @ParameterizedTest @CsvSource({"0,false","100,false","200,false","0,true","100,true","200,true"})
    void outgoingCountPausesThenIncomingAcceptanceResumesSameSession(int opening,boolean unattended) throws Exception {
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
                try(var p=c.prepareStatement("INSERT INTO cash_drawer_sessions(cash_drawer_id,location_id,device_id,drawer_name,opening_cash,current_cashier_user_id,current_cashier_name,main_cashier_user_id,main_cashier_name,status) VALUES (?,?,?,'Handover drawer',"+opening+",?,'Outgoing',?,'Outgoing','OPEN') RETURNING cash_drawer_session_id")) {
                    p.setLong(1,drawer);p.setInt(2,location);p.setObject(3,device);p.setInt(4,a);p.setInt(5,a);try(var r=p.executeQuery()){r.next();session=r.getLong(1);}
                }
                JsonObject request=CashDrawerHandoverServiceTest.count();request.addProperty("sessionId",session);request.addProperty("acceptTakeover",false);
                if(unattended) {
                    request.addProperty("takeoverWithoutHandover",true);
                    request.addProperty("previousCashierUserId",a);
                    request.addProperty("notes","Previous cashier had to leave");
                    assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,a,"Outgoing",device,"Test",location));
                    assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",UUID.randomUUID(),"Test",location));
                    LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location);
                    assertNull(CashDrawerHandoverService.pending(c,session));
                    var taken=CashDrawerService.getActiveSessionForDevice(c,location,device.toString());
                    assertEquals(b,taken.currentCashierUserId());
                    assertEquals(a,taken.mainCashierUserId());
                    var recorded=CashDrawerService.listHandovers(c,session).get(0);
                    assertEquals("Outgoing",recorded.fromUserName());
                    assertEquals("Incoming",recorded.toUserName());
                    assertTrue(recorded.notes().contains("WITHOUT OUTGOING HANDOVER"));
                    assertTrue(recorded.notes().contains("Previous cashier had to leave"));
                    assertEquals(0,java.math.BigDecimal.valueOf(100-opening).compareTo(recorded.variance()));
                    assertEquals(0,java.math.BigDecimal.valueOf(100).compareTo(CashDrawerService.calculateExpectedCash(c,session)));
                    var history=(CashDrawerCountHistoryService.Draft)CashDrawerCountHistoryService.latest(c,session,location,device,b).get("draft");
                    assertEquals("Incoming",history.changedByName());
                    assertEquals(0,java.math.BigDecimal.valueOf(opening).compareTo(history.expectedCash()));
                    assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location));
                    request.addProperty("previousCashierUserId",a);
                    assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,a,"Outgoing",device,"Test",location));
                    return;
                }
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location));
                LanCashDrawerService.handover(c,request,a,"Outgoing",device,"Test",location);
                assertNotNull(CashDrawerHandoverService.pending(c,session));
                assertEquals(0,java.math.BigDecimal.valueOf(100-opening).compareTo(CashDrawerHandoverService.pending(c,session).variance()));
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
                var handover=CashDrawerService.listHandovers(c,session).get(0);
                assertEquals("Outgoing",handover.fromUserName());
                assertEquals(0,java.math.BigDecimal.valueOf(100-opening).compareTo(handover.variance()));
                assertEquals(0,java.math.BigDecimal.valueOf(100).compareTo(CashDrawerService.calculateExpectedCash(c,session)));
                assertThrows(SQLException.class,()->LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location));
                request.addProperty("acceptTakeover",false);
                request.addProperty("countedCash",200);
                request.getAsJsonObject("denominationCounts").addProperty("100",2);
                LanCashDrawerService.handover(c,request,b,"Incoming",device,"Test",location);
                request.addProperty("acceptTakeover",true);
                LanCashDrawerService.handover(c,request,a,"Outgoing",device,"Test",location);
                var second=CashDrawerService.listHandovers(c,session).get(1);
                assertEquals("Incoming",second.fromUserName());
                assertEquals(0,java.math.BigDecimal.valueOf(100).compareTo(second.variance()));
                assertEquals(0,java.math.BigDecimal.valueOf(200).compareTo(CashDrawerService.calculateExpectedCash(c,session)));
            } finally {c.rollback();}
        }
    }
    private static long id(Connection c,String sql)throws SQLException{try(var p=c.prepareStatement(sql);var r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}}
}
