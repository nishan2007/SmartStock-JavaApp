package services;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Content evidence for drawer continuity and register authorization, without exporting records or tokens. */
final class ServerHandoffContinuity {
    private ServerHandoffContinuity() { }
    static final List<String> TABLES=List.of("cash_drawers","cash_drawer_sessions",
            "cash_drawer_device_assignments","cash_drawer_handovers","cash_drawer_count_events",
            "sales","sale_returns","cross_store_refund_requests","custom_order_payments",
            "invoice_payments","customer_account_transactions","devices","device_sessions",
            "lan_api_sessions","roles","permissions","role_permissions","user_locations","users");

    static JsonObject capture(Connection connection) throws Exception {
        JsonObject result=new JsonObject();result.addProperty("version",1);
        JsonObject fingerprints=new JsonObject();boolean complete=true;
        try(Statement s=connection.createStatement()){s.execute("SET LOCAL TIME ZONE 'UTC'");}
        for(String table:TABLES) {
            boolean exists;
            try(PreparedStatement p=connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
                p.setString(1,"public."+table);try(ResultSet r=p.executeQuery()){r.next();exists=r.getBoolean(1);}
            }
            JsonObject fingerprint=new JsonObject();fingerprint.addProperty("present",exists);
            if(exists) {
                String expression=table.equals("users")
                        ? "jsonb_build_object('user_id',t.user_id,'role_id',t.role_id,'is_active',t.is_active)"
                        : "to_jsonb(t)";
                if(table.equals("devices")) expression+=" - ARRAY['last_seen','last_login_user_id','session_count','updated_at','api_credential_last_used_at','hostname','os_name','os_version','os_arch','java_version','app_version','local_username','mac_addresses']::text[]";
                if(table.equals("lan_api_sessions")) expression+=" - 'last_seen_at'";
                List<byte[]> rows=new ArrayList<>();
                try(Statement s=connection.createStatement()) {
                    s.setFetchSize(128);
                    try(ResultSet r=s.executeQuery("SELECT ("+expression+")::text FROM public."+ServerHandoffDiagnostics.quote(table)+" t")) {
                        while(r.next())rows.add(hash(r.getString(1)));
                    }
                }
                fingerprint.addProperty("rows",rows.size());fingerprint.addProperty("sha256",aggregate(rows));
            } else complete=false;
            fingerprints.add(table,fingerprint);
        }
        result.add("content",fingerprints);result.addProperty("requiredTablesPresent",complete);
        if(complete) {
            try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("SELECT count(*) FROM public.cash_drawer_sessions WHERE status='OPEN'")) {
                r.next();result.addProperty("openDrawerSessions",r.getLong(1));
            }
            // Expiry is checked at capture time as well as comparing the stored credential records.
            try(Statement s=connection.createStatement();ResultSet r=s.executeQuery("""
                    SELECT count(*),count(*) FILTER (WHERE api_credential_hash IS NULL
                      OR api_credential_expires_at IS NULL OR api_credential_expires_at<=CURRENT_TIMESTAMP)
                    FROM public.devices WHERE is_approved=TRUE AND is_blocked=FALSE AND access_mode='CLIENT'
                    """)) {
                r.next();result.addProperty("approvedRegisters",r.getLong(1));result.addProperty("registersNeedingCredentialRecovery",r.getLong(2));
            }
        }
        result.addProperty("scope","Content fingerprints cover drawer sessions, assignments, cash-balance input tables, device credentials and permissions. No rows or credential hashes are exported. Stop writes before the final baseline. This checks preservation, not the transfer itself.");
        return result;
    }
    static byte[] hash(String row)throws Exception{return MessageDigest.getInstance("SHA-256").digest(row.getBytes(StandardCharsets.UTF_8));}
    static String aggregate(List<byte[]> rows)throws Exception {
        rows.sort(Arrays::compareUnsigned);MessageDigest digest=MessageDigest.getInstance("SHA-256");
        for(byte[] row:rows)digest.update(row);
        return HexFormat.of().formatHex(digest.digest());
    }
    static JsonObject compare(JsonObject before,JsonObject after,boolean sameIdentity) {
        JsonObject result=new JsonObject();JsonArray problems=new JsonArray();
        if(before==null||after==null)problems.add("Capture a new baseline: drawer and register continuity evidence is missing.");
        else {
            if(!valid(before)||!valid(after))problems.add("Required drawer/register continuity evidence is incomplete.");
            if(!Objects.equals(before.get("content"),after.get("content")))problems.add("Drawer, cash activity, device authorization or permissions changed. Review content differences before accepting the handoff.");
            if(!Objects.equals(before.get("openDrawerSessions"),after.get("openDrawerSessions")))problems.add("The number of open drawer sessions changed.");
            if(!after.has("registersNeedingCredentialRecovery")||after.get("registersNeedingCredentialRecovery").getAsLong()!=0)problems.add("One or more approved registers have missing or expired credentials, or credential readiness was not checked.");
        }
        if(!sameIdentity)problems.add("Store or HTTPS identity changed: existing registers may require pairing again.");
        result.add("blockers",problems);result.addProperty("preservationChecksPassed",problems.isEmpty());
        result.addProperty("automaticReconnectionVerified",false);
        result.addProperty("nextStep","Keep the original drawer open; verify its session and expected cash on each original register after cutover. Confirm authenticated reconnection from every register. Expired employee sessions may require login without requiring device pairing.");
        return result;
    }
    private static boolean valid(JsonObject report) {
        if(!report.has("version")||report.get("version").getAsInt()!=1||!report.has("requiredTablesPresent")||!report.get("requiredTablesPresent").getAsBoolean()||!report.has("content")||!report.get("content").isJsonObject())return false;
        for(String table:TABLES) {
            JsonElement entry=report.getAsJsonObject("content").get(table);
            if(entry==null||!entry.isJsonObject())return false;
            JsonObject value=entry.getAsJsonObject();
            if(!value.has("present")||!value.get("present").getAsBoolean()||!value.has("rows")||!value.has("sha256"))return false;
        }
        return report.has("openDrawerSessions")&&report.has("approvedRegisters");
    }
}
