package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/** Minimal server-only client for Supabase Auth administrator operations. */
final class SupabaseAuthAdminClient {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private SupabaseAuthAdminClient() {
    }

    static UUID createConfirmedUser(String email, char[] password, String displayName)
            throws IOException, InterruptedException {
        char[] copy = password == null ? new char[0] : password.clone();
        try {
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email is required.");
            }
            if (copy.length < 8) {
                throw new IllegalArgumentException("Password must contain at least 8 characters.");
            }
            JsonObject metadata = new JsonObject();
            metadata.addProperty("name", displayName == null ? "" : displayName.trim());
            JsonObject body = new JsonObject();
            body.addProperty("email", email.trim().toLowerCase());
            body.addProperty("password", new String(copy));
            body.addProperty("email_confirm", true);
            body.add("user_metadata", metadata);

            SupabaseProjectConfig project = SupabaseProjectConfig.load();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(project.url() + "/auth/v1/admin/users"))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "SmartStock-Server")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(),
                            StandardCharsets.UTF_8));
            HttpResponse<String> response = HTTP.send(
                    ServerSupabaseCredentials.applyTo(builder).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Supabase Auth could not create the administrator "
                        + "(HTTP " + response.statusCode() + "). "
                        + safeError(response.body()));
            }
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String id = json.has("id") ? json.get("id").getAsString() : null;
            if (id == null || id.isBlank()) {
                throw new IOException("Supabase Auth created the user without returning an ID.");
            }
            return UUID.fromString(id);
        } finally {
            Arrays.fill(copy, '\0');
            if (password != null) Arrays.fill(password, '\0');
        }
    }

    private static String safeError(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            for (String key : new String[]{"msg", "message", "error_description", "error"}) {
                if (json.has(key) && json.get(key).isJsonPrimitive()) {
                    String value = json.get(key).getAsString();
                    if (!value.isBlank() && !value.toLowerCase().contains("password")) return value;
                }
            }
        } catch (Exception ignored) {
        }
        return "Check the email, Auth settings, and server secret key.";
    }
}
