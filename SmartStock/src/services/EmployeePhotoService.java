package services;

import utils.ImageCacheManager;
import utils.ImageOptimizationHelper;

import java.io.File;
import java.util.Locale;

public final class EmployeePhotoService {
    private static final String EMPLOYEE_PHOTO_BUCKET = getConfig("EMPLOYEE_PHOTO_BUCKET", "employee files");
    private static final String EMPLOYEE_PHOTO_FOLDER = getConfig("EMPLOYEE_PHOTO_FOLDER", "employee photos");
    private static final long MAX_ORIGINAL_PHOTO_BYTES = 12L * 1024L * 1024L;
    private static final long MAX_EMPLOYEE_PHOTO_UPLOAD_BYTES = 180L * 1024L;
    private EmployeePhotoService() {
    }

    public static String uploadLocalPhotoIfNeeded(String photoPathOrUrl, String employeeName) throws Exception {
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
            String employeeSlug = sanitizePathPart(employeeName);
            String filename = StorageObjectNameBuilder.filename(
                    optimizedImage.filename(), "jpg", Long.toString(System.currentTimeMillis()),
                    employeeName, "employee-photo");
            String objectPath = EMPLOYEE_PHOTO_FOLDER + "/" + employeeSlug + "/" + filename;
            String authenticatedUrl = LanApiClient.uploadCloudFile(
                    EMPLOYEE_PHOTO_BUCKET, objectPath, optimizedImage.contentType(),
                    java.nio.file.Files.readAllBytes(optimizedImage.file().toPath()));
            ImageCacheManager.cacheUploadedImage(authenticatedUrl, optimizedImage.file().toPath());
            return authenticatedUrl;
        }
    }

    private static String sanitizePathPart(String value) {
        String sanitized = value == null ? "employee" : value.trim().toLowerCase(Locale.ROOT);
        sanitized = sanitized.replaceAll("[^a-z0-9._-]", "-").replaceAll("-+", "-");
        return sanitized.isBlank() ? "employee" : sanitized;
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
