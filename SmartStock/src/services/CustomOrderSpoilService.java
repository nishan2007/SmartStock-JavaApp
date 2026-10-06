package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import java.security.MessageDigest;

/** Internal, store-scoped spoil evidence and replacement consumption. Caller owns transaction. */
public final class CustomOrderSpoilService {
    private CustomOrderSpoilService() {}
    public record Photo(byte[] bytes) {
        public Photo { if(bytes==null||bytes.length==0||bytes.length>2*1024*1024)throw new IllegalArgumentException("Each photo must be between 1 byte and 2 MB.");bytes=bytes.clone(); }
        @Override public byte[] bytes(){return bytes.clone();}
    }
    static String reason(String value){if(value==null||value.isBlank()||value.trim().length()>2000)throw new IllegalArgumentException("Enter a reason of up to 2000 characters.");return value.trim();}
    static boolean eligible(String order,String delivery,String returned){return !List.of("DELIVERED","CANCELLED").contains(order)&&!"DELIVERED".equals(delivery)&&!"FULL".equals(returned);}
    public static List<Map<String,Object>> search(Connection c,int location,String query)throws SQLException{
        String q=query==null?"":query.trim();if(q.length()>120)throw new IllegalArgumentException("Search is too long.");
        return rows(c,"SELECT custom_order_id AS \"orderId\",order_number AS \"number\",customer_name AS customer,status FROM custom_orders WHERE location_id=? AND (?='' OR order_number ILIKE ? OR customer_name ILIKE ?) ORDER BY created_at DESC LIMIT 50",location,q,"%"+q+"%","%"+q+"%");
    }
    public static Map<String,Object> state(Connection c,int location,long order)throws SQLException{
        var orders=rows(c,"SELECT custom_order_id AS \"orderId\",order_number AS \"number\",customer_name AS customer,status FROM custom_orders WHERE custom_order_id=? AND location_id=?",order,location);
        if(orders.isEmpty())throw new IllegalArgumentException("Order unavailable at this store.");
        var lines=rows(c,"""
          SELECT l.custom_order_line_id AS "lineId",l.item_name AS item,COALESCE(l.variant_name,'') AS variant,
          COALESCE(l.order_instructions,'') AS notes,COALESCE(l.production_status,'NOT_STARTED') AS production,
          l.delivery_status AS delivery,l.return_status AS returned,
          COALESCE(i.product_type,'SERVICE') AS "productType",
          CASE WHEN l.custom_variant_id IS NULL THEN i.quantity_on_hand ELSE v.quantity_on_hand END AS stock
          FROM custom_order_lines l JOIN custom_orders o ON o.custom_order_id=l.custom_order_id
          LEFT JOIN custom_order_items i ON i.custom_item_id=l.custom_item_id
          LEFT JOIN custom_order_item_variants v ON v.custom_variant_id=l.custom_variant_id
          WHERE o.custom_order_id=? AND o.location_id=? ORDER BY l.sort_order,l.custom_order_line_id
          """,order,location);
        for(var line:lines)line.put("eligible",eligible(String.valueOf(orders.get(0).get("status")),String.valueOf(line.get("delivery")),String.valueOf(line.get("returned"))));
        var reports=rows(c,"""
          SELECT spoil_id AS id,custom_order_line_id AS "lineId",reason,stock_deducted AS "stockDeducted",
          created_by_name AS employee,created_at AS "createdAt",reversed_at AS "reversedAt",
          reversed_by_name AS "reversedBy",reversal_reason AS "reversalReason"
          FROM custom_order_spoils WHERE custom_order_id=? AND location_id=? ORDER BY created_at DESC
          """,order,location);
        for(var report:reports)report.put("photos",rows(c,"SELECT photo_id AS id FROM custom_order_spoil_photos WHERE spoil_id=? ORDER BY created_at,photo_id",UUID.fromString(report.get("id").toString())));
        return Map.of("order",orders.get(0),"lines",lines,"reports",reports);
    }
    public static Map<String,Object> submit(Connection c,int location,int user,String employee,UUID id,long order,long line,String explanation,List<Photo> photos)throws Exception{
        if(c.getAutoCommit())throw new SQLException("Spoil submission requires a transaction.");
        String why=reason(explanation);if(photos==null||photos.isEmpty()||photos.size()>3)throw new IllegalArgumentException("Attach between one and three photos.");
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){p.setString(1,"spoil:"+id);p.execute();}
        var prior=rows(c,"SELECT custom_order_id,custom_order_line_id,created_by_user_id,reason,stock_deducted,stock_after FROM custom_order_spoils WHERE spoil_id=? AND location_id=?",id,location);
        if(!prior.isEmpty()){
            var r=prior.get(0);if(((Number)r.get("custom_order_id")).longValue()!=order||((Number)r.get("custom_order_line_id")).longValue()!=line||((Number)r.get("created_by_user_id")).intValue()!=user||!why.equals(r.get("reason")))throw new IllegalArgumentException("This report ID belongs to a different submission.");
            var hashes=rows(c,"SELECT sha256 FROM custom_order_spoil_photos WHERE spoil_id=? ORDER BY sha256",id);
            List<String> supplied=new ArrayList<>();for(Photo photo:photos)supplied.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(photo.bytes())));Collections.sort(supplied);
            if(!supplied.equals(hashes.stream().map(h->h.get("sha256").toString()).toList()))throw new IllegalArgumentException("This report ID was used with different photos.");
            return Map.of("id",id.toString(),"alreadyRecorded",true,"stockDeducted",r.get("stock_deducted"),"shortage",r.get("stock_after") instanceof BigDecimal stock&&stock.signum()<0);
        }
        // Lock the order first, then line, then parent item and variant for consistent stock updates.
        var locked=rows(c,"SELECT status FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE",order,location);
        if(locked.isEmpty())throw new IllegalArgumentException("Order unavailable at this store.");
        var data=rows(c,"SELECT custom_item_id,custom_variant_id,COALESCE(variant_name,'') AS variant,delivery_status,return_status FROM custom_order_lines WHERE custom_order_id=? AND custom_order_line_id=? FOR UPDATE",order,line);
        if(data.isEmpty())throw new IllegalArgumentException("Order line unavailable.");var l=data.get(0);
        if(!eligible(locked.get(0).get("status").toString(),String.valueOf(l.get("delivery_status")),String.valueOf(l.get("return_status"))))throw new IllegalArgumentException("Only active, undelivered lines can have new spoils.");
        Long item=l.get("custom_item_id")==null?null:((Number)l.get("custom_item_id")).longValue(),variant=l.get("custom_variant_id")==null?null:((Number)l.get("custom_variant_id")).longValue();
        boolean deduct=false;
        if(item!=null){var items=rows(c,"SELECT COALESCE(product_type,'INVENTORY') AS product_type FROM custom_order_items WHERE custom_item_id=? FOR UPDATE",item);if(items.isEmpty())throw new IllegalArgumentException("The original item is unavailable.");deduct="INVENTORY".equals(items.get(0).get("product_type"));}
        execute(c,"INSERT INTO custom_order_spoils(spoil_id,location_id,custom_order_id,custom_order_line_id,custom_item_id,custom_variant_id,stock_deducted,reason,created_by_user_id,created_by_name) VALUES(?,?,?,?,?,?,?,?,?,?)",id,location,order,line,item,variant,deduct,why,user,employee);
        for(Photo photo:photos){byte[] bytes=photo.bytes();String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));execute(c,"INSERT INTO custom_order_spoil_photos(photo_id,location_id,spoil_id,bytes_base64,sha256) VALUES(?,?,?,?,?)",UUID.randomUUID(),location,id,Base64.getEncoder().encodeToString(bytes),hash);}
        BigDecimal stock=deduct?move(c,item,variant,location,order,line,id,-1,why,user,employee):null;
        execute(c,"UPDATE custom_order_spoils SET stock_after=? WHERE spoil_id=?",stock,id);
        CustomOrderMediaService.audit(c,order,"ORDER_SPOIL_RECORDED",id.toString(),why,user,employee);
        return Map.of("id",id.toString(),"stockDeducted",deduct,"shortage",stock!=null&&stock.signum()<0);
    }
    public static Map<String,Object> reverse(Connection c,int location,int user,String employee,UUID id,String explanation)throws Exception{
        if(c.getAutoCommit())throw new SQLException("Spoil reversal requires a transaction.");String why=reason(explanation);
        var initial=rows(c,"SELECT custom_order_id FROM custom_order_spoils WHERE spoil_id=? AND location_id=?",id,location);if(initial.isEmpty())throw new IllegalArgumentException("Spoil report unavailable.");long order=((Number)initial.get(0).get("custom_order_id")).longValue();
        rows(c,"SELECT custom_order_id FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE",order,location);
        var found=rows(c,"SELECT * FROM custom_order_spoils WHERE spoil_id=? AND location_id=? FOR UPDATE",id,location);var r=found.get(0);
        if(r.get("reversed_at")!=null)return Map.of("reversed",true,"alreadyReversed",true);
        long line=((Number)r.get("custom_order_line_id")).longValue();
        if(Boolean.TRUE.equals(r.get("stock_deducted")))move(c,((Number)r.get("custom_item_id")).longValue(),r.get("custom_variant_id")==null?null:((Number)r.get("custom_variant_id")).longValue(),location,order,line,id,1,why,user,employee);
        execute(c,"UPDATE custom_order_spoils SET reversed_at=now(),reversed_by_user_id=?,reversed_by_name=?,reversal_reason=?,updated_at=now() WHERE spoil_id=?",user,employee,why,id);
        CustomOrderMediaService.audit(c,order,"ORDER_SPOIL_REVERSED",id.toString(),why,user,employee);return Map.of("reversed",true);
    }
    public static Map<String,Object> photo(Connection c,int location,UUID id)throws SQLException{
        var rows=rows(c,"SELECT p.bytes_base64 AS \"bytesBase64\",p.content_type AS \"contentType\" FROM custom_order_spoil_photos p JOIN custom_order_spoils s ON s.spoil_id=p.spoil_id WHERE p.photo_id=? AND s.location_id=?",id,location);
        if(rows.isEmpty())throw new IllegalArgumentException("Photo unavailable.");return rows.get(0);
    }
    private static BigDecimal move(Connection c,long item,Long variant,int location,long order,long line,UUID id,int delta,String why,int user,String employee)throws SQLException{
        var parent=rows(c,"SELECT custom_item_id FROM custom_order_items WHERE custom_item_id=? FOR UPDATE",item);if(parent.isEmpty())throw new SQLException("Replacement item unavailable.");
        List<Map<String,Object>> updated;
        if(variant==null)updated=rows(c,"UPDATE custom_order_items SET quantity_on_hand=COALESCE(quantity_on_hand,0)+?,updated_at=now() WHERE custom_item_id=? RETURNING quantity_on_hand AS stock",delta,item);
        else{
            updated=rows(c,"UPDATE custom_order_item_variants SET quantity_on_hand=COALESCE(quantity_on_hand,0)+?,updated_at=now() WHERE custom_variant_id=? AND custom_item_id=? RETURNING quantity_on_hand AS stock",delta,variant,item);
            execute(c,"UPDATE custom_order_items SET quantity_on_hand=COALESCE((SELECT SUM(quantity_on_hand) FROM custom_order_item_variants WHERE custom_item_id=? AND is_active),0),updated_at=now() WHERE custom_item_id=?",item,item);
        }
        if(updated.isEmpty())throw new SQLException("Replacement variant unavailable.");
        execute(c,"INSERT INTO custom_order_item_movements(custom_item_id,custom_variant_id,location_id,change_qty,reason,note,user_name,user_id,device_name,custom_order_id,custom_order_line_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",item,variant,location,delta,delta<0?"SPOIL_REPLACEMENT":"SPOIL_REVERSAL","spoil_id="+id+"; "+why,employee,user,"WEB APP",order,line);
        return (BigDecimal)updated.get(0).get("stock");
    }
    private static void execute(Connection c,String sql,Object...args)throws SQLException{try(var p=c.prepareStatement(sql)){bind(p,args);p.executeUpdate();}}
    private static void bind(PreparedStatement p,Object[] args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
    private static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();try(var p=c.prepareStatement(sql)){bind(p,args);try(var r=p.executeQuery()){var meta=r.getMetaData();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=meta.getColumnCount();i++){Object v=r.getObject(i);row.put(meta.getColumnLabel(i),v instanceof Timestamp||v instanceof UUID?v.toString():v);}out.add(row);}}}return out;
    }
}
