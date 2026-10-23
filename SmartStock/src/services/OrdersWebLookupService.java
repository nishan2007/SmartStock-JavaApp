package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Exact order-label lookup; callers enforce the employee and browser permissions. */
final class OrdersWebLookupService {
    private OrdersWebLookupService() { }
    static LanOrdersDashboardService.ActionRow lookup(Connection c,int location,String number)throws SQLException {
        if(number==null||number.isBlank()||number.length()>160||number.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Scan a printed order label or enter its order number.");
        try(var p=c.prepareStatement("""
                SELECT custom_order_id,order_number,status,due_date,customer_name,customer_phone,
                    COALESCE(location_name,''),COALESCE(assigned_to_name,''),COALESCE(balance_due,0),pricing_state
                FROM custom_orders WHERE location_id=? AND order_type='CUSTOM' AND order_number=?
                """)) {
            p.setInt(1,location);p.setString(2,number.trim());
            try(var r=p.executeQuery()) {
                if(!r.next())return null;
                var due=r.getDate(4);
                var row=new LanOrdersDashboardService.ActionRow(r.getLong(1),r.getString(2),r.getString(3),due==null?null:due.toLocalDate(),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getBigDecimal(9),r.getString(10));
                if(r.next())throw new SQLException("More than one custom order has this order number.");
                return row;
            }
        }
    }
}
