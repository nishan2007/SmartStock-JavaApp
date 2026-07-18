package services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-owned orders-manager dashboard repository. */
public final class LanOrdersDashboardService {
    private LanOrdersDashboardService() { }

    public static Dashboard load(Connection c,int locationId,ZoneId zone)throws SQLException {
        Metrics metrics=new Metrics(count(c,locationId,"due_date < CURRENT_DATE AND status NOT IN ('DELIVERED','CANCELLED')"),
                count(c,locationId,"due_date = CURRENT_DATE AND status NOT IN ('DELIVERED','CANCELLED')"),
                count(c,locationId,"status='READY'"),count(c,locationId,"status='ASSIGNED'"),
                count(c,locationId,"status='CANCELLED' AND cancelled_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'"),
                sum(c,locationId,"COALESCE(balance_due,0)","status <> 'CANCELLED' AND COALESCE(balance_due,0)>0"),
                refundsToday(c,locationId,zone),countLowStock(c));
        return new Dashboard(metrics,actions(c,locationId),exceptions(c,locationId),lowStock(c),audit(c,locationId));
    }

    private static int count(Connection c,int locationId,String predicate)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM custom_orders WHERE ("+predicate+") AND location_id=?")){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getInt(1):0;}}
    }
    private static BigDecimal sum(Connection c,int locationId,String expression,String predicate)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM("+expression+"),0) FROM custom_orders WHERE ("+predicate+") AND location_id=?")){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){return rs.next()?zero(rs.getBigDecimal(1)):BigDecimal.ZERO;}}
    }
    private static BigDecimal refundsToday(Connection c,int locationId,ZoneId zone)throws SQLException {
        String sql="""
SELECT COALESCE(SUM(p.payment_amount),0) FROM custom_order_payments p JOIN custom_orders co ON co.custom_order_id=p.custom_order_id WHERE p.payment_action='REFUND' AND (p.created_at AT TIME ZONE ?) >= ? AND (p.created_at AT TIME ZONE ?) < ? AND co.location_id=?
""";
        LocalDate today=LocalDate.now(zone);try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,zone.getId());ps.setTimestamp(2,Timestamp.valueOf(today.atStartOfDay()));ps.setString(3,zone.getId());ps.setTimestamp(4,Timestamp.valueOf(today.plusDays(1).atStartOfDay()));ps.setInt(5,locationId);try(ResultSet rs=ps.executeQuery()){return rs.next()?zero(rs.getBigDecimal(1)):BigDecimal.ZERO;}}
    }
    private static int countLowStock(Connection c)throws SQLException {
        String sql="""
SELECT COUNT(*) FROM (SELECT 1 FROM custom_order_items WHERE is_active=TRUE AND COALESCE(has_variants,FALSE)=FALSE AND COALESCE(reorder_level,0)>0 AND COALESCE(quantity_on_hand,0)<=COALESCE(reorder_level,0) UNION ALL SELECT 1 FROM custom_order_item_variants v JOIN custom_order_items i ON i.custom_item_id=v.custom_item_id WHERE i.is_active=TRUE AND v.is_active=TRUE AND COALESCE(v.reorder_level,0)>0 AND COALESCE(v.quantity_on_hand,0)<=COALESCE(v.reorder_level,0)) low_stock
""";
        try(PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){return rs.next()?rs.getInt(1):0;}
    }
    private static List<ActionRow> actions(Connection c,int locationId)throws SQLException {
        String sql="""
SELECT custom_order_id,order_number,status,due_date,customer_name,customer_phone,COALESCE(location_name,'') location_name,COALESCE(assigned_to_name,'') assigned_to_name,COALESCE(balance_due,0) balance_due FROM custom_orders WHERE status NOT IN ('DELIVERED','CANCELLED') AND (due_date<=CURRENT_DATE OR status IN ('NEW','ASSIGNED','READY') OR COALESCE(balance_due,0)>0) AND location_id=? ORDER BY CASE WHEN due_date<CURRENT_DATE THEN 0 WHEN due_date=CURRENT_DATE THEN 1 ELSE 2 END,due_date NULLS LAST,created_at DESC LIMIT 100
""";
        List<ActionRow>out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){Date due=rs.getDate("due_date");out.add(new ActionRow(rs.getLong("custom_order_id"),rs.getString("order_number"),rs.getString("status"),due==null?null:due.toLocalDate(),rs.getString("customer_name"),rs.getString("customer_phone"),rs.getString("location_name"),rs.getString("assigned_to_name"),zero(rs.getBigDecimal("balance_due"))));}}}return out;
    }
    private static List<ExceptionRow> exceptions(Connection c,int locationId)throws SQLException {
        String sql="""
SELECT p.created_at,'REFUND' event_type,co.order_number,co.customer_name,COALESCE(p.payment_amount,0) amount,COALESCE(p.taken_by_name,'') user_name,COALESCE(p.void_reason,p.payment_reference,'') reason FROM custom_order_payments p JOIN custom_orders co ON co.custom_order_id=p.custom_order_id WHERE p.payment_action='REFUND' AND p.created_at>=CURRENT_TIMESTAMP-INTERVAL '30 days' AND co.location_id=? UNION ALL SELECT co.cancelled_at,'CANCELLED',co.order_number,co.customer_name,COALESCE(co.total_amount,0),COALESCE(co.cancelled_by_name,''),COALESCE(co.cancellation_reason,'') FROM custom_orders co WHERE co.status='CANCELLED' AND co.cancelled_at>=CURRENT_TIMESTAMP-INTERVAL '30 days' AND co.location_id=? ORDER BY created_at DESC LIMIT 100
""";
        List<ExceptionRow>out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,locationId);ps.setInt(2,locationId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){Timestamp t=rs.getTimestamp(1);out.add(new ExceptionRow(t==null?0:t.getTime(),rs.getString(2),rs.getString(3),rs.getString(4),zero(rs.getBigDecimal(5)),rs.getString(6),rs.getString(7)));}}}return out;
    }
    private static List<LowStockRow> lowStock(Connection c)throws SQLException {
        String sql="""
SELECT item_name,'' variant_name,quantity_on_hand,reorder_level FROM custom_order_items WHERE is_active=TRUE AND COALESCE(has_variants,FALSE)=FALSE AND COALESCE(reorder_level,0)>0 AND COALESCE(quantity_on_hand,0)<=COALESCE(reorder_level,0) UNION ALL SELECT i.item_name,v.variant_name,v.quantity_on_hand,v.reorder_level FROM custom_order_item_variants v JOIN custom_order_items i ON i.custom_item_id=v.custom_item_id WHERE i.is_active=TRUE AND v.is_active=TRUE AND COALESCE(v.reorder_level,0)>0 AND COALESCE(v.quantity_on_hand,0)<=COALESCE(v.reorder_level,0) ORDER BY item_name,variant_name LIMIT 100
""";
        List<LowStockRow>out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql);ResultSet rs=ps.executeQuery()){while(rs.next()){BigDecimal q=zero(rs.getBigDecimal(3)),r=zero(rs.getBigDecimal(4));out.add(new LowStockRow(rs.getString(1),rs.getString(2),q,r,q.signum()<=0?"Out":"Low"));}}return out;
    }
    private static List<AuditRow> audit(Connection c,int locationId)throws SQLException {
        String sql="""
SELECT a.created_at,co.order_number,a.action_type,COALESCE(a.field_name,''),COALESCE(a.old_value,''),COALESCE(a.new_value,''),COALESCE(a.user_name,''),COALESCE(a.device_name,a.device_id,''),COALESCE(a.reason,'') FROM custom_order_audit_log a JOIN custom_orders co ON co.custom_order_id=a.custom_order_id WHERE co.location_id=? ORDER BY a.created_at DESC LIMIT 150
""";
        List<AuditRow>out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){Timestamp t=rs.getTimestamp(1);out.add(new AuditRow(t==null?0:t.getTime(),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9)));}}}return out;
    }

    public static void assign(Connection c,long orderId,Integer employeeId,String status,int actorId,String actorName,UUID deviceId,String deviceName,int locationId)throws SQLException {
        if(!List.of("NEW","ASSIGNED","IN_PROGRESS","READY","COMPLETED").contains(status))throw new SQLException("The selected order status is invalid.");
        String oldStatus,oldAssigned;BigDecimal balance;
        try(PreparedStatement ps=c.prepareStatement("SELECT status,assigned_to_name,COALESCE(balance_due,0) FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE")){ps.setLong(1,orderId);ps.setInt(2,locationId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Order was not found for the selected store.");oldStatus=rs.getString(1);oldAssigned=rs.getString(2);balance=zero(rs.getBigDecimal(3));}}
        if("DELIVERED".equals(status)&&balance.signum()>0)throw new SQLException("This order still has a balance due. Complete payment before marking it delivered.");
        String employeeName=null;if(employeeId!=null){try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(full_name,''),username) FROM users WHERE user_id=? AND COALESCE(is_active,TRUE)=TRUE AND (EXISTS(SELECT 1 FROM user_locations WHERE user_id=? AND location_id=?) OR NOT EXISTS(SELECT 1 FROM user_locations WHERE user_id=?))")){ps.setInt(1,employeeId);ps.setInt(2,employeeId);ps.setInt(3,locationId);ps.setInt(4,employeeId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("The selected employee is not active at this store.");employeeName=rs.getString(1);}}}
        if(employeeId!=null&&"NEW".equals(status))status="ASSIGNED";
        try(PreparedStatement ps=c.prepareStatement("UPDATE custom_orders SET assigned_to_user_id=?,assigned_to_name=?,assigned_by_user_id=?,assigned_by_name=?,assigned_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE assigned_at END,status=?,completed_at=CASE WHEN ?='COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END,updated_at=CURRENT_TIMESTAMP WHERE custom_order_id=? AND location_id=?")){setInt(ps,1,employeeId);ps.setString(2,employeeName);ps.setInt(3,actorId);ps.setString(4,actorName);ps.setBoolean(5,employeeId!=null);ps.setString(6,status);ps.setString(7,status);ps.setLong(8,orderId);ps.setInt(9,locationId);ps.executeUpdate();}
        if(!safe(oldStatus).equals(status)){recordStatus(c,orderId,oldStatus,status,"Updated from orders manager dashboard",actorId,actorName,deviceId,deviceName);}
        if(!safe(oldAssigned).equals(safe(employeeName))){recordAudit(c,orderId,"ASSIGNMENT","assigned_to_name",oldAssigned,employeeName,"Assigned from orders manager dashboard",actorId,actorName,deviceId,deviceName);}
    }
    private static void recordStatus(Connection c,long id,String oldV,String newV,String reason,int uid,String name,UUID did,String dname)throws SQLException {try(PreparedStatement ps=c.prepareStatement("INSERT INTO custom_order_status_history(custom_order_id,old_status,new_status,reason,user_id,user_name,device_id,device_name) VALUES(?,?,?,?,?,?,?,?)")){ps.setLong(1,id);ps.setString(2,oldV);ps.setString(3,newV);ps.setString(4,reason);ps.setInt(5,uid);ps.setString(6,name);ps.setString(7,did.toString());ps.setString(8,dname);ps.executeUpdate();}recordAudit(c,id,"STATUS_CHANGE","status",oldV,newV,reason,uid,name,did,dname);}
    private static void recordAudit(Connection c,long id,String action,String field,Object oldV,Object newV,String reason,int uid,String name,UUID did,String dname)throws SQLException {try(PreparedStatement ps=c.prepareStatement("INSERT INTO custom_order_audit_log(custom_order_id,action_type,field_name,old_value,new_value,reason,user_id,user_name,device_id,device_name) VALUES(?,?,?,?,?,?,?,?,?,?)")){ps.setLong(1,id);ps.setString(2,action);ps.setString(3,field);ps.setString(4,oldV==null?null:oldV.toString());ps.setString(5,newV==null?null:newV.toString());ps.setString(6,reason);ps.setInt(7,uid);ps.setString(8,name);ps.setString(9,did.toString());ps.setString(10,dname);ps.executeUpdate();}}
    private static void setInt(PreparedStatement ps,int i,Integer v)throws SQLException{if(v==null)ps.setNull(i,Types.INTEGER);else ps.setInt(i,v);}private static BigDecimal zero(BigDecimal v){return v==null?BigDecimal.ZERO:v;}private static String safe(String v){return v==null?"":v;}

    public record Dashboard(Metrics metrics,List<ActionRow>actions,List<ExceptionRow>exceptions,List<LowStockRow>lowStock,List<AuditRow>audit){}
    public record Metrics(int overdue,int dueToday,int ready,int assigned,int cancelled,BigDecimal unpaid,BigDecimal refunds,int lowStock){}
    public record ActionRow(long orderId,String orderNumber,String status,LocalDate dueDate,String customer,String phone,String store,String assigned,BigDecimal balance){}
    public record ExceptionRow(long atEpochMillis,String type,String orderNumber,String customer,BigDecimal amount,String user,String reason){}
    public record LowStockRow(String item,String variant,BigDecimal quantity,BigDecimal reorder,String status){}
    public record AuditRow(long atEpochMillis,String orderNumber,String action,String field,String oldValue,String newValue,String user,String device,String reason){}
}
