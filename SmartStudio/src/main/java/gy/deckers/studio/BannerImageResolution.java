package gy.deckers.studio;

import java.awt.AlphaComposite;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import publishing.Publication;
import publishing.PublicationImport;

final class BannerImageResolution {
    static final int TARGET_PPI = 300;
    // Preserve the existing decode/heap budget; encoded file size must not
    // reduce the resampling resolution.
    private static final long MAX_PIXELS = Math.max(1_000_000L,
            Math.min(100_000_000L, Runtime.getRuntime().maxMemory() / 20));

    record Picture(int page, int layer, String name, Rectangle crop, double ppi,
                   long targetWidth, long targetHeight) {
        Dimension outputSize(long pixelBudget) {
            double scale = Math.min(1, Math.sqrt((double) pixelBudget / targetWidth / targetHeight));
            return new Dimension((int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.floor(targetWidth * scale))),
                    (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.floor(targetHeight * scale))));
        }
        Dimension outputSize() { return outputSize(MAX_PIXELS); }
        double outputPpi() {
            Dimension size = outputSize();
            return TARGET_PPI * Math.min((double) size.width / targetWidth, (double) size.height / targetHeight);
        }
        boolean canResample() {
            Dimension size = outputSize();
            return size.width >= crop.width && size.height >= crop.height
                    && (size.width > crop.width || size.height > crop.height);
        }
        String description() {
            return String.format(java.util.Locale.ROOT, "Page %d, layer %d (%s): %.1f PPI",
                    page + 1, layer + 1, name, ppi);
        }
    }

    static List<Picture> belowTarget(Publication banner) throws IOException {
        var dimensions = new HashMap<String, Dimension>();
        var pictures = new ArrayList<Picture>();
        for (int i = 0; i < banner.pages.size(); i++) {
            var page = banner.pages.get(i);
            if (page.reference) continue;
            for (int j = 0; j < page.objects.size(); j++) {
                var layer = page.objects.get(j);
                if (!"IMAGE".equals(layer.type)) continue;
                Dimension size = dimensions.get(layer.asset);
                if (size == null) {
                    size = readSize(banner, layer.asset);
                    dimensions.put(layer.asset, size);
                }
                // Match the source crop used by PublicationRenderer.
                int x = (int) (layer.cropX * size.width);
                int y = (int) (layer.cropY * size.height);
                int width = Math.max(1, Math.min(size.width - x, (int) (layer.cropWidth * size.width)));
                int height = Math.max(1, Math.min(size.height - y, (int) (layer.cropHeight * size.height)));
                double ppi = Math.min(width * 25.4 / layer.width, height * 25.4 / layer.height);
                if (ppi >= TARGET_PPI) continue;
                pictures.add(new Picture(i, j, layer.text, new Rectangle(x, y, width, height), ppi,
                        Math.max(width, (long) Math.ceil(layer.width / 25.4 * TARGET_PPI)),
                        Math.max(height, (long) Math.ceil(layer.height / 25.4 * TARGET_PPI))));
            }
        }
        return List.copyOf(pictures);
    }

    private static Dimension readSize(Publication banner, String id) throws IOException {
        try (var stream = banner.openAsset(id); var input = ImageIO.createImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("A banner image could not be read. Reinsert the source file.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new Dimension(reader.getWidth(0), reader.getHeight(0));
            } finally { reader.dispose(); }
        }
    }

    private record ResampleKey(String asset, Rectangle crop, int width, int height) {}

    static Publication resample(Publication source, List<Picture> pictures) throws IOException {
        long started = System.nanoTime();
        var groups = new java.util.LinkedHashMap<String, List<Picture>>();
        var dimensions = new HashMap<String, Dimension>();
        long largest = 1;
        for (var picture : pictures) {
            if (!picture.canResample()) continue;
            String asset = source.pages.get(picture.page()).objects.get(picture.layer()).asset;
            groups.computeIfAbsent(asset, k -> new ArrayList<>()).add(picture);
            var size = picture.outputSize();
            var original = dimensions.get(asset);
            if (original == null) { original = readSize(source, asset); dimensions.put(asset, original); }
            // Account for source decoding, destination, encoder buffers and job copies.
            largest = Math.max(largest, 16L * size.width * size.height
                    + 12L * original.width * original.height);
        }
        int workers = (int) Math.max(1, Math.min(Math.min(4, Runtime.getRuntime().availableProcessors()),
                Math.min(groups.size(), Runtime.getRuntime().maxMemory() / 3 / largest)));
        if (workers == 1) return resampleSerial(source, pictures);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(workers);
        var results = new java.util.concurrent.ConcurrentLinkedQueue<Publication>();
        boolean success = false;
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Publication>>();
            for (var group : groups.values()) futures.add(pool.submit(() -> {
                var result = resampleSerial(source, group); results.add(result); return result;
            }));
            var output = source.copy();
            int index = 0;
            for (var group : groups.values()) {
                var part = futures.get(index++).get();
                for (var picture : group) {
                    var layer = part.pages.get(picture.page()).objects.get(picture.layer());
                    output.pages.get(picture.page()).objects.set(picture.layer(), layer);
                    if (part.assetFiles.containsKey(layer.asset)) output.assetFiles.put(layer.asset, part.assetFiles.get(layer.asset));
                    if (part.decodedAssets.containsKey(layer.asset)) output.decodedAssets.put(layer.asset, part.decodedAssets.get(layer.asset));
                }
            }
            success = true;
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IOException("Export cancelled.", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IOException("Banner resampling failed.", ex.getCause());
        } finally {
            pool.shutdownNow();
            // Join workers before deleting their job files, including late completions.
            boolean interrupted = Thread.interrupted();
            while (!pool.isTerminated()) try { pool.awaitTermination(100, java.util.concurrent.TimeUnit.MILLISECONDS); }
            catch (InterruptedException ex) { interrupted = true; }
            if (!success) for (var result : results) cleanup(source, result);
            if (interrupted) Thread.currentThread().interrupt();
            timing("total", started, "workers=" + workers);
        }
    }

    private static void timing(String stage, long started, String detail) {
        System.getLogger(BannerImageResolution.class.getName()).log(System.Logger.Level.INFO,
                "Banner resampling " + stage + " ms=" + (System.nanoTime()-started)/1_000_000 + " " + detail);
    }

    private static String contentHash(Publication source, String asset) throws IOException {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            try (var input = source.openAsset(asset)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Export cancelled.");
                    digest.update(buffer, 0, n);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }

    private static Publication resampleSerial(Publication source, List<Picture> pictures) throws IOException {
        long started = System.nanoTime();
        Publication copy = source.copy();
        boolean success=false;
        var reused = new HashMap<ResampleKey, String>();
        var hashes = new HashMap<String, String>();
        BufferedImage cachedOriginal = null;
        String cachedAsset = null;
        long decodeCacheBudget = Runtime.getRuntime().maxMemory() / 8;
        try {
        for (Picture picture : pictures) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Export cancelled.");
            // Preserve sources that already exceed the resampling budget.
            if (!picture.canResample()) continue;
            Dimension outputSize = picture.outputSize();
            var layer = copy.pages.get(picture.page()).objects.get(picture.layer());
            var crop = picture.crop();
            var key = new ResampleKey(layer.asset, crop, outputSize.width, outputSize.height);
            String existing = reused.get(key);
            if (existing != null) {
                layer.asset = existing;
                layer.cropX = layer.cropY = 0;
                layer.cropWidth = layer.cropHeight = 1;
                continue;
            }
            String hash = hashes.get(layer.asset);
            if (hash == null) {
                long hashing = System.nanoTime();
                hash = contentHash(source, layer.asset); hashes.put(layer.asset, hash);
                timing("hash", hashing, "");
            }
            String cacheKey = hash + ":bicubic-v1:" + crop + ":" + outputSize.width + "x" + outputSize.height;
            var encoded = Files.createTempFile("smartstudio-resampled-", ".png");
            encoded.toFile().deleteOnExit();
            // Register immediately so every failure path cleans up its own temporary file.
            String generatedAsset = java.util.UUID.randomUUID().toString();
            copy.assetFiles.put(generatedAsset, encoded);
            long stage = System.nanoTime();
            var restored = BannerResampleCache.restore(cacheKey, encoded);
            if (restored != null) {
                layer.asset = generatedAsset;
                copy.decodedAssets.put(generatedAsset, restored);
                reused.put(key, generatedAsset);
                layer.cropX = layer.cropY = 0; layer.cropWidth = layer.cropHeight = 1;
                timing("cache-hit", stage, "");
                continue;
            }
            BufferedImage original;
            if (layer.asset.equals(cachedAsset)) original = cachedOriginal;
            else {
                if (cachedOriginal != null) cachedOriginal.flush();
                cachedOriginal = null;
                cachedAsset = null;
                original = PublicationImport.decodeImage(source.openAsset(layer.asset), 0);
                // Retain one source, bounded independently of the output pixel budget.
                if ((long) original.getWidth() * original.getHeight() * 4 <= decodeCacheBudget) {
                    cachedOriginal = original;
                    cachedAsset = layer.asset;
                }
            }
            timing("decode", stage, "");
            stage = System.nanoTime();
            var scaled = new BufferedImage(outputSize.width, outputSize.height,
                    original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            var graphics = scaled.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.drawImage(original.getSubimage(crop.x, crop.y, crop.width, crop.height),
                        0, 0, scaled.getWidth(), scaled.getHeight(), null);
            } finally {
                graphics.dispose();
                if (original != cachedOriginal) original.flush();
            }
            timing("scale", stage, "pixels=" + (long)outputSize.width*outputSize.height);
            stage = System.nanoTime();
            boolean complete=false;
            try {
                writeTemporaryPng(scaled, encoded);
                BannerResampleCache.remember(cacheKey, encoded, scaled);
                copy.decodedAssets.put(generatedAsset, new java.lang.ref.SoftReference<>(scaled));
                complete=true;
            } finally { if(!complete){scaled.flush();Files.deleteIfExists(encoded);} }
            timing("encode", stage, "");
            // Job-only output is not a template import. Stream the lossless PNG
            // to disk without a 100 MiB cap or duplicate encoded byte arrays.
            layer.asset = generatedAsset;
            copy.assetFiles.put(layer.asset,encoded);
            reused.put(key, layer.asset);
            layer.cropX = layer.cropY = 0;
            layer.cropWidth = layer.cropHeight = 1;
        }
        success=true;
        return copy;
        } finally {
            if (cachedOriginal != null) cachedOriginal.flush();
            if(!success)cleanup(source,copy);
            timing("total", started, "workers=1");
        }
    }

    private static void writeTemporaryPng(BufferedImage image, java.nio.file.Path file) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("PNG");
        if (!writers.hasNext()) throw new IOException("Could not encode the resampled image.");
        var writer = writers.next();
        try (var output = new javax.imageio.stream.FileImageOutputStream(file.toFile())) {
            writer.setOutput(output);
            var settings = writer.getDefaultWriteParam();
            if (settings.canWriteCompressed()) {
                settings.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                // PNG remains lossless; this selects the fastest deflate level.
                settings.setCompressionQuality(1f);
            }
            writer.write(null, new javax.imageio.IIOImage(image, null, null), settings);
        } finally { writer.dispose(); }
    }

    static void cleanup(Publication source,Publication output) {
        if(source==output)return;
        output.decodedAssets.clear();
        for(var file:output.assetFiles.values())
            if(!source.assetFiles.containsValue(file))try{Files.deleteIfExists(file);}catch(IOException ignored){}
    }

    private BannerImageResolution() {}
}
