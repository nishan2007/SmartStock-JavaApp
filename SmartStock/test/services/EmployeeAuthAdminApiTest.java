package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeAuthAdminApiTest {
    @Test
    void extractsCurrentSupabaseAuthErrorShapes() {
        assertEquals("User already registered",
                LanApiServer.employeeAuthError("{\"message\":\"User already registered\"}"));
        assertEquals("Email is invalid",
                LanApiServer.employeeAuthError("{\"msg\":\"Email is invalid\"}"));
        assertEquals("Employee Auth synchronization failed.",
                LanApiServer.employeeAuthError("not-json"));
        assertTrue(LanApiServer.isEmployeeAuthConflict("A user with this email already exists"));
        assertFalse(LanApiServer.isEmployeeAuthConflict("Password must contain more characters"));
    }

    @Test
    void employeeAuthUsesServerSideAdminApiInsteadOfMissingEdgeFunctions() throws Exception {
        String source = Files.readString(Path.of("src/services/LanApiServer.java"));

        assertTrue(source.contains("/auth/v1/admin/users"));
        assertTrue(source.contains("ServerSupabaseCredentials.applyTo(request)"));
        assertFalse(source.contains("/functions/v1/create-employee-auth-user"));
        assertFalse(source.contains("/functions/v1/update-employee-auth-user"));
        assertFalse(source.contains("/functions/v1/delete-employee-auth-user"));
    }

    @Test
    void blankEmployeeEmailUsesAStableUniqueSharedMailboxAlias() throws Exception {
        String first = LanApiServer.employeeAuthEmail("", "John.Doe", "manager+reports@deckers.example.com");
        String again = LanApiServer.employeeAuthEmail(null, "John.Doe", "manager@deckers.example.com");
        String second = LanApiServer.employeeAuthEmail("", "Jane.Doe", "manager@deckers.example.com");

        assertEquals(first, again);
        assertTrue(first.startsWith("manager+smartstock-johndoe-"));
        assertTrue(first.endsWith("@deckers.example.com"));
        assertNotEquals(first, second);
        assertEquals("employee@personal.example.com",
                LanApiServer.employeeAuthEmail(" Employee@Personal.Example.com ", "ignored", "manager@deckers.example.com"));
    }

    @Test
    void employeeScreenNoLongerRequiresEmail() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/EmployeeManagement.java"));
        assertTrue(source.contains("Email (optional):"));
        assertFalse(source.contains("missing.add(\"Email\")"));
    }
}
