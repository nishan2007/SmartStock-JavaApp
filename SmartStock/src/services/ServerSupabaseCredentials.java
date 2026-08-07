package services;

import data.DatabaseCredentials;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpRequest;
import com.google.gson.JsonParser;
import data.EnvironmentProfile;

/** Server-only access to the privileged Supabase credential. */
public final class ServerSupabaseCredentials {
    public static final String KEYCHAIN_SERVICE = "com.smartstock.supabase";
    public static final String KEYCHAIN_ACCOUNT = "server-service-role";
    public static Path windowsCredentialPath() {
        return EnvironmentProfile.active().file("server-cloud-credential.dpapi");
    }
    private static volatile String cached;

    private ServerSupabaseCredentials() { }

    public static String require() {
        String value = get();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("The SmartStock Server cloud credential is not configured.");
        }
        return value;
    }

    public static String get() {
        String existing = cached;
        if (existing != null && !existing.isBlank()) return existing;

        String configured = firstNonBlank(
                System.getProperty("SUPABASE_SECRET_KEY"),
                System.getenv("SUPABASE_SECRET_KEY"),
                DatabaseCredentials.load().get("SUPABASE_SECRET_KEY"),
                System.getProperty("SUPABASE_SERVICE_ROLE_KEY"),
                System.getenv("SUPABASE_SERVICE_ROLE_KEY"),
                DatabaseCredentials.load().get("SUPABASE_SERVICE_ROLE_KEY"));
        if (configured == null && isMac()) configured = readMacKeychain();
        if (configured == null && isWindows()) configured = readWindowsDpapi();
        if (configured != null && !configured.isBlank()) cached = configured.trim();
        return cached;
    }

    public static boolean isConfigured() { return get() != null; }

    public static HttpRequest.Builder applyTo(HttpRequest.Builder builder) {
        String credential = require();
        builder.header("apikey", credential);
        if (!credential.startsWith("sb_secret_")) {
            builder.header("Authorization", "Bearer " + credential);
        }
        return builder;
    }

    public static void install(char[] credential) throws IOException {
        char[] copy = credential == null ? new char[0] : credential.clone();
        try {
            String value = new String(copy).trim();
            validate(value);
            if (isMac()) installMacKeychain(value);
            else if (isWindows()) installWindowsDpapi(value);
            else throw new IOException("Configure SUPABASE_SERVICE_ROLE_KEY for the operating-system account running this SmartStock server.");
            cached = value;
        } finally {
            Arrays.fill(copy, '\0');
            if (credential != null) Arrays.fill(credential, '\0');
        }
    }

    static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Paste the Supabase server secret key.");
        }
        if (value.startsWith("sb_secret_")) {
            if (value.length() < 32 || value.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("The Supabase secret key is incomplete.");
            }
            return;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Use an sb_secret_ key or the legacy service_role JWT, not a publishable or user key.");
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            var json = JsonParser.parseString(payload).getAsJsonObject();
            String role = json.has("role") ? json.get("role").getAsString() : "";
            String ref = json.has("ref") ? json.get("ref").getAsString() : "";
            if (!"service_role".equals(role)) {
                throw new IllegalArgumentException("Use the Supabase server secret key.");
            }
            SupabaseProjectConfig.load().requireMatchingCredentialProject(ref);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("The Supabase service_role key is invalid.");
        }
    }

    static void clearCacheForTest() { cached = null; }

    private static String readMacKeychain() {
        Process process = null;
        try {
            process = new ProcessBuilder("security", "find-generic-password", "-w",
                    "-s", KEYCHAIN_SERVICE, "-a", profileAccount())
                    .redirectErrorStream(true).start();
            if (!process.waitFor(Duration.ofSeconds(3).toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) return null;
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static void installMacKeychain(String value) throws IOException {
        Process process = new ProcessBuilder("security", "add-generic-password", "-U",
                "-s", KEYCHAIN_SERVICE, "-a", profileAccount(), "-w", value)
                .redirectErrorStream(true).start();
        waitForSuccess(process, "macOS Keychain rejected the server cloud credential.");
    }

    private static void installWindowsDpapi(String value) throws IOException {
        String script = "Add-Type -AssemblyName System.Security;"
                + "$ErrorActionPreference='Stop';"
                + "$plain=[Console]::In.ReadToEnd();"
                + "$data=[Text.Encoding]::UTF8.GetBytes($plain);"
                + "$protected=[Security.Cryptography.ProtectedData]::Protect($data,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                + "if($null -eq $protected){throw 'Windows DPAPI returned no encrypted data.'};"
                + "[Console]::Out.Write([Convert]::ToBase64String($protected))";
        String encrypted = runPowerShell(script, value);
        if (encrypted.isBlank()) throw new IOException("Windows DPAPI returned an empty credential.");
        Path credentialPath = windowsCredentialPath();
        Files.createDirectories(credentialPath.getParent());
        utils.SecureFilePermissions.restrictDirectoryToOwner(credentialPath.getParent());
        Files.writeString(credentialPath, encrypted.trim(), StandardCharsets.US_ASCII);
        utils.SecureFilePermissions.restrictFileToOwner(credentialPath);
    }

    private static String readWindowsDpapi() {
        Path credentialPath = windowsCredentialPath();
        if (!Files.isRegularFile(credentialPath)) return null;
        try {
            String encrypted = Files.readString(credentialPath, StandardCharsets.US_ASCII).trim();
            String script = "Add-Type -AssemblyName System.Security;"
                    + "$ErrorActionPreference='Stop';"
                    + "$encoded=[Console]::In.ReadToEnd();"
                    + "$protected=[Convert]::FromBase64String($encoded);"
                    + "$data=[Security.Cryptography.ProtectedData]::Unprotect($protected,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                    + "if($null -eq $data){throw 'Windows DPAPI returned no decrypted data.'};"
                    + "[Console]::Out.Write([Text.Encoding]::UTF8.GetString($data))";
            String value = runPowerShell(script, encrypted).trim();
            validate(value);
            return value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String profileAccount() {
        return KEYCHAIN_ACCOUNT + "-" + EnvironmentProfile.active().id();
    }

    private static String runPowerShell(String script, String input) throws IOException {
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script).redirectErrorStream(true).start();
        try {
            process.getOutputStream().write(input.getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().close();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Windows DPAPI credential storage timed out.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) throw new IOException(output.isBlank()
                    ? "Windows DPAPI rejected the server cloud credential." : output);
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Windows DPAPI credential storage was interrupted.", ex);
        } finally {
            process.destroy();
        }
    }

    private static void waitForSuccess(Process process, String fallback) throws IOException {
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Saving the server cloud credential timed out.");
            }
            if (process.exitValue() != 0) {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException(output.isBlank() ? fallback : output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Saving the server cloud credential was interrupted.", ex);
        } finally {
            process.destroy();
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static String secureStoreDescription() {
        if (isMac()) return "this server's macOS Keychain";
        if (isWindows()) return "Windows DPAPI for the account running this server";
        return "the server operating-system credential store";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value.trim();
        return null;
    }
}
