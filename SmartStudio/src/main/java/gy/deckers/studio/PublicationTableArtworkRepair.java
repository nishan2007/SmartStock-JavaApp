package gy.deckers.studio;

import publishing.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;

/** Recover broken table exports from the source PDF without flattening editable cells. */
final class PublicationTableArtworkRepair {
    static Set<String> clippedHeadings(Publication p,Path reference)throws Exception{
        Set<String> result=new HashSet<>();
        try(var pdf=PDDocument.load(reference.toFile())){
            if(pdf.getNumberOfPages()!=p.pages.size())return result;
            for(int page=0;page<p.pages.size();page++)for(var e:p.pages.get(page).objects){
                if(e.sourceTable.isBlank()||e.sourceRow!=0||e.text.isBlank()||e.rowSpan!=1||e.text.contains("\n"))continue;
                String expected=e.text.replaceAll("\\s+","");if(expected.length()<3)continue;
                var stripper=new org.apache.pdfbox.text.PDFTextStripperByArea();stripper.setSortByPosition(true);stripper.setSuppressDuplicateOverlappingText(false);double pt=72/25.4;
                stripper.addRegion("cell",new java.awt.geom.Rectangle2D.Double(e.x*pt,e.y*pt,e.width*pt,e.height*pt));
                stripper.addRegion("extended",new java.awt.geom.Rectangle2D.Double(e.x*pt,e.y*pt,e.width*pt,e.height*pt*2.5));
                stripper.extractRegions(pdf.getPage(page));String visible=stripper.getTextForRegion("cell").replaceAll("\\s+",""),extended=stripper.getTextForRegion("extended").replaceAll("\\s+","");
                if(!visible.isBlank()&&!visible.equals(expected)&&expected.startsWith(visible)&&extended.equals(expected))result.add(e.id);
            }
        }return result;
    }
    /** Reproject converted text artwork after native row heights replace the converter's stretched geometry. */
    static int refreshNativeCrops(Publication p,Path reference)throws Exception{
        int repaired=0;
        try(var pdf=PDDocument.load(reference.toFile())){
            if(pdf.getNumberOfPages()!=p.pages.size())throw new java.io.IOException("Reference page count differs from editable design.");
            var renderer=new PDFRenderer(pdf);
            for(int page=0;page<p.pages.size();page++){
                var objects=p.pages.get(page).objects;
                if(objects.stream().noneMatch(e->e.type.equals("LINE")&&e.text.startsWith("Native Publisher table ")))continue;
                Map<String,java.util.List<Publication.Element>> tables=new LinkedHashMap<>();
                for(var e:objects)if(!e.sourceTable.isBlank())tables.computeIfAbsent(e.sourceTable,k->new ArrayList<>()).add(e);
                BufferedImage full=null;
                for(var entry:tables.entrySet()){
                    var cells=entry.getValue();double x=cells.stream().mapToDouble(e->e.x).min().orElse(0),y=cells.stream().mapToDouble(e->e.y).min().orElse(0);
                    double width=cells.stream().mapToDouble(e->e.x+e.width).max().orElse(x)-x,height=cells.stream().mapToDouble(e->e.y+e.height).max().orElse(y)-y;
                    if(width<=0||height<=0||x<0||y<0||x+width>p.width+.5||y+height>p.height+.5)continue;
                    if(objects.stream().noneMatch(e->e.type.equals("LINE")&&e.text.startsWith("Native Publisher table ")&&Math.abs(e.x-x)<.1&&Math.abs(e.y-y)<.1))continue;
                    if(full==null)full=renderer.renderImageWithDPI(page,(float)Math.min(200,Math.min(5000*25.4/p.width,5000*25.4/p.height)));
                    double sx=full.getWidth()/p.width,sy=full.getHeight()/p.height;int w=(int)Math.ceil(width*sx),h=(int)Math.ceil(height*sy);
                    if(w<1||h<1||(long)w*h>25000000)continue;
                    var crop=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=crop.createGraphics();try{g.setColor(Color.WHITE);g.fillRect(0,0,w,h);g.drawImage(full,(int)Math.round(-x*sx),(int)Math.round(-y*sy),null);}finally{g.dispose();}
                    var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(crop,"png",bytes);String encoded=Base64.getEncoder().encodeToString(bytes.toByteArray()),id="native-table-"+entry.getKey();boolean changed=false;
                    for(var e:cells){double cx=(e.x-x)/width,cy=(e.y-y)/height,cw=e.width/width,ch=e.height/height;
                        if(!e.text.isBlank()){
                            changed|=!e.asset.equals(id)||!encoded.equals(p.assets.get(id))||Math.abs(e.cropY-cy)>.000001||Math.abs(e.cropHeight-ch)>.000001;
                            e.asset=id;e.cropX=cx;e.cropY=cy;e.cropWidth=cw;e.cropHeight=ch;
                        }
                    }
                    if(changed){p.assetFiles.remove(id);p.assets.put(id,encoded);repaired++;}
                }
            }
        }return repaired;
    }
    static double blackFraction(BufferedImage image){
        long black=0,total=0;int step=Math.max(1,Math.max(image.getWidth(),image.getHeight())/1000);
        for(int y=0;y<image.getHeight();y+=step)for(int x=0;x<image.getWidth();x+=step){
            int c=image.getRGB(x,y);total++;
            if((c>>>24)>240&&((c>>>16)&255)<16&&((c>>>8)&255)<16&&(c&255)<16)black++;
        }
        return (double)black/total;
    }
    static int repair(Publication p,Path reference)throws Exception{
        if(!Files.isRegularFile(reference))return 0;
        int repaired=0;
        try(var pdf=PDDocument.load(reference.toFile())){
            if(pdf.getNumberOfPages()!=p.pages.size())throw new java.io.IOException("Reference page count differs from editable design.");
            var renderer=new PDFRenderer(pdf);
            for(int page=0;page<p.pages.size();page++){
                Map<String,java.util.List<Publication.Element>> tables=new LinkedHashMap<>();
                for(var e:p.pages.get(page).objects)if((!e.asset.isBlank()||!e.sourceTable.isBlank())&&(!e.sourceTable.isBlank()||
                        (e.rotation==0&&e.cropWidth==1&&e.cropHeight==1&&e.width>=1&&e.height>=1&&
                        (e.type.equals("TEXT")||e.type.equals("IMAGE")))))tables.computeIfAbsent(e.asset.isBlank()?"recovered-table-"+e.sourceTable:e.asset,k->new ArrayList<>()).add(e);
                BufferedImage full=null;
                for(var entry:tables.entrySet()){
                    boolean missing=entry.getValue().get(0).asset.isBlank();
                    var original=missing?null:PublicationImport.decodeImage(p.assetBytes(entry.getKey()));
                    if(!missing&&(original==null||blackFraction(original)<.90))continue;
                    var box=pdf.getPage(page).getCropBox();double pw=box.getWidth()*25.4/72,ph=box.getHeight()*25.4/72;
                    if(pdf.getPage(page).getRotation()%180!=0){double temp=pw;pw=ph;ph=temp;}
                    if(Math.abs(pw-p.width)>.5||Math.abs(ph-p.height)>.5)throw new java.io.IOException("Reference page dimensions differ from editable design.");
                    if(full==null)full=renderer.renderImageWithDPI(page,(float)Math.min(200,Math.min(5000*25.4/p.width,5000*25.4/p.height)));
                    var first=entry.getValue().get(0);
                    if(first.cropWidth<=0||first.cropHeight<=0)continue;
                    double width=first.width/first.cropWidth,height=first.height/first.cropHeight;
                    double x=first.x-width*first.cropX,y=first.y-height*first.cropY;
                    double sx=full.getWidth()/p.width,sy=full.getHeight()/p.height;
                    int w=(int)Math.ceil(width*sx),h=(int)Math.ceil(height*sy);
                    if(w<1||h<1||w>5000||h>5000||(long)w*h>25000000)continue;
                    var crop=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=crop.createGraphics();
                    try{g.setColor(Color.WHITE);g.fillRect(0,0,w,h);g.drawImage(full,(int)Math.round(-x*sx),(int)Math.round(-y*sy),null);}finally{g.dispose();}
                    // Keep genuinely dark source tables. Only replace an export which disagrees with its reference.
                    if(blackFraction(crop)>.5)continue;
                    if(first.sourceTable.isBlank()&&first.type.equals("TEXT")&&!first.text.isBlank()){
                        for(var e:entry.getValue())e.asset="";repaired++;continue;
                    }
                    var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(crop,"png",bytes);
                    p.assetFiles.remove(entry.getKey());p.assets.put(entry.getKey(),Base64.getEncoder().encodeToString(bytes.toByteArray()));
                    for(var e:entry.getValue())e.asset=entry.getKey();
                    for(var e:entry.getValue())if(e.fill.equalsIgnoreCase("#18a303")){
                        int white=0;
                        for(int iy=1;iy<=5;iy++)for(int ix=1;ix<=5;ix++){
                            int px=Math.max(0,Math.min(w-1,(int)((e.cropX+e.cropWidth*ix/6)*w)));
                            int py=Math.max(0,Math.min(h-1,(int)((e.cropY+e.cropHeight*iy/6)*h)));
                            int c=crop.getRGB(px,py);if(((c>>>16)&255)>245&&((c>>>8)&255)>245&&(c&255)>245)white++;
                        }
                        if(white>=20)e.fill="#ffffff";
                    }
                    repaired++;
                }
            }
        }
        return repaired;
    }
}
