package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.Publication;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import static org.junit.jupiter.api.Assertions.*;

class BannerPrintingTest {
    @Test void whole44By72InchDesignSurvivesDeviceScalingAndBandClips() throws Exception {
        Publication p=BannerDocuments.create(44,72,"IN");
        for(double x:new double[]{25.4,p.width-50.8})
            for(double y:new double[]{25.4,p.height-50.8}) {
                Publication.Element box=new Publication.Element();box.type="RECT";
                box.x=x;box.y=y;box.width=25.4;box.height=25.4;box.fill="#ff0000";
                p.pages.get(0).objects.add(box);
            }
        var image=new BufferedImage(317,519,BufferedImage.TYPE_INT_RGB);
        Printable artwork=BannerPrinting.printable(p);
        for(int top=0;top<image.getHeight();top+=50) {
            var g=image.createGraphics();
            try {
                g.setClip(0,top,image.getWidth(),50);
                g.scale(.1,.1);
                artwork.print(g,BannerPrinting.format(p),0);
            } finally {g.dispose();}
        }
        for(int x:new int[]{10,306})for(int y:new int[]{10,508})
            assertEquals(0xff0000,image.getRGB(x,y)&0xffffff,"Corner at "+x+","+y);
        assertEquals(0xffffff,image.getRGB(158,259)&0xffffff);
    }

    @Test void removesDialogMarginsWithoutResizingPaper() throws Exception {
        Publication p=BannerDocuments.create(100,50,"MM");
        for(boolean nativeDriver : new boolean[]{false,true}) {
            PageFormat chosen=BannerPrinting.format(p);
            Paper paper=chosen.getPaper();
            paper.setImageableArea(10,20,paper.getWidth()-30,paper.getHeight()-40);
            chosen.setPaper(paper);
            var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
            PageFormat actual=BannerPrinting.selectedFormat(p,chosen,nativeDriver?chosen:null,settings);
            assertEquals(chosen.getWidth(),actual.getWidth(),.001);
            assertEquals(chosen.getHeight(),actual.getHeight(),.001);
            assertEquals(0,actual.getImageableX(),.001);
            assertEquals(0,actual.getImageableY(),.001);
            assertEquals(actual.getWidth(),actual.getImageableWidth(),.001);
            assertEquals(actual.getHeight(),actual.getImageableHeight(),.001);
            var area=(javax.print.attribute.standard.MediaPrintableArea)settings.get(
                    javax.print.attribute.standard.MediaPrintableArea.class);
            assertEquals(paper.getWidth()/72,area.getWidth(javax.print.attribute.standard.MediaPrintableArea.INCH),.001);
            assertEquals(paper.getHeight()/72,area.getHeight(javax.print.attribute.standard.MediaPrintableArea.INCH),.001);
            assertEquals(10,chosen.getPaper().getImageableX(),.001);
        }
    }

    @Test void bannerPageableRetainsExactCustomSizeAndSkipsReferencePages() {
        Publication p=BannerDocuments.create(72,44,"IN");
        Publication.Page reference=new Publication.Page();reference.reference=true;p.pages.add(reference);
        p.pages.add(new Publication.Page());
        var artwork=BannerPrinting.printable(p);
        Pageable pages=BannerPrinting.pages(p,artwork,BannerPrinting.format(p));
        assertInstanceOf(Book.class,pages);
        assertEquals(2,pages.getNumberOfPages());
        assertEquals(72*72,pages.getPageFormat(0).getWidth(),.001);
        assertEquals(44*72,pages.getPageFormat(1).getHeight(),.001);
        assertSame(artwork,pages.getPrintable(1));
    }

    @Test void landscapeNativeDcKeepsShortEdgeAcrossRollAndPreservesMargins() {
        PageFormat nativePage=new PageFormat();
        Paper paper=new Paper();paper.setSize(72*72,44*72);
        paper.setImageableArea(10,20,72*72-30,44*72-50);nativePage.setPaper(paper);
        PageFormat normalized=BannerPrinting.normalizeNativePage(nativePage);
        assertEquals(PageFormat.LANDSCAPE,normalized.getOrientation());
        assertEquals(44*72,normalized.getPaper().getWidth(),.001);
        assertEquals(72*72,normalized.getPaper().getHeight(),.001);
        assertEquals(nativePage.getWidth(),normalized.getWidth(),.001);
        assertEquals(nativePage.getHeight(),normalized.getHeight(),.001);
        assertEquals(nativePage.getImageableX(),normalized.getImageableX(),.001);
        assertEquals(nativePage.getImageableY(),normalized.getImageableY(),.001);
        assertEquals(nativePage.getImageableWidth(),normalized.getImageableWidth(),.001);
        assertEquals(nativePage.getImageableHeight(),normalized.getImageableHeight(),.001);
    }

    @Test void windowsBannerPrintUsesNativeDialogToRetainTemporaryDriverMedia() {
        var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
        BannerPrinting.configureDialog(settings);
        assertEquals(System.getProperty("os.name", "").startsWith("Windows")
                ? javax.print.attribute.standard.DialogTypeSelection.NATIVE : null,
                settings.get(javax.print.attribute.standard.DialogTypeSelection.class));
    }

    @Test void temporaryWindowsCustomSizeReplacesStaleLetterAttributes() throws Exception {
        Publication p=BannerDocuments.create(72,44,"IN");
        var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
        settings.add(javax.print.attribute.standard.MediaSizeName.NA_LETTER);
        settings.add(new javax.print.attribute.standard.MediaPrintableArea(0,0,8,10,
                javax.print.attribute.standard.MediaPrintableArea.INCH));
        settings.add(javax.print.attribute.standard.PrintQuality.HIGH);
        settings.add(new javax.print.attribute.standard.PrinterResolution(1200,1200,
                javax.print.attribute.standard.PrinterResolution.DPI));
        PageFormat nativePage=BannerPrinting.format(p);
        assertEquals(nativePage.getWidth(),BannerPrinting.selectedFormat(p,new PageFormat(),nativePage,settings).getWidth(),.001);
        assertNull(settings.get(javax.print.attribute.standard.Media.class));
        assertNull(settings.get(javax.print.attribute.standard.PrinterResolution.class));
        assertNotNull(settings.get(javax.print.attribute.standard.MediaPrintableArea.class));
        assertEquals(javax.print.attribute.standard.PrintQuality.HIGH,
                settings.get(javax.print.attribute.standard.PrintQuality.class));
        assertEquals(javax.print.attribute.standard.OrientationRequested.LANDSCAPE,
                settings.get(javax.print.attribute.standard.OrientationRequested.class));
    }

    @Test void customSizeFallbackPreservesTrayAndStillRejectsSmallNativePaper() throws Exception {
        Publication p=BannerDocuments.create(72,44,"IN");
        var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
        settings.add(javax.print.attribute.standard.MediaTray.MAIN);
        assertThrows(PrinterException.class,()->BannerPrinting.selectedFormat(p,BannerPrinting.format(p),new PageFormat(),settings));
        BannerPrinting.selectedFormat(p,new PageFormat(),BannerPrinting.format(p),settings);
        assertEquals(javax.print.attribute.standard.MediaTray.MAIN,settings.get(javax.print.attribute.standard.Media.class));
    }

    @Test void validReportedSizeDoesNotReadNativePageOrChangeSettings() throws Exception {
        Publication p=BannerDocuments.create(72,44,"IN");
        PageFormat reported=BannerPrinting.format(p);
        var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
        settings.add(javax.print.attribute.standard.MediaSizeName.NA_LETTER);
        assertEquals(reported.getWidth(),BannerPrinting.selectedFormat(p,reported,null,settings).getWidth(),.001);
        assertEquals(javax.print.attribute.standard.MediaSizeName.NA_LETTER,settings.get(javax.print.attribute.standard.Media.class));
    }

    @Test void largeBannerRetainsPhysicalSizeAndRejectsLetterPaper() throws Exception {
        Publication p=BannerDocuments.create(240,48,"IN");
        PageFormat format=BannerPrinting.format(p);
        assertEquals(240*72,format.getWidth(),.001);
        assertEquals(48*72,format.getHeight(),.001);
        assertEquals(PageFormat.LANDSCAPE,format.getOrientation());
        assertEquals(48*72,format.getPaper().getWidth(),.001);
        assertEquals(240*72,format.getPaper().getHeight(),.001);
        BannerPrinting.requireSize(p,format);
        assertThrows(PrinterException.class,()->BannerPrinting.requireSize(p,new PageFormat()));
    }

    @Test void tallBannerUsesPortraitPaperAndMismatchReportsBothSizes() throws Exception {
        Publication p=BannerDocuments.create(50,100,"MM");
        PageFormat format=BannerPrinting.format(p);
        assertEquals(PageFormat.PORTRAIT,format.getOrientation());
        assertEquals(50*72/25.4,format.getWidth(),.001);
        assertEquals(100*72/25.4,format.getHeight(),.001);
        BannerPrinting.requireSize(p,format);
        Publication oversized=BannerDocuments.create(500,1000,"MM");
        var error=assertThrows(PrinterException.class,()->BannerPrinting.requireSize(oversized,new PageFormat()));
        assertTrue(error.getMessage().contains("500.0 × 1000.0 mm"));
        assertTrue(error.getMessage().contains("215.9 × 279.4 mm"));
    }

    @Test void acceptsLargerRollPaperAndRotatesArtworkWithoutScaling() throws Exception {
        Publication p=BannerDocuments.create(100,50,"MM");
        Publication.Element box=new Publication.Element();box.type="RECT";
        box.x=10;box.y=10;box.width=10;box.height=10;box.fill="#ff0000";
        p.pages.get(0).objects.add(box);
        PageFormat larger=BannerPrinting.format(BannerDocuments.create(150,70,"MM"));
        BannerPrinting.requireSize(p,larger);
        PageFormat portrait=BannerPrinting.format(BannerDocuments.create(60,120,"MM"));
        BannerPrinting.requireSize(p,portrait);
        var image=new BufferedImage(200,350,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        try {
            BannerPrinting.printable(p).print(g,portrait,0);
            assertEquals(0xff0000,image.getRGB(100,40)&0xffffff);
            assertEquals(0xffffff,image.getRGB(70,40)&0xffffff);
        } finally {g.dispose();}
        assertThrows(PrinterException.class,()->BannerPrinting.requireSize(p,
                BannerPrinting.format(BannerDocuments.create(40,90,"MM"))));
    }

    @Test void printsArtworkAtActualSizeAndSkipsReferencePages() throws Exception {
        Publication p=BannerDocuments.create(100,50,"MM");
        p.pages.get(0).reference=true;
        Publication.Page page=new Publication.Page();p.pages.add(page);
        Publication.Element box=new Publication.Element();box.type="RECT";
        box.x=10;box.y=10;box.width=10;box.height=10;box.fill="#ff0000";
        page.objects.add(box);
        var image=new BufferedImage(300,150,BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();
        try {
            Printable printable=BannerPrinting.printable(p);
            assertEquals(Printable.PAGE_EXISTS,printable.print(graphics,BannerPrinting.format(p),0));
            assertEquals(0xff0000,image.getRGB(40,40)&0xffffff);
            assertEquals(0xffffff,image.getRGB(70,40)&0xffffff);
            assertEquals(Printable.NO_SUCH_PAGE,printable.print(graphics,BannerPrinting.format(p),1));
        } finally {graphics.dispose();}
    }
}
