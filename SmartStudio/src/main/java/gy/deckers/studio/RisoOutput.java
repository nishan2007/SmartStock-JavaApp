package gy.deckers.studio;
import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
/** Physical-size grayscale master output shared by preview, PDF, and printing. */
final class RisoOutput {
    private RisoOutput() {}
    static BufferedImage plate(Publication publication,int index,int dpi,List<RisoSeparation.Ink> inks,int ink,boolean registration){
        return plate(publication,index,dpi,inks,ink,registration,java.util.Map.of());
    }
    static BufferedImage plate(Publication publication,int index,int dpi,List<RisoSeparation.Ink> inks,int ink,boolean registration,java.util.Map<String,Integer> assignments){
        BufferedImage result=RisoImageAssignments.render(publication,index,dpi,inks,ink,assignments);
        if(registration){var bounds=PublicationPrinting.requiredArea(publication);for(double x:new double[]{5,publication.print.width-5})for(double y:new double[]{5,publication.print.height-5})if(bounds.intersects(x-2.1,y-2.1,4.2,4.2))throw new IllegalArgumentException("Registration marks overlap the artwork or cut marks. Increase sheet margins or disable registration marks.");Graphics2D g=result.createGraphics();try{g.setColor(Color.BLACK);g.setStroke(new BasicStroke(Math.max(1,dpi/127f)));double scale=dpi/25.4;for(double x:new double[]{5,publication.print.width-5})for(double y:new double[]{5,publication.print.height-5}){int xx=(int)Math.round(x*scale),yy=(int)Math.round(y*scale),r=(int)Math.round(2*scale);g.drawLine(xx-r,yy,xx+r,yy);g.drawLine(xx,yy-r,xx,yy+r);}}finally{g.dispose();}}
        return result;
    }
    static Path pdf(Publication publication,List<RisoSeparation.Ink> inks,boolean registration,Path destination)throws java.io.IOException {
        return pdf(publication,inks,registration,destination,java.util.Map.of());
    }
    static Path pdf(Publication publication,List<RisoSeparation.Ink> inks,boolean registration,Path destination,java.util.Map<String,Integer> assignments)throws java.io.IOException {
        PublicationPreflight.requireMergeData(publication);PublicationRenderer renderer=new PublicationRenderer(publication);
        RisoImageAssignments.resolve(publication,inks,assignments);
        if(renderer.sheets()<1||renderer.sheets()>1000||inks.size()>8||inks.stream().noneMatch(RisoSeparation.Ink::enabled))throw new IllegalArgumentException("Choose enabled inks and at most 1000 sheets.");
        Path temp=Files.createTempFile(destination.toAbsolutePath().getParent(),"riso-",".pdf");try{try(PDDocument pdf=new PDDocument()){
            var labels=new org.apache.pdfbox.pdmodel.common.PDPageLabels(pdf);int pageIndex=0;
            for(int ink=0;ink<inks.size();ink++)if(inks.get(ink).enabled())for(int s=0;s<renderer.sheets();s++){
                if(Thread.currentThread().isInterrupted())throw new java.io.IOException("Export cancelled.");
                PDPage page=new PDPage(new PDRectangle((float)(publication.print.width*72/25.4),(float)(publication.print.height*72/25.4)));pdf.addPage(page);
                var range=new org.apache.pdfbox.pdmodel.common.PDPageLabelRange();range.setPrefix(inks.get(ink).name()+" • Sheet "+(s+1));labels.setLabelItem(pageIndex++,range);
                try(PDPageContentStream out=new PDPageContentStream(pdf,page)){out.drawImage(LosslessFactory.createFromImage(pdf,plate(publication,s,300,inks,ink,registration,assignments)),0,0,page.getMediaBox().getWidth(),page.getMediaBox().getHeight());}
            }pdf.getDocumentCatalog().setPageLabels(labels);pdf.save(temp.toFile());}Files.move(temp,destination,StandardCopyOption.REPLACE_EXISTING);return destination;}finally{Files.deleteIfExists(temp);}
    }
}
