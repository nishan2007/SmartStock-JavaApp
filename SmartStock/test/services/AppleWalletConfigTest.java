package services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppleWalletConfigTest {
    @Test void enrollmentOriginMustBeHttpsWithoutCredentialsOrExtraUrlParts() {
        for (String value : new String[]{"", "http://store.example", "https://user:secret@store.example",
                "https://store.example/path", "https://store.example?token=secret", "https://store.example#fragment",
                "https:///missing-host", "https://store.example:0", "https://store.example:99999"}) {
            assertFalse(AppleWalletConfig.validPublicOrigin(value), value);
        }
        assertFalse(AppleWalletConfig.validPublicOrigin(null));
        assertTrue(AppleWalletConfig.validPublicOrigin("https://store.example"));
        assertTrue(AppleWalletConfig.validPublicOrigin("https://store.example:8443/"));
    }
    @AfterEach void clear(){
        for(String key:new String[]{"smartstock.wallet.passType","smartstock.wallet.teamId","smartstock.wallet.signingP12","smartstock.wallet.signingPassword","smartstock.wallet.wwdrCert","smartstock.wallet.publicOrigin","smartstock.wallet.nfcEnabled","smartstock.wallet.nfcProvider","smartstock.wallet.nfcPublicKey","smartstock.wallet.trustedReaders"})System.clearProperty(key);
    }

    @Test void nfcRequiresExplicitCompleteConfiguration(){
        assertFalse(AppleWalletConfig.load().nfcReady());
        System.setProperty("smartstock.wallet.passType","pass.com.example.smartstock");
        System.setProperty("smartstock.wallet.teamId","TEAM123");
        System.setProperty("smartstock.wallet.signingP12","signer.p12");
        System.setProperty("smartstock.wallet.wwdrCert","wwdr.cer");
        System.setProperty("smartstock.wallet.publicOrigin","https://store.example");
        assertTrue(AppleWalletConfig.load().barcodeReady());
        assertFalse(AppleWalletConfig.load().nfcReady());
        System.setProperty("smartstock.wallet.nfcEnabled","true");
        System.setProperty("smartstock.wallet.nfcProvider","CERTIFIED_PROVIDER");
        System.setProperty("smartstock.wallet.nfcPublicKey","PUBLIC_KEY");
        System.setProperty("smartstock.wallet.trustedReaders","reader-1");
        assertFalse(AppleWalletConfig.load().nfcReady(),"NFC stays disabled until a certified provider adapter is implemented");
    }
}
