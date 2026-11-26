package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in disposable database. Never opens the configured store connection. */
@EnabledIfSystemProperty(named="smartstock.test.inventoryJdbc",matches="jdbc:postgresql://127\\.0\\.0\\.1:55451/postgres")
class InventoryWebDatabaseTest {
    private Connection c;
    @BeforeEach void prepare()throws Exception {
        c=DriverManager.getConnection(System.getProperty("smartstock.test.inventoryJdbc"),"inventory_test","");c.setAutoCommit(true);
        try(var s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS storefront CASCADE; DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public");
            SchemaContractService.installLocalBaseline(c);
            c.setAutoCommit(false);
            s.execute("INSERT INTO locations(location_id,name,receipt_store_code) VALUES(994,'Inventory test','0994'),(995,'Other store','0995')");
            s.execute("INSERT INTO roles(role_id,role_name) VALUES(994,'Inventory web test'),(995,'No inventory')");
            s.execute("INSERT INTO role_permissions(role_id,permission_id) SELECT 994,permission_id FROM permissions WHERE permission_key='VIEW_INVENTORY'");
            s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(994,'inventory-web-test','Inventory Tester',994,true),(995,'inventory-denied','Denied Tester',995,true)");
            s.execute("INSERT INTO user_locations(user_id,location_id) VALUES(994,994),(995,994)");
            s.execute("INSERT INTO products(product_id,name,sku,barcode,price,is_active) VALUES(994,'Cotton shirt','SHIRT','00123',2500,true),(995,'Archived shirt','OLD','00124',0,false)");
            s.execute("INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) VALUES(994,994,12,3),(994,995,99,3)");
            s.execute("INSERT INTO product_barcodes(product_id,barcode) VALUES(994,'009876')");
            s.execute("INSERT INTO custom_order_items(custom_item_id,item_name,sku,barcode,pricing_type,fixed_price,quantity_on_hand,reorder_level,has_variants) VALUES(994,'Printed mug','MUG','00234','FIXED',1800,2,4,false),(995,'Custom polo','POLO','00345','FIXED',3000,0,1,true)");
            s.execute("INSERT INTO custom_order_item_variants(custom_variant_id,custom_item_id,variant_name,sku,barcode,quantity_on_hand) VALUES(994,995,'Navy','POLO-N','00456',0)");
            s.execute("INSERT INTO custom_item_inventory(custom_item_id,location_id,quantity_on_hand,reorder_level) VALUES(994,994,2,4)");
            s.execute("INSERT INTO custom_variant_inventory(custom_variant_id,location_id,quantity_on_hand,reorder_level) VALUES(994,994,0,1)");
            s.execute("INSERT INTO custom_order_item_variant_barcodes(custom_variant_id,barcode) VALUES(994,'000111')");
        }
    }
    @AfterEach void cleanup()throws Exception{if(c!=null){c.rollback();c.close();}}
    private Map<String,Object> page(int user,int store,String request)throws Exception{return InventoryWebService.page(c,user,store,JsonParser.parseString(request).getAsJsonObject());}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> items(String request)throws Exception{return (List<Map<String,Object>>)page(994,994,request).get("items");}
    @Test void unifiedCatalogueExcludesArchivedAndVariantParentAndScopesStock()throws Exception {
        var rows=items("{}");assertEquals(3,rows.size());
        assertEquals(12,((Number)rows.stream().filter(r->r.get("kind").equals("product")).findFirst().orElseThrow().get("quantity")).intValue());
        assertEquals(1,items("{stock:'low'}").size());assertEquals(1,items("{stock:'out'}").size());assertEquals(2,items("{kind:'custom'}").size());
    }
    @Test void additionalBarcodesAndMultiTokenNamesMatch()throws Exception {
        for(String query:List.of("009876","000111","cotton shirt","polo navy","00234"))
            assertEquals(1,items("{query:'"+query+"'}").size(),query);
        assertTrue(items("{query:'no match'}").isEmpty());
    }
    @Test void variantItemTypeAlwaysComesFromItsMainItem()throws Exception {
        try(var statement=c.createStatement()){
            statement.execute("INSERT INTO categories(category_id,name) VALUES(994,'Custom inventory test')");
            statement.execute("INSERT INTO item_types(item_type_id,category_id,name) VALUES(994,994,'Plaques'),(995,994,'Awards')");
            statement.execute("UPDATE custom_order_items SET category_id=994,item_type_id=994 WHERE custom_item_id=995");
        }
        assertEquals("Plaques",items("{kind:'custom'}").stream().filter(row->"variant".equals(row.get("kind"))).findFirst().orElseThrow().get("item_type_name"));
        try(var statement=c.createStatement()){statement.execute("UPDATE custom_order_items SET item_type_id=995 WHERE custom_item_id=995");}
        assertEquals("Awards",items("{kind:'custom'}").stream().filter(row->"variant".equals(row.get("kind"))).findFirst().orElseThrow().get("item_type_name"));
    }
    @Test void deniesWrongRoleStoreAndInvalidFilters() {
        assertThrows(SecurityException.class,()->page(995,994,"{}"));
        assertThrows(SecurityException.class,()->page(994,995,"{}"));
        assertThrows(IllegalArgumentException.class,()->items("{offset:-1}"));
        assertThrows(IllegalArgumentException.class,()->items("{kind:'all stores'}"));
    }
}
