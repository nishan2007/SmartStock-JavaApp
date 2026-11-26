package publishing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudioDriveIdentityTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path root;
    @Test void readsTheSameIdentityStreamWrittenByWindowsPublicationTools()throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name","").startsWith("Windows"));
        var file=root.resolve("Windows-published.sspub");java.nio.file.Files.writeString(file,"original bytes");
        var identity=new StudioDriveIdentity("11111111-1111-4111-8111-111111111111","2","BUSINESS_CARDS");
        var builder=new ProcessBuilder("powershell.exe","-NoProfile","-Command",
                "$ErrorActionPreference='Stop'; Add-Type -Path $env:SMARTDRIVE_TEST_HELPER; $bytes=[Text.Encoding]::UTF8.GetBytes($env:SMARTDRIVE_TEST_IDENTITY); [SmartDriveImportIdentityStreamV1]::Write($env:SMARTDRIVE_TEST_FILE,$bytes); if([Convert]::ToBase64String([SmartDriveImportIdentityStreamV1]::Read($env:SMARTDRIVE_TEST_FILE)) -cne [Convert]::ToBase64String($bytes)){throw 'Metadata readback mismatch'}");
        builder.environment().put("SMARTDRIVE_TEST_HELPER",java.nio.file.Path.of(System.getProperty("basedir",System.getProperty("user.dir"))).resolve("../SmartDrive/installer/windows/IdentityStream.cs").normalize().toString());
        builder.environment().put("SMARTDRIVE_TEST_FILE",file.toString());
        builder.environment().put("SMARTDRIVE_TEST_IDENTITY",Publication.JSON.toJson(identity));builder.redirectErrorStream(true);
        Process process=builder.start();
        if(!process.waitFor(20,java.util.concurrent.TimeUnit.SECONDS)){process.destroyForcibly();fail("Windows metadata writer timed out.");}
        assertEquals(0,process.exitValue(),new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(identity.id,StudioDriveIdentity.fromFile(file).id);
        assertEquals("original bytes",java.nio.file.Files.readString(file));
    }
    @Test void readsFileIdentityMetadataWithoutChangingDocumentBytes()throws Exception {
        java.nio.file.Path file=root.resolve("Original.sspub");java.nio.file.Files.writeString(file,"original bytes");
        var view=java.nio.file.Files.getFileAttributeView(file,java.nio.file.attribute.UserDefinedFileAttributeView.class);
        org.junit.jupiter.api.Assumptions.assumeTrue(view!=null);
        var identity=new StudioDriveIdentity("11111111-1111-4111-8111-111111111111","2","BUSINESS_CARDS");
        byte[] bytes=Publication.JSON.toJson(identity).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        view.write("SmartDriveIdentity",java.nio.ByteBuffer.wrap(bytes));
        var renamed=root.resolve("Renamed.sspub");java.nio.file.Files.move(file,renamed);
        assertEquals(identity.id,StudioDriveIdentity.fromFile(renamed).id);
        assertEquals("original bytes",java.nio.file.Files.readString(renamed));
    }
    @Test void newNativeShareCopiesHaveDifferentIdentities() {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name","").startsWith("Windows"));
        var first=StudioDriveIdentity.forNewFile(java.nio.file.Path.of("\\\\DECKERSDRIVE\\DeckersDrive\\2\\Studio Jobs\\BADGE\\New.sstbadge"),"BADGE");
        var second=StudioDriveIdentity.forNewFile(java.nio.file.Path.of("Z:\\2\\Studio Jobs\\BADGE\\Copy.sstbadge"),"BADGE");
        assertEquals("2",first.storageStoreId);assertEquals("2",second.storageStoreId);assertNotEquals(first.id,second.id);
        assertNull(StudioDriveIdentity.forNewFile(java.nio.file.Path.of("C:\\Users\\Employee\\Export.sstbadge"),"BADGE"));
    }
    @Test void retainsIdentityThroughPublicationCopiesAndRejectsInvalidReferences() {
        Publication document=new Publication();
        document.smartDrive=new StudioDriveIdentity("11111111-1111-4111-8111-111111111111","2","BUSINESS_CARDS");
        Publication copy=document.copy();
        assertEquals(document.smartDrive.id,copy.smartDrive.id);
        assertEquals("2",copy.smartDrive.storageStoreId);
        assertNotSame(document.smartDrive,copy.smartDrive);
        assertThrows(IllegalArgumentException.class,()->new StudioDriveIdentity("../template","2","BADGE"));
        assertThrows(IllegalArgumentException.class,()->new StudioDriveIdentity(document.smartDrive.id,"0","BADGE"));
        assertThrows(IllegalArgumentException.class,()->new StudioDriveIdentity(document.smartDrive.id,"2","OTHER"));
    }
}
