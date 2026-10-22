package services;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.net.ServerSocket;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PrintReferenceSyncIntegrationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path temp;
    @Test void remapsMaterialIdsAndKeepsNewerPrices() throws Exception {
        Path bin = Path.of(System.getenv().getOrDefault("SMARTSTOCK_TEST_POSTGRES_BIN",
                "C:/Program Files/PostgreSQL/17/bin"));
        String suffix = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? ".exe" : "";
        assumeTrue(Files.isRegularFile(bin.resolve("initdb" + suffix)));
        Path cluster = temp.resolve("cluster");
        Process init = new ProcessBuilder(bin.resolve("initdb" + suffix).toString(), "-D", cluster.toString(),
                "-A", "trust", "-U", "clock_test", "--no-locale", "--encoding=UTF8")
                .redirectErrorStream(true).redirectOutput(temp.resolve("init.log").toFile()).start();
        assertTrue(init.waitFor(30,TimeUnit.SECONDS));
        assertEquals(0,init.exitValue(),Files.readString(temp.resolve("init.log")));
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Process server = new ProcessBuilder(bin.resolve("postgres" + suffix).toString(), "-D", cluster.toString(),
                "-h", "127.0.0.1", "-p", String.valueOf(port), "-F")
                .redirectErrorStream(true).redirectOutput(temp.resolve("postgres.log").toFile()).start();
        try {
            String url = "jdbc:postgresql://127.0.0.1:" + port + "/postgres?connectTimeout=1";
            Connection connected = null;
            for (int i=0;i<100 && connected==null;i++) {
                try { connected=DriverManager.getConnection(url,"clock_test",""); }
                catch(SQLException e) { Thread.sleep(100); }
            }
            assertNotNull(connected);
            try (Connection c = connected) {
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement()) {
                    s.execute("CREATE TEMP TABLE custom_order_print_materials (print_material_id bigserial PRIMARY KEY,material_name text UNIQUE,description text,is_active boolean,pricing_mode text,available_colors jsonb,created_at timestamptz,updated_at timestamptz)");
                    s.execute("CREATE TEMP TABLE custom_order_print_size_presets (print_size_preset_id bigserial PRIMARY KEY,print_material_id bigint REFERENCES custom_order_print_materials,preset_name text,pricing_mode text,fixed_price numeric,is_active boolean,created_at timestamptz,updated_at timestamptz,UNIQUE(print_material_id,preset_name))");
                }
                var material=JsonParser.parseString("""
                    {"print_material_id":999,"material_name":"Vinyl","description":"Test","pricing_mode":"FIXED_PRESET","available_colors":{},"is_active":true,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}
                    """).getAsJsonObject();
                PrintReferenceSync.apply(c,"custom_order_print_materials",material);
                var preset=JsonParser.parseString("""
                    {"print_material_id":999,"material_name":"Vinyl","preset_name":"Small","pricing_mode":"FIXED_PRESET","fixed_price":100,"is_active":true,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}
                    """).getAsJsonObject();
                PrintReferenceSync.apply(c,"custom_order_print_size_presets",preset);
                preset.addProperty("fixed_price",1);
                preset.addProperty("updated_at","2026-01-01T00:00:00Z");
                PrintReferenceSync.apply(c,"custom_order_print_size_presets",preset);
                try(var s=c.createStatement();var r=s.executeQuery("SELECT print_material_id,fixed_price FROM custom_order_print_size_presets")) {
                    assertTrue(r.next());assertNotEquals(999,r.getLong(1));assertEquals(100,r.getInt(2));assertFalse(r.next());
                }
            } finally {c.rollback();}
            }
        } finally { server.destroy(); assertTrue(server.waitFor(15,TimeUnit.SECONDS)); }
    }
}

