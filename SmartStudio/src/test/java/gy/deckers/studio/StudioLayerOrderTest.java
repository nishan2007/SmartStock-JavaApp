package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioLayerOrderTest {
    @Test void oneStepKeepsNoncontiguousSelectionInOrder(){
        var layers=new ArrayList<>(List.of("a","b","c","d","e"));
        var result=StudioLayerOrder.move(layers,new int[]{1,3},StudioLayerOrder.Move.FORWARD,e->false);
        assertEquals(List.of("a","c","b","e","d"),layers);
        assertArrayEquals(new int[]{2,4},result.selection());
        StudioLayerOrder.move(layers,result.selection(),StudioLayerOrder.Move.BACKWARD,e->false);
        assertEquals(List.of("a","b","c","d","e"),layers);
    }
    @Test void frontAndBackPreserveSelectedRelativeOrderAndHandleBoundaries(){
        var layers=new ArrayList<>(List.of("a","b","c","d","e"));
        var result=StudioLayerOrder.move(layers,new int[]{1,3},StudioLayerOrder.Move.FRONT,e->false);
        assertEquals(List.of("a","c","e","b","d"),layers);
        assertFalse(StudioLayerOrder.move(layers,result.selection(),StudioLayerOrder.Move.FORWARD,e->false).changed());
        result=StudioLayerOrder.move(layers,result.selection(),StudioLayerOrder.Move.BACK,e->false);
        assertEquals(List.of("b","d","a","c","e"),layers);
        assertArrayEquals(new int[]{0,1},result.selection());
        assertFalse(StudioLayerOrder.move(layers,result.selection(),StudioLayerOrder.Move.BACKWARD,e->false).changed());
    }
    @Test void lockedSelectionAndEmptySelectionDoNotMove(){
        var layers=new ArrayList<>(List.of("a","b","c"));
        assertFalse(StudioLayerOrder.move(layers,new int[]{1},StudioLayerOrder.Move.FRONT,e->e.equals("b")).changed());
        assertFalse(StudioLayerOrder.move(layers,new int[0],StudioLayerOrder.Move.BACK,e->false).changed());
        assertEquals(List.of("a","b","c"),layers);
    }
    @Test void editorRetainsSelectedObjectsAndUndoRestoresOrder()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            try{
                for(boolean labels:List.of(false,true)){
                    var panel=new BusinessCardsPanel(labels);
                    var field=BusinessCardsPanel.class.getDeclaredField("document");field.setAccessible(true);
                    var p=(publishing.Publication)field.get(panel);var objects=p.pages.get(0).objects;objects.clear();
                    for(String name:List.of("Background","Seal","Barcode","Title")){var e=new publishing.Publication.Element();e.text=name;objects.add(e);}
                    var tableField=BusinessCardsPanel.class.getDeclaredField("layerTable");tableField.setAccessible(true);
                    var table=(javax.swing.JTable)tableField.get(panel);
                    ((javax.swing.table.AbstractTableModel)table.getModel()).fireTableDataChanged();
                    var reset=BusinessCardsPanel.class.getDeclaredMethod("resetHistory");reset.setAccessible(true);reset.invoke(panel);
                    table.addRowSelectionInterval(1,1);table.addRowSelectionInterval(2,2);
                    panel.reorderLayers(StudioLayerOrder.Move.FRONT);
                    assertEquals(List.of("Background","Title","Seal","Barcode"),panel.output().pages.get(0).objects.stream().map(e->e.text).toList());
                    assertArrayEquals(new int[]{2,3},table.getSelectedRows());
                    var reopened=publishing.PublicationFiles.read(publishing.PublicationFiles.archive(panel.output()));
                    assertEquals("Barcode",reopened.pages.get(0).objects.get(3).text);
                    panel.historyAction(false);
                    assertEquals(List.of("Background","Seal","Barcode","Title"),panel.output().pages.get(0).objects.stream().map(e->e.text).toList());
                    panel.historyAction(true);
                    assertEquals("Barcode",panel.output().pages.get(0).objects.get(3).text);
                    assertEquals(4,table.getComponentPopupMenu().getComponentCount());
                }
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}

