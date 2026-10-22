package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Private PostgreSQL history. Every read requires an active administrator. */
final class StudioBadgeRecords {
    static void requireAdmin(Connection c,int actor)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.is_active=TRUE AND UPPER(r.role_name)='ADMIN'")) {
            p.setInt(1,actor);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Sign in as an administrator to open print records.");}
        }
    }
    static JsonObject execute(Connection c,int store,int actor,JsonObject head,JsonObject request)throws Exception {
        String action=request.get("action").getAsString();
        UUID template=UUID.fromString(head.get("id").getAsString());
        if(action.equals("RECORD_SAVE")) {
            JsonObject design=JsonParser.parseString(head.get("content").getAsString()).getAsJsonObject();
            if(!design.has("savePrintRecords")||!design.get("savePrintRecords").getAsBoolean())throw new IllegalArgumentException("Record saving is disabled. Save the enabled template first.");
            JsonObject payload=request.getAsJsonObject("payload");
            if(payload==null||payload.toString().length()>12*1024*1024)throw new IllegalArgumentException("Print record exceeds 12 MB.");
            if(!Set.of("values","photos").containsAll(payload.keySet()))throw new IllegalArgumentException("Only record details and photos may be retained.");
            if(!payload.has("values")||!payload.has("photos"))throw new IllegalArgumentException("Record details and photos are required.");
            var values=payload.getAsJsonObject("values");
            if(values.size()>64||payload.getAsJsonObject("photos").size()>64)throw new IllegalArgumentException("Too many record fields.");
            for(var entry:values.entrySet())if(!entry.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,39}")||!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString()||entry.getValue().getAsString().length()>1000)throw new IllegalArgumentException("Invalid record field.");
            for(var entry:payload.getAsJsonObject("photos").entrySet())validateImage(entry.getValue().getAsJsonObject(),"image");
            UUID id=UUID.fromString(request.get("recordId").getAsString());
            try(var p=c.prepareStatement("INSERT INTO studio_badge_print_records(record_id,location_id,template_id,created_by,payload) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(record_id) DO NOTHING")) {
                p.setObject(1,id);p.setInt(2,store);p.setObject(3,template);p.setInt(4,actor);p.setString(5,payload.toString());p.executeUpdate();
            }
            JsonObject result=new JsonObject();result.addProperty("saved",true);return result;
        }
        requireAdmin(c,actor);
        JsonObject result=new JsonObject();
        if(action.equals("RECORD_LIST")) {
            int offset=request.has("offset")?request.get("offset").getAsInt():0;
            if(offset<0)throw new IllegalArgumentException("Invalid history offset.");
            JsonArray rows=new JsonArray();
            try(var p=c.prepareStatement("SELECT record_id,created_at,payload->'values' FROM studio_badge_print_records WHERE location_id=? AND template_id=? ORDER BY created_at DESC,record_id LIMIT 100 OFFSET ?")) {
                p.setInt(1,store);p.setObject(2,template);p.setInt(3,offset);try(var r=p.executeQuery()){while(r.next()){JsonObject row=new JsonObject();row.addProperty("recordId",r.getString(1));row.addProperty("createdAt",r.getString(2));row.add("values",JsonParser.parseString(r.getString(3)));rows.add(row);}}
            }
            result.add("records",rows);return result;
        }
        if(!action.equals("RECORD_GET"))throw new IllegalArgumentException("Unknown record operation.");
        try(var p=c.prepareStatement("SELECT payload FROM studio_badge_print_records WHERE location_id=? AND template_id=? AND record_id=?")) {
            p.setInt(1,store);p.setObject(2,template);p.setObject(3,UUID.fromString(request.get("recordId").getAsString()));
            try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Record not found.");return JsonParser.parseString(r.getString(1)).getAsJsonObject();}
        }
    }
    private static void validateImage(JsonObject payload,String key)throws Exception {
        byte[] bytes=Base64.getDecoder().decode(payload.get(key).getAsString());
        try(var in=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Invalid record photo.");
            var reader=readers.next();try{reader.setInput(in);if((long)reader.getWidth(0)*reader.getHeight(0)>12_000_000)throw new IllegalArgumentException("Record image exceeds 12 megapixels.");}finally{reader.dispose();}
        }
    }
}
