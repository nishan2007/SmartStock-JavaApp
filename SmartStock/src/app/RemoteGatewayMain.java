package app;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.LanApiServer;

import java.sql.Connection;
import java.util.concurrent.CountDownLatch;

/** Dedicated, opt-in cloud gateway process for credential-free Remote Admin desktops. */
public final class RemoteGatewayMain {
    private RemoteGatewayMain() { }

    public static void main(String[] args) throws Exception {
        if (!"REMOTE_ADMIN".equals(System.getenv("SMARTSTOCK_GATEWAY_MODE"))) {
            throw new IllegalStateException("Set SMARTSTOCK_GATEWAY_MODE=REMOTE_ADMIN to start the remote gateway.");
        }
        System.setProperty("smartstock.remote.gateway", "true");
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER || !config.hasPrimaryConnection()) {
            throw new IllegalStateException("The gateway host requires SERVER mode with a server-side cloud PostgreSQL connection.");
        }
        try (Connection connection = DB.getConnection()) {
            // Fail closed before opening the listener if credentials or schema are invalid.
            if (!services.ServerSetupGuardService.authorizeBackgroundService(connection)) {
                throw new IllegalStateException("This machine is not the active store primary.");
            }
        }
        LanApiServer api = LanApiServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(api::close, "smartstock-remote-gateway-shutdown"));
        System.out.println("SmartStock Remote Admin gateway is ready.");
        new CountDownLatch(1).await();
    }
}
