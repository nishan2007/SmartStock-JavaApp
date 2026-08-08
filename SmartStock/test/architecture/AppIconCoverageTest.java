package architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppIconCoverageTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void mainInstallsGlobalWindowAndTaskbarIconHandling() throws Exception {
        String main = read("src/app/Main.java");
        String manager = read("src/ui/helpers/AppIconManager.java");
        assertTrue(main.contains("AppIconManager.install()"));
        assertTrue(manager.contains("/Images/AppIconLight.png"));
        assertTrue(manager.contains("/Images/AppIconDark.png"));
        assertTrue(manager.contains("frame.setIconImages"));
        assertTrue(manager.contains("Taskbar.Feature.ICON_IMAGE"));
    }

    @Test
    void windowsExecutableAndInstallerUseProvidedAppIcon() throws Exception {
        String packaging = read("tools/package-windows-release.ps1");
        assertTrue(packaging.contains("src\\Images\\AppIconLight.png"));
        assertTrue(packaging.contains("--icon $WindowsIcon"));
        assertTrue(packaging.contains("SetupIconFile=$WindowsIcon"));
    }

    @Test
    void macPackagingUsesBothProvidedThemeIcons() throws Exception {
        String packaging = read("tools/package-macos-release.sh");
        assertTrue(packaging.contains("src/Images/AppIconLight.png"));
        assertTrue(packaging.contains("src/Images/AppIconDark.png"));
        assertTrue(packaging.contains("--icon \"$MAC_ICON_PATH\""));
    }
}
