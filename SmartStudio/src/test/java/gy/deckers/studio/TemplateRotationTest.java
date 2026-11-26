package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.Publication;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class TemplateRotationTest {
 @TempDir Path temp;
 @Test void orientationTurnsEveryPageAndMasterAndRoundTripsWithoutStretching(){
  Publication p=new Publication();p.width=100;p.height=60;
  p.masters.add(new Publication.Page());p.pages.add(new Publication.Page());
  for(var page:p.allPages()){
   var e=new Publication.Element();e.type="RECT";e.x=10;e.y=5;e.width=40;e.height=20;e.rotation=30;page.objects.add(e);
   page.horizontalGuides.add(15.);page.verticalGuides.add(25.);
  }
  assertTrue(TemplateOrientation.turn(p,true));assertEquals(60,p.width);assertEquals(100,p.height);
  for(var page:p.allPages()){
   var e=page.objects.get(0);assertEquals(25,e.x);assertEquals(20,e.y);assertEquals(40,e.width);assertEquals(20,e.height);assertEquals(120,e.rotation);
   assertEquals(45,page.verticalGuides.get(0));assertEquals(25,page.horizontalGuides.get(0));
  }
  assertFalse(TemplateOrientation.turn(p,true));assertTrue(TemplateOrientation.turn(p,false));
  for(var page:p.allPages()){var e=page.objects.get(0);assertEquals(10,e.x);assertEquals(5,e.y);assertEquals(30,e.rotation);assertEquals(15,page.horizontalGuides.get(0));assertEquals(25,page.verticalGuides.get(0));}
 }
 @Test void badgeRotationPersistsAndActuallyTurnsArtwork()throws Exception{
  BadgeTemplate p=new BadgeTemplate();p.widthMm=100;p.heightMm=60;
  var e=new BadgeTemplate.Element();e.type="RECT";e.label="Rectangle";e.x=10;e.y=5;e.width=40;e.height=20;p.elements.add(e);
  var before=p.render(java.util.Map.of(),null,254);
  assertTrue(TemplateOrientation.turn(p,true));
  Path file=temp.resolve("portrait.sstbadge");p.save(file);p=BadgeTemplate.load(file);
  assertEquals(90,p.elements.get(0).rotation);assertEquals(40,p.elements.get(0).width);
  var after=p.render(java.util.Map.of(),null,254);
  assertEquals(before.getWidth(),after.getHeight());assertEquals(before.getHeight(),after.getWidth());
  // Compare interior pixels after an exact clockwise turn, away from antialiased edges.
  for(int y=20;y<before.getHeight()-20;y+=19)for(int x=20;x<before.getWidth()-20;x+=19)
   assertEquals(before.getRGB(x,y),after.getRGB(before.getHeight()-1-y,x));
  assertTrue(TemplateOrientation.turn(p,false));assertEquals(0,p.elements.get(0).rotation);
 }
 @Test void presetsAreRelativeAndTypedAngleIsAbsoluteAndSelectionSafe()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var selection=new AtomicReference<>(new ImageFormattingToolbar.Picture("image",40,20,0,0));
   var toolbar=new ImageFormattingToolbar(selection::get,size->{});
   toolbar.setRotationEditor(angle->selection.set(new ImageFormattingToolbar.Picture("image",40,20,0,angle)));
   click(toolbar,"Right 90°");assertEquals(90,selection.get().rotation());click(toolbar,"Rotate 180°");assertEquals(270,selection.get().rotation());click(toolbar,"Left 90°");assertEquals(180,selection.get().rotation());
   ((JSpinner.DefaultEditor)toolbar.angle.getEditor()).getTextField().setText("45");toolbar.angle.commitEdit();assertEquals(45,selection.get().rotation());
   toolbar.angle.setValue(-30.);assertEquals(330,selection.get().rotation());assertEquals(40,selection.get().width());assertEquals(20,selection.get().height());
   selection.set(null);toolbar.refresh();assertFalse(toolbar.angle.isEnabled());
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 @Test void editorRotationAndOrientationSupportUndoAndArchiveRoundTrip()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var editor=new BusinessCardsPanel(true);var f=BusinessCardsPanel.class.getDeclaredField("document");f.setAccessible(true);var p=(Publication)f.get(editor);
   p.width=100;p.height=60;p.pages.get(0).objects.clear();publishing.PublicationImport.addImage(p,p.pages.get(0),new java.awt.image.BufferedImage(80,40,1),3,4,40,20);
   f=BusinessCardsPanel.class.getDeclaredField("layerTable");f.setAccessible(true);var table=(JTable)f.get(editor);((javax.swing.table.AbstractTableModel)table.getModel()).fireTableDataChanged();table.setRowSelectionInterval(0,0);
   editor.applyPictureRotation(45);assertEquals(45,editor.output().pages.get(0).objects.get(0).rotation);
   editor.orientTemplate(true);assertEquals(60,editor.output().width);assertEquals(135,editor.output().pages.get(0).objects.get(0).rotation);
   var loaded=publishing.PublicationFiles.read(publishing.PublicationFiles.archive(editor.output()));
   assertEquals(135,loaded.pages.get(0).objects.get(0).rotation);assertEquals(60,loaded.width);
   editor.historyAction(false);assertEquals(100,editor.output().width);assertEquals(45,editor.output().pages.get(0).objects.get(0).rotation);
   editor.historyAction(true);assertEquals(60,editor.output().width);assertEquals(135,editor.output().pages.get(0).objects.get(0).rotation);
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 @Test void pictureRibbonFitsAtEveryDisplayBudget()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var editor=new BusinessCardsPanel(true);
   try{var f=BusinessCardsPanel.class.getDeclaredField("document");f.setAccessible(true);var p=(Publication)f.get(editor);p.pages.get(0).objects.clear();publishing.PublicationImport.addImage(p,p.pages.get(0),new java.awt.image.BufferedImage(80,40,1),3,4,40,20);f=BusinessCardsPanel.class.getDeclaredField("layerTable");f.setAccessible(true);var table=(JTable)f.get(editor);((javax.swing.table.AbstractTableModel)table.getModel()).fireTableDataChanged();table.setRowSelectionInterval(0,0);}catch(Exception e){throw new RuntimeException(e);}
   var ribbon=find(editor,StudioRibbon.class);var tabs=find(ribbon,JTabbedPane.class);
   for(int screen:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5}){
    ribbon.select("Picture format");ribbon.setSize((int)(screen/scale)-32,240);layout(ribbon);ribbon.setSize(ribbon.getWidth(),ribbon.getPreferredSize().height);layout(ribbon);
    Container page=(Container)tabs.getSelectedComponent();var toolbar=find(page,ImageFormattingToolbar.class);assertNotNull(toolbar);
    if(screen==1366&&scale==1){try{var image=new java.awt.image.BufferedImage(page.getWidth(),page.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();page.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",Path.of("target/picture-rotation-ribbon.png").toFile());}catch(Exception e){throw new RuntimeException(e);}}
    for(Component c:toolbar.getComponents()){Rectangle b=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),page);assertTrue(b.x>=0&&b.y>=0&&b.x+b.width<=page.getWidth()&&b.y+b.height<=page.getHeight(),screen+" / "+scale+" "+b+" "+page.getSize());}
   }
  });
 }
 private static void click(Container c,String text){for(Component item:c.getComponents())if(item instanceof JButton b&&text.equals(b.getText())){b.doClick();return;}fail(text);}
 private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
 private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T r=find(n,type);if(r!=null)return r;}}return null;}
}
