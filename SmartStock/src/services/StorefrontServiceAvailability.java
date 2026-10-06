package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import static services.StorefrontService.*;

/** Store-owned availability for editorial services; absent rows mean available. */
final class StorefrontServiceAvailability {
    private StorefrontServiceAvailability() { }
    static JsonArray unavailable(Connection c,int location)throws SQLException{
        JsonArray result=new JsonArray();
        for(var item:rows(c,"SELECT slug FROM storefront.service_availability WHERE location_id=? AND NOT available ORDER BY slug",location)){
            String slug=text(item.getAsJsonObject(),"slug");if(StorefrontServicePage.has(slug))result.add(slug);
        }
        return result;
    }
    static JsonArray staff(Connection c,int location)throws SQLException{
        var unavailable=new java.util.HashSet<String>();
        for(var item:unavailable(c,location))unavailable.add(item.getAsString());
        JsonArray result=new JsonArray();var slugs=new ArrayList<>(StorefrontServicePage.slugs());slugs.sort(Comparator.naturalOrder());
        for(String slug:slugs){JsonObject row=new JsonObject();row.addProperty("slug",slug);row.addProperty("name",StorefrontServicePage.name(slug));row.addProperty("available",!unavailable.contains(slug));result.add(row);}
        return result;
    }
    static void set(Connection c,int location,String slug,boolean available)throws SQLException{
        if(!StorefrontServicePage.has(slug))throw new IllegalArgumentException("Choose a supported service.");
        execute(c,"""
            INSERT INTO storefront.service_availability(location_id,slug,available) VALUES(?,?,?)
            ON CONFLICT(location_id,slug) DO UPDATE SET available=EXCLUDED.available,updated_at=now()
            """,location,slug,available);
    }
    static void requireAvailable(Connection c,int location,String topic)throws SQLException{
        String slug=StorefrontServicePage.slugForName(topic);
        requireSlugAvailable(c,location,slug);
    }
    static void requireSlugAvailable(Connection c,int location,String slug)throws SQLException{
        if(slug.isBlank())return;
        if(!StorefrontServicePage.has(slug))throw new IllegalArgumentException("Choose a supported service.");
        if(!rows(c,"SELECT 1 FROM storefront.service_availability WHERE location_id=? AND slug=? AND NOT available",location,slug).isEmpty())
            throw new IllegalArgumentException("This service is not currently available at the selected store. Choose another store or contact the team.");
    }
}
