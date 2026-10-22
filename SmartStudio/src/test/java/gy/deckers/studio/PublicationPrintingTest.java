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
