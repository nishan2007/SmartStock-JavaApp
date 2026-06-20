package services;

import managers.SupabaseSessionManager;
import utils.ImageCacheManager;
import utils.ImageOptimizationHelper;

import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public final class EmployeePhotoService {
    private static final String EMPLOYEE_PHOTO_BUCKET = getConfig("EMPLOYEE_PHOTO_BUCKET", "employee files");
    private static final String EMPLOYEE_PHOTO_FOLDER = getConfig("EMPLOYEE_PHOTO_FOLDER", "employee photos");
    private static final long MAX_ORIGINAL_PHOTO_BYTES = 12L * 1024L * 1024L;
    private static final long MAX_EMPLOYEE_PHOTO_UPLOAD_BYTES = 180L * 1024L;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private EmployeePhotoService() {
    }

    public static String uploadLocalPhotoIfNeeded(String photoPathOrUrl, String employeeLabel) throws Exception {
        String value = photoPathOrUrl == null ? "" : photoPathOrUrl.trim();
        if (value.isBlank() || ImageCacheManager.isRemoteImageUrl(value)) {
            return value;
        }

        File imageFile = new File(value);
        if (!imageFile.isFile()) {
            throw new IllegalArgumentException("The selected employee photo file was not found.");
        }

        try (ImageOptimizationHelper.OptimizedImage optimizedImage = ImageOptimizationHelper.optimizeForUpload(
                imageFile,
                "employee-photo",
                900,
                900,
                0.82f,
                MAX_ORIGINAL_PHOTO_BYTES,
                MAX_EMPLOYEE_PHOTO_UPLOAD_BYTES,
                false
        )) {
            String accessToken = SupabaseSessionManager.getValidAccessToken();
            String objectPath = EMPLOYEE_PHOTO_FOLDER
                    + "/"
                    + sanitizePathPart(employeeLabel)
                    + "/"
                    + System.currentTimeMillis()
                    + "-"
                    + sanitizeFilename(optimizedImage.filename());
            String encodedBucket = encodePathSegment(EMPLOYEE_PHOTO_BUCKET);
            String encodedObjectPath = encodeObjectPath(objectPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                            + "/storage/v1/object/"
                            + encodedBucket
                            + "/"
                            + encodedObjectPath))
                    .timeout(Duration.ofSeconds(45))
                    .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", optimizedImage.contentType())
                    .header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofFile(optimizedImage.file().toPath()))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Supabase Storage returned HTTP "
                        + response.statusCode()
                        + " while uploading employee photo to bucket "
                        + EMPLOYEE_PHOTO_BUCKET
                        + ": "
                        + response.body());
            }

            String authenticatedUrl = SupabaseSessionManager.getSupabaseUrl()
                    + "/storage/v1/object/authenticated/"
                    + encodedBucket
                    + "/"
                    + encodedObjectPath;
            ImageCacheManager.cacheUploadedImage(authenticatedUrl, optimizedImage.file().toPath());
            return authenticatedUrl;
        }
    }

    private static String sanitizeFilename(String filename) {
        String sanitized = filename == null ? "employee-photo" : filename.trim();
        sanitized = sanitized.replaceAll("[^A-Za-z0-9._-]", "-");
        sanitized = sanitized.replaceAll("-+", "-");
        if (sanitized.isBlank() || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "employee-photo";
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
