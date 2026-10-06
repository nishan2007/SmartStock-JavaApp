package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ManualHoursAuthorizationTest {
    @Test void permissionProtectsEmployeeDirectoryCreationAndRetryReplay() throws Exception {
        String source=Files.readString(Path.of("src/services/LanApiServer.java"));
        String directory=source.substring(source.indexOf("private ApiResult manualTimeClockEmployees("),source.indexOf("private ApiResult createManualTimeClock("));
        assertTrue(directory.indexOf("authenticateSession")<directory.indexOf("ManualTimeClockService.employees"));
        assertTrue(directory.contains("requireAnyPermission(c,s.userId(),\"TIME_CLOCK_MANAGEMENT\")"));
        String creation=source.substring(source.indexOf("private ApiResult createManualTimeClock("),source.indexOf("private ApiResult timeClockAutoCloseSettings("));
        int permission=creation.indexOf("requireAnyPermission(c,s.userId(),\"TIME_CLOCK_MANAGEMENT\")");
        assertTrue(permission>=0 && permission<creation.indexOf("loadIdempotentResult"));
        assertTrue(creation.contains("ManualTimeClockService.create(c,employeeId,s.locationId(),entry)"));
        assertTrue(creation.indexOf("auditSecurity")<creation.indexOf("completeIdempotency"));
        assertTrue(creation.indexOf("completeIdempotency")<creation.lastIndexOf("c.commit()"));
    }
}
