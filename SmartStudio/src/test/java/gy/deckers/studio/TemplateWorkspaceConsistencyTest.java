package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;
class TemplateWorkspaceConsistencyTest {
 @Test void commonTemplateTabsAndLayerCommandsStayReachableAcrossEditors()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   for(JComponent editor:new JComponent[]{new BadgeCardsPanel(),new BusinessCardsPanel(),new BusinessCardsPanel(true),new BusinessCardsPanel(true,false,false,true),new BusinessCardsPanel(false,true),new BusinessCardsPanel(false,false,true)}){
    var ribbon=find(editor,StudioRibbon.class);var tabs=find(ribbon,JTabbedPane.class);
    for(String tab:new String[]{"Home","Insert","Layout","Print Setup","View"}){ribbon.select(tab);assertEquals(tab,tabs.getTitleAt(tabs.getSelectedIndex()),editor.getClass()+" "+tab);}
    editor.setSize(1920,1080);ribbon.setCollapsed(false);
    for(var command:new StudioCommand[]{StudioCommand.CHANGE_TEMPLATE,StudioCommand.UNDO,StudioCommand.REDO,
        StudioCommand.COPY,StudioCommand.PASTE,StudioCommand.DELETE,StudioCommand.FORWARD,StudioCommand.BACKWARD,
        StudioCommand.TEXT,StudioCommand.IMAGE,StudioCommand.RECTANGLE,StudioCommand.ELLIPSE,StudioCommand.LINE,
        StudioCommand.BARCODE,StudioCommand.QR,StudioCommand.ZOOM_OUT,StudioCommand.FIT,StudioCommand.ZOOM_IN}){
     ribbon.select(command.tab);layout(editor);
     var action=button(tabTools(tabs.getSelectedComponent()),command.label);
     assertNotNull(action,command.name());
     assertSame(command,action.getClientProperty(StudioCommand.class));
     assertNotNull(action.getIcon(),command.name());
    }
    for(String tab:new String[]{"Layout","View"}){ribbon.select(tab);assertNotNull(button(tabs.getSelectedComponent(),"Layers"));}
    for(int screen:new int[]{1366,1920})for(double scale:new double[]{1,1.25,1.5}){
     ribbon.select("Layout");ribbon.setSize((int)(screen/scale)-32,240);layout(ribbon);ribbon.setSize(ribbon.getWidth(),ribbon.getPreferredSize().height);layout(ribbon);
     if(editor instanceof BadgeCardsPanel&&screen==1366&&scale==1){try{var image=new java.awt.image.BufferedImage(ribbon.getWidth(),ribbon.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();ribbon.printAll(g);g.dispose();javax.imageio.ImageIO.write(image,"png",java.nio.file.Path.of("target/badge-consistent-layout.png").toFile());}catch(Exception e){throw new RuntimeException(e);}}
     var page=(Container)tabs.getSelectedComponent();var controls=(Container)page.getComponent(0);
     for(Component c:controls.getComponents())assertTrue(c.getY()+c.getHeight()<=controls.getHeight(),editor.getClass()+" layout clips at "+screen+" / "+scale+" control "+c.getBounds()+" container "+controls.getSize());
    }
    assertNotNull(button(ribbon,"Export PDF"));
    var header=button(ribbon,"Save").getParent();assertEquals(java.util.List.of("Save","Print","Export PDF"),java.util.Arrays.stream(header.getComponents()).filter(c->c instanceof JButton).map(c->((JButton)c).getText()).toList());
   }
  });
 }
 private static JComponent tabTools(Component tab){
  try{
   var method=tab.getClass().getDeclaredMethod("dialogContent");
   method.setAccessible(true);
   return (JComponent)method.invoke(tab);
  }catch(ReflectiveOperationException ex){throw new AssertionError(ex);}
 }
 @Test void publicationShapeCommandsUsePortableLayersAndParticipateInUndo()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   try{
    var editor=new BusinessCardsPanel();
    var ribbon=find(editor,StudioRibbon.class);var tabs=find(ribbon,JTabbedPane.class);
    var insert=tabs.getComponentAt(tabs.indexOfTab("Insert"));
    var home=tabs.getComponentAt(tabs.indexOfTab("Home"));
    var field=BusinessCardsPanel.class.getDeclaredField("document");field.setAccessible(true);
    var original=(publishing.Publication)field.get(editor);
    int count=original.pages.get(0).objects.size();
    for(String shape:new String[]{"Rectangle","Ellipse","Line"})button(insert,shape).doClick();
    var reopened=publishing.PublicationFiles.read(publishing.PublicationFiles.archive((publishing.Publication)field.get(editor)));
    assertEquals(java.util.List.of("RECT","ELLIPSE","LINE"),reopened.pages.get(0).objects.stream().skip(count).map(layer->layer.type).toList());
    button(home,"Undo").doClick();
    assertEquals(count+2,((publishing.Publication)field.get(editor)).pages.get(0).objects.size());
   }catch(Exception ex){throw new RuntimeException(ex);}
  });
 }
 @Test void badgeInlineSizeScalesArtworkAndRejectsInvalidDimensionsWithoutChangingTemplate()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var editor=new BadgeCardsPanel();var field=BadgeCardsPanel.class.getDeclaredField("template");field.setAccessible(true);var badge=BadgeTemplate.builtin();field.set(editor,badge);
   double w=badge.widthMm,h=badge.heightMm,x=badge.elements.get(0).x;
   editor.resizeTemplate(w*1.5,h*1.5,true);var resized=(BadgeTemplate)field.get(editor);assertEquals(w*1.5,resized.widthMm);assertEquals(x*1.5,resized.elements.get(0).x,.00001);
   assertThrows(IllegalArgumentException.class,()->editor.resizeTemplate(1,1,true));assertSame(resized,field.get(editor));
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
 private static JButton button(Component c,String text){if(c instanceof JButton b&&text.equals(b.getText()))return b;if(c instanceof Container n)for(Component child:n.getComponents()){var r=button(child,text);if(r!=null)return r;}return null;}
 private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
 private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T r=find(n,type);if(r!=null)return r;}}return null;}
}
