package services;

import managers.SupabaseSessionManager;
import utils.SecureFilePermissions;
import utils.ImageCacheManager;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

public final class EmployeeDocumentService {
    private static final String EMPLOYEE_FILE_BUCKET = getConfig("EMPLOYEE_FILE_BUCKET", "employee files");
    private static final String ID_CARD_FOLDER = getConfig("EMPLOYEE_ID_CARD_FOLDER", "ID cards");
    private static final long MAX_ID_CARD_DOCUMENT_BYTES = 25L * 1024L * 1024L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private EmployeeDocumentService() {
    }

    public static String uploadLocalIdCardDocumentIfNeeded(String documentPathOrUrl, String employeeLabel) throws Exception {
        String value = documentPathOrUrl == null ? "" : documentPathOrUrl.trim();
        if (value.isBlank() || ImageCacheManager.isRemoteImageUrl(value)) {
            return value;
        }

        File documentFile = new File(value);
        if (!documentFile.isFile()) {
            throw new IllegalArgumentException("The selected ID card document file was not found.");
        }
        if (documentFile.length() > MAX_ID_CARD_DOCUMENT_BYTES) {
            throw new IllegalArgumentException("ID card documents must be 25 MB or smaller.");
        }

        String contentType = Files.probeContentType(documentFile.toPath());
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        String accessToken = SupabaseSessionManager.getValidAccessToken();
        String objectPath = ID_CARD_FOLDER
                + "/"
                + sanitizePathPart(employeeLabel)
                + "/"
                + System.currentTimeMillis()
                + "-"
                + sanitizeFilename(documentFile.getName());
        String encodedBucket = encodePathSegment(EMPLOYEE_FILE_BUCKET);
        String encodedObjectPath = encodeObjectPath(objectPath);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                        + "/storage/v1/object/"
                        + encodedBucket
                        + "/"
                        + encodedObjectPath))
                .timeout(Duration.ofSeconds(60))
                .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofFile(documentFile.toPath()))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Supabase Storage returned HTTP "
                    + response.statusCode()
                    + " while uploading employee ID card document to bucket "
                    + EMPLOYEE_FILE_BUCKET
                    + ": "
                    + response.body());
        }

        return SupabaseSessionManager.getSupabaseUrl()
                + "/storage/v1/object/authenticated/"
                + encodedBucket
                + "/"
                + encodedObjectPath;
    }

    public static File downloadAuthenticatedDocument(String documentUrl) throws Exception {
        String value = documentUrl == null ? "" : documentUrl.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("No ID card document is saved for this employee.");
        }
        URI uri = URI.create(value);
        if (!uri.getPath().contains("/storage/v1/object/authenticated/")) {
            throw new IllegalArgumentException("The saved ID card document is not an authenticated Storage URL.");
        }

        String accessToken = SupabaseSessionManager.getValidAccessToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(60))
                .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Supabase Storage returned HTTP "
                    + response.statusCode()
                    + " while downloading employee ID card document: "
                    + new String(response.body(), StandardCharsets.UTF_8));
        }

        Path targetDirectory = Path.of(System.getProperty("user.home"), ".smartstock", "employee-id-card-cache");
        Files.createDirectories(targetDirectory);
        SecureFilePermissions.restrictDirectoryToOwner(targetDirectory);
        Path target = targetDirectory.resolve(sanitizeFilename(extractFilename(uri)));
        Files.write(target, response.body());
        SecureFilePermissions.restrictFileToOwner(target);
        return target.toFile();
    }

    public static boolean isAuthenticatedStorageUrl(String documentUrl) {
        String value = documentUrl == null ? "" : documentUrl.trim();
        return value.startsWith(SupabaseSessionManager.getSupabaseUrl() + "/storage/v1/object/authenticated/");
    }

    private static String extractFilename(URI uri) {
        String path = uri.getPath();
        int slashIndex = path == null ? -1 : path.lastIndexOf('/');
        String filename = slashIndex < 0 ? path : path.substring(slashIndex + 1);
        if (filename == null || filename.isBlank()) {
            return "id-card-document";
        }
        return URLDecoder.decode(filename, StandardCharsets.UTF_8);
    }

    private static String sanitizeFilename(String filename) {
        String sanitized = filename == null ? "id-card-document" : filename.trim();
        sanitized = sanitized.replaceAll("[^A-Za-z0-9._-]", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "id-card-document";
        }
        return sanitized;
    }

    private static String sanitizePathPart(String value) {
        String sanitized = value == null ? "employee" : value.trim().toLowerCase(Locale.ROOT);
        sanitized = sanitized.replaceAll("[^a-z0-9._-]", "-").replaceAll("-+", "-");
        return sanitized.isBlank() ? "employee" : sanitized;
    }

    private static String encodeObjectPath(String objectPath) {
        String[] parts = objectPath.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String part : parts) {
            if (!encoded.isEmpty()) {
                encoded.append("/");
            }
            encoded.append(encodePathSegment(part));
        }
        return encoded.toString();
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
