package services;

import managers.SupabaseSessionManager;
import utils.DeviceUtils;

import javax.swing.*;
import java.awt.Component;
import java.awt.Dialog;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppUpdateService {
    private static final String RELEASE_BUCKET = "smartstock-releases";
    private static final Path SMARTSTOCK_ROOT =
            Path.of(System.getProperty("user.home"), ".smartstock");
    private static final Path UPDATE_ROOT = SMARTSTOCK_ROOT.resolve("updates");
    private static final Path ROLLBACK_DIR = SMARTSTOCK_ROOT.resolve("rollback");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private AppUpdateService() {
    }

    public static void checkForUpdatesAsync(Component parent, boolean manual) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private UpdateCheckResult result;
            private Exception error;

            @Override
            protected Void doInBackground() {
                try {
                    result = checkForUpdate();
                } catch (Exception ex) {
                    error = ex;
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    if (manual) {
                        JOptionPane.showMessageDialog(parent,
                                "Unable to check for updates:\n" + rootMessage(error),
                                "SmartStock Updates",
                                JOptionPane.WARNING_MESSAGE);
                    }
                    return;
                }
                if (result == null || result.release() == null) {
                    if (manual) {
                        JOptionPane.showMessageDialog(parent,
                                "SmartStock is up to date.\nCurrent version: " + DeviceUtils.getAppVersion(),
                                "SmartStock Updates",
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                    return;
                }
                AppUpdatePrompt.show(parent, result.release(), result.required());
            }
        };
        worker.execute();
    }

    static boolean hasSupabaseUpdateSession() {
        String accessToken = managers.SessionManager.getCurrentAccessToken();
        SupabaseSessionManager.PersistedSession persisted = SupabaseSessionManager.loadPersistedSession();
        return hasMatchingSupabaseUpdateSession(accessToken, persisted,
                managers.SessionManager.getCurrentUserId(), managers.SessionManager.getCurrentLocationId());
    }

    static boolean hasMatchingSupabaseUpdateSession(String accessToken,
                                                     SupabaseSessionManager.PersistedSession persisted,
                                                     Integer userId, Integer locationId) {
        if (accessToken != null && !accessToken.isBlank()) return true;
        return persisted != null && persisted.userId().equals(userId)
                && persisted.locationId().equals(locationId);
    }

    public static UpdateCheckResult checkForUpdate() throws IOException, InterruptedException {
        AppRelease latest = fetchLatestRelease();
        if (latest == null) {
            return new UpdateCheckResult(null, false);
        }
        String currentVersion = DeviceUtils.getAppVersion();
        if (compareVersions(latest.version(), currentVersion) <= 0) {
            return new UpdateCheckResult(null, false);
        }
        boolean required = latest.required();
        if (!isBlank(latest.minimumSupportedVersion())
                && compareVersions(currentVersion, latest.minimumSupportedVersion()) < 0) {
            required = true;
        }
        return new UpdateCheckResult(latest, required);
    }

    public static void downloadAndStageAsync(Component parent, AppRelease release) {
        JDialog progress = createProgressDialog(parent, "Downloading SmartStock " + release.version() + "...");
        SwingWorker<Path, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected Path doInBackground() {
                try {
                    return downloadAndStage(release);
                } catch (Exception ex) {
                    error = ex;
                    return null;
                }
            }

            @Override
            protected void done() {
                progress.dispose();
                if (error != null) {
                    JOptionPane.showMessageDialog(parent,
                            "The update could not be prepared:\n" + rootMessage(error),
                            "SmartStock Updates",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                try {
                    Path manifest = get();
                    int choice = JOptionPane.showConfirmDialog(parent,
                            "SmartStock " + release.version() + " is ready to install.\n"
                                    + "The app will close, install the update, and reopen.",
                            "Install Update",
                            JOptionPane.OK_CANCEL_OPTION,
                            JOptionPane.INFORMATION_MESSAGE);
                    if (choice == JOptionPane.OK_OPTION) {
                        launchUpdaterAndExit(manifest);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent,
                            "The updater could not be started:\n" + rootMessage(ex),
                            "SmartStock Updates",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
        progress.setVisible(true);
    }

    private static AppRelease fetchLatestRelease() throws IOException, InterruptedException {
        String platform = detectPlatform();
        try {
            return LanApiClient.loadLatestAppRelease(platform);
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("The SmartStock server could not check for updates.", ex);
        }
    }

    private static Path downloadAndStage(AppRelease release) throws IOException, InterruptedException {
        Path appJar = currentAppJar();
        Path appDir = appJar.getParent();
        Path appBundle = findContainingMacAppBundle(appJar);
        if (appBundle != null) {
            validateMacUpdateLocation(appBundle);
        }
        cleanupStaleStagingDirectories(UPDATE_ROOT);
        Path stagingDir = UPDATE_ROOT.resolve("staged-" + release.version() + "-" + UUID.randomUUID());
        Files.createDirectories(stagingDir);

        Path zipPath = stagingDir.resolve("release.zip");
        String signedUrl = createSignedDownloadUrl(release.artifactBucket(), release.artifactPath());
        downloadFile(signedUrl, zipPath);
        String actualSha256 = sha256(zipPath);
        if (!actualSha256.equalsIgnoreCase(release.sha256())) {
            throw new IOException("Checksum mismatch. Expected " + release.sha256() + " but downloaded " + actualSha256 + ".");
        }

        Path updaterRunner = stagingDir.resolve("smartstock-updater-runner.jar");
        Files.copy(appJar, updaterRunner, StandardCopyOption.REPLACE_EXISTING);

        Properties manifest = new Properties();
        manifest.setProperty("version", release.version());
        manifest.setProperty("platform", release.platform());
        manifest.setProperty("install.layout", appBundle == null ? "jar-dir" : "mac-app");
        manifest.setProperty("app.dir", appDir.toString());
        if (detectPlatform().equals("windows") && appDir.getParent() != null) {
            manifest.setProperty("app.launcher.path",
                    appDir.getParent().resolve("SmartStock.exe").toString());
        }
        if (appBundle != null) {
            manifest.setProperty("app.bundle.path", appBundle.toString());
        }
        manifest.setProperty("current.jar", appJar.getFileName().toString());
        manifest.setProperty("release.zip", zipPath.toString());
        manifest.setProperty("backup.dir", rollbackDirectory().toString());
        manifest.setProperty("java.bin", javaBinary().toString());
        manifest.setProperty("sync.service.app.dir", Path.of(System.getProperty("user.home"), ".smartstock", "sync-service", "app").toString());
        manifest.setProperty("sync.service.task.name", "SmartStockServerService");
        manifest.setProperty("sync.service.launch.agent.label", "com.smartstock.sync");
        manifest.setProperty("relaunch", "true");
        Path manifestPath = stagingDir.resolve("update.properties");
        try (var output = Files.newOutputStream(manifestPath)) {
            manifest.store(output, "SmartStock staged update");
        }
        return manifestPath;
    }

    static Path rollbackDirectory() {
        return ROLLBACK_DIR;
    }

    static void cleanupStaleStagingDirectories(Path updateRoot) throws IOException {
        if (updateRoot == null || !Files.isDirectory(updateRoot)) return;
        try (var entries = Files.list(updateRoot)) {
            for (Path entry : entries.toList()) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry) && name.startsWith("staged-")) {
                    deleteRecursively(entry);
                }
            }
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (var entries = Files.walk(path)) {
            for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static String createSignedDownloadUrl(String bucket, String artifactPath) throws IOException, InterruptedException {
        try {
            return LanApiClient.createUpdateDownloadUrl(bucket, artifactPath);
        } catch (InterruptedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("The SmartStock server could not prepare the update download.", ex);
        }
    }

    static String resolveSignedDownloadUrl(String supabaseUrl, String signedUrl) {
        if (signedUrl.startsWith("http://") || signedUrl.startsWith("https://")) {
            return signedUrl;
        }
        String path = signedUrl.startsWith("/") ? signedUrl : "/" + signedUrl;
        if (path.startsWith("/object/")) {
            path = "/storage/v1" + path;
        }
        return supabaseUrl + path;
    }

    private static void downloadFile(String url, Path target) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Download failed with HTTP " + response.statusCode() + ".");
        }
        try (InputStream input = response.body()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void launchUpdaterAndExit(Path manifestPath) throws IOException {
        Path runner = manifestPath.getParent().resolve("smartstock-updater-runner.jar");
        Path appBundle = findContainingMacAppBundle(currentAppJar());
        Path nativeUpdater = appBundle == null ? null
                : appBundle.resolve("Contents").resolve("MacOS").resolve("SmartStockUpdater");
        ProcessBuilder process;
        if (nativeUpdater != null && Files.isExecutable(nativeUpdater)) {
            process = new ProcessBuilder(nativeUpdater.toString(), manifestPath.toString());
        } else if (detectPlatform().equals("windows")) {
            Path java = windowsUpdaterJavaBinary();
            if (!Files.isExecutable(java)) {
                throw new IOException("No updater launcher or Java executable was found at " + java + ".");
            }
            process = new ProcessBuilder(buildWindowsElevatedUpdaterCommand(
                    java, runner, manifestPath));
        } else {
            Path java = javaBinary();
            if (!Files.isExecutable(java)) {
                throw new IOException("No updater launcher or Java executable was found at " + java + ".");
            }
            process = new ProcessBuilder(java.toString(), "-cp", runner.toString(),
                    "app.SmartStockUpdater", manifestPath.toString());
        }
        process.directory(manifestPath.getParent().toFile()).start();
        System.exit(0);
    }

    static List<String> buildWindowsElevatedUpdaterCommand(
            Path java, Path runner, Path manifestPath) {
        Path workingDirectory = manifestPath.toAbsolutePath().normalize().getParent();
        String command = "$p=Start-Process -FilePath '" + quotePowerShell(java.toString())
                + "' -Verb RunAs -WindowStyle Hidden -WorkingDirectory '"
                + quotePowerShell(workingDirectory.toString())
                + "' -ArgumentList @('-cp','" + quotePowerShell(runner.toString())
                + "','app.SmartStockUpdater','" + quotePowerShell(manifestPath.toString())
                + "') -PassThru; if ($null -eq $p) { exit 1 }";
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", command);
    }

    private static Path windowsUpdaterJavaBinary() {
        Path java = javaBinary();
        Path javaw = java.resolveSibling("javaw.exe");
        return Files.isExecutable(javaw) ? javaw : java;
    }

    private static String quotePowerShell(String value) {
        return value.replace("'", "''");
    }

    private static Path currentAppJar() throws IOException {
        CodeSource source = AppUpdateService.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new IOException("Unable to locate the running SmartStock jar.");
        }
        Path path;
        try {
            path = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (Exception ex) {
            throw new IOException("Unable to resolve the running SmartStock jar path.", ex);
        }
        if (!path.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IOException("In-app updates are only available from a packaged SmartStock jar.");
        }
        return path;
    }

    private static Path findContainingMacAppBundle(Path jarPath) {
        if (!detectPlatform().equals("mac")) {
            return null;
        }
        Path current = jarPath.toAbsolutePath().normalize();
        while (current != null) {
            if (current.getFileName() != null
                    && current.getFileName().toString().endsWith(".app")
                    && Files.isDirectory(current.resolve("Contents"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    static void validateMacUpdateLocation(Path appBundle) throws IOException {
        String normalized = appBundle.toAbsolutePath().normalize().toString();
        if (normalized.contains("/AppTranslocation/") || normalized.startsWith("/Volumes/")) {
            throw new IOException("SmartStock is running from a temporary location. Install SmartStock.app in Applications, open it there, then try the update again.");
        }
        Path parent = appBundle.getParent();
        if (parent == null || !Files.isWritable(parent)) {
            throw new IOException("SmartStock cannot update its current installation. Reinstall SmartStock.app in Applications, then try again.");
        }
    }

    private static Path javaBinary() {
        String executable = isWindows() ? "java.exe" : "java";
        Path bundled = Path.of(System.getProperty("java.home"), "bin", executable);
        if (Files.isExecutable(bundled)) return bundled;
        String javaHome = System.getenv("JAVA_HOME");
        if (!isBlank(javaHome)) {
            Path configured = Path.of(javaHome, "bin", executable);
            if (Files.isExecutable(configured)) return configured;
        }
        String path = System.getenv("PATH");
        if (!isBlank(path)) {
            for (String directory : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
                if (isBlank(directory)) continue;
                Path candidate = Path.of(directory, executable);
                if (Files.isExecutable(candidate)) return candidate;
            }
        }
        if (detectPlatform().equals("mac")) {
            Path systemJava = Path.of("/usr/bin/java");
            if (Files.isExecutable(systemJava)) return systemJava;
        }
        return bundled;
    }

    static int compareVersions(String left, String right) {
        List<Integer> leftParts = versionParts(left);
        List<Integer> rightParts = versionParts(right);
        int max = Math.max(leftParts.size(), rightParts.size());
        for (int i = 0; i < max; i++) {
            int l = i < leftParts.size() ? leftParts.get(i) : 0;
            int r = i < rightParts.size() ? rightParts.get(i) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static List<Integer> versionParts(String value) {
        List<Integer> parts = new ArrayList<>();
        if (value == null) {
            return parts;
        }
        Matcher matcher = Pattern.compile("\\d+").matcher(value);
        while (matcher.find()) {
            parts.add(Integer.parseInt(matcher.group()));
        }
        return parts;
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IOException("Unable to calculate update checksum.", ex);
        }
    }

    private static JDialog createProgressDialog(Component parent, String message) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "SmartStock Updates", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(new JLabel(message));
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(12));
        panel.add(progressBar);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }

    private static String detectPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac")) {
            return "mac";
        }
        return "linux";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String firstJsonObject(String jsonArray) {
        if (jsonArray == null) {
            return null;
        }
        int start = jsonArray.indexOf('{');
        int end = jsonArray.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return jsonArray.substring(start, end + 1);
    }

    private static String extractJsonString(String json, String fieldName) {
        if (json == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*(null|\\\"((?:\\\\.|[^\\\\\"])*)\\\")");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find() || matcher.group(2) == null) {
            return null;
        }
        return unescapeJson(matcher.group(2));
    }

    private static String extractJsonNumber(String json, String fieldName) {
        if (json == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*(-?\\d+)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean extractJsonBoolean(String json, String fieldName) {
        if (json == null) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(fieldName) + "\\\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String encodePath(String path) {
        String[] parts = path.split("/");
        List<String> encoded = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                encoded.add(urlEncode(part));
            }
        }
        return String.join("/", encoded);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String valueOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    public record AppRelease(
            long releaseId,
            String version,
            int buildNumber,
            String platform,
            String artifactBucket,
            String artifactPath,
            String sha256,
            long fileSizeBytes,
            String releaseNotes,
            boolean required,
            String minimumSupportedVersion
    ) {
    }

    public record UpdateCheckResult(AppRelease release, boolean required) {
    }
}
