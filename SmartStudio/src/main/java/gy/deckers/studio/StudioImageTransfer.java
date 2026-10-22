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

/** Shared image transfers: file drops use the same bounded reader as Choose image. */
final class StudioImageTransfer {
    static final DataFlavor PNG = new DataFlavor("image/png;class=java.io.InputStream", "PNG image");
    static void registerNativeImages() {
        SystemFlavorMap map = (SystemFlavorMap) SystemFlavorMap.getDefaultFlavorMap();
        map.addUnencodedNativeForFlavor(PNG, "PNG");
        map.addFlavorForUnencodedNative("PNG", PNG);
    }

    static boolean supports(Transferable source) {
        return source.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || source.isDataFlavorSupported(DataFlavor.imageFlavor)
                || source.isDataFlavorSupported(PNG);
    }

    static StudioImageFile.Loaded load(Transferable source) throws Exception {
        return capture(source).call();
    }

    /** Read native transfer data while importData's drop is active; decode files afterward. */
    static Callable<StudioImageFile.Loaded> capture(Transferable source) throws Exception {return capture(source,false); }
    static Callable<StudioImageFile.Loaded> capture(Transferable source,boolean artwork) throws Exception {
        IOException failure = new IOException("The source application did not supply a readable image.");
        if (source.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
            Object value = source.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(value instanceof List<?> files) || files.size() != 1 || !(files.get(0) instanceof File file))
                throw new IOException("Drop one PNG or JPEG image at a time.");
            var path = file.toPath();
            return () -> artwork?StudioImageFile.loadArtwork(path):StudioImageFile.load(path);
            } catch (Exception ex) { failure.addSuppressed(ex); }
        }
        // Some Windows sources advertise a bitmap or file list that they cannot render.
        // Try every supplied representation while the native drop is still active.
        if (!artwork && source.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try {
                BufferedImage snapshot = snapshot((Image) source.getTransferData(DataFlavor.imageFlavor),artwork);
                return () -> loadImage(snapshot,artwork);
            } catch (Exception ex) { failure.addSuppressed(ex); }
        }
        for (DataFlavor flavor : source.getTransferDataFlavors()) {
            if (!(flavor.isMimeTypeEqual("image/png") || flavor.isMimeTypeEqual("image/jpeg"))) continue;
            try {
            Object value = source.getTransferData(flavor);
            byte[] bytes;
            if (value instanceof byte[] data) {
                if (data.length > (artwork?StudioImageFile.ARTWORK_MAX_BYTES:StudioImageFile.PHOTO_MAX_BYTES)) throw new IOException(artwork?"Banner image exceeds 100 MiB.":"Image exceeds 20 MB.");
                bytes = data.clone();
            } else if (value instanceof InputStream input) {
                try (input) {
                bytes = input.readNBytes((artwork?StudioImageFile.ARTWORK_MAX_BYTES:StudioImageFile.PHOTO_MAX_BYTES) + 1);
                }
            } else { throw new IOException("Unsupported encoded image representation."); }
            if(artwork){String name=flavor.isMimeTypeEqual("image/jpeg")?"dropped-image.jpg":"dropped-image.png";return ()->StudioImageFile.artwork(bytes,name);}
            StudioImageFile.validatePhoto(bytes);
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
            if (decoded == null) throw new IOException("The supplied image cannot be decoded.");
            String name = flavor.isMimeTypeEqual("image/jpeg") ? "dropped-image.jpg" : "dropped-image.png";
            return () -> new StudioImageFile.Loaded(bytes, decoded, name);
            } catch (Exception ex) { failure.addSuppressed(ex); }
            }
        // Banner paste prefers the source PNG/JPEG bytes over a re-encoded bitmap.
        if(artwork&&source.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try{BufferedImage bitmap=snapshot((Image)source.getTransferData(DataFlavor.imageFlavor),true);return ()->loadImage(bitmap,true);}
            catch(Exception ex){failure.addSuppressed(ex);}
        }
        if (failure.getSuppressed().length == 1 && failure.getSuppressed()[0] instanceof IOException ex) throw ex;
        throw failure;
    }

    private static BufferedImage snapshot(Image image,boolean artwork) throws IOException {
        if (image == null) throw new IOException("The source application did not provide an image. Save it first and choose the saved file.");
        ImageIcon loaded = new ImageIcon(image);
        int width = loaded.getIconWidth(), height = loaded.getIconHeight();
        if (width < 32 || height < 32 || (long) width * height > (artwork?100_000_000:12_000_000))
            throw new IOException(artwork?"Banner images must be at least 32 × 32 pixels and up to 100 megapixels.":"Choose an image at least 32 × 32 pixels and no larger than 12 megapixels.");
        BufferedImage snapshot = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = snapshot.createGraphics();
        try { graphics.setComposite(java.awt.AlphaComposite.Src); graphics.drawImage(loaded.getImage(), 0, 0, null); }
        finally { graphics.dispose(); }
        return snapshot;
    }

    private static StudioImageFile.Loaded loadImage(Image image,boolean artwork) throws Exception {
        ImageIcon loaded = new ImageIcon(image);
        int width = loaded.getIconWidth(), height = loaded.getIconHeight();
        if (width < 32 || height < 32 || (long) width * height > (artwork?100_000_000:12_000_000))
            throw new IOException(artwork?"Banner images must be at least 32 × 32 pixels and up to 100 megapixels.":"Choose an image at least 32 × 32 pixels and no larger than 12 megapixels.");
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
        if(artwork)return StudioImageFile.artwork(png,"dropped-image.png");
        StudioImageFile.validatePhoto(png);
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
