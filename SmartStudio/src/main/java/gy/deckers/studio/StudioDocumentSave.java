package gy.deckers.studio;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

final class StudioDocumentSave {
    private StudioDocumentSave() {}

    static String observe(Path file)throws IOException {
        return Files.exists(file,java.nio.file.LinkOption.NOFOLLOW_LINKS)?BadgeTemplateLibrary.fingerprint(file):"";
    }

    static String write(Path file, String expected, StudioFileNames.ContentWriter writer) throws Exception {
        Path target = file.toAbsolutePath().normalize();
        return locked(target, () -> {
            if ("".equals(expected)) {
                if(Files.exists(target,java.nio.file.LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Another file was created with this name. Choose a different name or reopen the saved file.");
            } else if (expected != null && (!Files.isRegularFile(target)
                    || !expected.equals(BadgeTemplateLibrary.fingerprint(target)))) {
                throw new IOException("This file changed on disk. Save As a new copy or reopen the latest version.");
            }
            writer.write(target);
            return BadgeTemplateLibrary.fingerprint(target);
        });
    }

    static <T> T locked(Path file, Callable<T> operation) throws Exception {
        Path target = file.toAbsolutePath().normalize();
        Path lockPath = target.resolveSibling("." + target.getFileName() + ".smartstudio-lock");
        // Keep the lock file after release so all clients continue locking the same inode.
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            if (System.getProperty("os.name", "").startsWith("Windows")) {
                var attributes = Files.getFileAttributeView(lockPath, java.nio.file.attribute.DosFileAttributeView.class);
                if (attributes != null) attributes.setHidden(true);
            }
            java.nio.channels.FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException busy) {
                throw new IOException("Another SmartStudio window is saving this file. Try again.", busy);
            }
            if (lock == null) {
                throw new IOException("Another computer is saving this file. Try again.");
            }
            try (lock) {
                return operation.call();
            }
        }
    }

    static <T> T lockedRename(Path source, Path destination, Callable<T> operation) throws Exception {
        Path first = source.toAbsolutePath().normalize(), second = destination.toAbsolutePath().normalize();
        if(first.equals(second) || System.getProperty("os.name", "").startsWith("Windows")
                && first.toString().equalsIgnoreCase(second.toString())) return locked(first, operation);
        if(first.toString().compareTo(second.toString()) > 0) { Path swap=first; first=second; second=swap; }
        Path other=second;
        return locked(first, () -> locked(other, operation));
    }
}
