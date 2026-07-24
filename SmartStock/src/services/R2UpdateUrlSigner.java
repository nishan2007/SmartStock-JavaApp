package services;

import data.EnvironmentProfile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;

/** Creates short-lived download URLs for private SmartStock update objects in R2. */
final class R2UpdateUrlSigner {
    static final String R2_BUCKET_REFERENCE = "r2:smartstock-updates";
    private static final String WORKER_URL_KEY = "SMARTSTOCK_UPDATE_R2_WORKER_URL";
    private static final String SIGNING_SECRET_KEY = "SMARTSTOCK_UPDATE_R2_SIGNING_SECRET";
    private static final String CONFIG_FILE_NAME = "r2-update.properties";
    private static final long URL_LIFETIME_SECONDS = 10 * 60;

    private R2UpdateUrlSigner() {
    }

    static boolean handles(String bucketReference) {
        return bucketReference != null && bucketReference.startsWith("r2:");
    }

    static String createDownloadUrl(String bucketReference, String objectKey) throws Exception {
        return createDownloadUrl(bucketReference, objectKey, Instant.now(),
                configured(WORKER_URL_KEY), configured(SIGNING_SECRET_KEY));
    }

    static String createDownloadUrl(String bucketReference, String objectKey, Instant now,
                                    String workerUrl, String signingSecret) throws Exception {
        if (!R2_BUCKET_REFERENCE.equals(bucketReference)) {
            throw new IllegalArgumentException("The R2 update bucket is not allowed.");
        }
        validateObjectKey(objectKey);
        String baseUrl = validateWorkerUrl(workerUrl);
        if (signingSecret == null || signingSecret.length() < 32) {
            throw new IllegalStateException(SIGNING_SECRET_KEY
                    + " must contain at least 32 characters on the SmartStock server.");
        }

        String encodedPath = encodePath(objectKey);
        long expires = now.getEpochSecond() + URL_LIFETIME_SECONDS;
        String canonical = canonicalRequest(encodedPath, expires);
        String signature = hmacSha256(signingSecret, canonical);
        return baseUrl + "/" + encodedPath + "?expires=" + expires + "&signature=" + signature;
    }

    static String canonicalRequest(String encodedPath, long expires) {
        return "GET\n/" + encodedPath + "\n" + expires;
    }

    static String encodePath(String objectKey) {
        return java.util.Arrays.stream(objectKey.split("/", -1))
                .map(R2UpdateUrlSigner::encodePathSegment)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%7E", "~");
    }

    private static String hmacSha256(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String validateWorkerUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(WORKER_URL_KEY + " is not configured on the SmartStock server.");
        }
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        URI uri;
        try {
            uri = URI.create(result);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(WORKER_URL_KEY + " is invalid.", ex);
        }
        boolean localTest = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!localTest && !"https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalStateException(WORKER_URL_KEY + " must be an HTTPS origin URL.");
        }
        return result;
    }

    private static void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.contains("\\") || java.util.Arrays.asList(objectKey.split("/", -1)).contains("..")) {
            throw new IllegalArgumentException("The R2 update object path is invalid.");
        }
    }

    private static String configured(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) value = System.getProperty(key);
        if (value == null || value.isBlank()) {
            Properties saved = new Properties();
            Path path = EnvironmentProfile.active().file(CONFIG_FILE_NAME);
            if (Files.isRegularFile(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    saved.load(input);
                    value = saved.getProperty(key);
                } catch (Exception ignored) {
                }
            }
        }
        return value == null ? null : value.trim();
    }
}
