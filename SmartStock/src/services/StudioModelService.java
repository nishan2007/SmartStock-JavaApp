package services;

import app.StudioModelMigration;
import com.google.gson.Gson;
import data.EnvironmentProfile;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Server-owned model storage. App replacement and rollback never touch this root. */
public final class StudioModelService {
    private static final Gson JSON = new Gson();
    private static final Map<Path, StudioModelService> STORES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ai-model-download-deadline"); t.setDaemon(true); return t;
    });
    private final Path root;
    private final Transport transport;
    private final Map<StudioQuality, Progress> jobs = new EnumMap<>(StudioQuality.class);
    private final Map<Path, Verified> verified = new ConcurrentHashMap<>();
    private volatile StudioModelCatalogue catalogue;
    private final Object catalogueLock = new Object();
    private boolean migrated;
    private final Set<StudioQuality> legacyDamaged = EnumSet.noneOf(StudioQuality.class);

    @FunctionalInterface interface Transport { InputStream open(String objectKey) throws Exception; }
    private record Verified(long size, java.nio.file.attribute.FileTime modified, Object key, String hash) { }
    public record Progress(String state, long downloadedBytes, long totalBytes, String message) { }

    StudioModelService(Path root, Transport transport) throws IOException {
        this.root = root;
        this.transport = transport;
        catalogue = StudioModelCatalogue.bundled();
        if (Files.isRegularFile(root.resolve("catalogue.json"))) {
            try { catalogue = StudioModelCatalogue.parse(Files.readString(root.resolve("catalogue.json"))); }
            catch (IOException ignored) { /* Keep the supported built-in catalogue available for repair. */ }
        }
    }

    public static Path modelRoot() { return EnvironmentProfile.active().file("ai-models"); }

    public static StudioModelService current() throws IOException {
        Path root = modelRoot();
        synchronized (STORES) {
            StudioModelService store = STORES.get(root);
            if (store == null) {
                store = new StudioModelService(root, StudioModelService::openDeckersObject);
                STORES.put(root, store);
            }
            return store;
        }
    }

    private static InputStream openDeckersObject(String key) throws Exception {
        String signed = R2UpdateUrlSigner.createDownloadUrl(R2UpdateUrlSigner.R2_BUCKET_REFERENCE, key);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<InputStream> response = client.send(HttpRequest.newBuilder(URI.create(signed))
                .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("Deckers model download returned HTTP " + response.statusCode() + ".");
        }
        return response.body();
    }

    public synchronized void migrateLegacy() throws Exception {
        if (migrated) return;
        Path jar = Path.of(CatalogStudioImageProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        // Runtime repair must remain possible even when an old bundled model is corrupt.
        // The updater uses the strict migration path and aborts before changing app files.
        for (StudioQuality quality : StudioQuality.values()) {
            StudioModelCatalogue.Entry baseline = StudioModelCatalogue.bundled().entry(quality);
            Path source = jar.getParent().resolve("dependency/catalog-studio/" +
                    (quality == StudioQuality.FAST ? "isnet-general-use.onnx" : "birefnet-general.onnx"));
            try { StudioModelMigration.preserve(source, root, baseline.sha256()); }
            catch (IOException e) { legacyDamaged.add(quality); }
        }
        migrated = true;
    }

    public void refreshCatalogue() throws Exception {
        synchronized (catalogueLock) {
            try (InputStream input = transport.open(StudioModelCatalogue.OBJECT_KEY)) {
                ScheduledFuture<?> deadline = closeAfter(input, Duration.ofSeconds(30));
                try {
                    byte[] bytes = input.readNBytes(65537);
                    if (bytes.length > 65536) throw new IOException("The AI model catalogue is too large.");
                    StudioModelCatalogue next = StudioModelCatalogue.parse(new String(bytes, StandardCharsets.UTF_8));
                    saveAtomic(root.resolve("catalogue.json"), next.json());
                    catalogue = next;
                } finally { deadline.cancel(false); }
            }
        }
    }

    private static ScheduledFuture<?> closeAfter(InputStream input, Duration duration) {
        return DEADLINES.schedule(() -> { try { input.close(); } catch (IOException ignored) { } },
                duration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public StudioModelCatalogue.Entry installed(StudioQuality quality) throws IOException {
        Path active = activeFile(quality);
        if (Files.isRegularFile(active)) {
            try {
                StudioModelCatalogue.Entry entry = JSON.fromJson(Files.readString(active), StudioModelCatalogue.Entry.class);
                if (entry == null || entry.quality() != quality) throw new IOException("Invalid installed AI model.");
                entry.validate();
                return entry;
            } catch (RuntimeException e) { throw new IOException("Invalid installed AI model.", e); }
        }
        StudioModelCatalogue.Entry baseline = StudioModelCatalogue.bundled().entry(quality);
        return Files.isRegularFile(file(baseline)) ? baseline : null;
    }

    public Path resolve(StudioQuality quality) throws Exception {
        migrateLegacy();
        StudioModelCatalogue.Entry entry;
        try { entry = installed(quality); }
        catch (IOException e) { throw unavailable("The installed AI model needs repair."); }
        if (entry == null) throw unavailable(quality + " is not installed on the store server.");
        try { verify(entry); }
        catch (IOException e) { throw unavailable("The installed AI model failed its integrity check."); }
        return file(entry);
    }

    public String installedHash(StudioQuality quality) throws IOException {
        StudioModelCatalogue.Entry entry = installed(quality);
        if (entry == null) throw new IOException("AI model is not installed.");
        return entry.sha256();
    }

    public boolean available(StudioQuality quality) {
        try { resolve(quality); return true; } catch (Exception e) { return false; }
    }

    private static CatalogStudioImageProcessor.ModelUnavailableException unavailable(String text) {
        return new CatalogStudioImageProcessor.ModelUnavailableException(text + " Ask an administrator to open Status > AI Models in SmartStock.");
    }

    private Path file(StudioModelCatalogue.Entry entry) { return root.resolve(entry.sha256() + ".onnx"); }
    private Path activeFile(StudioQuality quality) { return root.resolve(quality.name().toLowerCase(Locale.ROOT) + "-active.json"); }

    private void verify(StudioModelCatalogue.Entry entry) throws IOException {
        entry.validate();
        Path path = file(entry);
        BasicFileAttributes a = Files.readAttributes(path, BasicFileAttributes.class);
        Verified old = verified.get(path);
        if (a.size() != entry.sizeBytes()) throw new IOException("AI model size check failed.");
        if (old != null && old.size == a.size() && old.modified.equals(a.lastModifiedTime())
                && Objects.equals(old.key, a.fileKey()) && old.hash.equals(entry.sha256())) return;
        StudioModelMigration.verify(path, entry.sha256());
        verified.put(path, new Verified(a.size(), a.lastModifiedTime(), a.fileKey(), entry.sha256()));
    }

    public List<Map<String, Object>> status() throws Exception {
        migrateLegacy();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StudioQuality quality : StudioQuality.values()) {
            StudioModelCatalogue.Entry desired = catalogue.entry(quality);
            StudioModelCatalogue.Entry installed = null;
            boolean valid = false, damaged = Files.exists(activeFile(quality)) || legacyDamaged.contains(quality);
            try { installed = installed(quality); if (installed != null) { damaged = true; verify(installed); valid = true; } }
            catch (IOException ignored) { }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("quality", quality.name()); row.put("version", desired.version());
            row.put("installedVersion", installed == null ? "" : installed.version());
            row.put("sizeBytes", desired.sizeBytes()); row.put("available", valid);
            row.put("action", !valid ? (damaged ? "Repair" : "Install")
                    : installed.sha256().equals(desired.sha256()) ? "Installed" : "Update");
            synchronized (this) { row.put("progress", jobs.getOrDefault(quality, new Progress("IDLE", 0, desired.sizeBytes(), ""))); }
            rows.add(row);
        }
        return rows;
    }

    public synchronized Progress start(StudioQuality quality) {
        Progress old = jobs.get(quality);
        if (old != null && (old.state.equals("DOWNLOADING") || old.state.equals("CHECKING"))) return old;
        Progress pending = new Progress("CHECKING", 0, catalogue.entry(quality).sizeBytes(), "Checking Deckers model catalogue…");
        jobs.put(quality, pending);
        Thread worker = new Thread(() -> runInstall(quality), "ai-model-" + quality.name().toLowerCase(Locale.ROOT));
        worker.setDaemon(true); worker.start();
        return pending;
    }

    private void progress(StudioQuality q, String state, long bytes, long total, String message) {
        synchronized (this) { jobs.put(q, new Progress(state, bytes, total, message)); }
    }

    private void runInstall(StudioQuality quality) {
        long total = catalogue.entry(quality).sizeBytes();
        try {
            Files.createDirectories(root);
            try (FileChannel lockChannel = FileChannel.open(root.resolve(quality.name() + ".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = lockChannel.tryLock()) {
                if (lock == null) throw new IOException("Another server process is installing this model. Retry after it finishes.");
                migrateLegacy();
                refreshCatalogue();
                StudioModelCatalogue.Entry entry = catalogue.entry(quality);
                total = entry.sizeBytes();
                install(entry);
                progress(quality, "COMPLETE", total, total, "Model ready to use.");
            }
        } catch (Exception e) {
            // Do not expose signed URLs or secrets from transport exceptions to clients.
            progress(quality, "FAILED", 0, total,
                    "Model installation failed. Check the server download configuration, connection, free space and file permissions, then retry. The previous model was retained.");
        }
    }

    void install(StudioModelCatalogue.Entry entry) throws Exception {
        entry.validate();
        Files.createDirectories(root);
        try { verify(entry); saveAtomic(activeFile(entry.quality()), JSON.toJson(entry)); return; }
        catch (IOException missingOrCorrupt) { /* Only fetch a missing or damaged model. */ }
        Path partial = Files.createTempFile(root, "download-", ".partial");
        long count = 0;
        progress(entry.quality(), "DOWNLOADING", 0, entry.sizeBytes(), "Downloading from Deckers…");
        try {
            try (InputStream input = transport.open(entry.objectKey()); OutputStream output = Files.newOutputStream(partial)) {
                ScheduledFuture<?> deadline = closeAfter(input, Duration.ofMinutes(45));
                try {
                    byte[] buffer = new byte[65536];
                    for (int n; (n = input.read(buffer)) != -1;) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Model download interrupted. Retry to continue.");
                        count += n;
                        if (count > entry.sizeBytes()) throw new IOException("AI model size check failed.");
                        output.write(buffer, 0, n);
                        progress(entry.quality(), "DOWNLOADING", count, entry.sizeBytes(), "Downloading from Deckers…");
                    }
                } finally { deadline.cancel(false); }
            }
            if (count != entry.sizeBytes()) throw new IOException("AI model download was incomplete. Retry to repair.");
            StudioModelMigration.verify(partial, entry.sha256());
            // Immutable filenames let current inference sessions finish with their original model.
            Files.move(partial, file(entry), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            verified.remove(file(entry));
            saveAtomic(activeFile(entry.quality()), JSON.toJson(entry));
        } finally { Files.deleteIfExists(partial); }
    }

    private static void saveAtomic(Path target, String value) throws IOException {
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), "metadata-", ".partial");
        try {
            Files.writeString(staged, value, StandardCharsets.UTF_8);
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(staged); }
    }

    public static boolean administrator(String role) { return "ADMIN".equalsIgnoreCase(role); }
}
