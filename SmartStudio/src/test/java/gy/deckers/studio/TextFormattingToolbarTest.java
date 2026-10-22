package gy.deckers.studio;
import publishing.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
class TextFormattingToolbarTest {
 Object field(Object owner,String name)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
 TextFormattingToolbar.Style style(){return new TextFormattingToolbar.Style("Serif",20,true,true,true,true,"#ff0000",true,"#0000ff",.3);}
 long pixels(java.awt.image.BufferedImage image){java.util.zip.CRC32 crc=new java.util.zip.CRC32();for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int pixel=image.getRGB(x,y);crc.update(pixel);crc.update(pixel>>>8);crc.update(pixel>>>16);}return crc.getValue();}
 @Test void underlineAndOutlineChangeRenderedPixelsForNormalFittedAndWordArtText(){
  for(String mode:new String[]{"normal","fitted","wordart"}){Publication p=new Publication();p.width=70;p.height=35;var e=p.pages.get(0).objects;Publication.Element text=new Publication.Element();text.text="Example";text.x=2;text.y=2;text.width=60;text.height=25;text.fontSize=24;text.fitText=mode.equals("fitted");if(mode.equals("wordart"))text.wordArt="PLAIN";e.add(text);long plain=pixels(PublicationTemplateLibrary.preview(p,0));text.underline=true;assertNotEquals(plain,pixels(PublicationTemplateLibrary.preview(p,0)),mode);text.underline=false;text.textOutline=true;text.textOutlineColor="#ff0000";text.textOutlineWidth=.5;if(mode.equals("wordart")){text.wordArtOutline="#ff0000";text.wordArtOutlineWidth=.5;}assertNotEquals(plain,pixels(PublicationTemplateLibrary.preview(p,0)),mode);}
 }
 @Test void controlsFollowSelectionAndDisableWithoutText()throws Exception{
  SwingUtilities.invokeAndWait(()->{AtomicReference<TextFormattingToolbar.Style> selection=new AtomicReference<>();var toolbar=new TextFormattingToolbar(selection::get,selection::set);assertFalse(toolbar.bold.isEnabled());selection.set(style());toolbar.refresh();assertTrue(toolbar.bold.isSelected());assertEquals(20.,toolbar.size.getValue());toolbar.bold.doClick();assertFalse(selection.get().bold());toolbar.size.setValue(22.);assertEquals(22,selection.get().size());selection.set(null);toolbar.refresh();assertFalse(toolbar.fonts.isEnabled());});
 }
 @Test void cardAndLabelStylesPersistRenderAndUndo()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{for(boolean labels:new boolean[]{false,true}){
   var panel=new BusinessCardsPanel(labels);Publication document=(Publication)field(panel,"document");if(labels){var element=new Publication.Element();element.width=40;element.height=20;document.pages.get(0).objects.add(element);var changed=BusinessCardsPanel.class.getDeclaredMethod("changed");changed.setAccessible(true);changed.invoke(panel);}
   JTable table=(JTable)field(panel,"layerTable");table.setRowSelectionInterval(0,0);var element=document.pages.get(0).objects.get(0);double original=element.fontSize;
   var apply=BusinessCardsPanel.class.getDeclaredMethod("applyTextStyle",TextFormattingToolbar.Style.class);apply.setAccessible(true);apply.invoke(panel,style());assertEquals(0,table.getSelectedRow());var saved=PublicationFiles.read(PublicationFiles.archive((Publication)field(panel,"document"))).pages.get(0).objects.get(0);assertTrue(saved.bold&&saved.italic&&saved.underline&&saved.strike&&saved.textOutline);assertEquals("#0000ff",saved.textOutlineColor);assertEquals(20,saved.fontSize);assertNotNull(PublicationTemplateLibrary.preview((Publication)field(panel,"document"),0));panel.historyAction(false);assertEquals(original,((Publication)field(panel,"document")).pages.get(0).objects.get(0).fontSize);panel.historyAction(true);assertEquals(20,((Publication)field(panel,"document")).pages.get(0).objects.get(0).fontSize);
  }}catch(Exception ex){throw new RuntimeException(ex);}});
 }
 @Test void badgeUsesPointsAndStylesRoundTripAndRender()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{var panel=new BadgeCardsPanel();JTable table=(JTable)field(panel,"table");BadgeTemplate badge=(BadgeTemplate)field(panel,"template");int index=0;while(!badge.elements.get(index).type.equals("TEXT"))index++;table.setRowSelectionInterval(index,index);var apply=BadgeCardsPanel.class.getDeclaredMethod("applyTextStyle",TextFormattingToolbar.Style.class);apply.setAccessible(true);apply.invoke(panel,style());assertEquals(index,table.getSelectedRow());var element=badge.snapshot().elements.get(index);assertEquals(20*25.4/72,element.fontMm,.00001);assertTrue(element.bold&&element.italic&&element.underline&&element.strike&&element.textOutline);assertNotNull(badge.render(java.util.Map.of(),null,100));}catch(Exception ex){throw new RuntimeException(ex);}});
 }
}
