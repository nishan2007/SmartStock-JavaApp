package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MobileItemLastSavedDetailsArchitectureTest {
    @Test void newProductCanReuseLastSuccessfullySavedClassification()throws Exception{
        String html=Files.readString(Path.of("src/mobile-web/index.html"));
        String js=Files.readString(Path.of("src/mobile-web/app.js"));
        assertTrue(html.contains("id=\"useLastSaved\""));
        assertTrue(html.contains("Use Last Saved Details"));
        assertTrue(js.contains("smartstock.lastSavedProductDetails"));
        assertTrue(js.contains("rememberLastSavedProductDetails(f)"));
        assertTrue(js.contains("applyLastSavedProductDetails"));
        assertTrue(js.contains("details.itemTypeName"));
        assertTrue(js.contains("details.brandName"));
        assertTrue(js.contains("details.shelfName"));
        assertTrue(js.contains("refreshItemTypes()"));
    }
}
