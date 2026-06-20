package utils;

import managers.SupabaseSessionManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

public final class ImageCacheManager {
    private static final Path CACHE_DIRECTORY = Path.of(System.getProperty("user.home"), ".smartstock", "image-cache");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ImageCacheManager() {
    }

    public static boolean isRemoteImageUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    public static BufferedImage loadImage(String pathOrUrl) {
        String value = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (value.isBlank()) {
            return null;
        }

        try {
            if (isRemoteImageUrl(value)) {
                return loadRemoteImage(value);
            }

            Path path = Path.of(value);
            if (!Files.exists(path)) {
                return null;
            }
            return ImageIO.read(path.toFile());
        } catch (Exception ex) {
            return null;
        }
    }

    public static void cacheUploadedImage(String remoteUrl, Path sourcePath) {
        if (!isRemoteImageUrl(remoteUrl) || sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return;
        }

        try {
            Files.createDirectories(CACHE_DIRECTORY);
            SecureFilePermissions.restrictDirectoryToOwner(CACHE_DIRECTORY);
            Path target = cachePath(remoteUrl);
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
            SecureFilePermissions.restrictFileToOwner(target);
        } catch (IOException ex) {
            // Cache failure should not block the actual image upload/save path.
        }
    }

    private static BufferedImage loadRemoteImage(String imageUrl) throws IOException, InterruptedException {
        Path cachePath = cachePath(imageUrl);
        BufferedImage cached = readCachedImage(cachePath);
        if (cached != null) {
            return cached;
        }

        Files.createDirectories(CACHE_DIRECTORY);
        SecureFilePermissions.restrictDirectoryToOwner(CACHE_DIRECTORY);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (isSupabaseStorageUrl(imageUrl)) {
            builder.header("apikey", SupabaseSessionManager.getSupabasePublishableKey());
        }
        String authHeader = authHeaderFor(imageUrl);
        if (!authHeader.isBlank()) {
            builder.header("Authorization", authHeader);
        }
        HttpRequest request = builder.build();
        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            return null;
        }

        Path tempPath = Files.createTempFile(CACHE_DIRECTORY, "image-", ".tmp");
        try {
            Files.write(tempPath, response.body());
            BufferedImage downloaded = ImageIO.read(tempPath.toFile());
            if (downloaded == null) {
                return null;
            }
            Files.move(tempPath, cachePath, StandardCopyOption.REPLACE_EXISTING);
            SecureFilePermissions.restrictFileToOwner(cachePath);
            return downloaded;
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private static BufferedImage readCachedImage(Path cachePath) {
        if (!Files.isRegularFile(cachePath)) {
            return null;
        }
        try {
            return ImageIO.read(cachePath.toFile());
        } catch (IOException ex) {
            return null;
        }
    }

    private static Path cachePath(String imageUrl) {
        return CACHE_DIRECTORY.resolve(sha256(imageUrl) + "." + extensionForUrl(imageUrl));
    }

    private static String extensionForUrl(String imageUrl) {
        try {
            String path = URI.create(imageUrl).getPath();
            int dot = path == null ? -1 : path.lastIndexOf('.');
            if (dot >= 0 && dot < path.length() - 1) {
                String extension = path.substring(dot + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (!extension.isBlank() && extension.length() <= 5) {
                    if ("jpeg".equals(extension)) {
                        return "jpg";
                    }
                    return extension;
                }
            }
        } catch (Exception ex) {
            // Fall through to a broadly readable default.
        }
        return "img";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private static String authHeaderFor(String imageUrl) {
        if (!isSupabaseStorageUrl(imageUrl) || !imageUrl.contains("/storage/v1/object/authenticated/")) {
            return "";
        }
        try {
            return "Bearer " + SupabaseSessionManager.getValidAccessToken();
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean isSupabaseStorageUrl(String imageUrl) {
        return imageUrl != null
                && imageUrl.startsWith(SupabaseSessionManager.getSupabaseUrl() + "/storage/v1/object/");
    }
}
