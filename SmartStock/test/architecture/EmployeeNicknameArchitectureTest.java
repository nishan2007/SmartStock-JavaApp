package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeNicknameArchitectureTest {
    @Test
    void optionalNicknameIsStoredAndTransportedWithoutChangingEmployeeIdentity() throws Exception {
        String baseSchema = source("database/v1/local/001_schema.sql");
        String localSyncSchema = source("database/v1/local/001_schema.sql");
        String migration = source("database/v1/local/001_schema.sql");
        String installer = source("src/services/BaseSchemaInstaller.java");
        String sync = source("src/services/ReferenceDataSyncService.java");
        String service = source("src/services/LanEmployeeAdminService.java");
        String screen = source("src/ui/screens/EmployeeManagement.java");

        assertTrue(baseSchema.contains("nickname text"));
        assertTrue(localSyncSchema.contains("CREATE TABLE public.users"));
        assertTrue(migration.contains("nickname text"));
        assertTrue(installer.contains("SchemaContractService.requireLocalReady(connection)"));
        assertTrue(sync.contains("\"users\""));

        assertTrue(service.contains("COALESCE(u.nickname,'')"));
        assertTrue(service.contains("password_cache_invalidated_at,nickname)"));
        assertTrue(service.contains("nickname=?,updated_at=CURRENT_TIMESTAMP"));
        assertTrue(service.contains("String fullName,String nickname,String email"));

        assertTrue(screen.contains("new JLabel(\"Nickname (optional):\")"));
        assertTrue(screen.contains("String nickname = nicknameField.getText().trim()"));
        assertTrue(screen.contains("fullName,nickname,email"));
        assertTrue(screen.contains("nicknameField.setText(\"\")"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
