package services;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomOrderItemSearchIntegrationTest {
    @Test void returnsRankedDistinctActiveItemsAndVariantsForTypingAndBarcodes() throws Exception {
        String url=System.getProperty("smartstock.test.jdbc", "");
        String user=System.getProperty("smartstock.test.dbUser", "");
        assumeTrue(!url.isBlank() && !user.isBlank());
        try(var c=DriverManager.getConnection(url,user,System.getProperty("smartstock.test.dbPassword", ""))){
            c.setAutoCommit(false);
            try(var s=c.createStatement()){
                s.execute("CREATE TEMP TABLE custom_order_items(custom_item_id bigint,item_name text,size text,color text,sku text,barcode text,description text,product_type text,category_id bigint,item_type_id bigint,brand_id bigint,is_active boolean) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE custom_order_item_variants(custom_item_id bigint,custom_variant_id bigint,variant_name text,size text,color text,sku text,barcode text,brand_id bigint,is_active boolean) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE custom_order_item_barcodes(custom_item_id bigint,barcode text) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE custom_order_item_variant_barcodes(custom_variant_id bigint,barcode text) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE categories(category_id bigint,name text) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE item_types(item_type_id bigint,name text) ON COMMIT DROP");
                s.execute("CREATE TEMP TABLE item_brands(brand_id bigint,name text) ON COMMIT DROP");
                s.execute("INSERT INTO custom_order_items(custom_item_id,item_name,sku,is_active) VALUES (1,'Shirt','SHIRT',TRUE),(2,'Cap','CAP',TRUE),(3,'Hidden Shirt','HIDDEN',FALSE)");
                s.execute("INSERT INTO custom_order_item_variants(custom_item_id,custom_variant_id,variant_name,size,color,sku,barcode,is_active) VALUES (1,11,'Red shirt','Small','Red','RED-S','123456789',TRUE),(1,12,'Blue shirt','Large','Blue','BLUE-L','987654321',TRUE),(1,13,'Hidden variant','Small','Green','HIDDEN-V',NULL,FALSE)");
                s.execute("INSERT INTO custom_order_item_variant_barcodes VALUES (11,'123456789'),(11,'123456789')");
                var matches=ServerCustomOrderDataService.searchCustomItems(c,"shirt");
                assertEquals(3,matches.size());
                assertEquals(3,matches.stream().map(x->x.customItemId()+":"+x.customVariantId()).distinct().count());
                var blue=ServerCustomOrderDataService.searchCustomItems(c,"large blue shirt");
                assertEquals(1,blue.size());
                assertEquals(12L,blue.get(0).customVariantId());
                assertTrue(blue.get(0).label().contains("Blue"));
                assertTrue(blue.get(0).label().contains("Large"));
                assertTrue(blue.get(0).label().contains("BLUE-L"));
                var barcode=ServerCustomOrderDataService.searchCustomItems(c,"123456789");
                assertEquals(1,barcode.size());
                assertEquals(11L,barcode.get(0).customVariantId());
                assertEquals(11L,ServerCustomOrderDataService.lookupCustomItem(c,"123456789").customVariantId());
                assertTrue(ServerCustomOrderDataService.searchCustomItems(c,"HIDDEN").isEmpty());
                assertTrue(ServerCustomOrderDataService.searchCustomItems(c,"no such item").isEmpty());
                assertTrue(ServerCustomOrderDataService.searchCustomItems(c," ").isEmpty());
                s.execute("INSERT INTO custom_order_items(custom_item_id,item_name,is_active) SELECT n,'Shirt '||n,TRUE FROM generate_series(100,140) n");
                assertEquals(20,ServerCustomOrderDataService.searchCustomItems(c,"shirt").size());
            }finally{c.rollback();}
        }
    }
}
