package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CrossStoreProductSyncTest {
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
            public List<JsonObject> rows(String table)throws SQLException{
                if(!tables.containsKey(table))throw new SQLException("Missing fixture table: "+table);
                return tables.get(table);
            }
        };
    }
}
