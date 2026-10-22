package gy.deckers.studio;

import publishing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.*;
import javax.print.PrintService;
import javax.print.attribute.*;
import javax.print.attribute.standard.MediaPrintableArea;

/** Sheet layout margins position artwork; they must not clip its external cut marks. */
final class PublicationPrinting {
    static final double POINTS_PER_MM=72/25.4;
    private static final double MARK_REACH_MM=2.55; // 2 mm mark + .5 mm offset + half stroke
    private PublicationPrinting(){}
    static Rectangle2D requiredArea(Publication p){
        var layout=SheetLayout.pack(p.width,p.height,p.print);
        Rectangle2D result=null;
        for(var front:layout.placements()){
            for(int side=0;side<(p.print.duplex?2:1);side++){
                var place=side==0?front:SheetLayout.back(front,p.print);
                double reach=p.print.bleed+(p.print.cropMarks?MARK_REACH_MM:0);
                double x=place.x()+p.print.offsetX+(side==0?0:p.print.backOffsetX);
                double y=place.y()+p.print.offsetY+(side==0?0:p.print.backOffsetY);
                var bounds=new Rectangle2D.Double(x-reach,y-reach,place.width()+2*reach,place.height()+2*reach);
                if(result==null)result=bounds;else result.add(bounds);
            }
        }
        if(result==null)throw new IllegalArgumentException("The item does not fit this sheet.");
        return result;
    }
    static MediaPrintableArea requestedArea(Publication p,PrintService service,PrintRequestAttributeSet settings){
        return PrintMargins.requestedArea(p.print.width,p.print.height,service,settings);
    }
    static void requireFits(Publication p,PageFormat page)throws PrinterException{
        double scale=POINTS_PER_MM;
        if(Math.abs(page.getWidth()-p.print.width*scale)>2||Math.abs(page.getHeight()-p.print.height*scale)>2)
            throw new PrinterException("Printer paper differs from sheet setup. Choose the matching paper and preview again.");
        // Preserve actual size and sheet coordinates; let the printer clip at its hardware limits.
    }
    static Printable printable(Publication p){
        PublicationRenderer renderer=new PublicationRenderer(p);int count=renderer.sheets();
        return (graphics,page,index)->{
            if(index<0||index>=count)return Printable.NO_SUCH_PAGE;
            requireFits(p,page);
            Graphics2D draw=(Graphics2D)graphics.create();
            try{draw.scale(POINTS_PER_MM,POINTS_PER_MM);renderer.sheet(draw,index);}finally{draw.dispose();}
            return Printable.PAGE_EXISTS;
        };
    }
}
