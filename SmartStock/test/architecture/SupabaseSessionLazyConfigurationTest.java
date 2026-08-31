package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupabaseSessionLazyConfigurationTest {
    @Test
    void lanLoginSessionOperationsDoNotInitializeProductionSupabaseConfiguration() throws Exception {
        String manager = Files.readString(Path.of("src/managers/SupabaseSessionManager.java"));
        String login = Files.readString(Path.of("src/ui/screens/Login.java"));

        assertFalse(manager.contains("private static final SupabaseProjectConfig SUPABASE_CONFIG"));
        assertTrue(manager.contains("private static SupabaseProjectConfig requireConfig()"));
        assertTrue(login.contains("catch (Exception | LinkageError ex)"));
    }
}
