package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class FontControlsRegressionTest {
    @Test void homeAndFormatFontControlsEditSelectedImportedTextAndPersist()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel();Publication document=(Publication)field(panel,"document");
                var text=document.pages.get(0).objects.get(0);text.text="Nishan sample";text.height=30;text.font="SansSerif";text.fontSize=10.504;text.fitText=true;
                JTable layers=(JTable)field(panel,"layerTable");layers.setRowSelectionInterval(0,0);
                var toolbar=(TextFormattingToolbar)field(panel,"textFormatting");
                JSpinner quickSize=(JSpinner)field(toolbar,"quickSize");
                quickSize.setValue(28.);assertEquals(28,panel.output().pages.get(0).objects.get(0).fontSize);assertFalse(panel.output().pages.get(0).objects.get(0).fitText);
                var input=((JSpinner.DefaultEditor)quickSize.getEditor()).getTextField();input.setText("31.5");quickSize.commitEdit();assertEquals(31.5,panel.output().pages.get(0).objects.get(0).fontSize);
                var before=PublicationTemplateLibrary.preview(panel.output(),0);int previousPixels=java.util.Arrays.hashCode(before.getRGB(0,0,before.getWidth(),before.getHeight(),null,0,before.getWidth()));
                text.fitText=true;
                JComboBox<?> quickFont=(JComboBox<?>)field(toolbar,"quickFont");quickFont.setSelectedItem("Serif");
                assertEquals("Serif",PublicationFonts.fontFamily(panel.output().pages.get(0).objects.get(0).font));
                assertFalse(panel.output().pages.get(0).objects.get(0).fitText);
                var after=PublicationTemplateLibrary.preview(panel.output(),0);assertNotEquals(previousPixels,java.util.Arrays.hashCode(after.getRGB(0,0,after.getWidth(),after.getHeight(),null,0,after.getWidth())));
                toolbar.size.setValue(36.);assertEquals(36,panel.output().pages.get(0).objects.get(0).fontSize);
                toolbar.fonts.setSelectedItem("SansSerif");assertEquals("SansSerif",PublicationFonts.fontFamily(panel.output().pages.get(0).objects.get(0).font));
                assertTrue(toolbar.styles.getItemCount()>0);
                for(int i=0;i<toolbar.styles.getItemCount();i++){
                    var face=toolbar.styles.getItemAt(i);assertTrue(PublicationFonts.available(face.name()));
                    if(face.bold()){
                        toolbar.styles.setSelectedItem(face);var saved=PublicationFiles.read(PublicationFiles.archive(panel.output())).pages.get(0).objects.get(0);
                        assertEquals(face.name(),saved.font);assertTrue(saved.bold);assertEquals(36,saved.fontSize);break;
                    }
                }
                panel.historyAction(false);panel.historyAction(true);assertEquals(36,panel.output().pages.get(0).objects.get(0).fontSize);
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    @Test void fontDropdownsStayOpenAndKeepTheirScrollPositionDuringRefresh()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(()->{
            JFrame frame=new JFrame("Font dropdown regression");
            var selected=new java.util.concurrent.atomic.AtomicReference<>(new TextFormattingToolbar.Style("Serif",12,false,false,false,false,"#000000",false,"#000000",.2,null));
            var toolbar=new TextFormattingToolbar(selected::get,selected::set,()->publishing.TextAlignment.LEFT);
            frame.add(toolbar,BorderLayout.CENTER);frame.add(toolbar.quickControls(),BorderLayout.SOUTH);
            frame.setSize(900,280);frame.setLocationRelativeTo(null);frame.setVisible(true);
            try{
                JComboBox<?> quick=(JComboBox<?>)field(toolbar,"quickFont");
                for(JComboBox<?> combo:new JComboBox<?>[]{toolbar.fonts,quick,toolbar.styles}){
                    var model=combo.getModel();combo.setPopupVisible(true);
                    assertTrue(combo.isPopupVisible(),"Dropdown must open");
                    var popup=(javax.swing.plaf.basic.ComboPopup)combo.getUI().getAccessibleChild(combo,0);
                    JList<?> list=popup.getList();
                    if(list.getModel().getSize()>18)list.ensureIndexIsVisible(18);
                    int first=list.getFirstVisibleIndex();
                    toolbar.refresh();toolbar.refresh();
                    assertTrue(combo.isPopupVisible(),"Selection refresh must not close the list");
                    assertSame(model,combo.getModel(),"Opening and refreshing must preserve the font list model");
                    assertEquals(first,list.getFirstVisibleIndex(),"Browsing position must be preserved");
                    combo.setPopupVisible(false);
                }
                toolbar.fonts.setSelectedItem("SansSerif");assertEquals("SansSerif",PublicationFonts.fontFamily(selected.get().font()));
            }catch(Exception ex){throw new RuntimeException(ex);}finally{frame.dispose();}
        });
    }
    private static Object field(Object target,String name)throws Exception{var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
}
