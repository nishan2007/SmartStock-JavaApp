package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerWebValidationTest {
    @Test void preservesPasswordWhitespaceExactly() throws Exception {
        JsonObject b=new JsonObject();b.addProperty("password","  sensitive password  ");
        assertEquals("  sensitive password  ",SchedulerWebServer.password(b));
        b.addProperty("password","");assertThrows(Exception.class,()->SchedulerWebServer.password(b));
    }
    @Test void originMustBeAnHttpsOriginWithoutCredentialsOrPaths() {
        assertEquals("https://scheduler.example.com",SchedulerWebServer.cleanOrigin("https://scheduler.example.com/"));
        for(String value:new String[]{"http://scheduler.example.com","https://user:pass@example.com","https://example.com/other","https://example.com/?query=1","https://example.com/#fragment"})
            assertNull(SchedulerWebServer.cleanOrigin(value),value);
    }
    @Test void clearRequiresExactBoundedPeriodConfirmation() throws Exception {
        LocalDate start=LocalDate.of(2026,8,24),end=start.plusDays(6);JsonObject b=new JsonObject();
        assertThrows(Exception.class,()->SchedulerWebServer.validateClear(b,start,end));
        b.addProperty("confirmation",start+"/"+end);SchedulerWebServer.validateClear(b,start,end);
        assertThrows(Exception.class,()->SchedulerWebServer.validateClear(b,start,end.plusDays(1)));
        assertThrows(Exception.class,()->SchedulerWebServer.validateRange(start,start.minusDays(1)));
        assertThrows(Exception.class,()->SchedulerWebServer.validateRange(start,start.plusDays(31)));
        SchedulerWebServer.validateRange(start,start.plusDays(30));
    }
    @Test void rawMfaSvgIsConvertedToImageData() {
        String svg="<svg xmlns='http://www.w3.org/2000/svg'/>";
        String value=SchedulerWebServer.qrData(svg);
        assertTrue(value.startsWith("data:image/svg+xml;base64,"));
        assertEquals(svg,new String(Base64.getDecoder().decode(value.substring(value.indexOf(',')+1)),StandardCharsets.UTF_8));
    }
    @Test void tunnelBinaryFailsClosedWhenMissingOrTampered(@TempDir Path dir) throws Exception {
        Path binary=dir.resolve("cloudflared.exe");
        assertThrows(Exception.class,()->CloudflareBinary.verify(binary,CloudflareBinary.WINDOWS_SHA256));
        Files.writeString(binary,"not the verified executable");
        assertThrows(Exception.class,()->CloudflareBinary.verify(binary,CloudflareBinary.WINDOWS_SHA256));
    }
}
