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
    static void configureNativePipeline(){
        if(System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("windows")
                &&System.getProperty("sun.java2d.print.pipeline")==null)
            System.setProperty("sun.java2d.print.pipeline","raster");
    }
    static PrintRequestAttributeSet settings(Publication p,String paper){
        var settings=new HashPrintRequestAttributeSet();
        var media=switch(paper){
            case "A4"->javax.print.attribute.standard.MediaSizeName.ISO_A4;
            case "Legal"->javax.print.attribute.standard.MediaSizeName.NA_LEGAL;
            case "11 × 17"->javax.print.attribute.standard.MediaSizeName.TABLOID;
            case "Custom"->null;
            default->javax.print.attribute.standard.MediaSizeName.NA_LETTER;
        };
        if(media!=null)settings.add(media);
        settings.add(PublicationPrintOptions.sides(p));
        settings.add(p.print.width>p.print.height?javax.print.attribute.standard.OrientationRequested.LANDSCAPE:javax.print.attribute.standard.OrientationRequested.PORTRAIT);
        // Do not probe the default printer for MediaPrintableArea here. The user
        // may select another driver in the native dialog; its hardware area wins.
        return settings;
    }
    static void selectedPrinterArea(Publication p,PrintService selected,PrintRequestAttributeSet settings){
        // The native dialog can insert Java's one-inch default rectangle even
        // though the selected printer supports much smaller hardware margins.
        // Sheet margins belong to the publication; query the chosen device with
        // its final media/tray settings rather than inheriting this extra clip.
        settings.add(requestedArea(p,selected,settings));
    }
    static void selectedPrinterQuality(PrintService selected,PrintRequestAttributeSet settings){
        if(selected==null)return;
        var resolution=new javax.print.attribute.standard.PrinterResolution(600,600,javax.print.attribute.standard.PrinterResolution.DPI);
        for(javax.print.attribute.Attribute candidate:new javax.print.attribute.Attribute[]{resolution,javax.print.attribute.standard.PrintQuality.HIGH,javax.print.attribute.standard.Chromaticity.COLOR}){
            if(settings.get(candidate.getCategory())==null&&selected.isAttributeValueSupported(candidate,null,settings))
                settings.add(candidate);
        }
    }
    static Rectangle2D requiredArea(Publication p){
        return requiredArea(p,true);
    }
    private static Rectangle2D requiredArea(Publication p,boolean marks){
        var layout=SheetLayout.pack(p.width,p.height,p.print);
        Rectangle2D result=null;
        for(var front:layout.placements()){
            for(int side=0;side<(p.print.duplex?2:1);side++){
                var place=side==0?front:SheetLayout.back(front,p.print);
                double reach=p.print.bleed+(marks&&p.print.cropMarks?MARK_REACH_MM:0);
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
        // Preserve actual size. The printable can reposition a layout that fits
        // within the hardware area, without changing card dimensions or capacity.
    }
    static Printable printable(Publication p){
        // Keep a bounded cache of full-resolution assets across native raster bands.
        PublicationRenderer renderer=new PublicationRenderer(p,Integer.MAX_VALUE);int count=renderer.sheets();
        return new Printable(){
          public synchronized int print(Graphics graphics,PageFormat page,int index)throws PrinterException{
            if(index<0||index>=count)return Printable.NO_SUCH_PAGE;
            requireFits(p,page);
            Graphics2D draw=(Graphics2D)graphics.create();
            try{
                draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                draw.scale(POINTS_PER_MM,POINTS_PER_MM);
                Rectangle2D bounds=requiredArea(p);
                double width=page.getImageableWidth()/POINTS_PER_MM,height=page.getImageableHeight()/POINTS_PER_MM;
                // Prefer preserving cut marks, but prioritize full-size card artwork
                // when the external marks cannot fit inside the hardware margins.
                if(bounds.getWidth()>width||bounds.getHeight()>height)bounds=requiredArea(p,false);
                draw.translate(fitOffset(bounds.getMinX(),bounds.getMaxX(),page.getImageableX()/POINTS_PER_MM,width),
                        fitOffset(bounds.getMinY(),bounds.getMaxY(),page.getImageableY()/POINTS_PER_MM,height));
                // Draw original assets/text into the driver's raster bands. A
                // separate 300-DPI sheet would discard detail before spooling.
                renderer.sheet(draw,index);
            }finally{draw.dispose();}
            return Printable.PAGE_EXISTS;
          }
        };
    }
    private static double fitOffset(double start,double end,double printableStart,double length){
        if(end-start>length+0.001)return 0;
        return Math.max(printableStart-start,Math.min(0,printableStart+length-end));
    }
}
