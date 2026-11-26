package ui.screens;

import org.junit.jupiter.api.Test;
import services.LanApiClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductImageCoverageDialogTest {
    @Test void sharedPendingAndRejectedDraftsNeverBecomeApprovedCoverage() {
        var product=new LanApiClient.StudioProduct(1,"Example","main",List.of("approved"));
        assertEquals("Needs ad · Awaiting ad review",ProductImageCoverageDialog.adReviewStatus(product,null,"pending"));
        assertEquals("Needs ad · Draft rejected",ProductImageCoverageDialog.adReviewStatus(product,null,"rejected"));
        var approved=new LanApiClient.ImageCoverage(1,"ad_square","approved",1080,1080);
        assertEquals("Done · Additional photo · Awaiting ad review",ProductImageCoverageDialog.adReviewStatus(product,approved,"pending"));
    }
    @Test
    void countsEveryMainAndGalleryPhotoAndTracksPromotedAiPhoto() {
        var product = new LanApiClient.StudioProduct(363, "Jumbo Crayons", "new-main",
                List.of("original", "previous-ai-main"));
        var review = new LanApiClient.StudioReview(363, "Jumbo Crayons", "original", "hash",
                "imported", "previous-ai-main", null, "codex-imagegen", true, 3);

        assertEquals(3, ProductImageCoverageDialog.photoCount(product));
        assertEquals("Done · Additional photo", ProductImageCoverageDialog.photoStatus(product, review));
        assertEquals("Done · Main photo", ProductImageCoverageDialog.photoStatus(
                new LanApiClient.StudioProduct(363, "Jumbo Crayons", "previous-ai-main", List.of("original")), review));
    }

    @Test
    void includesProductsWithoutPhotosAndFlagsMissingImportedReference() {
        var empty = new LanApiClient.StudioProduct(5, "No photo", null, List.of());
        assertEquals(0, ProductImageCoverageDialog.photoCount(empty));
        assertEquals("Needs AI photo", ProductImageCoverageDialog.photoStatus(empty, null));

        var imported = new LanApiClient.StudioReview(5, "No photo", "original", "hash",
                "imported", "missing", null, "codex-imagegen", true, 3);
        assertEquals("Photo no longer in gallery", ProductImageCoverageDialog.photoStatus(empty, imported));
    }
}
