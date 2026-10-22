package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class CanvasImageInsertTest {
 @TempDir Path folder;
 private StudioImageFile.Loaded image()throws Exception{
  BufferedImage bitmap=new BufferedImage(200,100,BufferedImage.TYPE_INT_ARGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(bitmap,"png",bytes);return new StudioImageFile.Loaded(bytes.toByteArray(),bitmap,"Inserted.png");
 }
 @Test void badgeInsertionAddsSelectedArtworkAndSupportsUndo()throws Exception{
  var loaded=image();String previous=System.getProperty("smartstudio.badge.folder");System.setProperty("smartstudio.badge.folder",folder.toString());
  try{SwingUtilities.invokeAndWait(()->{BadgeCardsPanel panel=new BadgeCardsPanel();try{
   var modelField=BadgeCardsPanel.class.getDeclaredField("template");modelField.setAccessible(true);BadgeTemplate model=(BadgeTemplate)modelField.get(panel);int before=model.elements.size();
   panel.insertImageLayer(loaded);assertEquals(before+1,model.elements.size());var added=model.elements.get(before);assertEquals("IMAGE",added.type);assertEquals(2,added.width/added.height,.0001);
   var tableField=BadgeCardsPanel.class.getDeclaredField("table");tableField.setAccessible(true);assertEquals(before,((JTable)tableField.get(panel)).getSelectedRow());
   var undo=BadgeCardsPanel.class.getDeclaredMethod("historyAction",boolean.class);undo.setAccessible(true);undo.invoke(panel,false);assertEquals(before,((BadgeTemplate)modelField.get(panel)).elements.size());
  }catch(Exception ex){throw new RuntimeException(ex);}finally{try{var timer=BadgeCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}});}finally{if(previous==null)System.clearProperty("smartstudio.badge.folder");else System.setProperty("smartstudio.badge.folder",previous);}
 }
 @Test void businessInsertionPreservesExistingLayersAndEmbedsAsset()throws Exception{
  var loaded=image();SwingUtilities.invokeAndWait(()->{BusinessCardsPanel panel=new BusinessCardsPanel();try{
   int before=panel.output().pages.get(0).objects.size();panel.insertImageLayer(loaded);var result=panel.output();assertEquals(before+1,result.pages.get(0).objects.size());var added=result.pages.get(0).objects.get(before);assertEquals("IMAGE",added.type);assertTrue(result.assets.containsKey(added.asset));assertEquals(2,added.width/added.height,.0001);
  }catch(Exception ex){throw new RuntimeException(ex);}finally{try{var timer=BusinessCardsPanel.class.getDeclaredField("refresh");timer.setAccessible(true);((Timer)timer.get(panel)).stop();}catch(Exception ex){throw new RuntimeException(ex);}}});
 }
}
