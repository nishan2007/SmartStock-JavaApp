package services;

import app.StudioModelMigration;
import data.EnvironmentProfile;
import utils.SecureFilePermissions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

/** Installs a verified portable Windows runtime in the active server profile. */
final class StudioPdfOcrRuntime {
    private static final String VERSION = "5.5.4";
    private static final Download RUNTIME = new Download(
            "https://github.com/SubtitleEdit/support-files/releases/download/tesseract554/Tesseract554.zip",
            22_089_806, "12b62fd309df3a26d4de12b114e66dc68e01851c621933bac0e99213d62257a5");
    private static final Download ENGLISH = new Download(
            "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/65727574dfcd264acbb0c3e07860e4e9e9b22185/eng.traineddata",
            4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2");
    private static final long TOTAL = RUNTIME.size() + ENGLISH.size();
    private static final long MAX_EXPANDED_BYTES = 96L * 1024 * 1024;
    private static final Map<Path, StudioPdfOcrRuntime> STORES = new ConcurrentHashMap<>();

    private record Download(String url, long size, String sha256) { }
    private record Installed(String version, StudioPdfOcrService.Settings settings) { }
    @FunctionalInterface interface Transport { InputStream open(String url) throws Exception; }

    private final Path root;
    private final Transport transport;
    private volatile StudioModelService.Progress progress = new StudioModelService.Progress("IDLE", 0, TOTAL, "");

    StudioPdfOcrRuntime(Path root) { this(root, StudioPdfOcrRuntime::openSource); }

    StudioPdfOcrRuntime(Path root, Transport transport) {
        this.root = root.toAbsolutePath();
        this.transport = transport;
    }

    static StudioPdfOcrRuntime current() {
        return STORES.computeIfAbsent(EnvironmentProfile.active().file("ocr-runtime"), StudioPdfOcrRuntime::new);
    }

    static boolean supported() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return System.getProperty("os.name", "").startsWith("Windows")
                && (arch.equals("amd64") || arch.equals("x86_64"));
    }

    StudioPdfOcrService.Settings managedSettings() throws IOException {
        Installed installed = installed();
        return installed == null ? null : installed.settings();
    }

    private Installed installed() throws IOException {
        Path active = root.resolve("active");
        if (!Files.isRegularFile(active)) return null;
        if (Files.size(active) > 100) throw new IOException("Invalid managed OCR installation.");
        String name = Files.readString(active).strip();
        if (!name.matches("tesseract-[0-9]+\\.[0-9]+\\.[0-9]+-[0-9a-f-]{36}"))
            throw new IOException("Invalid managed OCR installation.");
        var settings = settings(root.resolve(name));
        if (!Files.isRegularFile(Path.of(settings.executable()))
                || !Files.isRegularFile(Path.of(settings.dataDirectory(), "eng.traineddata")))
            throw new IOException("Managed OCR needs repair. Open Status > AI Models in SmartStock.");
        return new Installed(name.substring("tesseract-".length(), name.indexOf('-', "tesseract-".length())), settings);
    }

    Map<String, Object> status() {
        boolean supported = supported();
        boolean damaged = false;
        Installed installed = null;
        try {
            if (supported) installed = installed();
        } catch (IOException ex) {
            damaged = true;
        }
        boolean available = installed != null;
        String action = !supported ? "Manual setup"
                : damaged ? "Repair" : !available ? "Install PDF OCR"
                : installed.version().equals(VERSION) ? "Repair" : "Update";
        return Map.of("version", "Tesseract " + VERSION + " · English", "sizeBytes", TOTAL,
                "available", available, "installedVersion", available ? installed.version() : "",
                "action", action, "enabled", supported, "progress", progress,
                "description", supported ? "" : "Automatic installation requires an x64 Windows store server.");
    }

    synchronized StudioModelService.Progress start() {
        if (progress.state().equals("CHECKING") || progress.state().equals("DOWNLOADING")) return progress;
        progress = new StudioModelService.Progress("CHECKING", 0, TOTAL, "Preparing PDF OCR installation…");
        Thread worker = new Thread(this::runInstall, "pdf-ocr-install");
        worker.setDaemon(true);
        worker.start();
        return progress;
    }

    private void runInstall() {
        try {
            install();
            progress = new StudioModelService.Progress("COMPLETE", TOTAL, TOTAL, "PDF OCR is ready to use.");
        } catch (Exception ex) {
            progress = new StudioModelService.Progress("FAILED", 0, TOTAL,
                    "PDF OCR installation failed. Check the server internet connection, disk space and permissions, then retry.");
            System.err.println("PDF OCR installation failed: " + ex.getClass().getSimpleName());
        }
    }

    void install() throws Exception {
        Files.createDirectories(root);
        SecureFilePermissions.restrictDirectoryToOwner(root);
        try (var channel = FileChannel.open(root.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another server process is installing PDF OCR.");
            // Stage on the same volume as the active pointer so both moves can be atomic.
            Path staging = Files.createTempDirectory(root, "install-");
            try {
                Path archive = staging.resolve("tesseract.zip");
                download(RUNTIME, archive, 0, "Downloading Tesseract…");
                Path unpacked = Files.createDirectory(staging.resolve("runtime"));
                extract(archive, unpacked);
                download(ENGLISH, unpacked.resolve("tessdata/eng.traineddata"), RUNTIME.size(),
                        "Downloading English language data…");
                progress = new StudioModelService.Progress("CHECKING", TOTAL, TOTAL, "Testing PDF OCR…");
                StudioPdfOcrService.checkEnglish(settings(unpacked));

                String name = "tesseract-" + VERSION + "-" + UUID.randomUUID();
                Path candidate = root.resolve(name);
                Path pointer = Files.writeString(staging.resolve("active"), name);
                Files.move(unpacked, candidate, StandardCopyOption.ATOMIC_MOVE);
                try {
                    Files.move(pointer, root.resolve("active"), StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ex) {
                    try {
                        deleteStaging(candidate);
                    } catch (IOException cleanup) {
                        ex.addSuppressed(cleanup);
                    }
                    throw ex;
                }
            } finally {
                // Activation already succeeded if the pointer moved. Cleanup failure must not turn success into failure.
                try {
                    deleteStaging(staging);
                } catch (IOException ex) {
                    System.err.println("Could not remove temporary PDF OCR installation files.");
                }
            }
        }
    }

    private static StudioPdfOcrService.Settings settings(Path directory) {
        return new StudioPdfOcrService.Settings(directory.resolve("tesseract.exe").toString(),
                directory.resolve("tessdata").toString(), "eng");
    }

    private static InputStream openSource(String url) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        var response = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(45))
                .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("PDF OCR download returned HTTP " + response.statusCode() + ".");
        }
        return response.body();
    }

    private void download(Download asset, Path destination, long previous, String message) throws Exception {
        progress = new StudioModelService.Progress("DOWNLOADING", previous, TOTAL, message);
        try (var input = transport.open(asset.url());
             var output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            var deadline = StudioModelService.closeAfter(input, Duration.ofMinutes(5));
            try {
                byte[] buffer = new byte[64 * 1024];
                long count = 0;
                for (int length; (length = input.read(buffer)) != -1;) {
                    count += length;
                    if (count > asset.size()) throw new IOException("PDF OCR download exceeded its expected size.");
                    output.write(buffer, 0, length);
                    progress = new StudioModelService.Progress("DOWNLOADING", previous + count, TOTAL, message);
                }
                if (count != asset.size()) throw new IOException("PDF OCR download was incomplete.");
            } finally {
                deadline.cancel(false);
            }
        }
        if (!asset.sha256().equals(StudioModelMigration.sha256(destination)))
            throw new IOException("PDF OCR download failed SHA-256 verification.");
    }

    static void extract(Path archive, Path directory) throws IOException {
        try (var zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory()) {
                    if (!name.equals("tessdata/") && !name.equals("tessdata/configs/"))
                        throw new IOException("Unexpected directory in the PDF OCR package.");
                    continue;
                }
                if (!name.equals("tesseract.exe") && !name.matches("[A-Za-z0-9_+-][A-Za-z0-9_.+-]*\\.dll")
                        && !name.equals("tessdata/osd.traineddata") && !name.equals("tessdata/configs/hocr"))
                    throw new IOException("Unexpected file in the PDF OCR package.");
                Path target = directory.resolve(name);
                Files.createDirectories(target.getParent());
                try (var input = zip.getInputStream(entry);
                     var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    for (int length; (length = input.read(buffer)) != -1;) {
                        total += length;
                        if (total > MAX_EXPANDED_BYTES) throw new IOException("PDF OCR package expands beyond its limit.");
                        output.write(buffer, 0, length);
                    }
                }
            }
        }
        if (!Files.isRegularFile(directory.resolve("tesseract.exe")) || !Files.isDirectory(directory.resolve("tessdata")))
            throw new IOException("PDF OCR package is incomplete.");
    }

    private static void deleteStaging(Path directory) throws IOException {
        java.util.List<Path> files;
        try (var paths = Files.walk(directory)) {
            files = paths.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : files) Files.deleteIfExists(path);
    }
}
