package services;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontProjectQrTest {
    @Test void onlyPublishedSnapshotProjectsGetStoreSpecificCodes()throws Exception{
        UUID published=UUID.randomUUID(),draft=UUID.randomUUID();
        var snapshot=JsonParser.parseString("{\"projects\":[{\"id\":\""+published+"\"}]} ").getAsJsonObject();
        assertTrue(StorefrontProjectQr.published(snapshot,published));
        assertFalse(StorefrontProjectQr.published(snapshot,draft));
        String url=StorefrontProjectQr.url("https://www.deckers.gy",2,published);
        assertEquals("https://www.deckers.gy/shop/projects/2/"+published,url);
        String svg=new String(StorefrontProjectQr.svg(url),StandardCharsets.UTF_8);
        assertTrue(svg.startsWith("<svg "));
        assertTrue(svg.contains("<path "));
        assertFalse(svg.contains("http://www.w3.org/1999/xhtml"));
    }
}
