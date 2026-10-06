package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Studio gets its own key and token, attached to the physical device's existing row. */
final class StudioDeviceEnrollmentService {
    private StudioDeviceEnrollmentService() { }
    record Enrollment(UUID deviceId, boolean allowed) { }
    record Claim(UUID deviceId, int locationId, String token, Instant expiresAt) { }
    record Principal(UUID deviceId, String installationId, int locationId) { }
    static final class Denied extends Exception {
        final String code; final int status;
        Denied(int status,String code,String message) { super(message);this.status=status;this.code=code; }
    }
    static String value(JsonObject body,String key) {
        return body.has(key)&&!body.get(key).isJsonNull()?body.get(key).getAsString().trim():"";
    }
    static Enrollment enroll(Connection c, JsonObject body, int location, String challenge) throws Exception {
        String installation=value(body,"installationId"), key=value(body,"publicKey"), fingerprint=value(body,"deviceFingerprint"), hostname=value(body,"hostname");
        UUID device=null,legacy=null;
        // Serializes enrollment of companion apps on the same machine without locking unrelated devices.
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) { p.setString(1,"studio|"+fingerprint+"|"+hostname+"|"+location);p.execute(); }
        try(var p=c.prepareStatement("SELECT device_id,public_key,location_id FROM studio_device_clients WHERE installation_id=? FOR UPDATE")) {
            p.setString(1,installation);try(var r=p.executeQuery()) { if(r.next()) {
                if (!LanSecurity.constantTimeEquals(key,r.getString(2))) throw new Denied(409,"DEVICE_IDENTITY_MISMATCH","The Studio key changed. Administrator recovery is required.");
                if (r.getInt(3)!=location) throw new Denied(403,"DEVICE_STORE_MISMATCH","This Studio computer is paired with another store.");
                device=(UUID)r.getObject(1);
            }}
        }
        if(device==null) {
            // Exact fingerprint + hostname + assigned store; never match a computer by name alone.
            if(!fingerprint.isBlank()&&!hostname.isBlank()) try(var p=c.prepareStatement("""
                    SELECT device_id FROM devices d WHERE device_fingerprint=? AND hostname=? AND (last_store_id=? OR last_store_id IS NULL)
                      AND COALESCE(access_mode,'CLIENT')='CLIENT' AND installation_id<>?
                      AND NOT EXISTS(SELECT 1 FROM studio_device_clients sc WHERE sc.legacy_device_id=d.device_id)
                    ORDER BY first_seen LIMIT 2 FOR UPDATE
                    """)) {
                p.setString(1,fingerprint);p.setString(2,hostname);p.setInt(3,location);p.setString(4,installation);
                try(var r=p.executeQuery()) { if(r.next()) { device=(UUID)r.getObject(1);if(r.next())throw new Denied(409,"DEVICE_MATCH_AMBIGUOUS","More than one device matches this computer. Ask an administrator to resolve duplicate devices."); } }
            }
            try(var p=c.prepareStatement("SELECT device_id,last_store_id FROM devices WHERE installation_id=? FOR UPDATE")) {
                p.setString(1,installation);try(var r=p.executeQuery()) { if(r.next()) {
                    Integer assigned=(Integer)r.getObject(2);
                    if(assigned!=null&&assigned!=location)throw new Denied(403,"DEVICE_STORE_MISMATCH","This computer is assigned to another store.");
                    if(device==null)device=(UUID)r.getObject(1);else if(!device.equals(r.getObject(1)))legacy=(UUID)r.getObject(1);
                }}
            }
            if(device==null) try(var p=c.prepareStatement("""
                    INSERT INTO devices(installation_id,device_fingerprint,hostname,device_name,os_name,os_version,os_arch,
                      local_username,mac_addresses,last_store_id,is_approved,is_blocked,allow_sales,allow_orders,allow_studio,first_seen,last_seen,access_mode)
                    VALUES(?,?,?,?,?,?,?,?,?,?,false,false,false,false,false,now(),now(),'CLIENT') RETURNING device_id
                    """)) {
                String[] values={installation,fingerprint,hostname,value(body,"deviceName"),value(body,"osName"),value(body,"osVersion"),value(body,"osArch"),value(body,"localUsername"),value(body,"macAddresses")};
                for(int i=0;i<values.length;i++)p.setString(i+1,values[i]);p.setInt(10,location);
                try(var r=p.executeQuery()) { r.next();device=(UUID)r.getObject(1); }
            }
            try(var p=c.prepareStatement("INSERT INTO studio_device_clients(installation_id,device_id,legacy_device_id,location_id,public_key) VALUES(?,?,?,?,?)")) {
                p.setString(1,installation);p.setObject(2,device);p.setObject(3,legacy);p.setInt(4,location);p.setString(5,key);p.executeUpdate();
            }
            if(legacy!=null) {
                // Retain history and foreign keys, but retire the obsolete separate Studio row and token.
                try(var p=c.prepareStatement("UPDATE devices SET is_blocked=true,is_approved=false,api_credential_hash=NULL,api_previous_credential_hash=NULL WHERE device_id=?")) {p.setObject(1,legacy);p.executeUpdate();}
                try(var p=c.prepareStatement("UPDATE lan_api_sessions SET revoked_at=now() WHERE device_id=? AND revoked_at IS NULL")){p.setObject(1,legacy);p.executeUpdate();}
            }
        }
        try(var p=c.prepareStatement("UPDATE studio_device_clients SET challenge_hash=?,challenge_expires_at=now()+interval '24 hours' WHERE installation_id=?")) {
            p.setString(1,LanSecurity.sha256(challenge));p.setString(2,installation);p.executeUpdate();
        }
        try(var p=c.prepareStatement("UPDATE devices SET last_store_id=COALESCE(last_store_id,?) WHERE device_id=?")){p.setInt(1,location);p.setObject(2,device);p.executeUpdate();}
        boolean allowed;
        try(var p=c.prepareStatement("SELECT allow_studio,is_blocked,last_store_id FROM devices WHERE device_id=? FOR UPDATE")) {
            p.setObject(1,device);try(var r=p.executeQuery()){r.next();if(r.getBoolean(2))throw new Denied(403,"DEVICE_REVOKED","This computer is blocked in Device Management.");
                Integer store=(Integer)r.getObject(3);if(store==null||store!=location)throw new Denied(403,"DEVICE_STORE_MISMATCH","This device belongs to another store.");allowed=r.getBoolean(1);}
        }
        return new Enrollment(device,allowed);
    }
    static Claim claim(Connection c,JsonObject body)throws Exception {
        String installation=value(body,"installationId"),key=value(body,"publicKey"),challenge=value(body,"pairingChallenge");
        UUID device;int location;
        try(var p=c.prepareStatement("""
                SELECT sc.device_id,sc.location_id,sc.public_key,sc.challenge_hash,sc.challenge_expires_at,d.allow_studio,d.is_blocked,d.last_store_id
                FROM studio_device_clients sc JOIN devices d USING(device_id) WHERE sc.installation_id=? FOR UPDATE OF sc,d
                """)) {p.setString(1,installation);try(var r=p.executeQuery()) {
            if(!r.next())throw new Denied(404,"DEVICE_NOT_ENROLLED","Pair SmartStudio with the store first.");
            if(r.getBoolean(7))throw new Denied(403,"DEVICE_REVOKED","This computer is blocked.");
            if(!r.getBoolean(6))throw new Denied(409,"PAIRING_PENDING","Enable Allow Studio on this computer's existing Device Management row, then check approval.");
            if(!LanSecurity.constantTimeEquals(key,r.getString(3)))throw new Denied(409,"DEVICE_IDENTITY_MISMATCH","The Studio identity changed.");
            Timestamp expiry=r.getTimestamp(5);
            if(expiry==null||expiry.toInstant().isBefore(Instant.now())||!LanSecurity.constantTimeEquals(LanSecurity.sha256(challenge),r.getString(4)))throw new Denied(403,"PAIRING_CHALLENGE_INVALID","The pairing request expired. Enter a fresh pairing phrase.");
            device=(UUID)r.getObject(1);location=r.getInt(2);Integer store=(Integer)r.getObject(8);
            if(store==null||store!=location)throw new Denied(403,"DEVICE_STORE_MISMATCH","This computer's store assignment changed. Administrator recovery is required.");
        }}
        String token=LanSecurity.randomToken();Instant expires=Instant.now().plusSeconds(90L*86400);
        try(var p=c.prepareStatement("UPDATE studio_device_clients SET credential_hash=?,credential_expires_at=?,challenge_hash=NULL,challenge_expires_at=NULL WHERE installation_id=?")) {
            p.setString(1,LanSecurity.sha256(token));p.setTimestamp(2,Timestamp.from(expires));p.setString(3,installation);p.executeUpdate();
        }
        return new Claim(device,location,token,expires);
    }
    static Principal authenticate(Connection c,String hash,String path)throws Exception {
        try(var p=c.prepareStatement("""
                SELECT sc.device_id,sc.installation_id,sc.location_id,sc.credential_expires_at,d.allow_studio,d.is_blocked,d.last_store_id
                FROM studio_device_clients sc JOIN devices d USING(device_id) WHERE sc.credential_hash=?
                """)) {p.setString(1,hash);try(var r=p.executeQuery()){
            if(!r.next())return null;
            if(!r.getBoolean(5)||r.getBoolean(6))throw new Denied(403,"STUDIO_ACCESS_DENIED","Enable Allow Studio for this computer in Device Management.");
            if(!allowedPath(path))throw new Denied(403,"STUDIO_OPERATION_DENIED","This credential is only for SmartStudio.");
            Timestamp expiry=r.getTimestamp(4);if(expiry==null||expiry.toInstant().isBefore(Instant.now()))throw new Denied(401,"DEVICE_CREDENTIAL_EXPIRED","Pair SmartStudio again to renew this computer's credential.");
            Integer store=(Integer)r.getObject(7);int location=r.getInt(3);if(store==null||store!=location)throw new Denied(403,"DEVICE_STORE_MISMATCH","The computer's store assignment changed.");
            return new Principal((UUID)r.getObject(1),r.getString(2),location);
        }}
    }
    static boolean allowedPath(String path) {
        return Set.of("/v1/studio/branding","/v1/studio/remove-background","/v1/studio/device-status","/v1/sessions/login","/v1/sessions/logout",
                "/v1/cloud/update/latest","/v1/cloud/update/sign").contains(path);
    }

    static void adoptStudioOnlyDevice(Connection c,JsonObject body,int location)throws Exception {
        String installation=value(body,"installationId"),fingerprint=value(body,"deviceFingerprint"),hostname=value(body,"hostname");
        if(fingerprint.isBlank()||hostname.isBlank())return;
        try(var p=c.prepareStatement("""
                SELECT device_id FROM devices d WHERE device_fingerprint=? AND hostname=? AND last_store_id=?
                AND pairing_public_key IS NULL AND is_approved=false AND is_blocked=false
                AND EXISTS(SELECT 1 FROM studio_device_clients sc WHERE sc.device_id=d.device_id)
                AND NOT EXISTS(SELECT 1 FROM devices registered WHERE registered.installation_id=?) LIMIT 2 FOR UPDATE
                """)) {
            p.setString(1,fingerprint);p.setString(2,hostname);p.setInt(3,location);p.setString(4,installation);
            try(var r=p.executeQuery()){if(!r.next())return;UUID id=(UUID)r.getObject(1);if(r.next())throw new Denied(409,"DEVICE_MATCH_AMBIGUOUS","More than one device matches this computer.");
                try(var update=c.prepareStatement("UPDATE devices SET installation_id=? WHERE device_id=?")){update.setString(1,installation);update.setObject(2,id);update.executeUpdate();}
            }
        }
    }
    static void requireAllowed(Connection c,UUID device)throws Exception {
        try(var p=c.prepareStatement("SELECT 1 FROM devices WHERE device_id=? AND allow_studio=true AND is_blocked=false")){p.setObject(1,device);try(var r=p.executeQuery()){if(r.next())return;}}
        throw new Denied(403,"STUDIO_ACCESS_DENIED","Enable Allow Studio for this computer in Device Management.");
    }
}
