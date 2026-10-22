package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.image.BufferedImage;
import publishing.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplateSelectionIntegrationTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder;
    Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    void call(Object target,String name,Class<?>[] types,Object... args)throws Exception{var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);}
    void stop(BusinessCardsPanel p)throws Exception{for(String name:new String[]{"refresh","thumbnailRefresh"})((Timer)field(p,name)).stop();}
    @Test void tableMultiSelectionMovesTogetherAndUndoRestoresAllObjects()throws Exception{SwingUtilities.invokeAndWait(()->{try{
        var panel=new BusinessCardsPanel();try{var p=(Publication)field(panel,"document");p.pages.get(0).objects.clear();for(int i=0;i<2;i++){var e=new Publication.Element();e.x=10+i*30;e.y=10;p.pages.get(0).objects.add(e);}call(panel,"resetHistory",new Class<?>[]{});
            JTable table=(JTable)field(panel,"layerTable");((AbstractTableModel)table.getModel()).fireTableDataChanged();table.addRowSelectionInterval(0,1);var canvas=(TemplateWorkspace)field(panel,"design");assertArrayEquals(new int[]{0,1},canvas.selectedIndices());canvas.getActionMap().get("shift RIGHT").actionPerformed(null);assertEquals(11,p.pages.get(0).objects.get(0).x);assertEquals(41,p.pages.get(0).objects.get(1).x);assertEquals(2,table.getSelectedRowCount());assertArrayEquals(new int[]{0,1},canvas.selectedIndices());call(panel,"historyAction",new Class<?>[]{boolean.class},false);assertEquals(10,panel.output().pages.get(0).objects.get(0).x);assertEquals(40,panel.output().pages.get(0).objects.get(1).x);
        }finally{stop(panel);}
    }catch(Exception ex){throw new RuntimeException(ex);}});}
    @Test void imageRecolourCreatesIndependentPortableAssetAndUndoRestoresOriginal()throws Exception{SwingUtilities.invokeAndWait(()->{try{
        for(boolean labels:new boolean[]{false,true}){var panel=new BusinessCardsPanel(labels);try{var p=(Publication)field(panel,"document");p.pages.get(0).objects.clear();var image=new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB);PublicationImport.addImage(p,p.pages.get(0),image,3,4,20,15);var first=p.pages.get(0).objects.get(0);String originalAsset=first.asset;var second=Publication.JSON.fromJson(Publication.JSON.toJson(first),Publication.Element.class);second.id="other";second.x=30;p.pages.get(0).objects.add(second);call(panel,"resetHistory",new Class<?>[]{});
            JTable table=(JTable)field(panel,"layerTable");((AbstractTableModel)table.getModel()).fireTableDataChanged();table.setRowSelectionInterval(0,0);var red=new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB);red.setRGB(0,0,0x80ff0000);panel.applyPictureRecolour(red);var output=panel.output();assertNotEquals(originalAsset,output.pages.get(0).objects.get(0).asset);assertEquals(originalAsset,output.pages.get(0).objects.get(1).asset);assertTrue(output.assets.containsKey(originalAsset));assertEquals(3,output.pages.get(0).objects.get(0).x);assertEquals(20,output.pages.get(0).objects.get(0).width);PublicationFiles.read(PublicationFiles.archive(output));call(panel,"historyAction",new Class<?>[]{boolean.class},false);assertEquals(originalAsset,panel.output().pages.get(0).objects.get(0).asset);
        }finally{stop(panel);}}
    }catch(Exception ex){throw new RuntimeException(ex);}});}
    @Test void badgeGroupMovementAndRecolourSupportUndo()throws Exception{
        String previous=System.getProperty("smartstudio.badge.folder");System.setProperty("smartstudio.badge.folder",folder.toString());
        try{SwingUtilities.invokeAndWait(()->{try{var panel=new BadgeCardsPanel();try{
            var template=(BadgeTemplate)field(panel,"template");template.elements.clear();String bytes=java.util.Base64.getEncoder().encodeToString(ImageRecolour.png(new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB)));
            for(int i=0;i<2;i++){var e=new BadgeTemplate.Element();e.type="IMAGE";e.label="Artwork "+i;e.image=bytes;e.x=5+i*25;e.y=5;e.width=20;e.height=15;template.elements.add(e);}call(panel,"changedTemplate",new Class<?>[]{});
            JTable table=(JTable)field(panel,"table");table.addRowSelectionInterval(0,1);var canvas=(TemplateWorkspace)field(panel,"preview");assertEquals(2,canvas.selectedIndices().length);canvas.getActionMap().get("shift RIGHT").actionPerformed(null);assertEquals(6,template.elements.get(0).x);assertEquals(31,template.elements.get(1).x);call(panel,"historyAction",new Class<?>[]{boolean.class},false);template=(BadgeTemplate)field(panel,"template");assertEquals(5,template.elements.get(0).x);assertEquals(30,template.elements.get(1).x);
            table.setRowSelectionInterval(0,0);var red=new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB);red.setRGB(0,0,0x80ff0000);panel.applyPictureRecolour(red);assertNotEquals(bytes,template.elements.get(0).image);assertEquals(bytes,template.elements.get(1).image);assertEquals(20,template.elements.get(0).width);template.validate();BadgeTemplate.read(new java.io.ByteArrayInputStream(BadgeTemplate.portableJson(template).getBytes(java.nio.charset.StandardCharsets.UTF_8)));call(panel,"historyAction",new Class<?>[]{boolean.class},false);assertEquals(bytes,((BadgeTemplate)field(panel,"template")).elements.get(0).image);
        }finally{((Timer)field(panel,"refresh")).stop();}}catch(Exception ex){throw new RuntimeException(ex);}});}finally{if(previous==null)System.clearProperty("smartstudio.badge.folder");else System.setProperty("smartstudio.badge.folder",previous);}
    }
}
