package services;

import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.Map;
import static services.StorefrontService.*;

/** Staff-only review of usable launch accounts. Decisions are shared across stores. */
final class CustomerWebReview {
    private CustomerWebReview() { }
    static Map<String,Object> run(Connection c,JsonObject body,int user)throws Exception {
        String action=text(body,"action");StorefrontAdminService.require(c,user,action.startsWith("ACCOUNT_LAUNCH_")?"COMPANY_PREFERENCES":"CUSTOMER_ACCOUNTS");
        var config=StorefrontConfig.load();if(config==null)throw new IllegalArgumentException("Configure the customer website first.");
        var store=new CustomerWebIdentityStore(config);
        if(action.equals("ACCOUNT_LAUNCH_STATE"))return Map.of("policy",store.policy(),"reviews",new JsonArray());
        if(action.equals("ACCOUNT_REVIEW_STATE"))return Map.of("policy",store.policy(),"reviews",store.reviews());
        String reason=text(body,"reason").trim();if(reason.isBlank()||reason.length()>2000)throw new IllegalArgumentException("Enter a reason (up to 2000 characters).");
        StorefrontRuntime.requirePrimary();
        if(action.equals("ACCOUNT_LAUNCH_SAVE")){
            var result=store.policy(body.get("revision").getAsLong(),body.get("allowUnreviewedLinks").getAsBoolean());
            audit(c,user,action,"allowUnreviewedLinks="+result.get("allowUnreviewedLinks")+"; reason="+reason);return Map.of("ok",true);
        }
        String email=CustomerWebAuth.email(text(body,"email"));decide(store,email,body.get("revision").getAsLong(),text(body,"decision"),reason,user);
        StorefrontRuntime.requirePrimary();audit(c,user,"CUSTOMER_ONLINE_LINK_REVIEWED","email="+email+"; decision="+text(body,"decision")+"; reason="+reason);return Map.of("ok",true);
    }
    static void decide(CustomerWebIdentityStore store,String email,long revision,String decision,String reason,int user)throws Exception {
        if(!decision.equals("APPROVED")&&!decision.equals("REJECTED"))throw new IllegalArgumentException("Choose approve or reject.");
        if(reason==null||reason.isBlank()||reason.length()>2000)throw new IllegalArgumentException("Enter a reason for the review.");
        var old=store.read(email);var identity=old.identity().deepCopy();
        if(old.revision()!=revision)throw new CustomerWebIdentityStore.Conflict();
        if(!"PENDING".equals(text(identity,"linkReview"))||!identity.has("verified")||!identity.get("verified").getAsBoolean())throw new IllegalArgumentException("This account is no longer awaiting review. Refresh.");
        identity.addProperty("linkReview",decision);identity.addProperty("reviewedBy",user);identity.addProperty("reviewedAt",Instant.now().toString());identity.addProperty("reviewReason",reason);
        if(decision.equals("REJECTED")){identity.addProperty("verified",false);identity.addProperty("epoch",identity.get("epoch").getAsLong()+1);for(String field:java.util.List.of("challenge","challengeAction","challengeExpires","challengeAttempts"))identity.remove(field);}
        store.write(email,old,identity,false);
    }
}
