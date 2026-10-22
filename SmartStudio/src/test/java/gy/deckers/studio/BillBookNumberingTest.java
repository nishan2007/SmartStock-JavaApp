package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BillBookNumberingTest {
    @Test void billBookOutputModeDoesNotStripSavedTemplate()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel(false,false,true);
                var df=BusinessCardsPanel.class.getDeclaredField("document");df.setAccessible(true);var p=(Publication)df.get(panel);
                var e=new Publication.Element();e.text="{{number}}";p.pages.get(0).objects.add(e);
                var logo=new Publication.Element();logo.type="RECT";p.pages.get(0).objects.add(logo);
                var rf=BusinessCardsPanel.class.getDeclaredField("records");rf.setAccessible(true);
                var rows=(javax.swing.table.DefaultTableModel)rf.get(panel);rows.setDataVector(new Object[][]{{"0001"},{"0002"}},new Object[]{"number"});
                var mf=BusinessCardsPanel.class.getDeclaredField("billBookOutput");mf.setAccessible(true);var mode=(javax.swing.JComboBox<?>)mf.get(panel);
                assertEquals(2,panel.printOutput().pages.get(0).objects.size());mode.setSelectedIndex(1);
                assertEquals(1,panel.printOutput().pages.get(0).objects.size());assertEquals(2,panel.output().pages.get(0).objects.size());
                mode.setSelectedIndex(2);var template=panel.printOutput();
                assertEquals("",template.pages.get(0).objects.get(0).text);assertEquals("RECT",template.pages.get(0).objects.get(1).type);
                assertTrue(template.mergeRows.isEmpty());assertEquals("{{number}}",panel.output().pages.get(0).objects.get(0).text);
                mode.setSelectedIndex(0);assertEquals(2,panel.printOutput().pages.get(0).objects.size());
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    @Test void overlayKeepsNumbersAndGeometryButNoArtworkOrOtherMergeFields()throws Exception{
        var p=new Publication();var page=p.pages.get(0);
        var artwork=new Publication.Element();artwork.type="RECT";page.objects.add(artwork);
        var customer=new Publication.Element();customer.text="{{customer}}";page.objects.add(customer);
        var number=new Publication.Element();number.text="No. {{number}}";number.x=23;number.y=14;number.fontSize=16;number.rotation=90;number.color="#ff0000";page.objects.add(number);
        var master=new Publication.Page();master.name="Letterhead";master.objects.add(artwork);p.masters.add(master);page.master=master.name;
        p.mergeRows.add(Map.of("number","0009"));p.mergeRows.add(Map.of("number","0010"));p.print.cropMarks=true;
        var out=BillBookNumbering.numbersOnly(p,"number");
        assertEquals(1,out.pages.get(0).objects.size());assertTrue(out.masters.get(0).objects.isEmpty());
        var e=out.pages.get(0).objects.get(0);assertEquals("{{number}}",e.text);assertEquals(23,e.x);assertEquals(14,e.y);assertEquals(16,e.fontSize);assertEquals(90,e.rotation);assertEquals("#ff0000",e.color);
        assertEquals(p.mergeRows,out.mergeRows);assertEquals(new PublicationRenderer(p).sheets(),new PublicationRenderer(out).sheets());assertFalse(out.print.cropMarks);
        assertEquals(3,p.pages.get(0).objects.size());assertEquals("No. {{number}}",number.text);assertTrue(p.print.cropMarks);
        PublicationFiles.read(PublicationFiles.archive(out)).validate();
    }
    @Test void templateOnlyKeepsArtworkOtherFieldsAndMasterStories()throws Exception{
        var source=new Publication();
        var text=new Publication.Element();text.text="No. {{number}} / {{customer}}";text.x=12;source.pages.get(0).objects.add(text);
        var artwork=new Publication.Element();artwork.type="RECT";source.pages.get(0).objects.add(artwork);
        var master=new Publication.Page();master.name="Header";source.masters.add(master);source.pages.get(0).master=master.name;
        var story=new Publication.Element();story.story="serial";story.text="{{page1_number}}";master.objects.add(story);
        source.stories.put("serial","Serial {{page1_number}}");
        source.mergeRows.add(Map.of("number","001","page1_number","002","customer","Alice $5"));
        String before=Publication.JSON.toJson(source);
        var result=BillBookNumbering.templateOnly(source,"number, page1_number");
        assertEquals("No.  / {{customer}}",result.pages.get(0).objects.get(0).text);
        assertEquals(12,result.pages.get(0).objects.get(0).x);assertEquals("RECT",result.pages.get(0).objects.get(1).type);
        assertEquals("Serial ",result.stories.get("serial"));assertEquals(Map.of("customer","Alice $5"),result.mergeRows.get(0));
        PublicationPreflight.requireMergeData(result);PublicationFiles.read(PublicationFiles.archive(result)).validate();
        assertEquals(before,Publication.JSON.toJson(source));
    }
    @Test void missingFieldOrIncompleteSeriesStopsOutput(){
        var p=new Publication();var e=new Publication.Element();e.text="{{page1_number}}";p.pages.get(0).objects.add(e);
        assertThrows(IllegalArgumentException.class,()->BillBookNumbering.numbersOnly(p,"number"));
        assertThrows(IllegalArgumentException.class,()->BillBookNumbering.numbersOnly(p,"page1_number"));
        p.mergeRows.add(Map.of("page1_number","001"));p.mergeRows.add(Map.of("page1_number",""));
        assertThrows(IllegalArgumentException.class,()->BillBookNumbering.numbersOnly(p,"page1_number"));
    }
}
