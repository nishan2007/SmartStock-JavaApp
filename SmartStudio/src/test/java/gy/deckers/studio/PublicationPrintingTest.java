package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import javax.print.*;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;
class PublicationPrintingTest {
    @Test void requestsSupportedQualityWithoutReplacingUserChoices(){
        PrintService supported=(PrintService)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PrintService.class},(proxy,method,args)->{
            assertEquals("isAttributeValueSupported",method.getName());return true;
        });
        var settings=new HashPrintRequestAttributeSet();PublicationPrinting.selectedPrinterQuality(supported,settings);
        assertEquals(new PrinterResolution(600,600,PrinterResolution.DPI),settings.get(PrinterResolution.class));
        assertEquals(PrintQuality.HIGH,settings.get(PrintQuality.class));assertEquals(Chromaticity.COLOR,settings.get(Chromaticity.class));
        var explicit=new PrinterResolution(1200,1200,PrinterResolution.DPI);settings.add(explicit);settings.add(Chromaticity.MONOCHROME);settings.add(PrintQuality.DRAFT);
        PublicationPrinting.selectedPrinterQuality(supported,settings);assertEquals(explicit,settings.get(PrinterResolution.class));
        assertEquals(Chromaticity.MONOCHROME,settings.get(Chromaticity.class));assertEquals(PrintQuality.DRAFT,settings.get(PrintQuality.class));
    }
    @Test void originalImageDetailIsRenderedAtPrinterResolution()throws Exception{
        var p=document();p.width=p.height=25.4;p.print.width=p.print.height=25.4;
        p.print.left=p.print.right=p.print.top=p.print.bottom=0;p.print.cropMarks=false;
        var source=new BufferedImage(600,600,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<600;y++)for(int x=0;x<600;x++)source.setRGB(x,y,(x%2==0?0:0xffffff));
        PublicationImport.addImage(p,p.pages.get(0),source,0,0,25.4,25.4);
        var printed=new BufferedImage(600,600,BufferedImage.TYPE_INT_RGB);var g=printed.createGraphics();
        try{g.scale(600./72,600./72);PublicationPrinting.printable(p).print(g,format(p,0),0);}finally{g.dispose();}
        int transitions=0;for(int x=51;x<550;x++)if((printed.getRGB(x,300)&0xffffff)!=(printed.getRGB(x-1,300)&0xffffff))transitions++;
        assertTrue(transitions>450,"600-DPI source lines must survive without a 300-DPI intermediate");
    }
    @Test void dialogSettingsDoNotCarryHardwareMarginsFromTheDefaultPrinter(){
        var p=document();var settings=PublicationPrinting.settings(p,"Letter");
        assertNull(settings.get(MediaPrintableArea.class),"Selected printer must supply its own hardware area");
        assertEquals(MediaSizeName.NA_LETTER,settings.get(Media.class));
        assertEquals(OrientationRequested.PORTRAIT,settings.get(OrientationRequested.class));
        var chosen=new MediaPrintableArea(3.175f,3.175f,209.55f,273.05f,MediaPrintableArea.MM);
        settings.add(chosen);PublicationPrinting.selectedPrinterArea(p,null,settings);
        assertEquals(0,((MediaPrintableArea)settings.get(MediaPrintableArea.class)).getX(MediaPrintableArea.MM));
    }
    @Test void hardwareAreaIsQueriedFromSelectedPrinterAfterDialog(){
        var p=document();var settings=PublicationPrinting.settings(p,"Letter");
        settings.add(new MediaPrintableArea(25.4f,25.4f,165.1f,228.6f,MediaPrintableArea.MM));
        var selectedArea=new MediaPrintableArea(3.175f,3.175f,209.55f,273.05f,MediaPrintableArea.MM);
        PrintService selected=(PrintService)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PrintService.class},(proxy,method,args)->{
            if(method.getName().equals("isAttributeValueSupported"))return false;
            assertNull(((AttributeSet)args[2]).get(MediaPrintableArea.class),"Do not constrain hardware capability queries with the inherited clip");
            assertEquals("getSupportedAttributeValues",method.getName());return new MediaPrintableArea[]{selectedArea};
        });
        PublicationPrinting.selectedPrinterArea(p,selected,settings);
        assertSame(selectedArea,settings.get(MediaPrintableArea.class));
    }
    @Test void repeatedBandedCallsPreserveWholeSheetArtworkAndActualSize()throws Exception{
        var p=document();p.print.left=p.print.right=p.print.top=p.print.bottom=0;
        p.print.alignment="CENTER";p.print.rotate=true;p.print.quantity=12;
        var ink=new Publication.Element();ink.type="RECT";ink.x=ink.y=0;ink.width=p.width;ink.height=p.height;ink.fill="#000000";ink.stroke="none";
        p.pages.get(0).objects.add(ink);
        assertEquals(12,SheetLayout.pack(p.width,p.height,p.print).capacity());
        var page=format(p,0);var expected=new PublicationRenderer(p).raster(0,300);
        var actual=new BufferedImage(expected.getWidth(),expected.getHeight(),BufferedImage.TYPE_INT_RGB);
        var printable=PublicationPrinting.printable(p);
        for(int band=0;band<2;band++){
            var g=actual.createGraphics();try{
                int start=band*actual.getWidth()/2,end=(band+1)*actual.getWidth()/2;
                g.setClip(start,0,end-start,actual.getHeight());g.setColor(Color.WHITE);g.fillRect(0,0,actual.getWidth(),actual.getHeight());
                g.scale(300./72,300./72);assertEquals(Printable.PAGE_EXISTS,printable.print(g,page,0));
            }finally{g.dispose();}
        }
        for(int y=0;y<actual.getHeight();y++){
            assertArrayEquals(expected.getRGB(0,y,actual.getWidth(),1,null,0,actual.getWidth()),actual.getRGB(0,y,actual.getWidth(),1,null,0,actual.getWidth()),"Band seam or moved artwork at row "+y);
        }
        int row=(int)(20/25.4*300),first=-1,last=-1;
        for(int x=0;x<actual.getWidth();x++)if((actual.getRGB(x,row)&0xffffff)<0x888888){if(first<0)first=x;last=x;}
        assertEquals(203.2,(last-first+1)*25.4/300,.2);
    }
    @Test void shiftsTwelveCardsInsidePortraitLetterHardwareAreaWithoutScaling()throws Exception{
        var p=document();p.width=50.8;p.height=88.9;
        p.print.left=10;p.print.right=0;p.print.cropMarks=false;p.print.quantity=12;
        var ink=new Publication.Element();ink.type="RECT";ink.x=ink.y=0;ink.width=p.width;ink.height=p.height;ink.fill="#000000";
        p.pages.get(0).objects.add(ink);
        assertEquals(12,SheetLayout.pack(p.width,p.height,p.print).capacity());
        var image=print(p,format(p,3.175));
        int y=(int)(20/25.4*300),first=-1,last=-1;
        for(int x=0;x<image.getWidth();x++)if((image.getRGB(x,y)&0xffffff)<0x888888){if(first<0)first=x;last=x;}
        assertEquals(203.2,(last-first+1)*25.4/300,.2,"All four card widths must print at actual size");
        assertEquals(9.525,first*25.4/300,.15,"Shift left only enough to clear the right hardware margin");
    }
    private Publication document(){
        Publication p=new Publication();p.width=88.9;p.height=50.8;p.print.width=215.9;p.print.height=279.4;
        p.print.left=p.print.right=p.print.top=p.print.bottom=6.35;p.print.gapX=p.print.gapY=0;
        p.print.alignment="TOP_LEFT";p.print.rotate=false;p.print.quantity=1;p.print.cropMarks=true;return p;
    }
    private PageFormat format(Publication p,double margin){
        double scale=PublicationPrinting.POINTS_PER_MM;Paper paper=new Paper();paper.setSize(p.print.width*scale,p.print.height*scale);
        paper.setImageableArea(margin*scale,margin*scale,(p.print.width-2*margin)*scale,(p.print.height-2*margin)*scale);
        PageFormat format=new PageFormat();format.setPaper(paper);return format;
    }
    private BufferedImage print(Publication p,PageFormat format)throws Exception{
        double dpi=300,scale=dpi/72;BufferedImage image=new BufferedImage((int)Math.ceil(format.getWidth()*scale),(int)Math.ceil(format.getHeight()*scale),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.scale(scale,scale);
        g.clip(new java.awt.geom.Rectangle2D.Double(format.getImageableX(),format.getImageableY(),format.getImageableWidth(),format.getImageableHeight()));
        try{assertEquals(Printable.PAGE_EXISTS,PublicationPrinting.printable(p).print(g,format,0));}finally{g.dispose();}return image;
    }
    private int darkNear(BufferedImage image,double x,double y){
        int cx=(int)Math.round(x/25.4*300),cy=(int)Math.round(y/25.4*300),dark=0;
        for(int yy=cy-2;yy<=cy+2;yy++)for(int xx=cx-2;xx<=cx+2;xx++)if((image.getRGB(xx,yy)&0xffffff)<0x888888)dark++;return dark;
    }
    @Test void printsCutMarksOutsideLayoutMarginsUsingHardwareArea()throws Exception{
        var p=document();var page=format(p,3.175);var image=print(p,page);
        assertTrue(darkNear(image,4.85,6.35)>0,"Left trim mark must survive the actual printer clip");
        assertTrue(darkNear(image,6.35,4.85)>0,"Top trim mark must survive the actual printer clip");
        assertDoesNotThrow(()->PublicationPrinting.requireFits(p,format(p,6.35)),"Printer clipping must not block an actual-size print");
        p.print.cropMarks=false;assertEquals(0,darkNear(print(p,page),4.85,6.35));
    }
    @Test void refusesPaperMismatchButAllowsEdgeArtworkAndMarks(){
        var p=document();PageFormat wrong=format(p,3);Paper paper=wrong.getPaper();paper.setSize(595,842);wrong.setPaper(paper);
        assertThrows(PrinterException.class,()->PublicationPrinting.requireFits(p,wrong));
        p.print.left=0;p.print.alignment="TOP_LEFT";
        assertDoesNotThrow(()->PublicationPrinting.requireFits(p,format(p,0)));
    }
    @Test void choosesLargestSupportedAreaInsteadOfLayoutMargins(){
        var p=document();PrintService service=(PrintService)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PrintService.class},(proxy,method,args)->{
            if(method.getName().equals("isAttributeValueSupported"))return false;
            assertEquals("getSupportedAttributeValues",method.getName());
            return new MediaPrintableArea[]{new MediaPrintableArea(6.35f,6.35f,203.2f,266.7f,MediaPrintableArea.MM),new MediaPrintableArea(3.175f,3.175f,209.55f,273.05f,MediaPrintableArea.MM)};
        });
        var area=PublicationPrinting.requestedArea(p,service,new HashPrintRequestAttributeSet());assertEquals(3.175,area.getX(MediaPrintableArea.MM),.001);
        assertEquals(0,PublicationPrinting.requestedArea(p,null,new HashPrintRequestAttributeSet()).getX(MediaPrintableArea.MM));
    }
    @Test void preservesRotatedDuplexOffsetsAndPageBounds()throws Exception{
        var p=document();p.print.rotate=true;p.print.duplex=true;p.pages.add(new Publication.Page());
        p.print.backOffsetX=.2;p.print.backOffsetY=.2;assertDoesNotThrow(()->PublicationPrinting.requireFits(p,format(p,2)));
        var image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        try{var printable=PublicationPrinting.printable(p);assertEquals(Printable.NO_SUCH_PAGE,printable.print(g,format(p,2),-1));assertEquals(Printable.NO_SUCH_PAGE,printable.print(g,format(p,2),2));}finally{g.dispose();}
    }
}
