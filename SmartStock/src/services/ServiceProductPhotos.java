package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import static services.StorefrontService.*;

/** Public galleries resolve only photos belonging to published items and active variants. */
final class ServiceProductPhotos {
    static final List<String> GROUPS=List.of("sublimationProducts","businessCardsProducts","largeFormatPrintingProducts","laserProducts");
    static List<JsonObject> items(JsonObject snapshot){
        List<JsonObject> result=new ArrayList<>();
        for(String group:GROUPS)if(snapshot.has(group)&&snapshot.get(group).isJsonArray())for(var value:snapshot.getAsJsonArray(group))result.add(value.getAsJsonObject());
        if(snapshot.has("customItemCategories")&&snapshot.get("customItemCategories").isJsonArray())for(var value:snapshot.getAsJsonArray("customItemCategories")){var category=value.getAsJsonObject();if(category.has("items"))for(var item:category.getAsJsonArray("items"))result.add(item.getAsJsonObject());}
        return result;
    }
    static void attach(Connection connection,JsonObject snapshot)throws SQLException {
        for(var item:items(snapshot)){
            attachPhotos(connection,item,"CUSTOM_ITEM");
            var variants=rows(connection,"""
                SELECT v.custom_variant_id AS id,v.variant_name AS name,COALESCE(v.size,'') AS size,COALESCE(v.color,'') AS color,
                  CASE WHEN i.pricing_type='FIXED' THEN v.fixed_price END AS price,
                  v.template_width AS "templateWidth",v.template_height AS "templateHeight",v.template_unit AS "templateUnit",
                  COALESCE(NULLIF(v.image_url,''),i.image_url,'') AS "_imageReference",
                  v.additional_image_urls::text AS "_galleryReferences"
                FROM custom_order_item_variants v JOIN custom_order_items i USING(custom_item_id)
                WHERE v.custom_item_id=? AND i.has_variants AND v.is_active ORDER BY v.custom_variant_id
                """,integer(item,"id"));
            for(var value:variants)attachPhotos(connection,value.getAsJsonObject(),"CUSTOM_VARIANT");
            item.add("variants",variants);
        }
    }
    private static void attachPhotos(Connection c,JsonObject row,String category)throws SQLException{
        String primary=text(row,"_imageReference");var photos=new JsonArray();Set<String> seen=new HashSet<>();
        var main=manifest(c,primary,category);if(main!=null){row.add("_imageManifest",main);photos.add(main);seen.add(text(main,"id"));}
        for(String reference:CatalogPhotoGalleryService.parse(text(row,"_galleryReferences"))){var photo=manifest(c,reference,category);if(photo!=null&&seen.add(text(photo,"id")))photos.add(photo);}
        row.remove("_imageReference");row.remove("_galleryReferences");row.add("_photoManifests",photos);
    }
    private static JsonObject manifest(Connection c,String reference,String category)throws SQLException{
        var photo=ServerImageAssetService.storefrontManifest(c,reference,category);
        return photo==null&&"CUSTOM_VARIANT".equals(category)?ServerImageAssetService.storefrontManifest(c,reference,"CUSTOM_ITEM"):photo;
    }
    static void redact(JsonObject catalog){for(var item:items(catalog)){redactRow(item);if(item.has("variants"))for(var variant:item.getAsJsonArray("variants"))redactRow(variant.getAsJsonObject());}}
    private static void redactRow(JsonObject row){
        row.remove("_imageReference");row.remove("_galleryReferences");var photos=row.remove("_photoManifests");
        var main=row.remove("_imageManifest");row.addProperty("image",main!=null);row.addProperty("photoCount",photos!=null?photos.getAsJsonArray().size():main==null?0:1);
    }
    static JsonObject image(JsonObject snapshot,int itemId){return image(snapshot,itemId,0,0);}
    static JsonObject image(JsonObject snapshot,int itemId,int variantId,int index){
        if(index<0||variantId<0)return null;
        for(var item:items(snapshot))if(integer(item,"id")==itemId){
            if(variantId==0)return photo(item,index);
            if(item.has("variants"))for(var variant:item.getAsJsonArray("variants"))if(integer(variant.getAsJsonObject(),"id")==variantId)return photo(variant.getAsJsonObject(),index);
        }
        return null;
    }
    private static JsonObject photo(JsonObject row,int index){if(row.has("_photoManifests")){var photos=row.getAsJsonArray("_photoManifests");return index<photos.size()?photos.get(index).getAsJsonObject():null;}return index==0&&row.has("_imageManifest")?row.getAsJsonObject("_imageManifest"):null;}
    static List<JsonObject> manifests(JsonObject snapshot){
        List<JsonObject> result=new ArrayList<>();for(var item:items(snapshot)){collect(item,result);if(item.has("variants"))for(var value:item.getAsJsonArray("variants"))collect(value.getAsJsonObject(),result);}return result;
    }
    private static void collect(JsonObject row,List<JsonObject> out){if(row.has("_photoManifests"))for(var value:row.getAsJsonArray("_photoManifests"))out.add(value.getAsJsonObject());else if(row.has("_imageManifest"))out.add(row.getAsJsonObject("_imageManifest"));}
}
