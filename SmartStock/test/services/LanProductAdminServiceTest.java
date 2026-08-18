package services;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanProductAdminServiceTest {
    @Test void everyProductMutationRequiresADeviceIdentity() throws Exception {
        LanProductAdminService.RuleViolation error = assertThrows(
                LanProductAdminService.RuleViolation.class,
                () -> LanProductAdminService.requireDeviceId(null));
        assertEquals("DEVICE_ID_REQUIRED", error.code());
        UUID id = UUID.randomUUID();
        assertEquals(id.toString(), LanProductAdminService.deviceIdText(id));
    }
}
