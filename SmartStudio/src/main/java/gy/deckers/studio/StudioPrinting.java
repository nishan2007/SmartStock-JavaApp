package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.util.function.Consumer;

/** Prints rendered content, independent of preview zoom and workspace controls. */
final class StudioPrinting {
    private static boolean printing;
    private StudioPrinting() { }

    static Printable printable(BufferedImage image, double widthPoints, double heightPoints) {
        return (graphics, page, index) -> {
            if (index != 0) return Printable.NO_SUCH_PAGE;
            boolean actualSize = widthPoints > 0 && heightPoints > 0;
            double width = widthPoints, height = heightPoints;
            if (!actualSize) {
                double scale = Math.min(page.getImageableWidth() / image.getWidth(),
                        page.getImageableHeight() / image.getHeight());
                width = image.getWidth() * scale;
                height = image.getHeight() * scale;
            }
            if (width > page.getImageableWidth() + .5 || height > page.getImageableHeight() + .5)
                throw new PrinterException("The badge does not fit the printable area. Choose larger paper or change the printer orientation/margins.");
            Graphics2D draw = (Graphics2D) graphics.create();
            try {
                draw.translate(page.getImageableX() + (page.getImageableWidth() - width) / 2,
                        page.getImageableY() + (page.getImageableHeight() - height) / 2);
                draw.setColor(Color.WHITE);
                draw.fill(new java.awt.geom.Rectangle2D.Double(0, 0, width, height));
                draw.scale(width / image.getWidth(), height / image.getHeight());
                draw.drawImage(image, 0, 0, null);
            } finally { draw.dispose(); }
            return Printable.PAGE_EXISTS;
        };
    }

    static void print(Component owner, BufferedImage image, String title, double widthPoints,
                      double heightPoints, Consumer<String> status) {
        print(owner,image,null,title,widthPoints,heightPoints,status);
    }

    static void print(Component owner, BufferedImage image, BufferedImage back, String title, double widthPoints,
                      double heightPoints, Consumer<String> status) {
        print(owner,image,back,title,widthPoints,heightPoints,status,()->{});
    }
    static void print(Component owner,BufferedImage image,BufferedImage back,String title,double widthPoints,double heightPoints,Consumer<String> status,Runnable accepted) {
        print(owner,image,back,title,widthPoints,heightPoints,status,accepted,()->{});
    }
    static void print(Component owner,BufferedImage image,BufferedImage back,String title,double widthPoints,double heightPoints,Consumer<String> status,Runnable accepted,Runnable sent) {
        if(printing)throw new IllegalStateException("Wait for the current print job before starting another card.");
        prepare(owner,status,()->{
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName(title);
            javax.print.attribute.PrintRequestAttributeSet settings = new javax.print.attribute.HashPrintRequestAttributeSet();
            // Keep the selected printer's paper and tray defaults.
            settings.add(javax.print.attribute.standard.Sides.ONE_SIDED);
            PrintMargins.apply(job,settings);
            if (!job.printDialog(settings)) return null;
            PrintMargins.apply(job,settings);
            if(back!=null) {
                settings.add(new javax.print.attribute.standard.Copies(1));
                settings.add(javax.print.attribute.standard.Sides.ONE_SIDED);
            }
            job.setPrintable(printable(image, widthPoints, heightPoints), job.getPageFormat(settings));
            return ()->send(owner,job,settings,title,back,widthPoints,heightPoints,status,sent,accepted);
        });
    }

    static void printPassport(Component owner, Printable printable, Consumer<String> status) {
        printPassport(owner,printable,status,()->{});
    }
    static void printPassport(Component owner, Printable printable, Consumer<String> status,Runnable sent) {
        if(printing)throw new IllegalStateException("Wait for the current print job to finish.");
        prepare(owner,status,()->{
            PrinterJob job=PrinterJob.getPrinterJob();job.setJobName("Passport photos — 4 × 6");
            var settings=new javax.print.attribute.HashPrintRequestAttributeSet();
            var media=passportMedia(job.getPrintService());
            if(media!=null)settings.add(media);
            settings.add(javax.print.attribute.standard.OrientationRequested.PORTRAIT);
            settings.add(javax.print.attribute.standard.Sides.ONE_SIDED);
            PrintMargins.apply(job,settings);
            if(!job.printDialog(settings))return null;
            // Discard inherited application margins, then ask the selected driver for its hardware area.
            PrintMargins.apply(job,settings);
            job.setPrintable(printable,job.getPageFormat(settings));
            return ()->send(owner,job,settings,"Passport photos",null,0,0,status,sent);
        });
    }

    /** Native driver discovery and preferences may block on Windows/spooler/network IO. */
    static void prepare(Component owner,Consumer<String> status,java.util.concurrent.Callable<Runnable> task){
        if(printing)throw new IllegalStateException("Wait for the current print job to finish.");
        printing=true;status.accept("Opening printer settings…");
        new SwingWorker<Runnable,Void>(){
            protected Runnable doInBackground()throws Exception{return task.call();}
            protected void done(){
                try{
                    Runnable next=get();
                    if(next==null){printing=false;status.accept("Printing cancelled.");}
                    else next.run();
                }catch(Exception ex){
                    printing=false;status.accept("Could not open printer settings.");
                    Throwable cause=ex.getCause()==null?ex:ex.getCause();
                    JOptionPane.showMessageDialog(owner,cause.getMessage(),"Print",JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static final javax.print.attribute.standard.MediaSizeName PASSPORT_PAPER = new javax.print.attribute.standard.MediaSizeName(10000) {
        public String toString(){return "4x6-photo";}
    };
    static {
        new javax.print.attribute.standard.MediaSize(4,6,javax.print.attribute.Size2DSyntax.INCH,PASSPORT_PAPER);
    }
    static javax.print.attribute.standard.MediaSizeName passportMedia(javax.print.PrintService service) {
        if(service!=null) {
            Object supported=service.getSupportedAttributeValues(javax.print.attribute.standard.Media.class,null,null);
            if(supported instanceof javax.print.attribute.standard.Media[] media)for(var item:media) {
                if(item instanceof javax.print.attribute.standard.MediaSizeName name) {
                    var size=javax.print.attribute.standard.MediaSize.getMediaSizeForName(name);
                    if(size!=null&&Math.abs(size.getX(javax.print.attribute.Size2DSyntax.INCH)-4)<.01
                            &&Math.abs(size.getY(javax.print.attribute.Size2DSyntax.INCH)-6)<.01)return name;
                }
            }
        }
        // A private, synthetic media ID is not a valid driver paper selection.
        // If no exact supported size is advertised, let Printer Properties select it.
        return service==null?PASSPORT_PAPER:null;
    }

    private static void send(Component owner, PrinterJob job, javax.print.attribute.PrintRequestAttributeSet settings,
                             String title, BufferedImage back, double widthPoints, double heightPoints, Consumer<String> status) {
        send(owner,job,settings,title,back,widthPoints,heightPoints,status,()->{});
    }
    private static void send(Component owner, PrinterJob job, javax.print.attribute.PrintRequestAttributeSet settings,
                             String title, BufferedImage back, double widthPoints, double heightPoints, Consumer<String> status,Runnable sent) {
        send(owner,job,settings,title,back,widthPoints,heightPoints,status,sent,()->{});
    }
    private static void send(Component owner, PrinterJob job, javax.print.attribute.PrintRequestAttributeSet settings,
                             String title, BufferedImage back, double widthPoints, double heightPoints, Consumer<String> status,Runnable sent,Runnable beforePrint) {
        status.accept("Sending to printer…");
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws PrinterException { beforePrint.run();job.print(settings); return null; }
            protected void done() {
                try {
                    get(); status.accept("Print job sent: " + title);
                    if(back!=null) {
                        int answer=JOptionPane.showOptionDialog(owner,
                                "The front job has been sent. Wait until the front has physically printed and the card has ejected.\n"
                                +"Flip the same card and reinsert it using the Magicard 300 hand-feed slot.\n"
                                +"Keep Hand Feed Mode ON and use the orientation confirmed with your test card.\n"
                                +"Select Print back only when the card is ready. Cancelling leaves the front printed.",
                                "Magicard 300 — flip and reinsert card",JOptionPane.DEFAULT_OPTION,JOptionPane.INFORMATION_MESSAGE,
                                null,new String[]{"Print back","Cancel"},"Cancel");
                        if(answer==0) {
                            job.setJobName(title+" — back");
                            job.setPrintable(printable(back,widthPoints,heightPoints),job.getPageFormat(settings));
                            send(owner,job,settings,title+" — back",null,widthPoints,heightPoints,status,sent);
                        } else {printing=false;status.accept("Back printing cancelled. The front job was already sent.");}
                    } else {printing=false;sent.run();}
                }
                catch (Exception ex) {
                    printing=false;
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    status.accept("Printing failed.");
                    JOptionPane.showMessageDialog(owner, cause.getMessage(), "Print", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
