package utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureCredentialStoreTest {
    @Test
    void windowsCredentialHelperDoesNotInheritPowerShellCoreModulePath() {
        ProcessBuilder builder = new ProcessBuilder("powershell.exe");
        builder.environment().put("PSModulePath", "C:\\incompatible\\powershell-core-modules");

        SecureCredentialStore.sanitizeWindowsPowerShellEnvironment(builder);

        assertFalse(builder.environment().keySet().stream()
                .anyMatch(key -> "PSModulePath".equalsIgnoreCase(key)));
    }

    @Test
    void buildsNonInteractiveWindowsCredentialHelper() {
        ProcessBuilder builder = SecureCredentialStore.windowsPowerShell("Write-Output ok");

        assertTrue(builder.command().contains("-NoProfile"));
        assertTrue(builder.command().contains("-NonInteractive"));
    }
}
