package services;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Catalogue compatibility is an inference contract, independent of app versions. */
public record StudioModelCatalogue(int schemaVersion, List<Entry> models) {
    public static final String OBJECT_KEY = "models/catalogue-v1.json";
    private static final Gson JSON = new Gson();

    public record Entry(StudioQuality quality, String version, String objectKey,
                        long sizeBytes, String sha256, String compatibility) {
        public void validate() throws IOException {
            if (quality == null || version == null || !version.matches("[A-Za-z0-9._-]{1,64}")
                    || sha256 == null || !sha256.matches("[a-f0-9]{64}")
                    || sizeBytes <= 0 || sizeBytes > 2_000_000_000L
                    || !Objects.equals(compatibility, supportedCompatibility(quality))
                    || !Objects.equals(objectKey, "models/" + quality.name().toLowerCase(Locale.ROOT)
                        + "/" + sha256 + ".onnx")) {
                throw new IOException("The AI model catalogue contains an invalid or unsupported model.");
            }
        }
    }

    public static String supportedCompatibility(StudioQuality quality) {
        return quality == StudioQuality.FAST ? "isnet-1024-v1" : "birefnet-1024-imagenet-v1";
    }

    public Entry entry(StudioQuality quality) {
        return models.stream().filter(e -> e.quality() == quality).findFirst().orElseThrow();
    }

    public static StudioModelCatalogue parse(String text) throws IOException {
        try {
            StudioModelCatalogue result = JSON.fromJson(text, StudioModelCatalogue.class);
            if (result == null || result.schemaVersion != 1 || result.models == null
                    || result.models.size() != StudioQuality.values().length) throw new IOException("Unsupported AI model catalogue.");
            Set<StudioQuality> qualities = new HashSet<>();
            for (Entry e : result.models) {
                if (e == null) throw new IOException("Missing AI model entry.");
                e.validate();
                if (!qualities.add(e.quality())) throw new IOException("Duplicate AI model quality.");
            }
            return result;
        } catch (RuntimeException e) { throw new IOException("Invalid AI model catalogue.", e); }
    }

    public static StudioModelCatalogue bundled() throws IOException {
        try (InputStream input = StudioModelCatalogue.class.getResourceAsStream("/models/catalogue-v1.json")) {
            if (input == null) throw new IOException("The application AI model catalogue is missing.");
            return parse(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public String json() { return JSON.toJson(this); }
}
