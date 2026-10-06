package app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.*;
import com.sun.net.httpserver.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerUpdateSupportTest {
    @TempDir Path root;
    private static final String HEALTH = "{\"success\":true,\"data\":{\"service\":\"SmartStock LAN Service\","
            + "\"status\":\"ok\",\"localSchemaReady\":true,\"discoveryReady\":true,\"appVersion\":\"1.0.241\"}}";

    @Test void requiresBothUpdatedHealthyApiAndDiscovery() {
        assertTrue(ServerUpdateSupport.healthyResponse(HEALTH, "1.0.241"));
        assertFalse(ServerUpdateSupport.healthyResponse(HEALTH, "1.0.240"));
        for (String field : new String[]{"success", "localSchemaReady", "discoveryReady"})
            assertFalse(ServerUpdateSupport.healthyResponse(HEALTH.replace("\"" + field + "\":true", "\"" + field + "\":false"), "1.0.241"));
        assertFalse(ServerUpdateSupport.healthyResponse("{\"status\":\"ok\"}", "1.0.241"));
    }

    @Test void retriesStartupAndNeverTreatsAnExitedServerAsReady() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        ServerUpdateSupport.awaitReady(Duration.ofSeconds(2), () -> {
            if (attempts.incrementAndGet() < 2) throw new IOException("starting");
        });
        assertEquals(2, attempts.get());
        IOException failure = assertThrows(IOException.class, () ->
                ServerUpdateSupport.awaitReady(Duration.ofMillis(25), () -> { throw new IOException("process exited with code 1"); }));
        assertTrue(failure.getMessage().contains("sync-service.log"));
        assertTrue(failure.getMessage().contains("process exited with code 1"));
    }

    @Test void registersDoNotRequireServerAndOlderServerManifestsStillDo() throws Exception {
        Path app = root.resolve(".smartstock/sync-service/app");
        Properties props = new Properties();
        props.setProperty("sync.service.app.dir", app.toString());
        assertFalse(SmartStockUpdater.requiresServer(props));
        Files.createDirectories(app);
        assertTrue(SmartStockUpdater.requiresServer(props));
        props.setProperty("sync.service.required", "false");
        assertFalse(SmartStockUpdater.requiresServer(props));
        props.setProperty("sync.service.required", "true");
        assertTrue(SmartStockUpdater.requiresServer(props));
    }

    @Test void preservesProductionEnvironmentInOlderLaunchersAndManifests() throws Exception {
        Path launcher = root.resolve("run.cmd");
        Files.writeString(launcher, "@echo off\r\nset \"SMARTSTOCK_ENVIRONMENT=production\"\r\n");
        Properties props = new Properties();
        assertEquals("production", ServerUpdateSupport.environment(props, launcher, root));
        Files.writeString(launcher, "replaced launcher");
        assertEquals("production", ServerUpdateSupport.environment(props, launcher, root));
        String content = ServerUpdateSupport.windowsLauncher(root.resolve("java.exe"),
                root.resolve(".smartstock/sync-service/app"), root, "production");
        assertTrue(content.contains("-Dsmartstock.environment=production"));
        assertThrows(IllegalArgumentException.class, () -> ServerUpdateSupport.windowsLauncher(
                root.resolve("java.exe"), root.resolve("app"), root, "production & injected"));
    }

    @Test void persistentLaunchersUseAbsoluteRuntimeProfileAndLogsWithoutVersion() {
        String content = ServerUpdateSupport.windowsLauncher(root.resolve("runtime/bin/java.exe"),
                root.resolve(".smartstock/sync-service/app"), root);
        assertTrue(content.contains(root.resolve("runtime/bin/java.exe").toString()));
        assertTrue(content.contains("-Duser.home=\"" + root + "\""));
        assertTrue(content.contains("sync-service.log"));
        assertTrue(content.contains("Server exited with code"));
        assertTrue(content.contains("inventory-management-*.jar"));
        assertFalse(content.contains("java -jar"));
    }

    @Test void verifiesRealPinnedHttpsAndDiscoveryAndRejectsChangedIdentity() throws Exception {
        String password = UUID.randomUUID().toString();
        Path keyStoreFile = root.resolve("fixture.p12");
        var generate = services.LanTlsIdentity.class.getDeclaredMethod("generateKeyStore", Path.class, String.class, String.class);
        generate.setAccessible(true);
        generate.invoke(null, keyStoreFile, password, "localhost");
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keyStoreFile)) { keys.load(input, password.toCharArray()); }
        X509Certificate certificate = (X509Certificate) keys.getCertificate("smartstock-lan");
        String pin = ServerUpdateSupport.fingerprint(certificate);
        Files.createDirectories(root.resolve(".smartstock"));
        Files.write(root.resolve(".smartstock/lan-api.cer"), certificate.getEncoded());
        KeyManagerFactory manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, password.toCharArray());
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(manager.getKeyManagers(), null, null);
        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        AtomicReference<String> health = new AtomicReference<>(HEALTH);
        server.createContext("/v1/health", exchange -> {
            byte[] body = health.get().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try (DatagramSocket discovery = new DatagramSocket(new InetSocketAddress("127.0.0.1", 0))) {
            AtomicReference<String> advertisedPin = new AtomicReference<>(pin);
            Thread responder = new Thread(() -> {
                try {
                    while (!discovery.isClosed()) {
                        DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
                        discovery.receive(request);
                        byte[] reply = ("{\"service\":\"SmartStock LAN Service\",\"certificateFingerprint\":\""
                                + advertisedPin.get() + "\",\"port\":" + server.getAddress().getPort() + "}")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        discovery.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
                    }
                } catch (Exception ignored) { }
            });
            responder.setDaemon(true);
            responder.start();
            int port = server.getAddress().getPort(), udpPort = discovery.getLocalPort();
            ServerUpdateSupport.probe(root, pin, "1.0.241", port, udpPort);
            assertThrows(java.security.cert.CertificateException.class,
                    () -> ServerUpdateSupport.probe(root, "0".repeat(64), "1.0.241", port, udpPort));
            health.set(HEALTH.replace("\"localSchemaReady\":true", "\"localSchemaReady\":false"));
            assertThrows(IOException.class, () -> ServerUpdateSupport.probe(root, pin, "1.0.241", port, udpPort));
            health.set(HEALTH);
            advertisedPin.set("0".repeat(64));
            assertThrows(IOException.class, () -> ServerUpdateSupport.probe(root, pin, "1.0.241", port, udpPort));
        } finally { server.stop(0); }
    }

    @Test void commandLauncherWorksWithoutGlobalJavaAndCapturesFailure() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));
        Path app = root.resolve(".smartstock/sync-service/app");
        Files.createDirectories(app);
        Path source = root.resolve("FixtureServer.java");
        Files.writeString(source, "public class FixtureServer { public static void main(String[] args) {"
                + "System.out.println(System.getProperty(\"user.home\"));"
                + "System.err.println(\"fixture startup failed\");System.exit(7);}} ");
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", root.toString(), source.toString()));
        var manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "FixtureServer");
        try (var jar = new java.util.jar.JarOutputStream(Files.newOutputStream(app.resolve("inventory-management-1.0.241.jar")), manifest)) {
            jar.putNextEntry(new java.util.jar.JarEntry("FixtureServer.class"));
            jar.write(Files.readAllBytes(root.resolve("FixtureServer.class")));
            jar.closeEntry();
        }
        Path launcher = app.getParent().resolve("run-smartstock-sync-service.cmd");
        Files.writeString(launcher, ServerUpdateSupport.windowsLauncher(
                Path.of(System.getProperty("java.home"), "bin", "java.exe"), app, root));
        var builder = new ProcessBuilder(System.getenv("SystemRoot") + "\\System32\\cmd.exe", "/d", "/c", launcher.toString());
        builder.environment().put("PATH", root.toString());
        builder.redirectErrorStream(true);
        builder.redirectOutput(root.resolve("launcher-output.log").toFile());
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(7, process.exitValue());
            String log = Files.readString(app.getParent().resolve("sync-service.log"));
            assertTrue(log.contains(root.toString()));
            assertTrue(log.contains("fixture startup failed"));
            assertTrue(log.contains("Server exited with code 7"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    @Test void repairsMissingTaskAndStaleShortcutWithoutTouchingSystemTasks() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("win"));
        Path app = root.resolve(".smartstock/sync-service/app");
        Files.createDirectories(app);
        Path javaPath = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        Files.writeString(app.getParent().resolve("run-smartstock-sync-service.cmd"),
                ServerUpdateSupport.windowsLauncher(javaPath, app, root));
        String actual = SmartStockUpdater.windowsSyncTaskUpdateCommand("FixtureOnly", javaPath, app, "unused.jar", "")
                .get(6);
        String mock = """
            $ErrorActionPreference='Stop'
            $script:Task=$null;$script:MockRegistrationCount=0;$script:MockUpdateCount=0
            function New-ScheduledTaskAction {param($Execute,$Argument,$WorkingDirectory) [pscustomobject]@{Execute=$Execute;Arguments=$Argument;WorkingDirectory=$WorkingDirectory}}
            function Get-ScheduledTask {[CmdletBinding()]param($TaskName) $script:Task}
            function New-ScheduledTaskSettingsSet {param([switch]$StartWhenAvailable,$RestartCount,$RestartInterval,$ExecutionTimeLimit,$MultipleInstances) [pscustomobject]@{RestartCount=$RestartCount}}
            function New-ScheduledTaskTrigger {param([switch]$AtLogOn,$User) [pscustomobject]@{User=$User}}
            function New-ScheduledTaskPrincipal {param($UserId,$LogonType,$RunLevel) [pscustomobject]@{UserId=$UserId}}
            function Register-ScheduledTask {[CmdletBinding()]param($TaskName,$Action,$Trigger,$Principal,$Settings,$Description,[switch]$Force)
              $script:MockRegistrationCount++;$script:Task=[pscustomobject]@{Actions=@($Action);Principal=$Principal};$script:Task}
            function Set-ScheduledTask {[CmdletBinding()]param($TaskName,$Action,$Settings) $script:MockUpdateCount++;$script:Task.Actions=@($Action);$script:Task}
            function Enable-ScheduledTask {param($TaskName) $script:Task}
            """;
        String shortcut = app.getParent().resolve("SmartStockServer.lnk").toString().replace("'", "''");
        String checks = "\nif($script:MockRegistrationCount -ne 1 -or $script:MockUpdateCount -ne 1){throw 'Wrong repair branch'};"
                + "$shortcut=(New-Object -ComObject WScript.Shell).CreateShortcut('" + shortcut + "');"
                + "if($shortcut.Arguments -notlike '*run-smartstock-sync-service.cmd*' -or $shortcut.Arguments -like '*1.0.228*'){throw 'Stale shortcut'};";
        String stale = "$stale=(New-Object -ComObject WScript.Shell).CreateShortcut('" + shortcut + "');"
                + "$stale.TargetPath='" + javaPath.toString().replace("'", "''") + "';$stale.Arguments='-jar inventory-management-1.0.228.jar --sync-service';$stale.Save();\n";
        Path script = root.resolve("supervisor-fixture.ps1");
        Files.writeString(script, mock + stale + actual + "\n" + actual + checks);
        Path output = root.resolve("supervisor-output.log");
        var command = SmartStockUpdater.windowsSyncTaskUpdateCommand("FixtureOnly", javaPath, app, "unused.jar", "");
        Process process = new ProcessBuilder(command.get(0), "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", script.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, process.exitValue(), Files.readString(output));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
