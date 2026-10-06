package services;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in SQL-only restore rehearsal. Never connects to production or uploads assets. */
class StorefrontBackupRestoreRehearsalTest {
    @Test void restoreCompanyBackupIntoDisposableDatabase() throws Exception {
        String backup = System.getProperty("storefront.backup.rehearsal", "");
        assumeTrue(!backup.isBlank());
        Path packagePath = Path.of(backup);
        assertTrue(Files.isRegularFile(packagePath));
        String schema = System.getProperty("storefront.backup.schema", "");
        assertFalse(schema.isBlank(), "A schema-only copy of the live database is required");
        Path schemaPath = Path.of(schema);
        assertTrue(Files.isRegularFile(schemaPath));
        String root = "jdbc:postgresql://127.0.0.1:55439/postgres";
        String name = "smartstock_restore_" + UUID.randomUUID().toString().replace("-", "");
        Path sql = Files.createTempFile("smartstock-isolated-restore-", ".sql");
        try {
            boolean found = false;
            try (InputStream input = Files.newInputStream(packagePath);
                    ZipInputStream zip = new ZipInputStream(input)) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                    if ("data.sql".equals(entry.getName())) {
                        Files.copy(zip, sql, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        found = true;
                        break;
                    }
                }
            }
            assertTrue(found, "Backup has no data.sql");
            try (Connection admin = DriverManager.getConnection(root, "storefront_test", "")) {
                admin.createStatement().execute("CREATE DATABASE " + name);
                Process process = new ProcessBuilder(
                        "C:\\Program Files\\PostgreSQL\\17\\bin\\psql.exe",
                        "--quiet", "--no-psqlrc", "--set", "ON_ERROR_STOP=1",
                        "--username", "storefront_test", "--host", "127.0.0.1",
                        "--port", "55439", "--dbname", name,
                        "--file", schemaPath.toAbsolutePath().toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                assertTrue(process.waitFor(120, TimeUnit.SECONDS), "Schema import timed out");
                assertEquals(0, process.exitValue(), "Schema import failed");
                try (Connection isolated = DriverManager.getConnection(
                        root.replace("/postgres", "/" + name), "storefront_test", "")) {
                    assertTrue(SqlScriptRunner.runScript(isolated, sql) > 0);
                    try (Statement statement = isolated.createStatement();
                            ResultSet result = statement.executeQuery(
                                    "SELECT COUNT(*) FROM public.products WHERE product_id=168")) {
                        assertTrue(result.next());
                        assertEquals(1, result.getInt(1));
                    }
                    try (Statement statement = isolated.createStatement();
                            ResultSet result = statement.executeQuery(
                                    "SELECT enabled FROM storefront.settings WHERE location_id=1")) {
                        assertTrue(result.next(), "Skeldon storefront settings did not restore");
                        assertTrue(result.getBoolean(1), "Unexpected backup ordering setting");
                    }
                    try (Statement statement = isolated.createStatement();
                            ResultSet result = statement.executeQuery(
                                    "SELECT MAX(version) FROM storefront.schema_version")) {
                        assertTrue(result.next());
                        assertEquals(2, result.getInt(1));
                    }
                    StorefrontSchema.ensure(isolated);
                    try (Statement statement = isolated.createStatement();
                            ResultSet result = statement.executeQuery(
                                    "SELECT MAX(version) FROM storefront.schema_version")) {
                        assertTrue(result.next());
                        assertEquals(15, result.getInt(1));
                    }
                    var localSnapshot = StorefrontService.snapshot(isolated, 1);
                    var syncSnapshot = StorefrontService.publicSyncSnapshot(localSnapshot);
                    for (String privateField : new String[] {"customers", "links", "orders", "receipts"})
                        assertFalse(syncSnapshot.has(privateField), "Browse-only sync exposed " + privateField);
                    assertFalse(syncSnapshot.getAsJsonObject("settings").get("enabled").getAsBoolean());
                    var publicCatalog = StorefrontService.catalog(localSnapshot);
                    assertEquals(1, publicCatalog.getAsJsonArray("products").size());
                    var product = publicCatalog.getAsJsonArray("products").get(0).getAsJsonObject();
                    assertEquals(168, product.get("id").getAsInt());
                    assertEquals("0.5 Leads", product.get("name").getAsString());
                    assertEquals(100, product.get("price").getAsInt());
                    assertEquals(0, publicCatalog.getAsJsonArray("projects").size());
                    assertFalse(publicCatalog.has("customers"));
                    assertFalse(publicCatalog.has("receipts"));
                    StorefrontWebServer.markBrowseOnly(publicCatalog);
                    assertTrue(publicCatalog.get("browseOnly").getAsBoolean());
                    assertFalse(publicCatalog.getAsJsonObject("settings").get("enabled").getAsBoolean());
                    assertFalse(product.get("canOrder").getAsBoolean());
                    assertEquals("Find your store", publicCatalog.getAsJsonObject("campaign")
                            .get("primary").getAsString());
                    assertFalse(publicCatalog.getAsJsonObject("campaign").get("steps")
                            .getAsString().contains("Upload"));
                    String export = System.getProperty("storefront.backup.catalogExport", "");
                    if (!export.isBlank()) {
                        Path output = Path.of(export).toAbsolutePath().normalize();
                        Files.createDirectories(output.getParent());
                        Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting()
                                .create().toJson(publicCatalog));
                    }
                }
            } finally {
                try (Connection admin = DriverManager.getConnection(root, "storefront_test", "")) {
                    admin.createStatement().execute("DROP DATABASE IF EXISTS " + name + " WITH (FORCE)");
                }
            }
        } finally {
            Files.deleteIfExists(sql);
        }
    }
}
