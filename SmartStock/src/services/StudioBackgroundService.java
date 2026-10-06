package services;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;

/** Shared local studio processing; both clients use the same bounded worker. */
public final class StudioBackgroundService {
    private static final Semaphore WORKER = new Semaphore(1);
    private StudioBackgroundService() { }

    public static void validate(byte[] bytes) throws Exception {
        if (bytes.length == 0 || bytes.length > 6 * 1024 * 1024)
            throw new IllegalArgumentException("Choose a PNG or JPEG image of at most 6 MB.");
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Choose a valid PNG or JPEG image.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                    throw new IllegalArgumentException("Choose a PNG or JPEG image.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 32 || height < 32 || (long) width * height > 12_000_000)
                    throw new IllegalArgumentException("Choose an image at least 32 pixels wide and high, up to 12 megapixels.");
            } finally { reader.dispose(); }
        }
    }

    public static byte[] remove(byte[] bytes) throws Exception {
        return remove(bytes, StudioQuality.FAST, false);
    }

    public static byte[] remove(byte[] bytes, StudioQuality quality, boolean cleanEdges) throws Exception {
        return process(bytes, quality, cleanEdges, false);
    }

    public static byte[] reviewJpeg(byte[] bytes, StudioQuality quality) throws Exception {
        return process(bytes, quality, false, true);
    }

    private static byte[] process(byte[] bytes, StudioQuality quality, boolean cleanEdges, boolean review) throws Exception {
        validate(bytes);
        if (!WORKER.tryAcquire()) throw new BusyException();
        try (var processor = new CatalogStudioImageProcessor(quality)) {
            return review ? processor.makeStudioJpeg(bytes) : processor.removeBackgroundPng(bytes, cleanEdges);
        } finally { WORKER.release(); }
    }

    public static final class BusyException extends Exception {
        public BusyException() { super("Another photo is being processed. Try again shortly."); }
    }

    public static Map<String,Object> branding(int locationId) throws Exception {
        var info = managers.ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation(locationId);
        Map<String,Object> brand = new LinkedHashMap<>();
        brand.put("name", info.companyName()); brand.put("motto", info.mottoLine1());
        brand.put("phone", info.phoneLine1()); brand.put("email", info.emailLine1());
        brand.put("studioBestQualityAvailable", StudioModelService.current().available(StudioQuality.BEST));
        brand.put("studioFastQualityAvailable", StudioModelService.current().available(StudioQuality.FAST));
        if (!info.logoPath().isBlank()) {
            try {
                var logo = ServerImageAssetService.load(info.logoPath());
                if (logo.bytes().length <= 2*1024*1024 && (logo.contentType().equals("image/png") || logo.contentType().equals("image/jpeg")))
                    brand.put("logo", "data:" + logo.contentType() + ";base64," + Base64.getEncoder().encodeToString(logo.bytes()));
            } catch (Exception ignored) { /* Text remains available if the logo cannot load. */ }
        }
        return brand;
    }
}
