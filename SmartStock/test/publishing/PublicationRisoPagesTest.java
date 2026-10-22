package publishing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicationRisoPagesTest {
    Publication document(){var p=new Publication();var e=new Publication.Element();p.pages.get(0).objects.add(e);var sub=new Publication.RisoPage();sub.name="Black";sub.objectIds.add(e.id);p.pages.get(0).risoPages.add(sub);p.version=2;return p;}
    @Test void membershipsRoundTripWithoutDuplicatingObjectsAndLegacyFilesStillOpen()throws Exception{
        var p=document();var loaded=PublicationFiles.read(PublicationFiles.archive(p));assertEquals(2,loaded.version);assertEquals(1,loaded.pages.size());assertEquals(1,loaded.pages.get(0).objects.size());assertEquals(p.pages.get(0).risoPages.get(0).objectIds,loaded.pages.get(0).risoPages.get(0).objectIds);
        var legacy=PublicationFiles.read(PublicationFiles.archive(new Publication()));assertEquals(1,legacy.version);assertTrue(legacy.pages.get(0).risoPages.isEmpty());
        p.version=1;assertThrows(IllegalArgumentException.class,p::validate);
    }
    @Test void rejectsConflictingMembershipsInvalidNamesAndReservedViewIdentifiers(){var p=document();var sub=new Publication.RisoPage();sub.objectIds.add(p.pages.get(0).objects.get(0).id);p.pages.get(0).risoPages.add(sub);assertThrows(IllegalArgumentException.class,p::validate);p.pages.get(0).risoPages.remove(1);p.pages.get(0).risoExcluded.add(p.pages.get(0).objects.get(0).id);assertThrows(IllegalArgumentException.class,p::validate);p.pages.get(0).risoExcluded.clear();p.pages.get(0).risoPages.get(0).id="@unassigned";assertThrows(IllegalArgumentException.class,p::validate);}
}
