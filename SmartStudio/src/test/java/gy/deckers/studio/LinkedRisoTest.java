package gy.deckers.studio;
import publishing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LinkedRisoTest {
    @TempDir Path folder;
    Publication document(){var p=new Publication();p.width=60;p.height=40;p.print.width=p.print.height=100;p.print.left=p.print.right=p.print.top=p.print.bottom=10;p.print.quantity=1;p.print.cropMarks=false;p.print.alignment="TOP_LEFT";for(int i=0;i<2;i++){var e=new Publication.Element();e.type="RECT";e.x=5+i*25;e.y=5;e.width=10;e.height=10;e.fill="#000000";e.stroke="none";p.pages.get(0).objects.add(e);}return p;}
    @Test void linksFollowSourceEditsReorderingAndNewObjectsStayUnassigned()throws Exception{
        var p=document();var first=p.pages.get(0).objects.get(0);var sub=LinkedRiso.add(p,0);LinkedRiso.assign(p,0,List.of(first.id),sub.id);first.x=17;first.width=12;first.fill="#ff0000";Collections.reverse(p.pages.get(0).objects);var linked=LinkedRiso.visible(p,0,sub.id).get(0);assertSame(first,linked);assertEquals(17,linked.x);assertEquals(12,linked.width);assertEquals("#ff0000",linked.fill);
        var fresh=new Publication.Element();p.pages.get(0).objects.add(fresh);assertEquals(LinkedRiso.UNASSIGNED,LinkedRiso.assignment(p,0,fresh.id));var loaded=PublicationFiles.read(PublicationFiles.archive(p));assertEquals(first.id,LinkedRiso.visible(loaded,0,sub.id).get(0).id);assertEquals(17,LinkedRiso.visible(loaded,0,sub.id).get(0).x);
    }
    @Test void movingBetweenSubPagesAndRemovingThemNeverDuplicatesOrDeletesArtwork(){var p=document();var a=LinkedRiso.add(p,0);var b=LinkedRiso.add(p,0);String id=p.pages.get(0).objects.get(0).id;LinkedRiso.assign(p,0,List.of(id),a.id);LinkedRiso.assign(p,0,List.of(id),b.id);assertTrue(a.objectIds.isEmpty());assertEquals(Set.of(id),b.objectIds);LinkedRiso.remove(p,0,b.id);assertEquals(LinkedRiso.UNASSIGNED,LinkedRiso.assignment(p,0,id));assertEquals(2,p.pages.get(0).objects.size());}
    @Test void deletePrunesLinksAndSharedMasterGeometryRemainsOneSource(){var p=document();var master=new Publication.Page();master.name="Shared";var e=new Publication.Element();master.objects.add(e);p.masters.add(master);p.pages.get(0).master=master.name;var sub=LinkedRiso.add(p,0);LinkedRiso.assign(p,0,List.of(e.id),sub.id);e.x=37;assertSame(e,LinkedRiso.visible(p,0,sub.id).get(0));assertEquals(37,LinkedRiso.visible(p,0,sub.id).get(0).x);LinkedRiso.deleteSource(p,e);assertTrue(sub.objectIds.isEmpty());}
    @Test void subPageOutputUsesOnlyAssignedObjectsAndTracksMovementAtSamePhysicalSize()throws Exception{
        var p=document();var a=LinkedRiso.add(p,0);var b=LinkedRiso.add(p,0);var first=p.pages.get(0).objects.get(0);var second=p.pages.get(0).objects.get(1);LinkedRiso.assign(p,0,List.of(first.id),a.id);LinkedRiso.assign(p,0,List.of(second.id),b.id);
        var job=LinkedRisoOutput.job(p,0,List.of(a.id,b.id));var raster=LinkedRisoOutput.raster(job,0,254);assertEquals(0,raster.getRaster().getSample(200,200,0));assertEquals(255,raster.getRaster().getSample(450,200,0));assertEquals(0,LinkedRisoOutput.raster(job,1,254).getRaster().getSample(450,200,0));
        first.x=15;var moved=LinkedRisoOutput.raster(LinkedRisoOutput.job(p,0,List.of(a.id)),0,254);assertEquals(255,moved.getRaster().getSample(200,200,0));assertEquals(0,moved.getRaster().getSample(300,200,0));
        Path target=folder.resolve("linked.pdf");LinkedRisoOutput.pdf(LinkedRisoOutput.job(p,0,List.of(a.id,b.id)),target);try(var pdf=org.apache.pdfbox.pdmodel.PDDocument.load(target.toFile())){assertEquals(2,pdf.getNumberOfPages());assertEquals(100*72/25.4,pdf.getPage(0).getMediaBox().getWidth(),.001);}
    }
    @Test void unassignedObjectsBlockSilentOmissionAndExclusionsAreExplicit(){var p=document();var sub=LinkedRiso.add(p,0);assertThrows(IllegalArgumentException.class,()->LinkedRisoOutput.job(p,0,List.of(sub.id)));LinkedRiso.assign(p,0,LinkedRiso.objects(p,0).stream().map(e->e.id).toList(),LinkedRiso.EXCLUDED);assertEquals(1,LinkedRisoOutput.job(p,0,List.of(sub.id)).count());}
}
