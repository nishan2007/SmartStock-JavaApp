package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Base64;
import static services.StorefrontService.*;

/** Private, store-owned custom quote requests. No request data enters public snapshots. */
final class StorefrontQuoteRequests {
    private StorefrontQuoteRequests() { }

    static JsonObject submit(Connection c,int location,UUID auth,String email,JsonObject body)throws SQLException {
        UUID id=UUID.fromString(text(body,"requestId"));
        UUID sourceRequest=null;
        JsonObject source=new JsonObject();
        if(!text(body,"sourceRequestId").isBlank()){
            sourceRequest=UUID.fromString(text(body,"sourceRequestId"));
            source=source(c,location,auth,sourceRequest);
        }
        String topic=field(body,"topic",150,true),description=field(body,"description",2000,true),
            size=field(body,"size",120,false),color=field(body,"color",120,false),
            material=field(body,"material",120,false),phone=field(body,"contactPhone",60,false);
        description=withModelUnits(description,field(body,"modelUnits",2,false));
        description=withPrintQuality(description,field(body,"printQuality",8,false));
        int quantity=integer(body,"quantity");if(quantity<1||quantity>100000)throw new IllegalArgumentException("Choose a quantity between 1 and 100,000.");
        String desired=field(body,"desiredDate",10,false);LocalDate date=null;
        if(!desired.isBlank()){
            try{date=LocalDate.parse(desired);}catch(Exception e){throw new IllegalArgumentException("Enter a valid desired date.");}
            if(date.isBefore(LocalDate.now())||date.isAfter(LocalDate.now().plusYears(2)))throw new IllegalArgumentException("Choose a date within the next two years.");
        }
        UUID project=null;JsonObject context=source.has("projectContext")&&source.get("projectContext").isJsonObject()
            ?source.getAsJsonObject("projectContext").deepCopy():new JsonObject();
        if(!text(body,"projectId").isBlank()){
            project=UUID.fromString(text(body,"projectId"));
            JsonObject projectSource=one(rows(c,"""
                SELECT title,category,materials,production_method AS "productionMethod",service_slug AS "serviceSlug",customization
                FROM storefront.projects WHERE project_id=? AND location_id=? AND status IN ('PUBLISHED','FEATURED')
                """,project,location));
            if(projectSource.entrySet().isEmpty())throw new IllegalArgumentException("This project is no longer available. Choose another project.");
            context=projectSource;
        }
        String hash=LanSecurity.sha256(location+"|"+auth+"|"+(sourceRequest==null?"":sourceRequest+"|")+project+"|"+topic+"|"+description+"|"+quantity+"|"+size+"|"+color+"|"+material+"|"+phone+"|"+desired);
        JsonObject old=one(rows(c,"SELECT request_id,auth_id,location_id,request_hash,status,created_at FROM storefront.quote_requests WHERE request_id=? FOR UPDATE",id));
        if(!old.entrySet().isEmpty()){
            if(!auth.toString().equals(text(old,"auth_id"))||location!=integer(old,"location_id")||!hash.equals(text(old,"request_hash")))
                throw new IllegalArgumentException("This quote request identifier was already used for another request.");
            return response(old);
        }
        StorefrontServiceAvailability.requireAvailable(c,location,topic);
        StorefrontServiceAvailability.requireSlugAvailable(c,location,text(context,"serviceSlug"));
        execute(c,"""
            INSERT INTO storefront.quote_requests(request_id,location_id,auth_id,email,project_id,project_context,topic,
                description,quantity,size,color,material,contact_phone,desired_date,request_hash)
            VALUES(?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?)
            """,id,location,auth,email,project,context.toString(),topic,description,quantity,size,color,material,phone,date,hash);
        return response(one(rows(c,"SELECT request_id,status,created_at FROM storefront.quote_requests WHERE request_id=?",id)));
    }
    static JsonObject template(Connection c,int location,UUID auth,UUID request)throws SQLException{
        JsonObject row=source(c,location,auth,request);
        JsonObject out=new JsonObject();
        for(String field:java.util.List.of("requestId","topic","description","quantity","size","color","material","contactPhone","projectContext"))
            if(row.has(field))out.add(field,row.get(field).deepCopy());
        return out;
    }
    private static JsonObject source(Connection c,int location,UUID auth,UUID request)throws SQLException{
        JsonObject row=one(rows(c,"""
            SELECT request_id AS "requestId",topic,description,quantity,size,color,material,
                   contact_phone AS "contactPhone",project_context AS "projectContext"
            FROM storefront.quote_requests WHERE request_id=? AND location_id=? AND auth_id=?
            """,request,location,auth));
        if(row.entrySet().isEmpty())throw new IllegalArgumentException("This previous request is unavailable.");
        return row;
    }
    static String withPrintQuality(String description,String quality){
        if(quality.isBlank())return description;
        String label=switch(quality){case "STANDARD"->"Standard";case "DETAIL"->"Prioritize fine detail";
            default->throw new IllegalArgumentException("Choose a valid print quality preference.");};
        String result=description+"\nPrint quality preference: "+label;
        if(result.length()>2000)throw new IllegalArgumentException("Shorten your description to include the print quality preference.");
        return result;
    }
    static String withModelUnits(String description,String units){
        if(units.isBlank())return description;
        String label=switch(units){case "MM"->"millimeters";case "CM"->"centimeters";case "IN"->"inches";
            default->throw new IllegalArgumentException("Choose valid units for the 3D model.");};
        String result=description+"\nModel units: "+label;
        if(result.length()>2000)throw new IllegalArgumentException("Shorten your description to include the model units.");
        return result;
    }
    private static JsonObject response(JsonObject row){
        JsonObject result=new JsonObject();result.addProperty("requestId",text(row,"request_id"));
        result.addProperty("status",text(row,"status"));result.addProperty("createdAt",text(row,"created_at"));return result;
    }
    static JsonArray customer(Connection c,UUID auth)throws SQLException{
        return withOrderStatus(rows(c,"""
            SELECT q.request_id AS "requestId",q.location_id AS "locationId",q.topic,q.status,
                q.project_context AS "projectContext",q.quantity,q.created_at AS "createdAt",q.updated_at AS "updatedAt",
                o.order_number AS "orderNumber",o.status AS "nativeStatus"
            FROM storefront.quote_requests q LEFT JOIN storefront.quote_order_links l ON l.request_id=q.request_id
            LEFT JOIN custom_orders o ON o.custom_order_id=l.custom_order_id
            WHERE q.auth_id=? ORDER BY q.created_at DESC LIMIT 100
            """,auth));
    }
    static JsonObject upload(Connection c,int location,UUID auth,JsonObject body)throws Exception{
        UUID request=UUID.fromString(text(body,"requestId")),fileId=UUID.fromString(text(body,"fileId"));
        if(rows(c,"SELECT 1 FROM storefront.quote_requests WHERE request_id=? AND auth_id=? AND location_id=? AND status NOT IN ('COMPLETED','CANCELLED') FOR UPDATE",request,auth,location).isEmpty())
            throw new IllegalArgumentException("This custom request is unavailable for uploads.");
        String filename=field(body,"filename",180,true).replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);
        if(filename.isBlank()||filename.contains("..")||!filename.matches("[A-Za-z0-9 _().-]+"))throw new IllegalArgumentException("Choose a file with a simple name.");
        String extension=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT);
        if(!java.util.Set.of("jpg","jpeg","png","pdf","stl","obj","3mf").contains(extension))
            throw new IllegalArgumentException("Upload JPEG, PNG, PDF, STL, OBJ, or 3MF files.");
        String encoded=text(body,"bytesBase64");if(encoded.length()>5_600_000)throw new IllegalArgumentException("Each file must be 4 MB or smaller.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IllegalArgumentException("The file is invalid.");}
        if(bytes.length==0||bytes.length>4_194_304)throw new IllegalArgumentException("Each file must be 4 MB or smaller.");
        String type=switch(extension){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "pdf"->"application/pdf";default->"application/octet-stream";};
        if(type.equals("image/jpeg")&&!(bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)
                ||type.equals("image/png")&&!(bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71)
                ||type.equals("application/pdf")&&!(bytes.length>5&&bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F'&&bytes[4]=='-')
                ||extension.equals("3mf")&&!(bytes.length>4&&bytes[0]=='P'&&bytes[1]=='K'))
            throw new IllegalArgumentException("The file does not match its format.");
        if(extension.equals("3mf"))validate3mf(bytes);
        String sha;
        try{sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
        JsonObject existing=one(rows(c,"SELECT original_filename,byte_size,sha256 FROM storefront.quote_files WHERE file_id=?",fileId));
        if(!existing.entrySet().isEmpty()){
            if(!filename.equals(text(existing,"original_filename"))||bytes.length!=integer(existing,"byte_size")||!sha.equals(text(existing,"sha256")))
                throw new IllegalArgumentException("This file identifier was already used for different content.");
            JsonObject result=new JsonObject();result.addProperty("fileId",fileId.toString());result.addProperty("saved",true);return result;
        }
        if(integer(one(rows(c,"SELECT COUNT(*) AS count FROM storefront.quote_files WHERE request_id=?",request)),"count")>=5)
            throw new IllegalArgumentException("A request can include at most five files.");
        String reference=ServerImageAssetService.storeUpload(c,"QUOTE_ARTWORK","deckers-creative",
            "requests/"+request+"/"+fileId+"."+extension,type,filename,"AUTHENTICATED",bytes);
        execute(c,"""
            INSERT INTO storefront.quote_files(file_id,request_id,asset_reference,original_filename,content_type,byte_size,sha256)
            VALUES(?,?,?,?,?,?,?)
            """,fileId,request,reference,filename,type,bytes.length,sha);
        JsonObject result=new JsonObject();result.addProperty("fileId",fileId.toString());result.addProperty("saved",true);return result;
    }
    static void validate3mf(byte[] bytes){
        boolean model=false,relationship=false;int entries=0;long expanded=0;
        try(var archive=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(bytes))){
            java.util.zip.ZipEntry entry;byte[] chunk=new byte[8192];
            while((entry=archive.getNextEntry())!=null){
                if(++entries>128)throw new IllegalArgumentException("This 3MF contains too many files.");
                String name=entry.getName();
                if(name.startsWith("/")||name.contains("..")||name.contains("\\"))throw new IllegalArgumentException("This 3MF has an unsafe file path.");
                if(name.equals("_rels/.rels"))relationship=true;
                if(name.toLowerCase(java.util.Locale.ROOT).endsWith(".model"))model=true;
                int count;while((count=archive.read(chunk))!=-1){expanded+=count;
                    if(expanded>32L*1024*1024)throw new IllegalArgumentException("This 3MF expands beyond the safe upload limit.");}
                archive.closeEntry();
            }
        }catch(java.io.IOException e){throw new IllegalArgumentException("This 3MF archive could not be read.",e);}
        if(!relationship||!model)throw new IllegalArgumentException("Choose a 3MF file with a model and package relationship.");
    }
    static JsonArray files(Connection c,int location,UUID request)throws SQLException{
        return rows(c,"""
            SELECT f.file_id AS "fileId",f.original_filename AS filename,f.content_type AS "contentType",f.byte_size AS "byteSize"
            FROM storefront.quote_files f JOIN storefront.quote_requests q ON q.request_id=f.request_id
            WHERE q.location_id=? AND f.request_id=? ORDER BY f.created_at
            """,location,request);
    }
    static JsonObject staffFile(Connection c,int location,UUID fileId)throws Exception{
        JsonObject row=one(rows(c,"""
            SELECT f.asset_reference,f.original_filename,f.content_type
            FROM storefront.quote_files f JOIN storefront.quote_requests q ON q.request_id=f.request_id
            WHERE q.location_id=? AND f.file_id=?
            """,location,fileId));
        if(row.entrySet().isEmpty())throw new IllegalArgumentException("File not found at this store.");
        var data=ServerImageAssetService.load(c,text(row,"asset_reference"));
        JsonObject result=new JsonObject();result.addProperty("filename",text(row,"original_filename"));
        result.addProperty("contentType",text(row,"content_type"));
        result.addProperty("bytesBase64",Base64.getEncoder().encodeToString(data.bytes()));return result;
    }
    static JsonArray staff(Connection c,int location)throws SQLException{
        return withOrderStatus(rows(c,"""
            SELECT q.request_id AS "requestId",q.email,q.topic,q.description,q.quantity,q.size,q.color,q.material,
                q.contact_phone AS "contactPhone",q.desired_date AS "desiredDate",q.project_context AS "projectContext",
                q.status,q.created_at AS "createdAt",q.updated_at AS "updatedAt",
                o.order_number AS "orderNumber",o.status AS "nativeStatus"
            FROM storefront.quote_requests q LEFT JOIN storefront.quote_order_links l ON l.request_id=q.request_id
            LEFT JOIN custom_orders o ON o.custom_order_id=l.custom_order_id
            WHERE q.location_id=? ORDER BY q.created_at DESC LIMIT 300
            """,location));
    }
    static JsonObject linkOrder(Connection c,int location,int staff,JsonObject body)throws SQLException{
        UUID request=UUID.fromString(text(body,"requestId"));
        long order;try{order=body.get("customOrderId").getAsBigDecimal().longValueExact();}catch(Exception e){throw new IllegalArgumentException("Enter a valid SmartStock custom order ID.");}
        if(order<1)throw new IllegalArgumentException("Enter a valid SmartStock custom order ID.");
        JsonObject quote=one(rows(c,"SELECT auth_id,status FROM storefront.quote_requests WHERE request_id=? AND location_id=? FOR UPDATE",request,location));
        if(quote.entrySet().isEmpty()||java.util.Set.of("CANCELLED","COMPLETED").contains(text(quote,"status")))
            throw new IllegalArgumentException("Choose an open request at this store.");
        JsonObject latestProof=one(rows(c,"SELECT status FROM storefront.quote_proofs WHERE request_id=? ORDER BY revision DESC LIMIT 1",request));
        if(!latestProof.entrySet().isEmpty()&&!"APPROVED".equals(text(latestProof,"status")))
            throw new IllegalArgumentException("The customer must approve the latest design proof before linking a production order.");
        JsonObject nativeOrder=one(rows(c,"""
            SELECT o.custom_order_id,o.order_number,o.status
            FROM custom_orders o JOIN customer_accounts a ON a.customer_id=o.customer_id
            JOIN storefront.customer_links l ON l.customer_uuid=a.sync_uuid AND l.location_id=o.location_id
            WHERE o.custom_order_id=? AND o.location_id=? AND l.auth_id=? FOR UPDATE OF o
            """,order,location,UUID.fromString(text(quote,"auth_id"))));
        if(nativeOrder.entrySet().isEmpty()||!java.util.Set.of("NEW","ASSIGNED","IN_PROGRESS","READY").contains(text(nativeOrder,"status")))
            throw new IllegalArgumentException("The SmartStock order must belong to this request's linked customer and store.");
        JsonObject existing=one(rows(c,"SELECT custom_order_id FROM storefront.quote_order_links WHERE request_id=?",request));
        if(!existing.entrySet().isEmpty()){
            if(existing.get("custom_order_id").getAsLong()!=order)throw new IllegalArgumentException("This request already belongs to another SmartStock order.");
        }else execute(c,"INSERT INTO storefront.quote_order_links(request_id,custom_order_id,linked_by) VALUES(?,?,?)",request,order,staff);
        JsonObject result=new JsonObject();result.addProperty("requestId",request.toString());result.addProperty("orderNumber",text(nativeOrder,"order_number"));
        result.addProperty("status",customerStatus(text(nativeOrder,"status"),text(quote,"status")));return result;
    }
    private static JsonArray withOrderStatus(JsonArray rows){
        for(var item:rows){JsonObject row=item.getAsJsonObject();String nativeStatus=text(row,"nativeStatus");row.remove("nativeStatus");
            if(!nativeStatus.isBlank())row.addProperty("status",customerStatus(nativeStatus,text(row,"status")));
        }return rows;
    }
    private static String customerStatus(String nativeStatus,String fallback){
        return switch(nativeStatus){case "NEW"->"APPROVED".equals(fallback)?"APPROVED":"REVIEWING";
            case "ASSIGNED"->"REVIEWING";case "IN_PROGRESS"->"IN_PRODUCTION";
            case "READY"->"READY";case "COMPLETED","DELIVERED"->"COMPLETED";case "CANCELLED"->"CANCELLED";default->fallback;};
    }
    static void transition(Connection c,int location,JsonObject body)throws SQLException{
        String next=text(body,"status");
        if(!java.util.Set.of("REQUESTED","REVIEWING","AWAITING_ARTWORK","QUOTED","IN_PRODUCTION","READY","COMPLETED","CANCELLED").contains(next))
            throw new IllegalArgumentException("Choose a valid quote status.");
        UUID id=UUID.fromString(text(body,"requestId"));
        if(rows(c,"SELECT request_id FROM storefront.quote_requests WHERE request_id=? AND location_id=? FOR UPDATE",id,location).isEmpty())
            throw new IllegalArgumentException("Quote request not found at this store.");
        if(!rows(c,"SELECT 1 FROM storefront.quote_order_links WHERE request_id=?",id).isEmpty())
            throw new IllegalArgumentException("Update the linked SmartStock custom order instead.");
        if(java.util.Set.of("IN_PRODUCTION","READY","COMPLETED").contains(next)){
            JsonObject latest=one(rows(c,"SELECT status FROM storefront.quote_proofs WHERE request_id=? ORDER BY revision DESC LIMIT 1",id));
            if(!latest.entrySet().isEmpty()&&!"APPROVED".equals(text(latest,"status")))
                throw new IllegalArgumentException("The customer must approve the latest design proof before production advances.");
        }
        if(execute(c,"UPDATE storefront.quote_requests SET status=?,updated_at=now() WHERE request_id=? AND location_id=?",next,id,location)!=1)
            throw new IllegalArgumentException("Quote request not found at this store.");
    }
    private static String field(JsonObject body,String name,int limit,boolean required){
        String value=text(body,name).trim();if(value.length()>limit||(required&&value.isBlank()))throw new IllegalArgumentException("Enter a valid "+name+".");return value;
    }
}
