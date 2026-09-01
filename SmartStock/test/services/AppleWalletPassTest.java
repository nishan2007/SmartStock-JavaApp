package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class AppleWalletPassTest {
    @TempDir Path directory;

    @Test void signedPassHasVerifiedManifestAndOpaqueBarcodeOnly() throws Exception {
        // Ephemeral self-signed fixture, never an Apple certificate or production key.
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var subject = new X500Name("CN=SmartStock TEST ONLY");
        var holder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                Date.from(Instant.now().minusSeconds(60)), Date.from(Instant.now().plusSeconds(3600)),
                subject, pair.getPublic()).build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()));
        var certificate = new JcaX509CertificateConverter().getCertificate(holder);
        char[] password = "ephemeral-test-only".toCharArray();
        var store = KeyStore.getInstance("PKCS12");
        store.load(null, password);
        store.setKeyEntry("test", pair.getPrivate(), password, new Certificate[]{certificate});
        Path p12 = directory.resolve("test.p12"), wwdr = directory.resolve("test.cer");
        try (var out = Files.newOutputStream(p12)) { store.store(out, password); }
        Files.write(wwdr, certificate.getEncoded());
        var config = new AppleWalletConfig("pass.example.test", "TESTTEAM", p12, password, wwdr,
                "https://store.example", true, "UNIMPLEMENTED", "test", "test");
        String credential = "SSW10123456789ABCDEFGHJKMNPQRSTVWXYZ";
        byte[] archive = AppleWalletBadgeService.buildPass(config, "test-serial", "Test Employee", "testuser", credential);
        Map<String, byte[]> entries = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                assertNull(entries.put(entry.getName(), zip.readAllBytes()), "No duplicate archive entries");
            }
        }
        assertEquals(6, entries.size());
        JsonObject pass = JsonParser.parseString(new String(entries.get("pass.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(credential, pass.getAsJsonArray("barcodes").get(0).getAsJsonObject().get("message").getAsString());
        assertFalse(pass.has("nfc"), "Unimplemented NFC cannot be enabled by configuration");
        assertFalse(pass.has("authenticationToken"));
        var manifest = JsonParser.parseString(new String(entries.get("manifest.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(4, manifest.size());
        for (var entry : manifest.entrySet()) {
            assertEquals(entry.getValue().getAsString(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(entries.get(entry.getKey()))));
        }
        var signed = new CMSSignedData(new CMSProcessableByteArray(entries.get("manifest.json")), entries.get("signature"));
        assertEquals(1, signed.getSignerInfos().size());
        for (var signer : signed.getSignerInfos().getSigners()) {
            assertTrue(signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(holder)));
        }
        byte[] altered = entries.get("manifest.json").clone();
        altered[0] ^= 1;
        var tampered = new CMSSignedData(new CMSProcessableByteArray(altered), entries.get("signature"));
        assertThrows(org.bouncycastle.cms.CMSSignerDigestMismatchException.class, () ->
                tampered.getSignerInfos().getSigners().iterator().next()
                        .verify(new JcaSimpleSignerInfoVerifierBuilder().build(holder)));

        String image = java.util.Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(
                new java.awt.image.BufferedImage(40,20,java.awt.image.BufferedImage.TYPE_INT_RGB),120,60,100));
        var template = new WalletBadgeTemplate("Deckers", "Team Badge", "#112233", "#FFFFFF", "#FF7000",
                java.util.List.of(new WalletBadgeTemplate.Field(true,"primaryFields","NAME","TEAM MEMBER",""),
                        new WalletBadgeTemplate.Field(false,"secondaryFields","USERNAME","USERNAME",""),
                        new WalletBadgeTemplate.Field(true,"backFields","TEXT","NOTE","Return to reception")),
                image,image,50,75,false,new WalletBadgeTemplate.Poster(image,image,75));
        byte[] custom = AppleWalletBadgeService.buildPass(config,"custom-serial",credential,template,
                Map.of("NAME","Alice","USERNAME","hidden-username"),null);
        Map<String,byte[]> customFiles = new HashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(custom))){
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())customFiles.put(entry.getName(),zip.readAllBytes());
        }
        String customJson=new String(customFiles.get("pass.json"),StandardCharsets.UTF_8);
        assertTrue(customJson.contains("Deckers"));assertTrue(customJson.contains("Return to reception"));
        assertFalse(customJson.contains("hidden-username"));assertTrue(customJson.contains(credential));
        assertTrue(customJson.contains("posterGeneric"));assertTrue(customJson.contains("\"generic\""));
        var artwork=javax.imageio.ImageIO.read(new ByteArrayInputStream(customFiles.get("artwork@3x.png")));
        assertEquals(1074,artwork.getWidth());assertEquals(1344,artwork.getHeight());
        var primaryLogo=javax.imageio.ImageIO.read(new ByteArrayInputStream(customFiles.get("primaryLogo@3x.png")));
        assertEquals(378,primaryLogo.getWidth());assertEquals(90,primaryLogo.getHeight());
        var thumbnail=javax.imageio.ImageIO.read(new ByteArrayInputStream(customFiles.get("thumbnail@3x.png")));
        assertEquals(270,thumbnail.getWidth());
        var logo=javax.imageio.ImageIO.read(new ByteArrayInputStream(customFiles.get("logo@3x.png")));
        assertEquals(480,logo.getWidth());assertEquals(150,logo.getHeight());
        var customManifest=JsonParser.parseString(new String(customFiles.get("manifest.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        for(var entry:customManifest.entrySet())assertEquals(entry.getValue().getAsString(),HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(customFiles.get(entry.getKey()))));
        var customSignature=new CMSSignedData(new CMSProcessableByteArray(customFiles.get("manifest.json")),customFiles.get("signature"));
        assertTrue(customSignature.getSignerInfos().getSigners().iterator().next().verify(new JcaSimpleSignerInfoVerifierBuilder().build(holder)));
    }
}
