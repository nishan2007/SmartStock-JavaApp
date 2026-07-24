package services;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImageAssetServiceTest {
    @Test
    void stableReferenceRoundTripsUuid() {
        UUID id = UUID.randomUUID();
        String reference = ImageAssetReference.PREFIX + id;

        assertTrue(ImageAssetReference.isAssetReference(reference));
        assertEquals(id, ImageAssetReference.assetId(reference));
    }

    @Test
    void rejectsMalformedOrRemoteValuesAsAssetReferences() {
        assertFalse(ImageAssetReference.isAssetReference(null));
        assertFalse(ImageAssetReference.isAssetReference(""));
        assertFalse(ImageAssetReference.isAssetReference("https://example.test/image.png"));
        assertFalse(ImageAssetReference.isAssetReference(ImageAssetReference.PREFIX + "../escape"));
        assertThrows(IllegalArgumentException.class, () -> ImageAssetReference.assetId("not-an-asset"));
    }
}
