package services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Account-free Cloudflare tunnel used only when no permanent scheduler origin is configured. */
final class CloudflareQuickTunnel implements AutoCloseable {
    private static final Pattern URL = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");
    private static final Duration START_TIMEOUT = Duration.ofSeconds(35);
    private final Process process;
    private final String publicOrigin;

    private CloudflareQuickTunnel(Process process, String publicOrigin) {
        this.process = process;
        this.publicOrigin = publicOrigin;
    }

    static CloudflareQuickTunnel start(int schedulerPort) throws Exception {
        String executable = CloudflareBinary.executable();
        Process process = new ProcessBuilder(executable, "tunnel", "--no-autoupdate", "--no-tls-verify",
                "--url", "https://127.0.0.1:" + schedulerPort).redirectErrorStream(true).start();
        LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>(32);
        Thread reader = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) lines.offer(line);
            } catch (Exception ignored) { }
        }, "smartstock-cloudflare-quick-tunnel-output");
        reader.setDaemon(true);
        reader.start();
        long deadline = System.nanoTime() + START_TIMEOUT.toNanos();
        boolean handedOff = false;
        try {
        while (System.nanoTime() < deadline) {
            String line = lines.poll(500, TimeUnit.MILLISECONDS);
            if (line != null) {
                Matcher matcher = URL.matcher(line);
                if (matcher.find()) {
                    handedOff = true;
                    return new CloudflareQuickTunnel(process, matcher.group());
                }
            }
            if (!process.isAlive()) break;
        }
        throw new IllegalStateException("Cloudflare Quick Tunnel did not start. Check the bundled client and internet connection.");
        } finally {
            if (!handedOff) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    String publicOrigin() { return publicOrigin; }
    boolean isAlive() { return process.isAlive(); }

    @Override public void close() {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
