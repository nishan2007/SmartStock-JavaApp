package app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class StudioModelMigrationTest {
    @TempDir Path root;
    @Test void copiesVerifiedModelWithoutDeletingOriginalAndRepairsCorruptTarget() throws Exception {
        Path source = root.resolve("bundled.onnx"); Files.writeString(source, "verified model");
        String hash = StudioModelMigration.sha256(source); Path models = root.resolve("profile/ai-models");
        StudioModelMigration.preserve(source, models, hash);
        Path target = models.resolve(hash + ".onnx");
        assertEquals("verified model", Files.readString(target)); assertTrue(Files.exists(source));
        Files.writeString(target, "broken"); StudioModelMigration.preserve(source, models, hash);
        assertEquals("verified model", Files.readString(target));
        Files.writeString(source, "broken old bundle");
        StudioModelMigration.preserve(source, models, hash);
        assertEquals("verified model", Files.readString(target));
        Files.delete(source); StudioModelMigration.preserve(source, models, hash);
        assertTrue(Files.exists(target));
    }

    @Test void rejectsCorruptLegacyModelAndPreservesOriginalAndApp() throws Exception {
        Path app = root.resolve("app"); Path source = app.resolve("dependency/catalog-studio/isnet-general-use.onnx");
        Files.createDirectories(source.getParent()); Files.writeString(source, "corrupt legacy model");
        Path jar = app.resolve("inventory-management-test.jar"); Files.writeString(jar, "old app");
        IOException failure = assertThrows(IOException.class, () -> StudioModelMigration.migrate(app, root.resolve("models")));
        assertTrue(failure.getMessage().contains("Original files were preserved"));
        assertEquals("corrupt legacy model", Files.readString(source)); assertEquals("old app", Files.readString(jar));
        assertFalse(Files.exists(root.resolve("models")));
    }

    @Test void missingLegacyModelsDoNotPreventAppUpdate() throws Exception {
        StudioModelMigration.migrate(root.resolve("no-model-app"), root.resolve("models"));
        assertFalse(Files.exists(root.resolve("models")));
    }

    @Test void appReplacementAndRollbackDoNotAlterPersistentModels() throws Exception {
        Path app = root.resolve("app"), payload = root.resolve("payload"), backup = root.resolve("rollback");
        Files.createDirectories(app.resolve("dependency")); Files.createDirectories(payload.resolve("dependency"));
        Files.writeString(app.resolve("inventory-management-1.0.1.jar"), "old app");
        Files.writeString(app.resolve("dependency/postgresql-old.jar"), "driver");
        Files.writeString(payload.resolve("inventory-management-1.0.2.jar"), "new app");
        Files.writeString(payload.resolve("dependency/postgresql-new.jar"), "driver");
        Path legacy = app.resolve("dependency/test-model.onnx"); Files.writeString(legacy, "test model");
        String hash = StudioModelMigration.sha256(legacy); Path models = root.resolve("profiles/production/ai-models");
        StudioModelMigration.preserve(legacy, models, hash);
        SmartStockUpdater.backupCurrentApp(app, backup);
        SmartStockUpdater.replaceApp(app, payload);
        assertEquals("test model", Files.readString(models.resolve(hash + ".onnx")));
        assertFalse(Files.exists(app.resolve("dependency/test-model.onnx")));
        SmartStockUpdater.restoreBackup(app, backup);
        assertEquals("test model", Files.readString(models.resolve(hash + ".onnx")));
        assertEquals("old app", Files.readString(app.resolve("inventory-management-1.0.1.jar")));
    }
}
