package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TemplateSizeReviewTest {
    private JsonObject state(){return JsonParser.parseString("""
        {items:[
          {itemId:1,name:'Cup',itemType:'Cups',productType:'INVENTORY',active:true,isSublimation:true,templateWidth:8,templateUnit:'IN'},
          {itemId:2,name:'Pillow',productType:'INVENTORY',active:true,isSublimation:true,hasVariants:true,imageUrl:'parent-photo',variants:[
            {variantId:20,name:'Heart',active:true,templateWidth:12,templateHeight:12,templateUnit:'IN'},
            {variantId:21,name:'Square',active:true,templateHeight:12},
            {variantId:22,name:'Inactive',active:false}]},
          {itemId:3,name:'Inactive',productType:'INVENTORY',active:false,isSublimation:true},
          {itemId:4,name:'Archived',productType:'INVENTORY',active:true,archived:true,isSublimation:true},
          {itemId:5,name:'Shirt',productType:'INVENTORY',active:true,isShirt:true,isSublimation:true},
          {itemId:6,name:'Service',productType:'SERVICE',active:true,isSublimation:true},
          {itemId:7,name:'Laser',productType:'INVENTORY',active:true,isLaser:true},
          {itemId:8,name:'Missing units',productType:'INVENTORY',active:true,isSublimation:true,templateWidth:4,templateHeight:5}
        ]}
        """).getAsJsonObject();}
    @Test void pendingIncludesOnlyIncompleteActivePrintableTargets(){
        var entries=TemplateSizeReview.pending(state(),true);
        assertEquals(Set.of(1L,2L,8L),new HashSet<>(entries.stream().map(TemplateSizeReview.Entry::itemId).toList()));
        var variant=entries.stream().filter(e->e.variantId()!=null).findFirst().orElseThrow();
        assertEquals(21L,variant.variantId());assertEquals("parent-photo",variant.photo());
        assertEquals(4,TemplateSizeReview.pending(state(),false).size());
    }
    @Test void requestPreservesExpectedPartialDimensionsAndNullUnits(){
        var entry=TemplateSizeReview.pending(state(),true).stream().filter(e->e.itemId()==2).findFirst().orElseThrow();
        var request=TemplateSizeReview.request(entry,new BigDecimal("10"),new BigDecimal("12"),"IN");
        assertTrue(request.get("expectedWidth").isJsonNull());assertEquals(12,request.get("expectedHeight").getAsInt());assertTrue(request.get("expectedUnit").isJsonNull());
        assertEquals(21,request.get("variantId").getAsInt());
    }
    @Test void invalidMeasurementsRejectedBeforeDatabaseAccess(){
        for(String[] pair:new String[][]{{"0","4"},{"-1","4"},{"10001","4"},{"1.1234","4"}}){
            var req=JsonParser.parseString("{itemId:1,templateUnit:'IN'}").getAsJsonObject();req.addProperty("templateWidth",pair[0]);req.addProperty("templateHeight",pair[1]);
            assertThrows(IllegalArgumentException.class,()->TemplateSizeReview.save(null,req));
        }
        assertFalse(TemplateSizeReview.complete(BigDecimal.ONE,BigDecimal.ONE,""));
    }
    @Test void templateOnlyWriteChecksOwnershipAndStaleMeasurements()throws Exception{
        var entry=TemplateSizeReview.pending(state(),true).stream().filter(e->e.variantId()!=null).findFirst().orElseThrow();
        var req=TemplateSizeReview.request(entry,new BigDecimal("10"),new BigDecimal("12"),"CM");
        List<String> sql=new ArrayList<>();Map<Integer,Object> params=new HashMap<>();int[] updated={1};
        PreparedStatement ps=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
            if(m.getName().startsWith("set")){params.put((Integer)a[0],a[1]);return null;}if(m.getName().equals("executeUpdate"))return updated[0];return null;
        });
        Connection c=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{if(m.getName().equals("prepareStatement")){sql.add((String)a[0]);return ps;}return null;});
        assertEquals(21,TemplateSizeReview.save(c,req));assertEquals(2L,params.get(4));assertEquals(21L,params.get(5));
        assertTrue(sql.get(0).contains("v.custom_item_id=i.custom_item_id"));assertTrue(sql.get(0).contains("IS NOT DISTINCT FROM"));assertFalse(sql.get(0).contains("fixed_price"));
        updated[0]=0;assertThrows(IllegalArgumentException.class,()->TemplateSizeReview.save(c,req));
    }
}
