package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import publishing.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationTableArtworkRepairTest {
    @TempDir Path folder;
    @Test void detectsHeadingFragmentBelowNativeRowWithoutFlaggingCompleteWording()throws Exception{
        var p=design();var e=p.pages.get(0).objects.get(0);e.text="AMOUNT";e.height=5;
        Path file=reference(false);double pt=72/25.4;
        try(var doc=PDDocument.load(file.toFile())){
            try(var stream=new PDPageContentStream(doc,doc.getPage(0),PDPageContentStream.AppendMode.APPEND,true)){
                stream.beginText();stream.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA,10);stream.newLineAtOffset((float)(6*pt),(float)((50-8)*pt));stream.showText("AMOUN");stream.newLineAtOffset(0,(float)(-6*pt));stream.showText("T");stream.endText();
            }doc.save(file.toFile());
        }
        assertEquals(java.util.Set.of(e.id),PublicationTableArtworkRepair.clippedHeadings(p,file));
        e.text="AMOUN";assertTrue(PublicationTableArtworkRepair.clippedHeadings(p,file).isEmpty());
    }
    @Test void nativeCropsFollowRestoredCellGeometryAndAreIdempotent()throws Exception{
        var p=design();var e=p.pages.get(0).objects.get(0);e.height=10;e.cropHeight=.1;
        var blank=Publication.JSON.fromJson(Publication.JSON.toJson(e),Publication.Element.class);blank.id=java.util.UUID.randomUUID().toString();blank.text="";blank.y=15;blank.asset="";p.pages.get(0).objects.add(blank);
        var line=new Publication.Element();line.type="LINE";line.text="Native Publisher table 1";line.x=5;line.y=5;line.width=40;line.height=.001;p.pages.get(0).objects.add(line);
        assertEquals(1,PublicationTableArtworkRepair.refreshNativeCrops(p,reference(false)));
        assertEquals(.5,e.cropHeight,.00001);assertTrue(e.asset.startsWith("native-table-"));assertEquals("",blank.asset);
        assertEquals(0,PublicationTableArtworkRepair.refreshNativeCrops(p,reference(false)));
        PublicationFiles.read(PublicationFiles.archive(p)).validate();
    }
    @Test void smallBlackFragmentsAreRecoveredWithoutDeletingGenuineDarkArtwork()throws Exception{
        var p=design();var e=p.pages.get(0).objects.get(0);e.sourceTable="";e.type="IMAGE";e.width=14.8;e.height=2.7;
        assertEquals(1,PublicationTableArtworkRepair.repair(p,reference(false)));
        assertTrue(PublicationTableArtworkRepair.blackFraction(PublicationImport.decodeImage(p.assetBytes(e.asset)))<.1);
        var genuine=design();var dark=genuine.pages.get(0).objects.get(0);dark.sourceTable="";dark.type="IMAGE";dark.width=14.8;dark.height=2.7;
        assertEquals(0,PublicationTableArtworkRepair.repair(genuine,reference(true)));
    }
    @Test void damagedTableRasterIsReplacedWithoutLosingEditableCells()throws Exception{
        var p=design();Path pdf=reference(false);
        assertEquals(1,PublicationTableArtworkRepair.repair(p,pdf));
        var reopened=PublicationFiles.read(PublicationFiles.archive(p));var e=reopened.pages.get(0).objects.get(0);
        assertEquals("TEXT",e.type);assertEquals("QTY",e.text);assertEquals("table-1",e.sourceTable);
        assertTrue(PublicationTableArtworkRepair.blackFraction(PublicationImport.decodeImage(reopened.assetBytes(e.asset)))<.1);
        e.text="QUANTITY";assertEquals("QUANTITY",PublicationFiles.read(PublicationFiles.archive(reopened)).pages.get(0).objects.get(0).text);
    }
    @Test void genuinelyBlackSourceTableIsRetained()throws Exception{
        var p=design();String before=p.assets.values().iterator().next();
        assertEquals(0,PublicationTableArtworkRepair.repair(p,reference(true)));
        assertEquals(before,p.assets.values().iterator().next());
    }
    @Test void missingTableArtworkRecoversSourceAppearanceInsteadOfDefaultGreen()throws Exception{
        var p=design();var e=p.pages.get(0).objects.get(0);e.asset="";e.fill="#18a303";
        assertEquals(1,PublicationTableArtworkRepair.repair(p,reference(false)));
        assertFalse(e.asset.isBlank());assertEquals("#ffffff",e.fill);
        assertEquals(0,PublicationTableArtworkRepair.repair(p,reference(false)));
    }
    @Test void numberingNamesMatchVariantsWithoutMatchingCustomerNames(){
        assertTrue(BillBookDocuments.numberingName("A.K.B - NUMBERING"));
        assertTrue(BillBookDocuments.numberingName("NUM2.pub.sspub"));
        assertFalse(BillBookDocuments.numberingName("Numan Enterprise"));
        assertFalse(BillBookDocuments.numberingName("Number One Hardware"));
    }
    private Publication design()throws Exception{
        var p=new Publication();p.width=90;p.height=50;
        var black=new java.awt.image.BufferedImage(200,100,java.awt.image.BufferedImage.TYPE_INT_RGB);black.setRGB(10,10,0xffffff00);
        PublicationImport.addImage(p,p.pages.get(0),black,5,5,40,20);
        var e=p.pages.get(0).objects.get(0);e.type="TEXT";e.text=e.originalText="QTY";e.sourceTable="table-1";e.fitText=true;
        return p;
    }
    private Path reference(boolean black)throws Exception{
        Path file=folder.resolve(black?"black.pdf":"white.pdf");
        try(var doc=new PDDocument()){
            var page=new PDPage(new PDRectangle((float)(90*72/25.4),(float)(50*72/25.4)));doc.addPage(page);
            if(black)try(var stream=new PDPageContentStream(doc,page)){stream.setNonStrokingColor(java.awt.Color.BLACK);stream.addRect(0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());stream.fill();}
            doc.save(file.toFile());
        }
        return file;
    }
}
