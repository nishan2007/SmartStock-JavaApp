package managers;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import utils.SecureFilePermissions;

public final class SupabaseSessionManager {

    private static final String SUPABASE_URL = getConfig("SUPABASE_URL", "https://wbffhygkttoaaodjcvuh.supabase.co");
    private static final String SUPABASE_PUBLISHABLE_KEY = getConfig("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_A_Z2rTrylkxY9JIRCM1pRQ_Rf56Lqja");
    private static final Path SESSION_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "session.properties");
    // Refresh shortly before expiry so callers never send a known-expired JWT to /auth/v1/user.
    private static final long ACCESS_TOKEN_REFRESH_SKEW_SECONDS = 60;
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

    public static synchronized PersistedSession loadPersistedSession() {
        if (!Files.exists(SESSION_PATH)) {
            return null;
        }

        Properties properties = new Properties();
        try (var input = Files.newInputStream(SESSION_PATH)) {
            properties.load(input);
            String refreshToken = blankToNull(properties.getProperty("refresh_token"));
            Integer userId = parseInteger(properties.getProperty("user_id"));
            Integer locationId = parseInteger(properties.getProperty("location_id"));

            if (refreshToken == null || userId == null || locationId == null) {
                clearPersistedSession();
                return null;
            }

            return new PersistedSession(
                    blankToNull(properties.getProperty("access_token")),
                    refreshToken,
                    userId,
                    locationId
            );
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static synchronized boolean hasPersistedSession() {
        return Files.exists(SESSION_PATH) && loadPersistedSession() != null;
    }

    public static synchronized void savePersistedSession(Integer userId, Integer locationId) {
        String refreshToken = SessionManager.getCurrentRefreshToken();
        if (refreshToken == null || refreshToken.isBlank() || userId == null || locationId == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("access_token", SessionManager.getCurrentAccessToken() == null ? "" : SessionManager.getCurrentAccessToken());
        properties.setProperty("refresh_token", refreshToken);
        properties.setProperty("user_id", String.valueOf(userId));
        properties.setProperty("location_id", String.valueOf(locationId));

        try {
            Files.createDirectories(SESSION_PATH.getParent());
            SecureFilePermissions.restrictDirectoryToOwner(SESSION_PATH.getParent());
            try (var output = Files.newOutputStream(SESSION_PATH)) {
                properties.store(output, "SmartStock stay signed in session");
            }
            SecureFilePermissions.restrictFileToOwner(SESSION_PATH);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static synchronized void clearPersistedSession() {
        try {
            Files.deleteIfExists(SESSION_PATH);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
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

        restorePersistedSessionForCurrentUserIfNeeded();

        String accessToken = SessionManager.getCurrentAccessToken();
        if (accessToken == null || accessToken.isBlank() || isAccessTokenExpiredOrExpiring(accessToken)) {
            return refreshAccessToken();
        }

        try {
            validateAccessToken(accessToken);
            return accessToken;
        } catch (IllegalStateException ex) {
            // A token can still be revoked before its exp claim. Refresh once after that
            // server-side rejection, but never use /auth/v1/user as the expiry check.
            return refreshAccessToken();
        }
    }

    private static void restorePersistedSessionForCurrentUserIfNeeded() {
        if (SessionManager.getCurrentRefreshToken() != null
                && !SessionManager.getCurrentRefreshToken().isBlank()) {
            return;
        }
        Integer currentUserId = SessionManager.getCurrentUserId();
        Integer currentLocationId = SessionManager.getCurrentLocationId();
        PersistedSession persisted = loadPersistedSession();
        if (persisted != null
                && persisted.userId().equals(currentUserId)
                && persisted.locationId().equals(currentLocationId)) {
            setSession(persisted.accessToken(), persisted.refreshToken());
        }
    }

    public static synchronized String forceRefreshSession() throws IOException, InterruptedException {
        ensureConfig();
        return refreshAccessToken();
    }

    private static String refreshAccessToken() throws IOException, InterruptedException {
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

    public static synchronized String getCurrentAuthUserId() {
        return extractJwtStringClaim(SessionManager.getCurrentAccessToken(), "sub");
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

    private static String extractJwtStringClaim(String jwt, String claimName) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }

        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return extractJsonString(payload, claimName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isAccessTokenExpiredOrExpiring(String accessToken) {
        Long expiresAt = extractJwtLongClaim(accessToken, "exp");
        return expiresAt != null
                && Instant.now().getEpochSecond() >= expiresAt - ACCESS_TOKEN_REFRESH_SKEW_SECONDS;
    }

    private static Long extractJwtLongClaim(String jwt, String claimName) {
        if (jwt == null || jwt.isBlank()) {
            return null;
        }

        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(claimName) + "\\\"\\s*:\\s*(\\d+)");
            Matcher matcher = pattern.matcher(payload);
            return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
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

    public record PersistedSession(String accessToken, String refreshToken, Integer userId, Integer locationId) {
    }
}
