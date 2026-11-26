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
                    s.execute("CREATE TABLE custom_order_print_materials (print_material_id bigserial PRIMARY KEY,material_name text UNIQUE,description text,is_active boolean,pricing_mode text,available_colors jsonb,created_at timestamptz,updated_at timestamptz)");
                    s.execute("CREATE TABLE custom_order_print_size_presets (print_size_preset_id bigserial PRIMARY KEY,print_material_id bigint REFERENCES custom_order_print_materials,preset_name text,pricing_mode text,fixed_price numeric,is_active boolean,created_at timestamptz,updated_at timestamptz,UNIQUE(print_material_id,preset_name))");
                }
                var material=JsonParser.parseString("""
                    {"print_material_id":999,"material_name":"Vinyl","description":"Test","pricing_mode":"FIXED_PRESET","available_colors":{},"is_active":true,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}
                    """).getAsJsonObject();
                PrintReferenceSync.apply(c,"custom_order_print_materials",material);
                try(var statement=c.createStatement()){
                    statement.execute("INSERT INTO custom_order_print_size_presets(print_material_id,preset_name) SELECT print_material_id,'Per line 2inx10in' FROM custom_order_print_materials");
                    statement.execute("INSERT INTO custom_order_print_size_presets(print_material_id,preset_name) SELECT print_material_id,'Chest Logo' FROM custom_order_print_materials");
                }
                String migration=SqlScriptRunner.readResource("database/migrations/v1_after/20261026120000_print_preset_guide_sizes.sql");
                SqlScriptRunner.runSql(c,migration);SqlScriptRunner.runSql(c,migration);
                try(var statement=c.createStatement();var rows=statement.executeQuery("SELECT preset_name,guide_width_in,guide_height_in FROM custom_order_print_size_presets ORDER BY preset_name")){
                    assertTrue(rows.next());assertEquals(4,rows.getBigDecimal(2).intValue());assertEquals(4,rows.getBigDecimal(3).intValue());
                    assertTrue(rows.next());assertEquals(10,rows.getBigDecimal(2).intValue());assertEquals(2,rows.getBigDecimal(3).intValue());
                }
                try(var statement=c.createStatement()){statement.execute("DELETE FROM custom_order_print_size_presets");}
                var preset=JsonParser.parseString("""
                    {"print_material_id":999,"material_name":"Vinyl","preset_name":"Small","pricing_mode":"FIXED_PRESET","fixed_price":100,"is_active":true,"created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}
                    """).getAsJsonObject();
                preset.addProperty("guide_width_in",10);preset.addProperty("guide_height_in",2);
                PrintReferenceSync.apply(c,"custom_order_print_size_presets",preset);
                preset.addProperty("fixed_price",1);
                preset.addProperty("updated_at","2026-01-01T00:00:00Z");
                PrintReferenceSync.apply(c,"custom_order_print_size_presets",preset);
                try(var s=c.createStatement();var r=s.executeQuery("SELECT print_material_id,fixed_price,guide_width_in,guide_height_in FROM custom_order_print_size_presets")) {
                    assertTrue(r.next());assertNotEquals(999,r.getLong(1));assertEquals(100,r.getInt(2));assertEquals(10,r.getInt(3));assertEquals(2,r.getInt(4));assertFalse(r.next());
                }
                long materialId;try(var query=c.createStatement();var rows=query.executeQuery("SELECT print_material_id FROM custom_order_print_materials WHERE material_name='Vinyl'")){assertTrue(rows.next());materialId=rows.getLong(1);}
                var body=new com.google.gson.JsonObject();
                body.add("preset",LanJson.create().toJsonTree(new LanCustomOrderCatalogAdminService.PresetSave(null,materialId,"Shared guide","PER_LINE",java.math.BigDecimal.TEN,true,new java.math.BigDecimal("10"),new java.math.BigDecimal("2"))));
                long savedId=LanCustomOrderCatalogAdminService.mutate(c,"SAVE_PRESET",body,1);
                body.add("preset",LanJson.create().toJsonTree(new LanCustomOrderCatalogAdminService.PresetSave(savedId,materialId,"Shared guide","PER_LINE",java.math.BigDecimal.ONE,true,new java.math.BigDecimal("8"),new java.math.BigDecimal("3"))));
                LanCustomOrderCatalogAdminService.mutate(c,"SAVE_PRESET",body,1);
                body.add("preset",LanJson.create().toJsonTree(new LanCustomOrderCatalogAdminService.PresetSave(savedId,materialId,"Shared guide","PER_LINE",java.math.BigDecimal.TEN,true)));
                LanCustomOrderCatalogAdminService.mutate(c,"SAVE_PRESET",body,1);
                try(var query=c.prepareStatement("SELECT guide_width_in,guide_height_in FROM custom_order_print_size_presets WHERE print_size_preset_id=?")){query.setLong(1,savedId);try(var rows=query.executeQuery()){assertTrue(rows.next());assertEquals(8,rows.getInt(1));assertEquals(3,rows.getInt(2));}}
            } finally {c.rollback();}
            }
        } finally { server.destroy(); assertTrue(server.waitFor(15,TimeUnit.SECONDS)); }
    }
}

