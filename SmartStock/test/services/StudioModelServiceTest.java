package services;

import app.StudioModelMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class StudioModelServiceTest {
    @TempDir Path root;

    private StudioModelCatalogue.Entry entry(StudioQuality quality, String body) throws Exception {
        Path fixture = root.resolve(UUID.randomUUID() + ".fixture"); Files.writeString(fixture, body);
        String hash = StudioModelMigration.sha256(fixture); Files.delete(fixture);
        return new StudioModelCatalogue.Entry(quality, "fixture-1", "models/" + quality.name().toLowerCase(Locale.ROOT)
                + "/" + hash + ".onnx", body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, hash,
                StudioModelCatalogue.supportedCompatibility(quality));
    }

    @Test void modelsInstallIndependentlyAndReuseVerifiedFiles() throws Exception {
        var fast = entry(StudioQuality.FAST, "fast model"); var best = entry(StudioQuality.BEST, "best model");
        AtomicInteger requests = new AtomicInteger();
        var store = new StudioModelService(root, key -> {
            requests.incrementAndGet(); return new ByteArrayInputStream((key.equals(fast.objectKey()) ? "fast model" : "best model").getBytes());
        });
        store.install(fast);
        assertEquals(fast, store.installed(StudioQuality.FAST)); assertNull(store.installed(StudioQuality.BEST));
        assertTrue(store.available(StudioQuality.FAST));
        store.install(fast); assertEquals(1, requests.get());
        store.install(best); assertEquals(2, requests.get());
        assertTrue(store.available(StudioQuality.FAST)); assertTrue(store.available(StudioQuality.BEST));
        var restarted = new StudioModelService(root, key -> { throw new AssertionError("Offline reuse must not download"); });
        assertEquals(store.resolve(StudioQuality.FAST), restarted.resolve(StudioQuality.FAST));
        assertEquals(store.resolve(StudioQuality.BEST), restarted.resolve(StudioQuality.BEST));
    }

    @Test void failedUpdatesRetainActiveVersionAndClearPartialFiles() throws Exception {
        var first = entry(StudioQuality.FAST, "old model");
        var second = entry(StudioQuality.FAST, "new model");
        var store = new StudioModelService(root, key -> new ByteArrayInputStream("old model".getBytes()));
        store.install(first);
        // Equal-sized corrupt content must fail SHA-256, not just byte-size validation.
        assertThrows(IOException.class, () -> store.install(second));
        assertEquals(first, store.installed(StudioQuality.FAST)); assertTrue(store.available(StudioQuality.FAST));
        assertEquals("old model", Files.readString(store.resolve(StudioQuality.FAST)));
        try (var files = Files.list(root)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".partial"))); }
    }

    @Test void interruptedAndTruncatedDownloadsDoNotActivate() throws Exception {
        var fast = entry(StudioQuality.FAST, "fast model");
        var truncated = new StudioModelService(root, key -> new ByteArrayInputStream(new byte[]{1}));
        assertThrows(IOException.class, () -> truncated.install(fast)); assertNull(truncated.installed(StudioQuality.FAST));
        var interrupted = new StudioModelService(root, key -> new InputStream() {
            @Override public int read() throws IOException { throw new InterruptedIOException("Disconnected"); }
        });
        assertThrows(IOException.class, () -> interrupted.install(fast)); assertNull(interrupted.installed(StudioQuality.FAST));
        try (var files = Files.list(root)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".partial"))); }
    }

    @Test void corruptInstalledModelCanBeRepaired() throws Exception {
        var fast = entry(StudioQuality.FAST, "fast model");
        var store = new StudioModelService(root, key -> new ByteArrayInputStream("fast model".getBytes()));
        store.install(fast); Files.writeString(store.resolve(StudioQuality.FAST), "corrupt");
        assertFalse(store.available(StudioQuality.FAST));
        assertEquals("Repair", store.status().get(0).get("action"));
        store.install(fast); assertTrue(store.available(StudioQuality.FAST));
    }

    @Test void compatibleUpdateRetainsPreviousFile() throws Exception {
        var first = entry(StudioQuality.FAST, "old model"); var next = entry(StudioQuality.FAST, "new model");
        var store = new StudioModelService(root, key -> new ByteArrayInputStream((key.equals(first.objectKey()) ? "old model" : "new model").getBytes()));
        store.install(first); Path previous = store.resolve(StudioQuality.FAST);
        store.install(next); assertEquals(next, store.installed(StudioQuality.FAST));
        assertEquals("old model", Files.readString(previous));
    }

    @Test void incompatibleAndUnsafeCataloguesCannotReplaceSupportedCatalogue() throws Exception {
        String valid = StudioModelCatalogue.bundled().json();
        assertThrows(IOException.class, () -> StudioModelCatalogue.parse(valid.replace("isnet-1024-v1", "unknown-v2")));
        assertThrows(IOException.class, () -> StudioModelCatalogue.parse(valid.replace("models/fast/", "../fast/")));
        assertThrows(IOException.class, () -> StudioModelCatalogue.parse(valid.replace("\"BEST\"", "\"FAST\"")));
        assertThrows(IOException.class, () -> StudioModelCatalogue.parse(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2")));
        var store = new StudioModelService(root, key -> new ByteArrayInputStream(valid.replace("isnet-1024-v1", "unknown-v2").getBytes()));
        assertThrows(IOException.class, store::refreshCatalogue); assertFalse(Files.exists(root.resolve("catalogue.json")));
    }

    @Test void duplicateRequestsShareJobAndCrossProcessLockPreventsReplacement() throws Exception {
        var fast = entry(StudioQuality.FAST, "fast model"); var best = entry(StudioQuality.BEST, "best model");
        var catalogue = new StudioModelCatalogue(1, List.of(fast, best));
        CountDownLatch downloading = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger requests = new AtomicInteger();
        var store = new StudioModelService(root, key -> {
            if (key.equals(StudioModelCatalogue.OBJECT_KEY)) return new ByteArrayInputStream(catalogue.json().getBytes());
            requests.incrementAndGet(); downloading.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS)); return new ByteArrayInputStream("fast model".getBytes());
        });
        store.start(StudioQuality.FAST); assertTrue(downloading.await(5, TimeUnit.SECONDS));
        assertEquals("DOWNLOADING", store.start(StudioQuality.FAST).state()); release.countDown();
        awaitJob(store, "COMPLETE"); assertEquals(1, requests.get());
        var other = new StudioModelService(root, key -> { throw new AssertionError("A locked model must not be downloaded"); });
        try (var channel = FileChannel.open(root.resolve("FAST.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            other.start(StudioQuality.FAST); awaitJob(other, "FAILED");
        }
        assertEquals(fast, other.installed(StudioQuality.FAST));
    }

    private void awaitJob(StudioModelService store, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Object job = store.status().get(0).get("progress");
            if (((StudioModelService.Progress) job).state().equals(expected)) return;
            Thread.sleep(10);
        }
        fail("Model job did not reach " + expected);
    }

    @Test void slowCatalogueDownloadDoesNotBlockProgressPolling() throws Exception {
        CountDownLatch checking = new CountDownLatch(1), release = new CountDownLatch(1);
        String catalogue = StudioModelCatalogue.bundled().json();
        var store = new StudioModelService(root, key -> {
            checking.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS));
            return new ByteArrayInputStream(catalogue.getBytes());
        });
        store.start(StudioQuality.FAST); assertTrue(checking.await(5, TimeUnit.SECONDS));
        try {
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () ->
                    assertEquals("CHECKING", ((StudioModelService.Progress) store.status().get(0).get("progress")).state()));
        } finally { release.countDown(); }
        // The fixture supplies catalogue bytes instead of model bytes; the job must fail safely.
        awaitJob(store, "FAILED");
    }

    @Test void administratorRoleIsRequiredAndProfilesAreIsolated() {
        assertTrue(StudioModelService.administrator("ADMIN"));
        assertFalse(StudioModelService.administrator("MANAGER")); assertFalse(StudioModelService.administrator(null));
        String previous = System.getProperty("smartstock.environment");
        try {
            System.setProperty("smartstock.environment", "development"); Path development = StudioModelService.modelRoot();
            System.setProperty("smartstock.environment", "production"); Path production = StudioModelService.modelRoot();
            assertNotEquals(development, production);
            assertTrue(production.endsWith(Path.of("profiles", "production", "ai-models")));
            assertFalse(production.toString().contains("rollback"));
        } finally {
            if (previous == null) System.clearProperty("smartstock.environment"); else System.setProperty("smartstock.environment", previous);
        }
    }
}
