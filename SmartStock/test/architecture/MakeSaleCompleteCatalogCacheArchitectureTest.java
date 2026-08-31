package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MakeSaleCompleteCatalogCacheArchitectureTest {
    @Test
    void emptyCatalogRequestIsNotLimitedToInteractiveSearchResultSize() throws Exception {
        String api=Files.readString(Path.of(System.getProperty("user.dir"))
                .resolve("src/services/LanApiServer.java"));

        assertTrue(api.contains("String resultLimit = searchText.isBlank() ? \"\" : \"LIMIT 250\";"),
                "The cache-warming empty search must load the complete catalog.");
        assertTrue(api.contains("catalogOrder, resultLimit"));
    }
}
