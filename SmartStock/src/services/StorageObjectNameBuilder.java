package services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

/**
 * Builds readable, path-safe Storage object names without sacrificing uniqueness.
 */
public final class StorageObjectNameBuilder {
    private static final int MAX_SLUG_LENGTH = 180;

    private StorageObjectNameBuilder() {
    }

    public static String filename(String extensionSource, String fallbackExtension,
                                  String uniqueToken, String... descriptiveParts) {
        List<String> parts = new ArrayList<>();
        if (descriptiveParts != null) {
            for (String part : descriptiveParts) {
                String slug = slug(part);
                if (!slug.isBlank() && !parts.contains(slug)) parts.add(slug);
            }
        }
        String base = parts.isEmpty() ? "asset" : String.join("-", parts);
        if (base.length() > MAX_SLUG_LENGTH) {
            base = base.substring(0, MAX_SLUG_LENGTH).replaceFirst("-+$", "");
        }
        String unique = slug(uniqueToken);
        if (unique.isBlank()) unique = Long.toString(System.currentTimeMillis());
        return base + "-" + unique + "." + extension(extensionSource, fallbackExtension);
    }

    public static String productImageFilename(String extensionSource, String uniqueToken,
                                              String product, String brand, String type,
                                              String size, String variant) {
        String base=String.join("-", segment(product,"product",72),segment(brand,"unbranded",28),
                segment(type,"item",24),segment(size,"standard",20),segment(variant,"standard",24),"product-image");
        String unique=slug(uniqueToken);if(unique.isBlank())unique=Long.toString(System.currentTimeMillis());
        return base+"-"+unique+"."+extension(extensionSource,"jpg");
    }

    private static String fallback(String value, String fallback) {
        return slug(value).isBlank() ? fallback : value;
    }

    private static String segment(String value,String fallback,int maximum){
        String result=slug(fallback(value,fallback));
        return result.length()<=maximum?result:result.substring(0,maximum).replaceFirst("-+$","");
    }

    public static String slug(String value) {
        String slug = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        slug = Normalizer.normalize(slug, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        slug = slug.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug;
    }

    static String extension(String source, String fallback) {
        String value = source == null ? "" : source.trim();
        int dot = value.lastIndexOf('.');
        String extension = dot >= 0 && dot + 1 < value.length() ? value.substring(dot + 1) : "";
        extension = extension.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (extension.isBlank() || extension.length() > 10) {
            extension = fallback == null ? "" : fallback.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        return extension.isBlank() ? "bin" : extension;
    }
}
