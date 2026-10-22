package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Store-local organization intake. Public callers can submit, never read submissions. */
final class StudioSubmissionQueue {
    static final int MAX_BYTES=12*1024*1024;
    static final String RESET="RESET_TEMPLATE_QUEUE_CODE",PROCESS="PROCESS_TEMPLATE_SUBMISSIONS";
    static String text(JsonObject body,String key){
        JsonElement value=body.get(key);if(value==null||value.isJsonNull())return "";
        if(!value.isJsonPrimitive())throw new IllegalArgumentException("Invalid "+key+".");return value.getAsString();
    }
    static UUID uuid(JsonObject body,String key){return UUID.fromString(text(body,key));}
    static JsonObject object(Object... pairs){JsonObject out=new JsonObject();for(int i=0;i<pairs.length;i+=2)out.add((String)pairs[i],new Gson().toJsonTree(pairs[i+1]));return out;}
    static void enabled(JsonObject head){
        JsonObject design=JsonParser.parseString(text(head,"content")).getAsJsonObject();
        if(!design.has("savePrintRecords")||!design.get("savePrintRecords").getAsBoolean())throw new IllegalStateException("Save and enable print records for this template first.");
    }
    static JsonObject form(JsonObject head)throws Exception {
        enabled(head);JsonObject design=JsonParser.parseString(text(head,"content")).getAsJsonObject();
        JsonObject fields=new JsonObject(),photos=new JsonObject();
        for(var layer:design.getAsJsonArray("elements")){
            var e=layer.getAsJsonObject();String type=text(e,"type"),binding=text(e,"field");
            if(type.equals("TEXT")&&!binding.isBlank())fields.addProperty(binding,text(e,"label"));
            if(type.equals("PHOTO")){UUID.fromString(text(e,"photoId"));photos.add(text(e,"photoId"),object("label",text(e,"label"),"width",e.get("width"),"height",e.get("height")));}
        }
        JsonObject schema=object("fields",fields,"photos",photos);schema.addProperty("schema",LanSecurity.sha256(schema.toString()));schema.addProperty("name",text(design,"name"));schema.addProperty("templateRevision",text(head,"sha256"));return schema;
    }
    static String password(String code,String salt)throws Exception {
        var spec=new PBEKeySpec(code.toCharArray(),Base64.getDecoder().decode(salt),600_000,256);
        try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}finally{spec.clearPassword();}
    }
    static void validateCode(String code){if(code.length()<8||code.length()>128)throw new IllegalArgumentException("Choose an access code of 8–128 characters.");}
    static String linkToken(String edgeKey,int store,UUID template,UUID seed)throws Exception {
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(edgeKey.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("studio-template-submission:"+store+":"+template+":"+seed).getBytes(StandardCharsets.UTF_8)));
    }
    static JsonObject normalized(JsonObject payload,JsonObject schema)throws Exception {
        if(payload==null||!payload.keySet().equals(Set.of("values","photos"))||payload.toString().length()>MAX_BYTES)throw new IllegalArgumentException("Invalid submission size or fields.");
        if(!payload.get("values").isJsonObject()||!payload.get("photos").isJsonObject())throw new IllegalArgumentException("Use objects for submission fields and photos.");
        var fields=schema.getAsJsonObject("fields");var slots=schema.getAsJsonObject("photos");
        var values=payload.getAsJsonObject("values");var photos=payload.getAsJsonObject("photos");
        if(!values.keySet().equals(fields.keySet())||!photos.keySet().equals(slots.keySet()))throw new IllegalStateException("Template fields changed. Refresh the form and review all entries.");
        JsonObject normalized=new JsonObject(),cleanValues=new JsonObject(),cleanPhotos=new JsonObject();
        for(String key:fields.keySet()){
            JsonElement v=values.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString()||v.getAsString().isBlank()||v.getAsString().length()>1000)throw new IllegalArgumentException("Complete every text field (up to 1000 characters each).");cleanValues.addProperty(key,v.getAsString().strip());
        }
        for(String key:slots.keySet()){
            if(!photos.get(key).isJsonObject())throw new IllegalArgumentException("Invalid photo details.");
            JsonObject photo=photos.getAsJsonObject(key);String image=text(photo,"image");byte[] bytes=Base64.getDecoder().decode(image);
            if(bytes.length>2*1024*1024)throw new IllegalArgumentException("Each photo must be at most 2 MB.");
            try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
                var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Use a JPEG or PNG photo.");
                var reader=readers.next();try{reader.setInput(input);if(!Set.of("JPEG","PNG").contains(reader.getFormatName().toUpperCase(Locale.ROOT))||(long)reader.getWidth(0)*reader.getHeight(0)>12_000_000)throw new IllegalArgumentException("Use PNG/JPEG photos of at most 12 megapixels.");reader.read(0);}finally{reader.dispose();}
            }catch(java.io.IOException ex){throw new IllegalArgumentException("Use a readable JPEG or PNG photo.",ex);}
            double zoom=photo.has("zoom")?photo.get("zoom").getAsDouble():1,x=photo.has("offsetX")?photo.get("offsetX").getAsDouble():0,y=photo.has("offsetY")?photo.get("offsetY").getAsDouble():0;
            int turns=photo.has("turns")?photo.get("turns").getAsInt():0;
            if(!Double.isFinite(zoom+x+y)||zoom<.1||zoom>4||Math.abs(x)>4||Math.abs(y)>4||turns<0||turns>3)throw new IllegalArgumentException("Invalid photo framing.");
            cleanPhotos.add(key,object("image",image,"zoom",zoom,"offsetX",x,"offsetY",y,"turns",turns));
        }
        normalized.add("values",cleanValues);normalized.add("photos",cleanPhotos);return normalized;
    }
    static void bind(PreparedStatement p,int store,UUID template)throws SQLException{p.setInt(1,store);p.setObject(2,template);}
    static void requirePermission(Connection c,int actor,String... keys)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id LEFT JOIN role_permissions rp ON rp.role_id=r.role_id LEFT JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND u.is_active=TRUE AND (upper(r.role_name)='ADMIN' OR p.permission_key=ANY(?)) LIMIT 1")){
            p.setInt(1,actor);p.setArray(2,c.createArrayOf("text",keys));try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("This account does not have permission for this submission operation.");}
        }
    }
    static JsonObject staff(Connection c,int store,int actor,JsonObject head,JsonObject request)throws Exception {
        return staff(c,store,actor,head,request,()->{
            var config=StorefrontConfig.load();if(config==null)throw new IllegalStateException("Configure the store website first.");
            return new WebsiteLink(config.origin(),config.instanceId(),config.edgeKey());
        });
    }
    record WebsiteLink(String origin,String instanceId,String edgeKey){}
    interface Website {WebsiteLink load()throws Exception;}
    static JsonObject staff(Connection c,int store,int actor,JsonObject head,JsonObject request,Website website)throws Exception {
        enabled(head);String action=text(request,"action");UUID template=uuid(head,"id");
        if(action.equals("QUEUE_RESET"))requirePermission(c,actor,RESET);
        else if(Set.of("QUEUE_LINK","QUEUE_COPY_LINK","QUEUE_REVOKE").contains(action))requirePermission(c,actor,"COMPANY_PREFERENCES","MANAGE_CUSTOM_ORDER_ITEMS");
        else requirePermission(c,actor,PROCESS);
        c.setAutoCommit(false);
        try {
            JsonObject result=staffTransaction(c,store,actor,template,head,request,action,website);c.commit();return result;
        }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
    }
    private static JsonObject staffTransaction(Connection c,int store,int actor,UUID template,JsonObject head,JsonObject request,String action,Website website)throws Exception {
        if(action.equals("QUEUE_LINK")||action.equals("QUEUE_COPY_LINK")){
            var config=website.load();
            UUID seed=UUID.randomUUID();String token;
            if(action.equals("QUEUE_LINK")){
                token=linkToken(config.edgeKey(),store,template,seed);
                try(var p=c.prepareStatement("INSERT INTO studio_template_submission_links(location_id,template_id,token_hash,link_seed,updated_by) VALUES(?,?,?,?,?) ON CONFLICT(location_id,template_id) DO UPDATE SET token_hash=excluded.token_hash,link_seed=excluded.link_seed,enabled=TRUE,generation=studio_template_submission_links.generation+1,updated_by=excluded.updated_by,updated_at=now()")){
                    bind(p,store,template);p.setString(3,LanSecurity.sha256(token));p.setObject(4,seed);p.setInt(5,actor);p.executeUpdate();
                }
            }else{
                try(var p=c.prepareStatement("SELECT link_seed,token_hash FROM studio_template_submission_links WHERE location_id=? AND template_id=? AND enabled=TRUE")){
                    bind(p,store,template);try(var r=p.executeQuery()){
                        if(!r.next()||r.getObject(1)==null)throw new IllegalStateException("Create an organization link first.");
                        token=linkToken(config.edgeKey(),store,template,(UUID)r.getObject(1));
                        if(!LanSecurity.sha256(token).equals(r.getString(2)))throw new IllegalStateException("The website connection key changed. Rotate the organization link.");
                    }
                }
            }
            return object("url",config.origin()+"/shop/template-submit#instance="+config.instanceId()+"&token="+token);
        }
        if(action.equals("QUEUE_RESET")||action.equals("QUEUE_REVOKE")){
            String change=action.equals("QUEUE_RESET")?"code_hash=NULL,salt=NULL,failures=0,locked_until=NULL,":"enabled=FALSE,";
            try(var p=c.prepareStatement("UPDATE studio_template_submission_links SET "+change+"generation=generation+1,updated_by=?,updated_at=now() WHERE location_id=? AND template_id=?")){
                p.setInt(1,actor);p.setInt(2,store);p.setObject(3,template);if(p.executeUpdate()!=1)throw new IllegalStateException("Create an organization link first.");
            }
            return object("updated",true);
        }
        if(action.equals("QUEUE_LIST")){
            int offset=request.has("offset")?request.get("offset").getAsInt():0;if(offset<0)throw new IllegalArgumentException("Invalid offset.");JsonArray rows=new JsonArray();
            boolean admin;
            try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.is_active=TRUE AND upper(r.role_name)='ADMIN'")){
                p.setInt(1,actor);try(var r=p.executeQuery()){admin=r.next();}
            }
            try(var p=c.prepareStatement("SELECT submission_id,received_at,state,payload->'values',claimed_by,claim_until FROM studio_template_submissions WHERE location_id=? AND template_id=? AND (state IN ('PENDING','IN_REVIEW','PRINTING') OR ?) ORDER BY received_at DESC,submission_id LIMIT 100 OFFSET ?")){
                bind(p,store,template);p.setBoolean(3,admin);p.setInt(4,offset);try(var r=p.executeQuery()){while(r.next())rows.add(object("id",r.getString(1),"receivedAt",r.getString(2),"state",r.getString(3),"values",JsonParser.parseString(r.getString(4)),"claimedBy",r.getObject(5),"claimUntil",r.getString(6)));}
            }return object("submissions",rows);
        }
        UUID id=uuid(request,"submissionId");
        if(action.equals("QUEUE_CLAIM")){
            String claim=UUID.randomUUID().toString();
            try(var p=c.prepareStatement("UPDATE studio_template_submissions SET state='IN_REVIEW',claimed_by=?,claim_token=?::uuid,claim_until=now()+interval '30 minutes',updated_at=now() WHERE location_id=? AND template_id=? AND submission_id=? AND (state='PENDING' OR (state='IN_REVIEW' AND claim_until<now())) RETURNING payload,schema_hash")){
                p.setInt(1,actor);p.setString(2,claim);p.setInt(3,store);p.setObject(4,template);p.setObject(5,id);
                try(var r=p.executeQuery()){if(!r.next())throw new IllegalStateException("This entry is already claimed or processed. Refresh the queue.");return object("claimToken",claim,"payload",JsonParser.parseString(r.getString(1)),"submittedSchema",r.getString(2),"form",form(head));}
            }
        }
        if(action.equals("QUEUE_RETRY")){
            // Explicit recovery only: a PRINTING job may already have physically printed.
            StudioBadgeRecords.requireAdmin(c,actor);
            try(var p=c.prepareStatement("UPDATE studio_template_submissions SET state='PENDING',claim_token=NULL,claim_until=NULL,claimed_by=NULL,updated_at=now(),note=? WHERE location_id=? AND template_id=? AND submission_id=? AND state IN ('PRINTING','JOB_SENT','IN_REVIEW')")){
                p.setString(1,"Retry explicitly authorized by admin "+actor);p.setInt(2,store);p.setObject(3,template);p.setObject(4,id);if(p.executeUpdate()!=1)throw new IllegalStateException("Entry cannot be retried.");
            }return object("updated",true);
        }
        UUID claim=uuid(request,"claimToken");String state;
        try(var p=c.prepareStatement("SELECT state FROM studio_template_submissions WHERE location_id=? AND template_id=? AND submission_id=? AND claimed_by=? AND claim_token=? AND (state IN ('PRINTING','JOB_SENT') OR claim_until>now()) FOR UPDATE")){
            bind(p,store,template);p.setObject(3,id);p.setInt(4,actor);p.setObject(5,claim);try(var r=p.executeQuery()){if(!r.next())throw new IllegalStateException("Your claim expired or changed. Reopen the queue.");state=r.getString(1);}
        }
        String next,note="";UUID recordId=null;
        switch(action){
            case "QUEUE_RELEASE" -> {if(!state.equals("IN_REVIEW"))throw new IllegalStateException("Use admin recovery for a print job that may already have been sent.");next="PENDING";}
            case "QUEUE_REJECT" -> {if(!state.equals("IN_REVIEW"))throw new IllegalStateException("Claim the entry before rejecting it.");note=text(request,"reason").strip();if(note.isBlank()||note.length()>500)throw new IllegalArgumentException("Enter a rejection reason up to 500 characters.");next="REJECTED";}
            case "QUEUE_BEGIN" -> {
                if(!state.equals("IN_REVIEW"))throw new IllegalStateException("This job has already started. Use admin recovery before retrying.");
                JsonObject schema=form(head);if(!text(request,"schema").equals(text(schema,"schema"))||!text(request,"templateRevision").equals(text(head,"sha256")))throw new IllegalStateException("Current template changed. Reload the template and review this submission.");
                JsonObject payload=normalized(request.getAsJsonObject("payload"),schema);recordId=uuid(request,"recordId");
                try(var p=c.prepareStatement("INSERT INTO studio_badge_print_records(record_id,location_id,template_id,created_by,payload) VALUES(?,?,?,?,?::jsonb)")){
                    p.setObject(1,recordId);p.setInt(2,store);p.setObject(3,template);p.setInt(4,actor);p.setString(5,payload.toString());p.executeUpdate();
                }next="PRINTING";
            }
            case "QUEUE_SENT" -> {if(!Set.of("PRINTING","JOB_SENT").contains(state))throw new IllegalStateException("The print job has not started.");next="JOB_SENT";note="Printer driver accepted job; physical output not verified.";}
            default -> throw new IllegalArgumentException("Unknown queue operation.");
        }
        try(var p=c.prepareStatement("UPDATE studio_template_submissions SET state=?,updated_at=now(),note=?,record_id=COALESCE(?,record_id),claim_until=CASE WHEN ?='IN_REVIEW' THEN claim_until ELSE NULL END WHERE location_id=? AND template_id=? AND submission_id=?")){
            p.setString(1,next);p.setString(2,note);p.setObject(3,recordId);p.setString(4,next);p.setInt(5,store);p.setObject(6,template);p.setObject(7,id);p.executeUpdate();
        }return object("updated",true);
    }
    static JsonObject publicRequest(Connection c,JsonObject request)throws Exception {
        return publicRequest(c,request,(store,id)->StudioTemplateStore.current().execute(store,object("action","GET","id",id.toString())));
    }
    interface TemplateReader {JsonObject read(int store,UUID template)throws Exception;}
    static JsonObject publicRequest(Connection c,JsonObject request,TemplateReader templates)throws Exception {
        String token=text(request,"token");if(!token.matches("[A-Za-z0-9_-]{43}"))throw new SecurityException("Invalid organization link.");
        c.setAutoCommit(false);
        try {
            JsonObject result;
            try(var p=c.prepareStatement("SELECT location_id,template_id,code_hash,salt,generation,failures,locked_until FROM studio_template_submission_links WHERE token_hash=? AND enabled=TRUE FOR UPDATE")){
                p.setString(1,LanSecurity.sha256(token));try(var r=p.executeQuery()){
                    if(!r.next())throw new SecurityException("This organization link is unavailable.");
                    int store=r.getInt(1),generation=r.getInt(5);UUID template=(UUID)r.getObject(2);String hash=r.getString(3),salt=r.getString(4);Timestamp locked=r.getTimestamp(7);
                    var head=templates.read(store,template);enabled(head);
                    String action=text(request,"action");
                    if(action.equals("INFO")){result=object("setupRequired",hash==null,"name",text(head,"name"));}
                    else if(action.equals("SETUP")||action.equals("LOGIN")){
                        if(locked!=null&&locked.getTime()>System.currentTimeMillis())throw new SecurityException("Too many attempts. Try again in 15 minutes.");
                        String code=text(request,"code");validateCode(code);
                        if(action.equals("SETUP")){
                            if(hash!=null)throw new IllegalStateException("An access code was already set. Sign in instead.");
                            if(!code.equals(text(request,"confirm")))throw new IllegalArgumentException("The access codes do not match.");
                            byte[] bytes=new byte[24];new java.security.SecureRandom().nextBytes(bytes);salt=Base64.getEncoder().encodeToString(bytes);hash=password(code,salt);
                            try(var update=c.prepareStatement("UPDATE studio_template_submission_links SET code_hash=?,salt=?,updated_at=now() WHERE location_id=? AND template_id=?")){update.setString(1,hash);update.setString(2,salt);update.setInt(3,store);update.setObject(4,template);update.executeUpdate();}
                        }else if(hash==null||!LanSecurity.constantTimeEquals(hash,password(code,salt))){
                            try(var update=c.prepareStatement("UPDATE studio_template_submission_links SET failures=CASE WHEN locked_until<now() THEN 1 ELSE failures+1 END,locked_until=CASE WHEN failures>=4 AND (locked_until IS NULL OR locked_until>now()) THEN now()+interval '15 minutes' ELSE NULL END WHERE location_id=? AND template_id=?")){bind(update,store,template);update.executeUpdate();}c.commit();throw new SecurityException("Access code not accepted.");
                        }
                        try(var update=c.prepareStatement("UPDATE studio_template_submission_links SET failures=0,locked_until=NULL WHERE location_id=? AND template_id=?")){bind(update,store,template);update.executeUpdate();}
                        String session=LanSecurity.randomToken();
                        try(var clean=c.prepareStatement("DELETE FROM studio_template_submission_sessions WHERE expires_at<now() OR (location_id=? AND template_id=? AND generation<>?)")){bind(clean,store,template);clean.setInt(3,generation);clean.executeUpdate();}
                        try(var sessionInsert=c.prepareStatement("INSERT INTO studio_template_submission_sessions(session_hash,location_id,template_id,generation,expires_at) SELECT ?,?,?,?,now()+interval '8 hours' WHERE (SELECT count(*) FROM studio_template_submission_sessions WHERE location_id=? AND template_id=?)<100")){
                            sessionInsert.setString(1,LanSecurity.sha256(session));sessionInsert.setInt(2,store);sessionInsert.setObject(3,template);sessionInsert.setInt(4,generation);sessionInsert.setInt(5,store);sessionInsert.setObject(6,template);if(sessionInsert.executeUpdate()!=1)throw new IllegalStateException("Too many active sessions. Try later.");
                        }result=object("session",session,"form",form(head));
                    }else {
                        try(var sessionQuery=c.prepareStatement("SELECT 1 FROM studio_template_submission_sessions WHERE session_hash=? AND location_id=? AND template_id=? AND generation=? AND expires_at>now()")){
                            sessionQuery.setString(1,LanSecurity.sha256(text(request,"session")));sessionQuery.setInt(2,store);sessionQuery.setObject(3,template);sessionQuery.setInt(4,generation);try(var sr=sessionQuery.executeQuery()){if(!sr.next())throw new SecurityException("Sign in again. The access code may have been reset.");}
                        }
                        var schema=form(head);
                        if(action.equals("FORM"))result=object("form",schema);
                        else if(action.equals("SUBMIT")){
                            if(!text(schema,"schema").equals(text(request,"schema")))throw new IllegalStateException("Template fields changed. Refresh the form and review your entries.");
                            UUID id=uuid(request,"submissionId");JsonObject payload=normalized(request.getAsJsonObject("payload"),schema);String digest=LanSecurity.sha256(payload.toString());
                            try(var previous=c.prepareStatement("SELECT request_hash FROM studio_template_submissions WHERE location_id=? AND template_id=? AND submission_id=?")){
                                bind(previous,store,template);previous.setObject(3,id);try(var pr=previous.executeQuery()){if(pr.next()){if(!digest.equals(pr.getString(1)))throw new IllegalStateException("This submission reference was already used for different details.");c.commit();return object("received",true,"reference",id.toString());}}
                            }
                            try(var insert=c.prepareStatement("INSERT INTO studio_template_submissions(location_id,template_id,submission_id,schema_hash,payload,request_hash) SELECT ?,?,?,?,?::jsonb,? WHERE (SELECT count(*) FROM studio_template_submissions WHERE location_id=? AND template_id=? AND state IN ('PENDING','IN_REVIEW','PRINTING'))<10000")){
                                bind(insert,store,template);insert.setObject(3,id);insert.setString(4,text(schema,"schema"));insert.setString(5,payload.toString());insert.setString(6,digest);insert.setInt(7,store);insert.setObject(8,template);if(insert.executeUpdate()!=1)throw new IllegalStateException("The queue is full. Contact the store.");
                            }result=object("received",true,"reference",id.toString());
                        }else throw new IllegalArgumentException("Invalid public submission operation.");
                    }
                }
            }c.commit();return result;
        }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
    }
}
