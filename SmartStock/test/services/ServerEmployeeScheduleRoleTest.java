package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerEmployeeScheduleRoleTest {
    @Test void recognizesAdministratorAndOwnerRolesForAutoScheduleDefaults() {
        assertTrue(ServerEmployeeScheduleService.isAdministratorRole("ADMIN"));
        assertTrue(ServerEmployeeScheduleService.isAdministratorRole(" administrator "));
        assertTrue(ServerEmployeeScheduleService.isAdministratorRole("Owner"));
        assertFalse(ServerEmployeeScheduleService.isAdministratorRole("Manager"));
        assertFalse(ServerEmployeeScheduleService.isAdministratorRole(null));
    }
}
