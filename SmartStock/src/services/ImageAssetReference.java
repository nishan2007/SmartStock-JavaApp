package services;

import java.util.UUID;

/** Credential-free stable image reference shared by registers and the server. */
public final class ImageAssetReference {
    public static final String PREFIX = "smartstock-asset:";

    private ImageAssetReference() { }

    public static boolean isAssetReference(String value) {
        if (value == null || !value.startsWith(PREFIX)) return false;
        try {
            UUID.fromString(value.substring(PREFIX.length()));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public static UUID assetId(String reference) {
        if (!isAssetReference(reference)) {
            throw new IllegalArgumentException("Invalid SmartStock image reference.");
        }
        return UUID.fromString(reference.substring(PREFIX.length()));
    }

    public static String format(UUID assetId) {
        if (assetId == null) throw new IllegalArgumentException("Image asset ID is required.");
        return PREFIX + assetId;
    }
}
