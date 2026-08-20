package services;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

/** Generates a server-local Entra client certificate without exposing the private key. */
public final class OneDriveCertificateService {
    private OneDriveCertificateService() { }

    public static GeneratedCertificate generate() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048,new SecureRandom());
        KeyPair pair=generator.generateKeyPair();
        Instant now=Instant.now(),expires=now.plus(730,ChronoUnit.DAYS);
        X500Name subject=new X500Name("CN=SmartStock Image Storage");
        BigInteger serial=new BigInteger(160,new SecureRandom()).abs();
        ContentSigner signer=new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        X509CertificateHolder holder=new JcaX509v3CertificateBuilder(subject,serial,
                Date.from(now.minus(5,ChronoUnit.MINUTES)),Date.from(expires),subject,pair.getPublic())
                .build(signer);
        X509Certificate certificate=new JcaX509CertificateConverter().getCertificate(holder);
        certificate.verify(pair.getPublic());
        String certificatePem=pem("CERTIFICATE",certificate.getEncoded());
        String privateKeyPem=pem("PRIVATE KEY",pair.getPrivate().getEncoded());
        String thumbprint=hex(MessageDigest.getInstance("SHA-1").digest(certificate.getEncoded()));
        return new GeneratedCertificate(certificatePem,privateKeyPem,thumbprint,expires.toEpochMilli());
    }

    public static PublicCertificate describe(String certificatePem)throws Exception{
        String body=certificatePem.replace("-----BEGIN CERTIFICATE-----","")
                .replace("-----END CERTIFICATE-----","").replaceAll("\\s+","");
        X509Certificate certificate=(X509Certificate)CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(body)));
        return new PublicCertificate(certificatePem,hex(MessageDigest.getInstance("SHA-1")
                .digest(certificate.getEncoded())),certificate.getNotAfter().getTime());
    }

    static String pem(String label,byte[] bytes){
        return "-----BEGIN "+label+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(bytes)
                +"\n-----END "+label+"-----";
    }
    private static String hex(byte[] bytes){
        StringBuilder out=new StringBuilder(bytes.length*2);
        for(byte value:bytes)out.append(String.format("%02X",value));
        return out.toString();
    }

    public record GeneratedCertificate(String certificatePem,String privateKeyPem,String thumbprint,
                                       long expiresAtEpochMillis) { }
    public record PublicCertificate(String certificatePem,String thumbprint,long expiresAtEpochMillis) { }
}
