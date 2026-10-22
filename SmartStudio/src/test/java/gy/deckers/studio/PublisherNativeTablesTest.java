package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublisherNativeTablesTest {
    @Test void tinyPreviewConfirmsContinuousRulesWhenIndividualRowsAreTooShort(){
        var p=new Publication();p.width=100;p.height=100;
        for(int r=0;r<20;r++){var e=new Publication.Element();e.sourceTable="tiny";e.sourceRow=r;e.x=10;e.y=10+r*4;e.width=80;e.height=4;p.pages.get(0).objects.add(e);}
        var image=new BufferedImage(50,50,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,50,50);g.setColor(Color.BLACK);g.drawRect(5,5,40,40);g.dispose();
        var table=new PublisherNativeTables.Table(0,7,-40,-40,80,80,new double[]{80},java.util.stream.IntStream.range(0,20).mapToDouble(i->4).toArray());
        assertEquals(1,PublisherNativeTables.restore(p,List.of(table),image));
        assertTrue(p.pages.get(0).objects.stream().anyMatch(e->e.type.equals("LINE")&&e.height>79));
        assertEquals(0,PublisherNativeTables.restore(p,List.of(table),image));
    }
    @Test void antialiasedSourceGridIsNotMistakenForMissingBorders(){
        var image=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);g.setColor(new Color(105,103,109));g.drawLine(20,40,180,40);g.dispose();
        assertTrue(PublisherNativeTables.confirmed(image,100,100,new double[]{10,20,90,20}));
        assertFalse(PublisherNativeTables.confirmed(image,100,100,new double[]{10,80,90,80}));
    }
    @Test void identicalTablesOnSeparatePagesAreEachRestored(){
        var p=new Publication();p.width=100;p.height=100;p.pages.add(new Publication.Page());
        for(int i=0;i<2;i++){var e=new Publication.Element();e.sourceTable="table-"+i;e.x=10;e.y=10;e.width=40;e.height=80;e.text="QTY";p.pages.get(i).objects.add(e);}
        var first=new PublisherNativeTables.Table(0,7,-40,-40,40,40,new double[]{40},new double[]{40});
        var second=new PublisherNativeTables.Table(1,8,-40,-40,40,40,new double[]{40},new double[]{40});
        assertEquals(2,PublisherNativeTables.restore(p,List.of(first,second),null));
        for(var page:p.pages)assertEquals(40,page.objects.get(0).height);
        assertEquals(0,PublisherNativeTables.restore(p,List.of(first,second),null));
    }
    @Test void sourcePreviewLineBandsRestoreWrappedMergedLabels(){
        var image=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,100,100);g.setColor(Color.BLACK);g.fillRect(10,10,20,3);g.fillRect(10,17,20,3);g.dispose();
        assertEquals("6 Inch\nBlocks",PublisherNativeTables.sourceLines(image,100,100,new double[]{10,10,20,10},"6 Inch Blocks"));
    }
    @Test void mergedLabelUsesVisibleSourceInkAndRetainsEditableWording()throws Exception{
        var p=new Publication();p.width=100;p.height=100;
        var e=new Publication.Element();e.sourceTable="table";e.rowSpan=2;e.text="4 Inch Blocks";e.x=10;e.y=10;e.width=40;e.height=60;p.pages.get(0).objects.add(e);
        var image=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);g.setColor(Color.BLACK);g.drawRect(20,20,80,120);g.fillRect(25,80,30,5);g.dispose();
        assertEquals(1,PublisherNativeTables.restore(p,List.of(),image));
        var label=p.pages.get(0).objects.get(1);assertEquals("4 Inch Blocks",label.text);assertEquals(40,label.y,.01);assertTrue(label.sourceTable.isBlank());assertTrue(label.asset.isBlank());assertEquals("",e.text);
        assertEquals(0,PublisherNativeTables.restore(p,List.of(),image));PublicationFiles.read(PublicationFiles.archive(p)).validate();
    }
    @Test void borderOnlyMergedCellDoesNotCreateLettering(){
        var image=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);g.setColor(Color.BLACK);g.drawRect(20,20,80,120);g.dispose();
        var e=new Publication.Element();e.x=10;e.y=10;e.width=40;e.height=60;
        assertNull(PublisherNativeTables.letteringBounds(image,100,100,e));
    }
    @Test void tableGeometryFindsReorderedPageWithoutInventingUnverifiedBorders(){
        var p=new Publication();p.width=100;p.height=100;p.pages.add(new Publication.Page());
        var e=new Publication.Element();e.sourceTable="reordered";e.x=10;e.y=10;e.width=40;e.height=80;e.text="QTY";p.pages.get(1).objects.add(e);
        var table=new PublisherNativeTables.Table(0,7,-40,-40,40,40,new double[]{40},new double[]{40});
        assertEquals(1,PublisherNativeTables.restore(p,List.of(table),null));
        assertEquals(40,e.height);assertTrue(p.pages.get(0).objects.isEmpty());
        assertEquals(1,p.pages.get(1).objects.size());assertEquals("QTY",e.text);
    }
    @Test void restoresSourceSizedGridWithoutFlatteningEditableText()throws Exception{
        var p=new Publication();p.width=100;p.height=100;
        for(int r=0;r<2;r++)for(int c=0;c<2;c++){
            var e=new Publication.Element();e.sourceTable="table";e.sourceRow=r;e.sourceColumn=c;e.cropWidth=.5;e.x=10+c*20;e.y=10+r*40;e.width=20;e.height=40;e.text=r==0?"Header":"";p.pages.get(0).objects.add(e);
        }
        var preview=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);var g=preview.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);g.setColor(Color.BLACK);
        for(int n=0;n<3;n++){g.drawLine(20+n*40,20,20+n*40,100);g.drawLine(20,20+n*40,100,20+n*40);}g.dispose();
        var table=new PublisherNativeTables.Table(0,42,-40,-40,40,40,new double[]{20,20},new double[]{20,20});
        assertEquals(1,PublisherNativeTables.restore(p,List.of(table),preview));
        assertEquals(20,p.pages.get(0).objects.get(0).height);assertEquals("Header",p.pages.get(0).objects.get(0).text);
        assertEquals(0,PublisherNativeTables.restore(p,List.of(table),preview));
        assertEquals(4,PublicationFiles.read(PublicationFiles.archive(p)).pages.get(0).objects.stream().filter(e->e.type.equals("TEXT")).count());
    }
    @Test void whiteReferenceDoesNotInventTableBorders(){
        var image=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,200,200);g.dispose();
        assertFalse(PublisherNativeTables.confirmed(image,100,100,new double[]{10,10,90,10}));
    }
}
