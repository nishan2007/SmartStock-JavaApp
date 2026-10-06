package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontEditorialPageTest {
    @Test void discoveryPagesHaveDistinctIndexableMetadata(){
        String template="<html><head><title>Store</title><meta name=\"description\" content=\"generic\"></head><body><div id=\"root\"></div></body></html>";
        assertEquals(3,StorefrontEditorialPage.paths().size());
        for(String path:StorefrontEditorialPage.paths()){
            String html=StorefrontEditorialPage.render(template,"https://deckers.example",path);
            assertTrue(html.contains("rel=\"canonical\" href=\"https://deckers.example/shop/"+path+"\""));
            assertTrue(html.contains("og:description"));
            assertTrue(html.contains("<h1>"));
            assertFalse(html.contains("content=\"generic\""));
        }
        assertThrows(IllegalArgumentException.class,()->StorefrontEditorialPage.render(template,"https://deckers.example","unknown"));
    }
}
