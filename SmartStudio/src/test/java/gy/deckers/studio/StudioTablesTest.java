package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioTablesTest {
    @Test void newTableClosesBottomAndSurvivesArchiveWithEditableCells()throws Exception{
        var p=new Publication();p.width=100;p.height=100;var g=StudioTables.create(3,2,60,30);g.text(0,0,"QTY");g.text(0,1,"Description");StudioTables.apply(p,0,g);
        var saved=PublicationFiles.read(PublicationFiles.archive(p));var read=StudioTables.read(saved.pages.get(0),saved.pages.get(0).objects.get(0));assertEquals(3,read.rows());assertEquals(2,read.columns());assertEquals("QTY",read.cell(0,0).text);
        var image=new BufferedImage(500,500,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();graphics.scale(5,5);new PublicationRenderer(saved).page(graphics,0,Map.of());graphics.dispose();
        for(int x=30;x<325;x++)assertTrue((image.getRGB(x,175)&0xffffff)<0x999999,"Bottom border must be closed at "+x);
    }
    @Test void rowsColumnsAndMergePreserveContentAndUnequalSizes(){
        var g=StudioTables.create(3,3,90,30);g.widths.set(0,10.);g.widths.set(1,50.);g.text(1,1,"Middle");g.insert(true,1);g.insert(false,1);assertEquals("Middle",g.cell(2,2).text);g.delete(true,0);g.delete(false,0);assertEquals("Middle",g.cell(1,1).text);assertEquals(50,g.widths.get(1));
        g.merge(0,0,1,1);assertEquals(2,g.cell(1,1).rowSpan);assertTrue(g.cell(0,0).text.contains("Middle"));g.insert(true,1);assertEquals(3,g.cell(1,0).rowSpan);g.delete(false,0);assertEquals(1,g.cell(0,0).columnSpan);g.split(0,0);assertEquals(1,g.cell(0,0).rowSpan);assertNotSame(g.cell(0,0),g.cell(1,0));
        assertThrows(IllegalArgumentException.class,()->StudioTables.create(100,100,100,100));assertThrows(IllegalArgumentException.class,()->g.resize(Double.NaN,10));
    }
    @Test void importedCellsRetainWidthsArtworkAndOtherPageObjectsWhenEditing(){
        var p=new Publication();var g=StudioTables.create(2,3,80,25);g.widths.clear();g.widths.addAll(java.util.List.of(10.,50.,20.));g.text(0,0,"QTY");
        var old=g.elements();for(var e:old){if(e.type.equals("TEXT")){e.sourceTable="publisher-table";e.group="";}else{e.group="Native Publisher table 319";e.text="Native Publisher table 319";}}
        // Simulate the missing lower border in the user's imported bill book.
        old.removeIf(e->e.type.equals("LINE")&&Math.abs(e.y-30)<.01&&e.width>e.height);p.pages.get(0).objects.addAll(old);
        var logo=new Publication.Element();logo.text="Company";p.pages.get(0).objects.add(logo);String logoJson=Publication.JSON.toJson(logo);
        var draft=StudioTables.read(p.pages.get(0),old.get(0));assertEquals(java.util.List.of(10.,50.,20.),draft.widths);draft.text(1,1,"Changed");assertEquals("",old.stream().filter(e->e.type.equals("TEXT")&&e.sourceRow==1&&e.sourceColumn==1).findFirst().orElseThrow().text,"Detached draft must not change original");
        StudioTables.apply(p,0,draft);assertEquals(logoJson,Publication.JSON.toJson(p.pages.get(0).objects.stream().filter(e->e.id.equals(logo.id)).findFirst().orElseThrow()));assertTrue(p.pages.get(0).objects.stream().anyMatch(e->e.type.equals("LINE")&&Math.abs(e.y-30)<.01&&e.width>e.height));assertEquals("Changed",StudioTables.read(p.pages.get(0),p.pages.get(0).objects.get(0)).cell(1,1).text);
    }
    @Test void pastedTablesRemainIndependentAndIdentifiable(){
        var p=new Publication();var g=StudioTables.create(2,2,60,30);StudioTables.apply(p,0,g);int count=p.pages.get(0).objects.size();int[] indices=java.util.stream.IntStream.range(0,count).toArray();
        var pasted=StudioLayerClipboard.paste(p,0,StudioLayerClipboard.publication(p,0,indices));var a=StudioTables.read(pasted.pages.get(0),pasted.pages.get(0).objects.get(0));var b=StudioTables.read(pasted.pages.get(0),pasted.pages.get(0).objects.get(count));assertNotEquals(a.id,b.id);assertEquals(count,a.replaced.size());assertEquals(count,b.replaced.size());
    }
    @Test void linkedRisoMembershipSurvivesTableEditsAndNewTablesStayOnMainPage(){
        var p=new Publication();p.version=2;var table=new Publication.Element();table.type="TABLE";table.text="A\tB\nC\tD";p.pages.get(0).objects.add(table);var sub=new Publication.RisoPage();sub.objectIds.add(table.id);p.pages.get(0).risoPages.add(sub);
        var g=StudioTables.read(p.pages.get(0),table);g.insert(true,1);StudioTables.apply(p,0,g);assertEquals(p.pages.get(0).objects.size(),sub.objectIds.size());
        int members=sub.objectIds.size();StudioTables.apply(p,0,StudioTables.create(2,2,50,20));assertEquals(members,sub.objectIds.size());assertTrue(p.pages.get(0).risoExcluded.isEmpty());
        var edited=StudioTables.read(p.pages.get(0),p.pages.get(0).objects.get(0));var borders=p.pages.get(0).objects.stream().filter(e->e.group.equals(edited.id)&&e.type.equals("LINE")).toList();sub.objectIds.remove(borders.get(0).id);p.pages.get(0).risoExcluded.add(borders.get(0).id);String before=Publication.JSON.toJson(p);assertThrows(IllegalArgumentException.class,()->StudioTables.apply(p,0,edited));assertEquals(before,Publication.JSON.toJson(p));
    }
    @Test void tableEditorAppliesCellAndStructureCommandsOnDetachedDraft()throws Exception{
        SwingUtilities.invokeAndWait(()->{var grid=StudioTables.create(3,3,60,30);var editor=new TableEditor(grid);editor.table.setRowSelectionInterval(1,1);editor.table.setColumnSelectionInterval(1,1);editor.table.setValueAt("Cell",1,1);find(editor,"Row below").doClick();assertEquals(4,grid.rows());find(editor,"Column right").doClick();assertEquals(4,grid.columns());assertEquals("Cell",grid.cell(1,1).text);editor.finish();});
    }
    @Test void tableCommandsArePresentAndTableEditsParticipateInUndo()throws Exception{
        SwingUtilities.invokeAndWait(()->{try{var panel=new BusinessCardsPanel(false,false,true);assertNotNull(find(panel,"Insert table…"));assertNotNull(find(panel,"Edit table…"));var f=BusinessCardsPanel.class.getDeclaredField("document");f.setAccessible(true);var p=(Publication)f.get(panel);int before=p.pages.get(0).objects.size();StudioTables.apply(p,0,StudioTables.create(2,2,60,30));var changed=BusinessCardsPanel.class.getDeclaredMethod("changed");changed.setAccessible(true);changed.invoke(panel);panel.historyAction(false);assertEquals(before,((Publication)f.get(panel)).pages.get(0).objects.size());panel.historyAction(true);assertTrue(((Publication)f.get(panel)).pages.get(0).objects.size()>before);for(String name:new String[]{"refresh","thumbnailRefresh"}){var timer=BusinessCardsPanel.class.getDeclaredField(name);timer.setAccessible(true);((javax.swing.Timer)timer.get(panel)).stop();}}catch(Exception ex){throw new RuntimeException(ex);}});
    }
    private static JButton find(Container root,String name){for(Component c:root.getComponents()){if(c instanceof JButton b&&name.equals(b.getText()))return b;if(c instanceof Container child){var found=find(child,name);if(found!=null)return found;}}return null;}
}
