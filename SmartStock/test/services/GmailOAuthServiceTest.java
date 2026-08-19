package services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class GmailOAuthServiceTest {
    private static final String DESKTOP_JSON = """
            {"installed":{"client_id":"123456789.apps.googleusercontent.com","client_secret":"secret-value",
            "auth_uri":"https://accounts.google.com/o/oauth2/v2/auth","token_uri":"https://oauth2.googleapis.com/token",
            "redirect_uris":["http://localhost"]}}
            """;

    @Test
    void acceptsOnlyGoogleDesktopOAuthClientJson() {
        GmailOAuthService.ClientConfig config = GmailOAuthService.parseClientJson(DESKTOP_JSON);
        assertEquals("123456789.apps.googleusercontent.com", config.clientId());
        assertThrows(IllegalArgumentException.class, () -> GmailOAuthService.parseClientJson("{}"));
        assertThrows(IllegalArgumentException.class, () -> GmailOAuthService.parseClientJson("""
                {"web":{"client_id":"id","client_secret":"secret","redirect_uris":["https://example.com"]}}
                """));
        assertThrows(IllegalArgumentException.class, () -> GmailOAuthService.parseClientJson("""
                {"installed":{"client_id":"id","client_secret":"secret","auth_uri":"https://evil.example/auth",
                "token_uri":"https://oauth2.googleapis.com/token","redirect_uris":["http://localhost"]}}
                """));
    }

    @Test
    void senderAddressNormalizationSharesOneSecureTokenKey() {
        assertEquals("sales@example.com", GmailOAuthService.normalizeEmail("  Sales@Example.COM "));
        assertEquals(GmailOAuthService.tokenKey("Sales@Example.com"), GmailOAuthService.tokenKey(" sales@example.COM "));
        assertFalse(GmailOAuthService.tokenKey("sales@example.com").contains("sales@example.com"));
    }

    @Test
    void mimePreservesUnicodeHtmlBccAndAttachment() {
        GmailOAuthService.GmailMessage message = new GmailOAuthService.GmailMessage(
                "sender@gmail.com", "Déckers", "customer@example.com", "audit@example.com", "Receipt ✓",
                "Thank you ✓", "<p>Thank you ✓</p>", "receipt-1.txt", "text/plain; charset=utf-8", "Line one\nLine two");
        String mime = new String(Base64.getUrlDecoder().decode(GmailOAuthService.buildMimeMessage(message)), StandardCharsets.UTF_8);
        assertTrue(mime.contains("To: customer@example.com"));
        assertTrue(mime.contains("Bcc: audit@example.com"));
        assertTrue(mime.contains("Content-Type: multipart/mixed"));
        assertTrue(mime.contains("filename=\"receipt-1.txt\""));
        assertTrue(mime.contains("=?UTF-8?B?"));
        assertTrue(mime.contains(Base64.getEncoder().encodeToString("Line one\nLine two".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void classifiesRevokedAndTransientGoogleFailures() {
        GmailOAuthService.GmailException revoked = GmailOAuthService.googleFailure("TOKEN", 400, "{\"error\":\"invalid_grant\"}");
        assertTrue(revoked.authorizationRequired());
        assertFalse(revoked.transientFailure());
        assertEquals("AUTHORIZATION_EXPIRED", revoked.category());

        GmailOAuthService.GmailException throttled = GmailOAuthService.googleFailure("SEND", 429, "rate limited");
        assertFalse(throttled.authorizationRequired());
        assertTrue(throttled.transientFailure());
    }
}
