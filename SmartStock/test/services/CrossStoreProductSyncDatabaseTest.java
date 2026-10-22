package services;

import com.google.gson.*;
import data.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** All writes target session-local temporary tables, including inbox acknowledgements. */
@EnabledIfSystemProperty(named="smartstock.test.productSync",matches="true")
class CrossStoreProductSyncDatabaseTest {
    private Connection fixture()throws Exception {
        var cfg=DatabaseConfig.load();var c=DriverManager.getConnection(cfg.jdbcUrl(),cfg.dbUser(),cfg.dbPassword());
        try(var s=c.createStatement()) {
            s.execute("CREATE TEMP TABLE categories(category_id integer PRIMARY KEY,name text NOT NULL)");
            s.execute("CREATE TEMP TABLE item_types(item_type_id integer PRIMARY KEY,category_id integer REFERENCES categories,name text)");
            s.execute("CREATE TEMP TABLE item_brands(brand_id integer PRIMARY KEY,name text)");
            s.execute("CREATE TEMP TABLE vendors(vendor_id integer PRIMARY KEY,name text)");
            s.execute("CREATE TEMP TABLE product_groups(group_id uuid PRIMARY KEY,name text,option_names jsonb)");
            s.execute("CREATE TEMP TABLE image_assets(asset_id uuid PRIMARY KEY,category text,local_status text,remote_item_id text)");
            s.execute("""
                    CREATE TEMP TABLE products(product_id integer PRIMARY KEY,name text NOT NULL,sku text UNIQUE NOT NULL,
                    barcode text,price numeric,color text,description text,category_id integer REFERENCES categories,
                    item_type_id integer REFERENCES item_types,brand_id integer REFERENCES item_brands,vendor_id integer REFERENCES vendors,
                    group_id uuid REFERENCES product_groups,image_url text,additional_image_urls jsonb NOT NULL DEFAULT '[]',
                    variant_options jsonb NOT NULL DEFAULT '{}',created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL)
                    """);
            s.execute("CREATE TEMP TABLE product_barcodes(product_barcode_id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY,product_id integer REFERENCES products,barcode text UNIQUE,created_at timestamptz,updated_at timestamptz)");
            s.execute("CREATE TEMP TABLE inventory(product_id integer,location_id integer,quantity_on_hand integer)");
            s.execute("INSERT INTO inventory VALUES(689,2,17)");
            s.execute("CREATE TEMP TABLE sync_outbox(event_id uuid DEFAULT gen_random_uuid(),event_type text,location_id integer,device_id text,user_id integer,payload jsonb,origin_location_id integer,origin_device_id text)");
            s.execute("CREATE TEMP TABLE sync_inbox(cloud_sequence bigint PRIMARY KEY,origin_location_id integer,origin_created_at timestamptz,payload jsonb,event_type text,status text DEFAULT 'RECEIVED',last_error text,applied_at timestamptz,received_at timestamptz DEFAULT '2026-10-17T14:00:00Z')");
            s.execute("INSERT INTO categories VALUES(5,'Paper')");
            s.execute("INSERT INTO products(product_id,name,sku,barcode,price,color,category_id,created_at,updated_at) VALUES(689,'Purple neon cardboard','PNC-0002','2169163773133',240,'Neon Purple',5,'2026-08-31T15:47:54Z','2026-09-14T22:53:18Z')");
            s.execute("INSERT INTO product_barcodes(product_id,barcode) VALUES(689,'OLD-EXTRA')");
            for(String table:CatalogIdentityService.KEYS.keySet())
                s.execute("ALTER TABLE "+table+" ADD COLUMN catalog_uid uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE");
            s.execute("CREATE TEMP TABLE catalog_source_ids(entity_table text,source_location_id integer,source_id integer,source_uid uuid,local_uid uuid,updated_at timestamptz DEFAULT now(),PRIMARY KEY(entity_table,source_location_id,source_id,source_uid))");
        }
        for(String table:List.of("categories","products")) {
            JsonObject row;try(var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(t)::text FROM "+table+" t")){r.next();row=JsonParser.parseString(r.getString(1)).getAsJsonObject();}
            try(var p=c.prepareStatement("UPDATE "+table+" SET catalog_uid=?")){p.setObject(1,CatalogIdentityService.legacyUid(table,row,null));p.executeUpdate();}
        }
        return c;
    }
    private Map<String,List<JsonObject>> tables() {
        var product=CrossStoreProductSyncTest.row("""
                {"product_id":"689","name":"Purple cardboard","sku":"PNC-0002","barcode":"2169163773133",
                 "price":"260.00","color":"Purple","description":"Neon cardboard","category_id":"5",
                 "additional_image_urls":"[]","variant_options":"{}",
                 "created_at":"2026-08-31 11:47:54-04","updated_at":"2026-10-17 09:54:25-04"}
                """);
        Map<String,List<JsonObject>> tables=new HashMap<>();tables.put("products",List.of(product));
        tables.put("categories",List.of(CrossStoreProductSyncTest.row("{\"category_id\":5,\"name\":\"Paper\"}")));
        tables.put("product_barcodes",List.of(CrossStoreProductSyncTest.row("{\"product_id\":689,\"barcode\":\"NEW-EXTRA\",\"created_at\":\"2026-10-17T13:54:25Z\",\"updated_at\":\"2026-10-17T13:54:25Z\"}")));
        return tables;
    }
    private void event(Connection c,long sequence,int origin,String time)throws Exception {
        event(c,sequence,origin,time,689);
    }
    private void event(Connection c,long sequence,int origin,String time,int productId)throws Exception {
        try(var p=c.prepareStatement("INSERT INTO sync_inbox(cloud_sequence,origin_location_id,origin_created_at,payload,event_type) VALUES(?,?,?::timestamptz,?::jsonb,'PRODUCT_UPDATED')")) {
            p.setLong(1,sequence);p.setInt(2,origin);p.setString(3,time);
            p.setString(4,"{\"product_id\":"+productId+",\"location_id\":"+origin+"}");p.executeUpdate();
        }
    }
    private String value(Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    @Test void applies689BacklogPreservesLocalStockAndRetriesIdempotently()throws Exception {
        try(var c=fixture()) {
            event(c,1,1,"2026-10-17T13:54:25Z");event(c,2,1,"2026-10-17T13:54:25Z");
            var source=CrossStoreProductSyncTest.source(tables());
            assertEquals(2,CrossStoreProductSyncService.applyInbox(c,2,id->source));
            assertEquals("Purple cardboard|260.00|Purple",value(c,"SELECT name||'|'||price||'|'||color FROM products WHERE product_id=689"));
            assertEquals("17",value(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=689 AND location_id=2"));
            assertEquals("NEW-EXTRA",value(c,"SELECT barcode FROM product_barcodes WHERE product_id=689"));
            assertEquals("0",value(c,"SELECT count(*) FROM sync_outbox"));
            assertEquals("2",value(c,"SELECT count(*) FROM sync_inbox WHERE status='APPLIED'"));
            String barcodeId=value(c,"SELECT product_barcode_id FROM product_barcodes");
            assertEquals(0,CrossStoreProductSyncService.applyInbox(c,2,id->{throw new AssertionError("Applied events downloaded again");}));
            event(c,3,1,"2026-10-17T13:54:25Z");CrossStoreProductSyncService.applyInbox(c,2,id->source);
            assertEquals(barcodeId,value(c,"SELECT product_barcode_id FROM product_barcodes"));
        }
    }
    @Test void keepsNewerLocalEditAndItsBarcodes()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("UPDATE products SET name='New local edit',price=280,updated_at='2026-10-17T15:00:00Z'");
            event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
            assertEquals("New local edit",value(c,"SELECT name FROM products"));
            assertEquals("OLD-EXTRA",value(c,"SELECT barcode FROM product_barcodes"));
        }
    }
    @Test void distinctIdentitiesWithTheSameSkuStillRequireReview()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("UPDATE products SET created_at='2026-09-01T15:47:54Z',catalog_uid=gen_random_uuid()");event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(0,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
            assertEquals("FAILED",value(c,"SELECT status FROM sync_inbox"));
            assertEquals("Purple neon cardboard",value(c,"SELECT name FROM products"));
        }
    }
    @Test void retriesSnapshotLagAndDownloadFailureThenApplies()throws Exception {
        try(var c=fixture()) {
            event(c,1,1,"2026-10-17T17:00:00Z");
            assertEquals(0,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
            assertEquals("FAILED",value(c,"SELECT status FROM sync_inbox"));
            try(var s=c.createStatement()){s.execute("UPDATE sync_inbox SET origin_created_at='2026-10-17T13:54:25Z'");}
            assertEquals(0,CrossStoreProductSyncService.applyInbox(c,2,id->{throw new SQLException("Offline fixture");}));
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
            assertEquals("APPLIED",value(c,"SELECT status FROM sync_inbox"));
        }
    }
    @Test void importsNewProductDependenciesAndDecodesJsonWithoutImportingStock()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("DELETE FROM product_barcodes");s.execute("DELETE FROM products");s.execute("DELETE FROM categories");
            var tables=tables();var product=tables.get("products").get(0);
            product.addProperty("item_type_id",181);product.addProperty("image_url","smartstock-asset:00000000-0000-0000-0000-000000000001");
            tables.put("item_types",List.of(CrossStoreProductSyncTest.row("{\"item_type_id\":181,\"category_id\":5,\"name\":\"Cardboard\"}")));
            tables.put("image_assets",List.of(CrossStoreProductSyncTest.row("{\"asset_id\":\"00000000-0000-0000-0000-000000000001\",\"category\":\"PRODUCT\",\"local_status\":\"PRESENT\",\"remote_item_id\":\"cloud-photo\"}")));
            event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables)));
            assertEquals("array",value(c,"SELECT jsonb_typeof(additional_image_urls) FROM products"));
            assertEquals("object",value(c,"SELECT jsonb_typeof(variant_options) FROM products"));
            assertEquals("MISSING",value(c,"SELECT local_status FROM image_assets"));
            assertEquals("17",value(c,"SELECT quantity_on_hand FROM inventory"));
            assertEquals("181",value(c,"SELECT item_type_id FROM products"));
        }
    }
    @Test void barcodeCollisionRollsBackEntireProductAndCanRetryAfterCorrection()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("INSERT INTO products(product_id,name,sku,created_at,updated_at) VALUES(678,'Other item','OTHER',now(),now())");
            s.execute("INSERT INTO product_barcodes(product_id,barcode) VALUES(678,'NEW-EXTRA')");
            event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(0,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
            assertEquals("Purple neon cardboard",value(c,"SELECT name FROM products WHERE product_id=689"));
            assertEquals("OLD-EXTRA",value(c,"SELECT barcode FROM product_barcodes WHERE product_id=689"));
            assertEquals("678",value(c,"SELECT product_id FROM product_barcodes WHERE barcode='NEW-EXTRA'"));
            s.execute("DELETE FROM product_barcodes WHERE product_id=678");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
        }
    }
    @Test void invalidSourceCannotBlockLaterValidNotification()throws Exception {
        try(var c=fixture()) {
            event(c,1,2,"2026-10-17T13:54:25Z");event(c,2,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->{assertEquals(1,id);return CrossStoreProductSyncTest.source(tables());}));
            assertEquals("FAILED",value(c,"SELECT status FROM sync_inbox WHERE cloud_sequence=1"));
            assertEquals("APPLIED",value(c,"SELECT status FROM sync_inbox WHERE cloud_sequence=2"));
        }
    }
    @Test void referenceCollisionMapsToANewIdAndPreservesExistingCategory()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("UPDATE categories SET name='Unrelated category',catalog_uid=gen_random_uuid()");event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
            assertEquals("Unrelated category",value(c,"SELECT name FROM categories WHERE category_id=5"));
            assertEquals("Paper",value(c,"SELECT name FROM categories WHERE category_id=6"));
            assertEquals("6",value(c,"SELECT category_id FROM products"));
            assertEquals("APPLIED",value(c,"SELECT status FROM sync_inbox"));
        }
    }
    @Test void waitsForProductVersionWhenOldSnapshotCompletedDuringTheEdit()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            event(c,1,1,"2026-10-17T13:54:25Z");
            s.execute("UPDATE sync_inbox SET received_at='2026-10-17T16:01:00Z'");
            var old=tables();old.get("products").get(0).addProperty("updated_at","2026-09-14T22:53:18Z");
            assertEquals(0,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(old)));
            assertEquals("FAILED",value(c,"SELECT status FROM sync_inbox"));
            assertEquals("Purple neon cardboard",value(c,"SELECT name FROM products"));
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(tables())));
        }
    }
    @Test void collidingProductsGetStableLocalIdsWithoutMovingStockOrExistingBarcodes()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("UPDATE products SET name='Existing local mug',sku='LOCAL-MUG',catalog_uid=gen_random_uuid()");
            event(c,1,1,"2026-10-17T13:54:25Z");
            var source=tables();
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(source)));
            assertEquals("Existing local mug",value(c,"SELECT name FROM products WHERE product_id=689"));
            assertEquals("Purple cardboard",value(c,"SELECT name FROM products WHERE product_id=690"));
            assertEquals("17",value(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=689"));
            assertEquals("689",value(c,"SELECT product_id FROM product_barcodes WHERE barcode='OLD-EXTRA'"));
            assertEquals("690",value(c,"SELECT product_id FROM product_barcodes WHERE barcode='NEW-EXTRA'"));
            assertEquals(690,CatalogIdentityService.localProductId(c,1,689,2));
            assertEquals(689,CatalogIdentityService.localProductId(c,2,689,2));
            assertThrows(SQLException.class,()->CatalogIdentityService.localProductId(c,99,689,2));
            source.get("products").get(0).addProperty("name","Updated imported item");
            source.get("products").get(0).addProperty("updated_at","2026-10-17T14:00:00Z");
            event(c,2,1,"2026-10-17T14:00:00Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(source)));
            assertEquals("2",value(c,"SELECT count(*) FROM products"));
            assertEquals("Updated imported item",value(c,"SELECT name FROM products WHERE product_id=690"));
        }
    }
    @Test void sendingARemappedProductBackUpdatesItsOriginalIdentityRatherThanDuplicatingIt()throws Exception {
        try(var origin=fixture();var destination=fixture();var s=destination.createStatement()) {
            s.execute("UPDATE products SET name='Unrelated local item',sku='UNRELATED',catalog_uid=gen_random_uuid()");
            event(destination,1,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(destination,2,id->CrossStoreProductSyncTest.source(tables())));
            JsonObject exported;
            try(var r=s.executeQuery("SELECT to_jsonb(p)::text FROM products p WHERE product_id=690")){assertTrue(r.next());exported=JsonParser.parseString(r.getString(1)).getAsJsonObject();}
            var back=tables();back.put("products",List.of(exported));
            event(origin,1,2,"2026-10-17T13:54:25Z",690);
            assertEquals(1,CrossStoreProductSyncService.applyInbox(origin,1,id->CrossStoreProductSyncTest.source(back)));
            assertEquals("1",value(origin,"SELECT count(*) FROM products"));
            assertEquals("Purple cardboard",value(origin,"SELECT name FROM products WHERE product_id=689"));
            assertEquals("17",value(origin,"SELECT quantity_on_hand FROM inventory WHERE product_id=689"));
        }
    }
    @Test void collidingBrandAndTypeIdsAreRewrittenOnTheImportedProduct()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("INSERT INTO item_brands(brand_id,name) VALUES(9,'Local brand')");
            s.execute("INSERT INTO item_types(item_type_id,category_id,name) VALUES(7,5,'Local type')");
            var source=tables();var product=source.get("products").get(0);
            product.addProperty("brand_id",9);product.addProperty("item_type_id",7);
            source.put("item_brands",List.of(CrossStoreProductSyncTest.row("{\"brand_id\":9,\"name\":\"Rosehall brand\"}")));
            source.put("item_types",List.of(CrossStoreProductSyncTest.row("{\"item_type_id\":7,\"category_id\":5,\"name\":\"Rosehall type\"}")));
            event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(source)));
            assertEquals("Local brand",value(c,"SELECT name FROM item_brands WHERE brand_id=9"));
            assertEquals("Local type",value(c,"SELECT name FROM item_types WHERE item_type_id=7"));
            assertEquals("10|8",value(c,"SELECT brand_id||'|'||item_type_id FROM products WHERE product_id=689"));
            event(c,2,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(source)));
            assertEquals("2",value(c,"SELECT count(*) FROM item_types"));
        }
    }
    @Test void outgoingVersionAllowsQuantityOnlyEditsWithoutImportingStock()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            SyncOutboxService.recordEvent(c,"PRODUCT_UPDATED",Map.of("product_id",689,"location_id",1),1,null,null);
            assertEquals("2026-09-14T22:53:18Z",value(c,"SELECT payload->>'catalog_updated_at' FROM sync_outbox"));
            event(c,1,1,"2026-10-17T13:54:25Z");
            s.execute("UPDATE sync_inbox SET payload=(SELECT payload FROM sync_outbox),origin_created_at='2026-10-17T17:00:00Z',received_at='2026-10-17T17:01:00Z'");
            var old=tables();old.get("products").get(0).addProperty("updated_at","2026-09-14T22:53:18Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,2,id->CrossStoreProductSyncTest.source(old)));
            assertEquals("17",value(c,"SELECT quantity_on_hand FROM inventory"));
        }
    }
    @Test void aNewStoreRetainsTheSharedIdentityAndMapsAnotherStoresNewProduct()throws Exception {
        try(var c=fixture();var s=c.createStatement()) {
            s.execute("DELETE FROM product_barcodes");s.execute("DELETE FROM products");
            var first=tables();UUID firstUid=UUID.randomUUID();
            first.get("products").get(0).addProperty("catalog_uid",firstUid.toString());
            event(c,1,1,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,3,id->CrossStoreProductSyncTest.source(first)));
            assertEquals(firstUid.toString(),value(c,"SELECT catalog_uid FROM products WHERE product_id=689"));
            var second=tables();var product=second.get("products").get(0);UUID secondUid=UUID.randomUUID();
            product.addProperty("catalog_uid",secondUid.toString());product.addProperty("sku","SECOND-STORE-NEW");
            product.addProperty("barcode","SECOND-BARCODE");second.put("product_barcodes",List.of());
            event(c,2,2,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,3,id->CrossStoreProductSyncTest.source(second)));
            assertEquals(secondUid.toString(),value(c,"SELECT catalog_uid FROM products WHERE product_id=690"));
            event(c,3,2,"2026-10-17T13:54:25Z");
            assertEquals(1,CrossStoreProductSyncService.applyInbox(c,3,id->CrossStoreProductSyncTest.source(second)));
            assertEquals("2",value(c,"SELECT count(*) FROM products"));
        }
    }
}
