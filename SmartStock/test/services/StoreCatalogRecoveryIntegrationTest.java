package services;
import data.DatabaseConfig;import com.google.gson.*;import java.sql.*;import java.util.*;
class StoreCatalogRecoveryIntegrationTest {
 static JsonObject mirrorFormat(JsonObject nativeRow){JsonObject mirrored=new JsonObject();for(var entry:nativeRow.entrySet()){if(entry.getValue().isJsonNull())mirrored.add(entry.getKey(),JsonNull.INSTANCE);else if(entry.getValue().isJsonArray() || entry.getValue().isJsonObject())mirrored.addProperty(entry.getKey(),entry.getValue().toString());else mirrored.addProperty(entry.getKey(),entry.getValue().getAsString());}return mirrored;}
 @org.junit.jupiter.api.Test
 @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="smartstock.test.catalogRecovery",matches="true")
 void repairsRosehallPlaceholderAndRollsBackOtherCollisions()throws Exception {
  var cfg=DatabaseConfig.load();try(var c=DriverManager.getConnection(cfg.jdbcUrl(),cfg.dbUser(),cfg.dbPassword())) {
   Map<String,JsonArray> plan=new LinkedHashMap<>();
   for(String table:StoreCatalogRecoveryService.TABLES){JsonArray rows=new JsonArray();try(var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(t)::text FROM public."+table+" t")){while(r.next())rows.add(mirrorFormat(JsonParser.parseString(r.getString(1)).getAsJsonObject()));}plan.put(table,table.equals("image_assets")?StoreCatalogRecoveryService.catalogImages(rows):rows);}
   try(var s=c.createStatement()) {
    s.execute("CREATE TEMP TABLE sync_cloud_state (LIKE public.sync_cloud_state INCLUDING ALL)");
    for(String table:StoreCatalogRecoveryService.TABLES)s.execute("CREATE TEMP TABLE "+table+" (LIKE public."+table+" INCLUDING ALL)");
   }
   for(JsonElement e:plan.get("products")){var row=e.getAsJsonObject();if("SMARTSTOCK-MISC".equals(row.get("sku").getAsString())){var misc=row.deepCopy(); for(String field:new java.util.ArrayList<>(misc.keySet())){JsonElement v=misc.get(field);if(v!=null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && (v.getAsString().startsWith("[") || v.getAsString().startsWith("{")))misc.add(field,JsonParser.parseString(v.getAsString()));} misc.addProperty("product_id",1);try(var p=c.prepareStatement("INSERT INTO products OVERRIDING SYSTEM VALUE SELECT * FROM jsonb_populate_record(NULL::products,?::jsonb)")){p.setString(1,misc.toString());p.executeUpdate();}}}
   StoreCatalogRecoveryService.applyPlan(c,plan);
   if(!StoreCatalogRecoveryService.completed(c))throw new Exception("Missing completion marker");
   try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM products")){r.next();if(r.getInt(1)!=plan.get("products").size())throw new Exception("Catalog incomplete");}
   try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM image_assets WHERE local_status<>'MISSING'")){r.next();if(r.getInt(1)!=0)throw new Exception("Source-local image presence copied");}
   StoreCatalogRecoveryService.applyPlan(c,plan);
   try(var s=c.createStatement()){s.execute("DELETE FROM sync_cloud_state");s.execute("UPDATE products SET name=name||' collision' WHERE product_id=(SELECT min(product_id) FROM products)");}
   try{StoreCatalogRecoveryService.applyPlan(c,plan);throw new Exception("Collision accepted");}catch(SQLException expected){if(!expected.getMessage().contains("collision"))throw expected;}
   if(StoreCatalogRecoveryService.completed(c))throw new Exception("Failed repair was marked complete");
   System.out.println("PASS: complete bootstrap, local image reset, idempotent retry, collision rollback. Only temporary tables changed.");
  }
 }
}
