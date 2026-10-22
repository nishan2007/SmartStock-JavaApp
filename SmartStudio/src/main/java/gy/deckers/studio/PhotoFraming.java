package gy.deckers.studio;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** Session-only, nondestructive framing in fractions of the destination box. */
record PhotoFraming(BufferedImage source,double zoom,double offsetX,double offsetY,int turns,Color background,Color outline,PhotoAdjustments adjustments,BufferedImage rendered) {
    PhotoFraming(BufferedImage source,double zoom,double offsetX,double offsetY,int turns,Color background,Color outline){this(source,zoom,offsetX,offsetY,turns,background,outline,PhotoAdjustments.NONE,source);}
    PhotoFraming(BufferedImage source,double zoom,double offsetX,double offsetY,int turns){this(source,zoom,offsetX,offsetY,turns,null,null);}
    PhotoFraming positioned(double zoom,double x,double y,int turns){return new PhotoFraming(source,zoom,x,y,turns,background,outline,adjustments,rendered);}
    PhotoFraming styled(Color background,Color outline){return new PhotoFraming(source,zoom,offsetX,offsetY,turns,background,outline,adjustments,rendered);}
    PhotoFraming withSource(BufferedImage image){return new PhotoFraming(image,zoom,offsetX,offsetY,turns,background,outline,adjustments,adjustments.apply(image));}
    PhotoFraming adjusted(PhotoAdjustments value){return new PhotoFraming(source,zoom,offsetX,offsetY,turns,background,outline,value,value.apply(source));}
    PhotoFraming {
        if(source==null||!Double.isFinite(zoom+offsetX+offsetY)||zoom<.1||zoom>4)throw new IllegalArgumentException("Invalid photo framing");
        turns=Math.floorMod(turns,4);
    }
    static PhotoFraming initial(BufferedImage image){return new PhotoFraming(image,1,0,0,0);}
    int width(){return turns%2==0?source.getWidth():source.getHeight();}
    int height(){return turns%2==0?source.getHeight():source.getWidth();}
    PhotoFraming fit(double boxWidth,double boxHeight){
        double fill=Math.max(boxWidth/width(),boxHeight/height());
        double fit=Math.min(boxWidth/width(),boxHeight/height());
        return positioned(Math.max(.1,fit/fill),0,0,turns);
    }
    void paint(Graphics2D graphics,double x,double y,double width,double height){
        paint(graphics,x,y,width,height,0);
    }
    void paint(Graphics2D graphics,double x,double y,double width,double height,double bleed){
        Graphics2D g=(Graphics2D)graphics.create();
        try{
            var area=new java.awt.geom.Rectangle2D.Double(x-bleed,y-bleed,width+2*bleed,height+2*bleed);
            g.clip(area);
            if(background!=null){g.setColor(background);g.fill(area);}
            double scale=Math.max(width/width(),height/height())*zoom;
            AffineTransform transform=new AffineTransform();
            transform.translate(x+width*(.5+offsetX),y+height*(.5+offsetY));
            transform.scale(scale,scale);transform.rotate(turns*Math.PI/2);
            transform.translate(-source.getWidth()/2.,-source.getHeight()/2.);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(rendered,transform,null);
            if(outline!=null){double stroke=Math.max(.01,Math.min(width,height)*.01);g.setColor(outline);g.setStroke(new BasicStroke((float)stroke));g.draw(new java.awt.geom.Rectangle2D.Double(x+stroke/2,y+stroke/2,width-stroke,height-stroke));}
        }finally{g.dispose();}
    }
}
