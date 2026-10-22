package services;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Machine-local employee web settings, kept outside application update payloads. */
final class EmployeeWebConfig {
    private EmployeeWebConfig() { }

    static String publicOrigin() throws IOException {
        return publicOrigin(System.getenv("SMARTSTOCK_EMPLOYEE_PUBLIC_ORIGIN"),
                Path.of(System.getProperty("user.home"), ".smartstock", "employee-web.properties"));
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
        String origin = EmployeeWebServer.cleanOrigin(value);
        if (origin == null) {
            throw new IOException("Employee public origin must be an HTTPS origin without a path, query or credentials.");
        }
        return origin;
    }
}
