package services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Verifies both public and server-only Supabase credentials without touching schema. */
public final class SupabaseProjectConnectionVerifier {
    private SupabaseProjectConnectionVerifier() {
    }

    public static void verify(SupabaseProjectConfig project, String serverSecret) throws Exception {
        if (project == null) throw new IllegalArgumentException("Supabase project is required.");
        if (serverSecret == null || serverSecret.isBlank()) {
            throw new IllegalArgumentException("Supabase server secret key is required.");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .build();
        verify(client, project.url() + "/auth/v1/settings", project.publishableKey(),
                "The Supabase URL or publishable key could not be verified.");
        verify(client, project.url() + "/rest/v1/", serverSecret.trim(),
                "The Supabase server secret key could not access this project.");
    }

    private static void verify(HttpClient client, String url, String key, String failure)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", key)
                .header("Accept", "application/json")
                .header("User-Agent", "SmartStock-Server-Setup")
                .GET()
                .build();
        HttpResponse<Void> response = client.send(
                request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(failure + " Supabase returned HTTP "
                    + response.statusCode() + ".");
        }
    }
}
