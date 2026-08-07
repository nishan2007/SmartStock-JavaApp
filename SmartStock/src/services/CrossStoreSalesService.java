package services;

import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** Refreshes and queries the local read-only cache of other stores' transactions. */
final class CrossStoreSalesService {
    private CrossStoreSalesService() { }

    static RefreshResult refreshAll(Connection local, int currentLocationId) throws SQLException {
        SyncSchemaInstaller.ensureSchema(local);
        int stores=0,sales=0,failed=0;
        for (CrossStoreInventoryService.Store store : CrossStoreInventoryService.stores(local,currentLocationId)) {
            try { int count=refreshStore(local,store);mark(local,store,count,"CURRENT",null);stores++;sales+=count; }
            catch(SQLException ex){failed++;mark(local,store,count(local,store.locationId()),"STALE",safe(ex));}
        }
        return new RefreshResult(stores,sales,failed);
    }

    static HistoryResult history(Connection c,int userId,int currentLocationId,String search,String fromDate,
                                 String toDate,Integer requestedLocationId) throws Exception {
        requirePair(c,userId,"VIEW_SALES","VIEW_MULTI_STORE_SALES");
        String term=clean(search,300);ZoneId zone=zone(c,currentLocationId);
        Instant from=boundary(fromDate,zone,false),to=boundary(toDate,zone,true);
        List<Map<String,Object>> rows=new ArrayList<>();
        StringBuilder sql=new StringBuilder("""
          SELECT c.source_location_id,c.sale_id,c.receipt_number,c.source_created_at,c.user_name,c.store_name,
            (SELECT COUNT(*) FROM sync_cross_store_sale_items_cache i WHERE i.source_location_id=c.source_location_id AND i.sale_id=c.sale_id),
            c.payment_method,c.payment_status,c.amount_paid,c.returned_amount,c.discount_amount,c.total_amount,
            GREATEST(c.total_amount-c.returned_amount,0),c.cache_refreshed_at,c.cache_status
          FROM sync_cross_store_sales_cache c WHERE c.source_location_id<>?
          """);List<Object> args=new ArrayList<>();args.add(currentLocationId);
        if(requestedLocationId!=null){sql.append(" AND c.source_location_id=?");args.add(requestedLocationId);}
        if(!term.isBlank()){sql.append(" AND (CAST(c.sale_id AS TEXT) ILIKE ? OR COALESCE(c.receipt_number,'') ILIKE ? OR COALESCE(c.user_name,'') ILIKE ? OR c.store_name ILIKE ?)");for(int i=0;i<4;i++)args.add("%"+term+"%");}
        if(from!=null){sql.append(" AND c.source_created_at>=?");args.add(Timestamp.from(from));}
        if(to!=null){sql.append(" AND c.source_created_at<?");args.add(Timestamp.from(to));}
        sql.append(" ORDER BY c.source_created_at DESC LIMIT 1000");
        try(PreparedStatement ps=c.prepareStatement(sql.toString())){bind(ps,args);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
            Map<String,Object> row=new LinkedHashMap<>();row.put("transactionType","SALE");row.put("sourceLocationId",rs.getInt(1));
            row.put("saleId",rs.getInt(2));row.put("returnId",null);row.put("receiptNumber",rs.getString(3));
            row.put("createdAtEpochMillis",epoch(rs.getTimestamp(4)));row.put("cashierName",rs.getString(5));row.put("storeName",rs.getString(6));
            row.put("itemCount",rs.getInt(7));row.put("paymentMethod",rs.getString(8));row.put("paymentStatus",rs.getString(9));
            row.put("amountPaid",rs.getBigDecimal(10));row.put("returnedAmount",rs.getBigDecimal(11));row.put("discountAmount",rs.getBigDecimal(12));
            row.put("totalAmount",rs.getBigDecimal(13));row.put("netAmount",rs.getBigDecimal(14));row.put("cacheRefreshedAtEpochMillis",epoch(rs.getTimestamp(15)));
            row.put("cacheStatus",rs.getString(16));rows.add(row);
        }}}
        return new HistoryResult(stores(c,currentLocationId),List.copyOf(rows));
    }

    static Map<String,Object> details(Connection c,int userId,int currentLocationId,int sourceLocationId,int saleId)throws Exception{
        requirePair(c,userId,"VIEW_SALES","VIEW_MULTI_STORE_SALES");if(sourceLocationId==currentLocationId)throw rule(400,"LOCAL_SALE","Use live sale details for the current store.");
        Map<String,Object> out=new LinkedHashMap<>();
        try(PreparedStatement ps=c.prepareStatement("""
          SELECT subtotal_amount,discount_percent,discount_amount,total_amount,store_name,cache_refreshed_at,cache_status
          FROM sync_cross_store_sales_cache WHERE source_location_id=? AND sale_id=?
          """)){ps.setInt(1,sourceLocationId);ps.setInt(2,saleId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"SALE_NOT_FOUND","Sale was not found in synchronized store data.");
          out.put("saleId",saleId);out.put("sourceLocationId",sourceLocationId);out.put("subtotalAmount",rs.getBigDecimal(1));out.put("discountPercent",rs.getBigDecimal(2));
          out.put("discountAmount",rs.getBigDecimal(3));out.put("totalAmount",rs.getBigDecimal(4));out.put("sourceStoreName",rs.getString(5));
          out.put("cacheRefreshedAtEpochMillis",epoch(rs.getTimestamp(6)));out.put("cacheStatus",rs.getString(7));}}
        out.put("items",items(c,sourceLocationId,saleId));out.put("returns",returns(c,sourceLocationId,saleId));
        out.put("returnItems",returnItems(c,sourceLocationId,saleId));out.put("overrideAudit",List.of());return out;
    }

    static List<Map<String,Object>> searchForReturn(Connection c,int userId,int currentLocationId,String query,Integer sourceLocationId)throws Exception{
        requirePair(c,userId,"PROCESS_RETURNS","PROCESS_MULTI_STORE_RETURNS");String term=clean(query,300);if(term.length()<2)return List.of();
        StringBuilder sql=new StringBuilder("""
          SELECT source_location_id,sale_id,receipt_number,source_created_at,total_amount,user_name,store_name,cache_refreshed_at,cache_status
          FROM sync_cross_store_sales_cache WHERE source_location_id<>? AND
          (CAST(sale_id AS TEXT)=? OR COALESCE(receipt_number,'') ILIKE ?)""");
        if(sourceLocationId!=null)sql.append(" AND source_location_id=?");sql.append(" ORDER BY source_created_at DESC LIMIT 50");
        List<Map<String,Object>> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql.toString())){ps.setInt(1,currentLocationId);ps.setString(2,term);ps.setString(3,"%"+term+"%");if(sourceLocationId!=null)ps.setInt(4,sourceLocationId);
        try(ResultSet rs=ps.executeQuery()){while(rs.next()){Map<String,Object> row=new LinkedHashMap<>();row.put("sourceLocationId",rs.getInt(1));row.put("saleId",rs.getInt(2));row.put("receiptNumber",rs.getString(3));
          row.put("createdAtEpochMillis",epoch(rs.getTimestamp(4)));row.put("totalAmount",rs.getBigDecimal(5));row.put("cashierName",rs.getString(6));row.put("storeName",rs.getString(7));
          row.put("cacheRefreshedAtEpochMillis",epoch(rs.getTimestamp(8)));row.put("cacheStatus",rs.getString(9));row.put("deviceId","");out.add(row);}}}return List.copyOf(out);
    }

    static List<StoreOption> returnStoreOptions(Connection c,int userId,int currentLocationId)throws Exception{
        requirePair(c,userId,"PROCESS_RETURNS","PROCESS_MULTI_STORE_RETURNS");
        return stores(c,currentLocationId);
    }

    static Map<String,Object> returnDetails(Connection c,int userId,int currentLocationId,int sourceLocationId,int saleId)throws Exception{
        requirePair(c,userId,"PROCESS_RETURNS","PROCESS_MULTI_STORE_RETURNS");Map<String,Object> out=new LinkedHashMap<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT receipt_number,customer_id,payment_method,payment_status,total_amount,returned_amount FROM sync_cross_store_sales_cache WHERE source_location_id=? AND sale_id=?")){
          ps.setInt(1,sourceLocationId);ps.setInt(2,saleId);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"SALE_NOT_FOUND","Sale was not found in synchronized store data.");
          out.put("saleId",saleId);out.put("sourceLocationId",sourceLocationId);out.put("receiptNumber",rs.getString(1));out.put("customerId",rs.getObject(2));out.put("paymentMethod",rs.getString(3));
          out.put("paymentStatus",rs.getString(4));out.put("totalAmount",rs.getBigDecimal(5));out.put("returnedAmount",rs.getBigDecimal(6));}}
        BigDecimal approvalLimit=BigDecimal.ZERO;try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(sale_return_approval_limit,0) FROM company_customization WHERE location_id=?")){ps.setInt(1,currentLocationId);try(ResultSet rs=ps.executeQuery()){if(rs.next())approvalLimit=rs.getBigDecimal(1);}}
        out.put("returnApprovalLimit",approvalLimit);out.put("requesterCanOverride",hasPermission(c,userId,"RETURN_OVERRIDE"));out.put("items",returnLines(c,sourceLocationId,saleId));return out;
    }

    private static int refreshStore(Connection c,CrossStoreInventoryService.Store store)throws SQLException{
        Map<Integer,JsonObject> products=map(CrossStoreInventoryService.fetchTable(store.locationId(),"products"),"product_id");
        List<JsonObject> sales=CrossStoreInventoryService.fetchTable(store.locationId(),"sales"),items=CrossStoreInventoryService.fetchTable(store.locationId(),"sale_items"),
          returns=CrossStoreInventoryService.fetchTable(store.locationId(),"sale_returns"),returnItems=CrossStoreInventoryService.fetchTable(store.locationId(),"sale_return_items");
        Set<Integer> saleIds=new HashSet<>();
        for(JsonObject sale:sales)if(integer(sale,"location_id")==store.locationId())saleIds.add(integer(sale,"sale_id"));
        Set<Long> returnIds=new HashSet<>();
        for(JsonObject returned:returns)if(saleIds.contains(integer(returned,"sale_id")))returnIds.add(longValue(returned,"return_id"));
        boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
          for(String table:List.of("sync_cross_store_return_items_cache","sync_cross_store_returns_cache","sync_cross_store_sale_items_cache","sync_cross_store_sales_cache"))try(PreparedStatement ps=c.prepareStatement("DELETE FROM "+table+" WHERE source_location_id=?")){ps.setInt(1,store.locationId());ps.executeUpdate();}
          try(PreparedStatement ps=c.prepareStatement("INSERT INTO sync_cross_store_sales_cache(source_location_id,sale_id,store_name,receipt_number,customer_id,user_name,payment_method,payment_status,subtotal_amount,discount_percent,discount_amount,total_amount,amount_paid,returned_amount,source_created_at,source_updated_at,cache_refreshed_at,cache_status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'CURRENT')")){
            for(JsonObject x:sales){if(integer(x,"location_id")!=store.locationId())continue;ps.setInt(1,store.locationId());ps.setInt(2,integer(x,"sale_id"));ps.setString(3,store.name());ps.setString(4,text(x,"receipt_number"));setInteger(ps,5,x,"customer_id");ps.setString(6,text(x,"user_name"));ps.setString(7,text(x,"payment_method"));ps.setString(8,text(x,"payment_status"));
              ps.setBigDecimal(9,decimal(x,"subtotal_amount"));ps.setBigDecimal(10,decimal(x,"discount_percent"));ps.setBigDecimal(11,decimal(x,"discount_amount"));ps.setBigDecimal(12,decimal(x,"total_amount"));ps.setBigDecimal(13,decimal(x,"amount_paid"));ps.setBigDecimal(14,decimal(x,"returned_amount"));ps.setTimestamp(15,time(text(x,"created_at")));ps.setTimestamp(16,time(text(x,"updated_at")));ps.addBatch();}ps.executeBatch();}
          try(PreparedStatement ps=c.prepareStatement("INSERT INTO sync_cross_store_sale_items_cache(source_location_id,sale_id,sale_item_id,product_id,sku,product_name,product_type,quantity,unit_price,original_unit_price,discount_percent,discount_amount) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")){
            for(JsonObject x:items){if(!saleIds.contains(integer(x,"sale_id")))continue;int productId=integer(x,"product_id");JsonObject p=products.get(productId);if(p==null)continue;ps.setInt(1,store.locationId());ps.setInt(2,integer(x,"sale_id"));ps.setInt(3,integer(x,"sale_item_id"));ps.setInt(4,productId);ps.setString(5,text(p,"sku"));ps.setString(6,text(p,"name"));ps.setString(7,text(x,"product_type"));ps.setInt(8,integer(x,"quantity"));ps.setBigDecimal(9,decimal(x,"unit_price"));ps.setBigDecimal(10,decimal(x,"original_unit_price"));ps.setBigDecimal(11,decimal(x,"discount_percent"));ps.setBigDecimal(12,decimal(x,"discount_amount"));ps.addBatch();}ps.executeBatch();}
          batchReturns(c,store.locationId(),returns,returnItems,saleIds,returnIds);c.commit();return saleIds.size();
        }catch(SQLException ex){c.rollback();throw ex;}finally{c.setAutoCommit(auto);}
    }

    private static void batchReturns(Connection c,int loc,List<JsonObject> returns,List<JsonObject> items,Set<Integer> saleIds,Set<Long> returnIds)throws SQLException{
      try(PreparedStatement ps=c.prepareStatement("INSERT INTO sync_cross_store_returns_cache(source_location_id,return_id,sale_id,user_name,refund_method,refund_amount,reason,source_created_at) VALUES(?,?,?,?,?,?,?,?)")){for(JsonObject x:returns){if(!saleIds.contains(integer(x,"sale_id")))continue;ps.setInt(1,loc);ps.setLong(2,longValue(x,"return_id"));ps.setInt(3,integer(x,"sale_id"));ps.setString(4,text(x,"user_name"));ps.setString(5,text(x,"refund_method"));ps.setBigDecimal(6,decimal(x,"refund_amount"));ps.setString(7,text(x,"reason"));ps.setTimestamp(8,time(text(x,"created_at")));ps.addBatch();}ps.executeBatch();}
      try(PreparedStatement ps=c.prepareStatement("INSERT INTO sync_cross_store_return_items_cache(source_location_id,return_item_id,return_id,sale_item_id,product_id,quantity,unit_price) VALUES(?,?,?,?,?,?,?)")){for(JsonObject x:items){if(!returnIds.contains(longValue(x,"return_id")))continue;ps.setInt(1,loc);ps.setLong(2,longValue(x,"return_item_id"));ps.setLong(3,longValue(x,"return_id"));ps.setInt(4,integer(x,"sale_item_id"));ps.setInt(5,integer(x,"product_id"));ps.setInt(6,integer(x,"quantity"));ps.setBigDecimal(7,decimal(x,"unit_price"));ps.addBatch();}ps.executeBatch();}
    }

    private static List<Map<String,Object>> items(Connection c,int loc,int sale)throws SQLException{return query(c,"SELECT i.product_id,i.product_name,i.quantity,COALESCE((SELECT SUM(r.quantity) FROM sync_cross_store_return_items_cache r WHERE r.source_location_id=i.source_location_id AND r.sale_item_id=i.sale_item_id),0),i.original_unit_price,i.discount_percent,i.discount_amount,i.unit_price,i.quantity*i.unit_price FROM sync_cross_store_sale_items_cache i WHERE i.source_location_id=? AND i.sale_id=? ORDER BY i.sale_item_id",loc,sale,new String[]{"productId","productName","quantity","returnedQuantity","originalUnitPrice","discountPercent","discountAmount","unitPrice","lineTotal"});}
    private static List<Map<String,Object>> returns(Connection c,int loc,int sale)throws SQLException{return query(c,"SELECT return_id,source_created_at,user_name,refund_method,refund_amount,reason FROM sync_cross_store_returns_cache WHERE source_location_id=? AND sale_id=? ORDER BY source_created_at DESC",loc,sale,new String[]{"returnId","createdAtEpochMillis","userName","refundMethod","refundAmount","reason"});}
    private static List<Map<String,Object>> returnItems(Connection c,int loc,int sale)throws SQLException{return query(c,"SELECT ri.return_id,ri.product_id,i.product_name,ri.quantity,ri.unit_price,ri.quantity*ri.unit_price FROM sync_cross_store_return_items_cache ri JOIN sync_cross_store_returns_cache r ON r.source_location_id=ri.source_location_id AND r.return_id=ri.return_id LEFT JOIN sync_cross_store_sale_items_cache i ON i.source_location_id=ri.source_location_id AND i.sale_item_id=ri.sale_item_id WHERE r.source_location_id=? AND r.sale_id=?",loc,sale,new String[]{"returnId","productId","productName","quantity","unitPrice","lineTotal"});}
    private static List<Map<String,Object>> returnLines(Connection c,int loc,int sale)throws SQLException{return query(c,"SELECT i.sale_item_id,i.product_id,i.sku,i.product_name,i.product_type,i.quantity,COALESCE((SELECT SUM(r.quantity) FROM sync_cross_store_return_items_cache r WHERE r.source_location_id=i.source_location_id AND r.sale_item_id=i.sale_item_id),0),i.quantity-COALESCE((SELECT SUM(r.quantity) FROM sync_cross_store_return_items_cache r WHERE r.source_location_id=i.source_location_id AND r.sale_item_id=i.sale_item_id),0),i.unit_price FROM sync_cross_store_sale_items_cache i WHERE i.source_location_id=? AND i.sale_id=? ORDER BY i.sale_item_id",loc,sale,new String[]{"saleItemId","productId","sku","productName","productType","soldQuantity","returnedQuantity","availableQuantity","unitPrice"});}
    private static List<Map<String,Object>> query(Connection c,String sql,int loc,int sale,String[] names)throws SQLException{List<Map<String,Object>> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,loc);ps.setInt(2,sale);try(ResultSet rs=ps.executeQuery()){while(rs.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=0;i<names.length;i++){Object v=rs.getObject(i+1);if(names[i].endsWith("EpochMillis")&&v instanceof Timestamp t)v=t.getTime();row.put(names[i],v);}out.add(row);}}}return out;}
    private static List<StoreOption> stores(Connection c,int current)throws SQLException{List<StoreOption> out=new ArrayList<>();for(var s:CrossStoreInventoryService.stores(c,current)){try(PreparedStatement ps=c.prepareStatement("SELECT status,refreshed_at FROM sync_cross_store_sales_status WHERE source_location_id=?")){ps.setInt(1,s.locationId());try(ResultSet rs=ps.executeQuery()){out.add(rs.next()?new StoreOption(s.locationId(),s.name(),rs.getString(1),epoch(rs.getTimestamp(2))):new StoreOption(s.locationId(),s.name(),"NOT_SYNCED",0));}}}return List.copyOf(out);}
    private static void mark(Connection c,CrossStoreInventoryService.Store s,int count,String status,String error)throws SQLException{try(PreparedStatement ps=c.prepareStatement("INSERT INTO sync_cross_store_sales_status(source_location_id,store_name,row_count,status,last_error,refreshed_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(source_location_id) DO UPDATE SET store_name=EXCLUDED.store_name,row_count=EXCLUDED.row_count,status=EXCLUDED.status,last_error=EXCLUDED.last_error,refreshed_at=CURRENT_TIMESTAMP")){ps.setInt(1,s.locationId());ps.setString(2,s.name());ps.setInt(3,count);ps.setString(4,status);ps.setString(5,error);ps.executeUpdate();}}
    private static int count(Connection c,int loc)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM sync_cross_store_sales_cache WHERE source_location_id=?")){ps.setInt(1,loc);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}}
    private static void requirePair(Connection c,int user,String a,String b)throws Exception{try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(DISTINCT UPPER(p.permission_key)) FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key) IN (?,?)")){ps.setInt(1,user);ps.setString(2,a);ps.setString(3,b);try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getInt(1)==2)return;}}throw rule(403,"PERMISSION_DENIED","Both local and multistore permissions are required.");}
    private static boolean hasPermission(Connection c,int user,String permission)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)=? LIMIT 1")){ps.setInt(1,user);ps.setString(2,permission);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static Map<Integer,JsonObject> map(List<JsonObject> rows,String key){Map<Integer,JsonObject> out=new HashMap<>();for(JsonObject row:rows)out.put(integer(row,key),row);return out;}
    private static int integer(JsonObject x,String k){return x.has(k)&&!x.get(k).isJsonNull()?x.get(k).getAsInt():0;}private static long longValue(JsonObject x,String k){return x.has(k)&&!x.get(k).isJsonNull()?x.get(k).getAsLong():0;}
    private static String text(JsonObject x,String k){return x.has(k)&&!x.get(k).isJsonNull()?x.get(k).getAsString():null;}private static BigDecimal decimal(JsonObject x,String k){try{return new BigDecimal(text(x,k));}catch(Exception ex){return BigDecimal.ZERO;}}
    private static Timestamp time(String x){try{return x==null?null:Timestamp.from(Instant.parse(x));}catch(Exception ex){return null;}}private static long epoch(Timestamp x){return x==null?0:x.getTime();}
    private static void setInteger(PreparedStatement ps,int n,JsonObject x,String k)throws SQLException{if(!x.has(k)||x.get(k).isJsonNull())ps.setNull(n,java.sql.Types.INTEGER);else ps.setInt(n,x.get(k).getAsInt());}
    private static void bind(PreparedStatement ps,List<Object> a)throws SQLException{for(int i=0;i<a.size();i++)ps.setObject(i+1,a.get(i));}private static String clean(String s,int max)throws RuleViolation{String v=s==null?"":s.trim();if(v.length()>max)throw rule(400,"VALIDATION_ERROR","Search text is too long.");return v;}
    private static ZoneId zone(Connection c,int id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT timezone FROM locations WHERE location_id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(rs.next())try{return ZoneId.of(rs.getString(1));}catch(Exception ignored){}}}return ZoneId.systemDefault();}
    private static Instant boundary(String s,ZoneId z,boolean end)throws RuleViolation{if(s==null||s.isBlank())return null;try{LocalDate d=LocalDate.parse(s);return(end?d.plusDays(1):d).atStartOfDay(z).toInstant();}catch(Exception ex){throw rule(400,"VALIDATION_ERROR","Dates must use yyyy-MM-dd format.");}}
    private static String safe(Exception ex){String s=ex.getMessage();return s==null?ex.getClass().getSimpleName():s.substring(0,Math.min(500,s.length()));}private static RuleViolation rule(int s,String c,String m){return new RuleViolation(s,c,m);}
    record RefreshResult(int storesRefreshed,int salesRefreshed,int storesFailed){}record HistoryResult(List<StoreOption> stores,List<Map<String,Object>> transactions){}record StoreOption(int locationId,String name,String status,long refreshedAtEpochMillis){}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
