package services;

import com.google.gson.*;
import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/** Supplementary refresh never occupies the operational sync executor. */
final class CrossStoreRefreshCoordinator {
    static final long INTERVAL_SECONDS=300;
    private static final long LOCK=0x534D435343414348L;
    private static final AtomicBoolean STARTED=new AtomicBoolean();
    private static final java.util.concurrent.ScheduledExecutorService EXECUTOR=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"smartstock-cross-store-cache");t.setDaemon(true);return t;
    });
    private static volatile Result latest=new Result(0,0,0,0,0);
    record Result(int stores,int inventoryRows,int salesRows,int historyRows,int failures) { }

    static Result requestRefresh() {
        if (STARTED.compareAndSet(false,true)) EXECUTOR.scheduleWithFixedDelay(() -> {
            try (Connection c=DB.getConnection()) {
                DatabaseConfig config=DatabaseConfig.load();
                if (config.mode()==DatabaseMode.SERVER && config.locationId()!=null && ServerSupabaseCredentials.isConfigured()
                        && ServerRoleGuard.state()==ServerRoleGuard.State.PRIMARY) latest=refreshAll(c,config.locationId());
            } catch (Exception ex) { System.err.println("Cross-store cache refresh failed: "+safe(ex)); }
        },0,15,TimeUnit.SECONDS);
        return latest;
    }

    static Result refreshAll(Connection c,int localLocation) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Cross-store coordinator requires its own connection.");
        try (PreparedStatement p=c.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            p.setLong(1,LOCK);try(ResultSet r=p.executeQuery()){r.next();if(!r.getBoolean(1))return latest;}
        }
        int stores=0,inventory=0,sales=0,history=0,failures=0;
        try {
            for (var store:CrossStoreInventoryService.stores(c,localLocation)) {
                if (!claimDue(c,store.locationId())) continue;
                try {
                    JsonObject request=new JsonObject();request.addProperty("p_location_id",store.locationId());
                    CloudSyncManifest manifest=CloudSyncManifest.parse(rpc(c,store.locationId(),
                            "smartstock_store_snapshot_manifest",request).toString());
                    if (!Objects.equals(manifest.schemaVersion(),SchemaContractService.BASELINE_VERSION))
                        throw new SQLException("Cloud schema is incompatible with changed-row sync.");
                    CrossStoreDelta.Download download=CrossStoreDelta.download(store.locationId(),manifest,
                            baseline(c,store.locationId()),(op,body)->rpc(c,store.locationId(),op,body));
                    if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY)
                        throw new SQLException("Store server role changed during the cross-store download.");
                    Result applied=apply(c,store,manifest,download);
                    stores++;inventory+=applied.inventoryRows();sales+=applied.salesRows();history+=applied.historyRows();
                } catch (Exception ex) { failures++;markFailure(c,store,safe(ex)); }
            }
            return new Result(stores,inventory,sales,history,failures);
        } finally {
            try(PreparedStatement p=c.prepareStatement("SELECT pg_advisory_unlock(?)")){p.setLong(1,LOCK);p.execute();}
        }
    }

    static boolean claimDue(Connection c,int location) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("""
            INSERT INTO sync_cross_store_refresh(source_location_id,next_refresh_at)
            VALUES(?,CURRENT_TIMESTAMP + INTERVAL '5 minutes')
            ON CONFLICT(source_location_id) DO UPDATE SET next_refresh_at=EXCLUDED.next_refresh_at
            WHERE sync_cross_store_refresh.next_refresh_at<=CURRENT_TIMESTAMP
            RETURNING source_location_id
            """)){p.setInt(1,location);try(ResultSet r=p.executeQuery()){return r.next();}}
    }

    private static JsonObject rpc(Connection c,int location,String operation,JsonObject body) throws SQLException {
        try {
            var response=SupabaseServerApi.postRpc(operation,body);
            CloudTransferMetrics.record(c,operation,location,body.toString(),response.body());
            if(!response.successful())throw new SQLException("Changed-row cloud sync failed (HTTP "+response.statusCode()+"). Check cloud migration capability.");
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }catch(InterruptedException ex){Thread.currentThread().interrupt();throw new SQLException("Cross-store download interrupted.",ex);}
        catch(java.io.IOException|RuntimeException ex){throw new SQLException("Cross-store download unavailable.",ex);}
    }

    static Map<String,CrossStoreDelta.Baseline> baseline(Connection c,int location) throws SQLException {
        Map<String,Map<JsonElement,CrossStoreDelta.Row>> rows=new HashMap<>();
        try(PreparedStatement p=c.prepareStatement("SELECT table_name,row_key::text,row_hash,row_data::text FROM sync_cross_store_source_rows WHERE source_location_id=?")){
            p.setInt(1,location);try(ResultSet r=p.executeQuery()){while(r.next()){
                JsonElement key=JsonParser.parseString(r.getString(2));
                rows.computeIfAbsent(r.getString(1),ignored->new LinkedHashMap<>()).put(key,
                        new CrossStoreDelta.Row(key,r.getString(3),JsonParser.parseString(r.getString(4)).getAsJsonObject()));
            }}
        }
        Map<String,CrossStoreDelta.Baseline> result=new HashMap<>();
        try(PreparedStatement p=c.prepareStatement("SELECT table_name,fingerprint FROM sync_cross_store_table_state WHERE source_location_id=?")){
            p.setInt(1,location);try(ResultSet r=p.executeQuery()){while(r.next())result.put(r.getString(1),
                    new CrossStoreDelta.Baseline(r.getString(2),rows.getOrDefault(r.getString(1),Map.of())));}
        }
        return result;
    }

    static Result apply(Connection c,CrossStoreInventoryService.Store store,CloudSyncManifest manifest,
                        CrossStoreDelta.Download download) throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Cross-store application requires its own transaction.");
        c.setAutoCommit(false);
        try {
            Map<String,List<JsonObject>> source=download.bodies();
            for(String table:download.changed()) {
                try(PreparedStatement p=c.prepareStatement("DELETE FROM sync_cross_store_source_rows WHERE source_location_id=? AND table_name=?")){
                    p.setInt(1,store.locationId());p.setString(2,table);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("INSERT INTO sync_cross_store_source_rows(source_location_id,table_name,row_key,row_hash,row_data) VALUES(?,?,?::jsonb,?,?::jsonb)")){
                    for(var row:download.tables().get(table).values()){
                        p.setInt(1,store.locationId());p.setString(2,table);p.setString(3,row.key().toString());
                        p.setString(4,row.hash());p.setString(5,row.data().toString());p.addBatch();
                    }p.executeBatch();
                }
                try(PreparedStatement p=c.prepareStatement("""
                    INSERT INTO sync_cross_store_table_state(source_location_id,table_name,fingerprint,row_count) VALUES(?,?,?,?)
                    ON CONFLICT(source_location_id,table_name) DO UPDATE SET fingerprint=EXCLUDED.fingerprint,row_count=EXCLUDED.row_count
                    """)){
                    p.setInt(1,store.locationId());p.setString(2,table);p.setString(3,manifest.tables().get(table).fingerprint());
                    p.setLong(4,manifest.tables().get(table).rowCount());p.executeUpdate();
                }
            }
            int inventory=affected(download,"products","product_barcodes","inventory")
                    ? CrossStoreInventoryService.rebuild(c,store,source):count(c,"sync_cross_store_inventory_cache",store.locationId());
            int sales=affected(download,"products","sales","sale_items","sale_returns","sale_return_items")
                    ? CrossStoreSalesService.rebuild(c,store,source):count(c,"sync_cross_store_sales_cache",store.locationId());
            int history=affected(download,"sales","customer_account_transactions","custom_orders","quotations","invoices")
                    ? CrossStoreCustomerHistoryService.rebuild(c,store,source):count(c,"sync_cross_store_customer_history_cache",store.locationId());
            mark(c,store,"inventory",inventory,"CURRENT",null,manifest.completedAt());
            mark(c,store,"sales",sales,"CURRENT",null,manifest.completedAt());
            mark(c,store,"customer_history",history,"CURRENT",null,manifest.completedAt());
            for(String table:List.of("sync_cross_store_inventory_cache","sync_cross_store_sales_cache","sync_cross_store_customer_history_cache")){
                try(PreparedStatement p=c.prepareStatement("UPDATE "+table+" SET cache_refreshed_at=? WHERE source_location_id=?")){
                    p.setTimestamp(1,Timestamp.from(manifest.completedAt()));p.setInt(2,store.locationId());p.executeUpdate();
                }
            }
            try(PreparedStatement p=c.prepareStatement("""
                UPDATE sync_cross_store_refresh SET successful_refresh_at=CURRENT_TIMESTAMP,source_completed_at=?,generation_id=?::uuid,
                    next_refresh_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes',last_error=NULL WHERE source_location_id=?
                """)){
                p.setTimestamp(1,Timestamp.from(manifest.completedAt()));p.setString(2,manifest.snapshotGenerationId());
                p.setInt(3,store.locationId());p.executeUpdate();
            }
            c.commit();return new Result(1,inventory,sales,history,0);
        }catch(Exception ex){c.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("Cross-store cache application failed.",ex);}
        finally{c.setAutoCommit(true);}
    }

    private static boolean affected(CrossStoreDelta.Download d,String... tables){return Arrays.stream(tables).anyMatch(d.changed()::contains);}
    private static int count(Connection c,String table,int location)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*) FROM "+table+" WHERE source_location_id=?")){
            p.setInt(1,location);try(ResultSet r=p.executeQuery()){r.next();return r.getInt(1);}
        }
    }
    private static void mark(Connection c,CrossStoreInventoryService.Store store,String kind,int count,
                             String status,String error,Instant sourceTime)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO sync_cross_store_"+kind+"_status(source_location_id,store_name,row_count,status,last_error,refreshed_at) VALUES(?,?,?,?,?,?) ON CONFLICT(source_location_id) DO UPDATE SET store_name=EXCLUDED.store_name,row_count=EXCLUDED.row_count,status=EXCLUDED.status,last_error=EXCLUDED.last_error,refreshed_at=CASE WHEN EXCLUDED.refreshed_at='epoch'::timestamptz THEN sync_cross_store_"+kind+"_status.refreshed_at ELSE EXCLUDED.refreshed_at END")){
            p.setInt(1,store.locationId());p.setString(2,store.name());p.setInt(3,count);p.setString(4,status);p.setString(5,error);
            p.setTimestamp(6,sourceTime==null?Timestamp.from(Instant.EPOCH):Timestamp.from(sourceTime));p.executeUpdate();
        }
        if(!kind.equals("inventory"))try(PreparedStatement p=c.prepareStatement("UPDATE sync_cross_store_"+kind+"_cache SET cache_status=? WHERE source_location_id=?")){
            p.setString(1,status);p.setInt(2,store.locationId());p.executeUpdate();
        }
    }
    static void markFailure(Connection c,CrossStoreInventoryService.Store store,String error)throws SQLException{
        c.setAutoCommit(false);
        try {
        Instant source=null;
        try(PreparedStatement p=c.prepareStatement("UPDATE sync_cross_store_refresh SET last_error=? WHERE source_location_id=? RETURNING source_completed_at")){
            p.setString(1,error);p.setInt(2,store.locationId());try(ResultSet r=p.executeQuery()){if(r.next()&&r.getTimestamp(1)!=null)source=r.getTimestamp(1).toInstant();}
        }
        for(String kind:List.of("inventory","sales","customer_history"))
            mark(c,store,kind,count(c,"sync_cross_store_"+kind+"_cache",store.locationId()),"STALE",error,source);
        c.commit();
        }catch(SQLException ex){c.rollback();throw ex;}
        finally{c.setAutoCommit(true);}
    }
    private static String safe(Exception ex){String message=ex.getMessage();return message==null?ex.getClass().getSimpleName():message.substring(0,Math.min(500,message.length()));}
}
