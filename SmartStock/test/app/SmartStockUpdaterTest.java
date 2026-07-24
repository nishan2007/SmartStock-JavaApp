package app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartStockUpdaterTest {
    private static final Path PLIST = Path.of("/Users/test/Library/LaunchAgents/com.smartstock.sync.plist");

    @Test
    void buildsMacLaunchAgentLifecycleCommands() {
        assertEquals(
                List.of("/bin/launchctl", "bootout", "gui/501", PLIST.toString()),
                SmartStockUpdater.macLaunchctlCommand("bootout", "501", PLIST, "com.smartstock.sync"));
        assertEquals(
                List.of("/bin/launchctl", "bootstrap", "gui/501", PLIST.toString()),
                SmartStockUpdater.macLaunchctlCommand("bootstrap", "501", PLIST, "com.smartstock.sync"));
        assertEquals(
                List.of("/bin/launchctl", "kickstart", "-k", "gui/501/com.smartstock.sync"),
                SmartStockUpdater.macLaunchctlCommand("kickstart", "501", PLIST, "com.smartstock.sync"));
    }

    @Test
    void rejectsUnknownLaunchctlActions() {
        assertThrows(IllegalArgumentException.class,
                () -> SmartStockUpdater.macLaunchctlCommand("remove", "501", PLIST, "com.smartstock.sync"));
    }

    @Test
    void usesAbsoluteMacOpenCommandForRelaunch() {
        Path app = Path.of("/Applications/SmartStock.app");
        assertEquals(List.of("/usr/bin/open", "-n", app.toString()),
                SmartStockUpdater.macOpenCommand(app));
        assertEquals(List.of("/usr/bin/xattr", "-rc", app.toString()),
                SmartStockUpdater.macXattrCommand(app));
        assertEquals(List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", app.toString()),
                SmartStockUpdater.macCodesignVerifyCommand(app));
    }

    @Test
    void rewritesBackgroundSyncLaunchersForUpdatedJar() {
        Path macAppDir = Path.of("/Users/test/.smartstock/sync-service/app");
        assertEquals("#!/usr/bin/env bash\nset -euo pipefail\n"
                        + "cd '/Users/test/.smartstock/sync-service/app'\n"
                        + "exec java -jar 'inventory-management-1.0.11.jar' --sync-service\n",
                SmartStockUpdater.syncLauncherContent(false, macAppDir, "inventory-management-1.0.11.jar"));

        Path windowsAppDir = Path.of("C:\\Users\\test\\.smartstock\\sync-service\\app");
        assertEquals("@echo off\r\ncd /d \"C:\\Users\\test\\.smartstock\\sync-service\\app\"\r\n"
                        + "java -jar \"inventory-management-1.0.11.jar\" --sync-service\r\n",
                SmartStockUpdater.syncLauncherContent(true, windowsAppDir, "inventory-management-1.0.11.jar"));
    }

    @Test
    void eachUpdateReplacesThePreviousRollback(@TempDir Path tempDir) throws Exception {
        Path rollback = tempDir.resolve("rollback");
        Files.createDirectories(rollback);
        Files.writeString(rollback.resolve("older-copy.txt"), "old");

        SmartStockUpdater.prepareSingleRollbackDirectory(rollback);

        assertTrue(Files.isDirectory(rollback));
        assertFalse(Files.exists(rollback.resolve("older-copy.txt")));
        try (var files = Files.list(rollback)) {
            assertEquals(0, files.count());
        }
    }
}
