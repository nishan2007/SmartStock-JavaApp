package services;

import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds a read-only, store-qualified customer-history cache from verified snapshots. */
final class CrossStoreCustomerHistoryService {
    private CrossStoreCustomerHistoryService() { }

    static RefreshResult refreshAll(Connection c, int currentLocationId) throws SQLException {
        int stores=0,rows=0,failed=0;
        for (CrossStoreInventoryService.Store store:CrossStoreInventoryService.stores(c,currentLocationId)) {
            try { int count=refreshStore(c,store);rows+=count;stores++;mark(c,store,count,"CURRENT",null); }
            catch (SQLException ex) { failed++; mark(c,store,count(c,store.locationId()),"STALE",safe(ex)); }
        }
        return new RefreshResult(stores,rows,failed);
    }

    private static int refreshStore(Connection c,CrossStoreInventoryService.Store store)throws SQLException{
        CloudSyncManifest manifest;
        try { manifest=CloudSyncManifest.fetchStoreSnapshot(store.locationId()); }
        catch(java.io.IOException ex){throw new SQLException("The verified snapshot for "+store.name()+" is unavailable.",ex);}
        String generation=manifest.snapshotGenerationId();
        List<Event> events=new ArrayList<>();
        for(JsonObject x:CrossStoreInventoryService.fetchTable(store.locationId(),generation,"customer_account_transactions")){
            if(integer(x,"location_id")!=store.locationId())continue;
            long id=longValue(x,"transaction_id");Long source=firstLong(x,"invoice_id","custom_order_id","sale_id","sales_order_id");
            String number=firstText(x,"payment_id","payment_reference");
            events.add(new Event("LEDGER:"+id,integer(x,"customer_id"),text(x,"transaction_type"),source,number,
                    time(text(x,"created_at")),text(x,"user_name"),firstText(x,"device_name","device_id"),text(x,"cash_drawer_name"),
                    text(x,"payment_method"),text(x,"payment_reference"),decimal(x,"amount"),"","",BigDecimal.ZERO,text(x,"note")));
        }
        addDocuments(events,store,generation,"sales","SALE","sale_id","receipt_number","total_amount","payment_status","status","user_name","created_at");
        addDocuments(events,store,generation,"custom_orders","CUSTOM_ORDER","custom_order_id","order_number","total_amount","payment_status","status","taken_by_name","created_at");
        addDocuments(events,store,generation,"quotations","QUOTATION","quotation_id","quotation_number","total_amount",null,"status","created_by_name","created_at");
        addDocuments(events,store,generation,"invoices","INVOICE","invoice_id","invoice_number","total_amount","payment_status","status","created_by_name","created_at");
        boolean auto=c.getAutoCommit();c.setAutoCommit(false);try{
            try(PreparedStatement ps=c.prepareStatement("DELETE FROM sync_cross_store_customer_history_cache WHERE source_location_id=?")){ps.setInt(1,store.locationId());ps.executeUpdate();}
            try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO sync_cross_store_customer_history_cache(source_location_id,event_key,customer_id,event_type,source_id,document_number,
                  source_created_at,store_name,user_name,device_name,cash_drawer_name,payment_method,payment_reference,amount,payment_status,
                  document_status,document_total,note,cache_refreshed_at,cache_status)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,'CURRENT')
                """)){
                for(Event e:events){int n=1;ps.setInt(n++,store.locationId());ps.setString(n++,e.key);ps.setInt(n++,e.customerId);ps.setString(n++,e.type);
                    if(e.sourceId==null)ps.setNull(n++,java.sql.Types.BIGINT);else ps.setLong(n++,e.sourceId);ps.setString(n++,e.number);ps.setTimestamp(n++,e.created);
                    ps.setString(n++,store.name());ps.setString(n++,e.user);ps.setString(n++,e.device);ps.setString(n++,e.drawer);ps.setString(n++,e.method);
                    ps.setString(n++,e.reference);ps.setBigDecimal(n++,e.amount);ps.setString(n++,e.paymentStatus);ps.setString(n++,e.documentStatus);
                    ps.setBigDecimal(n++,e.total);ps.setString(n,e.note);ps.addBatch();}ps.executeBatch();}
            c.commit();return events.size();
        }catch(SQLException ex){c.rollback();throw ex;}finally{c.setAutoCommit(auto);}
    }

    private static void addDocuments(List<Event> out,CrossStoreInventoryService.Store store,String generation,String table,String type,
                                     String idKey,String numberKey,String totalKey,String paymentStatusKey,String statusKey,String userKey,String createdKey)throws SQLException{
        for(JsonObject x:CrossStoreInventoryService.fetchTable(store.locationId(),generation,table)){
            if(integer(x,"location_id")!=store.locationId()||integer(x,"customer_id")<=0)continue;
            long id=longValue(x,idKey);BigDecimal total=decimal(x,totalKey);
            out.add(new Event(type+":"+id,integer(x,"customer_id"),type,id,text(x,numberKey),time(text(x,createdKey)),text(x,userKey),
                    firstText(x,"device_name","device_id"),text(x,"cash_drawer_name"),text(x,"payment_method"),text(x,"payment_reference"),
                    total,paymentStatusKey==null?"":text(x,paymentStatusKey),text(x,statusKey),total,firstText(x,"invoice_notes","quotation_notes","order_notes")));
        }
    }

    static List<Map<String,Object>> rows(Connection c,int customerId,int currentLocationId)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("""
            SELECT source_location_id,event_key,event_type,source_id,document_number,source_created_at,store_name,user_name,device_name,
              cash_drawer_name,payment_method,payment_reference,amount,payment_status,document_status,document_total,note
            FROM sync_cross_store_customer_history_cache WHERE customer_id=? AND source_location_id<>? ORDER BY source_created_at DESC,event_key
            """)){ps.setInt(1,customerId);ps.setInt(2,currentLocationId);try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                Map<String,Object> m=new LinkedHashMap<>();m.put("locationId",rs.getInt(1));m.put("eventId","REMOTE:"+rs.getInt(1)+":"+rs.getString(2));m.put("transactionType",rs.getString(3));
                m.put("transactionId",null);m.put("paymentId","");String eventType=rs.getString(3);m.put("documentType",List.of("SALE","CUSTOM_ORDER","QUOTATION","INVOICE").contains(eventType)?eventType:"ACCOUNT");m.put("documentId",rs.getObject(4));m.put("documentNumber",rs.getString(5));m.put("createdAtEpochMillis",epoch(rs.getTimestamp(6)));
                m.put("storeName",rs.getString(7));m.put("userName",rs.getString(8));m.put("deviceName",rs.getString(9));m.put("cashDrawerName",rs.getString(10));
                m.put("paymentMethod",rs.getString(11));m.put("paymentReference",rs.getString(12));m.put("amount",rs.getBigDecimal(13));m.put("paymentStatus",rs.getString(14));
                m.put("documentStatus",rs.getString(15));m.put("chargeTotal",rs.getBigDecimal(16));m.put("note",rs.getString(17));m.put("remote",true);out.add(m);
            }}}return out;
    }

    static boolean allStoresCurrent(Connection c,int currentLocationId)throws SQLException{try(PreparedStatement ps=c.prepareStatement("""
        SELECT NOT EXISTS (SELECT 1 FROM locations l WHERE l.location_id<>?
          AND NOT EXISTS (SELECT 1 FROM sync_cross_store_customer_history_status s WHERE s.source_location_id=l.location_id AND s.status='CURRENT'))
        """ )){ps.setInt(1,currentLocationId);try(ResultSet rs=ps.executeQuery()){return rs.next()&&rs.getBoolean(1);}}}
    private static void mark(Connection c,CrossStoreInventoryService.Store store,int count,String status,String error)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
          INSERT INTO sync_cross_store_customer_history_status(source_location_id,store_name,row_count,status,last_error,refreshed_at)
          VALUES(?,?,?,?,?,CURRENT_TIMESTAMP) ON CONFLICT(source_location_id) DO UPDATE SET store_name=EXCLUDED.store_name,row_count=EXCLUDED.row_count,
            status=EXCLUDED.status,last_error=EXCLUDED.last_error,refreshed_at=CURRENT_TIMESTAMP
          """)){ps.setInt(1,store.locationId());ps.setString(2,store.name());ps.setInt(3,count);ps.setString(4,status);ps.setString(5,error);ps.executeUpdate();}
        try(PreparedStatement ps=c.prepareStatement("UPDATE sync_cross_store_customer_history_cache SET cache_status=? WHERE source_location_id=?")){ps.setString(1,status);ps.setInt(2,store.locationId());ps.executeUpdate();}}
    private static int count(Connection c,int location)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COUNT(*) FROM sync_cross_store_customer_history_cache WHERE source_location_id=?")){ps.setInt(1,location);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}}
    private static String safe(Exception ex){String x=ex.getMessage();if(x==null)x=ex.getClass().getSimpleName();return x.substring(0,Math.min(500,x.length()));}
    private static int integer(JsonObject x,String k){return x.has(k)&&!x.get(k).isJsonNull()?x.get(k).getAsInt():0;}
    private static long longValue(JsonObject x,String k){return x.has(k)&&!x.get(k).isJsonNull()?x.get(k).getAsLong():0;}
    private static Long firstLong(JsonObject x,String...keys){for(String k:keys)if(x.has(k)&&!x.get(k).isJsonNull())return x.get(k).getAsLong();return null;}
    private static String text(JsonObject x,String k){return k!=null&&x.has(k)&&!x.get(k).isJsonNull()?x.get(k).getAsString():"";}
    private static String firstText(JsonObject x,String...keys){for(String k:keys){String v=text(x,k);if(!v.isBlank())return v;}return "";}
    private static BigDecimal decimal(JsonObject x,String k){try{return new BigDecimal(text(x,k));}catch(Exception ex){return BigDecimal.ZERO;}}
    private static Timestamp time(String x){return CrossStoreSalesService.time(x);}
    private static long epoch(Timestamp x){return x==null?0:x.getTime();}
    record RefreshResult(int storesRefreshed,int rowsRefreshed,int storesFailed){}
    private record Event(String key,int customerId,String type,Long sourceId,String number,Timestamp created,String user,String device,String drawer,
                         String method,String reference,BigDecimal amount,String paymentStatus,String documentStatus,BigDecimal total,String note){}
}
