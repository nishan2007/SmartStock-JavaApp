package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class TemplatePrintSetupTest {
    @Test void templateArchiveRestoresIndependentSetupForCardsLabelsAndBillBooks()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            for(int mode=0;mode<3;mode++){
                BusinessCardsPanel first=new BusinessCardsPanel(mode==1,false,mode==2);
                BusinessCardsPanel reopened=new BusinessCardsPanel(mode==1,false,mode==2);
                try{
                    SheetLayout.Settings a=new SheetLayout.Settings();a.width=304.8;a.height=457.2;
                    a.left=11;a.right=12;a.top=13;a.bottom=14;a.gapX=3;a.gapY=4;
                    a.quantity=7;a.rotate=false;a.cropMarks=true;a.mixPages=true;
                    first.restoreSheetSize(a);
                    Publication saved=PublicationFiles.read(PublicationFiles.archive(first.output()));
                    reopened.restoreSheetSize(saved.print);
                    var actual=reopened.output().print;
                    assertEquals(a.width,actual.width,.001);assertEquals(a.height,actual.height,.001);
                    assertEquals(11,actual.left,.000001);assertEquals(12,actual.right,.000001);assertEquals(13,actual.top,.000001);assertEquals(14,actual.bottom,.000001);
                    assertEquals(3,actual.gapX,.000001);assertEquals(4,actual.gapY,.000001);assertEquals(7,actual.quantity);
                    assertFalse(actual.rotate);assertTrue(actual.cropMarks);assertTrue(actual.mixPages);
                    SheetLayout.Settings b=new SheetLayout.Settings();b.width=215.9;b.height=279.4;b.quantity=1;
                    reopened.restoreSheetSize(b);assertEquals(5,reopened.output().print.left,.000001);assertEquals(2,reopened.output().print.gapY,.000001);
                    reopened.restoreSheetSize(saved.print);assertEquals(11,reopened.output().print.left,.000001);
                    JTabbedPane tabs=find(first,JTabbedPane.class);assertNotNull(tabs);
                    boolean print=false;for(int i=0;i<tabs.getTabCount();i++)print|=tabs.getTitleAt(i).equals("Print Setup");assertTrue(print);
                }catch(Exception e){throw new RuntimeException(e);}
                finally{stop(first);stop(reopened);}
            }
        });
    }
    private static void stop(Object panel){for(String name:new String[]{"refresh","thumbnailRefresh"})try{var f=panel.getClass().getDeclaredField(name);f.setAccessible(true);((Timer)f.get(panel)).stop();}catch(Exception e){throw new RuntimeException(e);}}
    private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T result=find(n,type);if(result!=null)return result;}}return null;}
}
