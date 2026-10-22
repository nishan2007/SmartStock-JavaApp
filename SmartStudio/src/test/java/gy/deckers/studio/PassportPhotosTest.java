package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import static org.junit.jupiter.api.Assertions.*;

class PassportPhotosTest {
    @Test void bleedPreservesTrimFramingAndExtendsBackground()throws Exception{
        BufferedImage source=new BufferedImage(80,100,BufferedImage.TYPE_INT_ARGB);
        Graphics2D fill=source.createGraphics();fill.setColor(Color.RED);fill.fillRect(30,25,20,45);fill.dispose();
        var photo=PhotoFraming.initial(source).styled(Color.BLUE,null);
        BufferedImage normal=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB),extended=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);
        Graphics2D a=normal.createGraphics(),b=extended.createGraphics();
        try{photo.paint(a,50,50,80,100);photo.paint(b,50,50,80,100,PassportPhotos.BLEED);}finally{a.dispose();b.dispose();}
        for(int y=50;y<150;y++)for(int x=50;x<130;x++)assertEquals(normal.getRGB(x,y),extended.getRGB(x,y));
        assertEquals(Color.BLUE.getRGB(),extended.getRGB(47,90));
        for(var template:PassportPhotos.Template.values()){
            var boxes=template.boxes(PassportPhotos.BLEED);
            for(int i=0;i<boxes.size();i++){
                var box=boxes.get(i);assertEquals(template.width,box.width);assertEquals(template.height,box.height);
                var outer=new java.awt.geom.Rectangle2D.Double(box.x-PassportPhotos.BLEED,box.y-PassportPhotos.BLEED,box.width+2*PassportPhotos.BLEED,box.height+2*PassportPhotos.BLEED);
                assertTrue(new java.awt.geom.Rectangle2D.Double(0,0,288,432).contains(outer));
                for(int j=i+1;j<boxes.size();j++)assertFalse(outer.intersects(boxes.get(j)));
            }
            Paper paper=new Paper();paper.setSize(288,432);paper.setImageableArea(9,9,270,414);
            PageFormat page=new PageFormat();page.setPaper(paper);
            Graphics2D g=extended.createGraphics();
            try{assertEquals(Printable.PAGE_EXISTS,PassportPhotos.printable(template,photo,PassportPhotos.BLEED).print(g,page,0));
                paper.setImageableArea(0,0,template.width,template.height*2+12);page.setPaper(paper);
                assertThrows(PrinterException.class,()->PassportPhotos.printable(template,photo,PassportPhotos.BLEED).print(g,page,0));
            }finally{g.dispose();}
        }
    }

    @Test void selectedBackgroundSurvivesSheetAndPrintRendering()throws Exception{
        BufferedImage cutout=new BufferedImage(20,20,BufferedImage.TYPE_INT_ARGB);
        cutout.setRGB(10,10,Color.RED.getRGB());
        for(var template:PassportPhotos.Template.values()){
            for(Color background:new Color[]{Color.BLUE,new Color(65,140,210),Color.WHITE}){
                PhotoFraming photo=PhotoFraming.initial(cutout).styled(background,null);
                BufferedImage sheet=PassportPhotos.render(template,photo);
                for(var box:template.boxes()){
                    assertEquals(background.getRGB(),sheet.getRGB((int)((box.x+5)*300/72),(int)((box.y+5)*300/72)));
                }
                Paper paper=new Paper();paper.setSize(288,432);paper.setImageableArea(0,0,288,432);
                PageFormat page=new PageFormat();page.setPaper(paper);
                BufferedImage printed=new BufferedImage(288,432,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=printed.createGraphics();
                try{assertEquals(Printable.PAGE_EXISTS,PassportPhotos.printable(template,photo).print(g,page,0));}finally{g.dispose();}
                for(var box:template.boxes())assertEquals(background.getRGB(),printed.getRGB((int)box.x+5,(int)box.y+5));
                assertEquals(Color.WHITE.getRGB(),printed.getRGB(0,0),"Paper outside photo boxes stays white");
            }
            var defaultSheet=PassportPhotos.render(template,PhotoFraming.initial(cutout));
            var box=template.boxes().get(0);
            assertEquals(Color.WHITE.getRGB(),defaultSheet.getRGB((int)((box.x+5)*300/72),(int)((box.y+5)*300/72)));
        }
    }
    @Test void defaultMediaIsExactlyFourBySixRatherThanNearestStandardPaper(){
        var size=javax.print.attribute.standard.MediaSize.getMediaSizeForName(StudioPrinting.passportMedia(null));
        assertEquals(4,size.getX(javax.print.attribute.Size2DSyntax.INCH),.0001);
        assertEquals(6,size.getY(javax.print.attribute.Size2DSyntax.INCH),.0001);
    }
    @Test void templatesHaveExactSizesAndCounts(){
        assertEquals(4,PassportPhotos.Template.GUYANA.boxes().size());
        assertEquals(35,PassportPhotos.Template.GUYANA.width*25.4/72,1e-9);
        assertEquals(45,PassportPhotos.Template.GUYANA.height*25.4/72,1e-9);
        assertEquals(2,PassportPhotos.Template.US.boxes().size());
        assertEquals(144,PassportPhotos.Template.US.width);
        assertEquals(144,PassportPhotos.Template.US.height);
        for(var template:PassportPhotos.Template.values()){
            var boxes=template.boxes();
            for(int i=0;i<boxes.size();i++){
                assertTrue(new java.awt.geom.Rectangle2D.Double(0,0,288,432).contains(boxes.get(i)));
                for(int j=i+1;j<boxes.size();j++)assertFalse(boxes.get(i).intersects(boxes.get(j)));
            }
        }
    }
    @Test void printingPreservesSizeRejectsClippingAndWrongPaper()throws Exception{
        BufferedImage source=new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);
        Graphics2D red=source.createGraphics();red.setColor(Color.RED);red.fillRect(0,0,20,20);red.dispose();
        for(var template:PassportPhotos.Template.values()){
            var photo=PhotoFraming.initial(source);var sheet=PassportPhotos.render(template,photo);
            assertEquals(1200,sheet.getWidth());assertEquals(1800,sheet.getHeight());
            for(var box:template.boxes())assertEquals(Color.RED.getRGB(),sheet.getRGB((int)(box.getCenterX()*300/72),(int)(box.getCenterY()*300/72)));
            var printable=PassportPhotos.printable(template,photo);
            Paper paper=new Paper();paper.setSize(288,432);paper.setImageableArea(9,9,270,414);
            PageFormat page=new PageFormat();page.setPaper(paper);
            Graphics2D g=sheet.createGraphics();
            try{
                assertEquals(Printable.PAGE_EXISTS,printable.print(g,page,0));
                assertEquals(Printable.NO_SUCH_PAGE,printable.print(g,page,1));
                paper.setImageableArea(100,100,88,232);page.setPaper(paper);
                assertThrows(PrinterException.class,()->printable.print(g,page,0));
                paper.setSize(612,792);paper.setImageableArea(0,0,612,792);page.setPaper(paper);
                assertThrows(PrinterException.class,()->printable.print(g,page,0));
            }finally{g.dispose();}
        }
    }

    @Test void landscapeAndAsymmetricMarginsKeepActualPhotoSize()throws Exception{
        BufferedImage source=new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB);
        Graphics2D fill=source.createGraphics();fill.setColor(Color.RED);fill.fillRect(0,0,20,20);fill.dispose();
        for(int orientation:new int[]{PageFormat.PORTRAIT,PageFormat.LANDSCAPE,PageFormat.REVERSE_LANDSCAPE}){
            Paper paper=new Paper();paper.setSize(288,432);paper.setImageableArea(55,70,230,350);
            PageFormat page=new PageFormat();page.setPaper(paper);page.setOrientation(orientation);
            BufferedImage target=new BufferedImage(500,500,BufferedImage.TYPE_INT_RGB);Graphics2D g=target.createGraphics();
            try{assertEquals(Printable.PAGE_EXISTS,PassportPhotos.printable(PassportPhotos.Template.GUYANA,PhotoFraming.initial(source)).print(g,page,0));}finally{g.dispose();}
            int redPixels=0;for(int y=0;y<500;y++)for(int x=0;x<500;x++)if(target.getRGB(x,y)==Color.RED.getRGB())redPixels++;
            assertEquals(4*(35*72/25.4)*(45*72/25.4),redPixels,1200,"Rotation and margin adjustment must not shrink photos");
        }
    }
}
