package gy.deckers.studio;

import publishing.Publication;
import java.util.*;

/** Quarter-turns the whole design without stretching or rasterising its artwork. */
final class TemplateOrientation {
    record Position(double x,double y){}
    static Position position(double x,double y,double w,double h,double pageW,double pageH,boolean clockwise){
        double cx=x+w/2,cy=y+h/2;
        return new Position((clockwise?pageH-cy:cy)-w/2,(clockwise?cx:pageW-cx)-h/2);
    }
    static boolean turn(Publication p,boolean portrait){
        if(p.width==p.height||(p.width<p.height)==portrait)return false;
        double w=p.width,h=p.height;boolean clockwise=portrait;
        for(var page:p.allPages()){
            var horizontal=new ArrayList<>(page.horizontalGuides);var vertical=new ArrayList<>(page.verticalGuides);
            page.verticalGuides.clear();for(double y:horizontal)page.verticalGuides.add(clockwise?h-y:y);
            page.horizontalGuides.clear();for(double x:vertical)page.horizontalGuides.add(clockwise?x:w-x);
            for(var e:page.objects){var next=position(e.x,e.y,e.width,e.height,w,h,clockwise);e.x=next.x();e.y=next.y();e.rotation=ImageFormattingToolbar.normalized(e.rotation+(clockwise?90:-90));}
        }
        p.width=h;p.height=w;p.validate();return true;
    }
    static boolean turn(BadgeTemplate p,boolean portrait){
        if(p.widthMm==p.heightMm||(p.widthMm<p.heightMm)==portrait)return false;
        double w=p.widthMm,h=p.heightMm;boolean clockwise=portrait;
        for(var e:p.allElements()){var next=position(e.x,e.y,e.width,e.height,w,h,clockwise);e.x=next.x();e.y=next.y();e.rotation=ImageFormattingToolbar.normalized(e.rotation+(clockwise?90:-90));}
        p.widthMm=h;p.heightMm=w;p.validate();return true;
    }
}
