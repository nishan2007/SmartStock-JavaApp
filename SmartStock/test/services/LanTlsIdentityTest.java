package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanTlsIdentityTest {
    @TempDir
    Path tempDir;

    @Test
    void createsPackagedRuntimeCompatibleLanCertificateWithoutKeytool() throws Exception {
        Path destination = tempDir.resolve("lan-api.p12");
        String password = "test-password";

        LanTlsIdentity.generateKeyStore(destination, password, "test-smartstock-server");

        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(destination)) {
            store.load(input, password.toCharArray());
        }
        X509Certificate certificate = (X509Certificate) store.getCertificate("smartstock-lan");
        certificate.checkValidity();
        assertEquals("RSA", store.getKey("smartstock-lan", password.toCharArray()).getAlgorithm());
        List<String> names = certificate.getSubjectAlternativeNames().stream()
                .map(entry -> entry.get(1).toString()).toList();
        assertTrue(names.contains("localhost"));
        assertTrue(names.contains("test-smartstock-server"));
        assertTrue(names.contains("127.0.0.1"));
    }
    @Test void acceptsOnlyDeckersSubdomainsForLocalBrowserHost() {
        assertEquals("studio.deckers.gy", LanTlsIdentity.validateMobileWebHost(" Studio.Deckers.GY "));
        assertEquals("", LanTlsIdentity.validateMobileWebHost(""));
        for (String host : List.of("deckers.gy", "studio.deckers.gy.attacker.test", "https://studio.deckers.gy", "studio.deckers.gy:8444", "bad_name.deckers.gy", "-bad.deckers.gy"))
            assertThrows(IllegalArgumentException.class, () -> LanTlsIdentity.validateMobileWebHost(host));
    }

    @Test void browserCertificateCoversTheLocalDeckersDomain() throws Exception {
        Path destination = tempDir.resolve("web.p12");
        LanTlsIdentity.generateKeyStore(destination,"test-password","studio.deckers.gy");
        KeyStore store=KeyStore.getInstance("PKCS12");
        try(var input=Files.newInputStream(destination)){store.load(input,"test-password".toCharArray());}
        var certificate=(X509Certificate)store.getCertificate("smartstock-lan");
        assertTrue(certificate.getSubjectAlternativeNames().stream().anyMatch(entry -> Integer.valueOf(2).equals(entry.get(0)) && "studio.deckers.gy".equals(entry.get(1))));
    }
}
