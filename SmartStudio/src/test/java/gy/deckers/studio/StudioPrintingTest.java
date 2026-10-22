package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import static org.junit.jupiter.api.Assertions.*;

class StudioPrintingTest {
    private PageFormat page() {
        Paper paper=new Paper();paper.setSize(600,800);paper.setImageableArea(20,30,560,740);
        PageFormat page=new PageFormat();page.setPaper(paper);return page;
    }
    private BufferedImage redImage() {
        BufferedImage image=new BufferedImage(300,150,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();g.setColor(Color.RED);g.fillRect(0,0,300,150);g.dispose();return image;
    }
    @Test void badgeKeepsPhysicalSizeAndOnlyPrintsOnePage() throws Exception {
        BufferedImage output=new BufferedImage(600,800,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=output.createGraphics();
        try {
            Printable content=StudioPrinting.printable(redImage(),240,120);
            assertEquals(Printable.PAGE_EXISTS,content.print(g,page(),0));
            assertEquals(Color.RED.getRGB(),output.getRGB(180,340));
            assertEquals(Color.RED.getRGB(),output.getRGB(419,459));
            assertEquals(Color.BLACK.getRGB(),output.getRGB(179,340));
            assertEquals(Color.BLACK.getRGB(),output.getRGB(420,459));
            assertEquals(Printable.NO_SUCH_PAGE,content.print(g,page(),1));
            assertThrows(PrinterException.class,()->StudioPrinting.printable(redImage(),600,120).print(g,page(),0));
        } finally {g.dispose();}
    }
    @Test void imageFitsPrintableAreaAndPreservesAspectRatio() throws Exception {
        BufferedImage output=new BufferedImage(600,800,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=output.createGraphics();
        try {
            StudioPrinting.printable(redImage(),0,0).print(g,page(),0);
            assertEquals(Color.RED.getRGB(),output.getRGB(20,260));
            assertEquals(Color.RED.getRGB(),output.getRGB(579,539));
            assertEquals(Color.BLACK.getRGB(),output.getRGB(20,259));
            assertEquals(Color.BLACK.getRGB(),output.getRGB(580,539));
        } finally {g.dispose();}
    }

    @Test void passportRequestsOnlyPaperAdvertisedByTheDriver(){
        javax.print.PrintService service=(javax.print.PrintService)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class<?>[]{javax.print.PrintService.class},(proxy,method,args)->{
                if(method.getName().equals("getSupportedAttributeValues"))return new javax.print.attribute.standard.Media[]{javax.print.attribute.standard.MediaSizeName.ISO_A4};
                return null;
            });
        assertNull(StudioPrinting.passportMedia(service),"Do not send a synthetic paper ID to a real printer");
        var exact=StudioPrinting.passportMedia(null);
        javax.print.PrintService photo=(javax.print.PrintService)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class<?>[]{javax.print.PrintService.class},(proxy,method,args)->{
                if(method.getName().equals("getSupportedAttributeValues"))return new javax.print.attribute.standard.Media[]{javax.print.attribute.standard.MediaSizeName.ISO_A4,exact};
                return null;
            });
        assertSame(exact,StudioPrinting.passportMedia(photo));
    }

    @Test void slowPrinterSetupDoesNotBlockSwingAndCancellationAllowsRetry()throws Exception{
        var entered=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        var cancelled=new java.util.concurrent.CountDownLatch(1);
        var retryDone=new java.util.concurrent.CountDownLatch(1);
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        try{
            javax.swing.SwingUtilities.invokeAndWait(()->StudioPrinting.prepare(null,text->{
                assertTrue(javax.swing.SwingUtilities.isEventDispatchThread());
                if(text.equals("Printing cancelled."))cancelled.countDown();
            },()->{assertFalse(javax.swing.SwingUtilities.isEventDispatchThread());entered.countDown();release.await();return null;}));
            assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            javax.swing.SwingUtilities.invokeAndWait(()->{
                try{assertThrows(IllegalStateException.class,()->StudioPrinting.prepare(null,text->{},()->null));}
                catch(Throwable ex){failure.set(ex);}
            });
        }finally{release.countDown();}
        assertTrue(cancelled.await(5,java.util.concurrent.TimeUnit.SECONDS));
        if(failure.get()!=null)throw new AssertionError(failure.get());
        javax.swing.SwingUtilities.invokeAndWait(()->StudioPrinting.prepare(null,text->{if(text.equals("Printing cancelled."))retryDone.countDown();},()->null));
        assertTrue(retryDone.await(5,java.util.concurrent.TimeUnit.SECONDS));
    }
}
