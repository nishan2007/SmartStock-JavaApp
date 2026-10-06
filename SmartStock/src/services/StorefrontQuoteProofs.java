package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import static services.StorefrontService.*;

/** Private design proofs, with an auditable customer decision for each revision. */
final class StorefrontQuoteProofs {
    private StorefrontQuoteProofs() { }

    static JsonObject publish(Connection c,int location,int staff,JsonObject body)throws Exception {
        UUID request=UUID.fromString(text(body,"requestId")),proof=UUID.fromString(text(body,"proofId"));
        JsonObject quote=one(rows(c,"SELECT status,email FROM storefront.quote_requests WHERE request_id=? AND location_id=? FOR UPDATE",request,location));
        if(quote.entrySet().isEmpty()||java.util.Set.of("COMPLETED","CANCELLED").contains(text(quote,"status")))
            throw new IllegalArgumentException("This request is closed or belongs to another store.");
        if(!rows(c,"SELECT 1 FROM storefront.quote_order_links WHERE request_id=?",request).isEmpty())
            throw new IllegalArgumentException("This request is already linked to a SmartStock production order. Manage designs in that order.");
        String filename=text(body,"filename").trim().replace('\\','/');filename=filename.substring(filename.lastIndexOf('/')+1);
        if(filename.length()<1||filename.length()>180||filename.contains("..")||!filename.matches("[A-Za-z0-9 _().-]+"))
            throw new IllegalArgumentException("Choose a proof with a simple filename.");
        String ext=filename.substring(filename.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String type=switch(ext){case "jpg","jpeg"->"image/jpeg";case "png"->"image/png";case "pdf"->"application/pdf";default->throw new IllegalArgumentException("Use a JPEG, PNG, or PDF design proof.");};
        String encoded=text(body,"bytesBase64");if(encoded.length()>5_600_000)throw new IllegalArgumentException("Proofs must be 4 MB or smaller.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Invalid proof file.");}
        if(bytes.length<5||bytes.length>4_194_304)throw new IllegalArgumentException("Proofs must be 4 MB or smaller.");
        if(type.equals("image/jpeg")&&!((bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)
                ||type.equals("image/png")&&!((bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71)
                ||type.equals("application/pdf")&&!(bytes[0]=='%'&&bytes[1]=='P'&&bytes[2]=='D'&&bytes[3]=='F'&&bytes[4]=='-'))
            throw new IllegalArgumentException("The proof does not match its file type.");
        String sha=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        JsonObject old=one(rows(c,"SELECT request_id,original_filename,sha256,status,revision FROM storefront.quote_proofs WHERE proof_id=?",proof));
        if(!old.entrySet().isEmpty()){
            if(!request.toString().equals(text(old,"request_id"))||!filename.equals(text(old,"original_filename"))||!sha.equals(text(old,"sha256")))
                throw new IllegalArgumentException("This proof identifier was already used for another file.");
            return summary(proof,old);
        }
        execute(c,"UPDATE storefront.quote_proofs SET status='SUPERSEDED',decided_at=now() WHERE request_id=? AND status='AWAITING_APPROVAL'",request);
        int revision=integer(one(rows(c,"SELECT COALESCE(MAX(revision),0)+1 AS revision FROM storefront.quote_proofs WHERE request_id=?",request)),"revision");
        String reference=ServerImageAssetService.storeUpload(c,"QUOTE_ARTWORK","deckers-creative",
            "proofs/"+request+"/"+proof+"."+ext,type,filename,"AUTHENTICATED",bytes);
        execute(c,"""
            INSERT INTO storefront.quote_proofs(proof_id,request_id,revision,asset_reference,original_filename,content_type,byte_size,sha256,published_by)
            VALUES(?,?,?,?,?,?,?,?,?)
            """,proof,request,revision,reference,filename,type,bytes.length,sha,staff);
        execute(c,"INSERT INTO storefront.quote_proof_events(proof_id,action,staff_user_id) VALUES(?,'PUBLISHED',?)",proof,staff);
        execute(c,"UPDATE storefront.quote_requests SET status='AWAITING_APPROVAL',updated_at=now() WHERE request_id=?",request);
        ServerEmailOutboxService.queueStorefrontProof(c,location,text(quote,"email"),request.toString(),revision);
        JsonObject result=new JsonObject();result.addProperty("proofId",proof.toString());result.addProperty("revision",revision);
        result.addProperty("status","AWAITING_APPROVAL");return result;
    }

    static JsonArray customer(Connection c,UUID auth)throws SQLException {
        return rows(c,"""
            SELECT p.proof_id AS "proofId",p.request_id AS "requestId",q.location_id AS "locationId",q.topic,
                   p.revision,p.original_filename AS filename,p.content_type AS "contentType",p.status,
                   p.created_at AS "createdAt",p.decided_at AS "decidedAt"
            FROM storefront.quote_proofs p JOIN storefront.quote_requests q ON q.request_id=p.request_id
            WHERE q.auth_id=? ORDER BY p.created_at DESC LIMIT 100
            """,auth);
    }

    static JsonArray history(Connection c,UUID auth)throws SQLException {
        return rows(c,"""
            SELECT e.proof_id AS "proofId",e.action,e.note,e.created_at AS "createdAt"
            FROM storefront.quote_proof_events e JOIN storefront.quote_proofs p ON p.proof_id=e.proof_id
            JOIN storefront.quote_requests q ON q.request_id=p.request_id
            WHERE q.auth_id=? ORDER BY e.created_at DESC LIMIT 300
            """,auth);
    }

    static JsonObject customerFile(Connection c,int location,UUID auth,UUID proof)throws Exception {
        JsonObject row=one(rows(c,"""
            SELECT p.asset_reference,p.content_type,p.original_filename
            FROM storefront.quote_proofs p JOIN storefront.quote_requests q ON q.request_id=p.request_id
            WHERE p.proof_id=? AND q.location_id=? AND q.auth_id=?
            """,proof,location,auth));
        if(row.entrySet().isEmpty())throw new IllegalArgumentException("This design proof is unavailable.");
        var data=ServerImageAssetService.load(c,text(row,"asset_reference"));
        JsonObject result=new JsonObject();result.addProperty("filename",text(row,"original_filename"));
        result.addProperty("contentType",text(row,"content_type"));
        result.addProperty("bytesBase64",Base64.getEncoder().encodeToString(data.bytes()));return result;
    }

    static JsonObject decide(Connection c,int location,UUID auth,JsonObject body)throws Exception {
        UUID proof=UUID.fromString(text(body,"proofId"));String action=text(body,"decision");
        if(!action.equals("APPROVE")&&!action.equals("REQUEST_CHANGES"))throw new IllegalArgumentException("Choose an approval decision.");
        String note=text(body,"note").trim();if(note.length()>2000||(action.equals("REQUEST_CHANGES")&&note.isBlank()))
            throw new IllegalArgumentException("Describe the requested changes in 2,000 characters or less.");
        JsonObject quote=one(rows(c,"""
            SELECT q.request_id,q.status AS quote_status
            FROM storefront.quote_requests q JOIN storefront.quote_proofs p ON p.request_id=q.request_id
            WHERE p.proof_id=? AND q.location_id=? AND q.auth_id=? FOR UPDATE OF q
            """,proof,location,auth));
        if(quote.entrySet().isEmpty())throw new IllegalArgumentException("This design proof is unavailable.");
        JsonObject row=one(rows(c,"SELECT status FROM storefront.quote_proofs WHERE proof_id=? FOR UPDATE",proof));
        String next=action.equals("APPROVE")?"APPROVED":"CHANGES_REQUESTED";
        if(!"AWAITING_APPROVAL".equals(text(row,"status"))||!"AWAITING_APPROVAL".equals(text(quote,"quote_status")))
            throw new IllegalArgumentException("This proof is no longer awaiting approval.");
        execute(c,"UPDATE storefront.quote_proofs SET status=?,decided_at=now() WHERE proof_id=?",next,proof);
        execute(c,"UPDATE storefront.quote_requests SET status=?,updated_at=now() WHERE request_id=?",next,UUID.fromString(text(quote,"request_id")));
        execute(c,"INSERT INTO storefront.quote_proof_events(proof_id,action,auth_id,note) VALUES(?,?,?,?)",proof,action.equals("APPROVE")?"APPROVED":"CHANGES_REQUESTED",auth,note);
        JsonObject result=new JsonObject();result.addProperty("proofId",proof.toString());result.addProperty("status",next);return result;
    }
    private static JsonObject summary(UUID id,JsonObject row){JsonObject result=new JsonObject();result.addProperty("proofId",id.toString());result.addProperty("revision",integer(row,"revision"));result.addProperty("status",text(row,"status"));return result;}
}
