package services;

import com.google.gson.Gson;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal UDP discovery; HTTPS pinning and the admin phrase authenticate the result. */
final class LanDiscoveryService implements AutoCloseable {
    static final int DISCOVERY_PORT = 18443;
    static final String REQUEST = "SMARTSTOCK_DISCOVER_V1";
    private static final Gson GSON = new Gson();

    private final DatagramSocket socket;
    private final Thread thread;
    private volatile boolean running = true;

    private LanDiscoveryService(DatagramSocket socket, Thread thread) {
        this.socket = socket;
        this.thread = thread;
    }

    static LanDiscoveryService start(int apiPort, LanTlsIdentity identity,
                                     DiscoveryIdentity advertisedIdentity) throws Exception {
        DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT);
        socket.setBroadcast(true);
        final LanDiscoveryService[] holder = new LanDiscoveryService[1];
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (holder[0] == null || holder[0].running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String request = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    if (!REQUEST.equals(request)) continue;
                    // Advertise the name covered by the TLS certificate. A numeric DHCP
                    // address is rejected when it is not present in the certificate SAN.
                    String host = LanTlsIdentity.tlsHostName();
                    var proofs = identity.pairingProofs();
                    byte[] response = GSON.toJson(Map.ofEntries(
                            Map.entry("service", "SmartStock LAN Service"),
                            Map.entry("host", host),
                            Map.entry("port", apiPort),
                            Map.entry("environment", advertisedIdentity.environment()),
                            Map.entry("storeName", advertisedIdentity.storeName()),
                            Map.entry("storeCode", advertisedIdentity.storeCode()),
                            Map.entry("computerName", advertisedIdentity.computerName()),
                            Map.entry("serverId", advertisedIdentity.serverId()),
                            Map.entry("certificateFingerprint", identity.fingerprint()),
                            Map.entry("pairingProof", proofs.get(0)),
                            Map.entry("previousPairingProof", proofs.get(1))))
                            .getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(response, response.length, packet.getAddress(), packet.getPort()));
                } catch (Exception ex) {
                    if (holder[0] == null || holder[0].running) {
                        System.err.println("LAN discovery listener error: " + ex.getMessage());
                    }
                }
            }
        }, "smartstock-lan-discovery");
        thread.setDaemon(true);
        LanDiscoveryService service = new LanDiscoveryService(socket, thread);
        holder[0] = service;
        thread.start();
        return service;
    }

    @Override public void close() {
        running = false;
        socket.close();
        thread.interrupt();
    }

    record DiscoveryIdentity(String environment, String storeName, String storeCode,
                             String computerName, String serverId) {
    }
}
