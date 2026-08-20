package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudSnapshotRetentionArchitectureTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    void cloudSchemaBoundsCompletedSnapshotRetention() throws Exception {
        String schema = source("database/v1/cloud/001_schema.sql");
        assertTrue(schema.contains("smartstock_prune_store_mirror_generations"));
        assertTrue(schema.contains("ranked.completed_rank > p_keep_complete"));
        assertTrue(schema.contains("status.current_generation_id IS NULL"));
        assertTrue(schema.contains("status = 'COMPLETE'"));
        assertTrue(schema.contains("ON DELETE SET NULL"));
        assertTrue(schema.contains("LIMIT p_max_delete"));
        assertTrue(schema.contains(
                "GRANT ALL ON FUNCTION public.smartstock_prune_store_mirror_generations"));
    }

    @Test
    void mirrorPrunesOnlyAfterSuccessfulCompletionPath() throws Exception {
        String service = source("src/services/CloudRowMirrorService.java");
        int verify = service.indexOf("verifyMirror(locationId, finalization.generationId()");
        int credentials = service.indexOf("synchronizeProtectedUserCredentials(\n                        local, locationId, finalization.generationId())");
        int persist = service.indexOf("persistCompletedState(local, locationId");
        int prune = service.indexOf("pruneCompletedGenerations(locationId)");
        assertTrue(verify >= 0 && credentials > verify && persist > credentials && prune > persist);
        assertTrue(service.contains("body.addProperty(\"p_keep_complete\", 2)"));
        assertTrue(service.contains("body.addProperty(\"p_max_delete\", 25)"));
    }
}
