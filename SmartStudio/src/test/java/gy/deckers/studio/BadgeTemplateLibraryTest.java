package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BadgeTemplateLibraryTest {
    @TempDir Path folder;
    @Test void libraryIsFlatSortedAndUsesSafeNames()throws Exception {
        BadgeTemplateLibrary library=new BadgeTemplateLibrary(folder);
        BadgeTemplate.builtin().save(library.fileFor("Zulu"));BadgeTemplate.builtin().save(library.fileFor("Alpha"));
        Path migration=Files.createDirectories(folder.resolve("_migration"));BadgeTemplate.builtin().save(migration.resolve("not-listed.sstbadge"));
        Files.writeString(folder.resolve("source.xtp"),"example");
        assertEquals(java.util.List.of("Alpha.sstbadge","Zulu.sstbadge"),library.templates().stream().map(p->p.getFileName().toString()).toList());
        assertTrue(library.contains(library.fileFor("../school:badges")));
        assertEquals("Badge-CON.sstbadge",library.fileFor("CON").getFileName().toString());
        assertFalse(library.contains(migration.resolve("not-listed.sstbadge")));
    }
    @Test void fingerprintDetectsExternalChanges()throws Exception {
        Path file=folder.resolve("badge.sstbadge");BadgeTemplate t=BadgeTemplate.builtin();t.save(file);
        String first=BadgeTemplateLibrary.fingerprint(file);t.name="Updated remotely";t.save(file);
        assertNotEquals(first,BadgeTemplateLibrary.fingerprint(file));
    }
    @Test void conversionToolChecksTemplatesAndRefusesPreviewOverwrite()throws Exception {
        Path file=folder.resolve("badge.sstbadge"), preview=folder.resolve("preview.png");BadgeTemplate.builtin().save(file);
        BadgeTemplateTool.run(new String[]{"validate",file.toString()});BadgeTemplateTool.run(new String[]{"preview",file.toString(),preview.toString()});
        assertTrue(Files.size(preview)>0);assertThrows(java.io.IOException.class,()->BadgeTemplateTool.run(new String[]{"preview",file.toString(),preview.toString()}));
        BadgeTemplateTool.run(new String[]{"check-library",folder.toString()});
        Files.writeString(folder.resolve("broken.sstbadge"),"{}");assertThrows(java.io.IOException.class,()->BadgeTemplateTool.run(new String[]{"check-library",folder.toString()}));
    }
}
