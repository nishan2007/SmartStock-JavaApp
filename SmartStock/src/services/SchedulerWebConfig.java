package services;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Machine-local scheduler settings, kept outside application update payloads. */
final class SchedulerWebConfig {
    private SchedulerWebConfig() { }

    static String publicOrigin() throws IOException {
        return publicOrigin(System.getenv("SMARTSTOCK_SCHEDULER_PUBLIC_ORIGIN"),
                Path.of(System.getProperty("user.home"), ".smartstock", "scheduler.properties"));
    }

    static String publicOrigin(String override, Path settings) throws IOException {
        String value = override;
        if (value == null || value.isBlank()) {
            if (Files.notExists(settings)) return null;
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(settings, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            value = properties.getProperty("public.origin");
        }
        if (value == null || value.isBlank()) return null;
        String origin = SchedulerWebServer.cleanOrigin(value);
        if (origin == null) {
            throw new IOException("Scheduler public origin must be an HTTPS origin without a path, query or credentials.");
        }
        return origin;
    }
}
