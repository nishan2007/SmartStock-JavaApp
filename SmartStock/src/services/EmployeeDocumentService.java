package services;

import managers.SupabaseSessionManager;
import utils.SecureFilePermissions;
import utils.ImageCacheManager;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class EmployeeDocumentService {
    private static final String EMPLOYEE_FILE_BUCKET = getConfig("EMPLOYEE_FILE_BUCKET", "employee files");
    private static final String ID_CARD_FOLDER = getConfig("EMPLOYEE_ID_CARD_FOLDER", "ID cards");
    private static final long MAX_ID_CARD_DOCUMENT_BYTES = 25L * 1024L * 1024L;
    private EmployeeDocumentService() {
    }

    public static String uploadLocalIdCardDocumentIfNeeded(String documentPathOrUrl, String employeeName) throws Exception {
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

        String employeeSlug = sanitizePathPart(employeeName);
        String filename = StorageObjectNameBuilder.filename(
                documentFile.getName(), "bin", Long.toString(System.currentTimeMillis()),
                employeeName, "id-card-document");
        String objectPath = ID_CARD_FOLDER + "/" + employeeSlug + "/" + filename;
        return LanApiClient.uploadCloudFile(EMPLOYEE_FILE_BUCKET, objectPath, contentType,
                Files.readAllBytes(documentFile.toPath()));
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

        byte[] downloaded = LanApiClient.downloadEmployeeCloudFile(value);

        Path targetDirectory = Path.of(System.getProperty("user.home"), ".smartstock", "employee-id-card-cache");
        Files.createDirectories(targetDirectory);
        SecureFilePermissions.restrictDirectoryToOwner(targetDirectory);
        Path target = targetDirectory.resolve(sanitizeFilename(extractFilename(uri)));
        Files.write(target, downloaded);
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
