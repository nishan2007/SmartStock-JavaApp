package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static services.StorefrontService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomItemCategoriesTest {
    @Test void sharedChoicesKeepTheirIdentityAndClearLegacyFlags(){
        var categories=JsonParser.parseString("[{\"id\":\"a\",\"name\":\"Stickers\"}]").getAsJsonArray();
        JsonObject item=new JsonObject();item.addProperty("isShirt",true);
        Object choice=CustomItemCategories.choices(categories)[6];CustomItemCategories.apply(choice,item);
        assertFalse(item.get("isShirt").getAsBoolean());assertEquals(choice,CustomItemCategories.choice(item,categories));
        CustomItemCategories.apply(CustomItemCategory.LASER,item);assertEquals("",item.get("customCategoryId").getAsString());assertEquals(CustomItemCategory.LASER,CustomItemCategories.choice(item,categories));
    }
    @Test void categoryRenameAssignmentAndWebsitePublicationShareOneCatalog()throws Exception {
        String admin=System.getProperty("storefront.test.admin","");assumeTrue(!admin.isBlank());
        assertEquals("jdbc:postgresql://127.0.0.1:55439/postgres",admin);
        String db="smartstock_dev_categories_"+UUID.randomUUID().toString().replace("-","");
        try(Connection root=DriverManager.getConnection(admin,"storefront_test","")){
            execute(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(admin.replace("/postgres","/"+db),"storefront_test","")){
                SchemaContractService.installLocalBaseline(c);
                // Exercise upgrade from an installed v16 database and repeat installation.
                execute(c,"DROP TABLE storefront.custom_item_category_assignments");execute(c,"DROP TABLE storefront.custom_item_categories");execute(c,"DELETE FROM storefront.schema_version WHERE version=17");
                StorefrontSchema.ensure(c);StorefrontSchema.ensure(c);
                UUID id=UUID.randomUUID();JsonObject body=new JsonObject();body.addProperty("id",id.toString());body.addProperty("revision",0);body.addProperty("name","Stickers");CustomItemCategories.save(c,body);
                JsonObject duplicate=body.deepCopy();duplicate.addProperty("id",UUID.randomUUID().toString());duplicate.addProperty("name"," stickers ");assertThrows(IllegalArgumentException.class,()->CustomItemCategories.save(c,duplicate));
                duplicate.addProperty("name","Shirts");assertThrows(IllegalArgumentException.class,()->CustomItemCategories.save(c,duplicate));
                execute(c,"INSERT INTO custom_order_items(custom_item_id,item_name,product_type,pricing_type,is_active,is_archived) VALUES(900001,'Round sticker','INVENTORY','VARIABLE',true,false)");
                CustomItemCategories.validateAssignment(c,id.toString(),false,false,false,false,false);assertThrows(IllegalArgumentException.class,()->CustomItemCategories.validateAssignment(c,id.toString(),true));
                CustomItemCategories.assign(c,900001,id.toString());
                assertEquals(1,CustomItemCategories.catalog(c).get(0).getAsJsonObject().getAsJsonArray("items").size());
                var state=LanCustomOrderCatalogAdminService.load(c);assertEquals(id.toString(),state.items().stream().filter(i->i.itemId()==900001).findFirst().orElseThrow().customCategoryId());assertEquals(1,state.customCategories().size());
                var source=state.items().stream().filter(i->i.itemId()==900001).findFirst().orElseThrow();
                var itemBody=new Gson().toJsonTree(source).getAsJsonObject();itemBody.addProperty("name","Round label");itemBody.addProperty("itemType","Labels");itemBody.addProperty("brand","Deckers");
                var mutation=new JsonObject();mutation.add("item",itemBody);LanCustomOrderCatalogAdminService.mutate(c,"SAVE_ITEM",mutation);
                assertEquals("Round label",text(CustomItemCategories.catalog(c).get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject(),"name"));
                String recovery=CompanyBackupService.buildBackupData(c).sql();assertTrue(recovery.contains("\"storefront\".\"custom_item_categories\""));assertTrue(recovery.contains("\"storefront\".\"custom_item_category_assignments\""));
                body.addProperty("revision",1);body.addProperty("name","Labels");CustomItemCategories.save(c,body);
                assertEquals("Labels",text(CustomItemCategories.catalog(c).get(0).getAsJsonObject(),"name"));assertThrows(IllegalArgumentException.class,()->CustomItemCategories.save(c,body));
                execute(c,"UPDATE custom_order_items SET is_archived=true WHERE custom_item_id=900001");assertEquals(0,CustomItemCategories.catalog(c).get(0).getAsJsonObject().getAsJsonArray("items").size());
                CustomItemCategories.assign(c,900001,"");assertTrue(rows(c,"SELECT 1 FROM storefront.custom_item_category_assignments").isEmpty());
            }finally{execute(root,"DROP DATABASE "+db+" WITH (FORCE)");}
        }
    }
}
