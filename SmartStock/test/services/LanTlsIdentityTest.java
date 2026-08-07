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
}
