package managers;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SupabaseSessionManager {

    private static final String SUPABASE_URL = getConfig("SUPABASE_URL", "https://wbffhygkttoaaodjcvuh.supabase.co");
    private static final String SUPABASE_PUBLISHABLE_KEY = getConfig("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_A_Z2rTrylkxY9JIRCM1pRQ_Rf56Lqja");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private SupabaseSessionManager() {
    }

    public static synchronized void setSession(String accessToken, String refreshToken) {
        SessionManager.setCurrentAccessToken(blankToNull(accessToken));
        SessionManager.setCurrentRefreshToken(blankToNull(refreshToken));
    }

    public static synchronized void clearSession() {
        SessionManager.setCurrentAccessToken(null);
        SessionManager.setCurrentRefreshToken(null);
    }

    public static synchronized String getAccessToken() {
        return SessionManager.getCurrentAccessToken();
    }

    public static synchronized String getRefreshToken() {
        return SessionManager.getCurrentRefreshToken();
    }

    public static String getSupabaseUrl() {
        return SUPABASE_URL;
    }

    public static String getSupabasePublishableKey() {
        return SUPABASE_PUBLISHABLE_KEY;
    }

    public static synchronized String getValidAccessToken() throws IOException, InterruptedException {
        ensureConfig();

        if (SessionManager.getCurrentAccessToken() == null || SessionManager.getCurrentAccessToken().isBlank()) {
            throw new IllegalStateException("No active Supabase session. Please log in again.");
        }

        try {
            validateAccessToken(SessionManager.getCurrentAccessToken());
            return SessionManager.getCurrentAccessToken();
        } catch (IllegalStateException ex) {
            if (SessionManager.getCurrentRefreshToken() == null || SessionManager.getCurrentRefreshToken().isBlank()) {
                clearSession();
                throw new IllegalStateException("Session expired. Please log in again.");
            }

            refreshSessionNow();

            if (SessionManager.getCurrentAccessToken() == null || SessionManager.getCurrentAccessToken().isBlank()) {
                throw new IllegalStateException("Session refresh failed. Please log in again.");
            }

            return SessionManager.getCurrentAccessToken();
        }
    }

    public static synchronized String forceRefreshSession() throws IOException, InterruptedException {
        ensureConfig();
        refreshSessionNow();
        if (SessionManager.getCurrentAccessToken() == null || SessionManager.getCurrentAccessToken().isBlank()) {
            throw new IllegalStateException("Session refresh failed. Please log in again.");
        }
        return SessionManager.getCurrentAccessToken();
    }

    private static void validateAccessToken(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/auth/v1/user"))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new IllegalStateException("Access token expired or invalid.");
        }

        throw new IllegalStateException("Unable to validate session: HTTP " + response.statusCode());
    }

    private static void refreshSessionNow() throws IOException, InterruptedException {
        if (SessionManager.getCurrentRefreshToken() == null || SessionManager.getCurrentRefreshToken().isBlank()) {
            throw new IllegalStateException("No refresh token available. Please log in again.");
        }

        String body = "{" +
                "\"refresh_token\":" + jsonValue(SessionManager.getCurrentRefreshToken()) +
                "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token"))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", SUPABASE_PUBLISHABLE_KEY)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            clearSession();
            String error = extractJsonString(response.body(), "error_description");
            if (error == null || error.isBlank()) {
                error = extractJsonString(response.body(), "msg");
            }
            if (error == null || error.isBlank()) {
                error = extractJsonString(response.body(), "error");
            }
            throw new IllegalStateException(error == null || error.isBlank()
                    ? "Session refresh failed. Please log in again."
                    : error);
        }

        String newAccessToken = extractJsonString(response.body(), "access_token");
        String newRefreshToken = extractJsonString(response.body(), "refresh_token");

        if (newAccessToken == null || newAccessToken.isBlank()) {
            clearSession();
            throw new IllegalStateException("Refresh succeeded but no access token was returned.");
        }

        SessionManager.setCurrentAccessToken(newAccessToken);
        SessionManager.setCurrentRefreshToken(blankToNull(newRefreshToken != null ? newRefreshToken : SessionManager.getCurrentRefreshToken()));
    }

    private static void ensureConfig() {
        if (SUPABASE_URL == null || SUPABASE_URL.isBlank()) {
            throw new IllegalStateException("Missing SUPABASE_URL configuration.");
        }
        if (SUPABASE_PUBLISHABLE_KEY == null || SUPABASE_PUBLISHABLE_KEY.isBlank()) {
            throw new IllegalStateException("Missing SUPABASE_PUBLISHABLE_KEY configuration.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String jsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonString(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }

        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String getConfig(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        if (value == null || value.isBlank()) {
            value = fallback;
        }
        return value;
    }
}
