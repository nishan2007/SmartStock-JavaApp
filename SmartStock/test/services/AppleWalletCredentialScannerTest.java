package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppleWalletCredentialScannerTest {
    private static final String CREDENTIAL = "SSW10123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    void acceptsExactCredentialAndNormalizesScannerCase() {
        assertEquals(CREDENTIAL, AppleWalletBadgeService.normalizeScannedCredential(CREDENTIAL));
        assertEquals(CREDENTIAL, AppleWalletBadgeService.normalizeScannedCredential(CREDENTIAL.toLowerCase()));
    }

    @Test
    void removesKeyboardWedgeFramingWithoutChangingCredential() {
        assertEquals(CREDENTIAL,
                AppleWalletBadgeService.normalizeScannedCredential("]Q3" + CREDENTIAL + "\r\n"));
        assertEquals(CREDENTIAL,
                AppleWalletBadgeService.normalizeScannedCredential("\u0002" + CREDENTIAL + "\u0003"));
    }

    @Test
    void rejectsTruncatedOrExtendedCredentials() {
        assertEquals("", AppleWalletBadgeService.normalizeScannedCredential(CREDENTIAL.substring(0, 35)));
        assertEquals(CREDENTIAL, AppleWalletBadgeService.normalizeScannedCredential(CREDENTIAL + "A"));
    }
}
