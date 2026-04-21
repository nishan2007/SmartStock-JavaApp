package utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;

public final class ImageOptimizationHelper {
    private ImageOptimizationHelper() {
    }

    public static OptimizedImage optimizeForUpload(
            File sourceFile,
            String outputPrefix,
            int maxWidth,
            int maxHeight,
            float initialQuality,
            long maxOriginalBytes,
            long maxOptimizedBytes
    ) throws IOException {
        return optimizeForUpload(
                sourceFile,
                outputPrefix,
                maxWidth,
                maxHeight,
                initialQuality,
                maxOriginalBytes,
                maxOptimizedBytes,
                true
        );
    }

    public static OptimizedImage optimizeForUpload(
            File sourceFile,
            String outputPrefix,
            int maxWidth,
            int maxHeight,
            float initialQuality,
            long maxOriginalBytes,
            long maxOptimizedBytes,
            boolean allowDimensionReduction
    ) throws IOException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IOException("The selected image file was not found.");
        }

        long originalBytes = Files.size(sourceFile.toPath());
        if (originalBytes > maxOriginalBytes) {
            throw new IOException("Image is too large. Maximum original file size is " + formatBytes(maxOriginalBytes) + ".");
        }

        BufferedImage source = ImageIO.read(sourceFile);
        if (source == null) {
            throw new IOException("The selected file is not a supported image.");
        }

        int targetWidth = source.getWidth();
        int targetHeight = source.getHeight();
        float quality = Math.max(Math.min(initialQuality, 0.95f), 0.30f);
        File outputFile = null;

        for (int attempt = 0; attempt < 11; attempt++) {
            Dimension scaledSize = fitWithin(targetWidth, targetHeight, maxWidth, maxHeight);
            BufferedImage scaled = scaleToJpeg(source, scaledSize.width, scaledSize.height);
            outputFile = File.createTempFile(outputPrefix + "-", ".jpg");
            writeJpeg(scaled, outputFile, quality);

            long optimizedBytes = Files.size(outputFile.toPath());
            if (optimizedBytes <= maxOptimizedBytes) {
                return new OptimizedImage(outputFile, "image/jpeg", outputPrefix + ".jpg", originalBytes, optimizedBytes);
            }

            Files.deleteIfExists(outputFile.toPath());
            outputFile = null;
            quality -= 0.07f;
            if (quality < 0.32f && allowDimensionReduction) {
                quality = 0.72f;
                targetWidth = Math.max((int) Math.round(targetWidth * 0.82), 320);
                targetHeight = Math.max((int) Math.round(targetHeight * 0.82), 320);
            }
        }

        if (outputFile != null) {
            Files.deleteIfExists(outputFile.toPath());
        }
        throw new IOException("Image could not be compressed below " + formatBytes(maxOptimizedBytes) + ".");
    }

    private static Dimension fitWithin(int width, int height, int maxWidth, int maxHeight) {
        if (width <= 0 || height <= 0) {
            return new Dimension(1, 1);
        }
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        scale = Math.min(scale, 1.0);
        return new Dimension(
                Math.max((int) Math.round(width * scale), 1),
                Math.max((int) Math.round(height * scale), 1)
        );
    }

    private static BufferedImage scaleToJpeg(BufferedImage source, int width, int height) {
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return output;
    }

    private static void writeJpeg(BufferedImage image, File outputFile, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG image writer is available.");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        if (params.canWriteCompressed()) {
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(Math.max(Math.min(quality, 0.95f), 0.28f));
        }

        try (ImageOutputStream output = ImageIO.createImageOutputStream(outputFile)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }

    private static String formatBytes(long bytes) {
        double mb = bytes / (1024.0 * 1024.0);
        return String.format("%.1f MB", mb);
    }

    public static final class OptimizedImage implements AutoCloseable {
        private final File file;
        private final String contentType;
        private final String filename;
        private final long originalBytes;
        private final long optimizedBytes;

        private OptimizedImage(File file, String contentType, String filename, long originalBytes, long optimizedBytes) {
            this.file = file;
            this.contentType = contentType;
            this.filename = filename;
            this.originalBytes = originalBytes;
            this.optimizedBytes = optimizedBytes;
        }

        public File file() {
            return file;
        }

        public String contentType() {
            return contentType;
        }

        public String filename() {
            return filename;
        }

        public long originalBytes() {
            return originalBytes;
        }

        public long optimizedBytes() {
            return optimizedBytes;
        }

        @Override
        public void close() throws IOException {
            if (file != null) {
                Files.deleteIfExists(file.toPath());
            }
        }
    }
}
