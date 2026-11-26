package gy.deckers.studio;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.*;
import java.util.*;

/** Bounded session cache. Every caller owns its own temporary file, so cleanup is independent. */
final class BannerResampleCache {
    private static final long LIMIT = 512L * 1024 * 1024;
    private record Entry(Path file, long bytes, SoftReference<BufferedImage> image) {}
    private static final Map<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    private static long bytes;

    static synchronized SoftReference<BufferedImage> restore(String key, Path target) throws IOException {
        var entry = entries.get(key);
        if (entry == null) return null;
        try { Files.copy(entry.file, target, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ex) { entries.remove(key); bytes -= entry.bytes; return null; }
        return entry.image;
    }

    static synchronized void remember(String key, Path source, BufferedImage image) {
        Path cached = null;
        try {
            long size = Files.size(source);
            if (size > LIMIT || entries.containsKey(key)) return;
            while (!entries.isEmpty() && (bytes + size > LIMIT || entries.size() >= 16)) {
                var it = entries.values().iterator(); var old = it.next();
                Files.deleteIfExists(old.file); bytes -= old.bytes; it.remove();
            }
            cached = Files.createTempFile("smartstudio-resample-cache-", ".png");
            cached.toFile().deleteOnExit();
            Files.copy(source, cached, StandardCopyOption.REPLACE_EXISTING);
            entries.put(key, new Entry(cached, size, new SoftReference<>(image))); bytes += size;
        } catch (IOException ex) {
            if (cached != null) try { Files.deleteIfExists(cached); } catch (IOException ignored) {}
            // A cache failure must never prevent an export.
        }
    }
    private BannerResampleCache() {}
}
