package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.nio.file.Path;
import java.awt.event.ActionEvent;
import static org.junit.jupiter.api.Assertions.*;
class BadgeFloatingLayersTest {
 @TempDir Path temp;
 @Test void badgeUsesSharedLayersWithVisibilityHistoryAndSavedRendering()throws Exception{
  var template=new BadgeTemplate();var e=new BadgeTemplate.Element();e.type="RECT";e.label="Background";e.x=5;e.y=5;e.width=20;e.height=20;template.elements.add(e);
  Path file=temp.resolve("badge.sstbadge");template.save(file);
  SwingUtilities.invokeAndWait(()->{try{
   var editor=new BadgeCardsPanel();var load=BadgeCardsPanel.class.getDeclaredMethod("loadTemplate",Path.class);load.setAccessible(true);load.invoke(editor,file);
   var field=BadgeCardsPanel.class.getDeclaredField("template");field.setAccessible(true);
   var panel=editor.createLayersPanel(()->{});assertEquals("Background",panel.table.getValueAt(0,2));panel.table.setRowSelectionInterval(0,0);
   panel.table.getActionMap().get("toggleLayer").actionPerformed(new ActionEvent(panel,0,"space"));
   var current=(BadgeTemplate)field.get(editor);assertTrue(current.elements.get(0).hidden);
   current.save(file);var saved=BadgeTemplate.load(file);assertTrue(saved.elements.get(0).hidden);assertEquals(0xffffff,saved.render(java.util.Map.of(),null,254).getRGB(100,100)&0xffffff);
   var history=BadgeCardsPanel.class.getDeclaredMethod("historyAction",boolean.class);history.setAccessible(true);history.invoke(editor,false);assertFalse(((BadgeTemplate)field.get(editor)).elements.get(0).hidden);
   history.invoke(editor,true);assertTrue(((BadgeTemplate)field.get(editor)).elements.get(0).hidden);
   editor.toggleLayerVisibility(0);assertFalse(((BadgeTemplate)field.get(editor)).elements.get(0).hidden);assertNotEquals(0xffffff,((BadgeTemplate)field.get(editor)).render(java.util.Map.of(),null,254).getRGB(100,100)&0xffffff);
   var tabs=find(editor,JTabbedPane.class);if(tabs!=null)for(int i=0;i<tabs.getTabCount();i++)assertNotEquals("Layers",tabs.getTitleAt(i));
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
 private static <T>T find(java.awt.Container c,Class<T> type){for(var child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof java.awt.Container n){T r=find(n,type);if(r!=null)return r;}}return null;}
}
