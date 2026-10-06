package services;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CatalogPhotoGalleryServiceTest {
    @Test void preservesPhotoOrderWithoutRepeatingThePrimary() throws Exception {
        assertEquals(List.of("second", "third"), CatalogPhotoGalleryService.normalize("first",
                List.of(" second ", "first", "third", "second")));
    }

    @Test void rejectsTooManyPhotos() {
        List<String> photos = new ArrayList<>();
        for (int i = 0; i < 21; i++) photos.add("photo-" + i);
        assertThrows(SQLException.class, () -> CatalogPhotoGalleryService.normalize("primary", photos));
    }

    @Test void promotionKeepsTheOldPrimaryAndExistingGalleryInOrder() throws Exception {
        assertEquals(List.of("side-one", "side-two", "old-primary"),
                CatalogPhotoGalleryService.afterPromotion("old-primary", "new-primary",
                        List.of("side-one", "new-primary", "side-two")));
    }
}
