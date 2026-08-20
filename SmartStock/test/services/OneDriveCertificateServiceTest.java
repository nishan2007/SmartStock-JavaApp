package services;

import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class OneDriveCertificateServiceTest {
    @Test void generatesMicrosoftUploadCertificateAndPkcs8PrivateKey()throws Exception{
        OneDriveCertificateService.GeneratedCertificate generated=OneDriveCertificateService.generate();
        assertTrue(generated.certificatePem().startsWith("-----BEGIN CERTIFICATE-----"));
        assertTrue(generated.privateKeyPem().startsWith("-----BEGIN PRIVATE KEY-----"));
        assertEquals(40,generated.thumbprint().length());
        CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der(generated.certificatePem())));
        assertNotNull(KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der(generated.privateKeyPem()))));
        assertEquals(generated.thumbprint(),OneDriveCertificateService.describe(generated.certificatePem()).thumbprint());
    }

    private static byte[] der(String pem){return Base64.getDecoder().decode(pem.replaceAll("-----[^-]+-----"," ").replaceAll("\\s+",""));}
}
