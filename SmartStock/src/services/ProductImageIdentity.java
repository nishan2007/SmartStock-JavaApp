package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Local image identities learned only from verified product catalogue snapshots. */
final class ProductImageIdentity {
    private static final String PREFIX="catalog-product-image-alias:";
    private ProductImageIdentity() { }

    static String existing(Connection c,JsonObject asset)throws SQLException {
        String remote=value(asset,"asset_id");UUID.fromString(remote);
        try(var p=c.prepareStatement("SELECT to_jsonb(a)::text FROM image_assets a WHERE asset_id=?::uuid OR (bucket_name=? AND object_path=?) ORDER BY asset_id FOR UPDATE")) {
            p.setString(1,remote);p.setString(2,value(asset,"bucket_name"));p.setString(3,value(asset,"object_path"));
            try(var r=p.executeQuery()) {
                String local=null;
                while(r.next()) {
                    JsonObject row=JsonParser.parseString(r.getString(1)).getAsJsonObject();
                    for(String key:List.of("bucket_name","object_path","category","access_level"))
                        if(!value(row,key).equals(value(asset,key)))throw new SQLException("Product photo identity conflicts with its storage object.","55000");
                    String a=value(row,"sha256"),b=value(asset,"sha256");
                    if(!a.isBlank()&&!b.isBlank()&&!a.equalsIgnoreCase(b))throw new SQLException("Product photo content checksum conflicts.","55000");
                    // Migration may put the same image in different providers at
                    // each store. Only a verified content identity permits reuse.
                    if(!value(row,"cloud_provider").equals(value(asset,"cloud_provider"))
                            && !(a.matches("(?i)[0-9a-f]{64}")&&a.equalsIgnoreCase(b)))
                        throw new SQLException("Product photo storage providers differ without a verified matching checksum.","55000");
                    if(Set.of("DELETED","DELETE_PENDING").contains(value(row,"lifecycle_status")))throw new SQLException("Product photo has been retired.","55000");
                    if(local!=null)throw new SQLException("Product photo identity is ambiguous.","55000");
                    local=value(row,"asset_id");
                }
                if(local!=null&&!local.equals(remote))remember(c,remote,local,asset);
                return local;
            }
        }
    }

    private static void remember(Connection c,String remote,String local,JsonObject asset)throws SQLException {
        JsonObject identity=new JsonObject();identity.addProperty("asset_id",local);
        for(String key:List.of("bucket_name","object_path"))identity.add(key,asset.get(key));
        try(var p=c.prepareStatement("INSERT INTO image_sync_state(state_key,state_value) VALUES(?,?) ON CONFLICT(state_key) DO UPDATE SET state_value=EXCLUDED.state_value WHERE image_sync_state.state_value=EXCLUDED.state_value")) {
            p.setString(1,PREFIX+remote);p.setString(2,identity.toString());
            if(p.executeUpdate()!=1)throw new SQLException("Product photo alias conflicts with a previous catalogue identity.","55000");
        }
    }

    static String reference(Connection c,String reference)throws SQLException {
        if(reference==null||!ImageAssetReference.isAssetReference(reference))return reference;
        UUID remote=ImageAssetReference.assetId(reference);
        try(var p=c.prepareStatement("SELECT a.asset_id FROM image_sync_state s JOIN image_assets a ON a.asset_id=(s.state_value::jsonb->>'asset_id')::uuid AND a.bucket_name=s.state_value::jsonb->>'bucket_name' AND a.object_path=s.state_value::jsonb->>'object_path' WHERE s.state_key=? AND a.lifecycle_status NOT IN ('DELETED','DELETE_PENDING')")) {
            p.setString(1,PREFIX+remote);try(var r=p.executeQuery()){return r.next()?ImageAssetReference.PREFIX+r.getString(1):reference;}
        }
    }

    static JsonObject photos(Connection c,JsonObject source)throws SQLException {
        JsonObject result=source.deepCopy();
        if(source.has("image_url")&&!source.get("image_url").isJsonNull())result.addProperty("image_url",reference(c,source.get("image_url").getAsString()));
        if(source.has("additional_image_urls")&&!source.get("additional_image_urls").isJsonNull()) {
            JsonElement gallery=source.get("additional_image_urls");
            JsonArray input=gallery.isJsonArray()?gallery.getAsJsonArray():JsonParser.parseString(gallery.getAsString()).getAsJsonArray();
            JsonArray output=new JsonArray();for(JsonElement entry:input)output.add(reference(c,entry.getAsString()));
            result.add("additional_image_urls",output);
        }
        return result;
    }

    private static String value(JsonObject row,String key){return row.has(key)&&!row.get(key).isJsonNull()?row.get(key).getAsString():"";}
}
