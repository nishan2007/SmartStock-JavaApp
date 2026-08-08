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

    @Test
    void bundledRuntimesIncludeEllipticCurveTlsProvider() throws Exception {
        String macScript = Files.readString(Path.of("tools/package-macos-release.sh"));
        String windowsScript = Files.readString(Path.of("tools/package-windows-release.ps1"));

        assertTrue(macScript.contains("jdk.crypto.ec"),
                "The macOS runtime must support ECDSA certificates used by Supabase");
        assertTrue(windowsScript.contains("jdk.crypto.ec"),
                "The Windows runtime must support ECDSA certificates used by Supabase");
    }

    @Test
    void windowsReleaseBuildsNativeInstallerWithoutForcingSetupOnUpgrades() throws Exception {
        String windowsScript = Files.readString(Path.of("tools/package-windows-release.ps1"));

        assertTrue(windowsScript.contains("iscc"),
                "The Windows release must compile a native installer");
        assertTrue(windowsScript.contains("PrivilegesRequired=admin"),
                "The all-in-one installer must be able to install under Program Files");
        assertTrue(windowsScript.contains("runasoriginaluser"),
                "SmartStock must launch as the signed-in user after installation");
        assertTrue(windowsScript.contains("Description: \"Launch SmartStock\""),
                "The installer must launch SmartStock normally so saved setup is reused");
        assertTrue(!windowsScript.contains("Launch SmartStock and continue Guided Setup"),
                "An upgrade must not force Guided Setup");
        assertTrue(windowsScript.contains("SmartStock.exe\" --setup-wizard"),
                "The explicit setup launcher must remain available for first-time recovery");
        assertTrue(windowsScript.contains("--add-launcher \"SmartStockServer=$ServerLauncherProperties\""),
                "The background server must have a distinct Task Manager process name");
        assertTrue(windowsScript.contains("arguments=--sync-service"),
                "The named server launcher must always use background service mode");
        assertTrue(windowsScript.contains("--icon $WindowsIcon"),
                "The installed executable and shortcuts must use the SmartStock icon");
        assertTrue(windowsScript.contains("SetupIconFile=$WindowsIcon"),
                "The native Windows installer must use the SmartStock icon");
        assertTrue(windowsScript.contains("[InstallDelete]")
                        && windowsScript.contains("inventory-management-*.jar"),
                "Windows upgrades must remove stale versioned application JARs");
        assertTrue(windowsScript.contains("Split-Path -Parent $IconPath"),
                "The Windows icon builder must recreate its temporary output directory if needed");
        assertTrue(windowsScript.contains("runtime\\bin\\java.exe"),
                "The Windows runtime must include java.exe for the independent updater process");
        assertTrue(windowsScript.contains("runtime\\bin\\javaw.exe"),
                "The Windows runtime must include javaw.exe for the windowless server process");
        assertTrue(!windowsScript.contains("-DSMARTSTOCK_ENVIRONMENT"),
                "The universal Windows installer must honor the environment selected in Guided Setup");
    }
}
