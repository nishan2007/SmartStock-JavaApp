package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.sql.Timestamp;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeWebCorrectionTest {
    private JsonObject valid() {
        JsonObject b=new JsonObject();b.addProperty("clockIn","2026-10-20T08:00:00");b.addProperty("clockOut","2026-10-20T17:00:00");return b;
    }
    @Test void existingManagementPermissionIsRequiredInAdditionToWebAccess() {
        assertFalse(EmployeeWebCorrectionService.canEdit(Set.of("ACCESS_EMPLOYEE_WEB","VIEW_ALL_EMPLOYEE_WEB")));
        assertFalse(EmployeeWebCorrectionService.canEdit(Set.of("TIME_CLOCK_MANAGEMENT")));
        assertTrue(EmployeeWebCorrectionService.canEdit(Set.of("ACCESS_EMPLOYEE_WEB","TIME_CLOCK_MANAGEMENT")));
    }
    @Test void blankAndOmittedReasonsBecomeNAWhileProvidedReasonsArePreserved()throws Exception {
        JsonObject b=valid();assertEquals("N/A",EmployeeWebCorrectionService.correction(b).reason());
        b.addProperty("reason"," \t ");assertEquals("N/A",EmployeeWebCorrectionService.correction(b).reason());
        b.addProperty("reason","  Missed lunch punch  ");assertEquals("Missed lunch punch",EmployeeWebCorrectionService.correction(b).reason());
    }
    @Test void existingOrderingAndPairValidationRemainEnforced() {
        JsonObject b=valid();b.addProperty("clockOut","2026-10-20T07:00:00");
        assertEquals(400,assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebCorrectionService.correction(b)).status);
        b.addProperty("clockOut","2026-10-20T17:00:00");b.addProperty("lunchStart","2026-10-20T12:00:00");
        assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebCorrectionService.correction(b));
        b.remove("lunchStart");b.addProperty("clockIn","bad-date");
        assertEquals(400,assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebCorrectionService.correction(b)).status);
    }
    @Test void revisionsDetectBothPunchAndMetadataChanges() {
        Timestamp a=Timestamp.from(java.time.Instant.parse("2026-10-20T12:00:00.123456Z"));
        Timestamp b=Timestamp.from(java.time.Instant.parse("2026-10-20T12:00:00.123457Z"));
        assertNotEquals(EmployeeWebCorrectionService.revision(1,a,null),EmployeeWebCorrectionService.revision(1,b,null));
        assertNotEquals(EmployeeWebCorrectionService.revision(1,a,a),EmployeeWebCorrectionService.revision(1,a,b));
        assertNotEquals(EmployeeWebCorrectionService.revision(1,a),EmployeeWebCorrectionService.revision(2,a));
    }
}
