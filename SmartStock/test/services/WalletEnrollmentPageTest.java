package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalletEnrollmentPageTest {
    @Test void requiresExplicitSubmissionWithoutExternalResources() {
        String page = WalletEnrollmentPage.html();
        assertTrue(page.contains("form method=\"post\""));
        assertTrue(page.contains("button type=\"submit\""));
        assertFalse(page.contains("<script"));
        assertFalse(page.contains("http://"));
        assertFalse(page.contains("https://"));
        assertFalse(page.contains("SSW1"));
    }
}
