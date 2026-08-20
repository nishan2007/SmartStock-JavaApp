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
                ],"generation_id":"44c38a27-1f91-4c47-992c-bf01ce17ca4d",
                   "completed_at":"2026-08-09T12:00:00Z"}
                """);

        assertTrue(manifest.hasTable("users"));
        assertEquals(3, manifest.rowCount("users"));
        assertTrue(manifest.tables().get("sales").columns().contains("location_id"));
        assertTrue(manifest.hasVerifiedSnapshot());
    }

    @Test
    void rejectsUnsafeManifestNames() {
        assertThrows(IOException.class, () -> CloudSyncManifest.parse("""
                {"tables":[{"name":"users;drop table users","row_count":1,"columns":[]}]}
                """));
    }

    @Test
    void reportsMissingSnapshotManifestWithoutLeakingJsonParserErrors() {
        IOException failure = assertThrows(IOException.class,
                () -> CloudSyncManifest.parse("null"));

        assertTrue(failure.getMessage().contains("completed store snapshot"));
    }
}
