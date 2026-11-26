package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrossStoreProductSyncTest {
    @Test void carriesCompleteTagAssignmentsAndLeavesOlderSnapshotsCompatible()throws Exception {
        var product=row("{product_id:7,sku:'POLO'}");
        var tag=row("{tag_id:'a89a32bd-7c4a-4099-a486-b2fed9d6043d',name:'School Wear',is_public:true}");
        var source=source(Map.of("products",List.of(product),"product_barcodes",List.of(),
            "product_tag_definitions",List.of(tag),"product_tags",List.of(row("{product_id:7,tag_id:'a89a32bd-7c4a-4099-a486-b2fed9d6043d'}"))));
        var plan=CrossStoreProductSyncService.plan(source,7);
        assertEquals(tag,plan.product().getAsJsonArray("_tagSnapshot").get(0));
        assertFalse(CrossStoreProductSyncService.plan(source(Map.of("products",List.of(product),"product_barcodes",List.of())),7).product().has("_tagSnapshot"));
    }
    static JsonObject row(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    @Test void comparesMirrorAndNativeTimestampsWithoutUsingMachineTimezone() throws Exception {
        assertEquals(CrossStoreProductSyncService.timestamp(row("{\"updated_at\":\"2026-10-17 09:54:25.277137-04\"}"),"updated_at"),
                CrossStoreProductSyncService.timestamp(row("{\"updated_at\":\"2026-10-17T13:54:25.277137Z\"}"),"updated_at"));
        assertThrows(SQLException.class,()->CrossStoreProductSyncService.timestamp(row("{}"),"updated_at"));
    }
    @Test void rejectsIndependentProductIdCollisionsButAllowsSkuAndBarcodeEdits() throws Exception {
        JsonObject a=row("{\"created_at\":\"2026-08-31T15:47:54Z\",\"sku\":\"old\"}");
        JsonObject b=a.deepCopy();b.addProperty("sku","new");
        assertTrue(CrossStoreProductSyncService.sameIdentity(a,b));
        b.addProperty("created_at","2026-09-01T15:47:54Z");
        assertFalse(CrossStoreProductSyncService.sameIdentity(a,b));
    }
    @Test void rejectsMissingProductAndLeavesSystemItemLocal() throws Exception {
        var source=source(Map.of("products",List.of(row("{\"product_id\":1,\"sku\":\"SMARTSTOCK-MISC\"}"))));
        assertThrows(SQLException.class,()->CrossStoreProductSyncService.plan(source,689));
        var plan=CrossStoreProductSyncService.plan(source,1);
        assertTrue(plan.dependencies().isEmpty());
        assertFalse(CrossStoreProductSyncService.applyPlan(null,plan,1));
    }
    static CrossStoreProductSyncService.CatalogSource source(Map<String,List<JsonObject>> tables) {
        return new CrossStoreProductSyncService.CatalogSource() {
            public Instant completedAt(){return Instant.parse("2026-10-17T16:00:00Z");}
            public boolean hasTags(){return tables.containsKey("product_tags")&&tables.containsKey("product_tag_definitions");}
            public List<JsonObject> rows(String table)throws SQLException{
                if(!tables.containsKey(table))throw new SQLException("Missing fixture table: "+table);
                return tables.get(table);
            }
        };
    }
}
