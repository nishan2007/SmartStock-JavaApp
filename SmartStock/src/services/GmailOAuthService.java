package services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import utils.SecureCredentialStore;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-only Gmail OAuth and message delivery. Tokens never cross the LAN API. */
public final class GmailOAuthService {
    private static final Gson GSON = LanJson.create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CLIENT_KEY = "gmail-oauth-client-v1";
    private static final String TOKEN_PREFIX = "gmail-refresh-token-v1-";
    private static final Duration FLOW_TTL = Duration.ofMinutes(10);
    private static final Map<String, PendingAuthorization> PENDING = new ConcurrentHashMap<>();

    private GmailOAuthService() { }

    public static ClientImportResult importClientJson(String json) throws IOException {
        ClientConfig config = parseClientJson(json);
        SecureCredentialStore.write(CLIENT_KEY, GSON.toJson(config));
        return new ClientImportResult(true, redactClientId(config.clientId()));
    }

    public static ClientImportResult clientStatus() {
        ClientConfig config = loadClient();
        return config == null
                ? new ClientImportResult(false, "")
                : new ClientImportResult(true, redactClientId(config.clientId()));
    }

    public static AuthorizationStart beginAuthorization(String senderEmail, String redirectUri) {
        ClientConfig client = requireClient();
        String sender = normalizeEmail(senderEmail);
        validateEmail(sender, "Enter a valid sender Gmail address.");
        validateLoopbackRedirect(redirectUri);
        prunePending();
        String state = randomUrlToken(32);
        String verifier = randomUrlToken(48);
        String challenge = base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        PENDING.put(state, new PendingAuthorization(sender, redirectUri, verifier, Instant.now().plus(FLOW_TTL)));
        String authorizationUrl = authorizationEndpoint()
                + "?client_id=" + url(client.clientId())
                + "&redirect_uri=" + url(redirectUri)
                + "&response_type=code"
                + "&scope=" + url("openid email https://www.googleapis.com/auth/gmail.send")
                + "&access_type=offline&prompt=consent"
                + "&code_challenge=" + url(challenge)
                + "&code_challenge_method=S256"
                + "&state=" + url(state)
                + "&login_hint=" + url(sender);
        return new AuthorizationStart(state, authorizationUrl, PENDING.get(state).expiresAt().toString());
    }

    public static ConnectionStatus completeAuthorization(String state, String code) throws IOException, InterruptedException {
        if (state == null || state.isBlank() || code == null || code.isBlank()) {
            throw new GmailException("AUTHORIZATION_INVALID", "Google authorization did not return a valid code.", false, false);
        }
        PendingAuthorization pending = PENDING.remove(state);
        if (pending == null || pending.expiresAt().isBefore(Instant.now())) {
            throw new GmailException("AUTHORIZATION_EXPIRED", "Google authorization expired. Start the connection again.", false, false);
        }
        ClientConfig client = requireClient();
        JsonObject token = postForm(tokenEndpoint(), Map.of(
                "client_id", client.clientId(),
                "client_secret", client.clientSecret(),
                "code", code.trim(),
                "code_verifier", pending.verifier(),
                "redirect_uri", pending.redirectUri(),
                "grant_type", "authorization_code"
        ));
        String accessToken = requiredJsonString(token, "access_token", "Google did not return an access token.");
        String refreshToken = requiredJsonString(token, "refresh_token",
                "Google did not return a refresh token. Revoke SmartStock access in Google and connect again.");
        JsonObject identity = getJson(userInfoEndpoint(), accessToken);
        String authorizedEmail = normalizeEmail(requiredJsonString(identity, "email", "Google did not return the account email."));
        if (!pending.senderEmail().equals(authorizedEmail)) {
            throw new GmailException("SENDER_MISMATCH",
                    "Google authorized " + authorizedEmail + " instead of " + pending.senderEmail() + ".", false, false);
        }
        if (identity.has("email_verified") && !identity.get("email_verified").getAsBoolean()) {
            throw new GmailException("EMAIL_NOT_VERIFIED", "The Google account email is not verified.", false, false);
        }
        SecureCredentialStore.write(tokenKey(authorizedEmail), refreshToken);
        return new ConnectionStatus(authorizedEmail, "CONNECTED", "Connected to Gmail.");
    }

    public static ConnectionStatus connectionStatus(String senderEmail) {
        String sender = normalizeEmail(senderEmail);
        if (loadClient() == null) return new ConnectionStatus(sender, "NOT_CONFIGURED", "Import a Google OAuth client JSON first.");
        String token = SecureCredentialStore.read(tokenKey(sender));
        if (token == null || token.isBlank()) return new ConnectionStatus(sender, "NOT_CONNECTED", "Connect this sender to Gmail.");
        try {
            refreshAccessToken(sender);
            return new ConnectionStatus(sender, "CONNECTED", "Connected to Gmail.");
        } catch (GmailException ex) {
            if (ex.authorizationRequired()) return new ConnectionStatus(sender, "AUTHORIZATION_EXPIRED", ex.getMessage());
            return new ConnectionStatus(sender, "CONNECTED", "Connected; Google could not be reached for a live check.");
        } catch (Exception ex) {
            return new ConnectionStatus(sender, "CONNECTED", "Connected; Google could not be reached for a live check.");
        }
    }

    public static void disconnect(String senderEmail) {
        PENDING.entrySet().removeIf(entry -> entry.getValue().senderEmail().equals(normalizeEmail(senderEmail)));
        SecureCredentialStore.delete(tokenKey(normalizeEmail(senderEmail)));
    }

    public static SendResult send(GmailMessage message) throws IOException, InterruptedException {
        validateMessage(message);
        String sender = normalizeEmail(message.fromEmail());
        String accessToken = refreshAccessToken(sender);
        String raw = buildMimeMessage(message);
        JsonObject payload = new JsonObject();
        payload.addProperty("raw", raw);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(gmailSendEndpoint() + "/gmail/v1/users/" + url(sender) + "/messages/send"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw googleFailure("GMAIL_SEND_FAILED", response.statusCode(), response.body());
        }
        JsonObject result = parseObject(response.body(), "Gmail returned an invalid response.");
        return new SendResult(sender, normalizeEmail(message.toEmail()), requiredJsonString(result, "id", "Gmail did not return a message ID."));
    }

    static ClientConfig parseClientJson(String json) {
        JsonObject root;
        try { root = JsonParser.parseString(json == null ? "" : json).getAsJsonObject(); }
        catch (Exception ex) { throw new IllegalArgumentException("Select a valid Google Desktop OAuth client JSON file.", ex); }
        JsonObject installed = root.has("installed") && root.get("installed").isJsonObject() ? root.getAsJsonObject("installed") : null;
        if (installed == null) throw new IllegalArgumentException("The Google OAuth JSON must contain an installed desktop client.");
        String clientId = text(installed, "client_id");
        String clientSecret = text(installed, "client_secret");
        String authUri = first(text(installed, "auth_uri"), "https://accounts.google.com/o/oauth2/v2/auth");
        String tokenUri = first(text(installed, "token_uri"), "https://oauth2.googleapis.com/token");
        if (clientId.isBlank() || clientSecret.isBlank()) throw new IllegalArgumentException("The Google OAuth client ID or secret is missing.");
        boolean loopback = false;
        JsonArray redirects = installed.has("redirect_uris") && installed.get("redirect_uris").isJsonArray()
                ? installed.getAsJsonArray("redirect_uris") : new JsonArray();
        for (var value : redirects) {
            String redirect = value.getAsString().toLowerCase(Locale.ROOT);
            if (redirect.startsWith("http://localhost") || redirect.startsWith("http://127.0.0.1")) loopback = true;
        }
        if (!loopback) throw new IllegalArgumentException("The Google Desktop OAuth client must allow a localhost redirect.");
        if (!authUri.startsWith("https://accounts.google.com/") || !tokenUri.startsWith("https://oauth2.googleapis.com/")) {
            throw new IllegalArgumentException("The OAuth client contains an unexpected Google endpoint.");
        }
        return new ClientConfig(clientId, clientSecret, authUri, tokenUri);
    }

    static String normalizeEmail(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }

    static String tokenKey(String email) { return TOKEN_PREFIX + hex(sha256(normalizeEmail(email).getBytes(StandardCharsets.UTF_8))); }

    private static String refreshAccessToken(String senderEmail) throws IOException, InterruptedException {
        ClientConfig client = requireClient();
        String refreshToken = SecureCredentialStore.read(tokenKey(senderEmail));
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new GmailException("GMAIL_NOT_CONNECTED", "Connect " + senderEmail + " to Gmail first.", false, true);
        }
        JsonObject token = postForm(tokenEndpoint(), Map.of(
                "client_id", client.clientId(), "client_secret", client.clientSecret(),
                "refresh_token", refreshToken, "grant_type", "refresh_token"));
        return requiredJsonString(token, "access_token", "Google did not return an access token.");
    }

    private static JsonObject postForm(String endpoint, Map<String, String> values) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!body.isEmpty()) body.append('&');
            body.append(url(entry.getKey())).append('=').append(url(entry.getValue()));
        }
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(Duration.ofSeconds(25))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw googleFailure("OAUTH_TOKEN_FAILED", response.statusCode(), response.body());
        }
        return parseObject(response.body(), "Google returned an invalid OAuth response.");
    }

    private static JsonObject getJson(String endpoint, String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint)).timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw googleFailure("GOOGLE_IDENTITY_FAILED", response.statusCode(), response.body());
        return parseObject(response.body(), "Google returned an invalid identity response.");
    }

    static GmailException googleFailure(String category, int status, String body) {
        String detail = safeDetail(body);
        boolean revoked = status == 400 && (detail.contains("invalid_grant") || detail.contains("invalid_client"));
        boolean transientFailure = status == 429 || status >= 500;
        String message = revoked ? "Google authorization expired. Reconnect the sender Gmail account."
                : "Google returned HTTP " + status + (detail.isBlank() ? "." : ": " + detail);
        return new GmailException(revoked ? "AUTHORIZATION_EXPIRED" : category, message, transientFailure, revoked);
    }

    static String buildMimeMessage(GmailMessage message) {
        String boundary = "smartstock_" + randomUrlToken(18);
        String alternative = boundary + "_alt";
        StringBuilder mime = new StringBuilder();
        mime.append("From: ").append(formatAddress(message.fromEmail(), message.fromName())).append("\r\n")
                .append("To: ").append(cleanHeader(message.toEmail())).append("\r\n");
        if (message.bccEmail() != null && !message.bccEmail().isBlank()) mime.append("Bcc: ").append(cleanHeader(message.bccEmail())).append("\r\n");
        mime.append("Subject: ").append(mimeHeader(message.subject())).append("\r\n")
                .append("MIME-Version: 1.0\r\n")
                .append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n")
                .append("--").append(boundary).append("\r\n")
                .append("Content-Type: multipart/alternative; boundary=\"").append(alternative).append("\"\r\n\r\n")
                .append("--").append(alternative).append("\r\nContent-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n")
                .append(base64Mime(first(message.bodyText(), ""))).append("\r\n")
                .append("--").append(alternative).append("\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n")
                .append(base64Mime(first(message.bodyHtml(), "<pre>" + escapeHtml(first(message.bodyText(), "")) + "</pre>"))).append("\r\n")
                .append("--").append(alternative).append("--\r\n");
        if (message.attachmentName() != null && !message.attachmentName().isBlank() && message.attachmentBody() != null) {
            mime.append("--").append(boundary).append("\r\nContent-Type: ")
                    .append(first(message.attachmentContentType(), "text/plain; charset=UTF-8"))
                    .append("; name=\"").append(cleanHeader(message.attachmentName())).append("\"\r\n")
                    .append("Content-Disposition: attachment; filename=\"").append(cleanHeader(message.attachmentName())).append("\"\r\n")
                    .append("Content-Transfer-Encoding: base64\r\n\r\n").append(base64Mime(message.attachmentBody())).append("\r\n");
        }
        mime.append("--").append(boundary).append("--\r\n");
        return base64Url(mime.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void validateMessage(GmailMessage message) {
        if (message == null) throw new IllegalArgumentException("Email message is required.");
        validateEmail(normalizeEmail(message.fromEmail()), "A valid sender email is required.");
        validateEmail(normalizeEmail(message.toEmail()), "A valid recipient email is required.");
        if (message.bccEmail() != null && !message.bccEmail().isBlank()) validateEmail(normalizeEmail(message.bccEmail()), "The BCC email is invalid.");
        if (message.subject() == null || message.subject().isBlank()) throw new IllegalArgumentException("Email subject is required.");
    }

    private static void validateEmail(String email, String message) {
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new IllegalArgumentException(message);
    }

    private static void validateLoopbackRedirect(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"http".equals(uri.getScheme()) || !("127.0.0.1".equals(host) || "localhost".equals(host)) || uri.getPort() <= 0) throw new IllegalArgumentException();
        } catch (Exception ex) { throw new IllegalArgumentException("OAuth redirect must use an ephemeral localhost HTTP port."); }
    }

    private static ClientConfig requireClient() {
        ClientConfig config = loadClient();
        if (config == null) throw new GmailException("OAUTH_NOT_CONFIGURED", "Import a Google Desktop OAuth client JSON first.", false, true);
        return config;
    }

    private static ClientConfig loadClient() {
        String json = SecureCredentialStore.read(CLIENT_KEY);
        try { return json == null || json.isBlank() ? null : GSON.fromJson(json, ClientConfig.class); }
        catch (Exception ex) { return null; }
    }

    private static void prunePending() { PENDING.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now())); }
    private static String authorizationEndpoint() { ClientConfig c = requireClient(); return property("smartstock.gmail.authorizationEndpoint", c.authUri()); }
    private static String tokenEndpoint() { ClientConfig c = requireClient(); return property("smartstock.gmail.tokenEndpoint", c.tokenUri()); }
    private static String userInfoEndpoint() { return property("smartstock.gmail.userInfoEndpoint", "https://openidconnect.googleapis.com/v1/userinfo"); }
    private static String gmailSendEndpoint() { return property("smartstock.gmail.apiBase", "https://gmail.googleapis.com"); }
    private static String property(String name, String fallback) { String value = System.getProperty(name); return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String text(JsonObject object, String key) { return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString().trim() : ""; }
    private static String requiredJsonString(JsonObject object, String key, String message) { String value = text(object, key); if (value.isBlank()) throw new GmailException("INVALID_GOOGLE_RESPONSE", message, false, false); return value; }
    private static JsonObject parseObject(String json, String message) { try { return JsonParser.parseString(json).getAsJsonObject(); } catch (Exception ex) { throw new GmailException("INVALID_GOOGLE_RESPONSE", message, false, false); } }
    private static String safeDetail(String value) { String clean = value == null ? "" : value.replaceAll("\\s+", " ").trim(); return clean.length() <= 350 ? clean : clean.substring(0, 350); }
    private static String url(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String randomUrlToken(int bytes) { byte[] value = new byte[bytes]; RANDOM.nextBytes(value); return base64Url(value); }
    private static byte[] sha256(byte[] value) { try { return MessageDigest.getInstance("SHA-256").digest(value); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private static String base64Url(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static String base64Mime(String value) { return Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hex(byte[] value) { StringBuilder out = new StringBuilder(); for (byte b : value) out.append(String.format("%02x", b)); return out.toString(); }
    private static String cleanHeader(String value) { return first(value, "").replace("\r", "").replace("\n", "").replace("\\", "\\\\").replace("\"", "\\\""); }
    private static String formatAddress(String email, String name) { return name == null || name.isBlank() ? cleanHeader(email) : mimeHeader(name.trim()) + " <" + cleanHeader(email) + ">"; }
    private static String mimeHeader(String value) { String clean = cleanHeader(value); return StandardCharsets.US_ASCII.newEncoder().canEncode(clean) ? clean : "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.getBytes(StandardCharsets.UTF_8)) + "?="; }
    private static String escapeHtml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private static String first(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String redactClientId(String value) { int marker = value.indexOf('.'); String base = marker > 0 ? value.substring(0, marker) : value; return base.length() <= 10 ? "configured" : base.substring(0, 6) + "..." + base.substring(base.length() - 4); }

    public record ClientImportResult(boolean configured, String clientIdHint) { }
    public record AuthorizationStart(String state, String authorizationUrl, String expiresAt) { }
    public record ConnectionStatus(String senderEmail, String status, String message) { }
    public record SendResult(String senderEmail, String recipientEmail, String messageId) { }
    public record GmailMessage(String fromEmail, String fromName, String toEmail, String bccEmail, String subject,
                               String bodyText, String bodyHtml, String attachmentName,
                               String attachmentContentType, String attachmentBody) { }
    record ClientConfig(String clientId, String clientSecret, String authUri, String tokenUri) { }
    private record PendingAuthorization(String senderEmail, String redirectUri, String verifier, Instant expiresAt) { }

    public static final class GmailException extends RuntimeException {
        private final String category;
        private final boolean transientFailure;
        private final boolean authorizationRequired;
        GmailException(String category, String message, boolean transientFailure, boolean authorizationRequired) {
            super(message); this.category = category; this.transientFailure = transientFailure; this.authorizationRequired = authorizationRequired;
        }
        public String category() { return category; }
        public boolean transientFailure() { return transientFailure; }
        public boolean authorizationRequired() { return authorizationRequired; }
    }
}
