package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LocalServerRepairService {
    private LocalServerRepairService() {
    }

    public static RepairResult repairAndSync() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER) {
            throw new IllegalStateException("Switch Mode to SERVER before running Repair/Sync Local Server.");
        }

        StringBuilder log = new StringBuilder();
        Path installer = findInstaller();
        if (installer != null) {
            PostgresRuntimeService.CommandResult installerResult = PostgresRuntimeService.runInstallerRepair(installer, "server");
            appendSection(log, "Installer Repair", installerResult.output());
            if (!installerResult.success()) {
                throw new IllegalStateException("Installer repair failed.\n\n" + installerResult.output());
            }
        } else {
            PostgresRuntimeService.CommandResult runtimeResult = PostgresRuntimeService.installOrUpdateRuntime();
            appendSection(log, "Runtime Repair", runtimeResult.output());
            if (!runtimeResult.success()) {
                throw new IllegalStateException("Runtime repair failed.\n\n" + runtimeResult.output());
            }
        }

        ServerProvisioningService.ProvisionResult provision = ServerProvisioningService.provision(DatabaseConfig.load());
        appendSection(log, "Provision And Pull", provision.message());

        SyncWorker.runOnceSafely();
        SyncWorker.SyncStatus status = SyncWorker.latestStatus();
        appendSection(log, "Sync Pass", """
                Cloud reachable: %s
                Message: %s
                Last pushed: %d
                Pending events: %d
                Failed events: %d
                Open conflicts: %d
                Last error: %s
                """.formatted(
                status.cloudReachable() ? "yes" : "no",
                blankToDash(status.message()),
                status.lastPushed(),
                status.pendingCount(),
                status.failedCount(),
                status.conflictCount(),
                blankToDash(status.lastError())
        ));

        return new RepairResult(log.toString().trim(), status);
    }

    private static Path findInstaller() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path path = current; path != null; path = path.getParent()) {
            Path candidate = path.resolve("installer/macos/install.command");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            Path moduleCandidate = path.resolve("SmartStock/installer/macos/install.command");
            if (Files.isRegularFile(moduleCandidate)) {
                return moduleCandidate;
            }
        }
        return null;
    }

    private static void appendSection(StringBuilder log, String title, String body) {
        if (!log.isEmpty()) {
            log.append("\n\n");
        }
        log.append("== ").append(title).append(" ==\n");
        log.append(body == null || body.isBlank() ? "(no output)" : body.trim());
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record RepairResult(String message, SyncWorker.SyncStatus syncStatus) {
    }
}
