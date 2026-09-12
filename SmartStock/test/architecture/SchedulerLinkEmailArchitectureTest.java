package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerLinkEmailArchitectureTest {
    @Test void preferenceRuntimeAndOutboxAreWiredEndToEnd() throws Exception {
        String migration = Files.readString(Path.of("database/migrations/v1_after/20260904120000_scheduler_link_email_notifications.sql"));
        String api = Files.readString(Path.of("src/services/LanApiServer.java"));
        String runtime = Files.readString(Path.of("src/services/SchedulerWebRuntimeController.java"));
        String notifier = Files.readString(Path.of("src/services/SchedulerLinkNotificationService.java"));
        String outbox = Files.readString(Path.of("src/services/ServerEmailOutboxService.java"));
        String preferences = Files.readString(Path.of("src/ui/screens/CompanyCustomization.java"));

        assertTrue(migration.contains("scheduler_link_email_enabled"));
        assertTrue(migration.contains("last_notified_origin"));
        assertTrue(api.contains("SCHEDULER_LINK_EMAIL"));
        assertTrue(preferences.contains("Email the scheduler link whenever it changes"));
        assertTrue(runtime.contains("SchedulerLinkNotificationService.notifyIfChanged"));
        assertTrue(notifier.contains("FOR UPDATE"));
        assertTrue(notifier.contains("last_notified_recipient"));
        assertTrue(outbox.contains("SCHEDULER_LINK"));
    }
}
