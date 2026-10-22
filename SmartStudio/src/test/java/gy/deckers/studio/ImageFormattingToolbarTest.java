package gy.deckers.studio;
import org.junit.jupiter.api.Test;import javax.swing.*;import java.awt.geom.*;import java.util.concurrent.atomic.*;import static org.junit.jupiter.api.Assertions.*;
class ImageFormattingToolbarTest{
 @Test void transparencyRefreshDoesNotEditAndChangingItAppliesToSelection()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var selected=new AtomicReference<>(new ImageFormattingToolbar.Picture("one",40,20,35));var applied=new AtomicInteger(-1);
   var toolbar=new ImageFormattingToolbar(selected::get,size->{});
   toolbar.setTransparencyEditor(value->{applied.set(value);selected.set(new ImageFormattingToolbar.Picture("one",40,20,value));});
   assertEquals(35,toolbar.transparency.getValue());assertEquals(-1,applied.get());
   toolbar.transparency.setValue(65);assertEquals(65,applied.get());
   selected.set(null);toolbar.refresh();assertFalse(toolbar.transparency.isEnabled());
  });
 }
 @Test void proportionalAndIndependentSizesValidatePhysicalDimensions(){
  var p=new ImageFormattingToolbar.Picture("image",40,20);
  assertEquals(new ImageFormattingToolbar.Size(80,40),ImageFormattingToolbar.resized(p,true,80,true));
  assertEquals(new ImageFormattingToolbar.Size(60,30),ImageFormattingToolbar.resized(p,false,30,true));
  assertEquals(new ImageFormattingToolbar.Size(40,30),ImageFormattingToolbar.resized(p,false,30,false));
  assertThrows(IllegalArgumentException.class,()->ImageFormattingToolbar.resized(p,true,Double.NaN,true));
  assertThrows(IllegalArgumentException.class,()->ImageFormattingToolbar.resized(p,true,0,true));
 }
 @Test void startsLockedUpdatesCurrentSizesAndConvertsUnitsWithoutEditing()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   var selected=new AtomicReference<>(new ImageFormattingToolbar.Picture("one",50.8,25.4));var count=new AtomicInteger();
   var toolbar=new ImageFormattingToolbar(selected::get,size->{count.incrementAndGet();selected.set(new ImageFormattingToolbar.Picture(selected.get().key(),size.width(),size.height()));});
   assertTrue(toolbar.locked.isSelected());assertEquals(50.8,toolbar.width.getValue());assertEquals(0,count.get());
   toolbar.width.setValue(101.6);assertEquals(50.8,selected.get().height());assertEquals(50.8,toolbar.height.getValue());
   toolbar.units.setSelectedIndex(1);assertEquals(4.,toolbar.width.getValue());assertEquals(2.,toolbar.height.getValue());assertEquals(1,count.get());
   toolbar.locked.doClick();toolbar.height.setValue(3.);assertEquals(101.6,selected.get().width());assertEquals(76.2,selected.get().height(),.00001);
   selected.set(new ImageFormattingToolbar.Picture("two",30,60));toolbar.refresh();assertTrue(toolbar.locked.isSelected());
   selected.set(null);toolbar.refresh();assertFalse(toolbar.width.isEnabled());
  });
 }
 @Test void ratioLockAlsoAppliesToCanvasSideAndCornerHandles(){
  var b=new Rectangle2D.Double(5,10,40,20);
  var side=TemplateWorkspace.resized(b,3,20,0,true);assertEquals(60,side.getWidth());assertEquals(30,side.getHeight());assertEquals(b.getCenterY(),side.getCenterY());
  var free=TemplateWorkspace.resized(b,4,20,0,false);assertEquals(60,free.getWidth());assertEquals(20,free.getHeight());
  for(int handle=0;handle<8;handle++){var next=TemplateWorkspace.resized(b,handle,12,7,true);assertEquals(2,next.getWidth()/next.getHeight(),.000001);}
 }
 @Test void topSizeControlsUpdateCardsAndLabelsWithoutChangingPictureContent()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   for(boolean labels:new boolean[]{false,true}){
    var panel=new BusinessCardsPanel(labels);var field=BusinessCardsPanel.class.getDeclaredField("document");field.setAccessible(true);var p=(publishing.Publication)field.get(panel);p.pages.get(0).objects.clear();
    publishing.PublicationImport.addImage(p,p.pages.get(0),new java.awt.image.BufferedImage(80,40,1),3,4,40,20);
    var before=p.pages.get(0).objects.get(0);String asset=before.asset,bytes=p.assets.get(asset);
    var tf=BusinessCardsPanel.class.getDeclaredField("layerTable");tf.setAccessible(true);var table=(JTable)tf.get(panel);((javax.swing.table.AbstractTableModel)table.getModel()).fireTableDataChanged();table.setRowSelectionInterval(0,0);
    var toolbar=find(panel);assertNotNull(toolbar);assertEquals(40.,toolbar.width.getValue());toolbar.width.setValue(60.);
    var after=panel.output().pages.get(0).objects.get(0);assertEquals(60,after.width);assertEquals(30,after.height);assertEquals(3,after.x);assertEquals(4,after.y);assertEquals(asset,after.asset);assertEquals(bytes,p.assets.get(asset));
    toolbar.locked.doClick();toolbar.height.setValue(25.);assertEquals(60,panel.output().pages.get(0).objects.get(0).width);assertEquals(25,panel.output().pages.get(0).objects.get(0).height);
   }
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
 private ImageFormattingToolbar find(java.awt.Container c){for(var child:c.getComponents()){if(child instanceof ImageFormattingToolbar toolbar)return toolbar;if(child instanceof java.awt.Container nested){var result=find(nested);if(result!=null)return result;}}return null;}
}
