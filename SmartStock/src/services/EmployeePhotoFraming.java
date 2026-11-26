package services;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** Shared badge rectangle and centered avatar crop, used by editor and web output. */
public final class EmployeePhotoFraming {
    public static final int WIDTH=660,HEIGHT=760;
    public static final Color BACKGROUND=new Color(247,245,240);
    public record Frame(double zoom,double x,double y) {
        public Frame {
            if(!Double.isFinite(zoom)||zoom<.1||zoom>6||!Double.isFinite(x)||!Double.isFinite(y)||Math.abs(x)>3||Math.abs(y)>3)
                throw new IllegalArgumentException("Invalid employee photo framing.");
        }
    }
    private EmployeePhotoFraming() {}
    public static Frame fit(){return new Frame(.85,0,0);}
    public static String encode(Frame f){return new com.google.gson.Gson().toJson(f);}
    public static Frame decode(String json){
        if(json==null||json.isBlank())return fit();
        if(json.length()>300)throw new IllegalArgumentException("Invalid employee photo framing.");
        var b=com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        return new Frame(b.get("zoom").getAsDouble(),b.get("x").getAsDouble(),b.get("y").getAsDouble());
    }
    public static BufferedImage render(BufferedImage source,Frame frame){
        var result=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);var g=result.createGraphics();
        try {
            g.setColor(BACKGROUND);g.fillRect(0,0,WIDTH,HEIGHT);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale=Math.min((double)WIDTH/source.getWidth(),(double)HEIGHT/source.getHeight())*frame.zoom();
            int w=(int)Math.round(source.getWidth()*scale),h=(int)Math.round(source.getHeight()*scale);
            int x=(int)Math.round((WIDTH-w)/2.0+frame.x()*WIDTH),y=(int)Math.round((HEIGHT-h)/2.0+frame.y()*HEIGHT);
            g.drawImage(source,x,y,w,h,null);
        }finally{g.dispose();}
        return result;
    }
    public static BufferedImage avatar(BufferedImage image,int size,boolean framed,boolean mask){
        var result=new BufferedImage(size,size,mask?BufferedImage.TYPE_INT_ARGB:BufferedImage.TYPE_INT_RGB);var g=result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if(mask)g.setClip(new Ellipse2D.Double(0,0,size,size));
            g.setColor(BACKGROUND);g.fillRect(0,0,size,size);
            if(framed){int side=Math.min(image.getWidth(),image.getHeight()),x=(image.getWidth()-side)/2,y=(image.getHeight()-side)/2;g.drawImage(image,0,0,size,size,x,y,x+side,y+side,null);}
            else { // Existing unframed photos retain the entire head instead of center-cropping it away.
                double scale=size*.94/Math.hypot(image.getWidth(),image.getHeight());
                int w=(int)Math.round(image.getWidth()*scale),h=(int)Math.round(image.getHeight()*scale);
                g.drawImage(image,(size-w)/2,(size-h)/2,w,h,null);
            }
        }finally{g.dispose();}
        return result;
    }
}
