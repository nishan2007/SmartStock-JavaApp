# LAN HTTPS DNS stall

On 15 October 2026 the Windows store server accepted TCP connections on 8443,
but HTTPS health checks timed out from registers and localhost. The Java thread
dump showed the LAN worker pool blocked in `SSLStreams` constructing TLS streams:
`InetSocketAddress.getHostName()` called a native Windows name lookup, and the
remaining workers waited on the same name-service monitor. UDP discovery remained
available. Restarting the background task restored HTTP 200 health responses.

The LAN listener now uses Jetty's Java HTTP Server SPI implementation, explicitly,
instead of the JDK listener's reverse-DNS-before-TLS path. Existing handlers,
certificate identity, client addresses and authorization remain in place. Jetty
owns its asynchronous TLS processing and thread pool. Main and the standalone
background service select the JDK provider for unrelated embedded web servers,
so adding the SPI dependency does not select it globally through ServiceLoader.

The regression test uses a temporary certificate and verifies concurrent HTTPS,
certificate rejection, authenticated POST bodies, remote addresses, port release,
and progress with more idle TLS peers than the old worker pool could handle.
It does not change operating-system DNS or reproduce the native Windows lookup
hang. A live register acceptance check is still required after installing a
prepared build. No new certificate, register pairing, or database migration is
required. This change does not prevent unrelated outbound DNS failures.

Upstream reference:
https://github.com/openjdk/jdk17u/blob/master/src/jdk.httpserver/share/classes/sun/net/httpserver/SSLStreams.java
