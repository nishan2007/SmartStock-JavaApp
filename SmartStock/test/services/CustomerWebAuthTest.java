package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomerWebAuthTest {
    private static final StorefrontConfig CONFIG=new StorefrontConfig("https://shop.test","test-edge-key-that-is-at-least-32-characters",new byte[32],8444,"test-instance",false);
    static final class Coordinator implements CustomerWebIdentityStore.Transport {
        final Map<String,JsonObject> identities=new HashMap<>();JsonObject directory=directory();boolean online=true,launch=true;long policyRevision;
        public synchronized JsonObject exchange(JsonObject b){
            if(!online)throw new IllegalStateException("offline");String action=b.get("action").getAsString();var reply=new JsonObject();
            if(action.equals("RATE")){reply.addProperty("allowed",true);return reply;}if(action.equals("LOOKUP"))return directory;
            if(action.startsWith("POLICY_")){if(action.equals("POLICY_WRITE")){if(b.get("revision").getAsLong()!=policyRevision)throw new CustomerWebIdentityStore.Conflict();launch=b.get("allowUnreviewedLinks").getAsBoolean();policyRevision++;}reply.addProperty("revision",policyRevision);reply.addProperty("allowUnreviewedLinks",launch);return reply;}
            if(action.equals("REVIEW_LIST")){var records=new JsonArray();for(var row:identities.values())if(row.has("reviewPending")&&row.get("reviewPending").getAsBoolean())records.add(row.deepCopy());reply.add("records",records);return reply;}
            String key=b.get("emailKey").getAsString();var old=identities.get(key);long revision=old==null?0:old.get("revision").getAsLong();
            if(action.equals("READ")){if(old!=null)return old.deepCopy();reply.addProperty("revision",0);reply.add("payload",JsonNull.INSTANCE);return reply;}
            if(revision!=b.get("revision").getAsLong())throw new CustomerWebIdentityStore.Conflict();
            if(b.has("launchClaim")&&b.get("launchClaim").getAsBoolean()&&!launch)throw new CustomerWebIdentityStore.Conflict();
            reply.addProperty("revision",revision+1);reply.add("payload",b.get("payload"));reply.addProperty("emailKey",key);if(b.has("reviewPending"))reply.add("reviewPending",b.get("reviewPending"));if(b.has("phoneKey"))reply.add("phoneKey",b.get("phoneKey"));identities.put(key,reply.deepCopy());var result=new JsonObject();result.addProperty("revision",revision+1);return result;
        }
        CustomerWebIdentityStore store(){return new CustomerWebIdentityStore(CONFIG,this,(e,r,i)->{});}
    }
    static JsonObject directory(JsonObject...customers){var result=new JsonObject();var stores=new JsonArray();var store=new JsonObject();var rows=new JsonArray();for(var customer:customers)rows.add(customer);store.add("customers",rows);stores.add(store);result.add("stores",stores);return result;}
    static JsonObject customer(UUID uuid,String email,String phone,String auth){var row=new JsonObject();row.addProperty("uuid",uuid.toString());row.addProperty("email",email);row.addProperty("phone",phone);row.addProperty("auth",auth);row.addProperty("name","Existing customer");row.addProperty("active",true);return row;}
    static JsonObject input(String email){var b=new JsonObject();b.addProperty("email",email);b.addProperty("name","Customer");b.addProperty("phone","6123456");b.addProperty("password","correct horse battery staple");return b;}
    @Test void hashesAreSaltedAndRejectIncorrectPasswords(){var first=CustomerWebPassword.hash("correct horse battery staple");var second=CustomerWebPassword.hash("correct horse battery staple");assertNotEquals(first,second);assertTrue(CustomerWebPassword.verify("correct horse battery staple",first));assertFalse(CustomerWebPassword.verify("wrong password",first));assertFalse(CustomerWebPassword.verify("password","malformed"));assertThrows(IllegalArgumentException.class,()->CustomerWebPassword.hash("short"));}
    @Test void encryptedSyncBindsEmailKeyAndDetectsTampering()throws Exception{var store=new Coordinator().store();var state=new JsonObject();state.addProperty("passwordHash","private-test-value");var sealed=store.seal(state,store.key("one@example.test"));assertFalse(sealed.contains("private-test-value"));assertEquals(state,store.open(sealed,store.key("one@example.test")));assertThrows(Exception.class,()->store.open(sealed,store.key("two@example.test")));byte[] bytes=Base64.getDecoder().decode(sealed);bytes[20]^=1;assertThrows(Exception.class,()->store.open(Base64.getEncoder().encodeToString(bytes),store.key("one@example.test")));}
    @Test void phoneFormattingCollapsesEquivalentNumbers(){assertEquals("+5926123456",CustomerWebAuth.phone("612-3456"));assertEquals("+5926123456",CustomerWebAuth.phone("+592 612 3456"));assertEquals("+5926123456",CustomerWebAuth.phone("00592 6123456"));assertThrows(IllegalArgumentException.class,()->CustomerWebAuth.phone("123"));assertThrows(IllegalArgumentException.class,()->CustomerWebAuth.phone("6123456abc"));}
    @Test void existingAccountRequiresExactRecordedEmailAndKeepsItsIdentity(){UUID customer=UUID.randomUUID(),auth=UUID.randomUUID();var d=directory(customer(customer,"saved@example.test","+5926123456",auth.toString()));var match=CustomerWebAuth.resolve(d,"+5926123456","saved@example.test");assertEquals(customer,match.customer());assertEquals(auth,match.auth());assertTrue(match.existing());assertNull(CustomerWebAuth.resolve(d,"+5926123456","other@example.test"));}
    @Test void ambiguousPhonesAndEmailOnlyMatchesCannotCreateDuplicates(){UUID customer=UUID.randomUUID();assertNull(CustomerWebAuth.resolve(directory(customer(customer,"saved@example.test","+5926123456",""),customer(UUID.randomUUID(),"saved@example.test","+5926123456","")),"+5926123456","saved@example.test"));assertNull(CustomerWebAuth.resolve(directory(customer(customer,"saved@example.test","+5926123456","")),"+5926223456","saved@example.test"));}
    @Test void signupCodeIsOneUseAndPasswordResetRevokesSessions()throws Exception {
        var coordinator=new Coordinator();String[] code={null};var store=coordinator.store();var auth=new CustomerWebAuth(CONFIG,store,(email,value,action)->code[0]=value);var body=input("person@example.test");
        assertTrue(auth.execute("activate",body,"ip").has("message"));assertNotNull(code[0]);var pending=store.read("person@example.test");assertNull(pending.phoneKey());assertFalse(pending.identity().has("passwordHash"));assertFalse(pending.identity().toString().contains(code[0]));
        body.addProperty("code",code[0]);var session=auth.execute("verify",body,"ip");auth.validateSession(session);assertNotNull(store.read("person@example.test").phoneKey());
        assertThrows(IllegalArgumentException.class,()->auth.execute("verify",body,"ip"));assertEquals(session.get("id"),auth.execute("login",body,"ip").get("id"));
        auth.execute("recover",body,"ip");body.addProperty("code",code[0]);body.addProperty("password","new correct password value");var reset=auth.execute("reset",body,"ip");assertThrows(EmploymentPortalCloud.AuthFailure.class,()->auth.validateSession(session));auth.validateSession(reset);
        body.addProperty("password","correct horse battery staple");assertThrows(EmploymentPortalCloud.AuthFailure.class,()->auth.execute("login",body,"ip"));
    }
    @Test void logoutIsVisibleToAnotherServerAndOutagesNeverUseCachedIdentity()throws Exception {
        var coordinator=new Coordinator();String[] code={null};var auth=new CustomerWebAuth(CONFIG,coordinator.store(),(email,value,action)->code[0]=value);var body=input("person@example.test");auth.execute("activate",body,"ip");body.addProperty("code",code[0]);var session=auth.execute("verify",body,"ip");var other=new CustomerWebAuth(CONFIG,coordinator.store(),(a,b,c)->{});other.validateSession(session);auth.logout(session);assertThrows(EmploymentPortalCloud.AuthFailure.class,()->other.validateSession(session));coordinator.online=false;assertThrows(IllegalStateException.class,()->other.execute("login",body,"ip"));assertThrows(IllegalStateException.class,()->other.validateSession(session));}
    @Test void incorrectVerificationCodesAreLimitedAndDoNotClaimThePhone()throws Exception {
        var coordinator=new Coordinator();String[] code={null};var store=coordinator.store();var auth=new CustomerWebAuth(CONFIG,store,(email,value,action)->code[0]=value);var b=input("person@example.test");auth.execute("activate",b,"ip");String wrong=code[0].equals("000000")?"111111":"000000";b.addProperty("code",wrong);for(int i=0;i<5;i++)assertThrows(IllegalArgumentException.class,()->auth.execute("verify",b,"ip"));b.addProperty("code",code[0]);assertThrows(IllegalArgumentException.class,()->auth.execute("verify",b,"ip"));assertNull(store.read("person@example.test").phoneKey());
    }
    @Test void contactMismatchSendsNoCodeAndCreatesNoIdentity()throws Exception {var coordinator=new Coordinator();coordinator.directory=directory(customer(UUID.randomUUID(),"saved@example.test","+5926123456",""));var auth=new CustomerWebAuth(CONFIG,coordinator.store(),(a,b,c)->fail("Must not email a mismatched claimant"));assertTrue(auth.execute("activate",input("attacker@example.test"),"ip").has("message"));assertTrue(coordinator.identities.isEmpty());}
    @Test void stalePasswordChangesCannotOverwriteTheLatestRevision()throws Exception {var store=new Coordinator().store();var original=store.read("person@example.test");var first=new JsonObject();first.addProperty("epoch",1);store.write("person@example.test",original,first,false);assertThrows(CustomerWebIdentityStore.Conflict.class,()->store.write("person@example.test",original,first,false));}
    @Test void launchOnlyPermitsMissingEmailUnlinkedUnambiguousCustomers(){
        UUID id=UUID.randomUUID();var missing=directory(customer(id,"","+5926123456",""));
        assertNull(CustomerWebAuth.resolve(missing,"+5926123456","new@example.test",false));
        var match=CustomerWebAuth.resolve(missing,"+5926123456","new@example.test",true);assertEquals(id,match.customer());assertTrue(match.review());
        assertNull(CustomerWebAuth.resolve(directory(customer(id,"saved@example.test","+5926123456","")),"+5926123456","new@example.test",true));
        assertNull(CustomerWebAuth.resolve(directory(customer(id,"","+5926123456",UUID.randomUUID().toString())),"+5926123456","new@example.test",true));
    }
    @Test void launchAccountWorksBeforeReviewAndApprovalClearsTheQueue()throws Exception {
        var coordinator=new Coordinator();UUID customer=UUID.randomUUID();coordinator.directory=directory(customer(customer,"","+5926123456",""));
        var store=coordinator.store();String[] code={null};var auth=new CustomerWebAuth(CONFIG,store,(a,b,c)->code[0]=b);var body=input("new@example.test");auth.execute("activate",body,"ip");body.addProperty("code",code[0]);var session=auth.execute("verify",body,"ip");
        assertEquals(customer.toString(),session.get("customerUuid").getAsString());auth.validateSession(session);assertEquals(1,store.reviews().size());assertFalse(store.reviews().toString().contains("passwordHash"));
        coordinator.launch=false;assertEquals(session.get("id"),auth.execute("login",body,"ip").get("id"));
        var old=store.read("new@example.test");CustomerWebReview.decide(store,"new@example.test",old.revision(),"APPROVED","Verified customer",7);assertEquals(0,store.reviews().size());auth.validateSession(session);
        assertEquals("Verified customer",store.read("new@example.test").identity().get("reviewReason").getAsString());
    }
    @Test void rejectionRevokesEveryServerAndCannotBeUndoneByPasswordReset()throws Exception {
        var coordinator=new Coordinator();coordinator.directory=directory(customer(UUID.randomUUID(),"","+5926123456",""));var store=coordinator.store();String[] code={null};var auth=new CustomerWebAuth(CONFIG,store,(a,b,c)->code[0]=b);var body=input("new@example.test");auth.execute("activate",body,"ip");body.addProperty("code",code[0]);var session=auth.execute("verify",body,"ip");
        auth.execute("recover",body,"ip");body.addProperty("code",code[0]);var old=store.read("new@example.test");
        assertThrows(CustomerWebIdentityStore.Conflict.class,()->CustomerWebReview.decide(store,"new@example.test",old.revision()-1,"REJECTED","Incorrect claimant",8));
        CustomerWebReview.decide(store,"new@example.test",old.revision(),"REJECTED","Incorrect claimant",8);
        var other=new CustomerWebAuth(CONFIG,coordinator.store(),(a,b,c)->{});assertThrows(EmploymentPortalCloud.AuthFailure.class,()->other.validateSession(session));
        for(String action:List.of("login","recover","reset","activate"))assertThrows(EmploymentPortalCloud.AuthFailure.class,()->other.execute(action,body,"ip"));
        assertNotNull(store.read("new@example.test").phoneKey());assertEquals(0,store.reviews().size());
    }
    @Test void closingLaunchBlocksUnfinishedClaims()throws Exception {
        var coordinator=new Coordinator();coordinator.directory=directory(customer(UUID.randomUUID(),"","+5926123456",""));var store=coordinator.store();String[] code={null};var auth=new CustomerWebAuth(CONFIG,store,(a,b,c)->code[0]=b);var body=input("new@example.test");auth.execute("activate",body,"ip");body.addProperty("code",code[0]);store.policy(0,false);
        assertThrows(IllegalArgumentException.class,()->auth.execute("verify",body,"ip"));assertNull(store.read("new@example.test").phoneKey());
    }
}
