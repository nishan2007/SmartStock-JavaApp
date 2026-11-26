package services;

import com.google.gson.*;
import java.sql.*;
import static services.StorefrontService.*;

/** Published plaque variants retain their own physical size and separate design area. */
final class PlaqueCatalog {
    static void attach(Connection c,JsonArray items)throws SQLException {
        for(var entry:items){
            var item=entry.getAsJsonObject();
            if(!text(item,"name").toLowerCase(java.util.Locale.ROOT).contains("plaque"))continue;
            item.addProperty("designer","PLAQUE");
            if(item.has("variants"))continue;
            var variants=rows(c,"""
                SELECT v.custom_variant_id AS id,v.variant_name AS name,COALESCE(v.size,'') AS size,
                  CASE WHEN i.pricing_type='FIXED' THEN v.fixed_price END AS price,
                  COALESCE(v.color,'') AS color,v.template_width AS "templateWidth",
                  v.template_height AS "templateHeight",v.template_unit AS "templateUnit",
                  COALESCE(NULLIF(v.image_url,''),i.image_url,'') AS "_imageReference"
                FROM custom_order_item_variants v JOIN custom_order_items i USING(custom_item_id)
                WHERE v.custom_item_id=? AND v.is_active ORDER BY v.custom_variant_id
                """,integer(item,"id"));
            for(var value:variants){
                var variant=value.getAsJsonObject();
                var manifest=ServerImageAssetService.storefrontManifest(c,text(variant,"_imageReference"),"CUSTOM_VARIANT");
                if(manifest==null)manifest=ServerImageAssetService.storefrontManifest(c,text(variant,"_imageReference"),"CUSTOM_ITEM");
                variant.remove("_imageReference");
                if(manifest!=null)variant.add("_imageManifest",manifest);
            }
            item.add("variants",variants);
        }
    }
    static void redact(JsonObject catalog){
        for(String group:new String[]{"laserProducts","sublimationProducts"}){
            if(!catalog.has(group)||!catalog.get(group).isJsonArray())continue;
            for(var value:catalog.getAsJsonArray(group)){
                var item=value.getAsJsonObject();if(!item.has("variants"))continue;
                for(var entry:item.getAsJsonArray("variants")){
                    var variant=entry.getAsJsonObject();var manifest=variant.remove("_imageManifest");
                    variant.addProperty("image",manifest!=null);
                }
            }
        }
    }
    static JsonObject image(JsonObject snapshot,int itemId,int variantId){
        for(String group:new String[]{"laserProducts","sublimationProducts"}){
            if(!snapshot.has(group)||!snapshot.get(group).isJsonArray())continue;
            for(var value:snapshot.getAsJsonArray(group)){
                var item=value.getAsJsonObject();if(integer(item,"id")!=itemId||!item.has("variants"))continue;
                for(var entry:item.getAsJsonArray("variants")){
                    var variant=entry.getAsJsonObject();
                    if(integer(variant,"id")==variantId&&variant.has("_imageManifest"))return variant.getAsJsonObject("_imageManifest");
                }
            }
        }
        return null;
    }
}
