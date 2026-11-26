package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TableGeometryTest {
    @Test void moveAndResizeKeepCellsBordersAndLinkedIdsTogether()throws Exception {
        var p=new Publication();var grid=StudioTables.create(3,2,60,30);grid.text(0,0,"Quantity");
        grid.merge(1,0,1,1);StudioTables.apply(p,0,grid);var page=p.pages.get(0);
        var selected=page.objects.get(page.objects.size()-1); // Selecting a border also selects the table.
        var ids=page.objects.stream().map(e->e.id).toList();
        var logo=new Publication.Element();logo.text="Unrelated logo";page.objects.add(logo);
        String originalLogo=Publication.JSON.toJson(logo);
        TableGeometry.move(page,selected,20,25);
        TableGeometry.resize(page,selected,new Rectangle2D.Double(20,25,120,60));
        var read=StudioTables.read(page,page.objects.get(0));
        assertEquals(20,read.x,.0001);assertEquals(25,read.y,.0001);
        assertEquals(120,read.width(),.0001);assertEquals(60,read.height(),.0001);
        assertEquals("Quantity",read.cell(0,0).text);assertEquals(2,read.cell(1,0).columnSpan);
        assertEquals(ids,page.objects.subList(0,ids.size()).stream().map(e->e.id).toList());
        assertEquals(originalLogo,Publication.JSON.toJson(logo));
        var saved=PublicationFiles.read(PublicationFiles.archive(p));
        assertEquals(120,TableGeometry.bounds(saved.pages.get(0),saved.pages.get(0).objects.get(0)).getWidth(),.0001);
    }
    @Test void repeatedSelectedMembersDoNotMultiplyMovementAndAnyLockProtectsWholeTable() {
        var p=new Publication();StudioTables.apply(p,0,StudioTables.create(2,2,60,30));var page=p.pages.get(0);
        for(var e:page.objects)TableGeometry.move(page,e,10,20);
        assertEquals(10,TableGeometry.bounds(page,page.objects.get(0)).getX(),.0001);
        page.objects.get(1).locked=true;assertTrue(TableGeometry.locked(page,page.objects.get(0)));
        String before=Publication.JSON.toJson(page);
        TableGeometry.move(page,page.objects.get(0),40,50);
        TableGeometry.resize(page,page.objects.get(0),new Rectangle2D.Double(0,0,80,40));
        assertEquals(before,Publication.JSON.toJson(page));
    }
    @Test void importedBordersMoveWithTheirCells() {
        var p=new Publication();var objects=StudioTables.create(2,2,60,30).elements();
        for(var e:objects){if(e.type.equals("TEXT")){e.sourceTable="native-table";e.group="";}else{e.group="";e.text="Native Publisher table 12";}}
        p.pages.get(0).objects.addAll(objects);
        var line=objects.stream().filter(e->e.type.equals("LINE")).findFirst().orElseThrow();double oldY=line.y;
        TableGeometry.move(p.pages.get(0),objects.get(0),15,25);
        assertEquals(oldY+20,line.y,.0001);
    }
}
