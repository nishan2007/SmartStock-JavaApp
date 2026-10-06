package app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class SmartStockUpdater {
    private static boolean serverRestartAttempted;
    private static String serviceRecovery = "Background server recovery was not attempted.";
    private static String installationRecovery = "Check the installed application; recovery status is in the log.";
    private SmartStockUpdater() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Missing update manifest path.");
            System.exit(2);
        }
        try {
            Path manifest = Path.of(args[0]);
            if (Files.exists(manifest.resolveSibling("updater.cancelled"))) return;
            Files.writeString(manifest.resolveSibling("updater.started"), Long.toString(ProcessHandle.current().pid()));
            log("Updater started.");
            Properties display = new Properties();
            try (InputStream input = Files.newInputStream(manifest)) { display.load(input); }
            try { UpdaterProgress.open(display.getProperty("version", "")); }
            catch (Exception uiError) { log("Progress window unavailable: " + rootMessage(uiError)); }
            UpdaterProgress.stage("Preparing update");
            Thread.sleep(1800);
            if (Files.exists(manifest.resolveSibling("updater.cancelled"))) { UpdaterProgress.close(); return; }
            apply(Path.of(args[0]));
            log("Updater completed.");
            UpdaterProgress.close();
        } catch (Exception ex) {
            log("Updater failed: " + ex.getClass().getName() + ": " + rootMessage(ex));
            ex.printStackTrace();
            String recovery = "Recovery could not be completed. See the updater log.";
            UpdaterProgress.stage("Recovering after update failure");
            try {
                restartServiceAfterFailure(Path.of(args[0]));
                recovery = installationRecovery + " " + relaunchAfterFailure(Path.of(args[0]))
                        + " " + serviceRecovery;
            } catch (Exception relaunchError) {
                log("Updater recovery relaunch failed: " + rootMessage(relaunchError));
                relaunchError.printStackTrace();
                recovery = installationRecovery + " Reopening failed: " + rootMessage(relaunchError);
            }
            if (!UpdaterProgress.failure(rootMessage(ex), recovery,
                    Path.of(System.getProperty("user.home"), ".smartstock", "updates", "updater.log")))
                System.exit(1);
        }
    }

    private static void apply(Path manifestPath) throws Exception {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(manifestPath)) {
            props.load(input);
        }

        Path appDir = Path.of(required(props, "app.dir"));
        Path releaseZip = Path.of(required(props, "release.zip"));
        Path backupDir = Path.of(required(props, "backup.dir"));
        // Keep each attempt isolated from partially extracted earlier attempts.
        Path extractDir = manifestPath.getParent().resolve("extract-" + UUID.randomUUID());
        Path javaBin = Path.of(required(props, "java.bin"));
        String currentJar = required(props, "current.jar");
        String layout = props.getProperty("install.layout", "jar-dir");
        // Older desktops did not carry a server-role flag. Existing service copies
        // still require a verified restart when installing this updater.
        props.putIfAbsent("sync.service.required", Boolean.toString(
                Files.isDirectory(Path.of(props.getProperty("sync.service.app.dir", "missing-service")))));
        if (requiresServer(props) && isWindows() && !Files.isExecutable(javaBin))
            throw new IOException("The bundled Java runtime is missing; the server update was not installed.");

        UpdaterProgress.stage("Extracting update");
        unzip(releaseZip, extractDir);
        Path payloadDir = normalizePayloadDir(extractDir);
        UpdaterProgress.stage("Preserving installed AI models");
        String modelEnvironment = props.getProperty("sync.service.environment", "development");
        if (!modelEnvironment.equals("development") && !modelEnvironment.equals("production"))
            throw new IOException("The update model environment is invalid.");
        Path modelRoot = Path.of(props.getProperty("models.dir", Path.of(System.getProperty("user.home"),
                ".smartstock", "profiles", modelEnvironment, "ai-models").toString()));
        StudioModelMigration.migrate(appDir, modelRoot);
        String serviceDir = props.getProperty("sync.service.app.dir");
        if (serviceDir != null) StudioModelMigration.migrate(Path.of(serviceDir), modelRoot);
        if (requiresServer(props) && isWindows()) {
            UpdaterProgress.stage("Checking background server startup registration");
            Path serviceApp = Path.of(required(props, "sync.service.app.dir"));
            Files.createDirectories(serviceApp);
            updateSyncServiceLauncher(serviceApp, currentJar, props);
            updateWindowsSyncServiceTask(props, serviceApp, currentJar);
        }
        UpdaterProgress.stage("Waiting for SmartStock to close");
        terminateRecordedDesktopProcess(props);
        Path launchTarget;
        if ("mac-app".equals(layout)) {
            Path currentBundle = Path.of(required(props, "app.bundle.path"));
            Path newBundle = findMacAppBundle(payloadDir);
            if (newBundle == null) {
                throw new IOException("Mac release zip must contain a SmartStock.app bundle.");
            }
            UpdaterProgress.stage("Backing up SmartStock");
            backupMacAppBundle(currentBundle, backupDir);
            stopSyncService(props);
            try {
                UpdaterProgress.stage("Installing update");
                replaceMacAppBundle(currentBundle, newBundle);
                Path newAppDir = findJarDirectoryInMacApp(currentBundle);
                if (newAppDir != null) {
                    updateSyncServiceCopy(newAppDir, props);
                }
            } catch (Exception ex) {
                UpdaterProgress.stage("Restoring previous installation");
                restoreMacAppBundle(currentBundle, backupDir);
                installationRecovery = "The previous app bundle was restored.";
                throw ex;
            }
            launchTarget = currentBundle;
        } else {
            Path newJar = findReleaseJar(payloadDir);
            if (newJar == null) {
                throw new IOException("Release zip must contain a SmartStock inventory-management jar.");
            }
            validateApplicationPayload(payloadDir);

            UpdaterProgress.stage("Backing up SmartStock");
            backupCurrentApp(appDir, backupDir);
            stopSyncService(props);
            boolean replaced = false;
            try {
                UpdaterProgress.stage("Installing update");
                replaceApp(appDir, payloadDir);
                replaced = true;
                updateNativeLauncherConfigs(appDir, newJar.getFileName().toString());
                // A mixed desktop/service release can fail schema and API contracts.
                // Let the existing rollback path restore both if either copy fails.
                updateSyncServiceCopy(appDir, props);
            } catch (Exception ex) {
                if (replaced) {
                    try {
                        UpdaterProgress.stage("Restoring previous installation");
                        restoreBackup(appDir, backupDir);
                        updateSyncServiceCopy(appDir, props);
                        installationRecovery = "The previous application and service files were restored.";
                    } catch (Exception restoreError) {
                        ex.addSuppressed(restoreError);
                        installationRecovery = "Restoring the previous installation failed: " + rootMessage(restoreError);
                        log("Update rollback failed: " + rootMessage(restoreError));
                    }
                }
                throw ex;
            }
            Path launchJar = findReleaseJar(appDir);
            launchTarget = launchJar == null ? appDir.resolve(currentJar) : launchJar;
        }

        // Database initialization may have applied migrations. A startup failure
        // must retain the new files and backup for deliberate recovery, rather
        // than silently restoring an older application against a newer schema.
        startSyncService(props);
        UpdaterProgress.stage("Cleaning up update");
        deleteRecursivelyQuietly(extractDir);
        UpdaterProgress.stage("Reopening SmartStock");
        if (Boolean.parseBoolean(props.getProperty("relaunch", "true"))) {
            if ("mac-app".equals(layout) && isMac()) {
                relaunchMacApp(launchTarget);
            } else {
                new ProcessBuilder(relaunchCommand(props, javaBin, launchTarget))
                        .directory(appDir.toFile())
                        .start();
            }
        }
    }

    static String relaunchAfterFailure(Path manifestPath) throws IOException {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(manifestPath)) {
            props.load(input);
        }
        if (!Boolean.parseBoolean(props.getProperty("relaunch", "true"))) return "Automatic reopening is disabled.";
        // Extraction can fail while the original desktop is still exiting.
        // Never launch a second desktop while that process remains alive.
        waitForWindowsDesktopExit(props);
        Path appDir = Path.of(required(props, "app.dir"));
        Path javaBin = Path.of(required(props, "java.bin"));
        Path launchTarget = findReleaseJar(appDir);
        if (launchTarget == null) {
            launchTarget = appDir.resolve(required(props, "current.jar"));
        }
        new ProcessBuilder(relaunchCommand(props, javaBin, launchTarget))
                .directory(appDir.toFile())
                .start();
        log("Relaunched SmartStock after updater failure.");
        return "SmartStock reopening was requested.";
    }

    private static void restartServiceAfterFailure(Path manifestPath) {
        if (serverRestartAttempted) return;
        try {
            Properties props = new Properties();
            try (InputStream input = Files.newInputStream(manifestPath)) {
                props.load(input);
            }
            startSyncService(props);
        } catch (Exception ex) {
            serviceRecovery = "Background server recovery failed: " + rootMessage(ex);
            log("Background service recovery failed: " + rootMessage(ex));
        }
    }

    private static void backupMacAppBundle(Path currentBundle, Path backupDir) throws IOException {
        if (!Files.isDirectory(currentBundle)) {
            throw new IOException("The installed SmartStock app bundle is missing.");
        }
        Path parent = backupDir.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("The rollback directory has no parent.");
        Files.createDirectories(parent);
        Path staged = parent.resolve("." + backupDir.getFileName() + "-staged-" + UUID.randomUUID());
        Path previous = parent.resolve("." + backupDir.getFileName() + "-previous-" + UUID.randomUUID());
        Files.createDirectories(staged);
        try {
            Path stagedBundle = staged.resolve(currentBundle.getFileName().toString());
            copyMacBundle(currentBundle, stagedBundle);
            validateMacBundle(stagedBundle);
            if (Files.exists(backupDir)) Files.move(backupDir, previous);
            try {
                Files.move(staged, backupDir);
            } catch (IOException ex) {
                if (Files.exists(previous) && !Files.exists(backupDir)) {
                    Files.move(previous, backupDir);
                }
                throw ex;
            }
            deleteRecursivelyQuietly(previous);
        } finally {
            deleteRecursivelyQuietly(staged);
        }
    }

    private static void replaceMacAppBundle(Path currentBundle, Path newBundle) throws IOException {
        Path parent = currentBundle.getParent();
        if (parent == null) {
            throw new IOException("Unable to determine current app bundle parent.");
        }
        Files.createDirectories(parent);
        String nonce = UUID.randomUUID().toString();
        Path stagedBundle = parent.resolve("." + currentBundle.getFileName() + ".update-" + nonce);
        Path previousBundle = parent.resolve("." + currentBundle.getFileName() + ".previous-" + nonce);
        try {
            copyMacBundle(newBundle, stagedBundle);
            prepareMacBundleForInstall(stagedBundle);
            validateMacBundle(stagedBundle);
            if (Files.exists(currentBundle)) {
                moveMacBundle(currentBundle, previousBundle);
            }
            try {
                moveMacBundle(stagedBundle, currentBundle);
            } catch (IOException ex) {
                if (Files.exists(previousBundle) && !Files.exists(currentBundle)) {
                    moveMacBundle(previousBundle, currentBundle);
                }
                throw ex;
            }
            deleteRecursively(previousBundle);
        } finally {
            deleteRecursively(stagedBundle);
            if (Files.exists(previousBundle) && Files.exists(currentBundle)) {
                deleteRecursively(previousBundle);
            }
        }
    }

    private static void validateMacBundle(Path bundle) throws IOException {
        Path executable = bundle.resolve("Contents").resolve("MacOS").resolve("SmartStock");
        Path appDir = bundle.resolve("Contents").resolve("app");
        if (!Files.isExecutable(executable) || findReleaseJar(appDir) == null) {
            throw new IOException("The staged Mac app is incomplete and was not installed.");
        }
    }

    private static void prepareMacBundleForInstall(Path bundle) throws IOException {
        if (!isMac()) return;
        runRequiredCommand(macXattrCommand(bundle), "Could not clear Mac update metadata");
        runRequiredCommand(macCodesignVerifyCommand(bundle), "The downloaded Mac app signature is invalid");
    }

    static List<String> macXattrCommand(Path appBundle) {
        return List.of("/usr/bin/xattr", "-rc", appBundle.toString());
    }

    static List<String> macCodesignVerifyCommand(Path appBundle) {
        return List.of("/usr/bin/codesign", "--verify", "--deep", "--strict", appBundle.toString());
    }

    private static void runRequiredCommand(List<String> command, String failureMessage) throws IOException {
        Path outputFile = Files.createTempFile("smartstock-updater-command-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException(failureMessage + ": timed out.");
            }
            String output;
            try (InputStream input = Files.newInputStream(outputFile)) {
                output = new String(input.readNBytes(32768), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            if (process.exitValue() != 0) {
                throw new IOException(failureMessage + " (exit " + process.exitValue() + ")"
                        + (output.isBlank() ? "." : ": " + output));
            }
            if (!output.isBlank()) log(output);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(failureMessage + ": interrupted.", ex);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(outputFile);
        }
    }

    private static void moveMacBundle(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    static void relaunchMacApp(Path appBundle) throws IOException {
        try {
            Process process = new ProcessBuilder(macOpenCommand(appBundle))
                    .redirectErrorStream(true)
                    .start();
            if (process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) {
                log("Relaunched SmartStock with /usr/bin/open.");
                return;
            }
            process.destroyForcibly();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        Path executable = appBundle.resolve("Contents").resolve("MacOS").resolve("SmartStock");
        if (!Files.isExecutable(executable)) {
            throw new IOException("The updated SmartStock launcher is missing at " + executable + ".");
        }
        new ProcessBuilder(executable.toString())
                .directory(appBundle.getParent().toFile())
                .start();
        log("Relaunched SmartStock with its absolute launcher.");
    }

    static List<String> macOpenCommand(Path appBundle) {
        return List.of("/usr/bin/open", "-n", appBundle.toString());
    }

    private static void restoreMacAppBundle(Path currentBundle, Path backupDir) throws IOException {
        Path backedUpBundle = backupDir.resolve(currentBundle.getFileName().toString());
        if (!Files.isDirectory(backedUpBundle)) {
            throw new IOException("The SmartStock rollback app bundle is missing.");
        }
        validateMacBundle(backedUpBundle);
        replaceMacAppBundle(currentBundle, backedUpBundle);
    }

    private static void copyMacBundle(Path source, Path target) throws IOException {
        try {
            Process process = new ProcessBuilder("/usr/bin/ditto", source.toString(), target.toString())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("Timed out while copying the Mac app bundle.");
            }
            if (process.exitValue() != 0) {
                throw new IOException("Could not copy the Mac app bundle: " + output.trim());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while copying the Mac app bundle.", ex);
        }
    }

    static void backupCurrentApp(Path appDir, Path backupDir) throws IOException {
        Path parent = backupDir.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("The rollback directory has no parent.");
        Files.createDirectories(parent);
        Path staged = parent.resolve("." + backupDir.getFileName() + "-staged-" + UUID.randomUUID());
        Path previous = parent.resolve("." + backupDir.getFileName() + "-previous-" + UUID.randomUUID());
        Files.createDirectories(staged);
        try {
            try (Stream<Path> stream = Files.list(appDir)) {
                for (Path source : stream.toList()) {
                    String name = source.getFileName().toString();
                    if (isRollbackArtifact(name)) {
                        copyRecursively(source, staged.resolve(name));
                    }
                }
            }
            validateRollbackPayload(staged);
            if (Files.exists(backupDir)) Files.move(backupDir, previous);
            try {
                Files.move(staged, backupDir);
            } catch (IOException ex) {
                if (Files.exists(previous) && !Files.exists(backupDir)) {
                    Files.move(previous, backupDir);
                }
                throw ex;
            }
            deleteRecursivelyQuietly(previous);
        } finally {
            deleteRecursivelyQuietly(staged);
        }
    }

    static void prepareSingleRollbackDirectory(Path backupDir) throws IOException {
        if (Files.exists(backupDir)) {
            deleteRecursively(backupDir);
        }
        Files.createDirectories(backupDir);
    }

    static void replaceApp(Path appDir, Path payloadDir) throws IOException {
        validateApplicationPayload(payloadDir);
        swapApplicationDirectory(appDir, payloadDir, false);
    }

    static void restoreBackup(Path appDir, Path backupDir) throws IOException {
        validateRollbackPayload(backupDir);
        swapApplicationDirectory(appDir, backupDir, true);
    }

    private static void swapApplicationDirectory(Path appDir, Path payloadDir, boolean rollback) throws IOException {
        Path parent = appDir.toAbsolutePath().normalize().getParent();
        if (parent == null) throw new IOException("The application directory has no parent.");
        Path staged = parent.resolve(".app-staged-" + UUID.randomUUID());
        Path previous = parent.resolve(".app-previous-" + UUID.randomUUID());
        Files.createDirectories(staged);
        try {
            try (Stream<Path> files = Files.list(appDir)) {
                for (Path source : files.toList()) {
                    String name = source.getFileName().toString();
                    if (!isAppJar(name) && !"dependency".equals(name))
                        copyRecursively(source, staged.resolve(name));
                }
            }
            try (Stream<Path> files = Files.list(payloadDir)) {
                for (Path source : files.toList()) {
                    String name = source.getFileName().toString();
                    if (isAppJar(name) || "dependency".equals(name) || (rollback && isRollbackArtifact(name)))
                        copyRecursively(source, staged.resolve(name));
                }
            }
            if (rollback) validateRollbackPayload(staged);
            else {
                validateApplicationPayload(staged);
                updateNativeLauncherConfigs(staged, findReleaseJar(staged).getFileName().toString());
            }
            // Never delete live libraries piecemeal: a locked executable must
            // leave the entire old installation available for recovery.
            Files.move(appDir, previous);
            try {
                Files.move(staged, appDir);
            } catch (IOException installError) {
                try { Files.move(previous, appDir); }
                catch (IOException restoreError) { installError.addSuppressed(restoreError); }
                throw installError;
            }
            deleteRecursivelyQuietly(previous);
        } finally {
            deleteRecursivelyQuietly(staged);
        }
    }

    static void updateNativeLauncherConfigs(Path appDir, String jarName) throws IOException {
        if (appDir == null || jarName == null || !isAppJar(jarName)) return;
        String version = jarName.substring("inventory-management-".length(),
                jarName.length() - ".jar".length());
        for (String configName : List.of("SmartStock.cfg", "SmartStockServer.cfg")) {
            Path config = appDir.resolve(configName);
            if (!Files.isRegularFile(config)) continue;
            List<String> lines = Files.readAllLines(config);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (line.startsWith("app.classpath=$APPDIR\\inventory-management-")
                        && line.endsWith(".jar")) {
                    lines.set(index, "app.classpath=$APPDIR\\" + jarName);
                } else if (line.startsWith("java-options=-Djpackage.app-version=")) {
                    lines.set(index, "java-options=-Djpackage.app-version=" + version);
                }
            }
            Files.write(config, lines);
        }
    }

    static void updateSyncServiceCopy(Path appDir, Properties props) throws IOException {
        if (!requiresServer(props)) return;
        UpdaterProgress.stage("Updating background service");
        String syncServiceAppDirValue = props.getProperty("sync.service.app.dir");
        if (syncServiceAppDirValue == null || syncServiceAppDirValue.isBlank()) {
            return;
        }
        Path syncServiceAppDir = Path.of(syncServiceAppDirValue);
        if (!Files.exists(syncServiceAppDir) && !requiresServer(props)) {
            return;
        }
        Files.createDirectories(syncServiceAppDir);
        Path parent = syncServiceAppDir.getParent();
        if (parent == null) return;
        Path staged = parent.resolve(".app-update-" + UUID.randomUUID());
        Path previous = parent.resolve(".app-previous-" + UUID.randomUUID());
        // The scheduled task can respawn its JVM while the task stop command
        // returns. Repeat the process shutdown immediately before swapping the
        // directory so Windows has no live handle to the old copy.
        if (isWindows()) {
            terminateWindowsJavaSyncProcesses(props);
            terminateWindowsSyncServiceCloudflareProcesses(props);
        }
        Files.createDirectories(staged);
        try (Stream<Path> stream = Files.list(appDir)) {
            for (Path source : stream.toList()) {
                String name = source.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    copyRecursively(source, staged.resolve(name));
                }
            }
        }
        validateApplicationPayload(staged);
        try {
            IOException moveFailure = null;
            for (int attempt = 0; attempt < 20; attempt++) {
                try {
                    Files.move(syncServiceAppDir, previous);
                    moveFailure = null;
                    break;
                } catch (IOException ex) {
                    moveFailure = ex;
                    if (isWindows()) {
                        terminateWindowsJavaSyncProcesses(props);
                        terminateWindowsSyncServiceCloudflareProcesses(props);
                    }
                    sleepQuietly(250);
                }
            }
            if (moveFailure != null) throw moveFailure;
            try {
                Files.move(staged, syncServiceAppDir);
            } catch (IOException installError) {
                Files.move(previous, syncServiceAppDir);
                throw installError;
            }
        } finally {
            deleteRecursivelyQuietly(staged);
        }
        Path serviceJar = findReleaseJar(syncServiceAppDir);
        if (serviceJar != null) {
            updateSyncServiceLauncher(syncServiceAppDir, serviceJar.getFileName().toString(), props);
            updateWindowsSyncServiceTask(props, syncServiceAppDir, serviceJar.getFileName().toString());
        }
        deleteRecursivelyQuietly(previous);
    }

    private static void validateApplicationPayload(Path stagedAppDir) throws IOException {
        if (findReleaseJar(stagedAppDir) == null) {
            throw new IOException("The staged SmartStock server copy is missing its application JAR.");
        }
        Path dependencies = stagedAppDir.resolve("dependency");
        boolean hasPostgres;
        if (!Files.isDirectory(dependencies)) {
            hasPostgres = false;
        } else {
            try (Stream<Path> files = Files.list(dependencies)) {
                hasPostgres = files.anyMatch(path -> Files.isRegularFile(path)
                        && path.getFileName().toString().startsWith("postgresql-")
                        && path.getFileName().toString().endsWith(".jar"));
            }
        }
        if (!hasPostgres) {
            throw new IOException("The staged SmartStock server copy is missing the PostgreSQL driver.");
        }
    }

    private static void validateRollbackPayload(Path backupDir) throws IOException {
        if (backupDir == null || !Files.isDirectory(backupDir)
                || findReleaseJar(backupDir) == null
                || !Files.isDirectory(backupDir.resolve("dependency"))) {
            throw new IOException("The SmartStock rollback copy is missing or incomplete.");
        }
    }

    private static void deleteRecursivelyQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException ex) {
            log("Deferred cleanup for " + path + ": " + rootMessage(ex));
        }
    }

    private static void updateWindowsSyncServiceTask(
            Properties props, Path syncServiceAppDir, String jarName) throws IOException {
        if (!isWindows()) return;
        String taskName = props.getProperty("sync.service.task.name", "").trim();
        String javaBinValue = props.getProperty("java.bin", "").trim();
        if (taskName.isEmpty() || javaBinValue.isEmpty()) return;
        Path javaBin = Path.of(javaBinValue);
        Path serviceJava = javaBin;
        String serviceUser = props.getProperty("sync.service.user", "").trim();
        runRequiredCommand(windowsSyncTaskUpdateCommand(
                taskName, serviceJava, syncServiceAppDir, jarName, serviceUser),
                "Could not update the SmartStock background service task");
    }

    static List<String> windowsSyncTaskUpdateCommand(
            String taskName, Path javaBin, Path syncServiceAppDir, String jarName,
            String serviceUser) {
        Path serviceDir = syncServiceAppDir.getParent();
        ServerUpdateSupport.home(syncServiceAppDir);
        Path launcher = serviceDir.resolve("run-smartstock-sync-service.cmd");
        String selectedUser = serviceUser == null || serviceUser.isBlank()
                ? "$env:USERNAME" : "'" + powerShellQuote(serviceUser.trim()) + "'";
        String script = "$ErrorActionPreference='Stop';"
                + "if(!(Test-Path -LiteralPath '" + powerShellQuote(javaBin.toString())
                + "' -PathType Leaf)){throw 'Bundled server Java is missing'};"
                + "$launcher='" + powerShellQuote(launcher.toString()) + "';"
                + "if(!(Test-Path -LiteralPath $launcher -PathType Leaf)){throw 'Server launcher is missing'};"
                + "$cmd=Join-Path $env:SystemRoot 'System32\\cmd.exe';"
                + "$arguments='/d /s /c \"\"'+$launcher+'\"\"';"
                + "$action=New-ScheduledTaskAction -Execute $cmd -Argument $arguments -WorkingDirectory '"
                + powerShellQuote(syncServiceAppDir.toString()) + "';"
                + "$task=Get-ScheduledTask -TaskName '" + powerShellQuote(taskName)
                + "' -ErrorAction SilentlyContinue;"
                + "$settings=New-ScheduledTaskSettingsSet -StartWhenAvailable -RestartCount 3 "
                + "-RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero) "
                + "-MultipleInstances IgnoreNew;"
                + "$serviceUser=" + selectedUser + ";"
                + "if($task){$account=$task.Principal.UserId;"
                + "$sid=if($account -like 'S-1-*'){$account}else{"
                + "(New-Object Security.Principal.NTAccount($account)).Translate([Security.Principal.SecurityIdentifier]).Value};"
                + "$expected=(New-Object Security.Principal.NTAccount($serviceUser)).Translate([Security.Principal.SecurityIdentifier]).Value;"
                + "if($sid -ne $expected){throw 'Existing server task uses a different account; repair Server Settings before updating'}};"
                + "if($task){Set-ScheduledTask -TaskName '" + powerShellQuote(taskName)
                + "' -Action $action -Settings $settings -ErrorAction Stop | Out-Null}else{"
                + "$trigger=New-ScheduledTaskTrigger -AtLogOn -User $serviceUser;"
                + "$principal=New-ScheduledTaskPrincipal -UserId $serviceUser "
                + "-LogonType Interactive -RunLevel Limited;"
                + "Register-ScheduledTask -TaskName '" + powerShellQuote(taskName)
                + "' -Action $action -Trigger $trigger -Principal $principal -Settings $settings "
                + "-Description 'SmartStock HTTPS LAN and synchronization service' "
                + "-Force -ErrorAction Stop | Out-Null};"
                + "Enable-ScheduledTask -TaskName '" + powerShellQuote(taskName) + "' | Out-Null;"
                + "$registered=Get-ScheduledTask -TaskName '" + powerShellQuote(taskName) + "' -ErrorAction Stop;"
                + "if($registered.Actions.Count -ne 1 -or $registered.Actions[0].Execute -ne $cmd "
                + "-or $registered.Actions[0].Arguments -ne $arguments){throw 'Server task verification failed'};"
                + "$actualAccount=$registered.Principal.UserId;"
                + "$actualSid=if($actualAccount -like 'S-1-*'){$actualAccount}else{"
                + "(New-Object Security.Principal.NTAccount($actualAccount)).Translate([Security.Principal.SecurityIdentifier]).Value};"
                + "$expectedSid=(New-Object Security.Principal.NTAccount($serviceUser)).Translate([Security.Principal.SecurityIdentifier]).Value;"
                + "if($actualSid -ne $expectedSid){throw 'Server task account does not match the update profile; repair Server Settings'};"
                + "$shell=New-Object -ComObject WScript.Shell;"
                + "$shortcut=$shell.CreateShortcut('" + powerShellQuote(serviceDir.resolve("SmartStockServer.lnk").toString()) + "');"
                + "$shortcut.TargetPath=$cmd;$shortcut.Arguments=$arguments;"
                + "$shortcut.WorkingDirectory='" + powerShellQuote(syncServiceAppDir.toString()) + "';"
                + "$shortcut.WindowStyle=7;$shortcut.Save();"
                + "Write-Output ('Verified server task and shortcut for '+$registered.Principal.UserId);";
        return List.of(windowsPowerShellExecutable(), "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script);
    }
    private static String powerShellQuote(String value) {
        return value.replace("'", "''");
    }

    private static void updateSyncServiceLauncher(Path syncServiceAppDir, String jarName, Properties props) throws IOException {
        Path serviceDir = syncServiceAppDir.getParent();
        if (serviceDir == null) return;
        if (isMac()) {
            Path launcher = serviceDir.resolve("run-smartstock-sync-service.command");
            Files.writeString(launcher, syncLauncherContent(false, syncServiceAppDir, jarName));
            try {
                Files.setPosixFilePermissions(launcher, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignored) {
                launcher.toFile().setExecutable(true, true);
            }
        } else if (isWindows()) {
            Path launcher = serviceDir.resolve("run-smartstock-sync-service.cmd");
            Path java = Path.of(props.getProperty("java.bin",
                    Path.of(System.getProperty("java.home"), "bin", "java.exe").toString()));
            Files.writeString(launcher, ServerUpdateSupport.windowsLauncher(java, syncServiceAppDir,
                    ServerUpdateSupport.home(syncServiceAppDir), ServerUpdateSupport.environment(props, launcher,
                            ServerUpdateSupport.home(syncServiceAppDir))));
        }
    }

    static String syncLauncherContent(boolean windows, Path appDir, String jarName) {
        if (windows) {
            return ServerUpdateSupport.windowsLauncher(
                    Path.of(System.getProperty("java.home"), "bin", "java.exe"), appDir,
                    ServerUpdateSupport.home(appDir));
        }
        return "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "cd " + shellQuote(unixPath(appDir)) + "\n"
                + "exec " + shellQuote(unixPath(Path.of(System.getProperty("java.home"), "bin", "java")))
                + " -Duser.home=" + shellQuote(unixPath(ServerUpdateSupport.home(appDir)))
                + " -Djava.awt.headless=true -Dapple.awt.UIElement=true -jar "
                + shellQuote(jarName) + " --sync-service\n";
    }

    private static String unixPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static void unzip(Path zip, Path targetDir) throws IOException {
        deleteRecursively(targetDir);
        Files.createDirectories(targetDir);
        if (isMac() && Files.isExecutable(Path.of("/usr/bin/ditto"))) {
            Process process = new ProcessBuilder("/usr/bin/ditto", "-x", "-k",
                    zip.toString(), targetDir.toString())
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
            try {
                if (!process.waitFor(2, TimeUnit.MINUTES)) {
                    process.destroyForcibly();
                    throw new IOException("Timed out while extracting the Mac update bundle.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while extracting the Mac update bundle.", ex);
            }
            if (process.exitValue() != 0) {
                throw new IOException("Could not extract the Mac update bundle: " + output.trim());
            }
            return;
        }
        try (java.util.zip.ZipFile sizes = new java.util.zip.ZipFile(zip.toFile());
             ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                // Windows Compress-Archive writes backslash directory entries;
                // ZipEntry.isDirectory() only recognizes a trailing slash.
                String name = entry.getName().replace('\\', '/');
                Path target = targetDir.resolve(name).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("Unsafe zip entry: " + entry.getName());
                }
                if (name.endsWith("/")) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (var output = Files.newOutputStream(target)) {
                        UpdaterProgress.copy(input, output, sizes.getEntry(entry.getName()).getSize(), target.getFileName().toString());
                    }
                }
                input.closeEntry();
            }
        }
    }

    private static Path normalizePayloadDir(Path extractDir) throws IOException {
        if (findReleaseJar(extractDir) != null || findMacAppBundle(extractDir) != null) {
            return extractDir;
        }
        try (Stream<Path> stream = Files.list(extractDir)) {
            var dirs = stream.filter(Files::isDirectory).toList();
            if (dirs.size() == 1 && (findReleaseJar(dirs.get(0)) != null || findMacAppBundle(dirs.get(0)) != null)) {
                return dirs.get(0);
            }
        }
        return extractDir;
    }

    private static Path findReleaseJar(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> isAppJar(path.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean isAppJar(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("inventory-management-") && lower.endsWith(".jar");
    }

    private static boolean isRollbackArtifact(String name) {
        return isAppJar(name) || "dependency".equals(name)
                || "SmartStock.cfg".equals(name)
                || "SmartStockServer.cfg".equals(name);
    }

    private static Path findMacAppBundle(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().endsWith(".app"))
                    .filter(path -> Files.isDirectory(path.resolve("Contents")))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path findJarDirectoryInMacApp(Path appBundle) throws IOException {
        Path appDir = appBundle.resolve("Contents").resolve("app");
        if (findReleaseJar(appDir) != null) {
            return appDir;
        }
        return null;
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            if (Files.exists(target) && !Files.isDirectory(target)) {
                deleteRecursively(target);
            }
            Files.createDirectories(target);
            try (Stream<Path> stream = Files.list(source)) {
                for (Path child : stream.toList()) {
                    copyRecursively(child, target.resolve(child.getFileName().toString()));
                }
            }
        } else {
            if (Files.exists(target) && Files.isDirectory(target)) {
                deleteRecursively(target);
            }
            Files.createDirectories(target.getParent());
            try (var input = Files.newInputStream(source); var output = Files.newOutputStream(target)) {
                UpdaterProgress.copy(input, output, Files.size(source), source.getFileName().toString());
            }
            var attributes = Files.readAttributes(source, java.nio.file.attribute.BasicFileAttributes.class);
            Files.getFileAttributeView(target, java.nio.file.attribute.BasicFileAttributeView.class)
                    .setTimes(attributes.lastModifiedTime(), attributes.lastAccessTime(), attributes.creationTime());
            if (Files.getFileStore(source).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> stream = Files.walk(path)) {
                for (Path child : stream.sorted(Comparator.reverseOrder()).toList()) {
                    deleteWithRetry(child);
                }
            }
        } else {
            deleteWithRetry(path);
        }
    }

    private static void deleteWithRetry(Path path) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException ex) {
                last = ex;
                sleepQuietly(250);
            }
        }
        throw last == null ? new IOException("Could not delete " + path) : last;
    }

    private static void stopSyncService(Properties props) {
        if (!requiresServer(props)) return;
        UpdaterProgress.stage("Stopping background service");
        if (isMac()) {
            runMacLaunchctl("bootout", props.getProperty("sync.service.launch.agent.label"));
            return;
        }
        terminateWindowsApplicationProcessTree();
        runWindowsTaskCommand(props.getProperty("sync.service.task.name"), "/End");
        terminateWindowsServerProcessTree();
        terminateWindowsDesktopJavaProcesses(props);
        terminateWindowsJavaSyncProcesses(props);
        terminateWindowsSyncServiceCloudflareProcesses(props);
        String desktopAppDir = props.getProperty("app.dir", "").trim();
        if (!desktopAppDir.isEmpty()) {
            Properties desktopTunnel = new Properties();
            desktopTunnel.setProperty("sync.service.app.dir", desktopAppDir);
            terminateWindowsSyncServiceCloudflareProcesses(desktopTunnel);
        }
        waitForWindowsDesktopExit(props);
    }

    private static void terminateRecordedDesktopProcess(Properties props) {
        if (!isWindows()) return;
        String value = props.getProperty("desktop.pid", "").trim();
        if (value.isEmpty()) return;
        try {
            long pid=Long.parseLong(value);
            ProcessHandle.of(pid).ifPresent(process -> {
                if (process.pid()!=ProcessHandle.current().pid()) {
                    process.destroy();
                    if (process.isAlive()) process.destroyForcibly();
                }
            });
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(System.nanoTime()<deadline && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) sleepQuietly(100);
            if(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false))
                throw new IllegalStateException("The previous SmartStock desktop process did not close.");
        } catch(NumberFormatException ignored) {
            throw new IllegalStateException("The updater manifest contains an invalid desktop process ID.");
        }
    }

    static boolean requiresServer(Properties props) {
        String explicit = props.getProperty("sync.service.required");
        if (explicit != null) return Boolean.parseBoolean(explicit);
        String app = props.getProperty("sync.service.app.dir", "");
        return !app.isBlank() && Files.isDirectory(Path.of(app));
    }

    private static void startSyncService(Properties props) throws IOException {
        if (!requiresServer(props)) {
            serviceRecovery = "This register does not require a local background server.";
            return;
        }
        serverRestartAttempted = true;
        serviceRecovery = "Background server restart did not pass readiness checks. The installation backup is retained.";
        UpdaterProgress.stage("Starting background service");
        Path app = Path.of(required(props, "sync.service.app.dir"));
        Path jar = findReleaseJar(app);
        if (jar == null) throw new IOException("The installed background server JAR is missing.");
        if (isMac()) {
            String label = props.getProperty("sync.service.launch.agent.label");
            runMacLaunchctl("bootstrap", label);
            runMacLaunchctl("kickstart", label);
        } else if (isWindows()) {
            updateSyncServiceLauncher(app, jar.getFileName().toString(), props);
            updateWindowsSyncServiceTask(props, app, jar.getFileName().toString());
            String task = required(props, "sync.service.task.name");
            log("Requesting background server task " + task + " using bundled runtime " + required(props, "java.bin")
                    + "; startup output: " + app.getParent().resolve("sync-service.log"));
            runRequiredCommand(List.of("schtasks", "/Run", "/TN", task),
                    "Could not start the SmartStock background server task");
        }
        UpdaterProgress.stage("Waiting for background server readiness");
        String name = jar.getFileName().toString();
        String version = name.substring("inventory-management-".length(), name.length()-4);
        try {
            ServerUpdateSupport.awaitReady(java.time.Duration.ofSeconds(90), () ->
                    ServerUpdateSupport.probe(ServerUpdateSupport.home(app),
                            props.getProperty("sync.service.certificate.fingerprint", ""), version, 8443, 18443));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Server readiness verification was interrupted.", ex);
        }
        serviceRecovery = "Background server HTTPS health, installed version, local schema and LAN discovery were verified.";
        log(serviceRecovery);
    }

    private static void runMacLaunchctl(String action, String label) {
        if (label == null || label.isBlank()) {
            return;
        }
        try {
            String uid = commandOutput(List.of("/usr/bin/id", "-u"));
            Path plist = Path.of(System.getProperty("user.home"), "Library", "LaunchAgents", label + ".plist");
            if (uid.isBlank() || !Files.exists(plist)) {
                return;
            }
            runCommand(macLaunchctlCommand(action, uid, plist, label));
        } catch (Exception ignored) {
        }
    }

    static List<String> macLaunchctlCommand(String action, String uid, Path plist, String label) {
        String domain = "gui/" + uid;
        return switch (action) {
            case "bootout" -> List.of("/bin/launchctl", "bootout", domain, plist.toString());
            case "bootstrap" -> List.of("/bin/launchctl", "bootstrap", domain, plist.toString());
            case "kickstart" -> List.of("/bin/launchctl", "kickstart", "-k", domain + "/" + label);
            default -> throw new IllegalArgumentException("Unsupported launchctl action: " + action);
        };
    }

    private static void runCommand(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }

    private static String commandOutput(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream input = process.getInputStream()) {
            output = new String(input.readAllBytes()).trim();
        }
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return "";
        }
        return process.exitValue() == 0 ? output : "";
    }

    private static void runWindowsTaskCommand(String taskName, String action) {
        if (taskName == null || taskName.isBlank() || !isWindows()) {
            return;
        }
        try {
            new ProcessBuilder("schtasks", action, "/TN", taskName).start().waitFor();
        } catch (Exception ignored) {
        }
    }

    static List<String> windowsServerTerminationCommand() {
        return List.of("taskkill", "/F", "/T", "/IM", "SmartStockServer.exe");
    }

    static List<String> windowsApplicationTerminationCommand() {
        // The elevated updater is launched from SmartStock.exe. Using /T here
        // kills the updater itself as a descendant before it can install or
        // execute its recovery/relaunch path.
        return List.of("taskkill", "/F", "/IM", "SmartStock.exe");
    }

    private static void terminateWindowsApplicationProcessTree() {
        if (!isWindows()) return;
        try {
            runCommand(windowsApplicationTerminationCommand());
            Thread.sleep(750);
        } catch (Exception ignored) {
        }
        // taskkill may return before a jpackage child JVM exits. Kill any
        // remaining native SmartStock launcher directly, then let the JVM
        // termination pass below handle javaw.exe.
        List<ProcessHandle> launchers = ProcessHandle.allProcesses()
                .filter(process -> process.pid() != ProcessHandle.current().pid())
                .filter(process -> windowsPath(process.info().command().orElse("")).endsWith("/smartstock.exe"))
                .toList();
        launchers.forEach(ProcessHandle::destroy);
        waitForProcessExit(launchers, 2_000);
        launchers.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
    }

    private static void terminateWindowsServerProcessTree() {
        if (!isWindows()) return;
        try {
            runCommand(windowsServerTerminationCommand());
            Thread.sleep(750);
        } catch (Exception ignored) {
        }
    }

    /** The native launcher can leave its Java child alive after taskkill returns. */
    private static void terminateWindowsDesktopJavaProcesses(Properties props) {
        if (!isWindows()) return;
        String javaBinValue = props.getProperty("java.bin", "").trim();
        if (javaBinValue.isEmpty()) return;
        Path javaBin = Path.of(javaBinValue);
        List<ProcessHandle> matches = desktopJavaProcesses(javaBin);
        matches.forEach(ProcessHandle::destroy);
        waitForProcessExit(matches, 2_000);
        matches.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        waitForProcessExit(matches, 3_000);
    }

    private static List<ProcessHandle> desktopJavaProcesses(Path javaBin) {
        return ProcessHandle.allProcesses()
                .filter(process -> process.pid() != ProcessHandle.current().pid())
                .filter(process -> isWindowsDesktopJavaProcess(
                        process.info().command().orElse(null),
                        process.info().arguments().orElse(null), javaBin))
                .toList();
    }

    static boolean isWindowsDesktopJavaProcess(String command, String[] arguments, Path javaBin) {
        if (command == null || arguments == null || javaBin == null) return false;
        String runtimeBin=windowsPath(javaBin.toAbsolutePath().normalize().toString());
        String executable=windowsPath(command);
        String javaw=runtimeBin.endsWith("/java.exe")
                ? runtimeBin.substring(0,runtimeBin.length()-"java.exe".length())+"javaw.exe" : runtimeBin;
        if (!executable.equals(runtimeBin) && !executable.equals(javaw)) return false;
        String joined = String.join(" ", arguments).toLowerCase(Locale.ROOT);
        return joined.contains("inventory-management-") && !joined.contains("--sync-service")
                && !joined.contains("smartstockupdater");
    }

    private static void waitForWindowsDesktopExit(Properties props) {
        if (!isWindows()) return;
        String javaBinValue = props.getProperty("java.bin", "").trim();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            boolean launcherAlive = ProcessHandle.allProcesses()
                    .filter(process -> process.pid() != ProcessHandle.current().pid())
                    .anyMatch(process -> {
                        String command = process.info().command().orElse("");
                        return windowsPath(command).endsWith("/smartstock.exe");
                    });
            boolean javaAlive = !javaBinValue.isEmpty() && !desktopJavaProcesses(Path.of(javaBinValue)).isEmpty();
            if (!launcherAlive && !javaAlive) return;
            sleepQuietly(150);
        }
        throw new IllegalStateException("The previous SmartStock desktop process did not close before the update.");
    }

    private static void terminateWindowsJavaSyncProcesses(Properties props) {
        if (!isWindows()) return;
        String javaBinValue = props.getProperty("java.bin", "").trim();
        if (javaBinValue.isEmpty()) return;
        Path javaBin = Path.of(javaBinValue);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        long quietSince = -1;
        while (System.nanoTime() < deadline) {
            List<ProcessHandle> matches = windowsSyncServiceProcesses(javaBin);
            if (matches.isEmpty()) {
                if (quietSince < 0) quietSince = System.nanoTime();
                if (System.nanoTime() - quietSince >= TimeUnit.SECONDS.toNanos(2)) return;
                sleepQuietly(100);
                continue;
            }
            quietSince = -1;
            matches.forEach(ProcessHandle::destroy);
            waitForProcessExit(matches, 1_500);
            matches.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            waitForProcessExit(matches, 3_000);
        }
        if (!windowsSyncServiceProcesses(javaBin).isEmpty())
            throw new IllegalStateException("The SmartStock background service did not stay stopped for the update.");
    }

    private static List<ProcessHandle> windowsSyncServiceProcesses(Path javaBin) {
        return ProcessHandle.allProcesses()
                .filter(process -> process.pid() != ProcessHandle.current().pid())
                .filter(process -> isWindowsSyncServiceProcess(
                        process.info().command().orElse(null),
                        process.info().arguments().orElse(null), javaBin))
                .toList();
    }

    private static void terminateWindowsSyncServiceCloudflareProcesses(Properties props) {
        if (!isWindows()) return;
        String serviceAppDirValue = props.getProperty("sync.service.app.dir", "").trim();
        if (serviceAppDirValue.isEmpty()) return;
        Path serviceAppDir = Path.of(serviceAppDirValue);
        try {
            runRequiredCommand(windowsCloudflareTerminationCommand(serviceAppDir),
                    "Could not stop the SmartStock Scheduler tunnel");
        } catch (IOException ex) {
            log("SmartStock tunnel helper unavailable; verifying processes directly: "
                    + rootMessage(ex));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12);
        while (System.nanoTime() < deadline) {
            List<ProcessHandle> matches = ProcessHandle.allProcesses()
                    .filter(process -> process.pid() != ProcessHandle.current().pid())
                    .filter(process -> isWindowsSyncServiceCloudflareProcess(
                            process.info().command().orElse(null), serviceAppDir))
                    .toList();
            if (matches.isEmpty()) return;
            matches.forEach(ProcessHandle::destroy);
            waitForProcessExit(matches, 1_500);
            matches.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            waitForProcessExit(matches, 3_000);
        }
        throw new IllegalStateException(
                "The SmartStock Scheduler tunnel did not stop for the update.");
    }

    static List<String> windowsCloudflareTerminationCommand(Path serviceAppDir) {
        Path tunnel = serviceAppDir.toAbsolutePath().normalize()
                .resolve("dependency/cloudflared/windows-amd64/cloudflared.exe");
        String script = "$target=[IO.Path]::GetFullPath('"
                + powerShellQuote(tunnel.toString()) + "');"
                + "Get-CimInstance Win32_Process -Filter \"Name='cloudflared.exe'\" "
                + "-ErrorAction SilentlyContinue | Where-Object { $_.ExecutablePath -and "
                + "[IO.Path]::GetFullPath($_.ExecutablePath).Equals($target, "
                + "[StringComparison]::OrdinalIgnoreCase) } | ForEach-Object { "
                + "$result=Invoke-CimMethod -InputObject $_ -MethodName Terminate -ErrorAction Stop;"
                + "if($result.ReturnValue-ne 0){throw ('Tunnel termination failed with code '+$result.ReturnValue)}}";
        return List.of(windowsPowerShellExecutable(), "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script);
    }

    private static String windowsPowerShellExecutable() {
        String windows = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
        return Path.of(windows, "System32", "WindowsPowerShell", "v1.0", "powershell.exe")
                .toString();
    }

    static boolean isWindowsSyncServiceCloudflareProcess(String command, Path serviceAppDir) {
        if (command == null || serviceAppDir == null) return false;
        String executable = windowsPath(command);
        String serviceRoot = windowsPath(serviceAppDir.toAbsolutePath().normalize().toString());
        return executable.equals(serviceRoot
                + "/dependency/cloudflared/windows-amd64/cloudflared.exe");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isWindowsSyncServiceProcess(String command, String[] arguments, Path javaBin) {
        if (command == null || arguments == null || javaBin == null) return false;
        String executable = windowsPath(command);
        String configuredJava = windowsPath(javaBin.toString());
        int executableSeparator = executable.lastIndexOf('/');
        int configuredSeparator = configuredJava.lastIndexOf('/');
        if (executableSeparator < 0 || configuredSeparator < 0
                || !executable.substring(0, executableSeparator)
                .equals(configuredJava.substring(0, configuredSeparator))) {
            return false;
        }
        String executableName = executable.substring(executableSeparator + 1);
        if (!"java.exe".equals(executableName) && !"javaw.exe".equals(executableName)) return false;
        for (String argument : arguments) {
            if ("--sync-service".equals(argument)) return true;
        }
        return false;
    }

    private static String windowsPath(String value) {
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static void waitForProcessExit(List<ProcessHandle> processes, long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (processes.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    static List<String> relaunchCommand(Properties props, Path javaBin, Path launchTarget) {
        return relaunchCommand(props, javaBin, launchTarget, isWindows());
    }

    static List<String> relaunchCommand(
            Properties props, Path javaBin, Path launchTarget, boolean windows) {
        String nativeLauncher = props.getProperty("app.launcher.path", "").trim();
        if (windows && !nativeLauncher.isEmpty()) {
            Path launcher = Path.of(nativeLauncher);
            if (Files.isRegularFile(launcher)) {
                return List.of(launcher.toString());
            }
        }
        return List.of(javaBin.toString(), "-jar", launchTarget.toString());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing update manifest value: " + key);
        }
        return value;
    }

    static void log(String message) {
        try {
            Path logPath = Path.of(System.getProperty("user.home"), ".smartstock", "updates", "updater.log");
            Files.createDirectories(logPath.getParent());
            Files.writeString(logPath,
                    java.time.Instant.now() + " " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        String detail = message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
        String context = error.getMessage();
        if (current != error && context != null && !context.isBlank())
            return context.contains(detail) ? context : context + " Cause: " + detail;
        return detail;
    }
}
