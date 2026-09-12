package services;

import com.google.gson.*;
import com.sun.net.httpserver.HttpExchange;
import data.DB;
import data.DatabaseConfig;
import data.EnvironmentProfile;
import utils.SecureCredentialStore;
import utils.SecureFilePermissions;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** Signed Supabase Send Email hook. Gmail credentials and codes never enter the LAN response or logs. */
public final class SupabaseAuthEmailHook implements AutoCloseable {
    public static final String PATH = "/register/hooks/send-email";
    static final int MAX_BODY = 64 * 1024;
    private static volatile String lastDelivery = "";
    private static volatile String lastError = "";
    private final ExecutorService deliveries = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "auth-email-delivery"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    private final ReentrantLock[] locks = new ReentrantLock[32];
    private final Path receipts;

    public SupabaseAuthEmailHook() { this(EnvironmentProfile.active().file("auth-email-receipts")); }
    SupabaseAuthEmailHook(Path receipts) {
        this.receipts = receipts;
        Arrays.setAll(locks, i -> new ReentrantLock());
    }
    private static String keyName() { return EnvironmentProfile.active().secretKey("auth-email-hook-v1"); }
    public static void configure(String secret) throws Exception {
        byte[] decoded = decodeSecret(secret);
        Arrays.fill(decoded, (byte) 0);
        SecureCredentialStore.write(keyName(), secret.trim());
    }
    public static Map<String, Object> status() {
        return Map.of("configured", configured(), "lastDelivery", lastDelivery, "lastError", lastError);
    }
    public static boolean configured() {
        String secret = SecureCredentialStore.read(keyName());
        return secret != null && !secret.isBlank();
    }
    public static GmailOAuthService.ConnectionStatus senderStatus() throws Exception {
        return GmailOAuthService.connectionStatus(loadSender().address());
    }
    public void handle(HttpExchange x) throws java.io.IOException {
        byte[] bytes = null;
        Future<?> delivery = null;
        try {
            if (!"POST".equals(x.getRequestMethod())) { respond(x, 405, "POST required."); return; }
            if (!configured()) { respond(x, 503, "Authentication email delivery is not configured."); return; }
            if (ServerRoleGuard.state() != ServerRoleGuard.State.PRIMARY) { respond(x, 503, "Primary server unavailable."); return; }
            String ct = x.getRequestHeaders().getFirst("Content-Type");
            if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("application/json")) { respond(x, 415, "JSON required."); return; }
            bytes = x.getRequestBody().readNBytes(MAX_BODY + 1);
            if (bytes.length > MAX_BODY) { respond(x, 413, "Request too large."); return; }
            String id = x.getRequestHeaders().getFirst("webhook-id");
            String timestamp = x.getRequestHeaders().getFirst("webhook-timestamp");
            String signatures = x.getRequestHeaders().getFirst("webhook-signature");
            verify(SecureCredentialStore.read(keyName()), id, timestamp, signatures, bytes, Instant.now());
            // Copy before returning to the HTTP worker; the original raw body is wiped in finally.
            byte[] payload = bytes.clone();
            try {
                delivery = deliveries.submit(() -> {
                    try {
                        Sender sender = loadSender();
                        JsonObject event = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
                        List<GmailOAuthService.GmailMessage> messages = messages(event, sender,
                                EmploymentPortalConfig.origin() + "/register", SupabaseProjectConfig.load().url());
                        deliverOnce(id, payload, messages, GmailOAuthService::send);
                    } catch (Exception ex) { throw new CompletionException(ex); }
                    finally { Arrays.fill(payload, (byte) 0); }
                });
            } catch (RejectedExecutionException ex) { Arrays.fill(payload, (byte) 0); throw ex; }
            // Supabase HTTP hooks have a five-second budget. Never acknowledge a queued/failed email as sent.
            delivery.get(3800, TimeUnit.MILLISECONDS);
            lastDelivery = Instant.now().toString(); lastError = "";
            respond(x, 200, null);
        } catch (SecurityException ex) { respond(x, 401, "Invalid webhook signature."); }
        catch (Exception ex) {
            if (delivery != null) delivery.cancel(true);
            lastError = "Gmail delivery failed or timed out. Check the store Gmail connection and retry.";
            respond(x, 503, "Authentication email could not be sent. Please retry.");
        } finally { if (bytes != null) Arrays.fill(bytes, (byte) 0); }
    }
    static byte[] decodeSecret(String value) {
        try {
            String secret = value == null ? "" : value.trim();
            if (secret.startsWith("v1,")) secret = secret.substring(3);
            if (!secret.startsWith("whsec_")) throw new IllegalArgumentException();
            byte[] key = Base64.getDecoder().decode(secret.substring(6));
            if (key.length < 24 || key.length > 64) throw new IllegalArgumentException();
            return key;
        } catch (Exception ex) { throw new IllegalArgumentException("Enter the Send Email hook signing secret from Supabase."); }
    }
    static void verify(String secret, String id, String timestamp, String signatures, byte[] body, Instant now) {
        try {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,200}") || timestamp == null || !timestamp.matches("[0-9]{1,12}")
                    || signatures == null || signatures.length() > 2048 || body.length > MAX_BODY) throw new SecurityException();
            long seconds = Long.parseLong(timestamp);
            if (seconds < now.getEpochSecond() - 300 || seconds > now.getEpochSecond() + 300) throw new SecurityException();
            byte[] key = decodeSecret(secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            try { mac.init(new SecretKeySpec(key, "HmacSHA256")); } finally { Arrays.fill(key, (byte) 0); }
            mac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            boolean valid = false;
            for (String candidate : signatures.split("\\s+")) {
                if (!candidate.startsWith("v1,")) continue;
                try { valid |= MessageDigest.isEqual(expected, Base64.getDecoder().decode(candidate.substring(3))); }
                catch (IllegalArgumentException ignored) { }
            }
            Arrays.fill(expected, (byte) 0);
            if (!valid) throw new SecurityException();
        } catch (Exception ex) { throw new SecurityException("Invalid webhook signature."); }
    }
    @FunctionalInterface interface Mailer { GmailOAuthService.SendResult send(GmailOAuthService.GmailMessage message) throws Exception; }
    void deliverOnce(String id, byte[] payload, List<GmailOAuthService.GmailMessage> messages, Mailer mailer) throws Exception {
        ReentrantLock lock = locks[Math.floorMod(id.hashCode(), locks.length)];
        if (!lock.tryLock(100, TimeUnit.MILLISECONDS)) throw new java.io.IOException("Delivery in progress.");
        try {
            Files.createDirectories(receipts); SecureFilePermissions.restrictDirectoryToOwner(receipts);
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
            for (int i = 0; i < messages.size(); i++) {
                Path receipt = receipts.resolve(LanSecurity.sha256(id) + "-" + i + ".sent");
                if (Files.exists(receipt)) {
                    if (!Files.readString(receipt).equals(fingerprint)) throw new SecurityException("Webhook ID reused.");
                    continue;
                }
                mailer.send(messages.get(i));
                // Store only a fingerprint, never the code, recipient, token hash, or email body.
                Path temp = Files.createTempFile(receipts, ".receipt-", ".tmp");
                try {
                    SecureFilePermissions.restrictFileToOwner(temp);
                    Files.writeString(temp, fingerprint);
                    Files.move(temp, receipt, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temp); }
            }
        } finally { lock.unlock(); }
    }
    record Sender(String address, String name) { }
    private static Sender loadSender() throws Exception {
        int location = DatabaseConfig.load().locationId();
        String address, name;
        try (var c = DB.getConnection(); var p = c.prepareStatement("SELECT email_sender_address,email_sender_name FROM locations WHERE location_id=?")) {
            p.setInt(1, location); p.setQueryTimeout(2);
            try (var r = p.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("Configure the store Gmail sender.");
                address = r.getString(1); name = r.getString(2);
            }
        }
        var branding = WalletCompanyBranding.from(managers.ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation(location));
        if (branding.name() != null && !branding.name().isBlank()) name = branding.name();
        return new Sender(email(address), name == null || name.isBlank() ? "SmartStock" : name);
    }
    static List<GmailOAuthService.GmailMessage> messages(JsonObject event, Sender sender, String portal, String project) {
        JsonObject user = event.getAsJsonObject("user"), data = event.getAsJsonObject("email_data");
        UUID.fromString(text(user, "id"));
        String recipient = email(text(user, "email")), action = text(data, "email_action_type");
        String token = text(data, "token"), hash = text(data, "token_hash");
        String redirect = text(data, "redirect_to");
        boolean portalFlow = portal.equals(redirect) || (portal + "/").equals(redirect);
        List<GmailOAuthService.GmailMessage> out = new ArrayList<>();
        switch (action) {
            case "signup", "recovery", "magiclink", "invite", "reauthentication" ->
                out.add(message(sender, recipient, action, token, hash, portalFlow, portal, project, redirect));
            case "email_change" -> {
                String next = email(text(user, "new_email")), nextToken = text(data, "token_new"), oldHash = text(data, "token_hash_new");
                if (!oldHash.isBlank()) {
                    out.add(message(sender, recipient, action, token, oldHash, false, portal, project, redirect));
                    out.add(message(sender, next, action, nextToken, hash, false, portal, project, redirect));
                } else out.add(message(sender, next, action, nextToken.isBlank() ? token : nextToken, hash, false, portal, project, redirect));
            }
            case "password_changed_notification", "email_changed_notification", "phone_changed_notification",
                    "identity_linked_notification", "identity_unlinked_notification", "mfa_factor_enrolled_notification", "mfa_factor_unenrolled_notification" -> {
                String body = "A security setting on your account was changed. If you did not make this change, contact the company immediately.";
                out.add(new GmailOAuthService.GmailMessage(sender.address(), sender.name(), recipient, "", sender.name() + " — Account security update", body, "<p>" + body + "</p>", null, null, null));
            }
            default -> throw new IllegalArgumentException("Unsupported authentication email action.");
        }
        return List.copyOf(out);
    }
    private static GmailOAuthService.GmailMessage message(Sender sender, String to, String action, String code, String hash,
                                                           boolean portalFlow, String portal, String project, String redirect) {
        if (!code.matches("[0-9]{6,10}")) throw new IllegalArgumentException("Missing verification code.");
        String title = "recovery".equals(action) ? "Reset your password" : "Verify your account";
        String instructions = "recovery".equals(action) ? "Enter this code on the password recovery screen and choose your new password."
                : "Enter this code on the verification screen where you started this request.";
        String link = "";
        if (!portalFlow && !"reauthentication".equals(action)) {
            if (!hash.matches("[A-Za-z0-9_-]{20,256}")) throw new IllegalArgumentException("Missing confirmation token.");
            // Project origin comes from trusted server configuration, never from the event's site_url or metadata.
            link = project + "/auth/v1/verify?token=" + encode(hash) + "&type=" + encode(action);
            if (!redirect.isBlank()) link += "&redirect_to=" + encode(redirect);
            instructions = "Use this code in SmartStock, or follow the confirmation link below.";
        }
        String plain = sender.name() + "\n\n" + title + "\n\n" + instructions + "\n\n" + code
                + (link.isEmpty() ? "" : "\n\n" + link) + "\n\nDo not share this code. If you did not request this email, you can ignore it.";
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:560px;padding:24px;color:#241d35\"><h2>" + escape(sender.name())
                + "</h2><h3>" + title + "</h3><p>" + instructions + "</p><p style=\"font-size:30px;font-weight:bold;letter-spacing:4px;color:#6335a5\">"
                + code + "</p>" + (link.isEmpty() ? "" : "<p><a href=\"" + escape(link) + "\">Continue verification</a></p>")
                + "<p>Do not share this code. If you did not request this email, you can ignore it.</p></div>";
        return new GmailOAuthService.GmailMessage(sender.address(), sender.name(), to, "", sender.name() + " — " + title, plain, html, null, null, null);
    }
    private static String email(String value) {
        if (value == null || value.length() > 254 || !value.matches("[^\\s<>@,;]+@[^\\s<>@,;]+\\.[^\\s<>@,;]+")) throw new IllegalArgumentException("Invalid email address.");
        return value;
    }
    private static String text(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }
    private static String encode(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static void respond(HttpExchange x, int code, String error) throws java.io.IOException {
        byte[] body = (error == null ? "{}" : new Gson().toJson(Map.of("error", Map.of("http_code", code, "message", error)))).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json");
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(code, body.length); x.getResponseBody().write(body);
    }
    @Override public void close() { deliveries.shutdownNow(); }
}
