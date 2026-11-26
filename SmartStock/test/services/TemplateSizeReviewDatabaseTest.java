package services;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.math.BigDecimal;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Temporary tables on an explicitly opted-in disposable local database only. */
@EnabledIfSystemProperty(named="smartstock.test.inventoryJdbc",matches="jdbc:postgresql://127\\.0\\.0\\.1:55451/postgres")
class TemplateSizeReviewDatabaseTest {
    @Test void savesOnlyTemplateFieldsAndRejectsConcurrentOrWrongParentWrites()throws Exception{
        try(var c=DriverManager.getConnection(System.getProperty("smartstock.test.inventoryJdbc"),"inventory_test","");var s=c.createStatement()){
            s.execute("CREATE TEMP TABLE custom_order_items(custom_item_id bigint,template_width numeric,template_height numeric,template_unit text,updated_at timestamp,is_active boolean default true,is_archived boolean default false,has_variants boolean default false,is_shirt boolean default false,product_type text default 'INVENTORY',fixed_price numeric default 500)");
            s.execute("CREATE TEMP TABLE custom_order_item_variants(custom_variant_id bigint,custom_item_id bigint,template_width numeric,template_height numeric,template_unit text,is_active boolean default true,fixed_price numeric default 600)");
            s.execute("INSERT INTO custom_order_items(custom_item_id,template_unit) VALUES(1,'IN'),(2,'IN')");
            var main=new TemplateSizeReview.Entry(1,null,"Cup","Cup","Mug","11oz","",null,null,"IN",true);
            var request=TemplateSizeReview.request(main,new BigDecimal("8.5"),new BigDecimal("3.5"),"IN");
            assertEquals(1,TemplateSizeReview.save(c,request));assertThrows(IllegalArgumentException.class,()->TemplateSizeReview.save(c,request));
            try(var r=s.executeQuery("SELECT template_width,template_height,fixed_price FROM custom_order_items WHERE custom_item_id=1")){assertTrue(r.next());assertEquals(0,r.getBigDecimal(1).compareTo(new BigDecimal("8.5")));assertEquals(0,r.getBigDecimal(2).compareTo(new BigDecimal("3.5")));assertEquals(500,r.getInt(3));}
            s.execute("UPDATE custom_order_items SET has_variants=true WHERE custom_item_id=2; INSERT INTO custom_order_item_variants(custom_variant_id,custom_item_id) VALUES(20,2)");
            var variant=new TemplateSizeReview.Entry(2,20L,"Pillow","Heart","Pillow","","",null,null,null,true);
            var variantRequest=TemplateSizeReview.request(variant,new BigDecimal("12"),new BigDecimal("12"),"CM");
            var wrongParent=variantRequest.deepCopy();wrongParent.addProperty("itemId",1);assertThrows(IllegalArgumentException.class,()->TemplateSizeReview.save(c,wrongParent));
            assertEquals(20,TemplateSizeReview.save(c,variantRequest));
            try(var r=s.executeQuery("SELECT template_unit,fixed_price FROM custom_order_item_variants WHERE custom_variant_id=20")){assertTrue(r.next());assertEquals("CM",r.getString(1));assertEquals(600,r.getInt(2));}
            s.execute("UPDATE custom_order_item_variants SET is_active=false WHERE custom_variant_id=20");
            var completed=TemplateSizeReview.request(new TemplateSizeReview.Entry(2,20L,"","","","","",new BigDecimal("12"),new BigDecimal("12"),"CM",true),BigDecimal.TEN,BigDecimal.TEN,"IN");
            assertThrows(IllegalArgumentException.class,()->TemplateSizeReview.save(c,completed));
        }
    }
}
