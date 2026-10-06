package app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** JDK-only: also runs inside the isolated updater before any app files change. */
public final class StudioModelMigration {
    public static final String FAST_HASH = "60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a";
    public static final String BEST_HASH = "58f621f00f5d756097615970a88a791584600dcf7c45b18a0a6267535a1ebd3c";
    private StudioModelMigration() { }

    /** One-time bridge for older macOS native updaters, using the server's Java/home. */
    public static void main(String[] args) throws IOException {
        if (args.length < 2 || args.length > 3 || !(args[1].equals("development") || args[1].equals("production")))
            throw new IllegalArgumentException("Usage: StudioModelMigration <installed-app-jar-directory> <development|production> [service-app-directory]");
        Path root = Path.of(System.getProperty("user.home"), ".smartstock", "profiles", args[1], "ai-models");
        migrate(Path.of(args[0]), root);
        if (args.length == 3) migrate(Path.of(args[2]), root);
        System.out.println("AI models preserved in " + root + ". Original application files were retained.");
    }

    public static void migrate(Path appDir, Path modelRoot) throws IOException {
        preserve(appDir.resolve("dependency/catalog-studio/isnet-general-use.onnx"), modelRoot, FAST_HASH);
        preserve(appDir.resolve("dependency/catalog-studio/birefnet-general.onnx"), modelRoot, BEST_HASH);
    }

    public static void preserve(Path source, Path root, String hash) throws IOException {
        if (!Files.exists(source)) return;
        try {
            Path target = root.resolve(hash + ".onnx");
            if (Files.isRegularFile(target)) {
                try { verify(target, hash); return; } catch (IOException corrupt) { /* repair from source */ }
            }
            verify(source, hash);
            Files.createDirectories(root);
            Path partial = Files.createTempFile(root, "migration-", ".partial");
            try {
                Files.copy(source, partial, StandardCopyOption.REPLACE_EXISTING);
                verify(partial, hash);
                Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(partial); }
        } catch (IOException e) {
            throw new IOException("AI model migration failed. Original files were preserved. Repair the model or check free space and permissions before retrying the SmartStock update.", e);
        }
    }

    public static void verify(Path file, String expected) throws IOException {
        if (!expected.equals(sha256(file))) throw new IOException("AI model integrity check failed.");
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = input.read(buffer)) != -1;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
}
