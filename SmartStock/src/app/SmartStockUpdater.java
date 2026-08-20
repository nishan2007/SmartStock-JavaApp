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
    private SmartStockUpdater() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            System.err.println("Missing update manifest path.");
            System.exit(2);
        }
        try {
            log("Updater started.");
            Thread.sleep(1800);
            apply(Path.of(args[0]));
            log("Updater completed.");
        } catch (Exception ex) {
            log("Updater failed: " + rootMessage(ex));
            ex.printStackTrace();
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
        Path extractDir = manifestPath.getParent().resolve("extract");
        Path javaBin = Path.of(required(props, "java.bin"));
        String currentJar = required(props, "current.jar");
        String layout = props.getProperty("install.layout", "jar-dir");

        unzip(releaseZip, extractDir);
        Path payloadDir = normalizePayloadDir(extractDir);
        Path launchTarget;
        if ("mac-app".equals(layout)) {
            Path currentBundle = Path.of(required(props, "app.bundle.path"));
            Path newBundle = findMacAppBundle(payloadDir);
            if (newBundle == null) {
                throw new IOException("Mac release zip must contain a SmartStock.app bundle.");
            }
            backupMacAppBundle(currentBundle, backupDir);
            stopSyncService(props);
            try {
                replaceMacAppBundle(currentBundle, newBundle);
                Path newAppDir = findJarDirectoryInMacApp(currentBundle);
                if (newAppDir != null) {
                    updateSyncServiceCopy(newAppDir, props);
                }
            } catch (Exception ex) {
                restoreMacAppBundle(currentBundle, backupDir);
                throw ex;
            } finally {
                startSyncService(props);
            }
            launchTarget = currentBundle;
        } else {
            Path newJar = findReleaseJar(payloadDir);
            if (newJar == null) {
                throw new IOException("Release zip must contain a SmartStock inventory-management jar.");
            }

            backupCurrentApp(appDir, backupDir);
            stopSyncService(props);
            try {
                replaceApp(appDir, payloadDir);
                updateNativeLauncherConfigs(appDir, newJar.getFileName().toString());
                updateSyncServiceCopy(appDir, props);
            } catch (Exception ex) {
                restoreBackup(appDir, backupDir);
                try {
                    updateSyncServiceCopy(appDir, props);
                } catch (Exception restoreServiceError) {
                    ex.addSuppressed(restoreServiceError);
                }
                throw ex;
            } finally {
                startSyncService(props);
            }
            Path launchJar = findReleaseJar(appDir);
            launchTarget = launchJar == null ? appDir.resolve(currentJar) : launchJar;
        }

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

    private static void backupMacAppBundle(Path currentBundle, Path backupDir) throws IOException {
        prepareSingleRollbackDirectory(backupDir);
        if (Files.exists(currentBundle)) {
            copyMacBundle(currentBundle, backupDir.resolve(currentBundle.getFileName().toString()));
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
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException(failureMessage + ": timed out.");
            }
            if (process.exitValue() != 0) {
                throw new IOException(failureMessage + (output.isBlank() ? "." : ": " + output));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(failureMessage + ": interrupted.", ex);
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
        if (Files.exists(backedUpBundle)) {
            replaceMacAppBundle(currentBundle, backedUpBundle);
        }
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

    private static void backupCurrentApp(Path appDir, Path backupDir) throws IOException {
        prepareSingleRollbackDirectory(backupDir);
        try (Stream<Path> stream = Files.list(appDir)) {
            for (Path source : stream.toList()) {
                String name = source.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    copyRecursively(source, backupDir.resolve(name));
                }
            }
        }
    }

    static void prepareSingleRollbackDirectory(Path backupDir) throws IOException {
        if (Files.exists(backupDir)) {
            deleteRecursively(backupDir);
        }
        Files.createDirectories(backupDir);
    }

    private static void replaceApp(Path appDir, Path payloadDir) throws IOException {
        try (Stream<Path> stream = Files.list(appDir)) {
            for (Path target : stream.toList()) {
                String name = target.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    deleteRecursively(target);
                }
            }
        }
        try (Stream<Path> stream = Files.list(payloadDir)) {
            for (Path source : stream.toList()) {
                String name = source.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    copyRecursively(source, appDir.resolve(name));
                }
            }
        }
    }

    private static void restoreBackup(Path appDir, Path backupDir) throws IOException {
        try (Stream<Path> stream = Files.list(appDir)) {
            for (Path target : stream.toList()) {
                String name = target.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    deleteRecursively(target);
                }
            }
        }
        if (!Files.exists(backupDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(backupDir)) {
            for (Path source : stream.toList()) {
                copyRecursively(source, appDir.resolve(source.getFileName().toString()));
            }
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

    private static void updateSyncServiceCopy(Path appDir, Properties props) throws IOException {
        String syncServiceAppDirValue = props.getProperty("sync.service.app.dir");
        if (syncServiceAppDirValue == null || syncServiceAppDirValue.isBlank()) {
            return;
        }
        Path syncServiceAppDir = Path.of(syncServiceAppDirValue);
        if (!Files.exists(syncServiceAppDir)) {
            return;
        }
        Files.createDirectories(syncServiceAppDir);
        try (Stream<Path> stream = Files.list(syncServiceAppDir)) {
            for (Path target : stream.toList()) {
                String name = target.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    deleteRecursively(target);
                }
            }
        }
        try (Stream<Path> stream = Files.list(appDir)) {
            for (Path source : stream.toList()) {
                String name = source.getFileName().toString();
                if (isAppJar(name) || "dependency".equals(name)) {
                    copyRecursively(source, syncServiceAppDir.resolve(name));
                }
            }
        }
        Path serviceJar = findReleaseJar(syncServiceAppDir);
        if (serviceJar != null) {
            updateSyncServiceLauncher(syncServiceAppDir, serviceJar.getFileName().toString());
            updateWindowsSyncServiceTask(props, syncServiceAppDir, serviceJar.getFileName().toString());
        }
    }

    private static void updateWindowsSyncServiceTask(
            Properties props, Path syncServiceAppDir, String jarName) throws IOException {
        if (!isWindows()) return;
        String taskName = props.getProperty("sync.service.task.name", "").trim();
        String javaBinValue = props.getProperty("java.bin", "").trim();
        if (taskName.isEmpty() || javaBinValue.isEmpty()) return;
        Path javaBin = Path.of(javaBinValue);
        Path javaw = javaBin.resolveSibling("javaw.exe");
        Path serviceJava = Files.isRegularFile(javaw) ? javaw : javaBin;
        runRequiredCommand(windowsSyncTaskUpdateCommand(
                taskName, serviceJava, syncServiceAppDir, jarName),
                "Could not update the SmartStock background service task");
    }

    static List<String> windowsSyncTaskUpdateCommand(
            String taskName, Path javaBin, Path syncServiceAppDir, String jarName) {
        Path serviceDir = syncServiceAppDir.getParent();
        Path smartstockDir = serviceDir == null ? null : serviceDir.getParent();
        Path userHome = smartstockDir == null ? null : smartstockDir.getParent();
        if (serviceDir == null || userHome == null) {
            throw new IllegalArgumentException("The SmartStock service profile path is invalid.");
        }
        Path shortcut = serviceDir.resolve("SmartStockServer.lnk");
        String script = "$shell=New-Object -ComObject WScript.Shell;"
                + "$shortcut=$shell.CreateShortcut('" + powerShellQuote(shortcut.toString()) + "');"
                + "$shortcut.TargetPath='" + powerShellQuote(javaBin.toString()) + "';"
                + "$shortcut.Arguments='-Duser.home=\"" + powerShellQuote(userHome.toString())
                + "\" -jar \"" + powerShellQuote(jarName) + "\" --sync-service';"
                + "$shortcut.WorkingDirectory='" + powerShellQuote(syncServiceAppDir.toString()) + "';"
                + "$shortcut.WindowStyle=7;$shortcut.Save();"
                + "$action=New-ScheduledTaskAction -Execute (Join-Path $env:WINDIR 'explorer.exe') "
                + "-Argument ('\"' + '" + powerShellQuote(shortcut.toString()) + "' + '\"');"
                + "Set-ScheduledTask -TaskName '" + powerShellQuote(taskName)
                + "' -Action $action -ErrorAction Stop | Out-Null";
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script);
    }

    private static String powerShellQuote(String value) {
        return value.replace("'", "''");
    }

    private static void updateSyncServiceLauncher(Path syncServiceAppDir, String jarName) throws IOException {
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
            Files.writeString(launcher, syncLauncherContent(true, syncServiceAppDir, jarName));
        }
    }

    static String syncLauncherContent(boolean windows, Path appDir, String jarName) {
        if (windows) {
            return "@echo off\r\n"
                    + "cd /d \"" + appDir + "\"\r\n"
                    + "java -jar \"" + jarName + "\" --sync-service\r\n";
        }
        return "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "cd " + shellQuote(unixPath(appDir)) + "\n"
                + "exec java -Djava.awt.headless=true -Dapple.awt.UIElement=true -jar "
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
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                Path target = targetDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(targetDir)) {
                    throw new IOException("Unsafe zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
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
            Files.createDirectories(target);
            try (Stream<Path> stream = Files.list(source)) {
                for (Path child : stream.toList()) {
                    copyRecursively(child, target.resolve(child.getFileName().toString()));
                }
            }
        } else {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
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
        if (isMac()) {
            runMacLaunchctl("bootout", props.getProperty("sync.service.launch.agent.label"));
            return;
        }
        runWindowsTaskCommand(props.getProperty("sync.service.task.name"), "/End");
        terminateWindowsApplicationProcessTree();
        terminateWindowsServerProcessTree();
        terminateWindowsJavaSyncProcesses(props);
    }

    private static void startSyncService(Properties props) {
        if (isMac()) {
            String label = props.getProperty("sync.service.launch.agent.label");
            runMacLaunchctl("bootstrap", label);
            runMacLaunchctl("kickstart", label);
            return;
        }
        runWindowsTaskCommand(props.getProperty("sync.service.task.name"), "/Run");
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
        return List.of("taskkill", "/F", "/T", "/IM", "SmartStock.exe");
    }

    private static void terminateWindowsApplicationProcessTree() {
        if (!isWindows()) return;
        try {
            runCommand(windowsApplicationTerminationCommand());
            Thread.sleep(750);
        } catch (Exception ignored) {
        }
    }

    private static void terminateWindowsServerProcessTree() {
        if (!isWindows()) return;
        try {
            runCommand(windowsServerTerminationCommand());
            Thread.sleep(750);
        } catch (Exception ignored) {
        }
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

    private static void log(String message) {
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
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
