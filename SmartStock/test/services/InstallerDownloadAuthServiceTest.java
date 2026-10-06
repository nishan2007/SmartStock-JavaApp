package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstallerDownloadAuthServiceTest {
    @Test void websiteKeyDerivationIsStableAndDistinct() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("SMARTSTOCK_INSTALLER_AUTH_KEY")==null);
        String key="test-website-key-with-at-least-32-characters";
        String derived=InstallerDownloadAuthService.originKey(key);
        assertEquals(64,derived.length());
        assertEquals(derived,InstallerDownloadAuthService.originKey(key));
        assertNotEquals(key,derived);
        assertNotEquals(derived,InstallerDownloadAuthService.originKey(key+"other"));
    }
}
