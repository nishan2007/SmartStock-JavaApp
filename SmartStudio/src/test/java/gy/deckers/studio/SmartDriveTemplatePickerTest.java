package gy.deckers.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SmartDriveTemplatePickerTest {
    @TempDir Path root;
    @Test void validatesConfiguredShareRoots()throws Exception {
        assertEquals(Path.of("\\\\DECKERSDRIVE\\DeckersDrive"),SmartDriveTemplatePicker.networkRoot("\\\\DECKERSDRIVE\\DeckersDrive"));
        for(String invalid:new String[]{"C:\\Users","\\\\server\\share\\..","\\\\?\\C:","https://server/share","\\\\server\\share:stream"})
            assertThrows(IOException.class,()->SmartDriveTemplatePicker.networkRoot(invalid));
    }
    private JsonObject catalog(String path) {
        JsonObject row=new JsonObject();row.addProperty("name","School badge");row.addProperty("ownerStoreId","1");
        row.addProperty("kind","BADGE");row.addProperty("networkPath",path);
        row.addProperty("id","11111111-1111-4111-8111-111111111111");row.addProperty("storageStoreId","1");
        JsonArray rows=new JsonArray();rows.add(row);JsonObject catalog=new JsonObject();catalog.add("templates",rows);return catalog;
    }
    @Test void acceptsOnlyNativeSharePathsForTheRequestedStoreAndFileType()throws Exception {
        String path="\\\\DECKERSDRIVE\\DeckersDrive\\1\\Studio Jobs\\BADGE\\Badge.sstbadge";
        var entries=SmartDriveTemplatePicker.entries(catalog(path),"BADGE");
        assertEquals(1,entries.size());assertEquals(Path.of(path),entries.get(0).file());
        assertEquals("School badge",entries.get(0).name());
        for(String invalid:new String[]{"C:\\Badge.sstbadge","\\\\?\\C:\\Badge.sstbadge","\\\\.\\device\\1\\Badge.sstbadge",
                "\\\\DECKERSDRIVE\\DeckersDrive\\2\\Badge.sstbadge","\\\\DECKERSDRIVE\\DeckersDrive\\1\\..\\Badge.sstbadge",
                "\\\\DECKERSDRIVE\\DeckersDrive\\1\\Badge.exe","\\\\DECKERSDRIVE\\DeckersDrive\\1\\Badge.sstbadge:stream"}) {
            assertThrows(IOException.class,()->SmartDriveTemplatePicker.entries(catalog(invalid),"BADGE"));
        }
    }
    @Test void cutoverStopsLegacyDownloadsAndAutomaticMigration()throws Exception {
        JsonObject response=new JsonObject();response.addProperty("smartDrive",true);
        var badges=new SharedBadgeLibrary(root.resolve("badges"),request->{assertEquals("LIST",request.get("action").getAsString());return response;});
        badges.refresh();assertTrue(badges.smartDrive);assertFalse(badges.online);
        assertEquals(0,badges.migrateLegacy(root,()->true).imported());
        var publications=new SharedPublicationLibrary(root.resolve("publications"),"LABELS",request->{assertEquals("LIST",request.get("action").getAsString());return response;});
        publications.refresh();assertTrue(publications.smartDrive);assertFalse(publications.online);
        assertTrue(publications.files().isEmpty());
        assertEquals("Shared designs are stored in SmartDrive.",publications.migrate(java.util.List.of(),()->true));
    }
}
