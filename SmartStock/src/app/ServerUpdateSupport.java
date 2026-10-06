package app;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import javax.net.ssl.*;

/** JDK-only server restart support: the staged updater has no dependency classpath. */
public final class ServerUpdateSupport {
    private ServerUpdateSupport() {}

    static Path home(Path app) {
        Path service = app.toAbsolutePath().getParent();
        if (service == null || service.getParent() == null || service.getParent().getParent() == null)
            throw new IllegalArgumentException("Invalid background server profile path.");
        return service.getParent().getParent();
    }

    public static String windowsLauncher(Path java, Path app, Path userHome) {
        return windowsLauncher(java, app, userHome, "");
    }

    public static String windowsLauncher(Path java, Path app, Path userHome, String environment) {
        if (!environment.isEmpty() && !environment.matches("production|development"))
            throw new IllegalArgumentException("Invalid background server environment.");
        Path service = app.getParent();
        return "@echo off\r\nsetlocal\r\ncd /d \"" + batch(app) + "\"\r\n"
                + "set \"SMARTSTOCK_SERVER_JAR=\"\r\n"
                + "for %%F in (inventory-management-*.jar) do if exist \"%%F\" set \"SMARTSTOCK_SERVER_JAR=%%F\"\r\n"
                + "if not defined SMARTSTOCK_SERVER_JAR exit /b 2\r\n"
                + "echo [%date% %time%] Starting SmartStock background server using \"" + batch(java)
                + "\" and %SMARTSTOCK_SERVER_JAR% >> \"" + batch(service.resolve("sync-service.log")) + "\"\r\n"
                + "\"" + batch(java) + "\" -Duser.home=\"" + batch(userHome)
                + "\"" + (environment.isEmpty() ? "" : " -Dsmartstock.environment=" + environment)
                + " -jar \"%SMARTSTOCK_SERVER_JAR%\" --sync-service >> \""
                + batch(service.resolve("sync-service.log")) + "\" 2>&1\r\n"
                + "set \"SMARTSTOCK_EXIT=%errorlevel%\"\r\n"
                + "echo [%date% %time%] Server exited with code %SMARTSTOCK_EXIT% >> \""
                + batch(service.resolve("sync-service.log")) + "\"\r\n"
                + "exit /b %SMARTSTOCK_EXIT%\r\n";
    }

    static String environment(Properties props, Path launcher, Path userHome) throws IOException {
        String selected = props.getProperty("sync.service.environment", "");
        if (selected.isBlank() && Files.isRegularFile(launcher)) {
            var match = Pattern.compile("(?im)^set\\s+\"?SMARTSTOCK_ENVIRONMENT=(production|development|test)\"?\\s*$")
                    .matcher(Files.readString(launcher));
            if (match.find()) selected = match.group(1).toLowerCase(Locale.ROOT);
        }
        if (selected.isBlank()) {
            Path active = userHome.resolve(".smartstock/active-environment.properties");
            Properties selection = new Properties();
            if (Files.isRegularFile(active)) try (var input = Files.newInputStream(active)) { selection.load(input); }
            selected = selection.getProperty("environment", "development");
        }
        if (selected.equals("test")) selected = "development";
        if (!selected.matches("production|development")) throw new IOException("Invalid saved server environment.");
        props.setProperty("sync.service.environment", selected);
        return selected;
    }

    private static String batch(Path path) {
        String value = path.toString();
        if (value.contains("\r") || value.contains("\n") || value.contains("\""))
            throw new IllegalArgumentException("Unsupported server launcher path.");
        return value.replace("%", "%%");
    }

    static String fingerprint(X509Certificate certificate) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
    }

    static SSLContext pinnedContext(X509Certificate certificate) throws GeneralSecurityException {
        certificate.checkValidity();
        KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
        try { trust.load(null, null); } catch (IOException ex) { throw new GeneralSecurityException(ex); }
        trust.setCertificateEntry("store", certificate);
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, factory.getTrustManagers(), null);
        return context;
    }

    static boolean stringField(String json, String key, String value) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"" + Pattern.quote(value) + "\"").matcher(json).find();
    }
    static boolean trueField(String json, String key) {
        return Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*true(?:\\s*[,}])").matcher(json).find();
    }
    static boolean healthyResponse(String body, String version) {
        return trueField(body, "success") && stringField(body, "service", "SmartStock LAN Service")
                && stringField(body, "status", "ok") && trueField(body, "localSchemaReady")
                && trueField(body, "discoveryReady") && stringField(body, "appVersion", version);
    }

    static void probe(Path userHome, String expectedFingerprint, String version, int port, int discoveryPort) throws Exception {
        Path certificatePath = userHome.resolve(".smartstock/lan-api.cer");
        X509Certificate certificate;
        try (var input = Files.newInputStream(certificatePath)) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        String pin = fingerprint(certificate);
        if (!expectedFingerprint.isBlank() && !expectedFingerprint.equalsIgnoreCase(pin))
            throw new CertificateException("The store certificate changed during the update.");
        HttpsURLConnection connection = (HttpsURLConnection) URI.create("https://127.0.0.1:" + port + "/v1/health").toURL().openConnection();
        connection.setSSLSocketFactory(pinnedContext(certificate).getSocketFactory());
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Server health endpoint is not ready.");
            String body;
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(65537);
                if (bytes.length > 65536) throw new IOException("Unexpected health response size.");
                body = new String(bytes, StandardCharsets.UTF_8);
            }
            if (!healthyResponse(body, version)) throw new IOException("Updated server version, local schema or discovery is not ready.");
        } finally { connection.disconnect(); }
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("127.0.0.1"), discoveryPort);
            socket.setSoTimeout(2000);
            byte[] request = "SMARTSTOCK_DISCOVER_V1".getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(request, request.length));
            byte[] bytes = new byte[8192];
            DatagramPacket reply = new DatagramPacket(bytes, bytes.length);
            socket.receive(reply);
            String body = new String(reply.getData(), reply.getOffset(), reply.getLength(), StandardCharsets.UTF_8);
            if (!stringField(body, "service", "SmartStock LAN Service") || !stringField(body, "certificateFingerprint", pin)
                    || !Pattern.compile("\"port\"\\s*:\\s*" + port + "(?:\\s*[,}])").matcher(body).find())
                throw new IOException("LAN discovery does not match the updated HTTPS server.");
        }
    }

    interface ReadinessProbe { void check() throws Exception; }
    static void awaitReady(Duration timeout, ReadinessProbe probe) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception last = null;
        do {
            try { probe.check(); return; }
            catch (InterruptedException ex) { throw ex; }
            catch (Exception ex) { last = ex; }
            if (System.nanoTime() >= deadline) break;
            Thread.sleep(Math.min(500, Math.max(1, (deadline-System.nanoTime())/1_000_000)));
        } while (System.nanoTime() < deadline);
        throw new IOException("The updated background server did not become ready. Check sync-service.log. "
                + (last == null ? "" : last.getMessage()), last);
    }
}
