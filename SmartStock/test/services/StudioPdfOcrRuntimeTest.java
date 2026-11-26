package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StudioPdfOcrRuntimeTest {
    @TempDir Path profile;

    @Test
    void refusesAnArchiveEntryOutsideTheRuntime() throws Exception {
        Path archive = profile.resolve("runtime.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../unexpected.exe"));
            zip.write(0);
        }
        Path target = Files.createDirectory(profile.resolve("target"));

        assertThrows(IOException.class, () -> StudioPdfOcrRuntime.extract(archive, target));
        assertFalse(Files.exists(profile.resolve("unexpected.exe")));
    }

    @Test
    void rejectedDownloadPreservesThePreviousActiveRuntime() throws Exception {
        String old = "tesseract-5.5.3-00000000-0000-0000-0000-000000000001";
        Path previous = Files.createDirectories(profile.resolve(old).resolve("tessdata")).getParent();
        Files.createFile(previous.resolve("tesseract.exe"));
        Files.createFile(previous.resolve("tessdata/eng.traineddata"));
        Files.writeString(profile.resolve("active"), old);
        var runtime = new StudioPdfOcrRuntime(profile, url -> new ByteArrayInputStream(new byte[22_089_806]));

        IOException failure = assertThrows(IOException.class, runtime::install);
        assertTrue(failure.getMessage().contains("SHA-256"));
        assertEquals(old, Files.readString(profile.resolve("active")));
        assertEquals(previous.resolve("tesseract.exe").toString(), runtime.managedSettings().executable());
        try (var children = Files.list(profile)) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().startsWith("install-")
                    && Files.isDirectory(path)));
        }

        Path config = profile.resolve("studio-ocr.properties");
        Files.writeString(config, "language=eng+spa\nexecutable=tesseract\n");
        var configured = StudioPdfOcrService.Settings.load(config, runtime);
        assertEquals("tesseract", configured.executable());
        assertEquals("eng+spa", configured.language());
    }

    @Test
    void installsPinnedWindowsDownloadsAndRecognizesEnglish() throws Exception {
        assumeTrue(StudioPdfOcrRuntime.supported() && Boolean.getBoolean("smartstock.test.ocrManagedDownload"));
        var runtime = new StudioPdfOcrRuntime(profile);
        assertNull(runtime.managedSettings());

        runtime.install();

        var configured = StudioPdfOcrService.Settings.load(profile.resolve("no-overrides.properties"), runtime);
        assertEquals(runtime.managedSettings(), configured);
        assertEquals("5.5.4", runtime.status().get("installedVersion"));
        StudioPdfOcrService.checkEnglish(configured);
    }
}
