package services;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerWebSecurityTest {
    @Test void failedSignInIsReservedOnlyOnce() throws Exception {
        String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/services/SchedulerWebServer.java"));
        assertFalse(source.contains("failAttempt("),"Failures must not add a second reservation");
        assertEquals(1,source.split("recordAttempt\\(c,key,address,false\\)",-1).length-1);
        assertTrue(source.contains("rateLimit(c,key,address);recordAttempt(c,key,address,false);c.commit();"));
    }
    @Test void passwordOnlyModeAcceptsAuthenticatedSessionsButNotInvalidAssurance() throws Exception {
        SchedulerWebServer.requireAssurance(false,"aal1");
        SchedulerWebServer.requireAssurance(false,"aal2");
        assertThrows(SchedulerWebServer.WebError.class,()->SchedulerWebServer.requireAssurance(false,null));
        assertThrows(SchedulerWebServer.WebError.class,()->SchedulerWebServer.requireAssurance(false,"invalid"));
        assertThrows(SchedulerWebServer.WebError.class,()->SchedulerWebServer.requireAssurance(true,"aal1"));
        SchedulerWebServer.requireAssurance(true,"aal2");
    }
    @Test void onlyAal2CanCreateABrowserSession() throws Exception {
        SchedulerWebServer.requireAal2("aal2");
        for(String value:new String[]{null,"","aal1","AAL2","aal3"}) {
            var e=assertThrows(SchedulerWebServer.WebError.class,()->SchedulerWebServer.requireAal2(value));
            assertEquals(401,e.status);assertEquals("MFA_REQUIRED",e.code);
        }
    }
    @Test void csrfMustMatchTheStoredHash() throws Exception {
        String token="test-csrf-value",hash=LanSecurity.sha256(token);
        SchedulerWebServer.validateCsrf(token,hash);
        for(String value:new String[]{null,"",hash,"different"}) {
            var e=assertThrows(SchedulerWebServer.WebError.class,()->SchedulerWebServer.validateCsrf(value,hash));
            assertEquals(403,e.status);
        }
    }
    @Test void bothExpiryLimitsAreStrict() {
        Instant now=Instant.parse("2026-08-30T00:00:00Z");
        assertTrue(SchedulerWebServer.sessionAlive(now.plusSeconds(900),now.plusSeconds(28800),now));
        assertFalse(SchedulerWebServer.sessionAlive(now,now.plusSeconds(1),now));
        assertFalse(SchedulerWebServer.sessionAlive(now.plusSeconds(1),now,now));
        assertFalse(SchedulerWebServer.sessionAlive(now.minusSeconds(1),now.plusSeconds(100),now));
    }
    @Test void editCapabilityIsIndependentFromReadOnlyWebAccess() {
        assertFalse(SchedulerWebServer.canEdit(Set.of("ACCESS_SCHEDULER_WEB", "VIEW_EMPLOYEE_SCHEDULE")));
        assertTrue(SchedulerWebServer.canEdit(Set.of("ACCESS_SCHEDULER_WEB", "VIEW_EMPLOYEE_SCHEDULE", "EDIT_EMPLOYEE_SCHEDULE")));
        assertFalse(SchedulerWebServer.canEdit(null));
    }
    @Test void databaseErrorsNeverExposeSqlOrCredentials() {
        String sensitive="jdbc:postgresql://private-host/password=secret SELECT * FROM users";
        for(String state:new String[]{null,"23505","40001","40P01","55P03","08006","22007"}) {
            var e=SchedulerWebServer.publicError(new SQLException(sensitive,state));
            assertFalse(e.getMessage().contains("private-host"));assertFalse(e.getMessage().contains("secret"));
            assertFalse(e.getMessage().contains("SELECT"));
        }
        assertEquals(409,SchedulerWebServer.publicError(new SQLException(sensitive,"40001")).status);
        assertEquals(503,SchedulerWebServer.publicError(new SQLException(sensitive,"08006")).status);
        assertEquals(400,SchedulerWebServer.publicError(new SQLException(sensitive,"22007")).status);
    }
}
