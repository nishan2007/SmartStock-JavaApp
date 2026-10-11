package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;

/** Store-scoped drafts and protected attachment chunks; no stock or payment mutations. */
final class CustomOrderHeldCartService {
    static final int CHUNK=524288;
    record Summary(String id,String name,String createdAt,int items){}
    static Map<String,Object> execute(Connection c,JsonObject b,int location,int user,String device)throws Exception{
        String action=b.get("action").getAsString();
        if(action.equals("LIST")){
            List<Summary> rows=new ArrayList<>();
            try(var p=c.prepareStatement("SELECT hold_id,hold_name,created_at,jsonb_array_length(payload->'lines') FROM custom_order_held_carts WHERE location_id=? AND status='HELD' ORDER BY created_at DESC")){
                p.setInt(1,location);try(var r=p.executeQuery()){while(r.next())rows.add(new Summary(r.getString(1),r.getString(2),r.getTimestamp(3).toInstant().toString(),r.getInt(4)));}
            }return Map.of("holds",rows);
        }
        String id=UUID.fromString(b.get("id").getAsString()).toString();
        if(action.equals("CREATE")){
            String name=b.get("name").getAsString().trim();JsonObject draft=b.getAsJsonObject("draft");validate(name,draft);
            try(var p=c.prepareStatement("INSERT INTO custom_order_held_carts(hold_id,location_id,user_id,device_id,hold_name,payload) VALUES(?,?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING")){
                p.setString(1,id);p.setInt(2,location);p.setInt(3,user);p.setString(4,device);p.setString(5,name);p.setString(6,draft.toString());p.executeUpdate();
            }
        }
        JsonObject payload;String status;boolean owner;
        try(var p=c.prepareStatement("SELECT payload::text,status,user_id,device_id FROM custom_order_held_carts WHERE hold_id=? AND location_id=? FOR UPDATE")){
            p.setString(1,id);p.setInt(2,location);try(var r=p.executeQuery()){
                if(!r.next()){if(action.equals("ABANDON"))return Map.of("status","STAGING");throw new SQLException("Held cart not found in this store.");}
                payload=JsonParser.parseString(r.getString(1)).getAsJsonObject();status=r.getString(2);owner=r.getInt(3)==user&&device.equals(r.getString(4));
            }
        }
        if(Set.of("CREATE","UPLOAD","FINISH","ABANDON").contains(action)&&!owner)throw new SQLException("Only the original register and cashier may finish holding this cart.");
        if(action.equals("ABANDON")){
            if(status.equals("STAGING")){try(var p=c.prepareStatement("DELETE FROM custom_order_held_carts WHERE hold_id=?")){p.setString(1,id);p.executeUpdate();}}
            return Map.of("status",status);
        }
        if(action.equals("CREATE")){
            if(!payload.equals(b.getAsJsonObject("draft")))throw new SQLException("The hold identifier belongs to another draft.");
            if(status.equals("RESUMED"))throw new SQLException("This hold has already been resumed.");
            return Map.of("id",id,"status",status);
        }
        if(action.equals("UPLOAD")||action.equals("DOWNLOAD")){
            String file=UUID.fromString(b.get("fileId").getAsString()).toString();int chunk=b.get("chunk").getAsInt();
            JsonObject manifest=attachment(payload,file);long size=manifest.get("size").getAsLong();int expected=chunkSize(size,chunk);
            if(action.equals("UPLOAD")){
                if(!status.equals("STAGING"))throw new SQLException("This cart is no longer being held.");
                byte[] bytes=Base64.getDecoder().decode(b.get("bytes").getAsString());if(bytes.length!=expected)throw new SQLException("Invalid attachment chunk size.");
                try(var p=c.prepareStatement("INSERT INTO custom_order_held_files(hold_id,file_id,chunk_no,content) VALUES(?,?,?,?) ON CONFLICT DO NOTHING")){
                    p.setString(1,id);p.setString(2,file);p.setInt(3,chunk);p.setBytes(4,bytes);p.executeUpdate();
                }
                if(!Arrays.equals(bytes,read(c,id,file,chunk)))throw new SQLException("Attachment changed while holding the cart.");
                return Map.of("saved",true);
            }
            if(!status.equals("HELD"))throw new SQLException("This hold is unavailable or already resumed.");
            return Map.of("bytes",Base64.getEncoder().encodeToString(read(c,id,file,chunk)));
        }
        if(action.equals("FINISH")){
            if(status.equals("RESUMED"))throw new SQLException("This hold has already been resumed.");
            if(status.equals("STAGING")){
                for(var element:payload.getAsJsonArray("attachments")){
                    var f=element.getAsJsonObject();long size=f.get("size").getAsLong();var hash=MessageDigest.getInstance("SHA-256");
                    for(int n=0;(long)n*CHUNK<size;n++){byte[] bytes=read(c,id,f.get("id").getAsString(),n);if(bytes.length!=chunkSize(size,n))throw new SQLException("Incomplete attachment.");hash.update(bytes);}
                    if(!HexFormat.of().formatHex(hash.digest()).equals(f.get("sha256").getAsString()))throw new SQLException("Attachment checksum mismatch.");
                }
                try(var p=c.prepareStatement("UPDATE custom_order_held_carts SET status='HELD' WHERE hold_id=?")){p.setString(1,id);p.executeUpdate();}
            }return Map.of("id",id,"status",status);
        }
        if(!status.equals("HELD"))throw new SQLException("This hold is unavailable or already resumed.");
        if(action.equals("RESUME")){
            try(var p=c.prepareStatement("UPDATE custom_order_held_carts SET status='RESUMED',resumed_at=CURRENT_TIMESTAMP WHERE hold_id=?")){p.setString(1,id);p.executeUpdate();}
            try(var p=c.prepareStatement("DELETE FROM custom_order_held_files WHERE hold_id=?")){p.setString(1,id);p.executeUpdate();}
        }else if(!action.equals("LOAD"))throw new SQLException("Unknown hold action.");
        return Map.of("draft",payload);
    }
    static int chunkSize(long size,int n)throws SQLException{
        long start=(long)n*CHUNK;if(n<0||start>=size)throw new SQLException("Invalid attachment chunk.");return (int)Math.min(CHUNK,size-start);
    }
    private static byte[] read(Connection c,String id,String file,int chunk)throws SQLException{
        try(var p=c.prepareStatement("SELECT content FROM custom_order_held_files WHERE hold_id=? AND file_id=? AND chunk_no=?")){
            p.setString(1,id);p.setString(2,file);p.setInt(3,chunk);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Attachment is incomplete.");return r.getBytes(1);}
        }
    }
    private static JsonObject attachment(JsonObject payload,String id)throws SQLException{
        for(var e:payload.getAsJsonArray("attachments"))if(id.equals(e.getAsJsonObject().get("id").getAsString()))return e.getAsJsonObject();throw new SQLException("Attachment does not belong to this cart.");
    }
    static void validate(String name,JsonObject payload)throws SQLException{
        if(name==null||name.isBlank()||name.length()>200)throw new SQLException("Enter a hold name of up to 200 characters.");
        if(payload==null||!payload.has("lines")||!payload.get("lines").isJsonArray()||payload.getAsJsonArray("lines").isEmpty()||payload.getAsJsonArray("lines").size()>5000||payload.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1_500_000)throw new SQLException("The held custom cart is empty or too large.");
        if(!payload.has("attachments")||!payload.get("attachments").isJsonArray()||payload.getAsJsonArray("attachments").size()>100)throw new SQLException("Invalid attachment list.");
        Set<String> ids=new HashSet<>();long total=0;
        for(var e:payload.getAsJsonArray("attachments")){
            var f=e.getAsJsonObject();String id=UUID.fromString(f.get("id").getAsString()).toString();long size=f.get("size").getAsLong();String filename=f.get("name").getAsString();
            if(!ids.add(id)||size<0||size>104857600||filename.isBlank()||filename.length()>255||filename.contains("/")||filename.contains("\\")||!f.get("sha256").getAsString().matches("[a-f0-9]{64}"))throw new SQLException("Invalid attachment manifest (maximum 100 MB per file).");
            total+=size;if(total>536870912)throw new SQLException("A held cart may contain up to 512 MB of attachments.");
        }
        for(var line:payload.getAsJsonArray("lines"))for(var file:line.getAsJsonObject().getAsJsonArray("files"))if(!ids.contains(file.getAsString()))throw new SQLException("Missing attachment manifest.");
    }
}
