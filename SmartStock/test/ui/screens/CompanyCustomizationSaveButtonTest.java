package ui.screens;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyCustomizationSaveButtonTest {
    @Test
    void settingsLoadRestoresSaveAccessForAuthorizedUsers() throws Exception {
        String source = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));
        int applySettings = source.indexOf("private void applySettings(PreferencesSnapshot snapshot)");
        int nextMethod = source.indexOf("private record PreferencesSnapshot", applySettings);
        String method = source.substring(applySettings, nextMethod);

        assertTrue(method.contains("configureActionButtons();"),
                "Applying loaded preferences must restore the permission-based Save button state");
    }

    @Test
    void receiptSaveDoesNotRunBlockingSchemaRepair() throws Exception {
        String source = Files.readString(Path.of("src/managers/ServerCompanyCustomizationRepository.java"));
        int saveMethod = source.indexOf("private static void saveReceiptSettingsToDb");
        int nextMethod = source.indexOf("private static void ensureReceiptSettingsSchema", saveMethod);
        String method = source.substring(saveMethod, nextMethod);

        assertTrue(!method.contains("ensureReceiptSettingsSchema(conn)"),
                "Normal preference saves must not run ALTER TABLE schema repair while users are active");
    }
}
