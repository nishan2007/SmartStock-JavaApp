package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrossStoreDeltaTest {
    static final String GENERATION="00000000-0000-0000-0000-000000000001";
    static final String EMPTY="0".repeat(64),CHANGED="1".repeat(64),NEXT="2".repeat(64);

    static CrossStoreDelta.Row row(int id,String hash) {
        JsonObject key=new JsonObject();key.addProperty("product_id",id);
        JsonObject data=new JsonObject();data.addProperty("product_id",id);data.addProperty("name","Product "+id);
        return new CrossStoreDelta.Row(key,hash,data);
    }
    static CloudSyncManifest manifest(String generation,Map<String,List<CrossStoreDelta.Row>> source,Map<String,String> fingerprints) {
        Map<String,CloudSyncManifest.TableInfo> tables=new LinkedHashMap<>();
        CrossStoreDelta.TABLES.forEach(t->tables.put(t,new CloudSyncManifest.TableInfo(source.getOrDefault(t,List.of()).size(),Set.of(),fingerprints.getOrDefault(t,EMPTY))));
        return new CloudSyncManifest(tables,generation,Instant.parse("2026-10-01T00:00:00Z"),1,true,1);
    }
    static Map<String,CrossStoreDelta.Baseline> baseline(CrossStoreDelta.Download download,CloudSyncManifest manifest) {
        Map<String,CrossStoreDelta.Baseline> result=new HashMap<>();
        download.tables().forEach((t,rows)->result.put(t,new CrossStoreDelta.Baseline(manifest.tables().get(t).fingerprint(),rows)));
        return result;
    }
    static final class Fake implements CrossStoreDelta.Rpc {
        final Map<String,List<CrossStoreDelta.Row>> source;
        int hashCalls,bodyCalls,bodies;
        boolean failBodies,wrongHash;
        Fake(Map<String,List<CrossStoreDelta.Row>> source){this.source=source;}
        public JsonObject call(String operation,JsonObject request)throws SQLException {
            String table=request.get("p_table_name").getAsString();
            JsonObject page=new JsonObject();page.add("generation_id",request.get("p_generation_id"));
            JsonArray rows=new JsonArray();page.add("rows",rows);
            List<CrossStoreDelta.Row> all=source.getOrDefault(table,List.of());
            if(operation.equals("smartstock_cross_store_row_hashes")){
                hashCalls++;long after=request.get("p_after_sequence").getAsLong(),next=after;
                for(int i=(int)after;i<Math.min(all.size(),after+1000);i++){
                    var row=all.get(i);JsonObject envelope=new JsonObject();envelope.add("row_key",row.key());
                    envelope.addProperty("row_hash",row.hash());envelope.addProperty("sequence",i+1);rows.add(envelope);next=i+1;
                }page.addProperty("next_cursor",next);
            }else{
                bodyCalls++;if(failBodies)throw new SQLException("Snapshot was pruned");
                JsonArray keys=request.getAsJsonArray("p_row_keys");assertTrue(keys.size()<=1000);
                for(var row:all)if(keys.contains(row.key())){
                    JsonObject envelope=new JsonObject();envelope.add("row_key",row.key());
                    envelope.addProperty("row_hash",wrongHash?NEXT:row.hash());envelope.add("row_data",row.data());rows.add(envelope);bodies++;
                }
            }return page;
        }
    }

    @Test void unchangedTablesAndUnrelatedNewGenerationDownloadNoBodiesOrHashes()throws Exception {
        Map<String,List<CrossStoreDelta.Row>> source=Map.of("products",List.of(row(1,CHANGED)));
        var first=manifest(GENERATION,source,Map.of("products",CHANGED));var initial=new Fake(source);
        var download=CrossStoreDelta.download(2,first,Map.of(),initial);
        var next=manifest(UUID.randomUUID().toString(),source,Map.of("products",CHANGED));var rpc=new Fake(source);
        var result=CrossStoreDelta.download(2,next,baseline(download,first),rpc);
        assertTrue(result.changed().isEmpty());assertEquals(0,rpc.bodyCalls);assertEquals(0,rpc.hashCalls);
        assertEquals(1,result.bodies().get("products").size());
    }
    @Test void editInsertAndDeletionReuseUnchangedBodies()throws Exception {
        var oldSource=Map.of("products",List.of(row(1,CHANGED),row(2,CHANGED),row(3,CHANGED)));
        var oldManifest=manifest(GENERATION,oldSource,Map.of("products",CHANGED));
        var old=CrossStoreDelta.download(2,oldManifest,Map.of(),new Fake(oldSource));
        var newSource=Map.of("products",List.of(row(1,NEXT),row(2,CHANGED),row(4,CHANGED)));
        var rpc=new Fake(newSource);var result=CrossStoreDelta.download(2,
                manifest(GENERATION,newSource,Map.of("products",NEXT)),baseline(old,oldManifest),rpc);
        assertEquals(2,rpc.bodies);assertEquals(1,rpc.bodyCalls);
        assertEquals(Set.of(row(1,NEXT).key(),row(2,CHANGED).key(),row(4,CHANGED).key()),result.tables().get("products").keySet());
        assertEquals(CHANGED,result.tables().get("products").get(row(2,CHANGED).key()).hash());
    }
    @Test void oneEditDownloadsExactlyOneBodyAndProductsAreShared()throws Exception {
        var source=Map.of("products",List.of(row(1,CHANGED),row(2,CHANGED)));
        var m=manifest(GENERATION,source,Map.of("products",CHANGED));var old=CrossStoreDelta.download(2,m,Map.of(),new Fake(source));
        var next=Map.of("products",List.of(row(1,NEXT),row(2,CHANGED)));var rpc=new Fake(next);
        var result=CrossStoreDelta.download(2,manifest(GENERATION,next,Map.of("products",NEXT)),baseline(old,m),rpc);
        assertEquals(1,rpc.bodies);assertEquals(Set.of("products"),result.changed());
        // One source table supplies every derived-cache consumer.
        assertEquals(2,result.bodies().get("products").size());
    }
    @Test void emptyTableDeletesAllKeysWithoutBodyRequest()throws Exception {
        var source=Map.of("products",List.of(row(1,CHANGED)));var m=manifest(GENERATION,source,Map.of("products",CHANGED));
        var old=CrossStoreDelta.download(2,m,Map.of(),new Fake(source));var rpc=new Fake(Map.of());
        var next=CrossStoreDelta.download(2,manifest(GENERATION,Map.of(),Map.of()),baseline(old,m),rpc);
        assertTrue(next.tables().get("products").isEmpty());assertEquals(0,rpc.bodies);
    }
    @Test void pagesAtExactBoundaryAndOverBoundaryAreComplete()throws Exception {
        for(int count:List.of(1000,2001)){
            List<CrossStoreDelta.Row> rows=new ArrayList<>();for(int i=0;i<count;i++)rows.add(row(i,CHANGED));
            var source=Map.of("products",rows);var rpc=new Fake(source);
            var result=CrossStoreDelta.download(2,manifest(GENERATION,source,Map.of("products",CHANGED)),Map.of(),rpc);
            assertEquals(count,result.tables().get("products").size());assertEquals(count,rpc.bodies);
            assertEquals((count+999)/1000,rpc.bodyCalls);
        }
    }
    @Test void unavailableGenerationAndMismatchedBodiesDoNotModifyBaseline()throws Exception {
        var source=Map.of("products",List.of(row(1,CHANGED)));var m=manifest(GENERATION,source,Map.of("products",CHANGED));
        var original=Map.<String,CrossStoreDelta.Baseline>of();var rpc=new Fake(source);rpc.failBodies=true;
        assertThrows(SQLException.class,()->CrossStoreDelta.download(2,m,original,rpc));assertTrue(original.isEmpty());
        rpc.failBodies=false;rpc.wrongHash=true;
        assertThrows(SQLException.class,()->CrossStoreDelta.download(2,m,original,rpc));assertTrue(original.isEmpty());
    }
    @Test void missingCapabilityFailsClosedWithoutFullSnapshotFallback() {
        var m=new CloudSyncManifest(Map.of(),GENERATION,Instant.now(),1,true);var rpc=new Fake(Map.of());
        assertThrows(SQLException.class,()->CrossStoreDelta.download(2,m,Map.of(),rpc));assertEquals(0,rpc.bodyCalls);
    }
}
