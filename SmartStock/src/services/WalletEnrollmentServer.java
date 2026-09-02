package services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only listener intended as the sole upstream for the public Wallet tunnel. */
public final class WalletEnrollmentServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 8447;
    private final HttpServer server;
    private final ExecutorService executor;

    private WalletEnrollmentServer(HttpServer server, ExecutorService executor) {
        this.server = server;
        this.executor = executor;
    }

    public static WalletEnrollmentServer startIfConfigured() throws IOException {
        if (!AppleWalletConfig.load().barcodeReady()) return null;
        int port = Integer.getInteger("smartstock.wallet.enrollment.port", DEFAULT_PORT);
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 20);
        ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "smartstock-wallet-enrollment");
            thread.setDaemon(true);
            return thread;
        });
        http.setExecutor(executor);
        http.createContext("/wallet/enroll/", new WalletEnrollmentHandler());
        http.createContext("/", WalletEnrollmentServer::notFound);
        http.start();
        System.out.println("SmartStock Wallet enrollment gateway listening on loopback port " + port + ".");
        return new WalletEnrollmentServer(http, executor);
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }

    @Override public void close() {
        server.stop(1);
        executor.shutdownNow();
    }
}
