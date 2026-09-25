package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Requires an explicitly supplied disposable database; never uses application credentials. */
class ProductVariantIntegrationTest {
    @Test void createsGroupsPreservesStockRejectsRacesAndRollsBackBatches() throws Exception {
        String url=System.getProperty("smartstock.variants.test.jdbc","");
        assumeTrue(!url.isBlank());
        try(Connection c=DriverManager.getConnection(url,System.getProperty("smartstock.variants.test.user","variant_test"),"")) {
            try(var ps=c.prepareStatement("SELECT to_regclass('public.products')");var rs=ps.executeQuery()) {
                rs.next();if(rs.getString(1)==null)SchemaContractService.installLocalBaseline(c);
            }
            // Reconstruct an existing pre-variant catalog and exercise the upgrade path.
            try(var statement=c.createStatement()){
                statement.execute("ALTER TABLE products DROP COLUMN group_id");
                statement.execute("ALTER TABLE products DROP COLUMN variant_options");
                statement.execute("ALTER TABLE products DROP COLUMN color");
                statement.execute("ALTER TABLE products DROP COLUMN flavor");
                statement.execute("DROP TABLE product_groups");
            }
            try(var ps=c.prepareStatement("UPDATE smartstock_schema_metadata SET catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL'")){
                ps.setString(1,SchemaContractService.catalogFingerprint(c,List.of("public")));ps.executeUpdate();
            }
            SchemaContractService.ensureProductVariantsUpgrade(c);
            SchemaContractService.ensureProductGroupBarcodesUpgrade(c);
            SchemaContractService.ensureInventoryItemColorUpgrade(c);
            SchemaContractService.ensureInventoryItemFlavorUpgrade(c);
            assertTrue(SchemaContractService.validateLocal(c).ready());
            c.setAutoCommit(false);
            try {
                int location=insert(c,"INSERT INTO locations(name,receipt_store_code) VALUES ('Variant test','VT01') RETURNING location_id");
                int user=insert(c,"INSERT INTO users(username,full_name,role_id) SELECT 'variant-test','Variant Test',role_id FROM roles WHERE UPPER(role_name)='ADMIN' RETURNING user_id");
                UUID device=UUID.randomUUID();try(var ps=c.prepareStatement("INSERT INTO devices(device_id,installation_id) VALUES (?,?)")){ps.setObject(1,device);ps.setString(2,UUID.randomUUID().toString());ps.executeUpdate();}
                int first=create(c,device,user,location,"Existing Blue","Blue-code",8);
                int second=create(c,device,user,location,"Existing Red","Red-code",5);
                verifyItemDetailEdits(c,device,user,location,first);
                String before=snapshot(c,first,second);
                JsonObject request=request(null,null,List.of(member(first,"Blue"),member(second,"Red")));
                request.addProperty("barcode"," Main-001 ");
                request.add("additionalBarcodes",new Gson().toJsonTree(List.of("Main-002","Main-003")));
                var saved=ProductVariantService.save(c,request,device,user,"Variant Test",location);
                assertEquals(before,snapshot(c,first,second));
                String group=saved.get("groupId").toString();
                assertEquals("Blue",color(c,first));
                assertEquals("Red",color(c,second));
                assertEquals("Vanilla",flavor(c,first));
                assertEquals("Chocolate",flavor(c,second));
                assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.validateColorEdit(c,first,"Green"));
                ProductVariantService.validateColorEdit(c,first,"Blue");
                assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.validateFlavorEdit(c,first,"Mint"));
                ProductVariantService.validateFlavorEdit(c,first,"Vanilla");
                // Existing variants acquire a dedicated color during migration.
                try(var ps=c.prepareStatement("UPDATE products SET color='' WHERE product_id=?")){ps.setInt(1,first);ps.executeUpdate();}
                SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20260910140000_inventory_item_color.sql"));
                assertEquals("Blue",color(c,first));
                try(var ps=c.prepareStatement("UPDATE products SET flavor='' WHERE product_id=?")){ps.setInt(1,first);ps.executeUpdate();}
                SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20260911120000_inventory_item_flavor.sql"));
                assertEquals("Vanilla",flavor(c,first));
                var blueScan=LanApiServer.resolveCatalogIdentifier(c,location,"Blue-code");
                assertEquals("Blue",((Map<?,?>)((List<?>)blueScan.get("products")).get(0)).get("color"));
                assertEquals("Vanilla",((Map<?,?>)((List<?>)blueScan.get("products")).get(0)).get("flavor"));
                for(String code:List.of("MAIN001","MAIN002","MAIN003")) {
                    var lookup=LanApiServer.resolveCatalogIdentifier(c,location,code);
                    assertEquals("VARIANT_CHOICES",lookup.get("status"));
                    assertEquals(2,((List<?>)lookup.get("products")).size());
                    assertEquals(List.of(UUID.fromString(group)),CatalogBarcodeService.matchingGroups(c,BarcodeNormalizer.lookupCandidates(code),null));
                    assertThrows(CatalogBarcodeService.ConflictException.class,()->CatalogBarcodeService.requireAvailable(c,List.of(code),null,null,null));
                }
                assertEquals(List.of(),CatalogBarcodeService.matchingGroups(c,List.of("MAIN001"),UUID.fromString(group)));
                assertEquals("MATCH",LanApiServer.resolveCatalogIdentifier(c,location,"Blue-code").get("status"));
                JsonObject duplicate=request(group,1L,List.of(member(first,"Blue"),member(second,"Red")));
                duplicate.addProperty("barcode","Blue-code");
                assertThrows(CatalogBarcodeService.ConflictException.class,()->ProductVariantService.save(c,duplicate,device,user,"Test",location));
                duplicate.addProperty("barcode","MAIN001");duplicate.add("additionalBarcodes",new Gson().toJsonTree(List.of("main-001")));
                assertThrows(CatalogBarcodeService.ConflictException.class,()->ProductVariantService.save(c,duplicate,device,user,"Test",location));
                assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.validateItemEdit(c,first,null,"SERVICE"));
                assertEquals(2,scalar(c,"SELECT count(*) FROM products WHERE group_id IS NOT NULL"));
                assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.save(c,request(null,null,List.of(member(first,"Blue"))),device,user,"Test",location));
                assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.save(c,request(group,0L,List.of(member(first,"Blue"))),device,user,"Test",location));
                assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.save(c,request(group,1L,List.of(member(first,"Blue"),member(second,"blue"))),device,user,"Test",location));
                ProductVariantService.save(c,request(group,1L,List.of(member(first,"Blue"))),device,user,"Test",location);
                assertEquals(List.of(UUID.fromString(group)),CatalogBarcodeService.matchingGroups(c,List.of("MAIN002"),null),"Older register edits must retain group barcodes");
                assertEquals(before,snapshot(c,first,second));
                assertEquals(1,scalar(c,"SELECT count(*) FROM products WHERE group_id IS NOT NULL"));
                JsonObject standalone=product("Existing Red","Red-code",5);standalone.addProperty("productId",second);standalone.addProperty("color"," Scarlet ");standalone.addProperty("flavor"," Strawberry ");
                try(var ps=c.prepareStatement("SELECT sku FROM products WHERE product_id=?")){ps.setInt(1,second);try(var rs=ps.executeQuery()){rs.next();standalone.addProperty("sku",rs.getString(1));}}
                LanProductAdminService.update(c,standalone,device,user,"Test",location);
                assertEquals("Scarlet",color(c,second));
                assertEquals("Strawberry",flavor(c,second));
                standalone.remove("color");standalone.remove("flavor");LanProductAdminService.update(c,standalone,device,user,"Test",location);
                assertEquals("Scarlet",color(c,second),"Older register updates preserve color");
                assertEquals("Strawberry",flavor(c,second),"Older register updates preserve flavor");
                try(var ps=c.prepareStatement("SELECT count(*) FROM products p WHERE "+ProductSearchHelper.predicate("p",location,"Scarlet"))) {
                    ProductSearchHelper.bindTokens(ps,1,"Scarlet");try(var rs=ps.executeQuery()){rs.next();assertEquals(1,rs.getInt(1));}
                }
                try(var ps=c.prepareStatement("SELECT count(*) FROM products p WHERE "+ProductSearchHelper.predicate("p",location,"Strawberry"))) {
                    ProductSearchHelper.bindTokens(ps,1,"Strawberry");try(var rs=ps.executeQuery()){rs.next();assertEquals(1,rs.getInt(1));}
                }
                JsonObject cell=new JsonObject();cell.addProperty("productId",second);cell.addProperty("field","COLOR");cell.addProperty("value","Crimson");
                LanProductAdminService.inlineUpdate(c,cell,device,user,"Test",location);assertEquals("Crimson",color(c,second));
                JsonObject flavorCell=new JsonObject();flavorCell.addProperty("productId",second);flavorCell.addProperty("field","FLAVOR");flavorCell.addProperty("value","Cherry");
                LanProductAdminService.inlineUpdate(c,flavorCell,device,user,"Test",location);assertEquals("Cherry",flavor(c,second));
                cell.addProperty("productId",first);
                assertThrows(LanProductAdminService.RuleViolation.class,()->LanProductAdminService.inlineUpdate(c,cell,device,user,"Test",location));
                int count=scalar(c,"SELECT count(*) FROM products");
                Savepoint checkpoint=c.setSavepoint();
                JsonObject fresh=member(null,"Green");fresh.add("product",product("Green","",3));
                JsonObject bad=member(null,"Yellow");bad.add("product",product("Yellow","Blue-code",2));
                assertThrows(Exception.class,()->ProductVariantService.save(c,request(null,null,List.of(fresh,bad)),device,user,"Test",location));
                c.rollback(checkpoint);assertEquals(count,scalar(c,"SELECT count(*) FROM products"));
                ProductVariantService.save(c,request(null,null,List.of(fresh)),device,user,"Test",location);
                assertEquals(count+1,scalar(c,"SELECT count(*) FROM products"));
                assertEquals(16,scalar(c,"SELECT sum(quantity_on_hand) FROM inventory WHERE location_id="+location));
                int denied=insert(c,"INSERT INTO users(username,full_name) VALUES ('variant-no-role','No permissions') RETURNING user_id");
                var error=assertThrows(LanProductAdminService.RuleViolation.class,()->ProductVariantService.save(c,request(group,2L,List.of(member(first,"Blue"))),device,denied,"No permissions",location));
                assertEquals(403,error.status());
                ProductVariantService.save(c,request(group,2L,List.of()),device,user,"Test",location);
                var emptyGroup=LanApiServer.resolveCatalogIdentifier(c,location,"MAIN001");
                assertEquals("VARIANT_CHOICES",emptyGroup.get("status"));
                assertTrue(((List<?>)emptyGroup.get("products")).isEmpty());
                assertEquals(before,snapshot(c,first,second));
            } finally { c.rollback(); }
        }
    }
    private static int create(Connection c,UUID device,int user,int location,String name,String barcode,int quantity)throws Exception{return ((Number)LanProductAdminService.create(c,product(name,barcode,quantity),device,user,"Variant Test",location).get("productId")).intValue();}
    private static JsonObject product(String name,String barcode,int quantity){JsonObject p=new JsonObject();p.addProperty("name",name);p.addProperty("barcode",barcode);p.addProperty("sku",name.replace(" ","-")+UUID.randomUUID());p.addProperty("price",25);p.addProperty("costPrice",10);p.addProperty("categoryId",1);p.addProperty("itemTypeName","Bottle");p.addProperty("brandName","Test brand");p.addProperty("shelfName","A1");p.addProperty("productType","INVENTORY");p.addProperty("quantity",quantity);return p;}
    private static JsonObject member(Integer id,String color){JsonObject m=new JsonObject();if(id!=null)m.addProperty("productId",id);JsonObject options=new JsonObject();options.addProperty("Color",color);options.addProperty("Flavor",color.equalsIgnoreCase("Red")?"Chocolate":"Vanilla");m.add("options",options);return m;}
    private static JsonObject request(String group,Long revision,List<JsonObject> members){JsonObject r=new JsonObject();if(group!=null)r.addProperty("groupId",group);if(revision!=null)r.addProperty("expectedRevision",revision);r.addProperty("name","Bottle");r.add("optionNames",new Gson().toJsonTree(List.of("Color","Flavor")));r.add("members",new Gson().toJsonTree(members));return r;}
    private static int insert(Connection c,String sql)throws SQLException{try(var ps=c.prepareStatement(sql);var rs=ps.executeQuery()){assertTrue(rs.next());return rs.getInt(1);}}
    private static String color(Connection c,int id)throws SQLException{try(var ps=c.prepareStatement("SELECT color FROM products WHERE product_id=?")){ps.setInt(1,id);try(var rs=ps.executeQuery()){assertTrue(rs.next());return rs.getString(1);}}}
    private static String flavor(Connection c,int id)throws SQLException{try(var ps=c.prepareStatement("SELECT flavor FROM products WHERE product_id=?")){ps.setInt(1,id);try(var rs=ps.executeQuery()){assertTrue(rs.next());return rs.getString(1);}}}
    private static void verifyItemDetailEdits(Connection c,UUID device,int user,int location,int product)throws Exception {
        editDetail(c,device,user,location,product,"ITEM_TYPE","BOTTLE","Marker");
        String originalCategory;
        try(var ps=c.prepareStatement("SELECT name FROM categories WHERE category_id=1");var rs=ps.executeQuery()){
            assertTrue(rs.next());originalCategory=rs.getString(1);
        }
        String newCategory="Variant reclass "+UUID.randomUUID();
        int newCategoryId;
        try(var ps=c.prepareStatement("INSERT INTO categories(name) VALUES (?) RETURNING category_id")){
            ps.setString(1,newCategory);try(var rs=ps.executeQuery()){assertTrue(rs.next());newCategoryId=rs.getInt(1);}
        }
        editDetail(c,device,user,location,product,"CATEGORY",originalCategory,newCategory);
        try(var ps=c.prepareStatement("SELECT cat.name,it.name,it.category_id FROM products p JOIN categories cat ON cat.category_id=p.category_id JOIN item_types it ON it.item_type_id=p.item_type_id WHERE p.product_id=?")){
            ps.setInt(1,product);try(var rs=ps.executeQuery()){
                assertTrue(rs.next());assertEquals(newCategory,rs.getString(1));assertEquals("MARKER",rs.getString(2));
                assertEquals(newCategoryId,rs.getInt(3));
            }
        }
        assertThrows(LanProductAdminService.RuleViolation.class,()->editDetail(c,device,user,location,product,"CATEGORY",originalCategory,"Missing"));
        editDetail(c,device,user,location,product,"CATEGORY",newCategory,originalCategory);
        editDetail(c,device,user,location,product,"BRAND","TEST BRAND","Sharpie");
        editDetail(c,device,user,location,product,"STORAGE_SHELF","","Back room");
        editDetail(c,device,user,location,product,"SHELF","A1","H");
        try(var ps=c.prepareStatement("SELECT it.name,ib.name,sl.name,ss.name FROM products p JOIN item_types it USING(item_type_id) JOIN item_brands ib USING(brand_id) JOIN product_shelf_assignments a USING(product_id) JOIN shelf_locations sl ON sl.shelf_location_id=a.shelf_location_id JOIN shelf_locations ss ON ss.shelf_location_id=a.storage_shelf_location_id WHERE p.product_id=? AND a.location_id=?")) {
            ps.setInt(1,product);ps.setInt(2,location);try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals("MARKER",rs.getString(1));assertEquals("SHARPIE",rs.getString(2));assertEquals("H",rs.getString(3));assertEquals("BACK ROOM",rs.getString(4));}
        }
        assertThrows(LanProductAdminService.RuleViolation.class,()->editDetail(c,device,user,location,product,"SHELF","A1","Wrong"));
        assertThrows(LanProductAdminService.RuleViolation.class,()->editDetail(c,device,user,location,product,"BRAND","SHARPIE",""));
        editDetail(c,device,user,location,product,"STORAGE_SHELF","BACK ROOM","");
        int other=insert(c,"INSERT INTO locations(name,receipt_store_code) VALUES ('Other shelf store','SH02') RETURNING location_id");
        editDetail(c,device,user,other,product,"SHELF","","OTHER");
        assertEquals(1,scalar(c,"SELECT count(*) FROM product_shelf_assignments a JOIN shelf_locations s USING(shelf_location_id) WHERE a.product_id="+product+" AND a.location_id="+location+" AND s.name='H' AND a.storage_shelf_location_id IS NULL"));
        int denied=insert(c,"INSERT INTO users(username,full_name) VALUES ('inline-no-role','No permissions') RETURNING user_id");
        var failure=assertThrows(LanProductAdminService.RuleViolation.class,()->editDetail(c,device,denied,location,product,"BRAND","SHARPIE","Denied"));
        assertEquals(403,failure.status());
    }
    private static void editDetail(Connection c,UUID device,int user,int location,int product,String field,String before,String after)throws Exception {
        JsonObject body=new JsonObject();body.addProperty("productId",product);body.addProperty("field",field);body.addProperty("expectedValue",before);body.addProperty("value",after);
        LanProductAdminService.inlineUpdate(c,body,device,user,"Test",location);
    }
    private static int scalar(Connection c,String sql)throws SQLException{return insert(c,sql);}
    private static String snapshot(Connection c,int a,int b)throws SQLException{try(var ps=c.prepareStatement("SELECT jsonb_agg(to_jsonb(t) ORDER BY product_id)::text FROM (SELECT p.product_id,p.name,p.sku,p.barcode,p.price,p.cost_price,p.image_url,i.location_id,i.quantity_on_hand,(SELECT count(*) FROM inventory_movements m WHERE m.product_id=p.product_id) movements FROM products p JOIN inventory i USING(product_id) WHERE p.product_id IN (?,?)) t")){ps.setInt(1,a);ps.setInt(2,b);try(var rs=ps.executeQuery()){rs.next();return rs.getString(1);}}}
}
