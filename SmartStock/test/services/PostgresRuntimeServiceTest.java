package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgresRuntimeServiceTest {
    @Test
    void buildsLaunchersForTheCurrentlyPackagedJar() {
        Path macAppDir = Path.of("/Users/test/.smartstock/sync-service/app");
        assertEquals("#!/usr/bin/env bash\nset -euo pipefail\n"
                        + "cd '/Users/test/.smartstock/sync-service/app'\n"
                        + "exec java -jar 'inventory-management-1.0.11.jar' --sync-service\n",
                PostgresRuntimeService.installedSyncLauncherContent(
                        false, macAppDir, "inventory-management-1.0.11.jar"));

        Path windowsAppDir = Path.of("C:\\Users\\test\\.smartstock\\sync-service\\app");
        assertEquals("@echo off\r\ncd /d \"C:\\Users\\test\\.smartstock\\sync-service\\app\"\r\n"
                        + "java -jar \"inventory-management-1.0.11.jar\" --sync-service\r\n",
                PostgresRuntimeService.installedSyncLauncherContent(
                        true, windowsAppDir, "inventory-management-1.0.11.jar"));
    }
}
