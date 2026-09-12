package ui.helpers;

import services.LanApiClient;
import utils.ImageCacheManager;
import utils.ImageOptimizationHelper;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

/** Optional customer portraits stored through the authenticated LAN image service. */
public final class CustomerPhotoHelper {
    private CustomerPhotoHelper() { }

    public static String uploadIfNeeded(String value) throws Exception {
        String photo = value == null ? "" : value.trim();
        if (photo.isEmpty() || ImageCacheManager.isRemoteImageUrl(photo)) return photo;
        File file = new File(photo);
        if (!file.isFile()) throw new IllegalArgumentException("The selected customer photo was not found.");
        try (ImageOptimizationHelper.OptimizedImage optimized = ImageOptimizationHelper.optimizeForUpload(
                file, "customer-photo", 800, 800, 0.85f,
                15L * 1024 * 1024, 200L * 1024, false)) {
            String reference = LanApiClient.uploadManagedImage("CUSTOMER_PHOTO", "customer files",
                    "customer photos/" + UUID.randomUUID() + "-" + optimized.filename(),
                    optimized.contentType(), Files.readAllBytes(optimized.file().toPath()));
            ImageCacheManager.cacheUploadedImage(reference, optimized.file().toPath());
            return reference;
        }
    }
}
