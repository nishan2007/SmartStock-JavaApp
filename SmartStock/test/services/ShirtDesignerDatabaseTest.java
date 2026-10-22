package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static services.StorefrontService.*;

/** An isolated, explicitly selected cluster only; never uses the application's database config. */
class ShirtDesignerDatabaseTest {
    @Test void migrationsCatalogueEstimatesPrivateSubmissionAndRetries()throws Exception{
        String admin=System.getProperty("shirt.test.admin","");assumeTrue(!admin.isBlank());if(!admin.equals("jdbc:postgresql://127.0.0.1:55449/postgres"))throw new IllegalArgumentException("Use the disposable shirt test cluster.");
        String db="shirt_test_"+UUID.randomUUID().toString().replace("-","");try(Connection root=DriverManager.getConnection(admin,"shirt_test","")){
            execute(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(admin.replace("/postgres","/"+db),"shirt_test","")){
                SchemaContractService.installLocalBaseline(c);StorefrontSchema.ensure(c);assertTrue(SchemaContractService.validateLocal(c).ready());
                execute(c,"ALTER TABLE custom_order_items DROP COLUMN is_business_cards; ALTER TABLE custom_order_items DROP COLUMN is_large_format_printing; ALTER TABLE custom_order_items DROP COLUMN is_laser");
                SchemaContractService.ensurePrintServiceProductsUpgrade(c);SchemaContractService.ensurePrintServiceProductsUpgrade(c);
                execute(c,"ALTER TABLE custom_order_items DROP COLUMN is_sublimation");SchemaContractService.ensureSublimationUpgrade(c);SchemaContractService.ensureSublimationUpgrade(c);
                execute(c,"ALTER TABLE custom_order_items DROP COLUMN is_shirt; ALTER TABLE custom_order_items DROP COLUMN shirt_designer_config");
                SchemaContractService.ensureShirtDesignerUpgrade(c);assertTrue(SchemaContractService.validateLocal(c).ready());
                execute(c,"ALTER TABLE custom_order_print_materials DROP COLUMN available_colors");SchemaContractService.ensurePrintMaterialColorsUpgrade(c);SchemaContractService.ensurePrintMaterialColorsUpgrade(c);assertTrue(SchemaContractService.validateLocal(c).ready());
                for(int i=0;i<2;i++)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261013120000_shirt_designer.sql"));
                execute(c,"INSERT INTO locations(location_id,name) VALUES(990,'Shirt test'); INSERT INTO company_customization(location_id) VALUES(990); INSERT INTO custom_order_print_materials(print_material_id,material_name) VALUES(991,'Vinyl'),(992,'Transfer paper'); INSERT INTO custom_order_print_size_presets(print_size_preset_id,print_material_id,preset_name,pricing_mode,fixed_price) VALUES(991,991,'Large vinyl','FIXED_PRESET',100),(992,992,'Full sheet','FIXED_PRESET',400),(993,992,'Logo','FIXED_PRESET',150)");
                JsonObject material=new JsonObject();material.addProperty("materialId",991);material.addProperty("name","Vinyl");material.addProperty("active",true);material.add("availableColors",PrintMaterialColors.parse("Red=#ff0000, Black=#000000"));JsonObject materialBody=new JsonObject();materialBody.add("material",material);
                LanCustomOrderCatalogAdminService.mutate(c,"SAVE_MATERIAL",materialBody);
                assertEquals("#ff0000",LanCustomOrderCatalogAdminService.load(c).materials().stream().filter(m->m.materialId()==991).findFirst().orElseThrow().availableColors().get("Red").getAsString());
                JsonObject config=ShirtDesignerService.defaults();config.addProperty("websiteEnabled",true);
                JsonObject item=JsonParser.parseString("{\"name\":\"Cotton shirt\",\"size\":\"M\",\"color\":\"Black\",\"productType\":\"INVENTORY\",\"pricingType\":\"FIXED\",\"fixedPrice\":1003,\"active\":true,\"isShirt\":true}").getAsJsonObject();item.addProperty("itemType","Shirts");item.addProperty("brand","Test cotton");item.add("shirtDesignerConfig",config);JsonObject body=new JsonObject();body.add("item",item);
                long id=LanCustomOrderCatalogAdminService.mutate(c,"SAVE_ITEM",body);var loaded=LanCustomOrderCatalogAdminService.load(c).items().stream().filter(i->i.itemId()==id).findFirst().orElseThrow();assertTrue(loaded.isShirt());assertEquals("Ready",loaded.shirtReadiness());assertEquals(1,ShirtDesignerService.catalog(c,990).size());
                assertEquals(3,ShirtDesignerService.catalog(c,990).get(0).getAsJsonObject().getAsJsonArray("addOns").size());
                execute(c,"INSERT INTO custom_order_print_materials(print_material_id,material_name) VALUES(994,'Embroidery'),(995,'Future material'); INSERT INTO custom_order_print_size_presets(print_size_preset_id,print_material_id,preset_name,pricing_mode,fixed_price) VALUES(994,994,'Crest','FIXED_PRESET',700),(995,995,'Future line','PER_LINE',60)");
                JsonArray automatic=ShirtDesignerService.catalog(c,990).get(0).getAsJsonObject().getAsJsonArray("addOns");assertEquals(5,automatic.size());
                assertTrue(automatic.asList().stream().anyMatch(v->text(v.getAsJsonObject(),"material").equals("Embroidery")));assertTrue(automatic.asList().stream().anyMatch(v->text(v.getAsJsonObject(),"pricingMode").equals("PER_LINE")));
                execute(c,"UPDATE custom_order_print_size_presets SET is_active=false WHERE print_size_preset_id=995");assertEquals(4,ShirtDesignerService.catalog(c,990).get(0).getAsJsonObject().getAsJsonArray("addOns").size());
                execute(c,"UPDATE custom_order_print_materials SET is_active=false WHERE print_material_id=994");assertEquals(3,ShirtDesignerService.catalog(c,990).get(0).getAsJsonObject().getAsJsonArray("addOns").size());
                // Continue covering legacy, already-saved area-priced designs explicitly.
                config.getAsJsonObject("colors").addProperty("Black","#000000");
                for(String name:ShirtDesignerService.AREAS){JsonObject a=config.getAsJsonObject("areas").getAsJsonObject(name);a.addProperty("widthIn",name.equals("CHEST")?3:10);a.addProperty("heightIn",name.equals("CHEST")?3:12);a.addProperty("singlePresetId",991);a.addProperty("fullPresetId",name.equals("CHEST")?993:992);}
                config.addProperty("useSystemPresets",false);item.addProperty("itemId",id);item.add("shirtDesignerConfig",config);LanCustomOrderCatalogAdminService.mutate(c,"SAVE_ITEM",body);
                JsonObject design=ShirtDesignerServiceTest.design();design.addProperty("itemId",id);design.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("variantId",0);
                var estimate=ShirtDesignerService.estimate(c,990,design);assertEquals(2800,estimate.get("total").getAsInt());
                UUID user=UUID.randomUUID(),request=UUID.randomUUID();JsonObject quote=new JsonObject();quote.addProperty("requestId",request.toString());quote.addProperty("topic","Custom T-Shirts");quote.addProperty("description","Review my shirt");quote.addProperty("quantity",2);quote.add("shirtDesign",design);quote.addProperty("estimateFingerprint",text(estimate,"fingerprint"));
                c.setAutoCommit(false);assertEquals("AWAITING_ARTWORK",text(StorefrontQuoteRequests.submit(c,990,user,"shirt@example.test",quote),"status"));assertEquals(0,StorefrontQuoteRequests.staff(c,990).size());
                JsonObject finalize=new JsonObject();finalize.addProperty("requestId",request.toString());finalize.addProperty("estimateFingerprint",text(estimate,"fingerprint"));assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.finalizeShirt(c,990,UUID.randomUUID(),finalize));
                execute(c,"UPDATE custom_order_print_size_presets SET fixed_price=420 WHERE print_size_preset_id=992");JsonObject changed=StorefrontQuoteRequests.finalizeShirt(c,990,user,finalize);assertTrue(changed.get("estimateChanged").getAsBoolean());finalize.addProperty("estimateFingerprint",text(changed.getAsJsonObject("estimate"),"fingerprint"));
                assertEquals("REQUESTED",text(StorefrontQuoteRequests.finalizeShirt(c,990,user,finalize),"status"));assertEquals("REQUESTED",text(StorefrontQuoteRequests.finalizeShirt(c,990,user,finalize),"status"));assertEquals(1,StorefrontQuoteRequests.staff(c,990).size());
                assertEquals(request.toString(),text(StorefrontQuoteRequests.shirtImport(c,990,request),"requestId"));
                UUID proof=UUID.randomUUID();execute(c,"INSERT INTO storefront.quote_proofs(proof_id,request_id,revision,asset_reference,original_filename,content_type,byte_size,sha256,published_by) VALUES(?,?,1,'test-only','proof.png','image/png',1,?,990)",proof,request,"0".repeat(64));
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.shirtImport(c,990,request));execute(c,"UPDATE storefront.quote_proofs SET status='APPROVED' WHERE proof_id=?",proof);assertEquals(request.toString(),text(StorefrontQuoteRequests.shirtImport(c,990,request),"requestId"));assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.shirtImport(c,991,request));
                quote.addProperty("estimateFingerprint",text(changed.getAsJsonObject("estimate"),"fingerprint"));assertEquals(request.toString(),text(StorefrontQuoteRequests.submit(c,990,user,"shirt@example.test",quote),"requestId"));
                JsonObject altered=quote.deepCopy();altered.getAsJsonObject("shirtDesign").getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject().addProperty("text","Different artwork");assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.submit(c,990,user,"shirt@example.test",altered));
                JsonObject imageDesign=design.deepCopy();JsonObject imageLayer=imageDesign.getAsJsonObject("areas").getAsJsonObject("FRONT").getAsJsonArray("layers").get(0).getAsJsonObject();imageLayer.addProperty("type","IMAGE");imageLayer.addProperty("fileId",UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->ShirtDesignerService.verifyFiles(c,990,user,request,imageDesign));
                assertFalse(catalog(snapshot(c,990)).has("shirtDesign"));assertFalse(publicSyncSnapshot(snapshot(c,990)).has("customers"));
                item.addProperty("itemId",id);item.addProperty("isShirt",false);LanCustomOrderCatalogAdminService.mutate(c,"SAVE_ITEM",body);assertEquals(0,ShirtDesignerService.catalog(c,990).size());c.rollback();
            }finally{execute(root,"DROP DATABASE "+db+" WITH (FORCE)");}
        }
    }
}
