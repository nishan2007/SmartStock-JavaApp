package services;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="smartstock.test.productMergeDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/product_merge_test")
class ProductMergeDatabaseTest {
    private static String url,replica;
    private static final UUID KEEP=UUID.fromString("11111111-1111-4111-8111-111111111111"),SOURCE=UUID.fromString("22222222-2222-4222-8222-222222222222"),DEVICE=UUID.fromString("33333333-3333-4333-8333-333333333333");
    @BeforeAll static void install()throws Exception {
        url=System.getProperty("smartstock.test.productMergeDatabaseUrl");replica=url.replace("/product_merge_test","/product_merge_replica_test");
        try(var admin=DriverManager.getConnection(url.replace("/product_merge_test","/postgres"),"postgres","");var s=admin.createStatement()) {s.execute("CREATE DATABASE product_merge_replica_test");}
        for(String target:new String[]{url,replica})try(var c=connect(target)){SchemaContractService.installLocalBaseline(c);assertTrue(SchemaContractService.validateLocal(c).ready());}
    }
    static Connection connect(String target)throws SQLException{return DriverManager.getConnection(target,"postgres","");}
    static void fixture(Connection c,int base,int location,int a,int b)throws Exception {
        c.setAutoCommit(false);try(var s=c.createStatement()){
            s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(990,'product-merge-admin','Merge Admin',1,true),(991,'inactive','Staff',1,false)");
            s.execute("INSERT INTO locations(location_id,name) VALUES(990,'Skeldon fixture'),(991,'Rosehall fixture')");
            s.execute("INSERT INTO devices(device_id,installation_id) VALUES('"+DEVICE+"','merge-fixture')");
            s.execute("INSERT INTO products(product_id,name,sku,color,price,cost_price,catalog_uid,description,image_url,additional_image_urls) VALUES("+base+",'Stamp Ink','KEEP','Red',240,20,'"+KEEP+"',E'Blue ink\\nShared line','keep.jpg','[\"keep-secondary.jpg\"]'),("+(base+1)+",'Stamp Ink','DUP','red',200,30,'"+SOURCE+"',E'shared line\\nNew details','duplicate.jpg','[]')");
            s.execute("INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES("+base+","+location+","+a+"),("+(base+1)+","+location+","+b+")");
            s.execute("UPDATE products SET barcode='KEEP-PRIMARY' WHERE product_id="+base);
            s.execute("UPDATE products SET barcode='DUP-PRIMARY' WHERE product_id="+(base+1));
            s.execute("INSERT INTO product_barcodes(product_id,barcode) VALUES("+(base+1)+",'DUP-ALIAS')");
            s.execute("INSERT INTO sales(sale_id,location_id,total_amount,amount_paid) VALUES(990,"+location+",43,43)");
            s.execute("INSERT INTO sale_items(sale_item_id,sale_id,product_id,quantity,unit_price,original_unit_price) VALUES(990,990,"+(base+1)+",1,43,43)");
            s.execute("INSERT INTO storefront.products(location_id,product_id,published) VALUES("+location+","+(base+1)+",true)");
            s.execute("INSERT INTO catalog_studio_reviews(product_id,name,decision) VALUES("+(base+1)+",'Stamp Ink','review')");
            s.execute("INSERT INTO sales_tasks(location_id,customer_name,instructions,checkout_request) VALUES("+location+",'Customer','Test','{\"lines\":[{\"productId\":"+(base+1)+",\"unitPrice\":43}]}')");
        }
    }
    @Test void imageReferencesWaitForMetadataWithoutAbortingSyncOrLosingExistingLinks()throws Exception {
        String old=UUID.randomUUID().toString(),missing=UUID.randomUUID().toString();
        String imageDb=url.replace("/product_merge_test","/product_merge_images_test");
        try(var admin=connect(url.replace("/product_merge_test","/postgres"));var s=admin.createStatement()){s.execute("CREATE DATABASE product_merge_images_test");}
        try(var c=connect(imageDb)) {
            SchemaContractService.installLocalBaseline(c);
            fixture(c,990,990,4,7);
            try(var s=c.createStatement()) {
                s.execute("INSERT INTO image_assets(asset_id,category,bucket_name,object_path) VALUES('"+old+"','PRODUCT','product-images','test/"+old+"')");
                s.execute("UPDATE products SET image_url='smartstock-asset:"+old+"',additional_image_urls='[]' WHERE product_id=990");
                ServerImageAssetService.reconcileReferences(c);
                s.execute("UPDATE products SET image_url='smartstock-asset:"+missing+"',additional_image_urls='[\"smartstock-asset:"+missing+"\",\"smartstock-asset:"+old+"\"]' WHERE product_id=990");
                ServerImageAssetService.reconcileReferences(c);
                assertEquals(old,string(s,"SELECT asset_id::text FROM image_asset_references WHERE source_table='products' AND source_key='990' AND source_column='image_url' AND active"));
                assertEquals(old,string(s,"SELECT asset_id::text FROM image_asset_references WHERE source_table='products' AND source_key='990' AND source_column='additional_image_urls:1' AND active"));
                assertEquals(0,number(s,"SELECT count(*) FROM image_assets WHERE asset_id='"+missing+"'"));
                assertEquals(0,number(s,"SELECT count(*) FROM image_asset_references WHERE asset_id='"+missing+"'"));
                s.execute("INSERT INTO image_assets(asset_id,category,bucket_name,object_path) VALUES('"+missing+"','PRODUCT','product-images','test/"+missing+"')");
                ServerImageAssetService.reconcileReferences(c);
                ServerImageAssetService.reconcileReferences(c);
                assertEquals(missing,string(s,"SELECT asset_id::text FROM image_asset_references WHERE source_table='products' AND source_key='990' AND source_column='image_url' AND active"));
                assertEquals(2,number(s,"SELECT count(*) FROM image_asset_references WHERE asset_id='"+missing+"' AND active"));
                assertEquals("smartstock-asset:"+missing,string(s,"SELECT image_url FROM products WHERE product_id=990"));
                var save=c.setSavepoint();
                assertEquals("23503",assertThrows(SQLException.class,()->s.execute("INSERT INTO image_asset_references(asset_id,source_table,source_key,source_column) VALUES('"+UUID.randomUUID()+"','products','990','invalid')")).getSQLState());
                c.rollback(save);
            } finally {c.rollback();}
        }
    }
    @Test void photoObjectWithDifferentStoreIdsUnblocksApprovedMergeWithoutChangingSharedApproval()throws Exception {
        String sourceAsset=UUID.randomUUID().toString(),localAsset=UUID.randomUUID().toString();
        try(var c=connect(url);var remote=connect(replica)) {
            fixture(c,990,990,4,7);fixture(remote,1990,991,15,1);
            try(var s=c.createStatement();var rs=remote.createStatement()) {
                s.execute("INSERT INTO image_assets(asset_id,category,bucket_name,object_path) VALUES('"+sourceAsset+"','PRODUCT','Product Images','products/same-photo.jpg')");
                rs.execute("INSERT INTO image_assets(asset_id,category,bucket_name,object_path) VALUES('"+localAsset+"','PRODUCT','Product Images','products/same-photo.jpg')");
                s.execute("UPDATE products SET image_url='smartstock-asset:"+sourceAsset+"' WHERE product_id=990");
                rs.execute("UPDATE products SET image_url='smartstock-asset:"+localAsset+"' WHERE product_id=1990");
                var review=ProductMergeService.review(c,990,990,990,991);
                ProductMergeService.merge(c,990,990,DEVICE,990,991,review.fingerprint());
                JsonObject mapping=row(s,"SELECT to_jsonb(t)::text FROM product_merges t");
                var pending=remote.setSavepoint();
                assertEquals("55000",assertThrows(SQLException.class,()->ProductMergeCoordinator.applyMapping(remote,mapping,991,null)).getSQLState());remote.rollback(pending);
                JsonObject asset=row(s,"SELECT to_jsonb(t)::text FROM image_assets t WHERE asset_id='"+sourceAsset+"'");
                String verifiedHash="a".repeat(64);
                rs.execute("UPDATE image_assets SET cloud_provider='ONEDRIVE',sha256='"+verifiedHash+"'");
                var unverified=remote.setSavepoint();
                assertEquals("55000",assertThrows(SQLException.class,()->ProductImageIdentity.existing(remote,asset)).getSQLState());remote.rollback(unverified);
                asset.addProperty("sha256",verifiedHash);
                JsonObject product=row(s,"SELECT to_jsonb(t)::text FROM products t WHERE product_id=990");
                product.addProperty("updated_at","2000-01-01T00:00:00Z");
                // Metadata must still be resolved when this store has newer item details.
                var plan=new CrossStoreProductSyncService.ProductPlan(product,java.util.List.of(),java.util.List.of(asset),java.util.List.of());
                assertFalse(CrossStoreProductSyncService.applyPlan(remote,plan,990));
                ProductMergeCoordinator.applyMapping(remote,mapping,991,null);
                ProductMergeCoordinator.applyMapping(remote,mapping,991,null);
                assertEquals(16,number(rs,"SELECT quantity_on_hand FROM inventory WHERE product_id=1990"));
                assertEquals(0,number(rs,"SELECT quantity_on_hand FROM inventory WHERE product_id=1991"));
                assertEquals("smartstock-asset:"+localAsset,string(rs,"SELECT image_url FROM products WHERE product_id=1990"));
                assertEquals(1,number(rs,"SELECT count(*) FROM image_assets"));
                assertEquals("ONEDRIVE",string(rs,"SELECT cloud_provider FROM image_assets"));
                assertEquals(mapping.getAsJsonObject("retained_fields"),row(rs,"SELECT retained_fields::text FROM product_merges"));
                JsonObject incompatible=asset.deepCopy();incompatible.addProperty("object_path","products/different-photo.jpg");incompatible.addProperty("asset_id",localAsset);
                assertEquals("55000",assertThrows(SQLException.class,()->ProductImageIdentity.existing(remote,incompatible)).getSQLState());
                JsonObject checksumConflict=asset.deepCopy();checksumConflict.addProperty("sha256","different-content");
                rs.execute("UPDATE image_assets SET sha256='original-content'");
                JsonObject conflict=checksumConflict;
                assertEquals("55000",assertThrows(SQLException.class,()->ProductImageIdentity.existing(remote,conflict)).getSQLState());
            } finally {c.rollback();remote.rollback();}
        }
    }
    @Test void signedStockSubtractsNegativeCountsAtEachStoreExactlyOnce()throws Exception {
        for(int[] counts:new int[][]{{56,-11,-3,82},{-7,-3,0,-2}})try(var c=connect(url);var remote=connect(replica)) {
            fixture(c,990,990,counts[0],counts[1]);fixture(remote,1990,991,counts[2],counts[3]);
            try(var s=c.createStatement();var rs=remote.createStatement()) {
                var review=ProductMergeService.review(c,990,990,990,991);
                assertTrue(review.blockers().isEmpty(),review.blockers().toString());
                assertTrue(review.warnings().stream().anyMatch(w->w.contains("negative quantities are subtracted")));
                ProductMergeService.merge(c,990,990,DEVICE,990,991,review.fingerprint());
                JsonObject mapping=row(s,"SELECT to_jsonb(t)::text FROM product_merges t");
                ProductMergeCoordinator.applyMapping(remote,mapping,991,null);ProductMergeCoordinator.applyMapping(remote,mapping,991,null);
                assertEquals(counts[0]+counts[1],number(s,"SELECT quantity_on_hand FROM inventory WHERE product_id=990"));
                assertEquals(counts[2]+counts[3],number(rs,"SELECT quantity_on_hand FROM inventory WHERE product_id=1990"));
                assertEquals(0,number(s,"SELECT quantity_on_hand FROM inventory WHERE product_id=991"));
                assertEquals(0,number(rs,"SELECT quantity_on_hand FROM inventory WHERE product_id=1991"));
                assertEquals(counts[1],number(s,"SELECT quantity_moved FROM product_merge_applications WHERE location_id=990"));
                assertEquals(counts[3],number(rs,"SELECT quantity_moved FROM product_merge_applications WHERE location_id=991"));
                assertEquals(43,number(s,"SELECT total_amount FROM sales WHERE sale_id=990"));
            }finally{c.rollback();remote.rollback();}
        }
    }
    @Test void signedStockUnderflowAndOverflowLeaveMergeUnapplied()throws Exception {
        for(int[] counts:new int[][]{{Integer.MIN_VALUE,-1},{Integer.MAX_VALUE,1}})try(var c=connect(url)) {
            fixture(c,990,990,counts[0],counts[1]);
            try(var s=c.createStatement()) {
                var review=ProductMergeService.review(c,990,990,990,991);assertFalse(review.blockers().isEmpty());
                assertThrows(SQLException.class,()->ProductMergeService.merge(c,990,990,DEVICE,990,991,review.fingerprint()));
                assertEquals(0,number(s,"SELECT count(*) FROM product_merges"));
                assertEquals(counts[0],number(s,"SELECT quantity_on_hand FROM inventory WHERE product_id=990"));
            }finally{c.rollback();}
        }
    }
    @Test void mergesEachStoreOnceAndPreservesMoneyPhotosAndAliases()throws Exception {
        try(var c=connect(url);var remote=connect(replica)) {
            fixture(c,990,990,4,7);fixture(remote,1990,991,15,1);
            try(var s=c.createStatement();var rs=remote.createStatement()) {
                assertEquals("42501",assertThrows(SQLException.class,()->ProductMergeService.review(c,991,990,990,991)).getSQLState());
                var review=ProductMergeService.review(c,990,990,990,991);assertTrue(review.blockers().isEmpty(),review.blockers().toString());
                assertTrue(review.references().keySet().stream().anyMatch(k->k.contains("sale_items")));
                var save=c.setSavepoint();s.execute("UPDATE inventory SET quantity_on_hand=8 WHERE product_id=991");
                assertEquals("40001",assertThrows(SQLException.class,()->ProductMergeService.merge(c,990,990,DEVICE,990,991,review.fingerprint())).getSQLState());c.rollback(save);
                var result=ProductMergeService.merge(c,990,990,DEVICE,990,991,review.fingerprint());assertEquals(7,result.get("quantityMoved"));
                assertEquals("AWAITING_STORES",result.get("state"));
                assertFalse(ProductMergeCoordinator.status(c).getAsJsonArray("merges").isEmpty());
                JsonObject mapping=row(s,"SELECT to_jsonb(t)::text FROM product_merges t");
                ProductMergeCoordinator.applyMapping(remote,mapping,991,null);ProductMergeCoordinator.applyMapping(remote,mapping,991,null);
                assertEquals(11,number(s,"SELECT quantity_on_hand FROM inventory WHERE product_id=990"));
                assertEquals(16,number(rs,"SELECT quantity_on_hand FROM inventory WHERE product_id=1990"));
                assertEquals(0,number(s,"SELECT quantity_on_hand FROM inventory WHERE product_id=991"));
                assertEquals(0,number(rs,"SELECT quantity_on_hand FROM inventory WHERE product_id=1991"));
                assertEquals(990,number(s,"SELECT product_id FROM sale_items WHERE sale_item_id=990"));
                assertEquals(1990,number(rs,"SELECT product_id FROM sale_items WHERE sale_item_id=990"));
                assertEquals(43,number(s,"SELECT unit_price FROM sale_items WHERE sale_item_id=990"));
                assertEquals(30,number(s,"SELECT merge_cost_price_snapshot FROM sale_items WHERE sale_item_id=990"));
                assertEquals(43,number(rs,"SELECT total_amount FROM sales WHERE sale_id=990"));
                assertEquals(3,number(s,"SELECT count(*) FROM product_barcodes WHERE product_id=990"));
                assertEquals(0,number(s,"SELECT count(*) FROM sync_outbox WHERE event_type='PRODUCT_UPDATED' AND payload->>'product_id'='991'"));
                assertEquals("KEEP-PRIMARY",string(s,"SELECT barcode FROM products WHERE product_id=990"));
                assertEquals("keep.jpg",string(s,"SELECT image_url FROM products WHERE product_id=990"));
                assertEquals("[\"keep-secondary.jpg\"]",string(s,"SELECT additional_image_urls::text FROM products WHERE product_id=990"));
                assertEquals("Blue ink\nShared line\nNew details",string(s,"SELECT description FROM products WHERE product_id=990"));
                assertEquals(1,number(s,"SELECT count(*) FROM catalog_studio_reviews WHERE product_id=991"));
                assertEquals(0,number(s,"SELECT count(*) FROM storefront.products WHERE product_id=991 AND published"));
                assertEquals(990,number(s,"SELECT (checkout_request->'lines'->0->>'productId')::integer FROM sales_tasks"));
                s.execute("UPDATE products SET is_active=true,barcode='STALE' WHERE product_id=991");
                assertEquals(0,number(s,"SELECT count(*) FROM products WHERE product_id=991 AND is_active"));
                save=c.setSavepoint();assertThrows(SQLException.class,()->s.execute("UPDATE inventory SET quantity_on_hand=99 WHERE product_id=991"));c.rollback(save);
                s.execute("INSERT INTO sale_items(sale_item_id,sale_id,product_id,quantity,unit_price) VALUES(991,990,991,1,19)");
                assertEquals(990,number(s,"SELECT product_id FROM sale_items WHERE sale_item_id=991"));
                s.execute("INSERT INTO product_barcodes(product_id,barcode) VALUES(991,'DELAYED-ALIAS')");
                assertEquals(990,number(s,"SELECT product_id FROM product_barcodes WHERE barcode='DELAYED-ALIAS'"));
                save=c.setSavepoint();assertThrows(SQLException.class,()->s.execute("DELETE FROM product_merges"));c.rollback(save);
                assertEquals(1,number(rs,"SELECT count(*) FROM product_merge_applications WHERE location_id=991"));
                assertTrue(SchemaContractService.validateLocal(c).ready());assertTrue(SchemaContractService.validateLocal(remote).ready());
            }finally{c.rollback();remote.rollback();}
        }
    }
    @Test void stockCommandsCheckOwnerExpectedQuantityAndReplay()throws Exception {
        try(var c=connect(url);var remote=connect(replica)) {
            fixture(c,990,990,4,7);fixture(remote,1990,991,15,1);
            try(var s=c.createStatement();var r=remote.createStatement()) {
                assertThrows(SQLException.class,()->ProductStockCommandService.request(c,991,990,991,990,15,20,DEVICE));
                ProductStockCommandService.request(c,990,990,991,990,15,20,DEVICE);
                JsonObject command=row(s,"SELECT to_jsonb(t)::text FROM product_stock_commands t");
                assertThrows(SQLException.class,()->ProductStockCommandService.apply(remote,command,991,992));
                ProductStockCommandService.apply(remote,command,991,990);ProductStockCommandService.apply(remote,command,991,990);
                assertEquals(20,number(r,"SELECT quantity_on_hand FROM inventory WHERE product_id=1990"));
                assertEquals(4,number(s,"SELECT quantity_on_hand FROM inventory WHERE product_id=990"));
                assertEquals(1,number(r,"SELECT count(*) FROM inventory_movements"));
                ProductStockCommandService.request(c,990,990,991,990,15,30,DEVICE);
                JsonObject stale=row(s,"SELECT to_jsonb(t)::text FROM product_stock_commands t WHERE new_quantity=30");
                ProductStockCommandService.apply(remote,stale,991,990);
                assertEquals(20,number(r,"SELECT quantity_on_hand FROM inventory WHERE product_id=1990"));
                assertEquals(1,number(r,"SELECT count(*) FROM product_stock_command_results WHERE state='CONFLICT'"));
            }finally{c.rollback();remote.rollback();}
        }
    }
    @Test void upgradesThePreviousContractAndRejectsSchemaDrift()throws Exception {
        String upgrade=url.replace("/product_merge_test","/product_merge_upgrade_test");
        try(var admin=connect(url.replace("/product_merge_test","/postgres"));var s=admin.createStatement()){s.execute("CREATE DATABASE product_merge_upgrade_test");}
        try(var c=connect(upgrade);var s=c.createStatement()) {
            SchemaContractService.installLocalBaseline(c);
            for(String function:new String[]{"canonical_product_uid(uuid)","canonical_product_id(integer)","product_merge_reference_columns()","redirect_merged_product_reference()","guard_merged_product_stock()","keep_merged_product_retired()","canonical_product_lines(jsonb,text)","redirect_merged_sales_task()","redirect_merged_stock_ledger()","install_product_merge_reference_guards()","preserve_product_merge_mapping()"})
                s.execute("DROP FUNCTION IF EXISTS public."+function+" CASCADE");
            s.execute("DROP TABLE product_merges,product_merge_applications,product_stock_commands,product_stock_command_results CASCADE");
            s.execute("ALTER TABLE sale_items DROP COLUMN merge_cost_price_snapshot");
            String oldCatalog=SchemaContractService.catalogFingerprint(c,java.util.List.of("public"));
            try(var p=c.prepareStatement("UPDATE smartstock_schema_metadata SET catalog_fingerprint_sha256=?,resource_fingerprint_sha256='7a8e2e5e30bc69db01e59c6748cdbc2735bf326cf3f0bbc7f0025f8b2e9a18dd' WHERE schema_scope='LOCAL'")){p.setString(1,oldCatalog);p.executeUpdate();}
            s.execute("CREATE TABLE product_merge_unexpected_drift(id integer)");
            assertEquals("55000",assertThrows(SQLException.class,()->SchemaContractService.ensureProductMergesUpgrade(c)).getSQLState());
            s.execute("DROP TABLE product_merge_unexpected_drift");
            SchemaContractService.ensureProductMergesUpgrade(c);SchemaContractService.ensureProductMergesUpgrade(c);
            assertTrue(SchemaContractService.validateLocal(c).ready());
        }
    }
    static int number(Statement s,String q)throws SQLException{try(var r=s.executeQuery(q)){r.next();return r.getInt(1);}}
    static String string(Statement s,String q)throws SQLException{try(var r=s.executeQuery(q)){r.next();return r.getString(1);}}
    static JsonObject row(Statement s,String q)throws SQLException{return JsonParser.parseString(string(s,q)).getAsJsonObject();}
}
