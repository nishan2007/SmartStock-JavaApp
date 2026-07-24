package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MacPackagingModulesTest {
    @Test
    void bundledRuntimeIncludesPcscSmartCardModule() throws Exception {
        String script = Files.readString(Path.of("tools/package-macos-release.sh"));
        assertTrue(script.contains("java.smartcardio"),
                "The macOS jpackage runtime must include java.smartcardio for ACR122U login and badge writing");
    }

    @Test
    void bundledRuntimesIncludePostgresScramAuthenticationModules() throws Exception {
        String macScript = Files.readString(Path.of("tools/package-macos-release.sh"));
        String windowsScript = Files.readString(Path.of("tools/package-windows-release.ps1"));

        for (String module : new String[]{
                "java.naming", "java.security.jgss", "java.security.sasl"
        }) {
            assertTrue(macScript.contains(module),
                    "The macOS runtime must include " + module + " for PostgreSQL authentication");
            assertTrue(windowsScript.contains(module),
                    "The Windows runtime must include " + module + " for PostgreSQL authentication");
        }
    }
}
