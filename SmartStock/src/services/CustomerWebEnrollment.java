package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static services.StorefrontService.*;

/** A verified identity always binds the exact canonical customer, never an email guess. */
final class CustomerWebEnrollment {
    private CustomerWebEnrollment() { }
    static void enroll(Connection c,int location,JsonObject identity,int serving)throws SQLException {
        UUID auth=UUID.fromString(text(identity,"id"));String email=text(identity,"email");
        UUID id=UUID.nameUUIDFromBytes(("deckers-enrollment:"+location+":"+auth).getBytes(StandardCharsets.UTF_8));
        execute(c,"INSERT INTO storefront.enrollments(enrollment_id,auth_id,location_id,email,name,source_server,note) VALUES(?,?,?,?,?,?,?) ON CONFLICT(enrollment_id) DO UPDATE SET status='PENDING',note=EXCLUDED.note,revision=storefront.enrollments.revision+1,updated_at=now() WHERE storefront.enrollments.status<>'LINKED' OR storefront.enrollments.customer_uuid IS DISTINCT FROM ?::uuid",id,auth,location,email,text(identity,"name"),serving,"store-auth-v1",text(identity,"customerUuid"));
        StorefrontEnrollmentService.enroll(c,location,auth,email,text(identity,"name"),serving);
    }
    static void complete(Connection c,JsonObject enrollment)throws SQLException {
        try{
            var config=StorefrontConfig.load();if(config==null)throw new IllegalStateException("Configure the customer website.");
            var identity=new CustomerWebIdentityStore(config).read(text(enrollment,"email")).identity();
            if(!identity.has("verified")||!identity.get("verified").getAsBoolean()||!text(identity,"id").equals(text(enrollment,"auth_id")))throw new IllegalArgumentException("The verified online identity changed. Ask a store employee to review it.");
            UUID customer=UUID.fromString(text(identity,"customerUuid")),auth=UUID.fromString(text(identity,"id"));String email=text(identity,"email"),phone=text(identity,"phone");
            var local=rows(c,"SELECT customer_id,email,public.customer_phone_key(phone) AS phone,is_active FROM customer_accounts WHERE sync_uuid=? FOR UPDATE",customer);
            if(local.isEmpty()){
                // An existing record must arrive through customer synchronization, not a duplicate INSERT.
                if(identity.get("existingCustomer").getAsBoolean())throw new IllegalArgumentException("Your customer record is awaiting this store's synchronization. Ask a store employee to review it.");
                CustomerPhoneOwnership.requireAvailable(c,phone,null);
                execute(c,"INSERT INTO customer_accounts(sync_uuid,name,email,phone) VALUES(?,?,?,?)",customer,text(identity,"name"),email,phone);
            }else {
                var row=local.get(0).getAsJsonObject();boolean launch=java.util.Set.of("PENDING","APPROVED").contains(text(identity,"linkReview"));
                if((!email.equalsIgnoreCase(text(row,"email"))&&!(launch&&text(row,"email").isBlank()))||!phone.equals(text(row,"phone"))||!row.get("is_active").getAsBoolean())throw new IllegalArgumentException("Your store account contact details changed. Ask an employee to review the link.");
            }
            // Repair an interrupted staff reassignment only after checking the displaced
            // identity's current authoritative state. Never trust a stale local link.
            int location=integer(enrollment,"location_id");
            for(var entry:rows(c,"SELECT auth_id,email FROM storefront.customer_links WHERE location_id=? AND customer_uuid=? AND auth_id<>? FOR UPDATE",location,customer,auth)){
                var prior=entry.getAsJsonObject();var displaced=new CustomerWebIdentityStore(config).read(text(prior,"email")).identity();
                if(displaced.has("verified")&&displaced.get("verified").getAsBoolean()&&customer.toString().equals(text(displaced,"customerUuid")))throw new IllegalArgumentException("This customer still has another verified online account. Ask an authorized employee to review it.");
                execute(c,"DELETE FROM storefront.customer_links WHERE location_id=? AND auth_id=?",location,UUID.fromString(text(prior,"auth_id")));
            }
            // Keep the proposed email on the online link; do not overwrite the store's
            // contact record while a launch claim is awaiting employee review.
            StorefrontAdminService.linkCustomer(c,auth,customer,email,location,java.util.Set.of("PENDING","APPROVED").contains(text(identity,"linkReview")));
        }catch(SQLException e){throw e;}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new SQLException("Current customer identity verification is unavailable. Retry enrollment after connectivity returns.",e);}
    }
}
