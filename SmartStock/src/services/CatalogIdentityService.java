package services;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Resolves distributed catalog identities without renumbering existing local records. */
final class CatalogIdentityService {
    static final Map<String,String> KEYS=Map.of("products","product_id","categories","category_id",
            "item_types","item_type_id","item_brands","brand_id","vendors","vendor_id");
    private static final DateTimeFormatter LEGACY_TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'").withZone(ZoneOffset.UTC);
    private CatalogIdentityService() { }

    static UUID legacyUid(String table,JsonObject row,UUID categoryUid)throws SQLException {
        String value;
        if("products".equals(table))
            value=table+":"+text(row,"product_id")+":"+LEGACY_TIME.format(CrossStoreProductSyncService.timestamp(row,"created_at"));
        else if("item_types".equals(table)) {
            if(categoryUid==null)throw new SQLException("Catalog type has no category identity.");
            value=table+":"+normalized(text(row,"name"))+":"+categoryUid;
        } else if("categories".equals(table)) value=table+":"+required(row,"name").toLowerCase(Locale.ROOT);
        else if("vendors".equals(table)) value=table+":"+required(row,"name");
        else if("item_brands".equals(table))value=table+":"+normalized(text(row,"name"));
        else throw new SQLException("Unsupported catalog identity.");
        try {
            String h=HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest(("SmartStock/catalog/v1/"+value).getBytes(StandardCharsets.UTF_8)));
            return UUID.fromString(h.substring(0,8)+"-"+h.substring(8,12)+"-"+h.substring(12,16)+"-"+h.substring(16,20)+"-"+h.substring(20));
        }catch(java.security.NoSuchAlgorithmException e){throw new SQLException("Catalog identity digest unavailable.",e);}
    }
    private static String required(JsonObject row,String field)throws SQLException {
        String value=text(row,field);if(value==null)throw new SQLException("Incomplete catalog identity.");return value;
    }
    private static String normalized(String value)throws SQLException {
        if(value==null)throw new SQLException("Catalog name is missing.");
        return value.trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);
    }
    private static String text(JsonObject row,String name){return CrossStoreProductSyncService.text(row,name);}

    static int localProductId(Connection c,int sourceLocation,int sourceId,int localLocation)throws SQLException {
        if(sourceLocation==localLocation)return sourceId;
        try(var p=c.prepareStatement("""
                SELECT DISTINCT p.product_id FROM catalog_source_ids m
                JOIN products p ON p.catalog_uid=m.local_uid
                WHERE m.entity_table='products' AND m.source_location_id=? AND m.source_id=?
                """)) {
            p.setInt(1,sourceLocation);p.setInt(2,sourceId);
            try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Cross-store restock is waiting for a verified product catalog mapping.");
                int id=r.getInt(1);
                if(r.next())throw new SQLException("Cross-store restock product identity is ambiguous; review the catalog mapping.");
                return id;
            }
        }
    }

    static JsonObject resolve(Connection c,String table,JsonObject source,int sourceLocation,UUID categoryUid)throws SQLException {
        String key=KEYS.get(table);if(key==null||sourceLocation<=0)throw new SQLException("Invalid catalog source.");
        JsonObject incoming=source.deepCopy();
        String sourceId=required(incoming,key);
        UUID sourceUid;
        try {sourceUid=text(source,"catalog_uid")==null?legacyUid(table,source,categoryUid):UUID.fromString(text(source,"catalog_uid"));}
        catch(IllegalArgumentException ex){throw new SQLException("Invalid source catalog identity.",ex);}
        UUID localUid=sourceUid;
        try(var p=c.prepareStatement("SELECT DISTINCT local_uid FROM catalog_source_ids WHERE entity_table=? AND source_uid=?")) {
            p.setString(1,table);p.setObject(2,sourceUid);
            try(var r=p.executeQuery()){if(r.next()){localUid=r.getObject(1,UUID.class);if(r.next())throw new SQLException("Catalog identity mapping is ambiguous.");}}
        }
        JsonObject existing=byUid(c,table,key,localUid);
        if(existing==null&&!"products".equals(table))existing=byNaturalKey(c,table,incoming);
        if(existing!=null){
            localUid=UUID.fromString(required(existing,"catalog_uid"));
            incoming.addProperty(key,required(existing,key));
        }else{
            // Serialize explicit IDs and sequence advancement with ordinary local INSERTs.
            try(var s=c.createStatement()){s.execute("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE");}
            // Recheck after obtaining the relation lock: another local writer may have won.
            existing=byUid(c,table,key,localUid);
            if(existing!=null)incoming.addProperty(key,required(existing,key));
            else if(idExists(c,table,key,sourceId))incoming.addProperty(key,allocate(c,table,key));
        }
        incoming.addProperty("catalog_uid",localUid.toString());
        try(var p=c.prepareStatement("""
                INSERT INTO catalog_source_ids(entity_table,source_location_id,source_id,source_uid,local_uid)
                VALUES(?,?,?,?::uuid,?::uuid)
                ON CONFLICT(entity_table,source_location_id,source_id,source_uid) DO UPDATE
                SET updated_at=CURRENT_TIMESTAMP
                WHERE catalog_source_ids.local_uid=EXCLUDED.local_uid
                """)){
            p.setString(1,table);p.setInt(2,sourceLocation);p.setInt(3,Integer.parseInt(sourceId));
            p.setObject(4,sourceUid);p.setObject(5,localUid);
            if(p.executeUpdate()!=1)throw new SQLException("Catalog mapping changed; import was rolled back.");
        }
        return incoming;
    }
    private static JsonObject byUid(Connection c,String table,String key,UUID uid)throws SQLException {
        try(var p=c.prepareStatement("SELECT to_jsonb(t)::text FROM "+table+" t WHERE catalog_uid=? FOR UPDATE")){
            p.setObject(1,uid);try(var r=p.executeQuery()){return r.next()?com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonObject():null;}
        }
    }
    private static JsonObject byNaturalKey(Connection c,String table,JsonObject row)throws SQLException {
        String predicate="vendors".equals(table)?"name=?":"categories".equals(table)?"lower(name)=?":"upper(regexp_replace(btrim(name),'\\s+',' ','g'))=?";
        if("item_types".equals(table))predicate+=" AND category_id=?";
        try(var p=c.prepareStatement("SELECT to_jsonb(t)::text FROM "+table+" t WHERE "+predicate+" FOR UPDATE")){
            p.setString(1,"vendors".equals(table)?required(row,"name"):
                    "categories".equals(table)?required(row,"name").toLowerCase(Locale.ROOT):normalized(text(row,"name")));
            if("item_types".equals(table))p.setInt(2,Integer.parseInt(required(row,"category_id")));
            try(var r=p.executeQuery()){
                if(!r.next())return null;JsonObject found=com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonObject();
                if(r.next())throw new SQLException("Catalog reference name is ambiguous.");
                return found;
            }
        }
    }
    private static boolean idExists(Connection c,String table,String key,String id)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM "+table+" WHERE "+key+"=?")){
            p.setInt(1,Integer.parseInt(id));try(var r=p.executeQuery()){return r.next();}
        }
    }
    private static int allocate(Connection c,String table,String key)throws SQLException {
        String sequence;
        try(var p=c.prepareStatement("SELECT pg_get_serial_sequence(?,?)")){
            p.setString(1,table);p.setString(2,key);try(var r=p.executeQuery()){r.next();sequence=r.getString(1);}
        }
        if(sequence==null){
            try(var s=c.createStatement();var r=s.executeQuery("SELECT COALESCE(max("+key+"),0)+1 FROM "+table)){r.next();return r.getInt(1);}
        }
        try(var p=c.prepareStatement("SELECT setval(?::regclass,GREATEST((SELECT last_value FROM "+sequence+
                "),COALESCE((SELECT max("+key+") FROM "+table+"),1)),true)")){
            p.setString(1,sequence);p.execute();
        }
        try(var p=c.prepareStatement("SELECT nextval(?::regclass)")){
            p.setString(1,sequence);try(var r=p.executeQuery()){r.next();return r.getInt(1);}
        }
    }
}

