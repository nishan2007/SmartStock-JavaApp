package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Shared server-only HTTP boundary for privileged Supabase RPC calls. */
final class SupabaseServerApi {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SupabaseServerApi() {
    }

    static Response postRpc(String functionName, JsonObject body)
            throws IOException, InterruptedException {
        if (functionName == null || !functionName.matches("[a-z][a-z0-9_]{0,100}")) {
            throw new IllegalArgumentException("Invalid Supabase RPC function name.");
        }
        HttpRequest.Builder builder = authenticatedBuilder(
                "/rest/v1/rpc/" + functionName)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        GSON.toJson(body == null ? new JsonObject() : body),
                        StandardCharsets.UTF_8));
        HttpResponse<String> response = HTTP.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    static Response getTablePage(String table, int offset, int limit)
            throws IOException, InterruptedException {
        if (table == null || !table.matches("[a-z][a-z0-9_]{0,100}")) {
            throw new IllegalArgumentException("Invalid Supabase table name.");
        }
        int cleanOffset = Math.max(offset, 0);
        int cleanLimit = Math.min(Math.max(limit, 1), 1_000);
        HttpRequest request = authenticatedBuilder("/rest/v1/" + table
                        + "?select=*&offset=" + cleanOffset + "&limit=" + cleanLimit)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    static Response insertIgnoreDuplicates(String table, JsonObject row, String conflictColumns)
            throws IOException, InterruptedException {
        if (table == null || !table.matches("[a-z][a-z0-9_]{0,100}")
                || conflictColumns == null
                || !conflictColumns.matches("[a-z_]+(?:,[a-z_]+)*")) {
            throw new IllegalArgumentException("Invalid Supabase table or conflict columns.");
        }
        HttpRequest request = authenticatedBuilder("/rest/v1/" + table
                        + "?on_conflict=" + conflictColumns)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Prefer", "resolution=ignore-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(row),
                        StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    /** Shares only public OneDrive identifiers; certificate and private key remain on each server. */
    static Response upsertOneDriveIdentifiers(JsonObject row)throws IOException,InterruptedException{
        HttpRequest request=authenticatedBuilder("/rest/v1/image_cloud_configuration?on_conflict=provider")
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type","application/json")
                .header("Prefer","resolution=merge-duplicates,return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(row),StandardCharsets.UTF_8)).build();
        HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(),response.body());
    }

    /** Narrow, credential-free employee-role history used by the one-time store repair. */
    static Response getRoleRecoveryHistory(int locationId, Long beforeSequence, int offset, int limit)
            throws IOException, InterruptedException {
        if (locationId <= 0 || offset < 0 || limit < 1 || limit > 1_000
                || beforeSequence != null && beforeSequence <= 0) {
            throw new IllegalArgumentException("Invalid role recovery history request.");
        }
        String select = "cloud_sequence,location_id,created_at,"
                + "user_id:payload->row_data->>user_id,role_name:payload->row_data->>role_name,"
                + "auth_user_id:payload->row_data->>auth_user_id";
        String route = "/rest/v1/sync_outbox?select="
                + java.net.URLEncoder.encode(select, StandardCharsets.UTF_8)
                + "&location_id=eq." + locationId
                + "&event_type=eq.REFERENCE_ROW_CHANGED&payload-%3E%3Etable_name=eq.users"
                + (beforeSequence == null ? "" : "&cloud_sequence=lt." + beforeSequence)
                + "&order=cloud_sequence.asc&offset=" + offset + "&limit=" + limit;
        HttpResponse<String> response = HTTP.send(authenticatedBuilder(route)
                .timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private static HttpRequest.Builder authenticatedBuilder(String route) {
        SupabaseProjectConfig project = SupabaseProjectConfig.load();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(project.url() + route))
                .header("User-Agent", "SmartStock-Server");
        return ServerSupabaseCredentials.applyTo(builder);
    }

    static String failureMessage(String operation, Response response) {
        String detail = "";
        try {
            JsonObject body = GSON.fromJson(response.body(), JsonObject.class);
            if (body != null) {
                for (String key : new String[]{"message", "details", "hint"}) {
                    if (body.has(key) && !body.get(key).isJsonNull()) {
                        String value = body.get(key).getAsString().replaceAll("[\\r\\n]+", " ").trim();
                        if (!value.isBlank()) {
                            detail = " " + value.substring(0, Math.min(value.length(), 500));
                            break;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Never expose an unstructured proxy response or credentials in an error dialog.
        }
        return operation + " returned HTTP " + response.statusCode() + "." + detail;
    }

    record Response(int statusCode, String body) {
        boolean successful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
