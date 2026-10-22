package gy.deckers.studio;

import javax.print.PrintService;
import javax.print.attribute.*;
import javax.print.attribute.standard.MediaPrintableArea;

/** Requests minimum margins without carrying inherited application clipping into capability queries. */
final class PrintMargins {
    private PrintMargins(){}
    static MediaPrintableArea requestedArea(double widthMm,double heightMm,PrintService service,PrintRequestAttributeSet settings){
        var query=new HashPrintRequestAttributeSet(settings);query.remove(MediaPrintableArea.class);
        var full=new MediaPrintableArea(0,0,(float)widthMm,(float)heightMm,MediaPrintableArea.MM);
        if(service==null)return full;
        MediaPrintableArea best=null;double largest=-1;
        Object supported=service.getSupportedAttributeValues(MediaPrintableArea.class,null,query);
        if(supported instanceof MediaPrintableArea area)supported=new MediaPrintableArea[]{area};
        if(supported instanceof MediaPrintableArea[] areas)for(var area:areas){
            float[] a=area.getPrintableArea(MediaPrintableArea.MM);double size=a[2]*a[3];
            if(a[0]>=0&&a[1]>=0&&a[0]+a[2]<=widthMm+.1&&a[1]+a[3]<=heightMm+.1&&size>largest){best=area;largest=size;}
        }
        // Some drivers advertise their default rectangle rather than all valid rectangles.
        // Explicitly probe smaller margins before accepting that default.
        for(double margin:new double[]{0,5.08}){
            if(widthMm<=2*margin||heightMm<=2*margin)continue;
            var candidate=new MediaPrintableArea((float)margin,(float)margin,(float)(widthMm-2*margin),(float)(heightMm-2*margin),MediaPrintableArea.MM);
            double size=(widthMm-2*margin)*(heightMm-2*margin);
            if(size>largest&&service.isAttributeValueSupported(candidate,null,query))return candidate;
        }
        return best==null?full:best;
    }
    static void apply(java.awt.print.PrinterJob job,PrintRequestAttributeSet settings){
        settings.remove(MediaPrintableArea.class);
        var page=job.getPageFormat(settings);var paper=page.getPaper();
        settings.add(requestedArea(paper.getWidth()*25.4/72,paper.getHeight()*25.4/72,job.getPrintService(),settings));
    }
}
