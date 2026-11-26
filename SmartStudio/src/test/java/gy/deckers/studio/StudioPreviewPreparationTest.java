package gy.deckers.studio;

import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.Publication;
import publishing.PublicationFiles;
import static org.junit.jupiter.api.Assertions.*;

class StudioPreviewPreparationTest {
    @TempDir Path temporary;

    @Test void preservesMigratedIdentityFromNativeStreamInPreparedDocument() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path file = temporary.resolve("migrated.sstbadge"), recovery = Files.createDirectory(temporary.resolve("recovery"));
        Files.writeString(file, Publication.JSON.toJson(new BadgeTemplate()));
        var identity = new publishing.StudioDriveIdentity("11111111-1111-4111-8111-111111111111", "2", "BADGE");
        var view = Files.getFileAttributeView(file, java.nio.file.attribute.UserDefinedFileAttributeView.class);
        view.write("SmartDriveIdentity", StandardCharsets.UTF_8.encode(Publication.JSON.toJson(identity)));
        StudioPreviewPreparation.prepare(file, recovery, BadgeTemplateLibrary.fingerprint(file));
        var document = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("2", document.getAsJsonObject("smartDrive").get("storageStoreId").getAsString());
        assertEquals(identity.id, BadgeTemplate.load(file).smartDrive.id);
    }

    @Test void preparesBadgeWithVerifiedRecoveryAndPreservesUnknownFields() throws Exception {
        Path file = temporary.resolve("legacy.sstbadge"), recovery = Files.createDirectory(temporary.resolve("recovery"));
        BadgeTemplate template = new BadgeTemplate();template.name = "Original badge";
        var json = Publication.JSON.toJsonTree(template).getAsJsonObject();json.addProperty("futureSetting", "keep me");
        Files.writeString(file, json.toString());String before = BadgeTemplateLibrary.fingerprint(file);
        var result = StudioPreviewPreparation.prepare(file, recovery, before);
        assertEquals("prepared", result.status());assertNotEquals(before, result.afterSha256());
        assertEquals(before, BadgeTemplateLibrary.fingerprint(Path.of(result.backup())));
        var prepared = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        assertEquals("keep me", prepared.get("futureSetting").getAsString());
        assertTrue(prepared.get("previewPng").getAsString().startsWith("iVBOR"));
        assertEquals(template.name, BadgeTemplate.load(file).name);
        assertEquals("already-prepared", StudioPreviewPreparation.prepare(file, recovery, before).status());
        try (var files = Files.list(recovery)) { assertEquals(1, files.count()); }
    }

    @Test void preparesPublicationWithoutDroppingUnknownMetadataOrChangingAssetBytes() throws Exception {
        Publication publication = new Publication();
        var raw = Publication.JSON.toJsonTree(publication).getAsJsonObject();raw.addProperty("futureSetting", 42);
        Path file = temporary.resolve("legacy.sspub"), recovery = Files.createDirectory(temporary.resolve("recovery"));
        byte[] asset = "unused retained asset".getBytes(StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("publication.json"));zip.write(raw.toString().getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/original"));zip.write(asset);zip.closeEntry();
        }
        String before = BadgeTemplateLibrary.fingerprint(file);
        var result = StudioPreviewPreparation.prepare(file, recovery, before);
        assertEquals(before, BadgeTemplateLibrary.fingerprint(Path.of(result.backup())));
        assertEquals(publication.title, PublicationFiles.read(Files.readAllBytes(file)).title);
        try (ZipFile zip = new ZipFile(file.toFile())) {
            var prepared = JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("publication.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(42, prepared.get("futureSetting").getAsInt());assertTrue(prepared.has("previewPng"));
            assertArrayEquals(asset, zip.getInputStream(zip.getEntry("assets/original")).readAllBytes());
        }
    }

    @Test void rejectsChangedOrInvalidDesignWithoutReplacingIt() throws Exception {
        Path file = temporary.resolve("legacy.sstbadge"), recovery = Files.createDirectory(temporary.resolve("recovery"));
        Files.writeString(file, Publication.JSON.toJson(new BadgeTemplate()));
        String before = BadgeTemplateLibrary.fingerprint(file);
        assertThrows(IOException.class, () -> StudioPreviewPreparation.prepare(file, recovery, "0".repeat(64)));
        assertEquals(before, BadgeTemplateLibrary.fingerprint(file));
        try (var files = Files.list(recovery)) { assertEquals(0, files.count()); }
        Files.writeString(file, "{}");String invalid = BadgeTemplateLibrary.fingerprint(file);
        assertThrows(Exception.class, () -> StudioPreviewPreparation.prepare(file, recovery, invalid));
        assertEquals(invalid, BadgeTemplateLibrary.fingerprint(file));
        try (var files = Files.list(recovery)) { assertEquals(1, files.count()); }
    }
}
