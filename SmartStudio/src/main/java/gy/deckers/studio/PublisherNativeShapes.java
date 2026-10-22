package gy.deckers.studio;

import publishing.*;
import org.apache.poi.sl.draw.geom.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

/** Office's cloud-callout preset, recovered when libmspub exports an empty placeholder. */
final class PublisherNativeShapes {
    static void cloud(Publication p,Publication.Page page,PublisherDrawingText.Field f)throws Exception{
        var geometry=PresetGeometries.getInstance().get("cloudCallout");
        Context context=new Context(geometry,new Rectangle2D.Double(0,0,f.width(),f.height()),name->{
            Integer nativeValue=name.equals("adj1")?f.adjustX():name.equals("adj2")?f.adjustY():null;
            if(nativeValue==null)return null;
            Guide guide=new Guide();guide.setName(name);guide.setFmla("val "+(nativeValue/21600.0*100000-50000));return guide;
        });
        record Part(Shape shape,boolean fill,boolean stroke){}
        var parts=new ArrayList<Part>();Rectangle2D bounds=null;
        for(var path:geometry){
            Shape shape=path.getPath(context);var scale=AffineTransform.getScaleInstance(path.getW()>0?f.width()/path.getW():1,path.getH()>0?f.height()/path.getH():1);
            shape=scale.createTransformedShape(shape);parts.add(new Part(shape,path.isFilled(),path.isStroked()));
            if(bounds==null)bounds=shape.getBounds2D();else bounds.add(shape.getBounds2D());
        }
        double pad=Math.max(.05,f.outlineWidth()/2),x=bounds.getX()-pad,y=bounds.getY()-pad,w=bounds.getWidth()+2*pad,h=bounds.getHeight()+2*pad;
        double dpi=300/25.4;int width=(int)Math.ceil(w*dpi),height=(int)Math.ceil(h*dpi);
        if(width<1||height<1||(long)width*height>25000000)throw new IllegalArgumentException("Publisher cloud geometry exceeds import limits.");
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(width/w,height/h);g.translate(-x,-y);
            for(var part:parts){if(part.fill()){g.setColor(new Color(f.color()));g.fill(part.shape());}if(part.stroke()&&f.outlineWidth()>0){g.setColor(new Color(f.outlineColor()));g.setStroke(new BasicStroke((float)f.outlineWidth()));g.draw(part.shape());}}
        }finally{g.dispose();}
        PublicationImport.addImage(p,page,image,f.x()+p.width/2+x,f.y()+p.height/2+y,w,h);
        page.objects.get(page.objects.size()-1).text="Publisher thought bubble";
    }
}
