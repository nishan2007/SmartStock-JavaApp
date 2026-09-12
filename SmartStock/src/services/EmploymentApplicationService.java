package services;

import com.google.gson.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.io.*;

/** Application ownership and transitions are checked under the same lock as every write. */
public final class EmploymentApplicationService {
    public static final int MAX_FILES=10, MAX_FILE_BYTES=10*1024*1024, MAX_TOTAL_BYTES=50*1024*1024;
    private static final Gson JSON=LanJson.create();
    private static final Set<String> EDITABLE=Set.of("DRAFT","INFORMATION_REQUESTED");
    private static final Set<String> CATEGORIES=Set.of("exam_results","identification","application","employment_letter","recommendation","other");
    private static final Set<String> FIELDS=Set.of("firstName","middleName","lastName","nickname","phone","dateOfBirth","address","town","availability","startDate","preferredStores","interests","introduction","education","examResults","skills","experience","reference1","reference2","declaration");
    public record Attachment(UUID id,String filename,String category,String description,String contentType,long size,int pages) { }
    public record Document(byte[] bytes,String type,int pages) { }
    private EmploymentApplicationService() { }

    public static UUID ensureApplicant(Connection c,UUID auth,String email,int location)throws Exception {
        try(PreparedStatement p=c.prepareStatement("SELECT registration_id FROM employee_registrations WHERE auth_user_id=?")){
            p.setObject(1,auth);try(ResultSet r=p.executeQuery()){if(r.next())return r.getObject(1,UUID.class);}
        }
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE auth_user_id=? OR LOWER(email)=LOWER(?)")){
            p.setObject(1,auth);p.setString(2,email);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("This is an employee account. Contact your employee manager.");}
        }
        UUID id=UUID.randomUUID();
        try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_registrations(registration_id,location_id,status,first_name,last_name,email,phone,submission_fingerprint,auth_user_id,portal_identity) VALUES(?,?,'DRAFT','','',?,'','',?,TRUE) ON CONFLICT DO NOTHING")){
            p.setObject(1,id);p.setInt(2,location);p.setString(3,email.toLowerCase(Locale.ROOT));p.setObject(4,auth);p.executeUpdate();
        }
        try(PreparedStatement p=c.prepareStatement("SELECT registration_id FROM employee_registrations WHERE auth_user_id=?")){
            p.setObject(1,auth);try(ResultSet r=p.executeQuery()){if(r.next())return r.getObject(1,UUID.class);}
        }
        throw new IllegalArgumentException("An existing application needs manager assistance before this account can be linked.");
    }

    public static JsonObject read(Connection c,UUID id,UUID auth,boolean manager)throws SQLException {
        JsonObject out=new JsonObject();
        try(PreparedStatement p=c.prepareStatement("SELECT * FROM employee_registrations WHERE registration_id=?")) {
            p.setObject(1,id);try(ResultSet r=p.executeQuery()) {
                if(!r.next()||(!manager&&!Objects.equals(auth,r.getObject("auth_user_id"))))throw new IllegalArgumentException("Application not found.");
                out.addProperty("id",id.toString());out.addProperty("status",r.getString("status"));out.addProperty("revision",r.getInt("revision"));out.addProperty("email",r.getString("email"));
                out.addProperty("updatedAt",r.getTimestamp("updated_at").toInstant().toString());
                out.addProperty("submittedAt","DRAFT".equals(r.getString("status"))?null:r.getTimestamp("submitted_at").toInstant().toString());
                JsonObject form=JsonParser.parseString(r.getString("application_json")).getAsJsonObject();
                if(form.size()==0){for(String[] pair:new String[][]{{"firstName","first_name"},{"middleName","middle_name"},{"lastName","last_name"},{"nickname","nickname"},{"phone","phone"},{"dateOfBirth","date_of_birth"}})form.addProperty(pair[0],r.getString(pair[1]));}
                out.add("form",form);
                if(manager)out.addProperty("legacyDocumentUrl",r.getString("document_url"));
            }
        }
        out.add("attachments",JSON.toJsonTree(attachments(c,id)));
        JsonArray events=new JsonArray();
        try(PreparedStatement p=c.prepareStatement("SELECT status,message,internal_note,created_at FROM employee_application_events WHERE registration_id=? ORDER BY event_id")){
            p.setObject(1,id);try(ResultSet r=p.executeQuery()){while(r.next()){JsonObject event=new JsonObject();event.addProperty("status",r.getString(1));event.addProperty("message",r.getString(2));if(manager)event.addProperty("internalNote",r.getString(3));event.addProperty("at",r.getTimestamp(4).toInstant().toString());events.add(event);}}
        }
        out.add("history",events);return out;
    }
    public static List<Attachment> attachments(Connection c,UUID id)throws SQLException {
        List<Attachment> result=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT attachment_id,filename,category,description,content_type,byte_size,page_count FROM employee_application_attachments WHERE registration_id=? AND removed=FALSE ORDER BY created_at,attachment_id")){
            p.setObject(1,id);try(ResultSet r=p.executeQuery()){while(r.next())result.add(new Attachment(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getInt(7)));}
        }return result;
    }
    static JsonObject validateForm(JsonObject input,boolean submitting) {
        if(input==null)throw new IllegalArgumentException("Application details are required.");
        JsonObject form=new JsonObject();
        for(String key:FIELDS)if(input.has(key)&&!input.get(key).isJsonNull()){
            if(!input.get(key).isJsonPrimitive())throw new IllegalArgumentException("Invalid application field.");
            if("declaration".equals(key)){form.addProperty(key,input.get(key).getAsBoolean());continue;}
            String value=input.get(key).getAsString().trim();int max=Set.of("education","examResults","experience","skills","introduction").contains(key)?4000:Set.of("firstName","middleName","lastName","nickname").contains(key)?100:"phone".equals(key)?50:500;
            if(value.length()>max)throw new IllegalArgumentException(key+" is too long.");form.addProperty(key,value);
        }
        if(!value(form,"dateOfBirth").isEmpty()) {
            LocalDate date;
            try{date=LocalDate.parse(value(form,"dateOfBirth"));}catch(Exception e){throw new IllegalArgumentException("Enter a valid date of birth.");}
            if(date.isAfter(LocalDate.now())||date.isBefore(LocalDate.now().minusYears(130)))throw new IllegalArgumentException("Enter a valid date of birth.");
        }
        if(submitting){
            for(String key:List.of("firstName","lastName","phone","dateOfBirth"))if(value(form,key).isBlank())throw new IllegalArgumentException("Complete "+key+" before submitting.");
            if(!form.has("declaration")||!form.get("declaration").getAsBoolean())throw new IllegalArgumentException("Confirm the declaration before submitting.");
        }
        return form;
    }
    static String value(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static void editable(JsonObject state){if(!EDITABLE.contains(value(state,"status")))throw new IllegalArgumentException("This application is read-only. Ask a manager to reopen it for corrections.");}
    public static JsonObject save(Connection c,UUID id,UUID auth,int revision,JsonObject input,boolean submit)throws Exception {
        EmployeeRegistrationService.lock(c,id);c.setAutoCommit(false);
        try {
            JsonObject state=read(c,id,auth,false);JsonObject form=validateForm(input,submit);
            if(submit&&"PENDING".equals(value(state,"status"))&&form.equals(state.getAsJsonObject("form"))){c.commit();return state;}
            editable(state);if(revision!=state.get("revision").getAsInt())throw new IllegalArgumentException("Your application changed in another window. Reload before saving.");
            try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET first_name=?,middle_name=?,last_name=?,nickname=?,phone=?,date_of_birth=?,application_json=?::jsonb,revision=revision+1,updated_at=CURRENT_TIMESTAMP,status=?,submitted_at=CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE submitted_at END WHERE registration_id=?")){
                p.setString(1,value(form,"firstName"));p.setString(2,value(form,"middleName"));p.setString(3,value(form,"lastName"));p.setString(4,value(form,"nickname"));p.setString(5,value(form,"phone"));p.setObject(6,value(form,"dateOfBirth").isBlank()?null:LocalDate.parse(value(form,"dateOfBirth")));p.setString(7,form.toString());p.setString(8,submit?"PENDING":value(state,"status"));p.setBoolean(9,submit);p.setObject(10,id);p.executeUpdate();
            }
            if(submit){
                try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_application_revisions(registration_id,revision,application_json,attachments_json) VALUES(?,?,?::jsonb,?::jsonb)")){
                    p.setObject(1,id);p.setInt(2,revision+1);p.setString(3,form.toString());p.setString(4,JSON.toJson(attachments(c,id)));p.executeUpdate();
                }event(c,id,"PENDING","Application submitted.","",null);
            }
            JsonObject result=read(c,id,auth,false);c.commit();return result;
        }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);EmployeeRegistrationService.unlock(c,id);}
    }
    public static void transition(Connection c,UUID id,UUID auth,String next,String message,String note,Integer actor)throws Exception {
        EmployeeRegistrationService.lock(c,id);c.setAutoCommit(false);
        try {
            JsonObject state=read(c,id,auth,actor!=null);String old=value(state,"status");
            validateTransition(old,next,actor!=null,message);
            if(message==null)message="";if(note==null)note="";
            if(message.length()>2000||note.length()>4000)throw new IllegalArgumentException("Review message is too long.");
            try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET status=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP,rejection_reason=? WHERE registration_id=?")){
                p.setString(1,next);p.setString(2,"REJECTED".equals(next)?message:null);p.setObject(3,id);p.executeUpdate();
            }
            event(c,id,next,message,note,actor);c.commit();
        }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);EmployeeRegistrationService.unlock(c,id);}
    }
    static void validateTransition(String old,String next,boolean manager,String message){
        if(Set.of("APPROVED","APPROVING","SUBMITTING").contains(old))throw new IllegalArgumentException("This application cannot change status here.");
        if(!manager){if(!"WITHDRAWN".equals(next)||Set.of("REJECTED","WITHDRAWN").contains(old))throw new IllegalArgumentException("Invalid application action.");return;}
        if(!Set.of("UNDER_REVIEW","INTERVIEW","INFORMATION_REQUESTED","REJECTED").contains(next))throw new IllegalArgumentException("Invalid review status.");
        if(Set.of("REJECTED","WITHDRAWN").contains(old)&&!"INFORMATION_REQUESTED".equals(next))throw new IllegalArgumentException("Reopen this application for corrections first.");
        if("DRAFT".equals(old))throw new IllegalArgumentException("Wait for the applicant to submit.");
        if("INFORMATION_REQUESTED".equals(next)&&(message==null||message.isBlank()))throw new IllegalArgumentException("Explain what the applicant should update.");
    }
    static void event(Connection c,UUID id,String status,String message,String note,Integer actor)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_application_events(registration_id,status,message,internal_note,actor_id) VALUES(?,?,?,?,?)")){
            p.setObject(1,id);p.setString(2,status);p.setString(3,message==null?"":message);p.setString(4,note==null?"":note);p.setObject(5,actor);p.executeUpdate();
        }
    }
    static Document validateDocument(byte[] raw)throws Exception {
        if(raw==null||raw.length>MAX_FILE_BYTES)throw new IllegalArgumentException("Each file must be 10 MB or smaller.");
        String type=EmployeeRegistrationService.documentType(raw);
        if("application/pdf".equals(type))try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(raw)){return new Document(raw,type,pdf.getNumberOfPages());}
        var image=javax.imageio.ImageIO.read(new ByteArrayInputStream(raw));
        ByteArrayOutputStream clean=new ByteArrayOutputStream();
        if(!javax.imageio.ImageIO.write(image,"image/png".equals(type)?"png":"jpg",clean))throw new IllegalArgumentException("Cannot process this image.");
        if(clean.size()>MAX_FILE_BYTES)throw new IllegalArgumentException("The processed image exceeds 10 MB. Choose a smaller image.");
        return new Document(clean.toByteArray(),type,1);
    }
    static void validateQuota(int count,long total,long incoming){if(count>=MAX_FILES||total+incoming>MAX_TOTAL_BYTES)throw new IllegalArgumentException("Use at most 10 attachments and 50 MB total.");}
    public static void upload(Connection c,UUID id,UUID auth,UUID attachment,String filename,String category,String description,byte[] raw,EmploymentPortalCloud cloud)throws Exception {
        if(!CATEGORIES.contains(category)||filename==null||filename.isBlank()||filename.length()>255||description==null||description.length()>500)throw new IllegalArgumentException("Check the document name, category, and description.");
        Document doc=validateDocument(raw);EmployeeRegistrationService.lock(c,id);c.setAutoCommit(false);
        try {
            editable(read(c,id,auth,false));
            try(PreparedStatement p=c.prepareStatement("SELECT registration_id,removed FROM employee_application_attachments WHERE attachment_id=?")){
                p.setObject(1,attachment);try(ResultSet r=p.executeQuery()){if(r.next()){if(id.equals(r.getObject(1))&&!r.getBoolean(2)){c.commit();return;}throw new IllegalArgumentException("Choose a new upload reference.");}}
            }
            List<Attachment> files=attachments(c,id);validateQuota(files.size(),files.stream().mapToLong(Attachment::size).sum(),doc.bytes.length);
            String path=id+"/"+attachment+switch(doc.type){case "application/pdf"->".pdf";case "image/png"->".png";default->".jpg";};
            cloud.upload(path,doc.type,doc.bytes);
            try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_application_attachments(attachment_id,registration_id,filename,category,description,content_type,byte_size,page_count,object_path) VALUES(?,?,?,?,?,?,?,?,?)")){
                p.setObject(1,attachment);p.setObject(2,id);p.setString(3,filename);p.setString(4,category);p.setString(5,description);p.setString(6,doc.type);p.setLong(7,doc.bytes.length);p.setInt(8,doc.pages);p.setString(9,path);p.executeUpdate();
            }c.commit();
        }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);EmployeeRegistrationService.unlock(c,id);Arrays.fill(doc.bytes,(byte)0);}
    }
    public static void remove(Connection c,UUID id,UUID auth,UUID attachment)throws Exception {
        EmployeeRegistrationService.lock(c,id);
        try {editable(read(c,id,auth,false));try(PreparedStatement p=c.prepareStatement("UPDATE employee_application_attachments SET removed=TRUE WHERE registration_id=? AND attachment_id=?")){p.setObject(1,id);p.setObject(2,attachment);p.executeUpdate();}}
        finally{EmployeeRegistrationService.unlock(c,id);}
    }
    public static Document download(Connection c,UUID id,UUID auth,UUID attachment,boolean manager,EmploymentPortalCloud cloud)throws Exception {
        read(c,id,auth,manager);
        try(PreparedStatement p=c.prepareStatement("SELECT object_path,content_type,page_count FROM employee_application_attachments WHERE registration_id=? AND attachment_id=? AND (? OR removed=FALSE)")){
            p.setObject(1,id);p.setObject(2,attachment);p.setBoolean(3,manager);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Document not found.");return new Document(cloud.download(r.getString(1)),r.getString(2),r.getInt(3));}
        }
    }
}
