package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Private PostgreSQL history. Every read requires an active administrator. */
final class StudioBadgeRecords {
    private static final String PERMIT_NUMBER = "permitNumber";
    static final List<String> PERMIT_FIELDS = List.of("name","village","eyeColor","height","sex",
            "dateOfBirth","issueDate","expiryDate","classes","placeOfIssue");

    static void requireAdmin(Connection c,int actor)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.is_active=TRUE AND UPPER(r.role_name)='ADMIN'")) {
            p.setInt(1,actor);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Sign in as an administrator to open print records.");}
        }
    }
    static JsonObject execute(Connection c,int store,int actor,JsonObject head,JsonObject request)throws Exception {
        String action=request.get("action").getAsString();
        UUID template=UUID.fromString(head.get("id").getAsString());
        if(action.equals("RECORD_SAVE")||action.equals("RECORD_CREATE")) {
            JsonObject design=JsonParser.parseString(head.get("content").getAsString()).getAsJsonObject();
            boolean permit=isRidersPermit(design);
            if(permit!=action.equals("RECORD_CREATE"))throw new IllegalArgumentException("Open the correct badge record workflow.");
            if(permit)return createPermit(c,store,actor,template,design,request);
            if(!design.has("savePrintRecords")||!design.get("savePrintRecords").getAsBoolean())throw new IllegalArgumentException("Record saving is disabled. Save the enabled template first.");
            JsonObject payload=validatedPayload(request);
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

    static boolean isRidersPermit(JsonObject design) {
        if(design.has("name")&&"Orealla Riders Permit".equalsIgnoreCase(design.get("name").getAsString()))return true;
        if(!design.has("backElements"))return false;
        for(var item:design.getAsJsonArray("backElements")){
            var layer=item.getAsJsonObject();
            if(layer.has("type")&&"QR".equals(layer.get("type").getAsString())
                    &&layer.has("field")&&PERMIT_NUMBER.equals(layer.get("field").getAsString()))return true;
        }
        return false;
    }

    private static JsonObject validatedPayload(JsonObject request)throws Exception {
        JsonObject payload=request.getAsJsonObject("payload");
        if(payload==null||payload.toString().length()>12*1024*1024)throw new IllegalArgumentException("Print record exceeds 12 MB.");
        if(!payload.keySet().equals(Set.of("values","photos")))throw new IllegalArgumentException("Only record details and photos may be retained.");
        var values=payload.getAsJsonObject("values");
        if(values.size()>64||payload.getAsJsonObject("photos").size()>64)throw new IllegalArgumentException("Too many record fields.");
        for(var entry:values.entrySet())if(!entry.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,39}")||!entry.getValue().isJsonPrimitive()||!entry.getValue().getAsJsonPrimitive().isString()||entry.getValue().getAsString().length()>1000)throw new IllegalArgumentException("Invalid record field.");
        for(var entry:payload.getAsJsonObject("photos").entrySet())validateImage(entry.getValue().getAsJsonObject(),"image");
        return payload;
    }

    private static void validatePermitPhoto(JsonObject design,JsonObject photos) {
        String photoId=permitPhotoSlot(design).get("photoId").getAsString();
        if(!photos.keySet().equals(Set.of(photoId)))throw new IllegalArgumentException("Choose a photo for the permit template.");
        var photo=photos.getAsJsonObject(photoId);
        for(String key:List.of("zoom","offsetX","offsetY","turns")){
            var value=photo.get(key);
            if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("Invalid permit photo framing.");
        }
        double zoom=photo.get("zoom").getAsDouble();
        double x=photo.get("offsetX").getAsDouble(),y=photo.get("offsetY").getAsDouble();
        int turns=photo.get("turns").getAsInt();
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(zoom)||zoom<.1||zoom>4||turns<0||turns>3)
            throw new IllegalArgumentException("Invalid permit photo framing.");
        copyPermitPortraitEdits(photo,new JsonObject());
    }

    static void copyPermitPortraitEdits(JsonObject photo,JsonObject destination) {
        for(String color:List.of("background","outline")){
            if(photo.has(color))destination.addProperty(color,portraitNumber(photo,color,0,0xffffff));
        }
        if(!photo.has("adjustments"))return;
        if(!photo.get("adjustments").isJsonObject())throw new IllegalArgumentException("Invalid photo adjustments.");
        var adjustments=photo.getAsJsonObject("adjustments");
        var clean=new JsonObject();
        for(String key:List.of("brightness","contrast","saturation","warmth")){
            if(adjustments.has(key))clean.addProperty(key,portraitNumber(adjustments,key,-100,100));
        }
        if(!clean.keySet().equals(adjustments.keySet()))throw new IllegalArgumentException("Invalid photo adjustment.");
        destination.add("adjustments",clean);
    }

    private static int portraitNumber(JsonObject object,String key,int minimum,int maximum){
        var value=object.get(key);
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid portrait setting.");
        double number=value.getAsDouble();
        if(number!=Math.rint(number)||number<minimum||number>maximum)
            throw new IllegalArgumentException("Invalid portrait setting.");
        return (int)number;
    }

    private static JsonObject createPermit(Connection c,int store,int actor,UUID template,JsonObject design,JsonObject request)throws Exception {
        c.setAutoCommit(false);
        try {
            String number=savePermit(c,store,actor,template,design,UUID.fromString(request.get("recordId").getAsString()),validatedPayload(request));
            c.commit();
            JsonObject result=new JsonObject();result.addProperty(PERMIT_NUMBER,number);return result;
        }catch(Exception ex){
            c.rollback();throw ex;
        }finally{c.setAutoCommit(true);}
    }

    static JsonObject permitPhotoSlot(JsonObject design) {
        for(String side:List.of("elements","backElements")){
            if(!design.has(side))continue;
            for(var item:design.getAsJsonArray(side)){
                var layer=item.getAsJsonObject();
                if("PHOTO".equals(layer.get("type").getAsString()))return layer;
            }
        }
        throw new IllegalArgumentException("This permit template requires a photo field.");
    }

    /** Called inside the desktop or website request's transaction. */
    static String savePermit(Connection c,int store,int actor,UUID template,JsonObject design,UUID id,JsonObject input)throws Exception {
        var payload=input.deepCopy();
        var values=payload.getAsJsonObject("values");
        if(values.has(PERMIT_NUMBER)||!values.keySet().containsAll(PERMIT_FIELDS)
                ||PERMIT_FIELDS.stream().anyMatch(key->values.get(key).getAsString().isBlank()))
            throw new IllegalArgumentException("Complete all permit details.");
        validatePermitPhoto(design,payload.getAsJsonObject("photos"));
        if(c.getAutoCommit())throw new IllegalStateException("Permit allocation requires a transaction.");
        // Serialize allocation for this store and template, including requests from other workstations.
        try(var lock=c.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))")){
            lock.setInt(1,store);lock.setString(2,"studio-permit:"+template);lock.execute();
        }
        String number=null;
        try(var find=c.prepareStatement("SELECT payload FROM studio_badge_print_records WHERE record_id=? AND location_id=? AND template_id=? AND created_by=?")){
            find.setObject(1,id);find.setInt(2,store);find.setObject(3,template);find.setInt(4,actor);
            try(var rows=find.executeQuery()){
                if(rows.next()){
                    var saved=JsonParser.parseString(rows.getString(1)).getAsJsonObject();
                    var savedNumber=saved.getAsJsonObject("values").remove(PERMIT_NUMBER);
                    if(savedNumber==null||!saved.equals(payload))throw new IllegalArgumentException("This record has already been saved with different details.");
                    number=savedNumber.getAsString();
                }
            }
        }
        if(number==null){
            try(var next=c.prepareStatement("""
                SELECT COALESCE(MAX(substring(payload->'values'->>'permitNumber' FROM 5)::bigint),0)+1
                FROM studio_badge_print_records WHERE location_id=? AND template_id=?
                AND payload->'values'->>'permitNumber' ~ '^OSV-[0-9]{4,18}$'
                """)){
                next.setInt(1,store);next.setObject(2,template);
                try(var rows=next.executeQuery()){
                    rows.next();number=String.format(Locale.ROOT,"OSV-%04d",rows.getLong(1));
                }
            }
            values.addProperty(PERMIT_NUMBER,number);
            try(var insert=c.prepareStatement("INSERT INTO studio_badge_print_records(record_id,location_id,template_id,created_by,payload) VALUES(?,?,?,?,?::jsonb)")){
                insert.setObject(1,id);insert.setInt(2,store);insert.setObject(3,template);
                insert.setInt(4,actor);insert.setString(5,payload.toString());insert.executeUpdate();
            }
        }
        return number;
    }

    private static void validateImage(JsonObject payload,String key)throws Exception {
        byte[] bytes=Base64.getDecoder().decode(payload.get(key).getAsString());
        try(var in=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Invalid record photo.");
            var reader=readers.next();try{reader.setInput(in);if((long)reader.getWidth(0)*reader.getHeight(0)>12_000_000)throw new IllegalArgumentException("Record image exceeds 12 megapixels.");}finally{reader.dispose();}
        }
    }
}
