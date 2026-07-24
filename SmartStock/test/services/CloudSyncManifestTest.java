package services;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudSyncManifestTest {
    @Test
    void parsesSchemaAndRecoveryCounts() throws Exception {
        CloudSyncManifest manifest = CloudSyncManifest.parse("""
                {"tables":[
                  {"name":"users","row_count":3,"columns":["user_id","email"]},
                  {"name":"sales","row_count":20,"columns":["sale_id","location_id"]}
                ]}
                """);

        assertTrue(manifest.hasTable("users"));
        assertEquals(3, manifest.rowCount("users"));
        assertTrue(manifest.tables().get("sales").columns().contains("location_id"));
    }

    @Test
    void rejectsUnsafeManifestNames() {
        assertThrows(IOException.class, () -> CloudSyncManifest.parse("""
                {"tables":[{"name":"users;drop table users","row_count":1,"columns":[]}]}
                """));
    }
}
