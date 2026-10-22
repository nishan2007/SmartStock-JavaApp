package gy.deckers.studio;

import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.print.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.print.attribute.*;
import javax.print.attribute.standard.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

final class LinkedRisoOutput {
    record Job(Publication document,List<String> subPages,int sheets){int count(){return Math.multiplyExact(sheets,subPages.size());}}
    static Job job(Publication source,int page,List<String> selected){
        if(selected.isEmpty()||new HashSet<>(selected).size()!=selected.size())throw new IllegalArgumentException("Choose at least one distinct Riso sub-page.");
        if(!LinkedRiso.visible(source,page,LinkedRiso.UNASSIGNED).isEmpty())throw new IllegalArgumentException("Organise the Unassigned objects first, or move them to Not printing. New objects are never silently omitted.");
        for(String id:selected)LinkedRiso.subPage(source,page,id);
        Publication p=PublicationPrintOptions.select(source,List.of(page),false,false);p.print.mixPages=false;
        PublicationPreflight.requireMergeData(p);int sheets=new PublicationRenderer(p).sheets();if((long)sheets*selected.size()>1000)throw new IllegalArgumentException("Choose at most 1000 output sheets.");
        return new Job(p,List.copyOf(selected),sheets);
    }
    static BufferedImage raster(Job job,int index,int dpi){
        if(index<0||index>=job.count())throw new IllegalArgumentException("Invalid Riso output page.");
        String sub=job.subPages().get(index/job.sheets());var p=job.document();
        var rgb=new PublicationRenderer(p).raster(index%job.sheets(),dpi,LinkedRiso.filter(p,0,sub,false));
        var gray=new BufferedImage(rgb.getWidth(),rgb.getHeight(),BufferedImage.TYPE_BYTE_GRAY);
        for(int y=0;y<rgb.getHeight();y++){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();for(int x=0;x<rgb.getWidth();x++)gray.getRaster().setSample(x,y,0,rgb.getRGB(x,y)&255);}return gray;
    }
    static Path pdf(Job job,Path target)throws Exception{
        Path temp=Files.createTempFile(target.toAbsolutePath().getParent(),"linked-riso-",".pdf");
        try{try(PDDocument doc=new PDDocument()){
            var labels=new PDPageLabels(doc);var p=job.document();
            for(int index=0;index<job.count();index++){
                if(Thread.currentThread().isInterrupted())throw new java.io.IOException("Export cancelled.");
                var sub=LinkedRiso.subPage(p,0,job.subPages().get(index/job.sheets()));var label=new PDPageLabelRange();label.setPrefix(p.pages.get(0).name+" • "+sub.name+" • Sheet "+(index%job.sheets()+1));labels.setLabelItem(index,label);
                PDPage page=new PDPage(new PDRectangle((float)(p.print.width*72/25.4),(float)(p.print.height*72/25.4)));doc.addPage(page);try(var out=new PDPageContentStream(doc,page)){out.drawImage(LosslessFactory.createFromImage(doc,raster(job,index,300)),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}
            }
            doc.getDocumentCatalog().setPageLabels(labels);doc.save(temp.toFile());
        }Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);return target;}finally{Files.deleteIfExists(temp);}
    }
    static PreparedPrint printer(Component parent,Job job)throws PrinterException{
        var p=job.document();PrinterJob printer=PrinterJob.getPrinterJob();printer.setJobName(p.title+" • Riso sub-pages");
        Paper paper=new Paper();paper.setSize(p.print.width*72/25.4,p.print.height*72/25.4);paper.setImageableArea(0,0,paper.getWidth(),paper.getHeight());var initial=printer.defaultPage();initial.setPaper(paper);printer.setPrintable((g,pf,index)->Printable.NO_SUCH_PAGE,initial);
        PrintRequestAttributeSet settings=new HashPrintRequestAttributeSet();settings.add(Sides.ONE_SIDED);settings.add(Chromaticity.MONOCHROME);settings.add(PublicationPrinting.requestedArea(p,printer.getPrintService(),settings));
        if(!printer.printDialog(settings))return null;
        settings.add(Sides.ONE_SIDED);settings.remove(MediaPrintableArea.class);settings.add(PublicationPrinting.requestedArea(p,printer.getPrintService(),settings));var actual=printer.getPageFormat(settings);PublicationPrinting.requireFits(p,actual);
        printer.setPrintable(new Printable(){int cached=-1;BufferedImage image;public int print(Graphics graphics,PageFormat pf,int index)throws PrinterException{if(index<0||index>=job.count())return NO_SUCH_PAGE;PublicationPrinting.requireFits(p,pf);try{if(cached!=index){image=raster(job,index,300);cached=index;}var g=(Graphics2D)graphics.create();try{g.drawImage(image,AffineTransform.getScaleInstance(pf.getWidth()/image.getWidth(),pf.getHeight()/image.getHeight()),null);}finally{g.dispose();}return PAGE_EXISTS;}catch(RuntimeException ex){throw new PrinterException(ex.getMessage());}}},actual);
        // Retain the verified dialog settings for the background print operation.
        return new PreparedPrint(printer,settings);
    }
    record PreparedPrint(PrinterJob printer,PrintRequestAttributeSet settings){void print()throws PrinterException{printer.print(settings);}}
}
