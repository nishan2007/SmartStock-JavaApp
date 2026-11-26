package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Persists the browser retry claim and the account adjustment in the same store transaction. */
final class CustomerAccountWebAdjustment {
    private static final Gson JSON = LanJson.create();

    private CustomerAccountWebAdjustment() { }

    static Object record(Connection c, int user, String userName, UUID browser, int location,
                         JsonObject body, UUID key) throws Exception {
        LanCustomerAccountService.require(c,user,"CUSTOMER_ACCOUNTS");
        JsonObject canonical = body.deepCopy();
        canonical.remove("idempotencyKey");
        String hash = LanSecurity.sha256(JSON.toJson(canonical));
        boolean inserted;
        try (var p = c.prepareStatement("""
                INSERT INTO customer_account_web_requests(browser_id,user_id,location_id,request_key,request_hash)
                VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING
                """)) {
            bind(p,browser,user,location,key);
            p.setString(5,hash);
            inserted=p.executeUpdate()==1;
        }
        if (!inserted) {
            try (var p = c.prepareStatement("""
                    SELECT request_hash,response::text FROM customer_account_web_requests
                    WHERE browser_id=? AND user_id=? AND location_id=? AND request_key=? FOR UPDATE
                    """)) {
                bind(p,browser,user,location,key);
                try (var r=p.executeQuery()) {
                    if (r.next() && LanSecurity.constantTimeEquals(hash,r.getString(1)) && r.getString(2)!=null)
                        return JsonParser.parseString(r.getString(2));
                    throw new LanCustomerAccountService.RuleViolation(409,"RETRY_KEY_USED",
                            "This retry key belongs to a different account adjustment.");
                }
            }
        }
        Long drawer = body.has("drawerSessionId") && !body.get("drawerSessionId").isJsonNull()
                ? body.get("drawerSessionId").getAsLong() : null;
        var origin = new PhoneMinutesService.BrowserOrigin(browser,"Customer Accounts web",drawer);
        var result = LanCustomerAccountService.adjust(c,body,null,user,userName,location,origin);
        try (var p = c.prepareStatement("""
                UPDATE customer_account_web_requests
                SET response=?::jsonb WHERE browser_id=? AND user_id=? AND location_id=? AND request_key=?
                """)) {
            p.setString(1,JSON.toJson(result));
            p.setObject(2,browser);
            p.setInt(3,user);
            p.setInt(4,location);
            p.setObject(5,key);
            if (p.executeUpdate()!=1) throw new SQLException("Could not complete the account adjustment retry record.");
        }
        return result;
    }

    private static void bind(PreparedStatement p, UUID browser, int user, int location, UUID key) throws SQLException {
        p.setObject(1,browser);
        p.setInt(2,user);
        p.setInt(3,location);
        p.setObject(4,key);
    }
}
