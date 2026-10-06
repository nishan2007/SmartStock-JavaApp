package services;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static services.StorefrontService.*;

/** Verified account enrollment is durable before login succeeds, independently of checkout. */
final class StorefrontEnrollmentService {
    private StorefrontEnrollmentService() { }

    static JsonObject enroll(Connection c,int location,UUID auth,String email,String name,int serving)throws SQLException {
        if(location<=0)throw new IllegalArgumentException("Choose a pickup store before activating your account.");
        email=StorefrontIdentityDirectory.email(email);name=name.trim();if(name.length()>150)throw new IllegalArgumentException("Use a name with at most 150 characters.");
        if(name.isBlank())name=email;
        lock(c,location);
        UUID id=UUID.nameUUIDFromBytes(("deckers-enrollment:"+location+":"+auth).getBytes(StandardCharsets.UTF_8));
        JsonObject existing=one(rows(c,"SELECT * FROM storefront.enrollments WHERE enrollment_id=?",id));
        if(!existing.entrySet().isEmpty()&&!email.equalsIgnoreCase(text(existing,"email")))
            throw new IllegalArgumentException("Your website email changed. Ask your store to update your customer link.");
        execute(c,"INSERT INTO storefront.enrollments(enrollment_id,auth_id,location_id,email,name,source_server) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING",id,auth,location,email,name,serving);
        if(location==serving)complete(c,id);
        return one(rows(c,"SELECT * FROM storefront.enrollments WHERE enrollment_id=?",id));
    }

    private static void complete(Connection c,UUID id)throws SQLException {
        JsonObject enrollment=one(rows(c,"SELECT * FROM storefront.enrollments WHERE enrollment_id=? FOR UPDATE",id));
        if(!"PENDING".equals(text(enrollment,"status")))return;
        int location=integer(enrollment,"location_id");UUID auth=UUID.fromString(text(enrollment,"auth_id"));String email=text(enrollment,"email");
        Savepoint point=c.setSavepoint();
        try{
            JsonObject context=new JsonObject();
            context.add("links",rows(c,"SELECT auth_id AS auth,customer_uuid AS customer FROM storefront.customer_links WHERE location_id=? AND (auth_id=? OR customer_uuid IN (SELECT sync_uuid FROM customer_accounts WHERE lower(btrim(email))=?))",location,auth,email));
            context.add("customers",rows(c,"SELECT sync_uuid AS uuid,name,email,is_active AS active FROM customer_accounts WHERE lower(btrim(email))=? OR sync_uuid=(SELECT customer_uuid FROM storefront.customer_links WHERE auth_id=? AND location_id=?)",email,auth,location));
            JsonObject customer=customer(context,auth,email);UUID uuid=UUID.fromString(text(customer,"uuid"));
            execute(c,"INSERT INTO customer_accounts(sync_uuid,name,email) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM customer_accounts WHERE sync_uuid=?)",uuid,text(enrollment,"name"),email,uuid);
            StorefrontAdminService.linkCustomer(c,auth,uuid,email,location);
            c.releaseSavepoint(point);
        }catch(IllegalArgumentException e){
            c.rollback(point);c.releaseSavepoint(point);
            execute(c,"UPDATE storefront.enrollments SET status='NEEDS_ATTENTION',note=?,revision=revision+1,updated_at=now() WHERE enrollment_id=?",e.getMessage(),id);
        }catch(SQLException e){c.rollback(point);c.releaseSavepoint(point);throw e;}
    }

    static void importEnrollment(Connection c,JsonObject incoming,int local)throws SQLException {
        UUID id=UUID.fromString(text(incoming,"enrollment_id"));int location=integer(incoming,"location_id");lock(c,location);
        JsonObject old=one(rows(c,"SELECT * FROM storefront.enrollments WHERE enrollment_id=? FOR UPDATE",id));
        if(!old.entrySet().isEmpty()){
            for(String key:List.of("auth_id","location_id","email"))
                if(!text(old,key).equals(text(incoming,key)))throw new IllegalArgumentException("Account enrollment identity cannot change during handoff.");
            if(old.get("revision").getAsLong()>=incoming.get("revision").getAsLong())return;
        }
        execute(c,"""
            INSERT INTO storefront.enrollments(enrollment_id,auth_id,location_id,email,name,source_server,status,customer_uuid,note,revision,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?::uuid,?,?,?::timestamptz,?::timestamptz)
            ON CONFLICT(enrollment_id) DO UPDATE SET status=EXCLUDED.status,customer_uuid=EXCLUDED.customer_uuid,
              note=EXCLUDED.note,revision=EXCLUDED.revision,updated_at=EXCLUDED.updated_at
            """,id,UUID.fromString(text(incoming,"auth_id")),location,text(incoming,"email"),text(incoming,"name"),integer(incoming,"source_server"),text(incoming,"status"),nullable(incoming,"customer_uuid"),text(incoming,"note"),incoming.get("revision").getAsLong(),text(incoming,"created_at"),text(incoming,"updated_at"));
        if(location==local)complete(c,id);
    }
}
