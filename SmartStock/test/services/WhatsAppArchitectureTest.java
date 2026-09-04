package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WhatsAppArchitectureTest {
    @Test void normalizesInternationalNumbers() throws Exception {
        assertEquals("5926123456", ServerWhatsAppService.normalizePhone("+592 612-3456"));
        assertThrows(ServerWhatsAppService.Rule.class, () -> ServerWhatsAppService.normalizePhone("6123"));
    }

    @Test void tokenIsServerOnlyAndLanRouteIsAuthenticated() throws Exception {
        String server=Files.readString(Path.of("src/services/ServerWhatsAppService.java"));
        String client=Files.readString(Path.of("src/services/LanApiClient.java"));
        assertTrue(server.contains("SMARTSTOCK_WHATSAPP_ACCESS_TOKEN"));
        assertFalse(client.contains("SMARTSTOCK_WHATSAPP_ACCESS_TOKEN"));
        assertTrue(client.contains("post(\"/v1/whatsapp/send\",copy(body),true,false"));
    }

    @Test void schemaRecordsConsentAndKeepsOutboxLocal() throws Exception {
        String migration=Files.readString(Path.of("database/migrations/v1_after/20260903120000_whatsapp_sales_documents.sql"));
        String cloud=Files.readString(Path.of("database/v1/cloud/001_schema.sql"));
        assertTrue(migration.contains("whatsapp_consent_phone"));
        assertTrue(migration.contains("ENABLE ROW LEVEL SECURITY"));
        assertFalse(cloud.contains("CREATE TABLE IF NOT EXISTS public.whatsapp_outbox"));
    }
}
