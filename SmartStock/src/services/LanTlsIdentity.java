package services;

import utils.SecureCredentialStore;
import utils.SecureFilePermissions;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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
        String javaHome = System.getProperty("java.home");
        String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "keytool.exe" : "keytool";
        Path keytool = Path.of(javaHome, "bin", executable);
        String hostname = InetAddress.getLocalHost().getHostName().replaceAll("[^A-Za-z0-9.-]", "");
        ProcessBuilder builder = new ProcessBuilder(
                keytool.toString(), "-genkeypair",
                "-alias", ALIAS,
                "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA",
                "-validity", "825",
                "-dname", "CN=" + (hostname.isBlank() ? "SmartStock Server" : hostname) + ", OU=SmartStock LAN, O=SmartStock",
                "-ext", "SAN=dns:localhost,dns:" + (hostname.isBlank() ? "smartstock-server" : hostname) + ",ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", KEYSTORE.toString(),
                "-storepass", password, "-keypass", password, "-noprompt"
        );
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out while creating the SmartStock LAN certificate.");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Could not create the SmartStock LAN certificate: " + output.trim());
        }
        SecureFilePermissions.restrictFileToOwner(KEYSTORE);
    }
}
