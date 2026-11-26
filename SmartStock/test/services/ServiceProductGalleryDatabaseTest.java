package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="smartstock.test.inventoryJdbc",matches="jdbc:postgresql://127\\.0\\.0\\.1:55451/postgres")
class ServiceProductGalleryDatabaseTest {
    @Test void cataloguePublishesActiveVariantsWithFixedPricesOnlyForFixedItems()throws Exception{
        try(var c=DriverManager.getConnection(System.getProperty("smartstock.test.inventoryJdbc"),"inventory_test","");var sql=c.createStatement()){
            sql.execute("CREATE TEMP TABLE custom_order_items(custom_item_id bigint,has_variants boolean,image_url text,pricing_type text)");
            sql.execute("CREATE TEMP TABLE custom_order_item_variants(custom_variant_id bigint,custom_item_id bigint,variant_name text,size text,color text,template_width numeric,template_height numeric,template_unit text,image_url text,additional_image_urls jsonb default '[]',is_active boolean,fixed_price numeric)");
            sql.execute("INSERT INTO custom_order_items VALUES(1,true,'','FIXED'),(2,true,'','VARIABLE'),(3,false,'','FIXED')");
            sql.execute("""
                INSERT INTO custom_order_item_variants(custom_variant_id,custom_item_id,variant_name,size,color,is_active,fixed_price)
                VALUES(11,1,'Plaque','11in','Pink',true,15000),(12,1,'Inactive plaque','11in','Blue',false,15500),
                    (21,2,'Other plaque','15in','Red',true,12000),(31,3,'Old variant','','',true,1000)
                """);
            var snapshot=JsonParser.parseString("""
                {sublimationProducts:[{id:1,name:'Plaque'},{id:3,name:'Single item'}],laserProducts:[{id:2,name:'Other plaque'}]}
                """).getAsJsonObject();
            ServiceProductPhotos.attach(c,snapshot);
            PlaqueCatalog.attach(c,snapshot.getAsJsonArray("sublimationProducts"));
            PlaqueCatalog.attach(c,snapshot.getAsJsonArray("laserProducts"));
            var variants=snapshot.getAsJsonArray("sublimationProducts").get(0).getAsJsonObject().getAsJsonArray("variants");
            assertEquals(1,variants.size());assertEquals(11,variants.get(0).getAsJsonObject().get("id").getAsInt());assertEquals("Pink",variants.get(0).getAsJsonObject().get("color").getAsString());
            assertTrue(snapshot.getAsJsonArray("sublimationProducts").get(1).getAsJsonObject().getAsJsonArray("variants").isEmpty());
            ServiceProductPhotos.redact(snapshot);assertFalse(snapshot.toString().contains("_photoManifests"));assertFalse(snapshot.toString().contains("_galleryReferences"));
            assertEquals(15000,variants.get(0).getAsJsonObject().get("price").getAsInt());
            var variable=snapshot.getAsJsonArray("laserProducts").get(0).getAsJsonObject().getAsJsonArray("variants").get(0).getAsJsonObject();
            assertTrue(variable.get("price").isJsonNull());
        }
    }
}
