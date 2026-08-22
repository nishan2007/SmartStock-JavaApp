package utils;

import data.EnvironmentProfile;
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
    private static final Path LEGACY_CACHE_DIRECTORY =
            Path.of(System.getProperty("user.home"), ".smartstock", "image-cache");
    private static final Object CACHE_LOCK = new Object();
    private static volatile Path preparedCacheDirectory;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ImageCacheManager() {
    }

    public static boolean isRemoteImageUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://")
                || services.ImageAssetReference.isAssetReference(value));
    }

    public static BufferedImage loadImage(String pathOrUrl) {
        String value = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (value.isBlank()) {
            return null;
        }

        try {
            if (isRemoteImageUrl(value)) {
                if (services.ImageAssetReference.isAssetReference(value)) {
                    return loadAssetImage(value);
                }
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

    /** Resolves protected/remote image references to the owner-only local cache for Swing HTML previews. */
    public static String resolveDisplayUrl(String pathOrUrl) {
        String value = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (value.isBlank() || value.startsWith("file:")) return value;
        try {
            if (isRemoteImageUrl(value)) {
                if (loadImage(value) == null) return value;
                return cachePath(value).toUri().toString();
            }
            Path path = Path.of(value).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path.toUri().toString() : value;
        } catch (Exception ex) {
            return value;
        }
    }

    public static void cacheUploadedImage(String remoteUrl, Path sourcePath) {
        if (!isRemoteImageUrl(remoteUrl) || sourcePath == null || !Files.isRegularFile(sourcePath)) {
            return;
        }

        try {
            Path cacheDirectory = cacheDirectory();
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

        cacheDirectory();
        byte[] proxied = null;
        if (isSupabaseStorageUrl(imageUrl) && imageUrl.contains("/storage/v1/object/authenticated/")) {
            try {
                String auth = authHeaderFor(imageUrl);
                if (auth.isBlank()) proxied = services.LanApiClient.downloadEmployeeCloudFile(imageUrl);
            } catch (Exception ignored) {
                proxied = null;
            }
        }
        if (proxied != null && proxied.length > 0) {
            return cacheDownloadedImage(cachePath, proxied);
        }
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

        return cacheDownloadedImage(cachePath, response.body());
    }

    private static BufferedImage loadAssetImage(String reference) throws IOException {
        Path cachePath = cachePath(reference);
        BufferedImage cached = readCachedImage(cachePath);
        if (cached != null) return cached;
        try {
            byte[] bytes;
            if (data.DatabaseConfig.load().mode() == data.DatabaseMode.SERVER) {
                bytes = services.ServerImageAssetService.load(reference).bytes();
            } else {
                bytes = services.LanApiClient.downloadImageAsset(reference);
            }
            return cacheDownloadedImage(cachePath, bytes);
        } catch (Exception ex) {
            throw new IOException("SmartStock server image download failed.", ex);
        }
    }

    private static BufferedImage cacheDownloadedImage(Path cachePath, byte[] bytes) throws IOException {
        Path tempPath = Files.createTempFile(cacheDirectory(), "image-", ".tmp");
        try {
            Files.write(tempPath, bytes);
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

    private static Path cachePath(String imageUrl) throws IOException {
        return cacheDirectory().resolve(sha256(imageUrl) + "." + extensionForUrl(imageUrl));
    }

    static Path cacheDirectory() throws IOException {
        String override = System.getProperty("smartstock.image.cache");
        Path desired = override == null || override.isBlank()
                ? EnvironmentProfile.active().directory().resolve("image-cache")
                : Path.of(override.trim());
        desired = desired.toAbsolutePath().normalize();
        Path ready = preparedCacheDirectory;
        if (desired.equals(ready)) return desired;
        synchronized (CACHE_LOCK) {
            if (desired.equals(preparedCacheDirectory)) return desired;
            if ((override == null || override.isBlank())
                    && EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT
                    && Files.isDirectory(LEGACY_CACHE_DIRECTORY)
                    && !Files.exists(desired)) {
                Files.createDirectories(desired.getParent());
                try {
                    Files.move(LEGACY_CACHE_DIRECTORY, desired, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                    Files.move(LEGACY_CACHE_DIRECTORY, desired);
                }
            }
            Files.createDirectories(desired);
            SecureFilePermissions.restrictDirectoryToOwner(desired);
            preparedCacheDirectory = desired;
            return desired;
        }
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
