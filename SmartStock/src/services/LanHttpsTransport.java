package services;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.eclipse.jetty.http.spi.JettyHttpServerProvider;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;

/** TLS listener that never resolves a register's hostname during its handshake. */
public final class LanHttpsTransport {
    private LanHttpsTransport() { }

    /** Keep the SPI dependency from changing unrelated embedded web servers. */
    public static void preserveDefaultProvider() {
        String property = "com.sun.net.httpserver.HttpServerProvider";
        if (System.getProperty(property) == null) {
            System.setProperty(property, "sun.net.httpserver.DefaultHttpServerProvider");
        }
    }

    static HttpsServer create(InetSocketAddress address, SSLContext tls) throws IOException {
        preserveDefaultProvider();
        HttpsServer server = new JettyHttpServerProvider().createHttpsServer(address, 50);
        server.setHttpsConfigurator(new HttpsConfigurator(tls));
        // Use Jetty's owned pool: selectors and TLS need threads independently of handlers.
        return server;
    }
}
