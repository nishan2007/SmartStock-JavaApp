package services;

import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static services.StorefrontService.*;

/** Customer-only store-hosted authentication, independent of employee and Supabase login. */
final class CustomerWebAuth {
    private final CustomerWebIdentityStore store;
    private final StorefrontConfig config;
    @FunctionalInterface interface CodeSender {void send(String email,String code,String action)throws Exception;}
    private final CodeSender sender;
    CustomerWebAuth(StorefrontConfig config){this.config=config;store=new CustomerWebIdentityStore(config);sender=this::sendCode;}
    CustomerWebAuth(StorefrontConfig config,CustomerWebIdentityStore store,CodeSender sender){this.config=config;this.store=store;this.sender=sender;}
    record Candidate(UUID customer,UUID auth,String email,String name,boolean existing,boolean review) { Candidate(UUID customer,UUID auth,String email,String name,boolean existing){this(customer,auth,email,name,existing,false);} }
    static String phone(String input){
        if(input==null||!input.matches("[+0-9 ()\\-]{7,32}"))throw new IllegalArgumentException("Enter your customer phone number, including country code outside Guyana.");
        String digits=input.replaceAll("[^0-9]","");if(digits.startsWith("00"))digits=digits.substring(2);if(digits.length()==7)digits="592"+digits;if(digits.length()<8||digits.length()>15)throw new IllegalArgumentException("Enter a valid phone number.");return "+"+digits;
    }
    static String email(String input){return StorefrontIdentityDirectory.email(input);}
    JsonObject execute(String action,JsonObject body,String ip)throws Exception {
        String email=email(text(body,"email"));store.rate(email,ip);
        var record=store.read(email);var identity=record.identity().deepCopy();
        if("REJECTED".equals(text(identity,"linkReview")))throw new EmploymentPortalCloud.AuthFailure("Online access was rejected by your store. Contact an employee to review your account.");
        if(action.equals("activate")||action.equals("recover")){
            if(action.equals("activate")&&identity.has("passwordHash"))return message();
            if(action.equals("recover")&&!identity.has("passwordHash"))return message();
            if(action.equals("activate")){
                String phone=phone(text(body,"phone")),name=text(body,"name").trim();if(name.isBlank()||name.length()>150)throw new IllegalArgumentException("Enter your name (up to 150 characters).");
                Candidate candidate=resolve(store.lookup(phone,email),phone,email,store.policy().get("allowUnreviewedLinks").getAsBoolean());
                if(candidate==null)return message(); // Never reveal whether a supplied phone/email exists.
                identity.addProperty("id",candidate.auth().toString());identity.addProperty("customerUuid",candidate.customer().toString());identity.addProperty("email",email);identity.addProperty("phone",phone);identity.addProperty("name",candidate.existing()?candidate.name():name);identity.addProperty("existingCustomer",candidate.existing());identity.addProperty("epoch",1);identity.remove("linkReview");identity.remove("linkRequestedAt");if(candidate.review()){identity.addProperty("linkReview","PENDING");identity.addProperty("linkRequestedAt",Instant.now().toString());}
            }
            String code=String.format(Locale.ROOT,"%06d",new java.security.SecureRandom().nextInt(1_000_000));
            identity.addProperty("challenge",store.key("code:"+email+":"+code));identity.addProperty("challengeAction",action);identity.addProperty("challengeExpires",Instant.now().getEpochSecond()+600);identity.addProperty("challengeAttempts",0);
            store.write(email,record,identity,false);
            sender.send(email,code,action);return message();
        }
        if(action.equals("verify")||action.equals("reset")){
            CustomerWebPassword.validate(text(body,"password"));
            String expectedAction=action.equals("reset")?"recover":"activate";
            boolean valid=identity.has("challenge")&&expectedAction.equals(text(identity,"challengeAction"))&&identity.get("challengeExpires").getAsLong()>Instant.now().getEpochSecond()&&identity.get("challengeAttempts").getAsInt()<5&&LanSecurity.constantTimeEquals(text(identity,"challenge"),store.key("code:"+email+":"+text(body,"code")));
            if(!valid){if(identity.has("challenge")){identity.addProperty("challengeAttempts",identity.get("challengeAttempts").getAsInt()+1);store.write(email,record,identity,false);}throw new IllegalArgumentException("The code is invalid or expired. Request a new verification email.");}
            // Recheck the entire store directory before claiming a previously unlinked identity.
            if(action.equals("verify")){
                var candidate=resolve(store.lookup(text(identity,"phone"),email),text(identity,"phone"),email,store.policy().get("allowUnreviewedLinks").getAsBoolean());
                if(candidate==null||!candidate.customer().toString().equals(text(identity,"customerUuid"))||candidate.review()!= "PENDING".equals(text(identity,"linkReview")))throw new IllegalArgumentException("Your customer record changed. Ask a store employee to review the link.");
            }
            identity.addProperty("passwordHash",CustomerWebPassword.hash(text(body,"password")));identity.addProperty("verified",true);identity.addProperty("epoch",identity.get("epoch").getAsLong()+1);identity.remove("challenge");identity.remove("challengeAction");identity.remove("challengeExpires");identity.remove("challengeAttempts");
            store.write(email,record,identity,false);return session(identity);
        }
        if(!action.equals("login"))throw new IllegalArgumentException("Unknown account action.");
        // Unknown users still perform the same expensive password primitive.
        boolean valid=identity.has("passwordHash")?CustomerWebPassword.verify(text(body,"password"),text(identity,"passwordHash")):dummy(text(body,"password"));
        if(!valid||!identity.has("verified")||!identity.get("verified").getAsBoolean())throw new EmploymentPortalCloud.AuthFailure("Email or password is incorrect. Existing online customers should activate their store-hosted account first.");
        return session(identity);
    }
    private static boolean dummy(String password){CustomerWebPassword.verify(password,DUMMY);return false;}
    private static final String DUMMY=CustomerWebPassword.hash("dummy-password-not-an-account");
    static JsonObject message(){var r=new JsonObject();r.addProperty("message","If your details can be verified, a code has been emailed. If no email arrives, ask a store employee to check your account email and phone. No new customer record is created when contacts conflict.");return r;}
    private JsonObject session(JsonObject identity){
        var result=new JsonObject();for(String field:List.of("id","email","customerUuid","phone","name","epoch","existingCustomer"))if(identity.has(field))result.add(field,identity.get(field));result.addProperty("provider","store-v1");result.addProperty("csrf",LanSecurity.randomToken());result.addProperty("sessionId",LanSecurity.randomToken());result.addProperty("expires",Instant.now().getEpochSecond()+3600);result.addProperty("issuedAt",System.currentTimeMillis());return result;
    }
    static void requireSnapshot(JsonObject snapshot,JsonObject session){
        JsonObject customer=StorefrontService.customer(snapshot,UUID.fromString(text(session,"id")),text(session,"email"));
        if(!text(session,"customerUuid").equals(text(customer,"uuid")))throw new IllegalArgumentException("This store's customer link is awaiting synchronization. Please retry shortly or contact a store employee.");
        snapshot.addProperty("_verifiedCustomerUuid",text(session,"customerUuid"));
    }
    void validateSession(JsonObject session)throws Exception {
        if(!"store-v1".equals(text(session,"provider")))throw new EmploymentPortalCloud.AuthFailure("Please activate or sign in to your store-hosted account.");
        var identity=store.read(email(text(session,"email"))).identity();
        if(!identity.has("verified")||!identity.get("verified").getAsBoolean()||!text(identity,"id").equals(text(session,"id"))||!text(identity,"epoch").equals(text(session,"epoch"))||!text(identity,"customerUuid").equals(text(session,"customerUuid")))throw new EmploymentPortalCloud.AuthFailure("Your account changed. Sign in again.");
        if(identity.has("revokedSessions"))for(var id:identity.getAsJsonArray("revokedSessions"))if(id.getAsString().equals(text(session,"sessionId")))throw new EmploymentPortalCloud.AuthFailure("Sign in again.");
    }
    void logout(JsonObject session)throws Exception {
        String email=email(text(session,"email"));var old=store.read(email);var identity=old.identity().deepCopy();var revoked=identity.has("revokedSessions")?identity.getAsJsonArray("revokedSessions"):new JsonArray();
        // A bounded list rolls over by revoking every session, rather than reviving old ones.
        if(revoked.size()>=100){identity.addProperty("epoch",identity.get("epoch").getAsLong()+1);revoked=new JsonArray();}else revoked.add(text(session,"sessionId"));identity.add("revokedSessions",revoked);store.write(email,old,identity,false);
    }
    static Candidate resolve(JsonObject directory,String phone,String email){return resolve(directory,phone,email,false);}
    static Candidate resolve(JsonObject directory,String phone,String email,boolean allowReview){
        var byUuid=new LinkedHashMap<String,JsonObject>();
        for(var store:directory.getAsJsonArray("stores"))for(var entry:store.getAsJsonObject().getAsJsonArray("customers")){
            var row=entry.getAsJsonObject();String uuid=text(row,"uuid");var old=byUuid.putIfAbsent(uuid,row);
            if(old!=null){if(!text(old,"email").equalsIgnoreCase(text(row,"email"))||!text(old,"phone").equals(text(row,"phone"))||old.get("active").getAsBoolean()!=row.get("active").getAsBoolean()||!text(old,"auth").isBlank()&&!text(row,"auth").isBlank()&&!text(old,"auth").equals(text(row,"auth")))return null;if(text(old,"auth").isBlank()&&!text(row,"auth").isBlank())old.add("auth",row.get("auth"));}
        }
        var phoneMatches=new ArrayList<JsonObject>();for(var row:byUuid.values())if(phone.equals(text(row,"phone")))phoneMatches.add(row);
        if(phoneMatches.size()>1)return null;
        if(phoneMatches.size()==1){var row=phoneMatches.get(0);if(byUuid.size()!=1||!row.get("active").getAsBoolean())return null;
            if(!email.equalsIgnoreCase(text(row,"email"))){if(!allowReview||!text(row,"email").isBlank()||!text(row,"auth").isBlank())return null;return new Candidate(UUID.fromString(text(row,"uuid")),UUID.nameUUIDFromBytes(("customer-web:"+email).getBytes(java.nio.charset.StandardCharsets.UTF_8)),email,text(row,"name"),true,true);}
            String prior=text(row,"auth");return new Candidate(UUID.fromString(text(row,"uuid")),prior.isBlank()?UUID.nameUUIDFromBytes(("customer-web:"+email).getBytes(java.nio.charset.StandardCharsets.UTF_8)):UUID.fromString(prior),email,text(row,"name"),true);
        }
        // An email-only match must be corrected by staff, rather than making a duplicate.
        if(!byUuid.isEmpty())return null;
        UUID auth=UUID.nameUUIDFromBytes(("customer-web:"+email).getBytes(java.nio.charset.StandardCharsets.UTF_8));return new Candidate(UUID.nameUUIDFromBytes(("deckers-customer:"+auth).getBytes(java.nio.charset.StandardCharsets.UTF_8)),auth,email,"",false);
    }
    static JsonObject lookup(Connection c,String suppliedPhone,String suppliedEmail,int location)throws SQLException {
        String phone=phone(suppliedPhone),email=email(suppliedEmail);var result=new JsonObject();var customers=new JsonArray();
        try(var p=c.prepareStatement("SELECT ca.sync_uuid,ca.name,lower(btrim(COALESCE(ca.email,''))),public.customer_phone_key(ca.phone),ca.is_active,l.auth_id FROM customer_accounts ca LEFT JOIN storefront.customer_links l ON l.customer_uuid=ca.sync_uuid AND l.location_id=? WHERE ca.customer_id=public.canonical_customer_id(ca.customer_id) AND (public.customer_phone_key(ca.phone)=? OR lower(btrim(ca.email))=?) ORDER BY ca.customer_id LIMIT 10")){p.setInt(1,location);p.setString(2,phone);p.setString(3,email);try(var r=p.executeQuery()){while(r.next()){var row=new JsonObject();row.addProperty("uuid",r.getObject(1).toString());row.addProperty("name",r.getString(2));row.addProperty("email",r.getString(3));row.addProperty("phone",r.getString(4));row.addProperty("active",r.getBoolean(5));row.addProperty("auth",r.getObject(6)==null?"":r.getObject(6).toString());customers.add(row);}}}
        // Retired phone reservations also block creating a fresh customer.
        if(customers.isEmpty())try(var p=c.prepareStatement("SELECT 1 FROM customer_phone_ownership WHERE phone_key=?")){p.setString(1,phone);try(var r=p.executeQuery()){if(r.next()){var blocked=new JsonObject();blocked.addProperty("uuid",UUID.nameUUIDFromBytes(phone.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());blocked.addProperty("phone",phone);blocked.addProperty("email","");blocked.addProperty("active",false);customers.add(blocked);}}}
        result.add("customers",customers);return result;
    }
    static void relink(Connection c,UUID auth,UUID customer,String email,String reason,int user,int location)throws Exception {
        // Existing explicit employee permission, enforced on the server as well as in the UI.
        StorefrontAdminService.require(c,user,"CUSTOMER_ACCOUNTS");
        if(reason==null||reason.isBlank()||reason.length()>2000)throw new IllegalArgumentException("Enter a reason for changing the online account link.");
        var config=StorefrontConfig.load();if(config==null)throw new IllegalArgumentException("Configure the customer website first.");
        var store=new CustomerWebIdentityStore(config);var old=store.read(email);var identity=old.identity().deepCopy();
        if(!identity.has("verified")||!identity.get("verified").getAsBoolean()||!auth.toString().equals(text(identity,"id")))throw new IllegalArgumentException("Search for the verified customer identity again.");
        var target=one(rows(c,"SELECT sync_uuid,name,email,phone FROM customer_accounts WHERE sync_uuid=? AND is_active AND lower(btrim(email))=? FOR UPDATE",customer,email));if(target.entrySet().isEmpty())throw new IllegalArgumentException("Update the selected customer's email to the verified online email first.");
        String phone=phone(text(target,"phone"));var prior=one(rows(c,"SELECT auth_id,email FROM storefront.customer_links WHERE customer_uuid=? AND location_id=? FOR UPDATE",customer,location));
        String previousEmail=text(prior,"email");CustomerWebIdentityStore.Record previous=null;JsonObject revoked=null;
        if(!previousEmail.isBlank()&&!previousEmail.equalsIgnoreCase(email)){
            previous=store.read(previousEmail);revoked=previous.identity().deepCopy();if(!text(revoked,"id").equals(text(prior,"auth_id")))throw new IllegalArgumentException("The previous online identity requires administrator review.");
            revoked.addProperty("verified",false);revoked.addProperty("epoch",revoked.get("epoch").getAsLong()+1);revoked.remove("challenge");
        }
        identity.addProperty("customerUuid",customer.toString());identity.addProperty("phone",phone);identity.addProperty("name",text(target,"name"));identity.addProperty("existingCustomer",true);identity.addProperty("epoch",identity.get("epoch").getAsLong()+1);
        StorefrontRuntime.requirePrimary();
        store.write(email,old,identity,true,previousEmail,previous,revoked);
        StorefrontRuntime.requirePrimary();
        StorefrontService.execute(c,"DELETE FROM storefront.customer_links WHERE location_id=? AND (customer_uuid=? OR auth_id=?)",location,customer,auth);
        StorefrontAdminService.linkCustomer(c,auth,customer,email,location);
        StorefrontService.execute(c,"INSERT INTO security_audit_events(event_type,actor_user_id,details) VALUES('CUSTOMER_WEB_ACCOUNT_RELINKED',?,?)",user,"customer="+customer+"; auth="+auth+"; previous_auth="+text(prior,"auth_id")+"; reason="+reason);
    }
    private void sendCode(String recipient,String code,String action)throws Exception {
        String address,name;try(var c=data.DB.getConnection();var p=c.prepareStatement("SELECT email_sender_address,email_sender_name FROM locations WHERE location_id=?")){p.setInt(1,data.DatabaseConfig.load().locationId());try(var r=p.executeQuery()){if(!r.next()||r.getString(1)==null||r.getString(1).isBlank())throw new IllegalStateException("The store email sender is not configured.");address=r.getString(1);name=r.getString(2);}}
        String text="Your Deckers "+(action.equals("recover")?"password reset":"account verification")+" code is "+code+". It expires in 10 minutes. If you did not request this, ignore this email.";
        GmailOAuthService.send(new GmailOAuthService.GmailMessage(address,name,recipient,"","Deckers account verification",text,null,null,null,null));
    }
}
