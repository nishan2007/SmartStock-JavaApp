package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontProductPageTest {
    @Test void publishedProductHasEscapedMetadataAndCurrentOffer(){
        JsonObject product=new JsonObject();product.addProperty("name","A&B <Cards>");product.addProperty("description","Cards for \"teams\" & events");
        product.addProperty("sku","SKU-1");product.addProperty("price",125.50);product.addProperty("image","available");
        product.addProperty("availability","In Stock");product.addProperty("canOrder",true);
        JsonObject settings=new JsonObject();settings.addProperty("currency","GYD");
        String template="<html><head><title>Store</title><meta name=\"description\" content=\"generic\"></head><body><div id=\"root\"></div></body></html>";
        String html=StorefrontProductPage.render(template,product,settings,"https://deckers.example",2,17);
        assertTrue(html.contains("A&amp;B &lt;Cards&gt; | Deckers"));
        assertTrue(html.contains("https://deckers.example/shop/products/2/17"));
        assertTrue(html.contains("\"@type\":\"Product\""));
        assertTrue(html.contains("https://schema.org/InStock"));
        assertTrue(html.contains("GYD 125.5"));
        assertFalse(html.contains("content=\"generic\""));
        assertFalse(html.contains("<Cards>"));
    }
}
