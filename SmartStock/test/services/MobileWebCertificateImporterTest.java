package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class MobileWebCertificateImporterTest {
    @TempDir Path temporary;
    private static final String HOST="store-7-lan.deckers.gy",PASSWORD="disposable-test-password";
    private void run(Path source,Path target,String host)throws Exception{
        InputStream original=System.in;
        try{System.setIn(new ByteArrayInputStream((PASSWORD+"\n").getBytes(StandardCharsets.UTF_8)));
            MobileWebCertificateImporter.main(new String[]{source.toString(),target.toString(),host});
        }finally{System.setIn(original);}
    }
    @Test void refusesSelfSignedLeafAndCannotOverwriteRegisterIdentity()throws Exception{
        Path source=temporary.resolve("source.p12"),target=temporary.resolve("mobile-web-"+HOST+".p12");
        LanTlsIdentity.generateKeyStore(source,PASSWORD,HOST);
        assertThrows(IllegalArgumentException.class,()->run(source,target,HOST));
        assertFalse(Files.exists(target));
        Path register=temporary.resolve("lan-api.p12");Files.writeString(register,"preserved");
        assertThrows(IllegalArgumentException.class,()->run(source,register,HOST));
        assertEquals("preserved",Files.readString(register));
    }
    @Test void importsValidChainWithCanonicalAliasAndRejectsWrongHostname()throws Exception{
        KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
        KeyPair rootKeys=generator.generateKeyPair(),leafKeys=generator.generateKeyPair();
        X500Name rootName=new X500Name("CN=Disposable test CA");
        X509Certificate root=certificate(rootName,rootName,rootKeys.getPublic(),rootKeys.getPrivate(),true);
        X509Certificate leaf=certificate(rootName,new X500Name("CN="+HOST),leafKeys.getPublic(),rootKeys.getPrivate(),false);
        KeyStore source=KeyStore.getInstance("PKCS12");source.load(null,PASSWORD.toCharArray());
        source.setKeyEntry("original-alias",leafKeys.getPrivate(),PASSWORD.toCharArray(),new Certificate[]{leaf,root});
        Path input=temporary.resolve("full-chain.pfx"),output=temporary.resolve("mobile-web-"+HOST+".p12");
        try(var stream=Files.newOutputStream(input)){source.store(stream,PASSWORD.toCharArray());}
        run(input,output,HOST);
        KeyStore imported=KeyStore.getInstance("PKCS12");
        try(var stream=Files.newInputStream(output)){imported.load(stream,PASSWORD.toCharArray());}
        assertEquals(2,imported.getCertificateChain("smartstock-lan").length);
        assertArrayEquals(leaf.getEncoded(),imported.getCertificate("smartstock-lan").getEncoded());
        Path wrong=temporary.resolve("mobile-web-store-8-lan.deckers.gy.p12");
        assertThrows(IllegalArgumentException.class,()->run(input,wrong,"store-8-lan.deckers.gy"));
        assertFalse(Files.exists(wrong));
    }
    private static X509Certificate certificate(X500Name issuer,X500Name subject,PublicKey key,PrivateKey signing,boolean ca)throws Exception{
        Instant now=Instant.now();
        var builder=new JcaX509v3CertificateBuilder(issuer,BigInteger.valueOf(ca?1:2),Date.from(now.minusSeconds(60)),Date.from(now.plusSeconds(3600)),subject,key);
        builder.addExtension(Extension.basicConstraints,true,new BasicConstraints(ca));
        if(!ca){
            builder.addExtension(Extension.subjectAlternativeName,false,new GeneralNames(new GeneralName(GeneralName.dNSName,HOST)));
            builder.addExtension(Extension.extendedKeyUsage,false,new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
        }
        return new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signing)));
    }
}
