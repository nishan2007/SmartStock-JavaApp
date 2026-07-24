package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeeNicknameArchitectureTest {
    @Test
    void optionalNicknameIsStoredAndTransportedWithoutChangingEmployeeIdentity() throws Exception {
        String baseSchema = source("database/base_schema_setup.sql");
        String localSyncSchema = source("database/local_network_sync_setup.sql");
        String migration = source("database/migrations/20260723013000_add_employee_nickname.sql");
        String installer = source("src/services/BaseSchemaInstaller.java");
        String sync = source("src/services/ReferenceDataSyncService.java");
        String service = source("src/services/LanEmployeeAdminService.java");
        String screen = source("src/ui/screens/EmployeeManagement.java");

        assertTrue(baseSchema.contains("ADD COLUMN IF NOT EXISTS nickname TEXT"));
        assertTrue(localSyncSchema.contains("nickname TEXT"));
        assertTrue(migration.contains("ADD COLUMN IF NOT EXISTS nickname TEXT"));
        assertTrue(installer.contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT"));
        assertTrue(sync.contains("ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname TEXT"));

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
