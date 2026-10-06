package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static services.StorefrontService.*;

/** Creates only disposable databases on an explicitly selected loopback test cluster. */
class StorefrontDatabaseIntegrationTest {
    @Test void twoStoreAcceptanceReplayPriceLockAndExpiry()throws Exception{
        String admin=System.getProperty("storefront.test.admin","");assumeTrue(!admin.isBlank());
        if(!admin.matches("jdbc:postgresql://127\\.0\\.0\\.1:55439/postgres"))throw new IllegalArgumentException("Use the isolated storefront cluster on port 55439.");
        String suffix=UUID.randomUUID().toString().replace("-",""),a="smartstock_dev_sf_a_"+suffix,b="smartstock_dev_sf_b_"+suffix;
        try(Connection root=DriverManager.getConnection(admin,"storefront_test","")){
            try(Statement s=root.createStatement()){s.execute("CREATE DATABASE "+a);s.execute("CREATE DATABASE "+b);}
            try(Connection primary=DriverManager.getConnection(admin.replace("/postgres","/"+a),"storefront_test","");Connection backup=DriverManager.getConnection(admin.replace("/postgres","/"+b),"storefront_test","")){
                SchemaContractService.installLocalBaseline(primary);SchemaContractService.installLocalBaseline(backup);
                String websitePriceMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20261006120000_storefront_website_prices.sql");
                SqlScriptRunner.runSql(backup,websitePriceMigration);SqlScriptRunner.runSql(backup,websitePriceMigration);
                StorefrontSchema.ensure(backup);
                String campaignActionMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20261005120000_storefront_campaign_actions.sql");
                SqlScriptRunner.runSql(backup,campaignActionMigration);SqlScriptRunner.runSql(backup,campaignActionMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.favorites");execute(backup,"DELETE FROM storefront.schema_version WHERE version>=13");
                String favoriteMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20261004120000_storefront_favorites.sql");
                SqlScriptRunner.runSql(backup,favoriteMigration);SqlScriptRunner.runSql(backup,favoriteMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=12");
                String projectServiceMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20261003120000_storefront_project_service.sql");
                SqlScriptRunner.runSql(backup,projectServiceMigration);SqlScriptRunner.runSql(backup,projectServiceMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.service_availability");execute(backup,"DELETE FROM storefront.schema_version WHERE version>=11");
                String serviceMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20261002120000_storefront_service_availability.sql");
                SqlScriptRunner.runSql(backup,serviceMigration);SqlScriptRunner.runSql(backup,serviceMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.project_media");execute(backup,"DELETE FROM storefront.schema_version WHERE version>=10");
                String mediaMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20261001120000_storefront_project_media.sql");
                SqlScriptRunner.runSql(backup,mediaMigration);SqlScriptRunner.runSql(backup,mediaMigration);
                StorefrontSchema.ensure(backup);
                recreateProjectsV4(backup);execute(backup,"DELETE FROM storefront.schema_version WHERE version>=9");
                String productMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20260930120000_storefront_project_products.sql");
                SqlScriptRunner.runSql(backup,productMigration);SqlScriptRunner.runSql(backup,productMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.quote_order_links");recreateProjectsV4(backup);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=8");
                String linkMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20260929120000_storefront_quote_order_links.sql");
                SqlScriptRunner.runSql(backup,linkMigration);SqlScriptRunner.runSql(backup,linkMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.quote_order_links");execute(backup,"DROP TABLE storefront.quote_proof_events");execute(backup,"DROP TABLE storefront.quote_proofs");
                recreateProjectsV4(backup);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=7");
                String proofMigration=SqlScriptRunner.readResource("database/migrations/v1_after/20260928120000_storefront_quote_proofs.sql");
                SqlScriptRunner.runSql(backup,proofMigration);SqlScriptRunner.runSql(backup,proofMigration);
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.quote_order_links");execute(backup,"DROP TABLE storefront.quote_proof_events");execute(backup,"DROP TABLE storefront.quote_proofs");execute(backup,"DROP TABLE storefront.quote_files");
                execute(backup,"DROP TABLE storefront.quote_requests");
                execute(backup,"DROP TABLE storefront.project_media");execute(backup,"DROP TABLE storefront.projects");
                execute(backup,"DROP TABLE storefront.enrollments");execute(backup,"DROP TABLE storefront.rejected_commands");
                execute(backup,"DROP TABLE storefront.settings");
                execute(backup,"""
                    CREATE TABLE storefront.settings (
                        location_id integer PRIMARY KEY, enabled boolean NOT NULL DEFAULT false,
                        pickup_hours integer NOT NULL DEFAULT 48 CHECK (pickup_hours BETWEEN 1 AND 8760),
                        currency text NOT NULL DEFAULT 'GYD' CHECK (currency ~ '^[A-Z]{3}$'),
                        welcome text NOT NULL DEFAULT 'Everyday essentials. A little extraordinary.',
                        updated_at timestamptz NOT NULL DEFAULT now()
                    )
                    """);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=2");
                StorefrontSchema.ensure(backup);StorefrontSchema.ensure(backup);
                // Older storefront versions must produce the same catalog as a fresh installation.
                execute(backup,"DROP TABLE storefront.quote_order_links");execute(backup,"DROP TABLE storefront.quote_proof_events");execute(backup,"DROP TABLE storefront.quote_proofs");execute(backup,"DROP TABLE storefront.quote_files");
                execute(backup,"DROP TABLE storefront.quote_requests");
                execute(backup,"DROP TABLE storefront.project_media");execute(backup,"DROP TABLE storefront.projects");
                execute(backup,"DROP TABLE storefront.settings");
                execute(backup,"""
                    CREATE TABLE storefront.settings (
                        location_id integer PRIMARY KEY, enabled boolean NOT NULL DEFAULT false,
                        pickup_hours integer NOT NULL DEFAULT 48 CHECK (pickup_hours BETWEEN 1 AND 8760),
                        currency text NOT NULL DEFAULT 'GYD' CHECK (currency ~ '^[A-Z]{3}$'),
                        welcome text NOT NULL DEFAULT 'Everyday essentials. A little extraordinary.',
                        updated_at timestamptz NOT NULL DEFAULT now()
                    )
                    """);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=3");
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.quote_order_links");execute(backup,"DROP TABLE storefront.quote_proof_events");execute(backup,"DROP TABLE storefront.quote_proofs");execute(backup,"DROP TABLE storefront.quote_files");
                execute(backup,"DROP TABLE storefront.quote_requests");
                execute(backup,"DROP TABLE storefront.project_media");execute(backup,"DROP TABLE storefront.projects");
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=4");
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.quote_order_links");execute(backup,"DROP TABLE storefront.quote_proof_events");execute(backup,"DROP TABLE storefront.quote_proofs");execute(backup,"DROP TABLE storefront.quote_files");
                execute(backup,"DROP TABLE storefront.quote_requests");
                recreateProjectsV4(backup);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=5");
                StorefrontSchema.ensure(backup);
                execute(backup,"DROP TABLE storefront.quote_order_links");execute(backup,"DROP TABLE storefront.quote_proof_events");execute(backup,"DROP TABLE storefront.quote_proofs");execute(backup,"DROP TABLE storefront.quote_files");
                recreateProjectsV4(backup);
                execute(backup,"DELETE FROM storefront.schema_version WHERE version>=6");
                StorefrontSchema.ensure(backup);
                for(String damage:List.of("DROP INDEX storefront.storefront_orders_expiry",
                        "ALTER TABLE storefront.orders DROP COLUMN note",
                        "ALTER TABLE storefront.orders ALTER COLUMN total TYPE numeric(8,2)",
                        "ALTER TABLE storefront.customer_links DROP CONSTRAINT customer_links_location_id_customer_uuid_key",
                        "GRANT USAGE ON SCHEMA storefront TO PUBLIC")){
                    primary.setAutoCommit(false);
                    try{execute(primary,damage);assertThrows(SQLException.class,()->StorefrontSchema.ensure(primary),damage);}
                    finally{primary.rollback();primary.setAutoCommit(true);}
                    StorefrontSchema.ensure(primary);
                }
                assertEquals(15,integer(one(rows(backup,"SELECT MAX(version) AS version FROM storefront.schema_version")),"version"));
                primary.createStatement().execute("SET search_path TO public");backup.createStatement().execute("SET search_path TO public");
                seed(primary);seed(backup);
                execute(primary,"UPDATE storefront.products SET website_price=120,promotional_price=90 WHERE location_id=1 AND product_id=1001");
                JsonObject saleProduct=catalog(snapshot(primary,1)).getAsJsonArray("products").get(0).getAsJsonObject();
                assertEquals(0,new java.math.BigDecimal("90").compareTo(decimal(saleProduct,"price")));
                assertEquals(0,new java.math.BigDecimal("120").compareTo(decimal(saleProduct,"regularPrice")));
                JsonObject saleCart=StorefrontPolicyTest.cart();saleCart.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("id",1001);
                assertEquals(0,new java.math.BigDecimal("184.68").compareTo(decimal(quote(snapshot(primary,1),saleCart,UUID.randomUUID(),"customer@example.test"),"total")));
                saleCart.addProperty("expectedTotal",new java.math.BigDecimal("184.68"));
                saleCart.addProperty("orderId",UUID.randomUUID().toString());
                primary.setAutoCommit(false);
                try{
                    JsonObject promotionalOrder=checkout(primary,snapshot(primary,1),saleCart,UUID.randomUUID(),"customer@example.test",1);
                    assertEquals(0,new java.math.BigDecimal("90").compareTo(decimal(promotionalOrder.getAsJsonObject("quote").getAsJsonArray("lines").get(0).getAsJsonObject(),"price")));
                }finally{primary.rollback();primary.setAutoCommit(true);}
                assertThrows(SQLException.class,()->execute(primary,"UPDATE storefront.products SET promotional_price=120 WHERE location_id=1 AND product_id=1001"));
                execute(primary,"UPDATE storefront.products SET website_price=NULL,promotional_price=NULL WHERE location_id=1 AND product_id=1001");
                UUID productGroup=UUID.randomUUID();
                execute(primary,"INSERT INTO product_groups(group_id,name,option_names) VALUES(?::uuid,'Test shirts','[\"Color\",\"Size\"]'::jsonb)",productGroup.toString());
                execute(primary,"UPDATE products SET group_id=?::uuid,variant_options='{\"Color\":\"Blue\",\"Size\":\"M\"}'::jsonb WHERE product_id=1001",productGroup.toString());
                execute(primary,"INSERT INTO products(product_id,name,sku,price,product_type,is_active,group_id,variant_options) VALUES(1002,'Private variant','SF-PRIVATE',100,'INVENTORY',false,?::uuid,'{\"Color\":\"Red\",\"Size\":\"M\"}'::jsonb)",productGroup.toString());
                execute(primary,"INSERT INTO storefront.products(location_id,product_id,published) VALUES(1,1002,true)");
                JsonArray publicVariants=catalog(snapshot(primary,1)).getAsJsonArray("products");
                assertEquals(1,publicVariants.size(),"Inactive group members stay out of the public catalog.");
                JsonObject publicVariant=publicVariants.get(0).getAsJsonObject();
                assertEquals(productGroup.toString(),text(publicVariant,"groupId"));
                assertEquals("Blue",publicVariant.getAsJsonObject("variantOptions").get("Color").getAsString());
                assertEquals("Test shirts",text(publicVariant,"groupName"));
                UUID favoriteOwner=UUID.randomUUID(),otherFavoriteOwner=UUID.randomUUID();
                assertEquals(1,StorefrontFavorites.set(primary,1,favoriteOwner,1001,true).size());
                assertEquals(1,StorefrontFavorites.set(primary,1,favoriteOwner,1001,true).size());
                assertTrue(StorefrontFavorites.list(primary,1,otherFavoriteOwner).isEmpty());
                assertThrows(IllegalArgumentException.class,()->StorefrontFavorites.set(primary,2,favoriteOwner,1001,true));
                execute(primary,"UPDATE storefront.products SET published=false WHERE location_id=1 AND product_id=1001");
                assertTrue(StorefrontFavorites.list(primary,1,favoriteOwner).isEmpty());
                execute(primary,"UPDATE storefront.products SET published=true WHERE location_id=1 AND product_id=1001");
                assertTrue(StorefrontFavorites.set(primary,1,favoriteOwner,1001,false).isEmpty());
                StorefrontServiceAvailability.set(primary,1,"embroidery",false);
                assertTrue(StorefrontServiceAvailability.staff(primary,1).toString().contains("\"slug\":\"embroidery\""));
                assertTrue(catalog(snapshot(primary,1)).getAsJsonArray("unavailableServices").toString().contains("embroidery"));
                assertTrue(catalog(snapshot(primary,2)).getAsJsonArray("unavailableServices").isEmpty());
                JsonObject pausedService=new JsonObject();pausedService.addProperty("requestId",UUID.randomUUID().toString());
                pausedService.addProperty("topic","Embroidery");pausedService.addProperty("description","Team polos");pausedService.addProperty("quantity",2);
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.submit(primary,1,UUID.randomUUID(),"customer@example.test",pausedService));
                assertThrows(IllegalArgumentException.class,()->StorefrontServiceAvailability.set(primary,1,"unknown-service",false));
                StorefrontServiceAvailability.set(primary,1,"embroidery",true);
                assertTrue(catalog(snapshot(primary,1)).getAsJsonArray("unavailableServices").isEmpty());
                execute(primary,"""
                    INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,
                        status,location_id,order_notes,total_amount)
                    VALUES(9002,'PRIVATE-9002',(SELECT customer_id FROM customer_accounts WHERE email='customer@example.test'),
                        'Private Customer','555-0199','COMPLETED',1,'Private artwork and internal notes',2500)
                    """);
                UUID privateDraft=StorefrontProjects.draft(primary,1,9002);
                assertEquals(privateDraft,StorefrontProjects.draft(primary,1,9002),"Adding the same completed order must reuse its private draft.");
                JsonObject draft=one(StorefrontProjects.staffState(primary,1));
                assertEquals("",text(draft,"title"));assertEquals("",text(draft,"summary"));
                assertFalse(draft.toString().contains("Private Customer"));assertFalse(draft.toString().contains("Private artwork"));
                assertTrue(snapshot(primary,1).getAsJsonArray("projects").isEmpty());
                UUID projectId=UUID.randomUUID(),projectCover=UUID.randomUUID();
                execute(primary,"INSERT INTO storefront.projects(project_id,location_id,source_custom_order_id,title,summary,category) VALUES(?,1,9001,'Approved sign','A finished sign','Signs')",projectId);
                assertTrue(snapshot(primary,1).getAsJsonArray("projects").isEmpty(),"Drafts must never enter replicated snapshots.");
                JsonObject projectAction=new JsonObject();projectAction.addProperty("projectId",projectId.toString());projectAction.addProperty("status","PUBLISHED");
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.status(primary,1,projectAction),"Publication needs an approved cover photo.");
                execute(primary,"INSERT INTO image_assets(asset_id,category,bucket_name,object_path,content_type,sha256) VALUES(?,'PROJECT','Product Images','projects/approved.jpg','image/jpeg',?)",projectCover,"b".repeat(64));
                execute(primary,"UPDATE storefront.projects SET cover_reference=? WHERE project_id=?",ImageAssetReference.format(projectCover),projectId);
                StorefrontProjects.status(primary,1,projectAction);
                UUID galleryId=UUID.randomUUID();
                execute(primary,"INSERT INTO storefront.project_media(media_id,project_id,location_id,role,caption,asset_reference,position) VALUES(?,?,1,'DETAIL','Finished edge',?,0)",galleryId,projectId,ImageAssetReference.format(projectCover));
                JsonObject mediaProject=catalog(snapshot(primary,1)).getAsJsonArray("projects").get(0).getAsJsonObject();
                assertEquals(galleryId.toString(),text(mediaProject.getAsJsonArray("gallery").get(0).getAsJsonObject(),"id"));
                assertEquals("Finished edge",text(mediaProject.getAsJsonArray("gallery").get(0).getAsJsonObject(),"caption"));
                assertFalse(mediaProject.toString().contains("_manifest"));
                assertTrue(StorefrontProjects.staffState(primary,1).asList().stream().map(com.google.gson.JsonElement::getAsJsonObject)
                    .filter(item->privateDraft.toString().equals(text(item,"id"))).findFirst().orElseThrow()
                    .getAsJsonArray("gallery").isEmpty(),"A different private draft has no public gallery.");
                JsonObject mediaAction=new JsonObject();mediaAction.addProperty("projectId",projectId.toString());mediaAction.addProperty("mediaId",galleryId.toString());
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.deleteMedia(primary,2,mediaAction));
                StorefrontProjects.deleteMedia(primary,1,mediaAction);
                assertTrue(catalog(snapshot(primary,1)).getAsJsonArray("projects").get(0).getAsJsonObject().getAsJsonArray("gallery").isEmpty());
                UUID requester=UUID.randomUUID(),requestId=UUID.randomUUID();
                JsonObject request=new JsonObject();request.addProperty("requestId",requestId.toString());
                request.addProperty("projectId",projectId.toString());request.addProperty("topic","Approved sign");
                request.addProperty("description","Use blue lettering instead.");request.addProperty("quantity",2);
                request.addProperty("size","50 cm");request.addProperty("color","Blue");
                JsonObject submitted=StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",request);
                assertEquals(requestId.toString(),text(submitted,"requestId"));
                assertEquals("REQUESTED",text(submitted,"status"));
                assertEquals(submitted,StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",request));
                assertEquals(1,StorefrontQuoteRequests.staff(primary,1).size());
                assertEquals(1,StorefrontQuoteRequests.customer(primary,requester).size());
                assertTrue(StorefrontQuoteRequests.customer(primary,UUID.randomUUID()).isEmpty());
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.submit(primary,1,UUID.randomUUID(),"another@example.test",request));
                JsonObject repeatTemplate=StorefrontQuoteRequests.template(primary,1,requester,requestId);
                assertEquals("Use blue lettering instead.",text(repeatTemplate,"description"));
                assertEquals(2,integer(repeatTemplate,"quantity"));
                assertFalse(repeatTemplate.has("email"));
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.template(primary,1,UUID.randomUUID(),requestId));
                String previousImageRoot=System.getProperty("smartstock.image.store");
                java.nio.file.Path artworkRoot=java.nio.file.Path.of("target","storefront-artwork-test-"+UUID.randomUUID()).toAbsolutePath();
                System.setProperty("smartstock.image.store",artworkRoot.toString());
                try{
                    JsonObject file=new JsonObject();UUID fileId=UUID.randomUUID();file.addProperty("requestId",requestId.toString());
                    file.addProperty("fileId",fileId.toString());file.addProperty("filename","design.pdf");
                    file.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString("%PDF-1.7\nreference".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.upload(primary,1,UUID.randomUUID(),file));
                    StorefrontQuoteRequests.upload(primary,1,requester,file);
                    StorefrontQuoteRequests.upload(primary,1,requester,file);
                    assertEquals(1,StorefrontQuoteRequests.files(primary,1,requestId).size());
                    JsonObject asset=one(rows(primary,"SELECT category,bucket_name,access_level FROM image_assets WHERE asset_id=(SELECT substring(asset_reference from 18)::uuid FROM storefront.quote_files WHERE file_id=?)",fileId));
                    assertEquals("QUOTE_ARTWORK",text(asset,"category"));assertEquals("deckers-creative",text(asset,"bucket_name"));
                    assertEquals("AUTHENTICATED",text(asset,"access_level"));
                    JsonObject altered=file.deepCopy();altered.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString("%PDF-1.7\ndifferent".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.upload(primary,1,requester,altered));
                    JsonObject proof=new JsonObject();UUID firstProof=UUID.randomUUID();proof.addProperty("requestId",requestId.toString());
                    proof.addProperty("proofId",firstProof.toString());proof.addProperty("filename","design-proof.pdf");
                    proof.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString("%PDF-1.7\nproof one".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    assertEquals("AWAITING_APPROVAL",text(StorefrontQuoteProofs.publish(primary,1,7,proof),"status"));
                    assertEquals(1,StorefrontQuoteProofs.customer(primary,requester).size());
                    assertTrue(StorefrontQuoteProofs.customer(primary,UUID.randomUUID()).isEmpty());
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteProofs.customerFile(primary,1,UUID.randomUUID(),firstProof));
                    assertEquals("design-proof.pdf",text(StorefrontQuoteProofs.customerFile(primary,1,requester,firstProof),"filename"));
                    JsonObject production=new JsonObject();production.addProperty("requestId",requestId.toString());production.addProperty("status","IN_PRODUCTION");
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.transition(primary,1,production));
                    JsonObject decision=new JsonObject();decision.addProperty("proofId",firstProof.toString());decision.addProperty("decision","REQUEST_CHANGES");decision.addProperty("note","Move the logo left.");
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteProofs.decide(primary,1,UUID.randomUUID(),decision));
                    assertEquals("CHANGES_REQUESTED",text(StorefrontQuoteProofs.decide(primary,1,requester,decision),"status"));
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.transition(primary,1,production));
                    assertThrows(IllegalArgumentException.class,()->StorefrontQuoteProofs.decide(primary,1,requester,decision));
                    JsonObject revised=proof.deepCopy();UUID secondProof=UUID.randomUUID();revised.addProperty("proofId",secondProof.toString());
                    revised.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString("%PDF-1.7\nproof two".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    assertEquals(2,integer(StorefrontQuoteProofs.publish(primary,1,7,revised),"revision"));
                    decision.addProperty("proofId",secondProof.toString());decision.addProperty("decision","APPROVE");decision.addProperty("note","");
                    assertEquals("APPROVED",text(StorefrontQuoteProofs.decide(primary,1,requester,decision),"status"));
                    assertEquals(4,StorefrontQuoteProofs.history(primary,requester).size());
                    StorefrontQuoteRequests.transition(primary,1,production);
                }finally{if(previousImageRoot==null)System.clearProperty("smartstock.image.store");else System.setProperty("smartstock.image.store",previousImageRoot);}
                assertFalse(snapshot(primary,1).toString().contains("Use blue lettering"),"Private quote details must never enter the snapshot.");
                assertFalse(snapshot(primary,1).toString().contains("design.pdf"),"Private reference filenames must never enter the snapshot.");
                assertFalse(catalog(snapshot(primary,1)).toString().contains("requester@example.test"));
                JsonObject quoteStatus=new JsonObject();quoteStatus.addProperty("requestId",requestId.toString());quoteStatus.addProperty("status","REVIEWING");
                StorefrontQuoteRequests.transition(primary,1,quoteStatus);
                assertEquals("REVIEWING",text(StorefrontQuoteRequests.customer(primary,requester).get(0).getAsJsonObject(),"status"));
                UUID nativeCustomer=UUID.randomUUID();
                execute(primary,"INSERT INTO customer_accounts(name,email,sync_uuid) VALUES('Request customer','requester@example.test',?)",nativeCustomer);
                execute(primary,"INSERT INTO storefront.customer_links(auth_id,location_id,customer_uuid,email) VALUES(?,1,?,'requester@example.test')",requester,nativeCustomer);
                execute(primary,"INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,status,location_id) VALUES(9003,'WEB-9003',(SELECT customer_id FROM customer_accounts WHERE sync_uuid=?),'Request customer','','NEW',1)",nativeCustomer);
                JsonObject link=new JsonObject();link.addProperty("requestId",requestId.toString());link.addProperty("customOrderId",9003);
                execute(primary,"INSERT INTO custom_orders(custom_order_id,order_number,customer_id,customer_name,customer_phone,status,location_id) VALUES(9004,'OTHER-9004',(SELECT customer_id FROM customer_accounts WHERE email='customer@example.test'),'Other customer','','NEW',1)");
                JsonObject wrongLink=link.deepCopy();wrongLink.addProperty("customOrderId",9004);
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.linkOrder(primary,1,7,wrongLink));
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.linkOrder(primary,2,7,link));
                assertEquals("WEB-9003",text(StorefrontQuoteRequests.linkOrder(primary,1,7,link),"orderNumber"));
                assertEquals("WEB-9003",text(StorefrontQuoteRequests.linkOrder(primary,1,7,link),"orderNumber"));
                JsonObject linkedProof=new JsonObject();linkedProof.addProperty("requestId",requestId.toString());linkedProof.addProperty("proofId",UUID.randomUUID().toString());
                assertTrue(assertThrows(IllegalArgumentException.class,()->StorefrontQuoteProofs.publish(primary,1,7,linkedProof)).getMessage().contains("already linked"));
                assertEquals("WEB-9003",text(StorefrontQuoteRequests.customer(primary,requester).get(0).getAsJsonObject(),"orderNumber"));
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.transition(primary,1,quoteStatus));
                execute(primary,"UPDATE custom_orders SET status='IN_PROGRESS' WHERE custom_order_id=9003");
                assertEquals("IN_PRODUCTION",text(StorefrontQuoteRequests.customer(primary,requester).get(0).getAsJsonObject(),"status"));
                execute(primary,"UPDATE custom_orders SET status='READY' WHERE custom_order_id=9003");
                assertEquals("READY",text(StorefrontQuoteRequests.customer(primary,requester).get(0).getAsJsonObject(),"status"));
                execute(primary,"UPDATE custom_orders SET status='COMPLETED' WHERE custom_order_id=9003");
                assertEquals("COMPLETED",text(StorefrontQuoteRequests.customer(primary,requester).get(0).getAsJsonObject(),"status"));
                JsonObject measured=request.deepCopy();measured.addProperty("requestId",UUID.randomUUID().toString());
                measured.addProperty("modelUnits","CM");measured.addProperty("printQuality","DETAIL");
                StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",measured);
                assertEquals("Use blue lettering instead.\nModel units: centimeters\nPrint quality preference: Prioritize fine detail",
                    text(StorefrontQuoteRequests.template(primary,1,requester,UUID.fromString(text(measured,"requestId"))),"description"));
                assertThrows(IllegalArgumentException.class,()->{
                    JsonObject invalidUnits=request.deepCopy();invalidUnits.addProperty("requestId",UUID.randomUUID().toString());invalidUnits.addProperty("modelUnits","METERS");
                    StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",invalidUnits);
                });
                JsonObject invalidEdit=new JsonObject();invalidEdit.addProperty("projectId",projectId.toString());
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.save(primary,1,invalidEdit));
                JsonObject curated=new JsonObject();curated.addProperty("projectId",projectId.toString());
                curated.addProperty("title","Approved sign");curated.addProperty("summary","A finished sign");curated.addProperty("category","Signs");
                curated.addProperty("serviceSlug","signs");
                curated.add("productIds",JsonParser.parseString("[1001]"));
                StorefrontProjects.save(primary,1,curated);
                JsonObject invalidService=curated.deepCopy();invalidService.addProperty("serviceSlug","unknown-service");
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.save(primary,1,invalidService));
                StorefrontServiceAvailability.set(primary,1,"signs",false);
                JsonObject pausedProject=request.deepCopy();pausedProject.addProperty("requestId",UUID.randomUUID().toString());
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",pausedProject));
                StorefrontServiceAvailability.set(primary,1,"signs",true);
                JsonObject wrongProduct=curated.deepCopy();wrongProduct.add("productIds",JsonParser.parseString("[9999]"));
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.save(primary,1,wrongProduct));
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.save(primary,2,curated));
                JsonObject publicProject=catalog(snapshot(primary,1)).getAsJsonArray("projects").get(0).getAsJsonObject();
                assertEquals("Approved sign",text(publicProject,"title"));assertEquals("available",text(publicProject,"cover"));
                assertEquals("signs",text(publicProject,"serviceSlug"));
                JsonObject social=StorefrontProjects.share(primary,1,projectId,"https://deckers.example");
                assertEquals("https://deckers.example/shop/projects/1/"+projectId,text(social,"url"));
                assertTrue(text(social,"caption").contains("Approved sign"));
                assertFalse(text(social,"caption").contains("requester@example.test"));
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.share(primary,2,projectId,"https://deckers.example"));
                assertEquals(1001,publicProject.getAsJsonArray("productIds").get(0).getAsInt());
                execute(primary,"UPDATE storefront.products SET published=false WHERE location_id=1 AND product_id=1001");
                assertTrue(catalog(snapshot(primary,1)).getAsJsonArray("projects").get(0).getAsJsonObject().getAsJsonArray("productIds").isEmpty(),
                    "Unpublished products must disappear from public project relationships.");
                execute(primary,"UPDATE storefront.products SET published=true WHERE location_id=1 AND product_id=1001");
                assertFalse(publicProject.has("source_custom_order_id"));assertFalse(publicProject.has("_coverManifest"));
                assertFalse(catalog(snapshot(primary,1)).toString().contains("projects/approved.jpg"));
                execute(primary,"UPDATE storefront.projects SET status='ARCHIVED' WHERE project_id=?",projectId);
                assertThrows(IllegalArgumentException.class,()->StorefrontProjects.share(primary,1,projectId,"https://deckers.example"));
                assertTrue(snapshot(primary,1).getAsJsonArray("projects").isEmpty(),"Archiving must remove projects from public snapshots.");
                JsonObject laterRequest=request.deepCopy();laterRequest.addProperty("requestId",UUID.randomUUID().toString());
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",laterRequest),"Archived inspiration cannot seed a new request.");
                JsonObject repeat=laterRequest.deepCopy();repeat.remove("projectId");repeat.addProperty("sourceRequestId",requestId.toString());
                repeat.addProperty("description","Repeat with green lettering.");
                assertThrows(IllegalArgumentException.class,()->StorefrontQuoteRequests.submit(primary,1,UUID.randomUUID(),"other@example.test",repeat),"Another customer cannot reuse private project context.");
                JsonObject repeated=StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",repeat);
                assertEquals(text(repeat,"requestId"),text(repeated,"requestId"));
                assertEquals(repeated,StorefrontQuoteRequests.submit(primary,1,requester,"requester@example.test",repeat));
                JsonObject repeatedContext=one(rows(primary,"SELECT project_context FROM storefront.quote_requests WHERE request_id=?",UUID.fromString(text(repeat,"requestId")))).getAsJsonObject("project_context");
                assertEquals("Approved sign",text(repeatedContext,"title"));
                execute(primary,"UPDATE company_info SET company_name='Sample Company',company_motto_line1='Saved primary motto',company_motto_line2='Saved secondary motto' WHERE company_info_id=1");
                execute(primary,"UPDATE locations SET company_phone_line1='555-0100',company_email_line1='shop@example.test' WHERE location_id=1");
                var publicBrand=catalog(snapshot(primary,1)).getAsJsonObject("branding");
                assertEquals("Sample Company",text(publicBrand,"name"));
                assertEquals("Saved primary motto",text(publicBrand,"mottoLine1"));
                assertEquals("Saved secondary motto",text(publicBrand,"mottoLine2"));
                assertEquals("555-0100",text(publicBrand,"phoneLine1"));
                assertEquals("shop@example.test",text(publicBrand,"emailLine1"));
                assertFalse(publicBrand.has("_logoReference"));
                JsonObject publicCampaign=catalog(snapshot(primary,1)).getAsJsonObject("campaign");
                assertEquals("3D Printing",text(publicCampaign,"topic"));
                assertEquals("Your Ideas. Made Real.",text(publicCampaign,"headline"));
                assertFalse(catalog(snapshot(primary,1)).getAsJsonObject("settings").has("campaign_headline"));
                execute(primary,"""
                    UPDATE storefront.settings SET campaign_topic='Embroidery',campaign_eyebrow='Made for your brand',
                        campaign_headline='Built to Last.',campaign_description='Custom embroidery at Deckers.',
                        campaign_steps='Choose your garment · Share your design · We stitch it',
                        campaign_primary='Explore Embroidery',campaign_secondary='Shop ready products',campaign_visual='EDITORIAL'
                    WHERE location_id=1
                    """);
                JsonObject changedCampaign=catalog(snapshot(primary,1)).getAsJsonObject("campaign");
                assertEquals("Embroidery",text(changedCampaign,"topic"));
                assertEquals("Built to Last.",text(changedCampaign,"headline"));
                assertEquals("EDITORIAL",text(changedCampaign,"visual"));
                // Missing sender configuration must retain notifications, without sending real mail.
                ServerEmailOutboxService.queueStorefrontOrder(primary,1,"customer@example.test","isolated-email-order","CONFIRMED","");
                var pendingEmail=one(rows(primary,"SELECT * FROM email_outbox WHERE document_id='isolated-email-order'"));
                assertEquals("FAILED",text(pendingEmail,"status"));assertEquals(0,integer(pendingEmail,"attempts"));
                assertTrue(text(pendingEmail,"last_error").startsWith("STOREFRONT_SENDER_REQUIRED:"));
                assertEquals(2,rows(primary,"SELECT email_outbox_id FROM email_outbox WHERE document_type='STOREFRONT_PROOF'").size());
                assertEquals(3,integer(StorefrontAdminService.notificationSummary(primary,1),"failed"));
                assertEquals(0,ServerEmailOutboxService.resumeStorefrontNotifications(primary));
                execute(primary,"UPDATE locations SET email_sender_address='sender@example.test',email_sender_name='Isolated Store' WHERE location_id=1");
                assertEquals(3,ServerEmailOutboxService.resumeStorefrontNotifications(primary));
                assertEquals(0,ServerEmailOutboxService.resumeStorefrontNotifications(primary));
                var resumedEmail=one(rows(primary,"SELECT * FROM email_outbox WHERE document_id='isolated-email-order'"));
                assertEquals(text(pendingEmail,"email_outbox_id"),text(resumedEmail,"email_outbox_id"));
                assertEquals("QUEUED",text(resumedEmail,"status"));assertEquals("sender@example.test",text(resumedEmail,"sender_email"));
                assertEquals("customer@example.test",text(resumedEmail,"recipient_email"));
                assertEquals(0,integer(StorefrontAdminService.notificationSummary(primary,1),"failed"));
                execute(primary,"UPDATE locations SET email_sender_address='' WHERE location_id=1");
                UUID imageId=UUID.randomUUID();
                execute(primary,"INSERT INTO image_assets(asset_id,category,bucket_name,object_path,content_type,sha256) VALUES(?,'PRODUCT','products','storefront-test.jpg','image/jpeg',?)",imageId,"a".repeat(64));
                execute(primary,"UPDATE products SET image_url='https://legacy.example.test/storage/v1/object/public/products/storefront-test.jpg' WHERE product_id=1001");
                JsonObject snapshot=snapshot(primary,1);assertEquals(1,snapshot.getAsJsonArray("products").size());
                JsonObject imageProduct=snapshot.getAsJsonArray("products").get(0).getAsJsonObject();
                assertEquals(ImageAssetReference.format(imageId),text(imageProduct,"image"));
                assertEquals("a".repeat(64),text(imageProduct.getAsJsonObject("_imageManifest"),"sha256"));
                assertFalse(catalog(snapshot).toString().contains("storefront-test.jpg"));
                execute(primary,"UPDATE image_assets SET category='EMPLOYEE_PHOTO' WHERE asset_id=?",imageId);
                assertNull(ServerImageAssetService.storefrontManifest(primary,ImageAssetReference.format(imageId)));
                execute(primary,"UPDATE image_assets SET category='PRODUCT' WHERE asset_id=?",imageId);
                UUID auth=UUID.randomUUID(),id=UUID.randomUUID();JsonObject cart=StorefrontPolicyTest.cart();cart.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("id",1001);cart.addProperty("orderId",id.toString());
                primary.setAutoCommit(false);backup.setAutoCommit(false);
                JsonObject order=checkout(backup,snapshot,cart,auth,"customer@example.test",2);backup.commit();
                assertEquals("CONFIRMED",text(order,"status"));assertEquals(0,rows(backup,"SELECT * FROM storefront.reservations").size());
                assertEquals(id.toString(),text(checkout(backup,snapshot,cart,auth,"customer@example.test",2),"order_id"));backup.commit();
                assertThrows(IllegalArgumentException.class,()->checkout(backup,snapshot,cart,UUID.randomUUID(),"customer@example.test",2));backup.rollback();
                importOrder(primary,order,1);importOrder(primary,order,1);primary.commit();
                assertEquals(1,rows(primary,"SELECT * FROM storefront.orders").size());assertEquals(2,integer(one(rows(primary,"SELECT quantity FROM storefront.reservations")),"quantity"));
                execute(primary,"UPDATE products SET price=500 WHERE product_id=1001");execute(primary,"UPDATE storefront.settings SET pickup_hours=168 WHERE location_id=1");
                transition(primary,id,1,"READY","Ready for pickup.",0);primary.commit();
                JsonObject ready=one(rows(primary,"SELECT *,EXTRACT(EPOCH FROM (expires_at-ready_at))/3600 AS hours FROM storefront.orders WHERE order_id=?",id));
                assertEquals(168,integer(ready,"hours"));assertEquals(0,new java.math.BigDecimal("205.20").compareTo(decimal(ready,"total")));
                execute(primary,"UPDATE storefront.settings SET pickup_hours=24 WHERE location_id=1");
                transition(primary,id,1,"NEEDS_ATTENTION","Check pickup details.",0);
                transition(primary,id,1,"PREPARING","Resolved.",0);transition(primary,id,1,"READY","Ready again.",0);primary.commit();
                JsonObject readyAgain=one(rows(primary,"SELECT * FROM storefront.orders WHERE order_id=?",id));
                assertEquals(text(ready,"ready_at"),text(readyAgain,"ready_at"));
                assertEquals(text(ready,"expires_at"),text(readyAgain,"expires_at"),"Status recovery must preserve the saved pickup deadline.");
                ready=readyAgain;
                importOrder(backup,ready,2);backup.commit();assertFalse(text(one(rows(backup,"SELECT expires_at FROM storefront.orders")),"expires_at").isBlank());
                // A newly added replica may first encounter an order after preparation.
                execute(backup,"DELETE FROM storefront.orders WHERE order_id=?",id);
                importOrder(backup,ready,2);backup.commit();
                JsonObject firstReady=one(rows(backup,"SELECT * FROM storefront.orders WHERE order_id=?",id));
                assertEquals(text(ready,"ready_at"),text(firstReady,"ready_at"));
                assertEquals(text(ready,"expires_at"),text(firstReady,"expires_at"));
                JsonObject reassigned=ready.deepCopy();reassigned.addProperty("auth_id",UUID.randomUUID().toString());reassigned.addProperty("revision",999);
                assertThrows(IllegalArgumentException.class,()->importOrder(backup,reassigned,2));backup.rollback();
                execute(primary,"UPDATE storefront.orders SET expires_at=now()-interval '1 second' WHERE order_id=?",id);expire(primary,1);primary.commit();
                assertEquals("EXPIRED",text(one(rows(primary,"SELECT status FROM storefront.orders")),"status"));assertTrue(rows(primary,"SELECT * FROM storefront.reservations").isEmpty());
                assertThrows(IllegalArgumentException.class,()->transition(primary,id,1,"COLLECTED","",0));primary.rollback();
                // Cache is allowed to over-promise during an outage; reconciliation must identify the shortage.
                JsonObject another=cart.deepCopy();another.addProperty("orderId",UUID.randomUUID().toString());JsonObject accepted=checkout(backup,snapshot,another,auth,"customer@example.test",2);backup.commit();
                execute(primary,"UPDATE inventory SET quantity_on_hand=0 WHERE location_id=1 AND product_id=1001");importOrder(primary,accepted,1);primary.commit();
                assertEquals("NEEDS_ATTENTION",text(one(rows(primary,"SELECT status FROM storefront.orders WHERE order_id=?",UUID.fromString(text(accepted,"order_id")))),"status"));
                UUID collectedId=UUID.fromString(text(accepted,"order_id")),device=UUID.randomUUID();
                execute(primary,"UPDATE inventory SET quantity_on_hand=10 WHERE location_id=1 AND product_id=1001");
                execute(primary,"INSERT INTO users(user_id,username,full_name,role_id,is_active) SELECT 1001,'sf-test','Test Cashier',role_id,true FROM roles WHERE upper(role_name)='ADMIN'");
                execute(primary,"INSERT INTO devices(device_id,installation_id,device_name,last_store_id,is_approved,receipt_device_code) VALUES(?,'sf-isolated','Test Register',1,true,'SF01')",device);
                transition(primary,collectedId,1,"PREPARING","Restocked.",0);transition(primary,collectedId,1,"READY","Ready.",0);primary.commit();
                var payment=new JsonObject();payment.addProperty("orderId",collectedId.toString());payment.addProperty("paymentMethod","CARD");payment.addProperty("cashCollected",0);payment.addProperty("paymentReference","isolated-test");
                var sale=StorefrontAdminService.collect(primary,payment,1001,1,"Test Cashier",device,(token,permission,action,reason)->{throw new AssertionError("Locked price must not require a manual override.");});primary.commit();
                var retry=StorefrontAdminService.collect(primary,payment,1001,1,"Test Cashier",device,(token,permission,action,reason)->{throw new AssertionError("Retry cannot create another sale.");});primary.commit();
                assertEquals(sale.get("saleId").toString(),retry.get("saleId").toString());
                assertEquals(1,rows(primary,"SELECT sale_id FROM sales WHERE customer_id=(SELECT customer_id FROM customer_accounts WHERE email='customer@example.test')").size());
                assertEquals(8,integer(one(rows(primary,"SELECT quantity_on_hand AS qty FROM inventory WHERE location_id=1 AND product_id=1001")),"qty"));
                assertEquals(0,new java.math.BigDecimal("205.20").compareTo(decimal(one(rows(primary,"SELECT total_amount AS total FROM sales WHERE sale_id=?",sale.get("saleId"))),"total")));
                JsonObject collected=one(rows(primary,"SELECT * FROM storefront.orders WHERE order_id=?",collectedId));
                execute(backup,"DELETE FROM storefront.orders WHERE order_id=?",collectedId);
                importOrder(backup,collected,2);importOrder(backup,collected,2);backup.commit();
                JsonObject replica=one(rows(backup,"SELECT * FROM storefront.orders WHERE order_id=?",collectedId));
                assertEquals(text(collected,"sale_id"),text(replica,"sale_id"));
                assertEquals(text(collected,"receipt_number"),text(replica,"receipt_number"));
                assertEquals(text(collected,"expires_at"),text(replica,"expires_at"));
                assertTrue(rows(backup,"SELECT * FROM sales").isEmpty(),"A replica must never create an authoritative sale.");
                assertTrue(rows(backup,"SELECT * FROM storefront.reservations").isEmpty());
                assertEquals(collectedId.toString(),text(resolveCommand(primary,collectedId,1).getAsJsonObject("order"),"order_id"));primary.commit();
                UUID interrupted=UUID.randomUUID();
                assertTrue(resolveCommand(backup,interrupted,1).get("rejected").getAsBoolean());backup.commit();
                JsonObject delayed=cart.deepCopy();delayed.addProperty("orderId",interrupted.toString());
                assertThrows(IllegalArgumentException.class,()->checkout(backup,snapshot,delayed,auth,"customer@example.test",2));backup.rollback();
                assertTrue(rows(backup,"SELECT * FROM storefront.orders WHERE order_id=?",interrupted).isEmpty(),"A late HTTP request cannot resurrect a fenced checkout.");
                // Staff choose one verified-email candidate; resolution never merges records or balances.
                UUID duplicate=UUID.randomUUID(),originalCustomer=UUID.fromString("00000000-0000-0000-0000-000000000001");
                execute(primary,"INSERT INTO customer_accounts(sync_uuid,name,email,current_balance) VALUES(?,'Second Customer','customer@example.test',77)",duplicate);
                assertEquals(2,StorefrontAdminService.linkCandidates(primary,"customer@example.test",1).size());
                assertThrows(IllegalArgumentException.class,()->StorefrontAdminService.linkCustomer(primary,UUID.randomUUID(),originalCustomer,"customer@example.test",1));
                StorefrontAdminService.linkCustomer(primary,auth,duplicate,"customer@example.test",1);
                assertEquals(2,rows(primary,"SELECT customer_id FROM customer_accounts WHERE email='customer@example.test'").size());
                assertEquals(77,integer(one(rows(primary,"SELECT current_balance AS balance FROM customer_accounts WHERE sync_uuid=?",duplicate)),"balance"));
                assertEquals(originalCustomer.toString(),text(one(rows(primary,"SELECT customer_uuid FROM storefront.orders WHERE order_id=?",collectedId)),"customer_uuid"));
                assertThrows(SecurityException.class,()->StorefrontAdminService.require(primary,987654,"CUSTOMER_ACCOUNTS"));
                int pickupRole=integer(one(rows(primary,"INSERT INTO roles(role_name) VALUES('Isolated Pickup Clerk') RETURNING role_id")),"role_id");
                execute(primary,"INSERT INTO role_permissions(role_id,permission_id) SELECT ?,permission_id FROM permissions WHERE permission_key='MAKE_SALE'",pickupRole);
                execute(primary,"INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(1002,'sf-pickup','Pickup Clerk',?,true)",pickupRole);
                StorefrontAdminService.require(primary,1002,"MAKE_SALE");
                assertThrows(SecurityException.class,()->StorefrontAdminService.require(primary,1002,"COMPANY_PREFERENCES"));
                assertThrows(SecurityException.class,()->StorefrontAdminService.require(primary,1002,"CUSTOMER_ACCOUNTS"));
                assertThrows(SecurityException.class,()->StorefrontAdminService.setEnabled(primary,1002,1,false));
                assertThrows(SecurityException.class,()->StorefrontAdminService.publishAllActive(primary,1002,1));
                execute(primary,"INSERT INTO products(product_id,name,sku,price,product_type,is_active) VALUES(1003,'Publish active','SF-ACTIVE',25,'INVENTORY',true),(1004,'Archived','SF-ARCHIVED',25,'INVENTORY',false),(1005,'Service','SF-SERVICE',25,'SERVICE',true)");
                execute(primary,"INSERT INTO storefront.products(location_id,product_id,published,featured,description) VALUES(1,1003,false,true,'Keep description'),(2,1003,true,true,'Other store')");
                assertEquals(1,StorefrontAdminService.publishAllActive(primary,1001,1));
                assertEquals(0,StorefrontAdminService.publishAllActive(primary,1001,1),"Retries do not republish or rewrite products.");
                assertTrue(rows(primary,"SELECT * FROM storefront.products WHERE product_id IN (1004,1005)").isEmpty());
                var published=one(rows(primary,"SELECT * FROM storefront.products WHERE product_id=1003 AND location_id=1"));
                assertTrue(published.get("published").getAsBoolean());assertTrue(published.get("featured").getAsBoolean());assertEquals("Keep description",text(published,"description"));
                var archiveRequest=new JsonObject();archiveRequest.addProperty("productId",1003);archiveRequest.addProperty("reason","Isolated archive test");
                LanProductAdminService.setArchived(primary,archiveRequest,device,1001,1,true);
                assertTrue(rows(primary,"SELECT * FROM storefront.products WHERE product_id=1003 AND (published OR featured)").isEmpty(),"Archiving unpublishes every store.");
                assertThrows(IllegalArgumentException.class,()->StorefrontAdminService.lockPublishableProduct(primary,1003));
                assertEquals(0,StorefrontAdminService.publishAllActive(primary,1001,1),"Bulk publication excludes archived products.");
                LanProductAdminService.setArchived(primary,archiveRequest,device,1001,1,false);
                assertTrue(rows(primary,"SELECT * FROM storefront.products WHERE product_id=1003 AND published").isEmpty(),"Restoring does not republish.");
                assertEquals(1,StorefrontAdminService.publishAllActive(primary,1001,1),"Restored products may be explicitly published again.");
                var previousSettings=one(rows(primary,"SELECT * FROM storefront.settings WHERE location_id=1"));
                StorefrontAdminService.setEnabled(primary,1001,1,false);
                assertFalse(snapshot(primary,1).getAsJsonObject("settings").get("enabled").getAsBoolean());
                var websiteStatus=StorefrontAdminService.websiteStatus(primary,1);
                assertFalse(websiteStatus.containsKey("customers"));assertFalse(websiteStatus.containsKey("links"));
                StorefrontAdminService.setEnabled(primary,1001,1,true);
                assertTrue(snapshot(primary,1).getAsJsonObject("settings").get("enabled").getAsBoolean());
                assertEquals(previousSettings.get("pickup_hours"),one(rows(primary,"SELECT * FROM storefront.settings WHERE location_id=1")).get("pickup_hours"));
                var pickupState=StorefrontAdminService.orderState(primary,1);
                assertFalse(pickupState.containsKey("links"));assertFalse(pickupState.containsKey("enrollments"));
                assertFalse(pickupState.containsKey("settings"));assertFalse(pickupState.containsKey("products"));
                for(var item:(JsonArray)pickupState.get("orders"))assertEquals(1,integer(item.getAsJsonObject(),"location_id"));
                primary.rollback();
                int orderCount=rows(primary,"SELECT order_id FROM storefront.orders").size();
                UUID newAuth=UUID.randomUUID();
                JsonObject enrolled=StorefrontEnrollmentService.enroll(primary,1,newAuth,"new@example.test","New Customer",1);primary.commit();
                assertEquals("LINKED",text(enrolled,"status"));
                assertEquals("New Customer",text(one(rows(primary,"SELECT name FROM customer_accounts WHERE email='new@example.test'")),"name"));
                StorefrontEnrollmentService.enroll(primary,1,newAuth,"new@example.test","Changed display name",1);primary.commit();
                assertEquals(1,rows(primary,"SELECT customer_id FROM customer_accounts WHERE email='new@example.test'").size());
                assertEquals(orderCount,rows(primary,"SELECT order_id FROM storefront.orders").size(),"Registration does not require placing an order.");
                UUID offlineAuth=UUID.randomUUID();
                JsonObject pending=StorefrontEnrollmentService.enroll(backup,1,offlineAuth,"offline@example.test","Offline Customer",2);backup.commit();
                assertEquals("PENDING",text(pending,"status"));assertTrue(rows(backup,"SELECT customer_id FROM customer_accounts WHERE email='offline@example.test'").isEmpty());
                StorefrontEnrollmentService.importEnrollment(primary,pending,1);StorefrontEnrollmentService.importEnrollment(primary,pending,1);primary.commit();
                JsonObject linked=one(rows(primary,"SELECT * FROM storefront.enrollments WHERE auth_id=?",offlineAuth));
                assertEquals("LINKED",text(linked,"status"));assertEquals(1,rows(primary,"SELECT customer_id FROM customer_accounts WHERE email='offline@example.test'").size());
                StorefrontEnrollmentService.importEnrollment(backup,linked,2);backup.commit();
                assertEquals("LINKED",text(one(rows(backup,"SELECT status FROM storefront.enrollments WHERE auth_id=?",offlineAuth)),"status"));
                execute(primary,"INSERT INTO customer_accounts(sync_uuid,name,email) VALUES(?,'Duplicate contact','customer@example.test')",duplicate);
                UUID ambiguousAuth=UUID.randomUUID();
                JsonObject ambiguous=StorefrontEnrollmentService.enroll(primary,1,ambiguousAuth,"customer@example.test","Unresolved Customer",1);primary.commit();
                assertEquals("NEEDS_ATTENTION",text(ambiguous,"status"));assertTrue(rows(primary,"SELECT auth_id FROM storefront.customer_links WHERE auth_id=?",ambiguousAuth).isEmpty());
                StorefrontAdminService.linkCustomer(primary,ambiguousAuth,duplicate,"customer@example.test",1);primary.commit();
                assertEquals("LINKED",text(one(rows(primary,"SELECT status FROM storefront.enrollments WHERE auth_id=?",ambiguousAuth)),"status"));
                JsonObject localSnapshot=snapshot(primary,1);
                StorefrontFavorites.set(primary,1,auth,1001,true);
                JsonObject history=account(primary,localSnapshot,auth,"customer@example.test");
                assertEquals(1001,history.getAsJsonArray("favorites").get(0).getAsJsonObject().get("id").getAsInt());
                assertEquals(1,history.getAsJsonArray("receipts").size());
                JsonObject receipt=history.getAsJsonArray("receipts").get(0).getAsJsonObject();
                assertEquals(1,integer(receipt,"locationId"));assertFalse(receipt.has("customer"));
                assertEquals(2,integer(receipt.getAsJsonArray("lines").get(0).getAsJsonObject(),"quantity"));
                assertTrue(account(primary,localSnapshot,newAuth,"new@example.test").getAsJsonArray("receipts").isEmpty());
                assertThrows(IllegalArgumentException.class,()->account(primary,localSnapshot,UUID.randomUUID(),"customer@example.test"));
                JsonObject remote=localSnapshot.deepCopy();remote.addProperty("locationId",2);remote.getAsJsonObject("store").addProperty("name","Second store");
                execute(primary,"INSERT INTO storefront.snapshots(location_id,captured_at,payload) VALUES(2,now(),?::jsonb)",remote.toString());
                JsonObject combined=account(primary,localSnapshot,auth,"customer@example.test");
                assertEquals(2,combined.getAsJsonArray("stores").size());assertEquals(2,combined.getAsJsonArray("receipts").size());
                assertEquals(2,integer(combined.getAsJsonArray("receipts").get(1).getAsJsonObject(),"locationId"));
                primary.rollback();
                JsonObject handoff=new JsonObject(),sent=new JsonObject();JsonArray snapshots=new JsonArray();snapshots.add(remote);
                handoff.add("snapshots",snapshots);handoff.add("orders",new JsonArray());handoff.add("enrollments",new JsonArray());
                sent.add("events",rows(primary,"SELECT event_id FROM storefront.events WHERE delivered_at IS NULL LIMIT 1"));
                assertFalse(sent.getAsJsonArray("events").isEmpty());primary.commit();
                var checks=new java.util.concurrent.atomic.AtomicInteger();
                assertThrows(IllegalStateException.class,()->StorefrontRuntime.applyHandoff(primary,handoff,sent,1,()->{if(checks.incrementAndGet()==2)throw new IllegalStateException("Fenced during import");}));
                assertTrue(rows(primary,"SELECT location_id FROM storefront.snapshots WHERE location_id=2").isEmpty());
                UUID event=UUID.fromString(text(sent.getAsJsonArray("events").get(0).getAsJsonObject(),"event_id"));
                assertTrue(text(one(rows(primary,"SELECT delivered_at FROM storefront.events WHERE event_id=?",event)),"delivered_at").isBlank());primary.rollback();
                assertThrows(IllegalStateException.class,()->StorefrontRuntime.applyHandoff(primary,handoff,sent,1,()->{throw new IllegalStateException("Already fenced");}));
                StorefrontRuntime.applyHandoff(primary,handoff,sent,1,()->{});
                assertEquals(1,rows(primary,"SELECT location_id FROM storefront.snapshots WHERE location_id=2").size());
                assertFalse(text(one(rows(primary,"SELECT delivered_at FROM storefront.events WHERE event_id=?",event)),"delivered_at").isBlank());
                // Company backup must preserve the entire storefront extension, not just public POS tables.
                JsonObject recoveryCart=cart.deepCopy();UUID recoveryOrder=UUID.randomUUID();recoveryCart.addProperty("orderId",recoveryOrder.toString());
                JsonObject recoverySnapshot=snapshot(primary,1);
                recoveryCart.add("expectedTotal",quote(recoverySnapshot,recoveryCart,auth,"customer@example.test").get("total"));
                checkout(primary,recoverySnapshot,recoveryCart,auth,"customer@example.test",1);
                UUID rejectedRecovery=UUID.randomUUID();resolveCommand(primary,rejectedRecovery,1);primary.commit();
                primary.setAutoCommit(true);primary.setReadOnly(true);primary.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);primary.setAutoCommit(false);
                var recoveryData=CompanyBackupService.buildBackupData(primary);
                backup.rollback();backup.setAutoCommit(true);
                SqlScriptRunner.runSql(backup,recoveryData.sql());
                for(String table:List.of("schema_version","settings","products","customer_links","enrollments","orders","rejected_commands","reservations","snapshots","events","audit")){
                    assertTrue(recoveryData.sql().contains("\"storefront\".\""+table+"\""));
                    assertEquals(rows(primary,"SELECT * FROM storefront."+table).size(),rows(backup,"SELECT * FROM storefront."+table).size(),table);
                }
                assertEquals(2,integer(one(rows(backup,"SELECT quantity FROM storefront.reservations WHERE order_id=?",recoveryOrder)),"quantity"));
                assertEquals(text(collected,"sale_id"),text(one(rows(backup,"SELECT sale_id FROM storefront.orders WHERE order_id=?",collectedId)),"sale_id"));
                assertEquals(1,rows(backup,"SELECT order_id FROM storefront.rejected_commands WHERE order_id=?",rejectedRecovery).size());
                long oldAudit=one(rows(backup,"SELECT COALESCE(MAX(id),0) AS id FROM storefront.audit")).get("id").getAsLong();
                audit(backup,0,"RESTORE_TEST","isolated recovery test");
                assertTrue(one(rows(backup,"SELECT MAX(id) AS id FROM storefront.audit")).get("id").getAsLong()>oldAudit);
            }finally{try(Statement s=root.createStatement()){s.execute("DROP DATABASE "+a+" WITH (FORCE)");s.execute("DROP DATABASE "+b+" WITH (FORCE)");}}
        }
    }
    private static void recreateProjectsV4(Connection c)throws Exception{
        execute(c,"DROP TABLE storefront.project_media");
        execute(c,"DROP TABLE storefront.projects");
        SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/004_projects.sql"));
    }
    private static void seed(Connection c)throws SQLException{
        execute(c,"INSERT INTO locations(location_id,name,receipt_store_code,timezone) VALUES(1,'Test Deckers','T1','America/Guyana'),(2,'Backup Deckers','T2','America/Guyana') ON CONFLICT(location_id) DO NOTHING");
        execute(c,"INSERT INTO products(product_id,name,sku,price,product_type,is_active) VALUES(1001,'Test product','SF-TEST',100,'INVENTORY',true)");
        execute(c,"INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES(1001,1,5)");
        execute(c,"INSERT INTO customer_accounts(name,email,sync_uuid,sales_discount_enabled,sales_discount_percent) VALUES('Customer','customer@example.test','00000000-0000-0000-0000-000000000001',true,10)");
        execute(c,"INSERT INTO company_customization(location_id,vat_enabled,vat_use_department_rates,vat_fixed_rate_percent,round_sales_to_nearest_twenty) VALUES(1,true,false,14,false) ON CONFLICT(location_id) DO UPDATE SET vat_enabled=true,vat_fixed_rate_percent=14,round_sales_to_nearest_twenty=false");
        execute(c,"INSERT INTO storefront.settings(location_id,enabled) VALUES(1,true),(2,true)");
        execute(c,"INSERT INTO storefront.products(location_id,product_id,published) VALUES(1,1001,true)");
    }
}
