package services;

import java.sql.*;
import java.util.*;

/** Structured, store-scoped read model; legacy text details remain available. */
public final class CustomOrderDetailsService {
    private CustomOrderDetailsService() {}
    public static Map<String,Object> read(Connection c,long id,int location)throws SQLException {
        var summaries=rows(c,"SELECT custom_order_id,customer_id,loyalty_discount_amount,order_number,customer_name,customer_phone,status,due_date,assigned_to_name,payment_method,payment_status,payment_reference,total_amount,amount_paid,balance_due,order_notes,invoice_billed,pricing_state,pricing_defer_reason,pricing_defer_by_name,pricing_finalized_at,pricing_finalized_by_name FROM custom_orders WHERE custom_order_id=? AND location_id=?",id,location);
        if(summaries.isEmpty())throw new SQLException("Order was not found for this store.");
        Map<String,Object> result=new LinkedHashMap<>();result.put("summary",summaries.get(0));
        result.put("lines",rows(c,"SELECT custom_order_line_id,custom_item_id,custom_variant_id,width_value,length_value,dimension_unit,area_value,area_unit,area_price,item_name,variant_name,item_size,item_color,line_total,production_status,delivery_status,return_status,returned_amount,customization_details,order_instructions FROM custom_order_lines WHERE custom_order_id=? ORDER BY sort_order,custom_order_line_id",id));
        result.put("files",rows(c,"SELECT custom_order_line_id,filename,content_type,byte_size,sha256 FROM custom_order_files WHERE custom_order_id=? AND removed_at IS NULL AND deleted_at IS NULL ORDER BY filename,sha256",id));
        result.put("addons",rows(c,"SELECT a.custom_order_line_id,a.print_material_name,a.print_size_name,a.print_description,a.print_charge FROM custom_order_line_print_addons a JOIN custom_order_lines l ON l.custom_order_line_id=a.custom_order_line_id WHERE l.custom_order_id=? ORDER BY a.sort_order,a.custom_order_line_print_addon_id",id));
        result.put("payments",rows(c,"SELECT created_at,payment_action,payment_amount,payment_method,payment_reference,taken_by_name,void_reason,voided_at FROM custom_order_payments WHERE custom_order_id=? ORDER BY created_at DESC,custom_order_payment_id DESC",id));
        result.put("activity",rows(c,"SELECT created_at,action_type,field_name,old_value,new_value,user_name,reason FROM custom_order_audit_log WHERE custom_order_id=? ORDER BY created_at DESC",id));
        result.put("deliveries",rows(c,"SELECT delivered_at,item_name,device_name,delivered_by_name,delivery_notes FROM custom_order_line_deliveries WHERE custom_order_id=? ORDER BY delivered_at DESC",id));
        result.put("production",rows(c,"SELECT created_at,item_name,old_status,new_status,device_name,updated_by_name,notes FROM custom_order_line_production_history WHERE custom_order_id=? ORDER BY created_at DESC",id));
        result.put("returns",rows(c,"SELECT created_at,item_name,return_type,refund_amount,restock_action,created_by_name,reason FROM custom_order_line_returns WHERE custom_order_id=? ORDER BY created_at DESC",id));
        result.put("authorization",rows(c,"SELECT representative_name,captured_at,captured_by_name,device_name,unsigned_reason,signature_purged_at,signature_png IS NOT NULL AS has_signature FROM customer_charge_authorizations WHERE document_type='CUSTOM_ORDER' AND document_id=?",id));
        return result;
    }
    private static List<Map<String,Object>> rows(Connection c,String sql,Object... args)throws SQLException {
        List<Map<String,Object>> rows=new ArrayList<>();try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++){Object value=r.getObject(i);if(value instanceof Timestamp t)value=t.toInstant().toString();else if(value instanceof java.sql.Date d)value=d.toString();row.put(r.getMetaData().getColumnLabel(i),value);}rows.add(row);}}}return rows;
    }
}
