package gy.deckers.studio;

import publishing.*;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.io.MemoryUsageSetting;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Physical banner geometry, bounded previews and disk-backed PDF content streams. */
final class BannerDocuments {
    static final double MAX_MM=1_000_000;
    static Publication create(double width,double height,String unit) {
        if(!Set.of("FT","IN","CM","MM").contains(unit))throw new IllegalArgumentException("Choose feet, inches, centimeters or millimeters.");
        Publication p=new Publication();p.title="Banner";p.template=true;p.displayUnit=unit;
        p.width=Publication.toMm(width,unit);p.height=Publication.toMm(height,unit);p.validate();return p;
    }
    static Path folder(){return Path.of(System.getProperty("user.home"),"template-library","banners");}
    static List<Path> files()throws IOException{return PublicationTemplateLibrary.files(List.of(folder()));}
    static void pdf(Publication source,Path target)throws IOException {
        source.validate();PublicationPreflight.requireMergeData(source);
        if(!source.mergeRows.isEmpty())throw new IllegalArgumentException("Banner export uses template artwork. Clear merge records before exporting.");
        float unit=(float)Math.max(1,Math.ceil(Math.max(source.width,source.height)/5080.));
        float width=(float)(source.width*72/25.4/unit),height=(float)(source.height*72/25.4/unit);
        Path temp=Files.createTempFile(target.toAbsolutePath().getParent(),"banner-",".pdf");
        try {
            try(PDDocument result=new PDDocument(MemoryUsageSetting.setupTempFileOnly())) {
                result.setVersion(1.6f);
                for(int i=0;i<source.pages.size();i++) {
                    if(Thread.currentThread().isInterrupted())throw new IOException("Export cancelled.");
                    if(source.pages.get(i).reference)continue;
                    PDPage page=new PDPage(new PDRectangle(width,height));page.setUserUnit(unit);result.addPage(page);
                    PdfBoxGraphics2D graphics=new PdfBoxGraphics2D(result,width,height);
                    graphics.scale(72/25.4/unit,72/25.4/unit);
                    try{new PublicationRenderer(source).page(graphics,i,Map.of());}finally{graphics.dispose();}
                    try(PDPageContentStream output=new PDPageContentStream(result,page)){output.drawForm(graphics.getXFormObject());}
                }
                if(result.getNumberOfPages()==0)throw new IOException("The banner has no printable pages.");
                result.save(temp.toFile());
            }
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(RuntimeException ex){throw new IOException("Banner export failed: "+ex.getMessage(),ex);}
        finally {Files.deleteIfExists(temp);}
    }
    private BannerDocuments(){}
}
