package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

public final class SecureFilePermissions {
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
    );
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
    );

    private SecureFilePermissions() {
    }

    public static void restrictFileToOwner(Path path) throws IOException {
        if (path == null || !Files.exists(path) || !supportsPosixPermissions(path)) {
            return;
        }
        Files.setPosixFilePermissions(path, OWNER_ONLY_FILE);
    }

    public static void restrictDirectoryToOwner(Path path) throws IOException {
        if (path == null || !Files.exists(path) || !supportsPosixPermissions(path)) {
            return;
        }
        Files.setPosixFilePermissions(path, OWNER_ONLY_DIRECTORY);
    }

    private static boolean supportsPosixPermissions(Path path) throws IOException {
        return Files.getFileStore(path.toAbsolutePath()).supportsFileAttributeView("posix");
    }
}
