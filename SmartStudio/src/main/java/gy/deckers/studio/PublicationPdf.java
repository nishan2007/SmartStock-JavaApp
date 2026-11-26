package gy.deckers.studio;

import publishing.*;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.io.MemoryUsageSetting;
import java.io.IOException;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2DLosslessImageEncoder;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/** Actual-size sheets with vector artwork and original-resolution image assets. */
final class PublicationPdf {
    private PublicationPdf(){}
    static void write(Publication source,Path target,int first,int last)throws IOException{
        source.validate();PublicationPreflight.requireMergeData(source);
        var renderer=new PublicationRenderer(source);
        if(first<0||last<first||last>=renderer.sheets()||last-first>999)
            throw new IOException("Choose a valid export range of at most 1000 sheets.");
        Path temporary=Files.createTempFile(target.toAbsolutePath().getParent(),"studio-sheet-",".pdf");
        try{
            try(PDDocument document=new PDDocument(MemoryUsageSetting.setupTempFileOnly())){
                var preferences=new org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences(new org.apache.pdfbox.cos.COSDictionary());
                preferences.setPrintScaling(org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences.PRINT_SCALING.None);
                preferences.setDuplex(source.print.duplex
                        ?(PublicationPrintOptions.sides(source)==javax.print.attribute.standard.Sides.TWO_SIDED_SHORT_EDGE
                            ?org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences.DUPLEX.DuplexFlipShortEdge
                            :org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences.DUPLEX.DuplexFlipLongEdge)
                        :org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences.DUPLEX.Simplex);
                document.getDocumentCatalog().setViewerPreferences(preferences);
                var encoder=new PdfBoxGraphics2DLosslessImageEncoder();
                Map<String,PDImageXObject> encoded=new LinkedHashMap<>(){
                    protected boolean removeEldestEntry(Map.Entry<String,PDImageXObject> entry){return size()>128;}
                };
                float width=(float)(source.print.width*72/25.4),height=(float)(source.print.height*72/25.4);
                for(int sheet=first;sheet<=last;sheet++){
                    if(Thread.currentThread().isInterrupted())throw new IOException("Export cancelled.");
                    var page=new PDPage(new PDRectangle(width,height));document.addPage(page);
                    var graphics=new PdfBoxGraphics2D(document,width,height);
                    graphics.setImageEncoder((doc,content,image,environment)->{
                        // Repeated card slots may decode separate BufferedImages of
                        // the same artwork. Embed identical pixels only once.
                        String key=image instanceof BufferedImage bitmap?imageKey(bitmap):null;
                        PDImageXObject existing=key==null?null:encoded.get(key);
                        if(existing!=null)return existing;
                        PDImageXObject result=encoder.encodeImage(doc,content,image,environment);
                        if(key!=null)encoded.put(key,result);
                        return result;
                    });
                    try{graphics.scale(72/25.4,72/25.4);renderer.sheet(graphics,sheet);}finally{graphics.dispose();}
                    try(var content=new PDPageContentStream(document,page)){content.drawForm(graphics.getXFormObject());}
                }
                document.save(temporary.toFile());
            }
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(RuntimeException ex){throw new IOException("PDF export failed: "+ex.getMessage(),ex);}
        finally{Files.deleteIfExists(temporary);}
    }
    private static String imageKey(BufferedImage image){
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            byte[] row=new byte[Math.multiplyExact(image.getWidth(),4)];
            for(int y=0;y<image.getHeight();y++){
                int[] pixels=image.getRGB(0,y,image.getWidth(),1,null,0,image.getWidth());
                for(int x=0;x<pixels.length;x++){int pixel=pixels[x],offset=x*4;row[offset]=(byte)(pixel>>>24);row[offset+1]=(byte)(pixel>>>16);row[offset+2]=(byte)(pixel>>>8);row[offset+3]=(byte)pixel;}
                hash.update(row);
            }
            return image.getWidth()+"x"+image.getHeight()+":"+HexFormat.of().formatHex(hash.digest());
        }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
