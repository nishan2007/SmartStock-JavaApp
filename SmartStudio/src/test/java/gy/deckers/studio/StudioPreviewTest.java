package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.Publication;
import publishing.PublicationFiles;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import static org.junit.jupiter.api.Assertions.*;

class StudioPreviewTest {
    @Test void publicationPreviewStaysInExistingMetadataAndRoundTrips() throws Exception {
        Publication publication = new Publication();
        byte[] archive = PublicationFiles.archive(publication);
        assertEquals(publication.title, PublicationFiles.read(archive).title);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            assertEquals("publication.json", zip.getNextEntry().getName());
            var json = com.google.gson.JsonParser.parseString(
                    new String(zip.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.get("previewPng").getAsString().startsWith("iVBOR"));
        }
    }
}
