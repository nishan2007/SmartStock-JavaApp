package services;

import com.google.gson.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Store-owned registration workflow. Passwords are passed to Auth only, never persisted. */
public final class EmployeeRegistrationService {
    private static final Gson JSON = LanJson.create();
    public static final int MAX_DOCUMENT_BYTES = 25 * 1024 * 1024;
    public interface Cloud {
        String createBlocked(UUID registrationId, String email, String password, String name) throws Exception;
        String uploadId(UUID registrationId, String contentType, byte[] bytes) throws Exception;
        void activate(String authId) throws Exception;
    }
    public record Submission(UUID id, String firstName, String middleName, String lastName, String nickname,
                             String email, String phone, LocalDate dateOfBirth, String password,
                             String passwordConfirmation, String documentBase64) {
        @Override public String toString() { return "EmployeeRegistrationSubmission[redacted]"; }
    }
    public record Registration(UUID id, int locationId, String status, String firstName, String middleName,
                               String lastName, String nickname, String email, String phone, LocalDate dateOfBirth,
                               String documentUrl, String submittedAt, String rejectionReason, Integer employeeId) { }
    private EmployeeRegistrationService() { }

    static void validate(Submission s) {
        if (s == null || s.id == null) throw new IllegalArgumentException("A submission reference is required.");
        required(s.firstName, "First name", 100); required(s.lastName, "Last name", 100);
        optional(s.middleName, 100); optional(s.nickname, 100);
        required(s.email, "Email", 254); required(s.phone, "Phone", 50);
        if (!s.email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw new IllegalArgumentException("Enter a valid email address.");
        if (s.dateOfBirth == null || s.dateOfBirth.isAfter(LocalDate.now()) || s.dateOfBirth.isBefore(LocalDate.now().minusYears(130)))
            throw new IllegalArgumentException("Enter a valid birth date.");
        if (s.password == null || s.password.length() < 8 || s.password.length() > 128)
            throw new IllegalArgumentException("Use a password between 8 and 128 characters.");
        if (!s.password.equals(s.passwordConfirmation)) throw new IllegalArgumentException("Passwords do not match.");
    }
    private static void required(String v, String name, int max) {
        if (v == null || v.isBlank() || v.length() > max) throw new IllegalArgumentException(name + " is required and must be at most " + max + " characters.");
    }
    private static void optional(String v, int max) { if (v != null && v.length() > max) throw new IllegalArgumentException("An optional field is too long."); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
    static byte[] document(String encoded) {
        if (encoded == null || encoded.length() > ((MAX_DOCUMENT_BYTES + 2L) / 3) * 4) throw new IllegalArgumentException("An ID document of 25 MB or smaller is required.");
        try { byte[] b = Base64.getDecoder().decode(encoded); documentType(b); return b; }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Upload a valid PDF, JPEG, or PNG ID document of 25 MB or smaller."); }
    }
    static String documentType(byte[] b) {
        if (b == null || b.length < 8 || b.length > MAX_DOCUMENT_BYTES) throw new IllegalArgumentException("Invalid ID document size.");
        try {
            if (b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-') {
                try (org.apache.pdfbox.pdmodel.PDDocument pdf = org.apache.pdfbox.pdmodel.PDDocument.load(b)) {
                    if (pdf.isEncrypted() || pdf.getNumberOfPages() < 1 || pdf.getNumberOfPages() > 20)
                        throw new IllegalArgumentException("Use an unencrypted ID document with at most 20 pages.");
                }
                return "application/pdf";
            }
            boolean png = b[0]==(byte)137 && b[1]==80 && b[2]==78 && b[3]==71 && b[4]==13 && b[5]==10 && b[6]==26 && b[7]==10;
            boolean jpeg = b[0]==(byte)255 && b[1]==(byte)216 && b[2]==(byte)255;
            if (!png && !jpeg) throw new IllegalArgumentException("Invalid image format.");
            try (var stream = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(b))) {
                var readers = javax.imageio.ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("Invalid image.");
                var reader=readers.next();
                try { reader.setInput(stream); long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                    if (pixels <= 0 || pixels > 40_000_000) throw new IllegalArgumentException("ID image is too large.");
                    reader.read(0);
                } finally { reader.dispose(); }
            }
            return png ? "image/png" : "image/jpeg";
        } catch (java.io.IOException e) { throw new IllegalArgumentException("The ID document is damaged or unreadable."); }
    }

    public static UUID submit(Connection c, Submission s, int locationId, Cloud cloud) throws Exception {
        validate(s); byte[] bytes=document(s.documentBase64); String type=documentType(bytes);
        String email=clean(s.email).toLowerCase(Locale.ROOT);
        // This fingerprint deliberately excludes both password fields.
        String fingerprint=LanSecurity.sha256(JSON.toJson(List.of(clean(s.firstName),clean(s.middleName),clean(s.lastName),clean(s.nickname),email,clean(s.phone),s.dateOfBirth.toString(),
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))));
        lock(c,s.id);
        try {
            try (PreparedStatement p=c.prepareStatement("SELECT 1 FROM users WHERE LOWER(email)=? AND NOT EXISTS (SELECT 1 FROM employee_registrations r WHERE r.registration_id=? AND r.employee_id=users.user_id)")) {
                p.setString(1,email);p.setObject(2,s.id);try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("This email cannot be registered. Ask an employee manager to review it.");}
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_registrations(registration_id,location_id,status,first_name,middle_name,last_name,nickname,email,phone,date_of_birth,submission_fingerprint) VALUES(?,?,'SUBMITTING',?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING")) {
                p.setObject(1,s.id);p.setInt(2,locationId);p.setString(3,clean(s.firstName));p.setString(4,clean(s.middleName));p.setString(5,clean(s.lastName));p.setString(6,clean(s.nickname));p.setString(7,email);p.setString(8,clean(s.phone));p.setObject(9,s.dateOfBirth);p.setString(10,fingerprint);p.executeUpdate();
            }
            String auth=null,url=null,status;
            try(PreparedStatement p=c.prepareStatement("SELECT submission_fingerprint,status,auth_user_id::text,document_url FROM employee_registrations WHERE registration_id=?")) {
                p.setObject(1,s.id);try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new IllegalArgumentException("This email cannot be registered. Ask an employee manager to review it.");
                    if(!fingerprint.equals(r.getString(1)))throw new IllegalArgumentException("This submission is already reserved. Retry with the same details or ask an employee manager for help.");
                    status=r.getString(2);auth=r.getString(3);url=r.getString(4);
                }
            }
            if(!"SUBMITTING".equals(status))return s.id;
            if(url==null){url=cloud.uploadId(s.id,type,bytes);try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET document_url=? WHERE registration_id=?")){p.setString(1,url);p.setObject(2,s.id);p.executeUpdate();}}
            if(auth==null){auth=cloud.createBlocked(s.id,email,s.password,clean(s.firstName)+" "+clean(s.lastName));try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET auth_user_id=?::uuid WHERE registration_id=?")){p.setString(1,auth);p.setObject(2,s.id);p.executeUpdate();}}
            try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET status='PENDING',updated_at=CURRENT_TIMESTAMP WHERE registration_id=?")){p.setObject(1,s.id);p.executeUpdate();}
            return s.id;
        } finally { Arrays.fill(bytes,(byte)0); unlock(c,s.id); }
    }

    public static List<Registration> list(Connection c, boolean rejected) throws SQLException {
        List<Registration> rows=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT registration_id,location_id,status,first_name,middle_name,last_name,nickname,email,phone,date_of_birth,document_url,submitted_at,rejection_reason,employee_id FROM employee_registrations WHERE status IN ('PENDING','APPROVING','UNDER_REVIEW','INTERVIEW','INFORMATION_REQUESTED') OR (? AND status IN ('REJECTED','WITHDRAWN','APPROVED')) ORDER BY submitted_at DESC,registration_id")) {
            p.setBoolean(1,rejected);try(ResultSet r=p.executeQuery()){while(r.next())rows.add(new Registration(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getString(9),r.getObject(10,LocalDate.class),r.getString(11),r.getTimestamp(12).toInstant().toString(),r.getString(13),(Integer)r.getObject(14)));}
        }return rows;
    }

    public static int approve(Connection c, UUID id, LanEmployeeAdminService.SaveRequest request, int actor, String actorName, Cloud cloud) throws Exception {
        lock(c,id);
        try {
            String status,auth,email,url; Integer employee;
            try(PreparedStatement p=c.prepareStatement("SELECT status,auth_user_id::text,email,document_url,employee_id FROM employee_registrations WHERE registration_id=?")) {
                p.setObject(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Registration not found.");status=r.getString(1);auth=r.getString(2);email=r.getString(3);url=r.getString(4);employee=(Integer)r.getObject(5);}
            }
            if("APPROVED".equals(status))return employee;
            if(!Set.of("PENDING","UNDER_REVIEW","INTERVIEW","APPROVING").contains(status))throw new IllegalArgumentException("Only submitted applications can be hired.");
            if(employee==null){
                if(request==null || !email.equalsIgnoreCase(clean(request.email())))throw new IllegalArgumentException("Keep the submitted email when approving this account.");
                validateEmployment(c,request);
                if(url==null||url.isBlank())try(PreparedStatement p=c.prepareStatement("SELECT attachment_id FROM employee_application_attachments WHERE registration_id=? AND category='identification' AND removed=FALSE ORDER BY created_at LIMIT 1")) {
                    p.setObject(1,id);try(ResultSet r=p.executeQuery()){if(r.next()){
                        var doc=EmploymentApplicationService.download(c,id,null,r.getObject(1,UUID.class),true,new EmploymentPortalCloud());
                        try{url=cloud.uploadId(id,doc.type(),doc.bytes());}finally{Arrays.fill(doc.bytes(),(byte)0);}
                        try(PreparedStatement update=c.prepareStatement("UPDATE employee_registrations SET document_url=? WHERE registration_id=?")){update.setString(1,url);update.setObject(2,id);update.executeUpdate();}
                    }}
                }
                var r=request;
                var inactive=new LanEmployeeAdminService.SaveRequest(r.username(),null,r.firstName(),r.middleName(),r.lastName(),r.fullName(),r.nickname(),email,r.phone(),r.photoUrl(),url,r.dateOfBirth(),r.hireDate(),r.compensationType(),r.salary(),r.role(),false,r.locationIds(),r.payrollPeriodType(),r.workHourLimit(),true,null);
                c.setAutoCommit(false);
                try {
                    employee=LanEmployeeAdminService.create(c,inactive,auth,actor,actorName);
                    try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET status='APPROVING',employee_id=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE registration_id=?")){p.setInt(1,employee);p.setInt(2,actor);p.setObject(3,id);p.executeUpdate();}
                    audit(c,id,actor,"APPROVAL_STARTED",null);c.commit();
                }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            }
            // The inactive local employee remains unable to work if Auth or finalization fails.
            // Portal identities already sign in; do not clear an unrelated Auth ban on hiring.
            boolean portal=false;
            try(PreparedStatement p=c.prepareStatement("SELECT portal_identity FROM employee_registrations WHERE registration_id=?")){p.setObject(1,id);try(ResultSet r=p.executeQuery()){if(r.next())portal=r.getBoolean(1);}}
            if(!portal)cloud.activate(auth);
            c.setAutoCommit(false);
            try {
                try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET status='APPROVED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE registration_id=?")){p.setInt(1,actor);p.setObject(2,id);p.executeUpdate();}
                try(PreparedStatement p=c.prepareStatement("UPDATE users SET is_active=TRUE,deactivated_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE user_id=?")){p.setInt(1,employee);p.executeUpdate();}
                audit(c,id,actor,"APPROVED",null);
                EmploymentApplicationService.event(c,id,"APPROVED","You have been hired. Your account is now linked to your employee profile.","",actor);
                c.commit();
            }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
            return employee;
        } finally { unlock(c,id); }
    }

    public static void review(Connection c, UUID id, boolean reject, String reason, int actor) throws Exception {
        if(reject)required(reason,"Rejection reason",2000);
        lock(c,id);c.setAutoCommit(false);
        try {
            try(PreparedStatement p=c.prepareStatement("UPDATE employee_registrations SET status=?,rejection_reason=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE registration_id=? AND status=?")){
                p.setString(1,reject?"REJECTED":"PENDING");p.setString(2,reject?reason.trim():null);p.setInt(3,actor);p.setObject(4,id);p.setString(5,reject?"PENDING":"REJECTED");if(p.executeUpdate()!=1)throw new IllegalArgumentException("The registration changed. Refresh and try again.");
            }
            audit(c,id,actor,reject?"REJECTED":"REOPENED",reject?reason.trim():null);c.commit();
        }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);unlock(c,id);}
    }
    private static void validateEmployment(Connection c,LanEmployeeAdminService.SaveRequest r)throws SQLException {
        if(r.dateOfBirth()==null||r.hireDate()==null||r.salary()==null||r.salary().signum()<0||r.locationIds()==null||r.locationIds().isEmpty())
            throw new IllegalArgumentException("Birth date, hire date, non-negative pay, and assigned stores are required.");
        if(EmployeePayrollSettingsService.usesSelectablePeriod(r.compensationType())&&(r.payrollPeriodType()==null||r.workHourLimit()==null||r.workHourLimit().signum()<=0))
            throw new IllegalArgumentException("Payroll period and a positive work-hour limit are required.");
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM roles WHERE UPPER(role_name)=UPPER(?)")){p.setString(1,r.role());try(ResultSet rs=p.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Select a valid role.");}}
        for(Integer id:r.locationIds())try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM locations WHERE location_id=?")){p.setObject(1,id);try(ResultSet rs=p.executeQuery()){if(!rs.next())throw new IllegalArgumentException("Select valid assigned stores.");}}
    }
    private static void audit(Connection c,UUID id,int actor,String action,String reason)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO employee_registration_reviews(registration_id,actor_id,action,reason) VALUES(?,?,?,?)")){p.setObject(1,id);p.setInt(2,actor);p.setString(3,action);p.setString(4,reason);p.executeUpdate();}
    }
    static void lock(Connection c,UUID id)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT pg_try_advisory_lock(hashtextextended(?, 17847))")){p.setString(1,id.toString());try(ResultSet r=p.executeQuery()){r.next();if(!r.getBoolean(1))throw new SQLException("This registration is being processed. Try again shortly.","55P03");}}
    }
    static void unlock(Connection c,UUID id)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT pg_advisory_unlock(hashtextextended(?, 17847))")){p.setString(1,id.toString());p.execute();}
    }
}
