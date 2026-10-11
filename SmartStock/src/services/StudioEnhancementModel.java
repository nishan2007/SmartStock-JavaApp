package services;

import app.StudioModelMigration;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Pinned, administrator-installed enhancement weights outside app and rollback directories. */
public final class StudioEnhancementModel {
    public static final String SHA256 = "4851ec156207d271f5328605d0582eeb851e656227da8aca093ced9e60789291";
    public static final long SIZE = 67_051_650;
    public static final String URL = "https://huggingface.co/skillsafe-ai/realesrgan-x4plus/resolve/107475e49b7d46412cc966481efccc3500955801/model.onnx";
    private static final Map<Path, StudioEnhancementModel> STORES = new ConcurrentHashMap<>();
    private final Path root;
    @FunctionalInterface interface Transport { InputStream open() throws Exception; }
    private final Transport transport;
    private volatile java.nio.file.attribute.BasicFileAttributes verified;
    private volatile StudioModelService.Progress progress = new StudioModelService.Progress("IDLE", 0, SIZE, "");

    StudioEnhancementModel(Path root) { this(root, StudioEnhancementModel::openSource); }
    StudioEnhancementModel(Path root, Transport transport) { this.root = root; this.transport = transport; }
    public static StudioEnhancementModel current() {
        return STORES.computeIfAbsent(StudioModelService.modelRoot().resolve("enhancement"), StudioEnhancementModel::new);
    }
    private Path file() { return root.resolve(SHA256 + ".onnx"); }
    public Path resolve() throws Exception {
        try {
            var attributes = Files.readAttributes(file(), java.nio.file.attribute.BasicFileAttributes.class);
            var old = verified;
            if (old == null || old.size() != attributes.size() || !old.lastModifiedTime().equals(attributes.lastModifiedTime())
                    || !java.util.Objects.equals(old.fileKey(), attributes.fileKey())) {
                verify(file()); verified = attributes;
            }
            return file();
        }
        catch (IOException e) {
            throw new CatalogStudioImageProcessor.ModelUnavailableException(
                    "The enhancement model is missing or needs repair. Ask an administrator to open Status > AI Models.");
        }
    }
    static void verify(Path file) throws IOException {
        if (Files.size(file) != SIZE) throw new IOException("Enhancement model size check failed.");
        StudioModelMigration.verify(file, SHA256);
    }
    public boolean available() { try { resolve(); return true; } catch (Exception e) { return false; } }
    public Map<String, Object> status() {
        boolean available = available();
        return Map.of("version", "Real-ESRGAN x4plus", "installedVersion", available ? "Real-ESRGAN x4plus" : "",
                "sizeBytes", SIZE, "available", available, "progress", progress,
                "action", available ? "Installed" : Files.exists(file()) ? "Repair" : "Install");
    }
    public synchronized StudioModelService.Progress start() {
        if (progress.state().equals("DOWNLOADING") || progress.state().equals("CHECKING")) return progress;
        progress = new StudioModelService.Progress("CHECKING", 0, SIZE, "Checking enhancement model…");
        Thread worker = new Thread(this::download, "ai-enhancement-download"); worker.setDaemon(true); worker.start();
        return progress;
    }
    private static InputStream openSource() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofMinutes(10)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) { response.body().close(); throw new IOException("Model download unavailable."); }
        return response.body();
    }
    private void download() {
        Path partial = null;
        StudioModelService.Progress outcome = null;
        try {
            Files.createDirectories(root);
            try (FileChannel channel = FileChannel.open(root.resolve("install.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("Another process is installing the enhancement model.");
                if (!available()) {
                    partial = Files.createTempFile(root, "enhancement-", ".partial");
                    try (InputStream input = transport.open(); OutputStream output = Files.newOutputStream(partial)) {
                        var deadline = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                            Thread t = new Thread(r, "enhancement-download-deadline"); t.setDaemon(true); return t;
                        });
                        deadline.schedule(() -> { try { input.close(); } catch (IOException ignored) { } }, 10, java.util.concurrent.TimeUnit.MINUTES);
                        try {
                            byte[] buffer = new byte[65536]; long count = 0;
                            for (int n; (n = input.read(buffer)) != -1;) {
                                count += n; if (count > SIZE) throw new IOException("Model download too large.");
                                output.write(buffer, 0, n);
                                progress = new StudioModelService.Progress("DOWNLOADING", count, SIZE, "Downloading enhancement model…");
                            }
                        } finally { deadline.shutdownNow(); }
                    }
                    verify(partial);
                    Files.move(partial, file(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    verified = null;
                }
                outcome = new StudioModelService.Progress("COMPLETE", SIZE, SIZE, "Enhancement model ready.");
            }
        } catch (Exception e) {
            outcome = new StudioModelService.Progress("FAILED", 0, SIZE, "Installation failed. Check the server internet connection and retry.");
        } finally {
            if (partial != null) try { Files.deleteIfExists(partial); } catch (IOException ignored) { }
            if (outcome != null) progress = outcome;
        }
    }
}
