package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class BillBookDocumentsTest {
    @Test void billBookWorkspaceConnectsToItsOwnSharedCache()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel(false,false,true);panel.useSharedLibrary(true);
                var field=BusinessCardsPanel.class.getDeclaredField("shared");field.setAccessible(true);
                var shared=(SharedPublicationLibrary)field.get(panel);
                assertNotNull(shared);assertEquals("bill-books",shared.folder().getFileName().toString());
                panel.stopSharedLibrary();
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    @Test void halfLetterBillFitsLetterAndRemainsEditableAfterSaving()throws Exception{
        var p=BillBookDocuments.create();
        var text=new Publication.Element();text.text="Invoice {{number}}";p.pages.get(0).objects.add(text);
        assertTrue(SheetLayout.pack(p.width,p.height,p.print).capacity()>0);
        var reopened=PublicationFiles.read(PublicationFiles.archive(p));
        reopened.pages.get(0).objects.get(0).text="Receipt {{number}}";
        var saved=PublicationFiles.read(PublicationFiles.archive(reopened));
        assertEquals("Receipt {{number}}",saved.pages.get(0).objects.get(0).text);
        assertEquals(139.7,saved.width);assertEquals(215.9,saved.height);
    }
    @Test void workspaceRestoresOversizedOriginalSheet()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel(false,false,true);
                var settings=new SheetLayout.Settings();settings.width=297;settings.height=420;
                panel.restoreSheetSize(settings);
                var output=panel.output();
                assertEquals(297,output.print.width,.0001);assertEquals(420,output.print.height,.0001);
                assertTrue(SheetLayout.pack(output.width,output.height,output.print).capacity()>0);
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
}
