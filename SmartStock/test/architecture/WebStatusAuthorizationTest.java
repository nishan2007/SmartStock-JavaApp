package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WebStatusAuthorizationTest {
    @Test void webMutationsRequireAuthenticatedLocalPrimaryServerAndAudit() throws Exception {
        String source=Files.readString(Path.of("src/services/LanApiServer.java"));
        String control=source.substring(source.indexOf("private synchronized ApiResult webControl("),source.indexOf("private ApiResult storefrontAdmin("));
        assertTrue(control.contains("authenticateDevice"));assertTrue(control.contains("authenticateSession"));
        assertTrue(control.contains("requireMobileControl"));assertTrue(control.contains("ServerRoleGuard.State.PRIMARY"));
        assertTrue(control.contains("IDEMPOTENCY_CONFLICT"));assertTrue(control.contains("WEB_CONTROL_REQUESTED"));
        assertTrue(control.contains("WEB_CONTROL_COMPLETED"));assertTrue(control.contains("EMPLOYEE_MANAGEMENT"));
        assertTrue(RemoteAdminSource().contains("\"/v1/web/mutation\""));
    }
    private static String RemoteAdminSource()throws Exception{return Files.readString(Path.of("src/services/RemoteAdminPolicy.java"));}
}
