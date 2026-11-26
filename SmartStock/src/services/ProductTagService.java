package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Caller supplies the authenticated EDIT_ITEM permission and transaction. */
final class ProductTagService {
    static final String PUBLIC_TAGS_SQL="COALESCE((SELECT jsonb_agg(td.name ORDER BY lower(td.name)) FROM product_tags pt JOIN product_tag_definitions td USING(tag_id) WHERE pt.product_id=p.product_id AND td.is_public),'[]'::jsonb)";
    private ProductTagService() { }

    static String cleanName(String value) {
        String name=value==null?"":value.strip().replaceAll("\\s+"," ");
        if(name.isEmpty()||name.length()>80||name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Enter a tag name of 1–80 characters.");
        return name;
    }

    static JsonObject state(Connection c) throws SQLException {
        JsonObject result=new JsonObject();
        result.add("tags",StorefrontService.rows(c,"""
            SELECT d.tag_id AS id,d.name,d.is_public AS "isPublic",count(pt.product_id) AS "productCount"
            FROM product_tag_definitions d LEFT JOIN product_tags pt USING(tag_id)
            GROUP BY d.tag_id ORDER BY lower(d.name),d.tag_id
            """));
        result.add("assignments",StorefrontService.rows(c,"SELECT product_id AS \"productId\",tag_id AS \"tagId\" FROM product_tags"));
        return result;
    }

    static Map<String,Object> mutate(Connection c,JsonObject body,UUID device,int user,int location) throws Exception {
        // Also serializes bulk assignment with rename/merge, including unused tag creation.
        try(var lock=c.createStatement()){lock.execute("SELECT pg_advisory_xact_lock(73490,1)");}
        String action=StorefrontService.text(body,"action");
        Set<Integer> affected=new TreeSet<>();
        UUID tag=null;
        if(action.equals("CREATE")) {
            String name=cleanName(StorefrontService.text(body,"name"));
            try(var p=c.prepareStatement("INSERT INTO product_tag_definitions(name) VALUES(?) ON CONFLICT (lower(btrim(name))) DO UPDATE SET name=product_tag_definitions.name RETURNING tag_id")) {
                p.setString(1,name);try(var r=p.executeQuery()){r.next();tag=r.getObject(1,UUID.class);}
            }
        } else {
            tag=UUID.fromString(StorefrontService.text(body,"tagId"));
            requireTag(c,tag);
        }
        switch(action) {
            case "CREATE" -> { }
            case "SAVE" -> {
                String name=cleanName(StorefrontService.text(body,"name"));
                affected.addAll(products(c,tag));
                StorefrontService.execute(c,"UPDATE product_tag_definitions SET name=?,is_public=?,updated_at=CURRENT_TIMESTAMP WHERE tag_id=?",
                    name,body.has("isPublic")&&body.get("isPublic").getAsBoolean(),tag);
            }
            case "ADD","REMOVE" -> {
                JsonArray ids=body.getAsJsonArray("productIds");
                if(ids==null||ids.isEmpty()||ids.size()>1000)throw new IllegalArgumentException("Select between 1 and 1,000 products.");
                for(JsonElement id:ids){int value=new java.math.BigDecimal(id.getAsString()).intValueExact();if(value<=0)throw new IllegalArgumentException("Select a valid product.");affected.add(value);}
                for(int id:affected) {
                    try(var p=c.prepareStatement("SELECT product_id FROM products WHERE product_id=? FOR UPDATE")) {
                        p.setInt(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("A selected product no longer exists.");}
                    }
                    StorefrontService.execute(c,action.equals("ADD")?
                        "INSERT INTO product_tags(product_id,tag_id) VALUES(?,?) ON CONFLICT DO NOTHING":
                        "DELETE FROM product_tags WHERE product_id=? AND tag_id=?",id,tag);
                }
            }
            case "MERGE" -> {
                UUID target=UUID.fromString(StorefrontService.text(body,"targetId"));requireTag(c,target);
                if(tag.equals(target))throw new IllegalArgumentException("Choose a different destination tag.");
                affected.addAll(products(c,tag));
                // Destination visibility wins; merging into an Internal tag never publishes it.
                StorefrontService.execute(c,"INSERT INTO product_tags(product_id,tag_id) SELECT product_id,? FROM product_tags WHERE tag_id=? ON CONFLICT DO NOTHING",target,tag);
                StorefrontService.execute(c,"DELETE FROM product_tag_definitions WHERE tag_id=?",tag);
            }
            default -> throw new IllegalArgumentException("Unknown tag action.");
        }
        for(int id:affected) {
            StorefrontService.execute(c,"UPDATE products SET updated_at=CURRENT_TIMESTAMP WHERE product_id=?",id);
            SyncOutboxService.recordEvent(c,"PRODUCT_UPDATED",Map.of("product_id",id,"location_id",location,"user_id",user),location,device.toString(),user);
        }
        LanProductAdminService.audit(c,"LAN_PRODUCT_TAG_"+action,device,user,"tag_id="+tag+"; products="+affected.size());
        return Map.of("tagId",tag.toString(),"changedProducts",affected.size());
    }

    private static void requireTag(Connection c,UUID id)throws SQLException {
        try(var p=c.prepareStatement("SELECT tag_id FROM product_tag_definitions WHERE tag_id=? FOR UPDATE")) {
            p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("This tag no longer exists. Refresh the tag list.");}
        }
    }
    private static Set<Integer> products(Connection c,UUID id)throws SQLException {
        Set<Integer> ids=new TreeSet<>();try(var p=c.prepareStatement("SELECT product_id FROM product_tags WHERE tag_id=?")) {
            p.setObject(1,id);try(var r=p.executeQuery()){while(r.next())ids.add(r.getInt(1));}
        }return ids;
    }
}
