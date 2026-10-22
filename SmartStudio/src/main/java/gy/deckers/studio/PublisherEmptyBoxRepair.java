package gy.deckers.studio;

import publishing.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.hpsf.*;

/** Recover empty outlined boxes only when Publisher's own preview confirms them. */
final class PublisherEmptyBoxRepair {
    static int repair(Publication publication,Path source)throws Exception{
        if(!source.toString().toLowerCase(Locale.ROOT).endsWith(".pub"))return 0;
        try(var fs=new POIFSFileSystem(source.toFile(),true)){
            if(!fs.getRoot().hasEntry("\005SummaryInformation"))return 0;
            var info=PropertySetFactory.create(fs.getRoot(),"\005SummaryInformation");
            return info instanceof SummaryInformation s?restore(publication,PublisherNativeTables.preview(s.getThumbnail())):0;
        }
    }
    static int restore(Publication p,BufferedImage preview)throws Exception{
        if(preview==null||p.pages.isEmpty())return 0;
        int repaired=0;var page=p.pages.get(0);
        for(var e:page.objects){
            if(!e.type.equals("IMAGE")||e.asset.isBlank()||Math.abs(e.rotation)>.001||e.cropX!=0||e.cropY!=0||e.cropWidth!=1||e.cropHeight!=1||e.width<1||e.height<1)continue;
            var image=PublicationImport.decodeImage(p.assetBytes(e.asset));if(image==null||PublicationTableArtworkRepair.blackFraction(image)<.90)continue;
            double x=e.x,y=e.y,w=e.width,h=e.height;
            if(white(preview,p.width,p.height,x,y,w,h)<.90)continue;
            boolean confirmed=true;for(var edge:new double[][]{{x,y,x+w,y},{x,y+h,x+w,y+h},{x,y,x,y+h},{x+w,y,x+w,y+h}})confirmed&=PublisherNativeTables.confirmed(preview,p.width,p.height,edge);
            if(!confirmed)continue;
            e.type="RECT";e.asset="";e.fill="#ffffff";e.stroke="#000000";e.strokeWidth=.2;e.text="Restored source outline box";e.group="Native Publisher empty boxes";repaired++;
        }
        var groups=new HashMap<String,java.util.List<Publication.Element>>();for(var e:page.objects)if(e.type.equals("TEXT")&&!e.sourceTable.isBlank())groups.computeIfAbsent(e.sourceTable,k->new ArrayList<>()).add(e);
        for(var cells:groups.values()){
            var first=cells.stream().filter(e->e.sourceRow==0&&e.sourceColumn==0).findFirst().orElse(null);if(first==null)continue;
            if(page.objects.stream().noneMatch(e->e.type.equals("LINE")&&e.text.startsWith("Native Publisher table ")&&Math.abs(e.x-first.x)<.15&&Math.abs(e.y-first.y)<.15))continue;
            for(var e:cells){
                if(white(preview,p.width,p.height,e.x,e.y,e.width,e.height)<.90)continue;
                if(!e.fill.equals("#ffffff")){e.fill="#ffffff";repaired++;}
                if(e.text.isBlank()){
                    double size=Math.max(1.5,Math.min(9,e.height*72/25.4*.70));
                    if(!e.asset.isBlank()||e.fitText||e.fontSize>size){e.asset="";e.fitText=false;e.fontSize=Math.min(e.fontSize,size);repaired++;}
                }
            }
        }
        return repaired;
    }
    static double white(BufferedImage image,double width,double height,double x,double y,double w,double h){
        int x0=(int)Math.ceil(x/width*image.getWidth())+2,x1=(int)Math.floor((x+w)/width*image.getWidth())-2;
        int y0=(int)Math.ceil(y/height*image.getHeight())+2,y1=(int)Math.floor((y+h)/height*image.getHeight())-2;
        if(x0<0||y0<0||x1>=image.getWidth()||y1>=image.getHeight()||x1<x0||y1<y0)return 0;
        int white=0,total=0;for(int yy=y0;yy<=y1;yy++)for(int xx=x0;xx<=x1;xx++){int c=image.getRGB(xx,yy);total++;if(((c>>16)&255)>235&&((c>>8)&255)>235&&(c&255)>235)white++;}return total==0?0:(double)white/total;
    }
}
