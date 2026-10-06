package services;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import com.google.gson.Gson;
import data.DB;
import data.DatabaseConfig;

/** A narrowly scoped browser-download authenticator; never issues register sessions. */
public final class InstallerDownloadAuthService {
    private InstallerDownloadAuthService() { }
    @FunctionalInterface public interface PasswordCheck {
        boolean verify(String email, String password) throws Exception;
    }

    static String originKey(String websiteKey) throws Exception {
        String configured=System.getenv("SMARTSTOCK_INSTALLER_AUTH_KEY");
        if(configured!=null&&!configured.isBlank())return configured;
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(websiteKey.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal("smartstock-installer-auth-v1".getBytes(StandardCharsets.UTF_8)));
    }

    public static void handle(HttpExchange exchange, String key, PasswordCheck passwords) throws java.io.IOException {
        try {
            if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) { send(exchange,403); return; }
            if (ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY) { send(exchange,503); return; }
            if (!exchange.getRequestURI().getPath().equals("/v1/installer/authenticate")) { send(exchange,404); return; }
            if (!"POST".equals(exchange.getRequestMethod())) { send(exchange,405); return; }
            String supplied=exchange.getRequestHeaders().getFirst("X-SmartStock-Installer-Key");
            if(key==null || key.length()<32) { send(exchange,503); return; }
            if(supplied==null || supplied.length()>256 || !MessageDigest.isEqual(
                    key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) { send(exchange,403); return; }
            byte[] bytes=exchange.getRequestBody().readNBytes(8193);
            if(bytes.length>8192) { send(exchange,413); return; }
            String username,password;
            try {
                var body=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
                username=body.get("username").getAsString().trim(); password=body.get("password").getAsString();
                if(username.isEmpty() || username.length()>512 || password.isEmpty() || password.length()>2048) throw new IllegalArgumentException();
            } catch(Exception invalid) { send(exchange,400); return; }
            String identifier="installer:"+username;
            try(Connection c=DB.getConnection()) {
                try { LoginSecurityService.requireAllowed(c,identifier); }
                catch(java.sql.SQLException locked) { send(exchange,429); return; }
                int userId=0; String email=null;
                try(PreparedStatement p=c.prepareStatement("""
                        SELECT u.user_id,u.email FROM users u
                        JOIN user_locations ul ON ul.user_id=u.user_id
                        WHERE u.is_active=TRUE AND LOWER(u.username)=LOWER(?) AND ul.location_id=?
                        """)) {
                    p.setString(1,username); p.setInt(2,DatabaseConfig.load().locationId());
                    try(ResultSet r=p.executeQuery()) {
                        if(r.next()) { userId=r.getInt(1); email=r.getString(2); }
                        if(r.next()) { userId=0; email=null; }
                    }
                }
                if(userId==0 || email==null || !passwords.verify(email,password)) {
                    LoginSecurityService.recordFailure(c,null,identifier,"Installer download login failed"); send(exchange,401); return;
                }
                // Recheck after the remote password verification: deactivation must take effect immediately.
                try(PreparedStatement p=c.prepareStatement("""
                        SELECT 1 FROM users u JOIN user_locations ul ON ul.user_id=u.user_id
                        WHERE u.user_id=? AND u.is_active=TRUE AND ul.location_id=? AND u.email=?
                        """)) {
                    p.setInt(1,userId); p.setInt(2,DatabaseConfig.load().locationId()); p.setString(3,email);
                    try(ResultSet r=p.executeQuery()) { if(!r.next()) { send(exchange,401); return; } }
                }
                LoginSecurityService.recordSuccess(c,identifier); send(exchange,200);
            }
        } catch(Exception unavailable) { send(exchange,503); }
        finally { exchange.close(); }
    }
    private static void send(HttpExchange x,int status) throws java.io.IOException {
        byte[] body=new Gson().toJson(Map.of("authenticated",status==200)).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type","application/json");
        x.getResponseHeaders().set("Cache-Control","private, no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.sendResponseHeaders(status,body.length); x.getResponseBody().write(body);
    }
}
