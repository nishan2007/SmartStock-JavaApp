package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Never connects to a live store: all databases are disposable, on one test-only loopback port. */
class CrossStoreEgressDatabaseTest {
    static final String CLOUD="database/migrations/v1_after/20261003173203_cross_store_egress_cloud.sql";
    static final String LOCAL="database/migrations/v1_after/20261003173205_cross_store_egress_local.sql";
    @FunctionalInterface interface Work { void run(Connection c)throws Exception; }
    private void database(Work work)throws Exception {
        String url=System.getProperty("egress.test.admin","");assumeTrue(!url.isBlank());
        assertEquals("jdbc:postgresql://127.0.0.1:55447/postgres",url,"Use only the isolated egress cluster.");
        String name="egress_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection root=DriverManager.getConnection(url,"egress_test","");Statement s=root.createStatement()){
            s.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN CREATE ROLE service_role BYPASSRLS; END IF; END $$");
            s.execute("CREATE DATABASE "+name);
            try(Connection c=DriverManager.getConnection(url.replace("/postgres","/"+name),"egress_test","")){work.run(c);}
            finally{s.execute("DROP DATABASE "+name+" WITH (FORCE)");}
        }
    }
    static void execute(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement()){s.execute(sql);}}
    static String scalar(Connection c,String sql)throws SQLException{try(Statement s=c.createStatement();ResultSet r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    static void cloudEmulation(Connection c)throws SQLException{
        execute(c,"CREATE SCHEMA auth; CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql AS $$ SELECT NULL::uuid $$; CREATE SCHEMA storage; CREATE TABLE storage.buckets(id text PRIMARY KEY,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]); CREATE TABLE storage.objects(id uuid,bucket_id text,name text,owner uuid); ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;");
    }
    static void generation(Connection c,String id,boolean populated)throws SQLException{
        execute(c,"INSERT INTO smartstock_store_snapshot_generations(generation_id,location_id) VALUES('"+id+"',2)");
        if(populated) execute(c,"INSERT INTO smartstock_store_snapshot_rows(generation_id,location_id,table_name,row_key,row_hash,row_data) VALUES('"+id+"',2,'products','{\"product_id\":1}','"+CrossStoreDeltaTest.CHANGED+"','{\"product_id\":1,\"name\":\"Product 1\"}')");
        execute(c,"SELECT smartstock_finalize_store_mirror(2,'"+id+"','{\"products\":"+(populated?1:0)+",\"inventory\":0}')");
    }

    @Test void cloudFreshAndExistingMigrationFingerprintsPermissionsAndPruning()throws Exception{database(c->{
        cloudEmulation(c);SchemaContractService.installCloudBaseline(c);
        for(String resource:SchemaContractService.cloudPostV1MigrationResources()) if(!resource.equals(CLOUD)) SqlScriptRunner.runResource(c,resource);
        String old=UUID.randomUUID().toString();generation(c,old,true);
        SqlScriptRunner.runResource(c,CLOUD);SqlScriptRunner.runResource(c,CLOUD);
        SchemaContractService.refreshCloudContract(c);
        assertTrue(SchemaContractService.validateCloud(c).ready());
        String fingerprint=scalar(c,"SELECT table_fingerprints->>'products' FROM smartstock_store_snapshot_generations WHERE generation_id='"+old+"'");
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        String same=UUID.randomUUID().toString();generation(c,same,true);
        assertEquals(fingerprint,scalar(c,"SELECT table_fingerprints->>'products' FROM smartstock_store_snapshot_generations WHERE generation_id='"+same+"'"));
        String empty=UUID.randomUUID().toString();generation(c,empty,false);
        assertNotEquals(fingerprint,scalar(c,"SELECT table_fingerprints->>'products' FROM smartstock_store_snapshot_generations WHERE generation_id='"+empty+"'"));
        for(String role:List.of("anon","authenticated")){
            assertEquals("f",scalar(c,"SELECT has_function_privilege('"+role+"','smartstock_cross_store_row_hashes(integer,uuid,text,bigint,integer)','EXECUTE')"));
            assertEquals("f",scalar(c,"SELECT has_function_privilege('"+role+"','smartstock_cross_store_rows(integer,uuid,text,jsonb)','EXECUTE')"));
        }
        execute(c,"SET ROLE service_role");
        assertEquals("1",scalar(c,"SELECT jsonb_array_length(smartstock_cross_store_row_hashes(2,'"+same+"','products')->'rows')"));
        assertEquals("1",scalar(c,"SELECT jsonb_array_length(smartstock_cross_store_rows(2,'"+same+"','products','[{\"product_id\":1}]')->'rows')"));
        assertThrows(SQLException.class,()->execute(c,"SELECT smartstock_cross_store_row_hashes(1,'"+same+"','products')"));
        assertThrows(SQLException.class,()->execute(c,"SELECT smartstock_cross_store_row_hashes(2,'"+same+"','users')"));
        assertThrows(SQLException.class,()->execute(c,"SELECT smartstock_cross_store_row_hashes(2,'"+same+"','products',0,1001)"));
        assertThrows(SQLException.class,()->execute(c,"SELECT smartstock_cross_store_rows(2,'"+same+"','products',(SELECT jsonb_agg(jsonb_build_object('product_id',n)) FROM generate_series(1,1001)n))"));
        execute(c,"RESET ROLE;DELETE FROM smartstock_store_snapshot_generations WHERE generation_id='"+same+"'");
        assertThrows(SQLException.class,()->execute(c,"SELECT smartstock_cross_store_row_hashes(2,'"+same+"','products')"));
        assertEquals("0",scalar(c,"SELECT jsonb_array_length(smartstock_cross_store_row_hashes(2,'"+empty+"','products')->'rows')"));
        String large=UUID.randomUUID().toString();
        execute(c,"INSERT INTO smartstock_store_snapshot_generations(generation_id,location_id) VALUES('"+large+"',2)");
        execute(c,"INSERT INTO smartstock_store_snapshot_rows(generation_id,location_id,table_name,row_key,row_hash,row_data) SELECT '"+large+"',2,'products',jsonb_build_object('product_id',n),'"+CrossStoreDeltaTest.CHANGED+"',jsonb_build_object('product_id',n) FROM generate_series(1,1001)n");
        execute(c,"SELECT smartstock_finalize_store_mirror(2,'"+large+"','{\"products\":1001}')");
        JsonObject page=JsonParser.parseString(scalar(c,"SELECT smartstock_cross_store_row_hashes(2,'"+large+"','products')")).getAsJsonObject();
        assertEquals(1000,page.getAsJsonArray("rows").size());
        assertEquals("1",scalar(c,"SELECT jsonb_array_length(smartstock_cross_store_row_hashes(2,'"+large+"','products',"+page.get("next_cursor").getAsLong()+")->'rows')"));
    });}

    @Test void localUpgradeSchedulingAtomicApplyAndSourceFreshness()throws Exception{database(c->{
        SchemaContractService.installLocalBaseline(c);
        assertTrue(SchemaContractService.validateLocal(c).ready());
        // Emulate the immediately previous installed contract, then use its real upgrade path.
        execute(c,"DROP TABLE sync_cross_store_refresh,sync_cross_store_table_state,sync_cross_store_source_rows,sync_transfer_settings; DROP INDEX sync_transfer_metrics_created_idx; ALTER TABLE sync_transfer_metrics DROP COLUMN source_location_id;");
        List<String> previous=new ArrayList<>(SchemaContractService.localContractResources());previous.remove(LOCAL);
        try(PreparedStatement p=c.prepareStatement("UPDATE smartstock_schema_metadata SET resource_fingerprint_sha256=?,catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL'")){
            p.setString(1,SchemaContractService.resourceFingerprint(previous));p.setString(2,SchemaContractService.catalogFingerprint(c,List.of("public")));p.executeUpdate();
        }
        SchemaContractService.ensureCrossStoreEgressUpgrade(c);assertTrue(SchemaContractService.validateLocal(c).ready());
        assertTrue(CrossStoreRefreshCoordinator.claimDue(c,2));assertFalse(CrossStoreRefreshCoordinator.claimDue(c,2));
        try(Connection restarted=DriverManager.getConnection(c.getMetaData().getURL(),"egress_test","")){
            assertFalse(CrossStoreRefreshCoordinator.claimDue(restarted,2));
        }
        Map<String,List<CrossStoreDelta.Row>> source=new HashMap<>();
        source.put("products",List.of(CrossStoreDeltaTest.row(1,CrossStoreDeltaTest.CHANGED)));
        JsonObject stock=JsonParser.parseString("{\"product_id\":1,\"location_id\":2,\"quantity_on_hand\":3,\"reorder_level\":0}").getAsJsonObject();
        JsonObject key=JsonParser.parseString("{\"inventory_id\":1}").getAsJsonObject();
        source.put("inventory",List.of(new CrossStoreDelta.Row(key,CrossStoreDeltaTest.CHANGED,stock)));
        var manifest=CrossStoreDeltaTest.manifest(CrossStoreDeltaTest.GENERATION,source,Map.of("products",CrossStoreDeltaTest.CHANGED,"inventory",CrossStoreDeltaTest.CHANGED));
        var download=CrossStoreDelta.download(2,manifest,Map.of(),new CrossStoreDeltaTest.Fake(source));
        var store=new CrossStoreInventoryService.Store(2,"Remote store");
        CrossStoreRefreshCoordinator.apply(c,store,manifest,download);
        assertEquals("3",scalar(c,"SELECT quantity_on_hand FROM sync_cross_store_inventory_cache WHERE source_location_id=2"));
        assertEquals(manifest.completedAt(),Instant.parse(scalar(c,"SELECT to_char(cache_refreshed_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM sync_cross_store_inventory_cache WHERE source_location_id=2")));
        assertEquals(1,CrossStoreRefreshCoordinator.baseline(c,2).get("products").rows().size());
        // Fail late, after source and inventory writes; the entire store application must roll back.
        execute(c,"ALTER TABLE sync_cross_store_sales_status ADD CONSTRAINT deliberate_failure CHECK(source_location_id<>2) NOT VALID");
        stock.addProperty("quantity_on_hand",9);
        assertThrows(SQLException.class,()->CrossStoreRefreshCoordinator.apply(c,store,manifest,download));
        assertEquals("3",scalar(c,"SELECT quantity_on_hand FROM sync_cross_store_inventory_cache WHERE source_location_id=2"));
        assertEquals("3",scalar(c,"SELECT row_data->>'quantity_on_hand' FROM sync_cross_store_source_rows WHERE table_name='inventory' AND source_location_id=2"));
        execute(c,"ALTER TABLE sync_cross_store_sales_status DROP CONSTRAINT deliberate_failure; UPDATE sync_cross_store_refresh SET next_refresh_at=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        assertTrue(CrossStoreRefreshCoordinator.claimDue(c,2));
        CrossStoreRefreshCoordinator.markFailure(c,store,"Apply the cloud migration first.");
        assertEquals("STALE",scalar(c,"SELECT status FROM sync_cross_store_inventory_status WHERE source_location_id=2"));
        assertEquals("3",scalar(c,"SELECT quantity_on_hand FROM sync_cross_store_inventory_cache WHERE source_location_id=2"));
        assertTrue(LanSyncAdminService.crossStoreRefreshes(c).get(0).get("lastError").toString().contains("cloud migration"));
        // A first failure after upgrade preserves a pre-existing displayed source timestamp.
        execute(c,"UPDATE sync_cross_store_refresh SET source_completed_at=NULL WHERE source_location_id=2");
        CrossStoreRefreshCoordinator.markFailure(c,store,"Cloud unreachable.");
        assertEquals(manifest.completedAt(),Instant.parse(scalar(c,"SELECT to_char(refreshed_at AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') FROM sync_cross_store_inventory_status WHERE source_location_id=2")));
        // Missing capability / unreachable network must retain these caches (covered by staged downloader tests).
    });}

    @Test void metricsCountUtf8SeparateUploadsAndActualConfiguredPeriod()throws Exception{database(c->{
        SchemaContractService.installLocalBaseline(c);
        CloudTransferMetrics.record(c,"hash_listing",2,"é","response");
        assertEquals("2",scalar(c,"SELECT request_bytes FROM sync_transfer_metrics WHERE operation='hash_listing'"));
        assertEquals("2",scalar(c,"SELECT source_location_id FROM sync_transfer_metrics WHERE operation='hash_listing'"));
        var totals=CloudTransferMetrics.totals(c);assertEquals(2L,totals.get("transferTodayRequestBytes"));assertEquals(8L,totals.get("transferTodayResponseBytes"));
        assertEquals(0L,totals.get("transferBillingStartEpochMillis"));
        execute(c,"UPDATE sync_transfer_settings SET billing_period_start=CURRENT_TIMESTAMP-INTERVAL '3 days'");
        totals=CloudTransferMetrics.totals(c);assertEquals(8L,totals.get("transferBillingResponseBytes"));
        assertTrue(totals.get("transferMeasurementLabel").toString().contains("not exact Supabase billing"));
        assertThrows(LanSyncAdminService.RuleViolation.class,()->LanSyncAdminService.setBillingPeriod(c,999999,Instant.now().toEpochMilli()));
    });}
}
