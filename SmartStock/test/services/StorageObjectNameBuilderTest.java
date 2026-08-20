package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageObjectNameBuilderTest {
    @Test
    void buildsDescriptiveProductAndVariantNames() {
        assertEquals(
                "classic-tee-nike-shirt-large-red-product-image-1700000000000.jpg",
                StorageObjectNameBuilder.filename("optimized.JPG", "jpg", "1700000000000",
                        "Classic Tee", "Nike", "Shirt", "Large", "Red", "product image"));
    }

    @Test void productNamesAlwaysContainEveryRequiredSegment() {
        assertEquals("water-unbranded-item-standard-standard-product-image-1700000000000.jpg",
                StorageObjectNameBuilder.productImageFilename("photo.jpg", "1700000000000",
                        "Water", "", "", "", ""));
    }

    @Test
    void buildsEmployeePhotoAndDocumentNames() {
        assertEquals(
                "jane-doe-employee-photo-1700000000001.webp",
                StorageObjectNameBuilder.filename("employee-photo.webp", "jpg", "1700000000001",
                        "Jane Doe", "employee photo"));
        assertEquals(
                "jane-doe-id-card-document-1700000000002.pdf",
                StorageObjectNameBuilder.filename("passport scan.PDF", "bin", "1700000000002",
                        "Jane Doe", "ID card document"));
    }

    @Test
    void removesUnsafePathCharactersAndKeepsNamesBounded() {
        String filename = StorageObjectNameBuilder.filename("../../bad/name.png", "png", "same token",
                "../../ACME / Product", "Men's & Women's", "product image");
        assertFalse(filename.contains("/"));
        assertFalse(filename.contains(".."));
        assertTrue(filename.endsWith("-same-token.png"));
        assertTrue(filename.length() < 240);
        assertEquals("jose-nunez", StorageObjectNameBuilder.slug("José Núñez"));
    }
}
