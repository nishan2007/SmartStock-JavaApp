package managers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

class CompanyPreferenceFileTest {
    @TempDir Path directory;

    @Test void recoversSettingsOnBothSidesOfDamagedEntry() throws Exception {
        Path file = directory.resolve("preferences.properties");
        Files.writeString(file, "# comment ending in backslash \\\ncustom_orders.minimum_deposit_percent=45\n"
                + "broken=" + "\\u00XX\n"
                + "custom_order_slip.enabled=true\n"
                + "title=Custom\\\n  Order\n"
                + "unicode=" + "\\u00e9\n");
        Properties result = CompanyPreferenceFile.read(file);
        assertEquals("45", result.getProperty("custom_orders.minimum_deposit_percent"));
        assertEquals("true", result.getProperty("custom_order_slip.enabled"));
        assertEquals("CustomOrder", result.getProperty("title"));
        assertEquals("é", result.getProperty("unicode"));
        assertNull(result.getProperty("broken"));
    }

    @Test void replacesExistingFileAndRoundTripsEscapes() throws Exception {
        Path file = directory.resolve("preferences.properties");
        Files.writeString(file, "old=value\n");
        Properties settings = new Properties();
        settings.setProperty("title", "Order é\nC:\\users");
        CompanyPreferenceFile.write(file, settings);
        assertEquals(settings, CompanyPreferenceFile.read(file));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }
}
