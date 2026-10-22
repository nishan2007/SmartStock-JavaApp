package publishing;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.io.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationLargeFilesTest {
    @TempDir Path temp;
    private Path source(long size)throws IOException{Path file=temp.resolve("source.bin");try(RandomAccessFile output=new RandomAccessFile(file.toFile(),"rw")){output.setLength(size);output.seek(0);output.write(new byte[]{17,23,42});output.seek(size-1);output.write(99);}return file;}
    @Test void diskBackedArchiveRoundTripsBeyondLegacyLimitWithoutBase64()throws Exception {
        Publication p=new Publication();p.template=true;p.assetFiles.put("picture",source(64L*1024*1024));
        Path file=temp.resolve("large.sspub");PublicationLargeFiles.write(file,p);
        assertTrue(Files.size(file)>PublicationFiles.MAX_BYTES);
        Publication reopened=PublicationLargeFiles.read(file,temp.resolve("cache"));
        assertTrue(reopened.assets.isEmpty());assertEquals(64L*1024*1024,reopened.assetSize("picture"));
        assertEquals(reopened.assetFiles,reopened.copy().assetFiles);
        try(var input=reopened.openAsset("picture")){assertArrayEquals(new byte[]{17,23,42},input.readNBytes(3));}
        assertThrows(IOException.class,()->PublicationFiles.archive(reopened));
    }
    @Test void limitsRejectBeforeReplacingAnExistingTemplate()throws Exception {
        Path file=temp.resolve("keep.sspub");Files.writeString(file,"keep");
        Publication p=new Publication();p.assetFiles.put("large",source((long)PublicationLargeFiles.MAX_IMAGE_BYTES+1));
        assertThrows(IOException.class,()->PublicationLargeFiles.write(file,p));assertEquals("keep",Files.readString(file));
        p.assetFiles.clear();Path image=source(PublicationLargeFiles.MAX_IMAGE_BYTES);
        for(int i=0;i<52;i++)p.assetFiles.put("image"+i,image);
        assertThrows(IOException.class,()->PublicationLargeFiles.write(file,p));assertEquals("keep",Files.readString(file));
    }
    @Test void maliciousEntryAndExpandedImageLimitAreEnforced()throws Exception {
        Path zip=temp.resolve("unsafe.sspub"),cache=temp.resolve("cache");
        try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(zip))){output.putNextEntry(new ZipEntry("assets/../escape"));output.write(1);output.closeEntry();}
        assertThrows(IOException.class,()->PublicationLargeFiles.read(zip,cache));assertFalse(Files.exists(temp.resolve("escape")));
        try(ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(zip))){output.putNextEntry(new ZipEntry("assets/image"));byte[] zeros=new byte[1024*1024];for(int i=0;i<100;i++)output.write(zeros);output.write(1);output.closeEntry();}
        assertThrows(IOException.class,()->PublicationLargeFiles.read(zip,cache));
        try(var paths=Files.list(cache)){assertEquals(0,paths.count());}
    }
    @Test @EnabledIfSystemProperty(named="smartstudio.largeTemplateProof",matches="true")
    void actualNearFiveGiBZip64RoundTripUsesBoundedHeap()throws Exception {
        Publication p=new Publication();p.template=true;Path image=source(PublicationLargeFiles.MAX_IMAGE_BYTES);
        for(int i=0;i<51;i++)p.assetFiles.put("image"+i,image);
        Path file=temp.resolve("five-gib.sspub");PublicationLargeFiles.write(file,p);
        assertTrue(Files.size(file)>4L*1024*1024*1024);assertTrue(Files.size(file)<=PublicationLargeFiles.MAX_BYTES);
        Publication reopened=PublicationLargeFiles.read(file,temp.resolve("cache"));
        assertEquals(51,reopened.assetFiles.size());assertTrue(reopened.assets.isEmpty());
        for(String id:reopened.assetIds())assertEquals(PublicationLargeFiles.MAX_IMAGE_BYTES,reopened.assetSize(id));
        assertEquals(1,reopened.assetFiles.values().stream().distinct().count());
        try(RandomAccessFile input=new RandomAccessFile(reopened.assetFiles.get("image50").toFile(),"r")){input.seek(PublicationLargeFiles.MAX_IMAGE_BYTES-1L);assertEquals(99,input.read());}
        System.out.println("ZIP64 proof: "+Files.size(file)+" bytes; 51 x 100 MiB assets reopened with disk-backed references.");
    }
}
