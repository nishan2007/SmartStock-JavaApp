package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import publishing.Publication;
import java.awt.datatransfer.*;
import static org.junit.jupiter.api.Assertions.*;
class StudioLayerClipboardTest {
    @Test void publicationSnapshotPastesAcrossPagesAndTemplatesWithIndependentAssetsAndStories(){
        var source=new Publication();var e=new Publication.Element();e.type="IMAGE";e.asset="art";e.story="story";e.group="group";e.x=12;e.rotation=30;
        source.assets.put("art","encoded");source.assets.put("unused","other");source.stories.put("story","Linked text");source.pages.get(0).objects.add(e);
        var payload=StudioLayerClipboard.publication(source,0,new int[]{0});e.x=80;source.assets.put("art","changed");
        var target=new Publication();target.pages.add(new Publication.Page());
        var first=StudioLayerClipboard.paste(target,1,payload);var a=first.pages.get(1).objects.get(0);
        assertEquals(12,a.x);assertEquals(30,a.rotation);assertNotEquals(e.id,a.id);assertEquals("encoded",first.assets.get(a.asset));assertEquals("Linked text",first.stories.get(a.story));assertEquals(1,first.assets.size());assertTrue(target.pages.get(1).objects.isEmpty());
        var second=StudioLayerClipboard.paste(first,1,payload);assertNotEquals(a.id,second.pages.get(1).objects.get(1).id);assertNotEquals(a.group,second.pages.get(1).objects.get(1).group);
    }
    @Test void badgeCopiesAreIndependentAndFailureDoesNotMutateTarget(){
        var source=new BadgeTemplate();var e=new BadgeTemplate.Element();e.label="Title";e.text="Hello";e.width=30;e.height=6;source.elements.add(e);
        var payload=StudioLayerClipboard.badge(source,new int[]{0});e.text="Changed";
        var pasted=StudioLayerClipboard.paste(source,payload);assertEquals("Hello",pasted.elements.get(1).text);assertNotEquals(e.photoId,pasted.elements.get(1).photoId);
        var small=new BadgeTemplate();small.widthMm=10;assertThrows(IllegalArgumentException.class,()->StudioLayerClipboard.paste(small,payload));assertTrue(small.elements.isEmpty());
    }
    @Test void clipboardUsesDedicatedFlavor()throws Exception{
        var payload=StudioLayerClipboard.publication(new Publication(),0,new int[0]);var transfer=StudioLayerClipboard.transfer(payload);
        assertSame(payload,transfer.getTransferData(StudioLayerClipboard.FLAVOR));assertFalse(transfer.isDataFlavorSupported(DataFlavor.stringFlavor));assertThrows(UnsupportedFlavorException.class,()->transfer.getTransferData(DataFlavor.stringFlavor));
    }

    @Test void shortcutsWorkOnCanvasAndLeaveTableCellEditorsAlone()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            int[] calls={0,0};var canvas=new javax.swing.JPanel();StudioLayerClipboard.bind(canvas,()->calls[0]++,()->calls[1]++);
            canvas.getActionMap().get("copyStudioLayers").actionPerformed(null);canvas.getActionMap().get("pasteStudioLayers").actionPerformed(null);
            assertArrayEquals(new int[]{1,1},calls);
            var table=new javax.swing.JTable(1,1);StudioLayerClipboard.bind(table,()->calls[0]++,()->calls[1]++);assertTrue(table.editCellAt(0,0));
            table.getActionMap().get("copyStudioLayers").actionPerformed(null);table.getActionMap().get("pasteStudioLayers").actionPerformed(null);assertArrayEquals(new int[]{1,1},calls);
        });
    }
}
