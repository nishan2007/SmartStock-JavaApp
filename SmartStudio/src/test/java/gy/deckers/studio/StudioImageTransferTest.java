package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class StudioImageTransferTest {
    @TempDir Path folder;

    @Test void copiedPngPreservesTransparentPixelsAndOriginalDimensions() throws Exception {
        BufferedImage image = new BufferedImage(40, 48, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 5, 0x8044aaee);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        byte[] png = bytes.toByteArray();
        Transferable copied = StudioImageTransfer.result(image, png);
        png[0] = 0;
        Clipboard clipboard = new Clipboard("test");
        clipboard.setContents(copied, null);
        try (InputStream stream = (InputStream) clipboard.getData(StudioImageTransfer.PNG)) {
            BufferedImage pasted = ImageIO.read(stream);
            assertEquals(40, pasted.getWidth()); assertEquals(48, pasted.getHeight());
            assertEquals(0x8044aaee, pasted.getRGB(5, 5)); assertEquals(0, pasted.getRGB(0, 0));
        }
        assertSame(image, clipboard.getData(DataFlavor.imageFlavor));
        assertThrows(UnsupportedFlavorException.class, () -> copied.getTransferData(DataFlavor.stringFlavor));
        try (InputStream again = (InputStream) copied.getTransferData(StudioImageTransfer.PNG)) {
            assertNotNull(ImageIO.read(again));
        }
    }

    @Test void droppedFileLoadsAndMultipleFilesAreRejected() throws Exception {
        Path file = folder.resolve("sample.png");
        ImageIO.write(new BufferedImage(40, 48, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        Transferable one = files(List.of(file.toFile()));
        assertTrue(StudioImageTransfer.supports(one));
        var loaded = StudioImageTransfer.load(one);
        assertEquals("sample.png", loaded.name()); assertEquals(48, loaded.image().getHeight());
        assertThrows(IOException.class, () -> StudioImageTransfer.load(files(List.of(file.toFile(), file.toFile()))));
        assertFalse(StudioImageTransfer.supports(new StringSelection("not an image")));
    }

    @Test void droppedImageRetainsAlpha() throws Exception {
        BufferedImage image = new BufferedImage(40, 48, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 5, 0x8044aaee);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        Transferable source = StudioImageTransfer.result(image, bytes.toByteArray());
        var loaded = StudioImageTransfer.load(source);
        assertEquals(image.getRGB(5, 5), loaded.image().getRGB(5, 5));
    }

    @Test void fileDropCanDecodeAfterNativeTransferExpires() throws Exception {
        Path file = folder.resolve("expired-drop.png");
        ImageIO.write(new BufferedImage(40, 48, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        boolean[] active = {true};
        Transferable nativeDrop = expiring(files(List.of(file.toFile())), active);
        var loader = StudioImageTransfer.capture(nativeDrop);
        active[0] = false;
        // No file I/O happens until the worker executes; the drop itself is already over.
        var loaded = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return loader.call(); } catch (Exception ex) { throw new RuntimeException(ex); }
        }).get();
        assertEquals("expired-drop.png", loaded.name());
        assertEquals(48, loaded.image().getHeight());
    }

    @Test void imageDropCanDecodeAfterNativeTransferExpires() throws Exception {
        BufferedImage image = new BufferedImage(40, 48, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 5, 0x8044aaee);
        boolean[] active = {true};
        var loader = StudioImageTransfer.capture(expiring(StudioImageTransfer.result(image, new byte[0]), active));
        active[0] = false;
        assertEquals(0x8044aaee, loader.call().image().getRGB(5, 5));
    }

    private static Transferable expiring(Transferable delegate, boolean[] active) {
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return delegate.getTransferDataFlavors(); }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return delegate.isDataFlavorSupported(flavor); }
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                if (!active[0]) throw new java.awt.dnd.InvalidDnDOperationException("Drop has ended");
                return delegate.getTransferData(flavor);
            }
        };
    }

    private static Transferable files(List<File> files) {
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.javaFileListFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.javaFileListFlavor.equals(flavor); }
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                return files;
            }
        };
    }
}
