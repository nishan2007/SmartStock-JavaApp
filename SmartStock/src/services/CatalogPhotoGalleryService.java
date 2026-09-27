package services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered secondary photos. The existing image_url remains the primary photo. */
final class CatalogPhotoGalleryService {
    private static final Gson GSON = new Gson();
    private CatalogPhotoGalleryService() { }

    static List<String> normalize(String primary, List<String> photos) throws SQLException {
        if (photos == null) return List.of();
        if (photos.size() > 20) throw new SQLException("An item can have no more than 20 additional photos.");
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String photo : photos) {
            if (photo == null || photo.isBlank()) continue;
            String value = photo.trim();
            if (value.length() > 4000) throw new SQLException("A photo reference is too long.");
            if (!value.equals(primary)) values.add(value);
        }
        return List.copyOf(values);
    }

    static List<String> load(Connection connection, String table, String key, long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT additional_image_urls::text FROM " + table + " WHERE " + key + "=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return List.of();
                return parse(rs.getString(1));
            }
        }
    }

    static List<String> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<String> result = GSON.fromJson(json, new TypeToken<ArrayList<String>>() { }.getType());
        return result == null ? List.of() : List.copyOf(result);
    }

    static void save(Connection connection, String table, String key, long id,
                     String primary, List<String> photos) throws SQLException {
        List<String> normalized=normalize(primary,photos);
        String category=switch(table){
            case "products"->"PRODUCT";
            case "custom_order_items"->"CUSTOM_ITEM";
            case "custom_order_item_variants"->"CUSTOM_VARIANT";
            default->throw new SQLException("Unsupported catalog photo owner.");
        };
        for(String value:normalized){
            if(ImageAssetReference.isAssetReference(value)){
                try(PreparedStatement ps=connection.prepareStatement(
                        "SELECT 1 FROM image_assets WHERE asset_id=? AND category=? AND lifecycle_status<>'DELETED'")){
                    ps.setObject(1,ImageAssetReference.assetId(value));ps.setString(2,category);
                    try(ResultSet rs=ps.executeQuery()){
                        if(!rs.next())throw new SQLException("A selected photo is unavailable for this item.");
                    }
                }
            }else if(!utils.ImageCacheManager.isRemoteImageUrl(value))
                throw new SQLException("Upload each selected photo before saving the item.");
        }
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(GSON.toJson(normalized));
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + table + " SET additional_image_urls=? WHERE " + key + "=?")) {
            ps.setObject(1, json);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }
}
