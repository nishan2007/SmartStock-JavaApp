package managers;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Keeps interrupted preference writes from damaging the shared local cache. */
final class CompanyPreferenceFile {
    private CompanyPreferenceFile() { }

    static Properties read(Path path) throws IOException {
        Properties result = new Properties();
        if (!Files.exists(path)) return result;
        StringBuilder record = new StringBuilder();
        for (String line : Files.readAllLines(path, StandardCharsets.ISO_8859_1)) {
            String trimmed = line.stripLeading();
            if (record.isEmpty() && (trimmed.startsWith("#") || trimmed.startsWith("!"))) continue;
            record.append(line).append('\n');
            int slashes = 0;
            for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) slashes++;
            if (slashes % 2 != 0) continue;
            readRecord(result, record.toString());
            record.setLength(0);
        }
        if (!record.isEmpty()) readRecord(result, record.toString());
        return result;
    }

    private static void readRecord(Properties result, String record) throws IOException {
        Properties entry = new Properties();
        try {
            entry.load(new StringReader(record));
            result.putAll(entry);
        } catch (IllegalArgumentException malformedEscape) {
            // Recover other settings rather than failing every configuration request.
            System.err.println("Skipped a damaged company preference entry (invalid escape).");
        }
    }

    static void write(Path path, Properties properties) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "company-preferences-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "SmartStock company customization settings");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
