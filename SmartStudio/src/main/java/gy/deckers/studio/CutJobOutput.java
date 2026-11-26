package gy.deckers.studio;

import java.awt.*;
import java.awt.print.*;
import java.io.*;
import java.nio.file.*;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

/** Both outputs paint the frozen job at actual size, with no fit-to-page transform. */
final class CutJobOutput {
    static final double POINTS=72/25.4;
    static PageFormat format(CutJob job){
        Paper paper=new Paper();paper.setSize(job.width()*POINTS,job.height()*POINTS);
        paper.setImageableArea(0,0,paper.getWidth(),paper.getHeight());PageFormat f=new PageFormat();f.setPaper(paper);return f;
    }
    static void requireFormat(CutJob job,PageFormat f)throws PrinterException {
        double epsilon=.5;
        if(Math.abs(f.getWidth()-job.width()*POINTS)>epsilon||Math.abs(f.getHeight()-job.height()*POINTS)>epsilon)
            throw new PrinterException("Choose matching custom media dimensions in the Canon driver; print at actual size / 100%. The driver changed the job size.");
        var b=job.printBounds();
        if(f.getImageableX()>b.getMinX()*POINTS+epsilon||f.getImageableY()>b.getMinY()*POINTS+epsilon||f.getImageableX()+f.getImageableWidth()<b.getMaxX()*POINTS-epsilon||f.getImageableY()+f.getImageableHeight()<b.getMaxY()*POINTS-epsilon)
            throw new PrinterException("Printer margins overlap the production area. Increase job printer margins, then create and print a new job.");
    }
    static Printable printable(CutJob job){return (graphics,format,index)->{
        if(index!=0)return Printable.NO_SUCH_PAGE;requireFormat(job,format);Graphics2D g=(Graphics2D)graphics.create();try{g.scale(POINTS,POINTS);job.paint(g,false);}finally{g.dispose();}return Printable.PAGE_EXISTS;
    };}
    static void pdf(CutJob job,Path target)throws IOException {
        Path temporary=Files.createTempFile(target.toAbsolutePath().getParent(),"cut-print-",".pdf");
        try{
            try(PDDocument document=new PDDocument()){
                float w=(float)(job.width()*POINTS),h=(float)(job.height()*POINTS);PDPage page=new PDPage(new PDRectangle(w,h));document.addPage(page);
                PdfBoxGraphics2D g=new PdfBoxGraphics2D(document,w,h);try{g.scale(POINTS,POINTS);job.paint(g,false);}finally{g.dispose();}
                try(PDPageContentStream stream=new PDPageContentStream(document,page)){stream.drawForm(g.getXFormObject());}document.save(temporary.toFile());
            }
            Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    private CutJobOutput(){}
}
