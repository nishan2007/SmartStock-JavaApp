package services;

import com.google.gson.JsonObject;
import models.CashDrawerHandover;
import models.CashDrawerSession;
import java.math.BigDecimal;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;

/** Two-phase handover, persisted in the existing immutable drawer lifecycle history. */
final class CashDrawerHandoverService {
    static final String PENDING = "AWAITING_TAKEOVER";
    private CashDrawerHandoverService() { }

    static CashDrawerHandover pending(Connection c, long sessionId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("""
                SELECT e.reason,e.counted_cash,e.expected_cash,e.created_at,e.changed_by_name
                FROM cash_drawer_count_events e
                WHERE e.cash_drawer_session_id=? AND e.event_type IN ('HANDOVER','CLOSE')
                ORDER BY e.revision_no DESC LIMIT 1
                """)) {
            p.setLong(1,sessionId);
            try (ResultSet r=p.executeQuery()) {
                if (!r.next() || !PENDING.equals(r.getString("reason"))) return null;
                return new CashDrawerHandover(0,sessionId,r.getString("changed_by_name"),null,
                        r.getBigDecimal("expected_cash"),r.getBigDecimal("counted_cash"),BigDecimal.ZERO,
                        r.getTimestamp("created_at"),PENDING);
            }
        }
    }

    static CashDrawerHandover confirm(Connection c, JsonObject body, int userId, String userName,
                                      UUID deviceId, String deviceName, int locationId) throws Exception {
        long id=body.get("sessionId").getAsLong();
        CashDrawerSession session;
        try (PreparedStatement p=c.prepareStatement("SELECT * FROM cash_drawer_sessions WHERE cash_drawer_session_id=? FOR UPDATE")) {
            p.setLong(1,id);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next() || !"OPEN".equals(r.getString("status"))) throw new SQLException("This drawer session is not open.");
            }
        }
        // Access is checked against the register's assigned physical drawer, including shared registers.
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM cash_drawer_sessions s JOIN cash_drawer_device_assignments a ON a.cash_drawer_id=s.cash_drawer_id WHERE s.cash_drawer_session_id=? AND s.location_id=? AND a.device_id=? AND a.is_active=TRUE AND a.unassigned_at IS NULL")) {
            p.setLong(1,id);p.setInt(2,locationId);p.setObject(3,deviceId);
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("This register cannot hand over that drawer session.");}
        }
        session=CashDrawerService.getActiveSessionForDevice(c,locationId,deviceId.toString());
        if(session==null || session.sessionId()!=id)throw new SQLException("This drawer session is no longer active.");
        CashDrawerHandover waiting=pending(c,id);
        boolean accepting=body.has("acceptTakeover") && body.get("acceptTakeover").getAsBoolean();
        if(accepting != (waiting!=null)) throw new SQLException("The handover stage changed. Reload Balance Draw before confirming.");
        validateCashier(session.currentCashierUserId(),userId,accepting);
        BigDecimal count=body.get("countedCash").getAsBigDecimal();
        validateCount(body,count);
        BigDecimal expected=CashDrawerService.calculateExpectedCash(c,id);
        if(count.compareTo(expected)!=0 || (waiting!=null && count.compareTo(waiting.countedCash())!=0))
            throw new SQLException("Handover cash must match expected cash and the outgoing cashier's count.");
        CashDrawerHandover result;
        if(accepting) {
            result=CashDrawerService.recordHandover(c,id,count,null,userId,userName);
            CashDrawerCountHistoryService.appendLifecycle(c,id,"HANDOVER",body,userId,userName,deviceId,deviceName,"TAKEOVER_ACCEPTED");
        } else {
            CashDrawerCountHistoryService.appendLifecycle(c,id,"HANDOVER",body,userId,userName,deviceId,deviceName,PENDING);
            result=pending(c,id);
        }
        return result;
    }

    static void validateCashier(Integer current, int user, boolean accepting) throws SQLException {
        if(current==null) throw new SQLException("The drawer has no current cashier.");
        if(accepting && Objects.equals(current,user)) throw new SQLException("A different cashier must recount and accept the drawer.");
        if(!accepting && !Objects.equals(current,user)) throw new SQLException("Only the current cashier can initiate handover.");
    }

    static void validateCount(JsonObject body, BigDecimal count) throws SQLException {
        try {
            if(count==null || count.signum()<0 || !body.get("complete").getAsBoolean()) throw new IllegalArgumentException();
            JsonObject quantities=body.getAsJsonObject("denominationCounts");
            if(!quantities.keySet().equals(java.util.Set.of("5000","2000","1000","500","100","50","20"))) throw new IllegalArgumentException();
            BigDecimal total=BigDecimal.ZERO;
            for(var entry:quantities.entrySet()) {
                int denomination=Integer.parseInt(entry.getKey());
                int quantity=entry.getValue().getAsBigDecimal().intValueExact();
                if(denomination<=0 || quantity<0) throw new IllegalArgumentException();
                total=total.add(BigDecimal.valueOf(denomination).multiply(BigDecimal.valueOf(quantity)));
            }
            if(quantities.size()==0 || total.compareTo(count)!=0) throw new IllegalArgumentException();
        } catch(Exception e) { throw new SQLException("Enter a complete, non-negative denomination count matching the counted cash.",e); }
    }
}
