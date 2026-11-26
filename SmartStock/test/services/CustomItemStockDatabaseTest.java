package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.*;
import java.time.ZoneId;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in integration gate, restricted to a disposable loopback PostgreSQL cluster. */
class CustomItemStockDatabaseTest {
    @Test void separatesStoresAcrossCatalogReceivingOrdersAndRecovery() throws Exception {
        String admin=System.getProperty("custom.stock.test.admin", "");
        assumeTrue(!admin.isEmpty());
        assertEquals("jdbc:postgresql://127.0.0.1:55439/postgres",admin);
        String database="custom_stock_"+UUID.randomUUID().toString().replace("-", "");
        try(var root=DriverManager.getConnection(admin,"custom_stock_test","")) {
            sql(root,"CREATE DATABASE "+database);
            try(var c=DriverManager.getConnection(admin.replace("/postgres","/"+database),"custom_stock_test","")) {
                SchemaContractService.installLocalBaseline(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                sql(c,"""
                    INSERT INTO locations(location_id,name) VALUES(910,'Store A'),(911,'Store B');
                    INSERT INTO roles(role_id,role_name) VALUES(910,'Custom stock test');
                    INSERT INTO role_permissions(role_id,permission_id) SELECT 910,permission_id FROM permissions;
                    INSERT INTO users(user_id,username,full_name,role_id) VALUES(910,'custom_stock_test','Stock test',910);
                    INSERT INTO user_locations(user_id,location_id) VALUES(910,910),(910,911);
                    INSERT INTO products(product_id,name,sku,price) VALUES(910,'Regular bottle','STOCK-REGULAR',100);
                    INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES(910,910,10),(910,911,5);
                    INSERT INTO custom_order_items(custom_item_id,item_name,pricing_type,fixed_price,quantity_on_hand,reorder_level,sell_in_pos)
                      VALUES(910,'Stock Bottle','FIXED',100,12,3,true),(911,'Stock Shirt','FIXED',100,0,0,true);
                    UPDATE custom_order_items SET has_variants=true WHERE custom_item_id=911;
                    INSERT INTO custom_order_item_variants(custom_variant_id,custom_item_id,variant_name,fixed_price,quantity_on_hand,reorder_level)
                      VALUES(910,911,'Blue',100,7,2),(911,911,'Red',100,4,1);
                    """);
                c.setAutoCommit(false);
                CustomItemStockService.migrateLegacy(c,910);
                c.commit();
                stock(c,910,null,910,12); stock(c,910,null,911,0);
                stock(c,911,910L,910,7); stock(c,911,null,910,11);
                stock(c,911,910L,911,0);
                CustomItemStockService.migrateLegacy(c,911); // A second login cannot clone the legacy balance.
                stock(c,910,null,911,0);
                CustomItemStockService.set(c,910,null,911,n(20),n(5));
                CustomItemStockService.set(c,911,910L,911,n(30),n(4));
                c.commit();

                var product=LanInventoryService.lockStock(c,"PRODUCT",910,911);
                assertEquals(5,product.currentStock());
                LanInventoryService.applyStock(c,product,2,911);
                c.commit();
                assertEquals(7,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=910 AND location_id=911"));
                assertEquals(10,number(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=910 AND location_id=910"));
                stock(c,910,null,911,20);

                var receiving=LanInventoryService.lockStock(c,"CUSTOM_ITEM",910,911);
                assertEquals(20,receiving.currentStock());
                LanInventoryService.applyStock(c,receiving,6,911);
                var variant=LanInventoryService.lockStock(c,"CUSTOM_VARIANT",910,911);
                assertEquals(30,variant.currentStock());
                LanInventoryService.applyStock(c,variant,8,911);
                c.commit();
                stock(c,910,null,911,26); stock(c,910,null,910,12);
                stock(c,911,910L,911,38); stock(c,911,910L,910,7);
                stock(c,911,null,911,38); stock(c,911,null,910,11);

                CustomItemStockService.consume(c,910,null,911);
                CustomItemStockService.consume(c,911,910L,911);
                c.commit();
                stock(c,910,null,911,25); stock(c,911,910L,911,37);
                stock(c,910,null,910,12); stock(c,911,910L,910,7);
                assertEquals(1,number(c,"SELECT sold_quantity FROM custom_item_inventory WHERE custom_item_id=910 AND location_id=911"));
                assertEquals(0,number(c,"SELECT sold_quantity FROM custom_item_inventory WHERE custom_item_id=910 AND location_id=910"));
                assertEquals(1,number(c,"SELECT sold_quantity FROM custom_variant_inventory WHERE custom_variant_id=910 AND location_id=911"));
                assertEquals(0,number(c,"SELECT sold_quantity FROM custom_variant_inventory WHERE custom_variant_id=910 AND location_id=910"));
                // Spoil/return/exchange use the same atomic stock writer, with an explicit shortage policy.
                CustomItemStockService.move(c,910,null,911,n(-2),n(0),false);
                CustomItemStockService.move(c,910,null,911,n(1),n(-1),false);
                stock(c,910,null,911,24); c.rollback(); stock(c,910,null,911,25);
                assertThrows(SQLException.class,()->CustomItemStockService.move(c,910,null,911,n(-100),n(0),true));
                c.rollback(); stock(c,910,null,911,25); stock(c,910,null,910,12);
                assertThrows(SQLException.class,()->CustomItemStockService.move(c,910,910L,911,n(1),n(0),false));
                c.rollback(); stock(c,911,910L,911,37);

                var a=LanCustomOrderCatalogAdminService.load(c,910);
                var b=LanCustomOrderCatalogAdminService.load(c,911);
                assertEquals(0,a.items().stream().filter(i->i.itemId()==910).findFirst().orElseThrow().quantity().compareTo(n(12)));
                assertEquals(0,b.items().stream().filter(i->i.itemId()==910).findFirst().orElseThrow().quantity().compareTo(n(25)));
                var item=b.items().stream().filter(i->i.itemId()==910).findFirst().orElseThrow();
                var payload=new JsonObject();var draft=LanJson.create().toJsonTree(item).getAsJsonObject();draft.addProperty("quantity",22);draft.addProperty("reorderLevel",23);draft.addProperty("itemType","Stock test");draft.addProperty("brand","Stock test");payload.add("item",draft);
                LanCustomOrderCatalogAdminService.mutate(c,"SAVE_ITEM",payload,911);
                c.commit();stock(c,910,null,911,22);stock(c,910,null,910,12);
                assertEquals(23,number(c,"SELECT reorder_level FROM custom_item_inventory WHERE custom_item_id=910 AND location_id=911"));
                assertEquals(3,number(c,"SELECT reorder_level FROM custom_item_inventory WHERE custom_item_id=910 AND location_id=910"));
                var savedVariant=b.items().stream().filter(i->i.itemId()==911).findFirst().orElseThrow().variants().stream().filter(v->v.variantId()==910).findFirst().orElseThrow();
                var variantDraft=LanJson.create().toJsonTree(savedVariant).getAsJsonObject();
                variantDraft.addProperty("quantity",35);variantDraft.addProperty("reorderLevel",6);
                var variantPayload=new JsonObject();variantPayload.add("variant",variantDraft);
                LanCustomOrderCatalogAdminService.mutate(c,"SAVE_VARIANT",variantPayload,911);
                c.commit();stock(c,911,910L,911,35);stock(c,911,910L,910,7);
                assertEquals(6,number(c,"SELECT reorder_level FROM custom_variant_inventory WHERE custom_variant_id=910 AND location_id=911"));
                assertEquals(2,number(c,"SELECT reorder_level FROM custom_variant_inventory WHERE custom_variant_id=910 AND location_id=910"));
                var inline = new JsonObject();
                inline.addProperty("itemId", 911);
                inline.addProperty("variantId", 910);
                inline.add("expected", com.google.gson.JsonParser.parseString("{\"color\":[\"\"]}"));
                inline.add("changes", com.google.gson.JsonParser.parseString("{\"color\":[\"Navy\"]}"));
                assertEquals(910, LanCustomOrderCatalogAdminService.mutate(c, "EDIT_VARIANT_CELLS", inline, 911));
                c.commit();
                stock(c,911,910L,911,35);stock(c,911,910L,910,7);
                var updatedVariant = LanCustomOrderCatalogAdminService.load(c, 911).items().stream()
                        .filter(record -> record.itemId() == 911).flatMap(record -> record.variants().stream())
                        .filter(record -> record.variantId() == 910).findFirst().orElseThrow();
                assertEquals("Navy", updatedVariant.color());
                inline.add("expected", com.google.gson.JsonParser.parseString("{\"quantity\":[34]}"));
                inline.add("changes", com.google.gson.JsonParser.parseString("{\"quantity\":[40]}"));
                assertThrows(IllegalArgumentException.class,
                        () -> LanCustomOrderCatalogAdminService.mutate(c, "EDIT_VARIANT_CELLS", inline, 911));
                c.rollback();
                stock(c,911,910L,911,35);stock(c,911,910L,910,7);
                variantDraft.remove("variantId");variantDraft.addProperty("name","Green");variantDraft.addProperty("quantity",9);
                long newVariant=LanCustomOrderCatalogAdminService.mutate(c,"SAVE_VARIANT",variantPayload,911);
                c.commit();stock(c,911,newVariant,911,9);stock(c,911,newVariant,910,0);
                draft.remove("itemId");draft.addProperty("name","New store item");draft.addProperty("quantity",4);
                long newItem=LanCustomOrderCatalogAdminService.mutate(c,"SAVE_ITEM",payload,911);
                c.commit();stock(c,newItem,null,911,4);stock(c,newItem,null,910,0);

                assertEquals(22,((Number)LanInventoryService.receivingSearch(c,"Stock Bottle",910,911).stream().filter(r->r.get("itemType").equals("Custom Item")).findFirst().orElseThrow().get("quantityOnHand")).intValue());
                assertFalse(LanProductAdminService.priceTagItems(c,"Stock",910,911).isEmpty());
                var body=new JsonObject();body.addProperty("query","Stock");
                assertNotNull(InventoryWebService.page(c,910,911,body));
                assertNotNull(LanOrdersDashboardService.load(c,911,ZoneId.of("America/Guyana")));
                assertTrue(ReferenceDataSyncService.cloudMirrorTablesForApi().contains("custom_item_inventory"));
                assertTrue(ReferenceDataSyncService.cloudMirrorTablesForApi().contains("custom_variant_inventory"));
                c.rollback();c.setAutoCommit(true);
                verifyRosehallCorrection(c);
                // Exercise the upgrade path and idempotency independently of a fresh baseline.
                sql(c,"DROP FUNCTION custom_stock_value(bigint,bigint,integer,text)");
                sql(c,"DROP TABLE custom_item_inventory,custom_variant_inventory,custom_inventory_legacy_migration");
                SchemaContractService.ensureCustomInventoryStoreUpgrade(c);
                SchemaContractService.ensureRosehallCustomStockCorrection(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                SchemaContractService.ensureCustomInventoryStoreUpgrade(c);
            } finally {sql(root,"DROP DATABASE "+database+" WITH (FORCE)");}
        }
    }
    private static void verifyRosehallCorrection(Connection c)throws Exception {
        String repair=SqlScriptRunner.readResource("database/maintenance/rosehall_custom_stock_correction.sql");
        c.setAutoCommit(false);
        sql(c,"INSERT INTO locations(location_id,name) VALUES(2,'Rosehall') ON CONFLICT(location_id) DO UPDATE SET name='Rosehall'");
        sql(c,"UPDATE custom_inventory_legacy_migration SET location_id=2,migrated_at='2026-11-17 10:04:46.069939-04',rosehall_stock_corrected_at=NULL");
        CustomItemStockService.set(c,910,null,2,n(12),n(4));
        CustomItemStockService.set(c,911,910L,2,n(-3),n(5));
        // A different/new installation must never inherit this historical reset.
        sql(c,"UPDATE custom_inventory_legacy_migration SET migrated_at=now()");
        SqlScriptRunner.runSql(c,repair);stock(c,910,null,2,12);
        sql(c,"UPDATE custom_inventory_legacy_migration SET migrated_at='2026-11-17 10:04:46.069939-04'");
        SqlScriptRunner.runSql(c,repair);
        stock(c,910,null,2,0);stock(c,911,910L,2,0);
        stock(c,910,null,910,12);stock(c,911,910L,910,7);
        assertEquals(4,number(c,"SELECT reorder_level FROM custom_item_inventory WHERE custom_item_id=910 AND location_id=2"));
        assertEquals(2,number(c,"SELECT count(*) FROM custom_order_item_movements WHERE location_id=2 AND note='rosehall-custom-stock-reset-20261124'"));
        assertEquals(12,number(c,"SELECT (rosehall_stock_correction_before->'items'->0->>'quantity_on_hand')::numeric FROM custom_inventory_legacy_migration"));
        CustomItemStockService.move(c,910,null,2,n(8),n(0),false);
        SqlScriptRunner.runSql(c,repair);stock(c,910,null,2,8);
        // Restored evidence/completion must still prevent replay after receiving.
        assertEquals(1,number(c,"SELECT count(*) FROM custom_inventory_legacy_migration WHERE rosehall_stock_corrected_at IS NOT NULL"));
        c.rollback();stock(c,910,null,2,0);
        // The already supplied manual repair must also protect subsequent receipts.
        sql(c,"INSERT INTO locations(location_id,name) VALUES(2,'Rosehall') ON CONFLICT(location_id) DO UPDATE SET name='Rosehall'");
        sql(c,"UPDATE custom_inventory_legacy_migration SET location_id=2,migrated_at='2026-11-17 10:04:46.069939-04',rosehall_stock_corrected_at=NULL");
        CustomItemStockService.set(c,910,null,2,n(8),n(4));
        sql(c,"INSERT INTO custom_order_item_movements(custom_item_id,location_id,change_qty,reason,note) VALUES(910,2,-12,'STOCK_COUNT','rosehall-custom-stock-reset-20261124')");
        SqlScriptRunner.runSql(c,repair);stock(c,910,null,2,8);
        c.rollback();c.setAutoCommit(true);
    }
    private static BigDecimal n(int value){return BigDecimal.valueOf(value);}
    private static void stock(Connection c,long item,Long variant,int store,int expected)throws Exception {
        assertEquals(0,n(expected).compareTo(CustomItemStockService.quantity(c,item,variant,store)),"item="+item+", variant="+variant+", store="+store);
    }
    private static int number(Connection c,String sql)throws Exception{try(var p=c.createStatement();var r=p.executeQuery(sql)){r.next();return r.getInt(1);}}
    private static void sql(Connection c,String sql)throws Exception{try(var p=c.createStatement()){p.execute(sql);}}
}
