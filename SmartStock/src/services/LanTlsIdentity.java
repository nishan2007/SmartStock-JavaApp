package services;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import utils.SecureCredentialStore;
import utils.SecureFilePermissions;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.List;

/** Owns the store-local HTTPS certificate and the phrase shown during admin pairing. */
public final class LanTlsIdentity {
    private static final Path KEYSTORE = Path.of(System.getProperty("user.home"), ".smartstock", "lan-api.p12");
    private static final String PASSWORD_SECRET = "lan-api-keystore-password";
    private static final String PHRASE_SECRET = "lan-api-pairing-phrase-secret";
    private static final String ALIAS = "smartstock-lan";

    private final SSLContext sslContext;
    private final String fingerprint;

    private LanTlsIdentity(SSLContext sslContext, String fingerprint) {
        this.sslContext = sslContext;
        this.fingerprint = fingerprint;
    }

    public static LanTlsIdentity loadOrCreate() throws Exception {
        Files.createDirectories(KEYSTORE.getParent());
        SecureFilePermissions.restrictDirectoryToOwner(KEYSTORE.getParent());
        String password = SecureCredentialStore.read(PASSWORD_SECRET);
        if (password == null) {
            password = LanSecurity.randomToken();
            SecureCredentialStore.write(PASSWORD_SECRET, password);
        }
        if (SecureCredentialStore.read(PHRASE_SECRET) == null) {
            SecureCredentialStore.write(PHRASE_SECRET, LanSecurity.randomToken());
        }
        if (!Files.isRegularFile(KEYSTORE)) {
            generateKeyStore(password);
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(KEYSTORE)) {
            keyStore.load(input, password.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, password.toCharArray());
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(kmf.getKeyManagers(), null, null);
        Certificate certificate = keyStore.getCertificate(ALIAS);
        String fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        return new LanTlsIdentity(context, fingerprint);
    }

    public SSLContext sslContext() {
        return sslContext;
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** DNS name embedded in the generated certificate and safe for HTTPS verification. */
    public static String tlsHostName() throws Exception {
        String hostname = InetAddress.getLocalHost().getHostName().replaceAll("[^A-Za-z0-9.-]", "");
        return hostname.isBlank() ? "smartstock-server" : hostname;
    }

    /** Changes every ten minutes and accepts the immediately previous window during setup. */
    public String currentPairingPhrase() {
        return pairingPhrase(Instant.now().getEpochSecond() / 600L);
    }

    public boolean acceptsPairingPhrase(String phrase) {
        long window = Instant.now().getEpochSecond() / 600L;
        return LanSecurity.constantTimeEquals(pairingPhrase(window), normalizePhrase(phrase))
                || LanSecurity.constantTimeEquals(pairingPhrase(window - 1), normalizePhrase(phrase));
    }

    /** Public proof binds the secret short-lived phrase to this exact TLS certificate without disclosing it. */
    public List<String> pairingProofs() {
        long window = Instant.now().getEpochSecond() / 600L;
        return List.of(pairingProof(pairingPhrase(window)), pairingProof(pairingPhrase(window - 1)));
    }

    private String pairingProof(String phrase) {
        return LanSecurity.hmacSha256(normalizePhrase(phrase), fingerprint);
    }

    private String pairingPhrase(long window) {
        String secret = SecureCredentialStore.read(PHRASE_SECRET);
        String hash = LanSecurity.sha256(secret + ":" + fingerprint + ":" + window).toUpperCase(Locale.ROOT);
        return hash.substring(0, 4) + "-" + hash.substring(4, 8) + "-" + hash.substring(8, 12);
    }

    private static String normalizePhrase(String phrase) {
        return phrase == null ? "" : phrase.trim().toUpperCase(Locale.ROOT);
    }

    private static void generateKeyStore(String password) throws Exception {
        generateKeyStore(KEYSTORE, password, tlsHostName());
        SecureFilePermissions.restrictFileToOwner(KEYSTORE);
    }

    static void generateKeyStore(Path destination, String password, String hostname)
            throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072, new SecureRandom());
        var keyPair = generator.generateKeyPair();
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=" + hostname
                + ",OU=SmartStock LAN,O=SmartStock");
        var builder = new JcaX509v3CertificateBuilder(
                subject, new BigInteger(160, new SecureRandom()).abs(),
                Date.from(now.minus(5, ChronoUnit.MINUTES)),
                Date.from(now.plus(825, ChronoUnit.DAYS)), subject, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[]{
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.dNSName, hostname),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1")
        }));
        X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                        .build(keyPair.getPrivate())));
        certificate.checkValidity();
        certificate.verify(keyPair.getPublic());

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, password.toCharArray());
        keyStore.setKeyEntry(ALIAS, keyPair.getPrivate(), password.toCharArray(),
                new Certificate[]{certificate});
        try (OutputStream output = Files.newOutputStream(destination)) {
            keyStore.store(output, password.toCharArray());
        }
    }
}
