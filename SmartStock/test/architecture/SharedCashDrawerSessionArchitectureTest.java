package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedCashDrawerSessionArchitectureTest {
    @Test
    void assignedRegistersUseThePhysicalDrawersExistingSession() throws Exception {
        String service=Files.readString(Path.of("src/services/CashDrawerService.java"));
        String resolver=between(service,
                "public static CashDrawerContext resolveDrawerForDevice",
                "public static CashDrawerContext requireActiveCashSession");
        String activeSession=between(service,
                "public static CashDrawerSession getActiveSessionForDevice",
                "public static CashDrawerSession openSessionForCurrentDevice");

        assertFalse(resolver.contains("cds.device_id = cdda.device_id"),
                "An open session belongs to the shared physical drawer, not only its opening device");
        assertTrue(activeSession.contains("FROM cash_drawer_device_assignments cdda"));
        assertTrue(activeSession.contains("cds.cash_drawer_id = cd.cash_drawer_id"));
        assertTrue(service.contains("lockDrawer(conn,drawer.cashDrawerId())"),
                "Opening must serialize on the physical drawer before rechecking for an existing session");
    }

    private static String between(String source,String start,String end) {
        int from=source.indexOf(start);
        int to=source.indexOf(end,from);
        return source.substring(from,to);
    }
}
