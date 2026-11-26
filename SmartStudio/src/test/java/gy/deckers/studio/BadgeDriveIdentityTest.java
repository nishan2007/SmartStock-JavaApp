package gy.deckers.studio;

import publishing.StudioDriveIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BadgeDriveIdentityTest {
    @TempDir Path root;
    @Test void readsMigratedFileMetadataAndEmbedsItOnTheFirstSave()throws Exception {
        BadgeTemplate badge=BadgeTemplate.builtin();Path original=root.resolve("Migrated.sstbadge");badge.save(original);
        byte[] originalBytes=Files.readAllBytes(original);
        var view=Files.getFileAttributeView(original,java.nio.file.attribute.UserDefinedFileAttributeView.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(view!=null);
        var identity=new StudioDriveIdentity("11111111-1111-4111-8111-111111111111","2","BADGE");
        view.write("SmartDriveIdentity",java.nio.ByteBuffer.wrap(new com.google.gson.Gson().toJson(identity).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertArrayEquals(originalBytes,Files.readAllBytes(original));
        Path renamed=root.resolve("First rename.sstbadge");Files.move(original,renamed);
        BadgeTemplate loaded=BadgeTemplate.load(renamed);assertEquals(identity.id,loaded.smartDrive.id);
        StudioDocumentSave.write(renamed,BadgeTemplateLibrary.fingerprint(renamed),loaded::save);
        assertTrue(Files.readString(renamed).contains(identity.id));
        assertEquals(identity.id,BadgeTemplate.load(renamed).smartDrive.id);
    }
    @Test void routesNativeFileQueueRequestsByIdentityWithoutLegacyCacheMetadata()throws Exception {
        BadgeTemplate badge=BadgeTemplate.builtin();
        badge.smartDrive=new StudioDriveIdentity("11111111-1111-4111-8111-111111111111","2","BADGE");
        Path file=root.resolve("Native.sstbadge");badge.save(file);
        var library=new SharedBadgeLibrary(root.resolve("cache"),request->{
            assertEquals(badge.smartDrive.id,request.get("id").getAsString());
            assertEquals(2,request.get("storageStoreId").getAsInt());
            assertEquals("QUEUE_LIST",request.get("action").getAsString());
            return new com.google.gson.JsonObject();
        });
        library.record(file,"QUEUE_LIST",new com.google.gson.JsonObject());
        badge.smartDrive=null;badge.save(file);
        assertThrows(java.io.IOException.class,()->library.record(file,"QUEUE_LIST",new com.google.gson.JsonObject()));
    }
    @Test void preservesIdentityAcrossRenameSaveAndSnapshot()throws Exception {
        BadgeTemplate badge=BadgeTemplate.builtin();
        badge.smartDrive=new StudioDriveIdentity("11111111-1111-4111-8111-111111111111","1","BADGE");
        Path original=root.resolve("Original.sstbadge");badge.save(original);
        Path renamed=root.resolve("Renamed.sstbadge");Files.move(original,renamed);
        BadgeTemplate loaded=BadgeTemplate.load(renamed);
        assertEquals(badge.smartDrive.id,loaded.smartDrive.id);
        loaded.snapshot().save(renamed);
        assertEquals(badge.smartDrive.id,BadgeTemplate.load(renamed).smartDrive.id);
        loaded.smartDrive.kind="LABELS";
        assertThrows(IllegalArgumentException.class,loaded::validate);
    }
}
