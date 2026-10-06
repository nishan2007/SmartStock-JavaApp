package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontProjectPageTest {
    @Test void metadataAndFallbackUseOnlyEscapedPublicFields(){
        UUID id=UUID.randomUUID();JsonObject project=new JsonObject();
        project.addProperty("title","A <special> sign");project.addProperty("summary","Made for a shop & displayed safely");
        project.addProperty("category","Signs");project.addProperty("privateNote","Do not publish");
        String template="<html><head><meta name=\"description\" content=\"generic\"/><title>Generic</title></head><body><div id=\"root\"></div></body></html>";
        String html=StorefrontProjectPage.render(template,project,"https://www.deckers.gy",2,id);
        assertTrue(html.contains("A &lt;special&gt; sign | Made at Deckers"));
        assertTrue(html.contains("Made for a shop &amp; displayed safely"));
        assertTrue(html.contains("/shop/projects/2/"+id));
        assertTrue(html.contains("application/ld+json"));
        assertFalse(html.contains("privateNote"));assertFalse(html.contains("Do not publish"));
        assertFalse(html.contains("<special>"));
        String sitemap=StorefrontProjectPage.sitemap("https://www.deckers.gy",List.of(StorefrontProjectQr.url("https://www.deckers.gy",2,id)));
        assertTrue(sitemap.contains("<loc>https://www.deckers.gy/shop/projects/2/"+id+"</loc>"));
        assertFalse(sitemap.contains("Do not publish"));
    }
}
