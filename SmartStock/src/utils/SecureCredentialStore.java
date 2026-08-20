package utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Stores workstation secrets outside database.properties. macOS uses Keychain,
 * Windows uses the current-user DPAPI, and other platforms use an owner-only
 * fallback file so credentials are never written into the application tree.
 */
public final class SecureCredentialStore {
    private static final String SERVICE = "com.smartstock.database";
    private static final Path FALLBACK_PATH = Path.of(
            System.getProperty("user.home"), ".smartstock", "secure-credentials.properties");

    private SecureCredentialStore() {
    }

    public static String read(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            String os = osName();
            if (os.contains("mac")) {
                return readMac(key);
            }
            if (os.contains("win")) {
                return readWindows(key);
            }
            return readFallback(key);
        } catch (Exception ex) {
            System.err.println("Could not read secure credential " + key + ": " + ex.getMessage());
            return null;
        }
    }

    public static void write(String key, String value) throws IOException {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        try {
            String os = osName();
            if (os.contains("mac")) {
                writeMac(key, value);
                return;
            }
            if (os.contains("win")) {
                writeWindows(key, value);
                return;
            }
            writeFallback(key, value);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Secure credential storage was interrupted.", ex);
        } catch (Exception ex) {
            if (ex instanceof IOException io) throw io;
            throw new IOException("Could not store the credential in " + backendLabel() + ".", ex);
        }
    }

    public static void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            String os = osName();
            if (os.contains("mac")) {
                run(new ProcessBuilder("/usr/bin/security", "delete-generic-password", "-s", SERVICE, "-a", key), null, false);
            } else if (os.contains("win")) {
                Files.deleteIfExists(windowsPath(key));
            } else {
                Properties properties = loadFallback();
                properties.remove(key);
                saveFallback(properties);
            }
        } catch (Exception ex) {
            System.err.println("Could not delete secure credential " + key + ": " + ex.getMessage());
        }
    }

    public static String backendLabel() {
        String os = osName();
        if (os.contains("mac")) return "macOS Keychain";
        if (os.contains("win")) return "Windows DPAPI (current user)";
        return "owner-only credential file";
    }

    private static String readMac(String key) throws Exception {
        return blankToNull(run(new ProcessBuilder("/usr/bin/security", "find-generic-password", "-w", "-s", SERVICE, "-a", key), null, false));
    }

    private static void writeMac(String key, String value) throws Exception {
        run(new ProcessBuilder("/usr/bin/security", "add-generic-password", "-U", "-s", SERVICE, "-a", key, "-w", value), null, true);
    }

    private static Path windowsPath(String key) {
        return FALLBACK_PATH.getParent().resolve("credentials").resolve(safeKey(key) + ".dpapi");
    }

    private static String readWindows(String key) throws Exception {
        Path path = windowsPath(key);
        if (!Files.isRegularFile(path)) return null;
        String script = "$p=[Console]::In.ReadToEnd().Trim();"
                + "$s=Get-Content -Raw -LiteralPath $p | ConvertTo-SecureString;"
                + "$b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s);"
                + "try {[Runtime.InteropServices.Marshal]::PtrToStringBSTR($b)} finally {[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}";
        return blankToNull(run(windowsPowerShell(script), path.toString(), true));
    }

    private static void writeWindows(String key, String value) throws Exception {
        Path path = windowsPath(key);
        Files.createDirectories(path.getParent());
        SecureFilePermissions.restrictDirectoryToOwner(path.getParent());
        String script = "$v=[Console]::In.ReadToEnd();"
                + "$s=ConvertTo-SecureString $v -AsPlainText -Force;"
                + "$s | ConvertFrom-SecureString | Set-Content -NoNewline -LiteralPath '"
                + path.toString().replace("'", "''") + "'";
        run(windowsPowerShell(script), value, true);
        SecureFilePermissions.restrictFileToOwner(path);
    }

    static ProcessBuilder windowsPowerShell(String script) {
        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script);
        sanitizeWindowsPowerShellEnvironment(builder);
        return builder;
    }

    static void sanitizeWindowsPowerShellEnvironment(ProcessBuilder builder) {
        // Launchers and development tools can inject a PowerShell Core module path.
        // Windows PowerShell then sees Microsoft.PowerShell.Security but cannot load it,
        // which makes valid DPAPI credentials look missing. With this variable absent,
        // powershell.exe reconstructs its own compatible default module path.
        builder.environment().keySet().removeIf(
                key -> "PSModulePath".equalsIgnoreCase(key));
    }

    private static String readFallback(String key) throws IOException {
        String encoded = loadFallback().getProperty(key);
        if (encoded == null || encoded.isBlank()) return null;
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static void writeFallback(String key, String value) throws IOException {
        Properties properties = loadFallback();
        properties.setProperty(key, Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)));
        saveFallback(properties);
    }

    private static Properties loadFallback() throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(FALLBACK_PATH)) {
            try (var input = Files.newInputStream(FALLBACK_PATH)) {
                properties.load(input);
            }
        }
        return properties;
    }

    private static void saveFallback(Properties properties) throws IOException {
        Files.createDirectories(FALLBACK_PATH.getParent());
        SecureFilePermissions.restrictDirectoryToOwner(FALLBACK_PATH.getParent());
        try (var output = Files.newOutputStream(FALLBACK_PATH)) {
            properties.store(output, "SmartStock secure credential fallback");
        }
        SecureFilePermissions.restrictFileToOwner(FALLBACK_PATH);
    }

    private static String run(ProcessBuilder builder, String stdin, boolean requireSuccess) throws Exception {
        builder.redirectErrorStream(true);
        Process process = builder.start();
        if (stdin != null) {
            try (var output = process.getOutputStream()) {
                output.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        } else {
            process.getOutputStream().close();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Credential helper timed out.");
        }
        if (requireSuccess && process.exitValue() != 0) {
            throw new IOException(output.isBlank() ? "Credential helper failed." : output);
        }
        return process.exitValue() == 0 ? output : null;
    }

    private static String safeKey(String key) {
        return key.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
