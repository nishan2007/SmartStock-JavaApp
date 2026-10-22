package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import publishing.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BusinessCardsTest {
    @TempDir Path folder;
    @Test void undoShortcutRestoresDesignAndRedoRestoresEditInCardsAndLabels()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            for(boolean labels:List.of(false,true)){
                var panel=new BusinessCardsPanel(labels);double original=panel.output().width;
                panel.resizeTemplate(original+10,40,false);assertEquals(original+10,panel.output().width,.001);
                Object undo=panel.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(javax.swing.KeyStroke.getKeyStroke("control Z"));assertNotNull(undo);
                panel.getActionMap().get(undo).actionPerformed(new java.awt.event.ActionEvent(panel,0,"undo"));assertEquals(original,panel.output().width,.001);
                panel.historyAction(true);assertEquals(original+10,panel.output().width,.001);
                panel.historyAction(false);panel.resizeTemplate(original+5,40,false);panel.historyAction(true);assertEquals(original+5,panel.output().width,.001);
            }
        });
    }
    @Test void everyTemplateCategoryDefaultsToLetterDespiteImportedA4Sheet()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            for(boolean labels:List.of(false,true)){
                var panel=new BusinessCardsPanel(labels);var imported=new SheetLayout.Settings();imported.width=210;imported.height=297;
                panel.restoreSheetSize(imported);var output=panel.output();assertEquals(215.9,output.print.width,.00001);assertEquals(279.4,output.print.height,.00001);
                if(!labels)assertEquals(12,SheetLayout.pack(output.width,output.height,output.print).capacity());
            }
        });
    }
    @Test void complexPublisherPagesKeepCompleteArtworkExactlyOnce()throws Exception{
        Publication p=BusinessCardsPanel.builtin();p.pages.clear();
        for(int i=0;i<2;i++){
            var editable=new Publication.Page();editable.name="Card "+i;
            PublicationImport.addImage(p,editable,new java.awt.image.BufferedImage(4,4,1),0,0,10,10);
            p.pages.add(editable);
            var reference=new Publication.Page();reference.reference=true;
            PublicationImport.addImage(p,reference,new java.awt.image.BufferedImage(8,8,1),0,0,p.width,p.height);
            p.pages.add(reference);
        }
        BusinessCardsPanel.preserveConvertedArtwork(p);p.validate();
        assertEquals(2,p.pages.size());assertEquals(2,p.assets.size());
        assertFalse(p.pages.stream().anyMatch(pg->pg.reference));
        assertEquals("Card 1",p.pages.get(1).name);
        assertEquals(88.9,p.pages.get(0).objects.get(0).width);
        var restored=PublicationFiles.read(PublicationFiles.archive(p));
        assertEquals(2,restored.pages.size());
        assertNotNull(new PublicationRenderer(restored).raster(0,72));
    }
    @Test void sheetSizesAndCompletePreviewKeepPhysicalDimensions()throws Exception{
        assertArrayEquals(new double[]{215.9,279.4},BusinessCardPaper.size("Letter",1,1),.00001);
        assertArrayEquals(new double[]{215.9,355.6},BusinessCardPaper.size("Legal",1,1),.00001);
        assertArrayEquals(new double[]{279.4,431.8},BusinessCardPaper.size("11 × 17",1,1),.00001);
        assertArrayEquals(new double[]{304.8,457.2},BusinessCardPaper.size("Custom",12,18),.00001);
        assertThrows(IllegalArgumentException.class,()->BusinessCardPaper.size("Custom",0,18));
        for(String name:List.of("Letter","Legal","11 × 17","Custom")){
            var size=BusinessCardPaper.size(name,12,18);Publication p=BusinessCardsPanel.builtin();
            p.print.width=size[0];p.print.height=size[1];p.print.left=p.print.right=p.print.top=p.print.bottom=6.35;
            p.print.quantity=1;p.pages.get(0).objects.forEach(e->e.text="Sample");
            var image=new PublicationRenderer(p).raster(0,72);
            for(var viewport:List.of(new java.awt.Dimension(280,170),new java.awt.Dimension(700,600))){
                var bounds=SheetPreview.fit(viewport.width,viewport.height,image.getWidth(),image.getHeight());
                assertTrue(bounds.x>=0&&bounds.y>=0);assertTrue(bounds.x+bounds.width<=viewport.width);assertTrue(bounds.y+bounds.height<=viewport.height);
                assertEquals(image.getWidth()/(double)image.getHeight(),bounds.width/(double)bounds.height,.02);
            }
            Path pdf=folder.resolve(name.replace(' ','_')+".pdf");PublicationFiles.pdf(p,pdf,0,0);
            try(var exported=org.apache.pdfbox.pdmodel.PDDocument.load(pdf.toFile())){
                assertEquals(size[0]*72/25.4,exported.getPage(0).getMediaBox().getWidth(),.01);
                assertEquals(size[1]*72/25.4,exported.getPage(0).getMediaBox().getHeight(),.01);
            }
        }
    }
    @Test void numberSeriesSupportsStepsPaddingAndDecimals(){
        assertEquals(List.of("0010","0015","0020"),BusinessCardSeries.values("10","5",3,4));
        assertEquals(List.of("2","1.5","1"),BusinessCardSeries.values("2","-0.5",3,0));
        assertEquals(List.of("7","7"),BusinessCardSeries.values("7","0",2,0));
        assertThrows(IllegalArgumentException.class,()->BusinessCardSeries.values("1","1",10001,0));
        assertThrows(IllegalArgumentException.class,()->BusinessCardSeries.values("1e9999","1",1,0));
    }
    @Test void renderTabAtDesktopSize()throws Exception{
        javax.swing.SwingUtilities.invokeAndWait(()->{
            BusinessCardsPanel panel=new BusinessCardsPanel();
            click(panel,"Page number field");click(panel,"Add page");
            javax.swing.JTable merge=findMerge(panel);
            var model=(javax.swing.table.DefaultTableModel)merge.getModel();
            assertEquals("page1_number",model.getColumnName(6));assertEquals("page2_number",model.getColumnName(7));
            model.addRow(new Object[]{"Alice","Deckers","Staff","5551234","alice@example.com","1","001","1001"});
            model.addRow(new Object[]{"Bob","Deckers","Staff","5555678","bob@example.com","1","002","1002"});
            choosePaper(panel,"11 × 17");assertEquals(279.4,panel.output().print.width,.0001);assertEquals(431.8,panel.output().print.height,.0001);
            choosePaper(panel,"Letter");
            findPreview(panel).setImage(new PublicationRenderer(panel.output()).raster(0,72));
            panel.setSize(1000,610);panel.addNotify();layout(panel);
            var image=new java.awt.image.BufferedImage(1000,610,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();panel.printAll(g);g.dispose();
            try{javax.imageio.ImageIO.write(image,"png",Path.of("target","business-cards-preview.png").toFile());}
            catch(java.io.IOException ex){throw new RuntimeException(ex);}
            panel.removeNotify();
        });
    }
    private static void click(java.awt.Container c,String name){for(var child:c.getComponents()){if(child instanceof javax.swing.JButton button && button.getText().equals(name)){button.doClick();return;}if(child instanceof javax.swing.JComponent component && component.getComponentPopupMenu()!=null)click(component.getComponentPopupMenu(),name);if(child instanceof java.awt.Container nested)click(nested,name);}}
    private static javax.swing.JTable findMerge(java.awt.Container c){for(var child:c.getComponents()){if(child instanceof javax.swing.JTable table && table.getColumnName(0).equals("name"))return table;if(child instanceof java.awt.Container nested){var found=findMerge(nested);if(found!=null)return found;}}return null;}
    private static void choosePaper(java.awt.Container c,String name){for(var child:c.getComponents()){if(child instanceof javax.swing.JComboBox<?> combo && combo.getItemCount()==5 && combo.getItemAt(0).equals("Letter")){combo.setSelectedItem(name);return;}if(child instanceof java.awt.Container nested)choosePaper(nested,name);}}
    private static SheetPreview findPreview(java.awt.Container c){for(var child:c.getComponents()){if(child instanceof SheetPreview preview)return preview;if(child instanceof java.awt.Container nested){var found=findPreview(nested);if(found!=null)return found;}}return null;}
    private static void layout(java.awt.Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof java.awt.Container nested)layout(nested);}
    @Test void exactSizeAndLetterPackingWithMergeAcrossSheets() {
        Publication p=BusinessCardsPanel.builtin();
        assertEquals(88.9,p.width);assertEquals(50.8,p.height);
        p.print.width=215.9;p.print.height=279.4;
        p.print.left=p.print.right=p.print.top=p.print.bottom=6.35;
        p.print.gapX=p.print.gapY=0;
        var layout=SheetLayout.pack(p.width,p.height,p.print);
        assertEquals(12,layout.capacity());
        for(var place:layout.placements()) {
            assertTrue(place.x()>=6.35-1e-8);assertTrue(place.y()>=6.35-1e-8);
            assertTrue(place.x()+place.width()<=209.55+1e-8);
            assertTrue(place.y()+place.height()<=273.05+1e-8);
        }
        p.mergeRows=List.of(Map.of("name","One","quantity","12"),Map.of("name","Two","quantity","1"));
        assertEquals(2,new PublicationRenderer(p).sheets());
        assertNotNull(new PublicationRenderer(p).raster(1,72));
    }
    @Test void excelKeepsFormattedPhoneAndReloadsEdits() throws Exception {
        Path file=folder.resolve("cards.xlsx");
        try(var book=new XSSFWorkbook()) {
            var sheet=book.createSheet("Staff");var heading=sheet.createRow(0);
            heading.createCell(0).setCellValue("name");heading.createCell(1).setCellValue("phone");
            var row=sheet.createRow(1);row.createCell(0).setCellValue("Alice");
            var phone=row.createCell(1);phone.setCellValue(1234);
            var style=book.createCellStyle();style.setDataFormat(book.createDataFormat().getFormat("000000"));phone.setCellStyle(style);
            try(var out=Files.newOutputStream(file)){book.write(out);}
            assertEquals("001234",BusinessCardMerge.read(file,"Staff").rows().get(0).get("phone"));
            row.getCell(0).setCellValue("Bob");try(var out=Files.newOutputStream(file)){book.write(out);}
        }
        assertEquals("Bob",BusinessCardMerge.read(file,"Staff").rows().get(0).get("name"));
        assertThrows(java.io.IOException.class,()->BusinessCardMerge.read(file,"Missing"));
    }
    @Test void duplicateHeadersAreRejected()throws Exception{
        Path file=folder.resolve("bad.xlsx");try(var book=new XSSFWorkbook()){
            var row=book.createSheet("Staff").createRow(0);row.createCell(0).setCellValue("name");row.createCell(1).setCellValue("name");
            try(var out=Files.newOutputStream(file)){book.write(out);}
        }
        assertThrows(java.io.IOException.class,()->BusinessCardMerge.read(file,"Staff"));
    }
}
