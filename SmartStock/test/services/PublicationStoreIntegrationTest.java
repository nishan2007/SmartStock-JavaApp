package services;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.net.ServerSocket;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;

/** Own disposable localhost cluster. Never connects to an installed store database. */
class PublicationStoreIntegrationTest {
  @TempDir Path temp;

  @Test
  void savesConflictsTemplatesAndLocationIsolationOnRealPostgres() throws Exception {
    Path bin =
        Path.of(
            System.getenv()
                .getOrDefault(
                    "SMARTSTOCK_TEST_POSTGRES_BIN", "C:/Program Files/PostgreSQL/17/bin"));
    String suffix =
        System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : "";
    Assumptions.assumeTrue(
        Files.isRegularFile(bin.resolve("initdb" + suffix)),
        "Optional disposable PostgreSQL runtime is unavailable.");
    Path data = temp.resolve("cluster"), log = temp.resolve("init.log");
    Process init =
        new ProcessBuilder(
                bin.resolve("initdb" + suffix).toString(),
                "-D",
                data.toString(),
                "-A",
                "trust",
                "-U",
                "publication_test",
                "--no-locale",
                "--encoding=UTF8")
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      assertTrue(init.waitFor(30, TimeUnit.SECONDS), "initdb timeout");
      assertEquals(0, init.exitValue(), Files.readString(log));
    } finally {
      if (init.isAlive()) init.destroyForcibly();
    }
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    Process server =
        new ProcessBuilder(
                bin.resolve("postgres" + suffix).toString(),
                "-D",
                data.toString(),
                "-h",
                "127.0.0.1",
                "-p",
                String.valueOf(port),
                "-F")
            .redirectErrorStream(true)
            .redirectOutput(temp.resolve("postgres.log").toFile())
            .start();
    try {
      Connection connection = null;
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      while (connection == null && System.nanoTime() < deadline) {
        try {
          connection =
              DriverManager.getConnection(
                  "jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=1",
                  "publication_test",
                  "");
        } catch (SQLException ex) {
          Thread.sleep(100);
        }
      }
      assertNotNull(connection, "Disposable PostgreSQL failed to start.");
      try (Connection c = connection) {
        try (var st = c.createStatement()) {
          st.execute(
              "CREATE TABLE locations(location_id integer PRIMARY KEY); CREATE TABLE users(user_id"
                  + " integer PRIMARY KEY); CREATE TABLE custom_orders(custom_order_id bigint"
                  + " PRIMARY KEY,location_id integer); CREATE TABLE"
                  + " custom_order_lines(custom_order_line_id bigint PRIMARY KEY,custom_order_id"
                  + " bigint); INSERT INTO locations VALUES(1),(2); INSERT INTO users VALUES(1);"
                  + " INSERT INTO custom_orders VALUES(10,1),(20,2); INSERT INTO custom_order_lines"
                  + " VALUES(100,10),(200,20)");
        }
        SqlScriptRunner.runSql(
            c,
            SqlScriptRunner.readResource(
                "database/migrations/v1_after/20261012120000_publication_workspace.sql"));
        SqlScriptRunner.runSql(
            c,
            SqlScriptRunner.readResource(
                "database/migrations/v1_after/20261012120000_publication_workspace.sql"));
        Publication p = Publication.preset("Card");
        p.orderId = 10L;
        p.lineId = 100L;
        UUID id = UUID.randomUUID();
        assertEquals(1, save(c, p, id, 0, true).get("revision"));
        JsonObject read = new JsonObject();
        read.addProperty("projectId", id.toString());
        read.addProperty("offset", 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> loaded =
            (Map<String, Object>) PublicationStoreService.execute(c, 1, 1, "READ", read, false);
        assertEquals(
            "Card",
            PublicationFiles.read(Base64.getDecoder().decode(loaded.get("bytes").toString()))
                .title);
        assertThrows(
            IllegalArgumentException.class,
            () -> PublicationStoreService.execute(c, 2, 1, "READ", read, false));
        p.title = "Updated";
        assertEquals(2, save(c, p, id, 1, true).get("revision"));
        assertThrows(PublicationStoreService.Conflict.class, () -> save(c, p, id, 1, true));
        p.template = true;
        assertThrows(SecurityException.class, () -> save(c, p, id, 2, false));
        assertEquals(3, save(c, p, id, 2, true).get("revision"));
        p.template = false;
        assertThrows(SecurityException.class, () -> save(c, p, id, 3, false));
        Publication wrong = Publication.preset("Card");
        wrong.orderId = 20L;
        wrong.lineId = 200L;
        assertThrows(
            IllegalArgumentException.class, () -> save(c, wrong, UUID.randomUUID(), 0, true));
        JsonObject invalid = new JsonObject();
        invalid.addProperty("projectId", "not-a-uuid");
        invalid.addProperty("offset", 0);
        assertThrows(
            IllegalArgumentException.class,
            () -> PublicationStoreService.execute(c, 1, 1, "READ", invalid, false));
      }
    } finally {
      server.destroy();
      if (!server.waitFor(10, TimeUnit.SECONDS)) {
        server.destroyForcibly();
        server.waitFor(10, TimeUnit.SECONDS);
      }
    }
  }

  private Map<?, ?> save(Connection c, Publication p, UUID id, int revision, boolean templates)
      throws Exception {
    byte[] bytes = PublicationFiles.archive(p);
    c.setAutoCommit(false);
    try {
      JsonObject b = new JsonObject();
      b.addProperty("projectId", id.toString());
      b.addProperty("revision", revision);
      Map<?, ?> begin = (Map<?, ?>) PublicationStoreService.execute(c, 1, 1, "BEGIN", b, templates);
      String upload = begin.get("uploadId").toString();
      b = new JsonObject();
      b.addProperty("uploadId", upload);
      b.addProperty("offset", 0);
      b.addProperty("bytes", Base64.getEncoder().encodeToString(bytes));
      PublicationStoreService.execute(c, 1, 1, "CHUNK", b, templates);
      b = new JsonObject();
      b.addProperty("uploadId", upload);
      Map<?, ?> result =
          (Map<?, ?>) PublicationStoreService.execute(c, 1, 1, "FINISH", b, templates);
      c.commit();
      return result;
    } catch (Exception ex) {
      c.rollback();
      throw ex;
    } finally {
      c.setAutoCommit(true);
    }
  }
}
