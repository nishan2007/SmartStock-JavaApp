package services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/** Resolves the updater-owned, pinned tunnel client without relying on a user's PATH. */
final class CloudflareBinary {
    static final String WINDOWS_SHA256 = "c29eee2b121f5436a642eed69fd9767da7e7b8c510fa50aaa130337f931357b5";
    private CloudflareBinary() { }

    static String executable() throws Exception {
        String override = System.getenv("SMARTSTOCK_CLOUDFLARED_PATH");
        if (override != null && !override.isBlank()) {
            Path path = Path.of(override).toAbsolutePath();
            if (!Files.isRegularFile(path)) throw new IOException("Configured cloudflared executable is missing.");
            return path.toString();
        }
        Path code = Path.of(CloudflareBinary.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path app = code.getParent();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            Path binary = app.resolve("dependency/cloudflared/windows-amd64/cloudflared.exe");
            verify(binary, WINDOWS_SHA256);
            return binary.toString();
        }
        throw new IOException("Configure SMARTSTOCK_CLOUDFLARED_PATH for this operating system.");
    }

    static void verify(Path file, String expected) throws Exception {
        if (!Files.isRegularFile(file)) throw new IOException("Cloudflare client is missing from this installation. Reinstall the complete update.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        if (!HexFormat.of().formatHex(digest.digest()).equals(expected))
            throw new IOException("Cloudflare client integrity check failed. The scheduler remains stopped.");
    }
}
