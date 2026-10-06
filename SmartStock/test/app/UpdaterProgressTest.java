package app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class UpdaterProgressTest {
    @TempDir Path temporary;
    @AfterEach void reset() { UpdaterProgress.listen((s, f, p) -> {}); }
    @Test void percentagesAreBoundedAndUnknownTotalsAreAnimated() {
        assertEquals(-1, UpdaterProgress.percentage(0, 0));
        assertEquals(-1, UpdaterProgress.percentage(20, -1));
        assertEquals(50, UpdaterProgress.percentage(5, 10));
        assertEquals(100, UpdaterProgress.percentage(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(100, UpdaterProgress.percentage(20, 10));
    }
    @Test void copyReportsMeasuredProgressAndPreservesBytes() throws Exception {
        List<Integer> values = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        UpdaterProgress.listen((s, f, p) -> { values.add(p); steps.add(s); });
        UpdaterProgress.stage("Installing update");
        byte[] bytes = new byte[300_000];
        new Random(42).nextBytes(bytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        UpdaterProgress.copy(new ByteArrayInputStream(bytes), output, bytes.length, "app.jar");
        assertArrayEquals(bytes, output.toByteArray());
        assertEquals(-1, values.get(0));
        assertEquals(0, values.get(1));
        assertEquals(100, values.get(values.size() - 1));
        assertTrue(values.stream().anyMatch(p -> p > 0 && p < 100));
        assertTrue(steps.stream().allMatch("Installing update"::equals));
        UpdaterProgress.stage("Starting background service");
        assertEquals(-1, values.get(values.size() - 1));
    }
    @Test void unknownLengthAndFailedCopyNeverClaimCompletion() throws Exception {
        List<Integer> values = new ArrayList<>();
        UpdaterProgress.listen((s, f, p) -> values.add(p));
        UpdaterProgress.copy(new ByteArrayInputStream(new byte[10]), new ByteArrayOutputStream(), -1, "file");
        assertTrue(values.stream().allMatch(p -> p == -1));
        values.clear();
        assertThrows(IOException.class, () -> UpdaterProgress.copy(new ByteArrayInputStream(new byte[10]),
                new OutputStream() { public void write(int b) throws IOException { throw new IOException("disk full"); } }, 10, "file"));
        assertFalse(values.contains(100));
    }
    @Test void copiesTemporaryFilesAndReportsRecoveryWithoutClaimingSuccess() throws Exception {
        Path source = temporary.resolve("source.jar");
        Path destination = temporary.resolve("target.jar");
        Files.writeString(source, "application payload");
        List<Integer> values = new ArrayList<>();
        UpdaterProgress.listen((s, f, p) -> values.add(p));
        try (var input = Files.newInputStream(source); var output = Files.newOutputStream(destination)) {
            UpdaterProgress.copy(input, output, Files.size(source), "source.jar");
        }
        assertEquals(-1, Files.mismatch(source, destination));
        assertEquals(100, values.get(values.size() - 1));
        Path log = temporary.resolve("updater.log");
        String text = UpdaterProgress.failureText("disk full", "Restoring the previous installation failed", log);
        assertTrue(text.contains("disk full"));
        assertTrue(text.contains("Restoring the previous installation failed"));
        assertTrue(text.contains(log.toString()));
        Path manifest = temporary.resolve("update.properties");
        Files.writeString(manifest, "relaunch=false\n");
        assertEquals("Automatic reopening is disabled.", SmartStockUpdater.relaunchAfterFailure(manifest));
    }
}
