package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import static org.junit.jupiter.api.Assertions.*;

class CatalogStudioTargetTest {
    @Test void routesOverlappingOwnerIdsToSeparateTablesAndAssetCategories() {
        var product=CatalogStudioTarget.from(29);
        var custom=CatalogStudioTarget.from(-29);
        var variant=CatalogStudioTarget.from(-1_000_000_029L);
        assertEquals(29,product.id());
        assertEquals(29,custom.id());
        assertEquals(29,variant.id());
        assertEquals("products",product.table());
        assertEquals("CUSTOM_ITEM",custom.category());
        assertEquals("CUSTOM_VARIANT",variant.category());
        assertNotEquals(custom.folder(),variant.folder());
        assertTrue(custom.photoSql(true).contains("is_archived=FALSE FOR UPDATE"));
        assertTrue(variant.photoSql(true).contains("parent.is_archived=FALSE"));
        assertTrue(variant.photoSql(true).endsWith("FOR UPDATE"));
    }

    @Test void rejectsUnassignedAndOverflowingSignedIds() {
        for(long id:new long[]{0,Long.MIN_VALUE,-1_000_000_000L})
            assertThrows(IllegalArgumentException.class,()->CatalogStudioTarget.from(id));
    }

    @Test void studioParserAcceptsSignedCatalogIdsButRejectsFractionalAndOverflowValues() throws Exception {
        var parser=LanApiServer.class.getDeclaredMethod("requiredStudioId",JsonObject.class);
        parser.setAccessible(true);
        for(long id:new long[]{29,-29,-1_000_000_029L}) {
            JsonObject body=new JsonObject();body.addProperty("productId",id);
            assertEquals(id,parser.invoke(null,body));
        }
        for(String value:new String[]{"0","-1000000000","1.5","9223372036854775808","nonsense"}) {
            JsonObject body=new JsonObject();body.addProperty("productId",value);
            assertThrows(InvocationTargetException.class,()->parser.invoke(null,body));
        }
    }
}
