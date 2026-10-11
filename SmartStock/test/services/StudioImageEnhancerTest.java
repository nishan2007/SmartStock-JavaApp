package services;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioImageEnhancerTest {
    @TempDir Path root;
    @Test void rejectsOversizedOutputsAndUnsupportedScaleBeforeAllocating() {
        assertThrows(IllegalArgumentException.class, () -> StudioImageEnhancer.validateDimensions(2000, 2000, 4));
        assertThrows(IllegalArgumentException.class, () -> StudioImageEnhancer.validateDimensions(Integer.MAX_VALUE, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> StudioImageEnhancer.validateDimensions(Integer.MAX_VALUE, Integer.MAX_VALUE, 4));
        for (int scale : new int[]{0, 1, 3, 8, -1}) assertThrows(IllegalArgumentException.class, () -> StudioImageEnhancer.validateScale(scale));
        StudioImageEnhancer.validateDimensions(32, 32, 2);
    }
    @Test void wireScaleCannotBeTruncatedOrCoerced() {
        for (String json : new String[]{"2.5", "\"2\"", "null", "true", "{}", "8"})
            assertThrows(IllegalArgumentException.class, () -> LanApiServer.strictStudioScale(JsonParser.parseString(json)));
        assertEquals(4, LanApiServer.strictStudioScale(JsonParser.parseString("4")));
    }
    @Test void alphaIsIndependentOfRgbAndEdgeColourExtendsVisiblePixels() {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xffaa2200); image.setRGB(0, 1, 0xffaa2200);
        assertEquals(128, StudioImageEnhancer.alphaAt(image, .5, 0));
        assertEquals(0, StudioImageEnhancer.alphaAt(image, 1, 1));
        assertEquals(0xffaa2200, StudioImageEnhancer.edgeColour(image, 1, 0));
        assertEquals(0, image.getRGB(1, 0));
    }
    @Test void corruptOrMissingWeightsDoNotBecomeAvailable() throws Exception {
        var store = new StudioEnhancementModel(root);
        assertFalse(store.available()); assertEquals("Install", store.status().get("action"));
        Files.writeString(root.resolve(StudioEnhancementModel.SHA256 + ".onnx"), "not a model");
        assertFalse(store.available()); assertEquals("Repair", store.status().get("action"));
        assertThrows(CatalogStudioImageProcessor.ModelUnavailableException.class, store::resolve);
    }
    @Test void actualPinnedModelRunsOfflineAtBothScalesAcrossTileBoundary() throws Exception {
        String path = System.getProperty("smartstock.test.enhancementModel");
        Assumptions.assumeTrue(path != null, "Supply the pinned enhancement model to verify CPU inference.");
        var store = new StudioEnhancementModel(root, () -> Files.newInputStream(Path.of(path)));
        store.start(); awaitDownload(store);
        assertTrue(store.available());
        assertEquals("COMPLETE", ((StudioModelService.Progress) store.status().get("progress")).state());
        var offline = new StudioEnhancementModel(root, () -> { throw new AssertionError("Installed models must not download again"); });
        offline.start(); awaitDownload(offline); assertTrue(offline.available());
        var input = new BufferedImage(104, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 104; x++)
            input.setRGB(x, y, x < 8 ? 0 : 0xff000000 | (x * 2 << 16) | (y * 6 << 8) | 90);
        long start = System.nanoTime();
        try (var processor = new StudioImageEnhancer(Path.of(path))) {
            for (int scale : new int[]{2, 4}) {
                var output = processor.enhance(input, scale);
                assertEquals(104 * scale, output.getWidth()); assertEquals(32 * scale, output.getHeight());
                assertEquals(0, output.getRGB(0, 10) >>> 24);
                assertEquals(255, output.getRGB(50 * scale, 10 * scale) >>> 24);
                int left = output.getRGB(96 * scale - 1, 16 * scale), right = output.getRGB(96 * scale, 16 * scale);
                for (int shift : new int[]{0, 8, 16}) assertTrue(Math.abs(((left >>> shift) & 255) - ((right >>> shift) & 255)) < 24,
                        "Visible seam at tile boundary");
            }
        }
        assertEquals(0, input.getRGB(0, 10));
        System.out.printf("Real-ESRGAN CPU: load + 2x/4x tiled inference %.2f seconds%n", (System.nanoTime() - start) / 1e9);
    }
    @Test void interruptedDownloadIsNotActivatedAndPartialFilesAreRemoved() throws Exception {
        var store = new StudioEnhancementModel(root, () -> new java.io.ByteArrayInputStream(new byte[32]));
        store.start(); awaitDownload(store);
        assertEquals("FAILED", ((StudioModelService.Progress) store.status().get("progress")).state());
        assertFalse(store.available());
        try (var files = Files.list(root)) { assertFalse(files.anyMatch(p -> p.toString().endsWith(".partial") || p.toString().endsWith(".onnx"))); }
    }
    private static void awaitDownload(StudioEnhancementModel store) throws Exception {
        long end = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < end) {
            String state = ((StudioModelService.Progress) store.status().get("progress")).state();
            if (state.equals("COMPLETE") || state.equals("FAILED")) return;
            Thread.sleep(25);
        }
        fail("Model installer did not finish");
    }
    @Test void combinedPipelineRemovesThenEnhancesAndEnhanceOnlyRetainsBackground() throws Exception {
        String enhancement = System.getProperty("smartstock.test.enhancementModel"), removal = System.getProperty("smartstock.test.photoModel");
        Assumptions.assumeTrue(enhancement != null && removal != null, "Supply both pinned models for combined verification.");
        var source = new BufferedImage(160, 128, BufferedImage.TYPE_INT_ARGB);
        var g = source.createGraphics(); g.setColor(java.awt.Color.WHITE); g.fillRect(0, 0, 160, 128);
        g.setColor(new java.awt.Color(112, 34, 168)); g.fillOval(45, 20, 70, 95); g.dispose(); source.setRGB(0, 0, 0);
        var bytes = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(source, "png", bytes);
        byte[] original = bytes.toByteArray();
        for (boolean cutout : new boolean[]{false, true}) {
            var result = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(StudioBackgroundService.transform(
                    original, StudioQuality.FAST, false, cutout, 2, Path.of(removal), Path.of(enhancement))));
            assertEquals(320, result.getWidth()); assertEquals(256, result.getHeight());
            assertEquals(0, result.getRGB(0, 0) >>> 24);
            assertTrue((result.getRGB(160, 120) >>> 24) > 200);
            if (cutout) assertTrue((result.getRGB(20, 20) >>> 24) < 20);
            else assertEquals(255, result.getRGB(20, 20) >>> 24);
        }
        assertArrayEquals(original, bytes.toByteArray());
    }
}
