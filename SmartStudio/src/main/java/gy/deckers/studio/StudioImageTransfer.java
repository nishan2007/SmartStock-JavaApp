package gy.deckers.studio;

import java.awt.Image;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.swing.ImageIcon;
import services.StudioBackgroundService;

/** Shared image transfers: file drops use the same bounded reader as Choose image. */
final class StudioImageTransfer {
    static final DataFlavor PNG = new DataFlavor("image/png;class=java.io.InputStream", "PNG image");

    static boolean supports(Transferable source) {
        return source.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || source.isDataFlavorSupported(DataFlavor.imageFlavor);
    }

    static StudioImageFile.Loaded load(Transferable source) throws Exception {
        return capture(source).call();
    }

    /** Read native transfer data while importData's drop is active; decode files afterward. */
    static Callable<StudioImageFile.Loaded> capture(Transferable source) throws Exception {
        if (source.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            Object value = source.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(value instanceof List<?> files) || files.size() != 1 || !(files.get(0) instanceof File file))
                throw new IOException("Drop one PNG or JPEG image at a time.");
            var path = file.toPath();
            return () -> StudioImageFile.load(path);
        }
        Image image = (Image) source.getTransferData(DataFlavor.imageFlavor);
        return () -> loadImage(image);
    }

    private static StudioImageFile.Loaded loadImage(Image image) throws Exception {
        ImageIcon loaded = new ImageIcon(image);
        int width = loaded.getIconWidth(), height = loaded.getIconHeight();
        if (width < 32 || height < 32 || (long) width * height > 12_000_000)
            throw new IOException("Choose an image at least 32 × 32 pixels and no larger than 12 megapixels.");
        BufferedImage bitmap;
        if (image instanceof BufferedImage buffered) bitmap = buffered;
        else {
            bitmap = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            var graphics = bitmap.createGraphics();
            try { graphics.setComposite(java.awt.AlphaComposite.Src); graphics.drawImage(image, 0, 0, null); }
            finally { graphics.dispose(); }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = new MemoryCacheImageOutputStream(bytes)) { ImageIO.write(bitmap, "png", output); }
        byte[] png = bytes.toByteArray();
        StudioBackgroundService.validate(png);
        return new StudioImageFile.Loaded(png, bitmap, "dropped-image.png");
    }

    static Transferable result(BufferedImage image, byte[] png) {
        // PNG retains alpha for editors that support it; imageFlavor supports ordinary image pasting.
        SystemFlavorMap map = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
        map.addUnencodedNativeForFlavor(PNG, "PNG");
        map.addFlavorForUnencodedNative("PNG", PNG);
        byte[] snapshot = png.clone();
        return new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{PNG, DataFlavor.imageFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return PNG.equals(flavor) || DataFlavor.imageFlavor.equals(flavor); }
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (PNG.equals(flavor)) return new ByteArrayInputStream(snapshot);
                if (DataFlavor.imageFlavor.equals(flavor)) return image;
                throw new UnsupportedFlavorException(flavor);
            }
        };
    }
}
