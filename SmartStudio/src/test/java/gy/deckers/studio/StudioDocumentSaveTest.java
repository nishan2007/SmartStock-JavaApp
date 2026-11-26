package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class StudioDocumentSaveTest {
    @TempDir Path folder;

    @Test void saveAsDoesNotOverwriteAConcurrentCreationOrAChangedReplacement()throws Exception {
        Path file=folder.resolve("New shared design.sspub");
        String absent=StudioDocumentSave.observe(file);assertEquals("",absent);
        Files.writeString(file,"other computer created this");
        assertThrows(IOException.class,()->StudioDocumentSave.write(file,absent,path->fail("Concurrent creation overwritten.")));
        assertEquals("other computer created this",Files.readString(file));
        String observed=StudioDocumentSave.observe(file);
        Files.writeString(file,"other computer revised this");
        assertThrows(IOException.class,()->StudioDocumentSave.write(file,observed,path->fail("Changed replacement overwritten.")));
        assertEquals("other computer revised this",Files.readString(file));
        String current=StudioDocumentSave.observe(file);
        StudioDocumentSave.write(file,current,path->Files.writeString(path,"approved replacement"));
        assertEquals("approved replacement",Files.readString(file));
        Path fresh=folder.resolve("Different name.sspub");
        StudioDocumentSave.write(fresh,StudioDocumentSave.observe(fresh),path->Files.writeString(path,"new file"));
        assertEquals("new file",Files.readString(fresh));
    }

    @Test void renameLocksBothNamesAndRejectsOutdatedContent() throws Exception {
        Path source=folder.resolve("Original.sspub"), destination=folder.resolve("Renamed.sspub");
        Files.writeString(source,"original");
        String revision=BadgeTemplateLibrary.fingerprint(source);
        for(Path locked:new Path[]{source,destination}) {
            StudioDocumentSave.locked(locked,()->{
                assertThrows(IOException.class,()->StudioFileNames.renameWithContent(source,"Renamed",".sspub",revision,
                        path->fail("Rename entered while another save owns a file name.")));
                return null;
            });
            assertEquals("original",Files.readString(source));
            assertFalse(Files.exists(destination));
        }
        StudioDocumentSave.write(source,revision,path->Files.writeString(path,"newer"));
        assertThrows(IOException.class,()->StudioFileNames.renameWithContent(source,"Renamed",".sspub",revision,
                path->fail("Outdated rename entered.")));
        String current=BadgeTemplateLibrary.fingerprint(source);
        assertEquals(destination,StudioFileNames.renameWithContent(source,"Renamed",".sspub",current,
                path->Files.writeString(path,"renamed content")));
        assertFalse(Files.exists(source));
        assertEquals("renamed content",Files.readString(destination));
        assertThrows(IOException.class,()->StudioDocumentSave.write(source,current,path->fail("Old path recreated.")));
    }

    @Test void serializesSavesAndRejectsAnOutdatedRevision() throws Exception {
        Path file = folder.resolve("Shared design.sspub");
        Files.writeString(file, "original");
        String revision = BadgeTemplateLibrary.fingerprint(file);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> StudioDocumentSave.write(file, revision, path -> {
                writing.countDown();
                if (!finish.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out.");
                Files.writeString(path, "saved");
            }));
            assertTrue(writing.await(10, TimeUnit.SECONDS));
            assertThrows(IOException.class,
                    () -> StudioDocumentSave.write(file, revision, path -> fail("Concurrent write entered.")));
            finish.countDown();
            String savedRevision = first.get(10, TimeUnit.SECONDS);
            assertEquals(BadgeTemplateLibrary.fingerprint(file), savedRevision);
            assertThrows(IOException.class,
                    () -> StudioDocumentSave.write(file, revision, path -> fail("Stale write entered.")));
            assertEquals("saved", Files.readString(file));
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
    }
}
