package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontServicePageTest {
    @Test void threeDPageHasIndexableMetadataAndUsefulFallback(){
        String template="<html><head><title>Store</title><meta name=\"description\" content=\"generic\"></head><body><div id=\"root\"></div></body></html>";
        String html=StorefrontServicePage.render(template,"https://deckers.example","3d-printing");
        assertTrue(html.contains("3D Printing in Guyana | Deckers"));
        assertTrue(html.contains("rel=\"canonical\" href=\"https://deckers.example/shop/services/3d-printing\""));
        assertTrue(html.contains("og:description"));
        assertTrue(html.contains("\"@type\":\"Service\""));
        assertTrue(html.contains("Start a project request"));
        assertFalse(html.contains("content=\"generic\""));
    }
    @Test void eachKnownServiceHasItsOwnCanonicalAndFallback(){
        String template="<html><head><title>Store</title></head><body><div id=\"root\"></div></body></html>";
        assertEquals(14,StorefrontServicePage.slugs().size());
        for(String slug:StorefrontServicePage.slugs()){
            String html=StorefrontServicePage.render(template,"https://deckers.example",slug);
            assertTrue(html.contains("https://deckers.example/shop/services/"+slug));
            assertTrue(html.contains("\"@type\":\"Service\""));
            assertTrue(html.contains("Start a project request"));
        }
        assertThrows(IllegalArgumentException.class,()->StorefrontServicePage.render(template,"https://deckers.example","unknown"));
    }
}
