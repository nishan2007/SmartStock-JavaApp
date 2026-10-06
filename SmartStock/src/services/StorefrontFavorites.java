package services;

import com.google.gson.JsonArray;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import static services.StorefrontService.*;

/** Private customer product bookmarks at one store. */
final class StorefrontFavorites {
    private StorefrontFavorites() { }
    static JsonArray list(Connection c,int location,UUID auth)throws SQLException{
        return rows(c,"""
            SELECT f.product_id AS id FROM storefront.favorites f
            JOIN storefront.products w ON w.location_id=f.location_id AND w.product_id=f.product_id
            JOIN products p ON p.product_id=f.product_id
            WHERE f.location_id=? AND f.auth_id=? AND w.published AND p.is_active AND p.product_type='INVENTORY'
            ORDER BY f.created_at DESC,f.product_id LIMIT 200
            """,location,auth);
    }
    static JsonArray set(Connection c,int location,UUID auth,int product,boolean saved)throws SQLException{
        if(product<=0)throw new IllegalArgumentException("Choose a product.");
        if(saved){
            if(rows(c,"""
                SELECT 1 FROM storefront.products w JOIN products p ON p.product_id=w.product_id
                WHERE w.location_id=? AND w.product_id=? AND w.published AND p.is_active AND p.product_type='INVENTORY'
                """,location,product).isEmpty())throw new IllegalArgumentException("This product is no longer available at the selected store.");
            if(!rows(c,"SELECT 1 FROM storefront.favorites WHERE location_id=? AND auth_id=? AND product_id=?",location,auth,product).isEmpty())return list(c,location,auth);
            int count=integer(one(rows(c,"SELECT count(*) AS count FROM storefront.favorites WHERE location_id=? AND auth_id=?",location,auth)),"count");
            if(count>=200)throw new IllegalArgumentException("Remove a saved product before adding another.");
            execute(c,"INSERT INTO storefront.favorites(location_id,auth_id,product_id) VALUES(?,?,?) ON CONFLICT DO NOTHING",location,auth,product);
        }else execute(c,"DELETE FROM storefront.favorites WHERE location_id=? AND auth_id=? AND product_id=?",location,auth,product);
        return list(c,location,auth);
    }
}
